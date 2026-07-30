# 提交 0380：Core: Fix lock acquisition logic in HadoopTableOperations rename (#9498)

## 提交信息

- **序号**：0380
- **哈希**：d4056530d27864adb6cf141d85c81adde46c7b28
- **短哈希**：d4056530d
- **日期**：2024-01-17 23:02:47 +0200
- **作者**：N-o-Z <ozery.nir@gmail.com>
- **提交说明**：Core: Fix lock acquisition logic in HadoopTableOperations rename (#9498)
- **PR/Issue**：#9498

## 总体目的

这个提交修复了 `HadoopTableOperations#renameToFinal` 中一个严重的并发控制缺陷：代码调用了 `lockManager.acquire(...)` 却完全忽略了它的返回值。`LockManager.acquire(entityId, ownerId)` 的契约是返回 `boolean`——`true` 表示成功获取锁，`false` 表示获取失败。原代码无视这个返回值，无论锁是否拿到都继续执行 rename 提交流程，等于让锁机制形同虚设。这会让 Hadoop catalog 在并发提交场景下失去互斥保护，可能导致元数据文件被并发覆盖、版本错乱。

要理解这个缺陷的影响，需要先看 `renameToFinal` 在 Hadoop catalog 提交链路中的角色。Hadoop catalog 的提交原子性依赖文件系统的 rename 操作：每次提交把写好的新版本元数据文件从临时位置 rename 到目标版本路径（`vN.metadata.json`），通过 rename 的原子性来保证同一版本只有一个写入者胜出。但 rename 的原子性只能保证"同一目标文件名"不被并发覆盖，无法保证"提交版本号递增"的串行性。因此 Iceberg 在 `renameToFinal` 中加入了 `lockManager`，以 `dst`（目标版本路径）为实体 ID、`src`（源临时文件）为 owner ID 加锁，确保对同一目标版本的写入串行化。

问题在于：原代码 `lockManager.acquire(dst.toString(), src.toString());` 把返回值直接丢弃了。当 `acquire` 因超时、竞争失败等原因返回 `false` 时，代码不会感知到失败，继续往下执行 `fs.exists(dst)` 检查和 `fs.rename(src, dst)`。这意味着：
- 即使锁没拿到，提交仍会尝试进行，锁的互斥作用被架空；
- 在 `InMemoryLockManager` 实现下，`acquire` 失败时不会注册心跳，但 `finally` 块仍无条件调用 `release`，曾引发 NPE（这正是历史提交 0106 / #8494 修复的 `InMemoryLockManager#release` 空指针问题）。

本提交与 0106 是针对同一调用链问题的互补修复：0106 在锁管理器实现侧让 `release` 对"未成功 acquire"的场景变得健壮（不再抛 NPE）；而本提交 0380 在调用侧让代码真正尊重 `acquire` 的返回值——获取失败就立刻抛 `CommitFailedException` 终止提交，而不是带病继续。两者结合后，调用链既不会在 release 时崩溃，也不会在 acquire 失败后错误地推进提交。

## 如何达成设计目的

整体思路是在调用侧补齐对 `acquire` / `release` 返回值的处理：

1. **acquire 失败立即失败**：把 `lockManager.acquire(...)` 的返回值用 `if (!...)` 检查，返回 `false` 时抛 `CommitFailedException`，附上目标文件和 owner 信息便于排查。由于 `CommitFailedException` 是 `RuntimeException`，不会被后续的 `catch (IOException e)` 吞掉，会直接向上传播终止提交，符合"加锁失败就放弃提交"的语义。
2. **release 失败降级为告警**：在 `finally` 块中检查 `release` 的返回值，返回 `false` 时只记 `LOG.warn` 而不抛异常。这是合理的权衡——释放阶段抛异常会掩盖 try 块里原始的提交失败原因，而释放失败通常意味着锁已因租约过期等原因不再被持有，记日志即可。
3. **新增测试覆盖**：用一个始终返回 `false` 的 `NoLockManager` 注入到 `HadoopTableOperations`，断言 `commit` 抛出 `CommitFailedException` 且消息以 "Failed to acquire lock on file" 开头，锁定了修复的行为契约。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopTableOperations.java`

**修改目的**：让 `renameToFinal` 真正尊重 `LockManager` 的返回值，在获取锁失败时终止提交，并在释放锁失败时记录告警，而不是静默忽略。

**工作逻辑**：

`renameToFinal(FileSystem fs, Path src, Path dst, int nextVersion)` 是 Hadoop catalog 把临时元数据文件 rename 为正式版本文件的核心方法，整个方法用 try/catch/finally 包裹：

- **acquire 部分**：原代码为 `lockManager.acquire(dst.toString(), src.toString());`（丢弃返回值）。修改为：

  ```java
  if (!lockManager.acquire(dst.toString(), src.toString())) {
    throw new CommitFailedException(
        "Failed to acquire lock on file: %s with owner: %s", dst, src);
  }
  ```

  这样当 `acquire` 返回 `false`（锁未拿到）时，立即抛 `CommitFailedException`。该异常是 `RuntimeException`，不会被下面的 `catch (IOException e)` 捕获，会直接向上抛出终止本次提交，符合"拿不到锁就不提交"的语义。消息中带上 `dst`（目标版本路径）和 `src`（owner/源临时文件）便于排查竞争场景。

- **release 部分**（`finally` 块）：原代码为 `lockManager.release(dst.toString(), src.toString());`（丢弃返回值）。修改为：

  ```java
  if (!lockManager.release(dst.toString(), src.toString())) {
    LOG.warn("Failed to release lock on file: {} with owner: {}", dst, src);
  }
  ```

  释放失败时仅记 `warn` 级日志而不抛异常。这是有意为之：`finally` 块里抛异常会覆盖 try 块中真正要传播的 `CommitFailedException`（来自版本已存在、rename 失败、acquire 失败等），让真正的失败原因被掩盖。而 release 返回 `false` 通常意味着锁已因租约过期等原因自然失效，记日志即可，不需要升级为提交失败。

整体上，try 块的后续逻辑（`fs.exists(dst)` 版本存在检查、`fs.rename(src, dst)` 原子提交、rename 失败时 `tryDelete(src)` 清理）保持不变。`catch (IOException e)` 分支把 IO 异常包装成 `CommitFailedException` 并尝试清理源文件，逻辑也不变。改动只聚焦在"锁的返回值处理"这一点上，不触碰 rename 与版本校验的既有语义。

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopCommits.java`

**修改目的**：新增测试 `testCommitFailedToAcquireLock`，验证当 `LockManager.acquire` 始终返回 `false` 时，`HadoopTableOperations.commit` 会抛出 `CommitFailedException`，锁定修复后的行为契约。

**工作逻辑**：

- 新增若干 import：`java.util.Map`、`org.apache.hadoop.conf.Configuration`、`org.apache.hadoop.fs.Path`、`org.apache.iceberg.LockManager` 等，用于构造自定义锁管理器和 `HadoopTableOperations` 实例。

- 新增测试方法 `testCommitFailedToAcquireLock`：
  1. 先用正常的表做一次 `newFastAppend().appendFile(FILE_A).commit()`，确保表有一个初始快照和当前 `TableMetadata`。
  2. 构造一个 `NoLockManager`（见下文），它的 `acquire` 恒返回 `false`。
  3. 用这个 `NoLockManager` 手动 new 一个 `HadoopTableOperations`（指向同一表 location），并调用 `refresh()` 让它读到当前元数据。
  4. 取表的当前 `TableMetadata` 作为 `meta2`，调用 `tableOperations.commit(tableOperations.current(), meta2)`——由于新旧 metadata 相同本不会产生新快照，但因为 `NoLockManager` 的 `acquire` 恒为 `false`，提交会在 `renameToFinal` 的 acquire 检查处抛 `CommitFailedException`。
  5. 用 AssertJ 断言抛出的是 `CommitFailedException`，且消息以 `"Failed to acquire lock on file"` 开头，精确对应修复后新增的异常分支。

- 新增内部静态类 `NoLockManager implements LockManager`：实现 `LockManager` 接口的四个方法，`acquire` 与 `release` 恒返回 `false`，`close` 与 `initialize` 为空实现。这是一个专门用于"模拟锁永远拿不到"的测试替身，使得测试无需依赖真实锁管理器的超时/竞争行为，就能稳定复现 acquire 失败路径。

该测试与生产代码改动形成闭环：它直接验证了"acquire 返回 false 时提交被正确拒绝"这一新行为，未来若有人误改回忽略返回值的写法，此测试会立刻失败。

## 小结

这是一个修复 Hadoop catalog 提交并发控制核心缺陷的重要提交。原代码调用 `lockManager.acquire(...)` 却丢弃返回值，使锁机制在"获取失败"时形同虚设，并发提交可能绕过互斥保护。修复在调用侧补齐了对 `acquire`/`release` 返回值的处理：acquire 失败立即抛 `CommitFailedException` 终止提交，release 失败降级为告警以避免掩盖原始失败原因。

它与历史提交 0106（#8494，修复 `InMemoryLockManager#release` 的 NPE）是同一调用链的互补修复：0106 让锁管理器实现侧对"未成功 acquire 后被 release"健壮，0380 让调用侧真正尊重 acquire 的成败。两者结合，既消除了 release 路径的崩溃，又恢复了 acquire 路径应有的互斥语义。配套测试用 `NoLockManager` 替身稳定复现并锁定了修复行为，是质量较高的缺陷修复范例。
