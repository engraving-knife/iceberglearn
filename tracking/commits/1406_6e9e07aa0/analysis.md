# 提交 1406：Hive: Bugfix for incorrect Deletion of Snapshot Metadata Due to OutOfMemoryError (#11576)

## 提交信息

- **序号**：1406 / 4088
- **哈希**：6e9e07aa0f35197bb23b218d23716e928d1c2814
- **短哈希**：6e9e07aa0
- **日期**：2024-11-21（Thu Nov 21 05:26:19 2024 +0800）
- **作者**：Zhendong Bai <johnnyprimary@163.com>
- **提交说明**：Hive: Bugfix for incorrect Deletion of Snapshot Metadata Due to OutOfMemoryError (#11576)
- **PR/Issue**：#11576

## 总体目的

`HiveTableOperations.doCommit` / `HiveViewOperations.doCommit` 在向 Hive Metastore 持久化表/视图后，通过 `finally` 块调用 `HiveOperationsBase.cleanupMetadataAndUnlock(io(), commitStatus, newMetadataLocation, lock)`：

```java
static void cleanupMetadata(FileIO io, String commitStatus, String metadataLocation) {
  try {
    if (commitStatus.equalsIgnoreCase("FAILURE")) {
      io.deleteFile(metadataLocation);  // 只在 FAILURE 时删新写入的 metadata 文件
    }
  } catch (RuntimeException e) {
    LOG.error("Failed to cleanup metadata file at {}", metadataLocation, e);
  }
}
```

也就是说，`commitStatus == FAILURE` 时会删掉本次新写入但未被 HMS 接受的 metadata 文件；`SUCCESS` 或 `UNKNOWN` 都不删（`UNKNOWN` 留待后续人工或快照过期清理，避免误删可能已经成功的提交）。

`doCommit` 中：

```java
BaseMetastoreOperations.CommitStatus commitStatus =
    BaseMetastoreOperations.CommitStatus.FAILURE;   // 初始值
// ...
try {
  lock.lock();
  // ...
  try {
    persistTable(...);
    commitStatus = BaseMetastoreOperations.CommitStatus.SUCCESS;
  } catch (LockException le) { /* ... */ } 
  // ...
} catch (Throwable e) {
  // ...特定错误判断...
  LOG.error("Cannot tell if commit to {}.{} succeeded, ...", e);
  commitStatus =
      BaseMetastoreOperations.CommitStatus.valueOf(
          checkCommitStatus(newMetadataLocation, metadata).name());  // 可能再抛 OOM
  switch (commitStatus) {
    case SUCCESS: break;
    case FAILURE: throw e;
    case UNKNOWN: throw new CommitStateUnknownException(e);
  }
} finally {
  HiveOperationsBase.cleanupMetadataAndUnlock(io(), commitStatus, newMetadataLocation, lock);
}
```

问题场景：`persistTable(...)` 在 HMS 端已经成功提交，但在客户端抛出 `Throwable`（例如 `UnknownError`/`OutOfMemoryError`），代码进入 `catch (Throwable e)`。此时 `commitStatus` 仍是初始值 `FAILURE`。接下来调用 `checkCommitStatus(...)` 想确认提交是否真的成功；但如果 JVM 此时又抛 `OutOfMemoryError`（堆已耗尽），`checkCommitStatus` 的赋值不会发生，`commitStatus` 仍是 `FAILURE`。

进入 `finally` 后，`cleanupMetadata` 看到 `FAILURE`，就把「HMS 端已经成功接受、新写入的 metadata 文件」删掉了。结果 HMS 上的表元数据指向一个已被删除的 metadata 文件，表变得不可用，且这种损坏不可逆（除非文件系统有备份）。

本提交的目的是：在进入 `checkCommitStatus` 之前先把 `commitStatus` 显式置为 `UNKNOWN`，这样即使 `checkCommitStatus` 因 OOM 失败，`finally` 也会以 `UNKNOWN` 清理（即不删 metadata 文件），保留可能成功的提交，把「可能存在的孤儿文件」留给后续 orphan files 清理，而不是「误删已成功提交的 metadata」。

## 如何达成设计目的

在 `HiveTableOperations.doCommit` 与 `HiveViewOperations.doCommit` 的 `catch (Throwable e)` 块内，在调用 `checkCommitStatus(...)` 之前先执行：

```java
commitStatus = BaseMetastoreOperations.CommitStatus.UNKNOWN;
```

这样：

1. `checkCommitStatus` 正常返回 → `commitStatus` 被覆写为 `SUCCESS` / `FAILURE` / `UNKNOWN`，后续 `switch` 处理与原逻辑一致；
2. `checkCommitStatus` 抛 `OutOfMemoryError`（或其它 `Throwable`）→ `commitStatus` 保持刚设置的 `UNKNOWN`，`finally` 中的 `cleanupMetadata` 不会执行 `deleteFile`，metadata 文件得以保留。

这是一个最小化的「先降级为安全状态、再尝试确认」的修复：在不改变成功路径行为的前提下，把异常路径下的最坏情况从「误删已成功提交的 metadata」降级为「可能遗留孤儿 metadata 文件」。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java`

**修改目的**：避免 `checkCommitStatus` 抛 OOM 时把 `commitStatus` 留在 `FAILURE`，导致 `finally` 误删已成功提交的 metadata。

**工作逻辑**：在 `catch (Throwable e)` 内、`checkCommitStatus` 调用之前插入一行：

```java
LOG.error(
    "Cannot tell if commit to {}.{} succeeded, attempting to reconnect and check.",
    database,
    tableName,
    e);
commitStatus = BaseMetastoreOperations.CommitStatus.UNKNOWN;   // 新增：先置为安全值
commitStatus =
    BaseMetastoreOperations.CommitStatus.valueOf(
        checkCommitStatus(newMetadataLocation, metadata).name());
switch (commitStatus) {
  case SUCCESS: break;
  case FAILURE: throw e;
  case UNKNOWN: throw new CommitStateUnknownException(e);
}
```

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveViewOperations.java`

**修改目的**：把同样的修复应用到 Hive 视图提交路径。

**工作逻辑**：

1. 增加 import：`org.apache.iceberg.BaseMetastoreOperations`；
2. 在 `catch (Throwable e)` 内、`checkCommitStatus(...)` 调用之前插入：

```java
commitStatus = BaseMetastoreOperations.CommitStatus.UNKNOWN;
```

注意 `HiveViewOperations#checkCommitStatus` 签名与表不同（多了 viewName、Map、Supplier 参数），但修复模式一致：先降级为 `UNKNOWN`，再尝试确认。

### 测试：`TestHiveCommits.java`

**修改目的**：用 Mockito 与反射模拟「`persistTable` 成功但抛 `UnknownError`，且 `checkCommitStatus` 抛 `OutOfMemoryError`」的场景，验证 metadata 文件不会被误删。

**工作逻辑**：

1. 增加 import：`org.junit.platform.commons.support.ReflectionSupport`、`static org.mockito.ArgumentMatchers.anyString`；
2. 新测试 `testSuccessCommitWhenCheckCommitStatusOOM`：
   - 加载表，做一次 schema update（产生 metadataV2），刷新；
   - 用 `spy(ops)` 创建间谍对象；
   - `doAnswer` 改写 `persistTable`：先调用真实 `ops.persistTable(...)` 让 HMS 真的提交成功，然后抛 `UnknownError`；
   - 用 `ReflectionSupport.invokeMethod` + `doThrow(new OutOfMemoryError()).when(spyOps)` 反射调用父类 `checkCommitStatus` 并让它抛 OOM；
   - 断言 `spyOps.commit(metadataV2, metadataV1)` 抛 `OutOfMemoryError`；
   - `ops.refresh()` 后断言：当前 metadata 的 location 等于 metadataV1 的 location（注意：因为 persistTable 已让 HMS 接受了 metadataV1 这次回滚提交），并且 `metadataFileExists(ops.current())` 为 `true`，证明 metadata 文件没被误删。

反射的层级：`ops.getClass().getSuperclass().getDeclaredMethod("checkCommitStatus", ...)`，因为 `checkCommitStatus` 是父类 `BaseMetastoreTableOperations` 的方法。

### 测试：`TestHiveViewCommits.java`

**修改目的**：为视图侧提供同样的回归测试。

**工作逻辑**：与表测试同构，差别在：

- 操作的是 `HiveViewOperations` 与 `ViewMetadata`；
- 反射取 `checkCommitStatus` 时跨两层父类（`ops.getClass().getSuperclass().getSuperclass()`），因为视图的 `checkCommitStatus` 在 `BaseViewOperations` 中，签名是 `(String, String, Map, Supplier)`；
- 增加 import：`java.util.Map`、`java.util.function.Supplier`、`org.junit.platform.commons.support.ReflectionSupport`、`static org.mockito.ArgumentMatchers.anyString`；
- 先做一次 `view.updateProperties().set("k1", "v1").commit()` 产生 metadataV2；
- 仿造 spy + `doAnswer` 让 `persistTable` 成功后抛 `UnknownError`，再让 `checkCommitStatus` 抛 `OutOfMemoryError`；
- 断言 commit 抛 `OutOfMemoryError`、当前 metadata location 等于 metadataV1 的 location、`metadataFileExists(metadataV2)` 为 true 且 `metadataFileCount(metadataV2) == 2`（即 metadataV2 的两个版本文件都还在）。

## 小结

- **成效**：在 Hive 表/视图提交时，若 `persistTable` 已让 HMS 接受提交但客户端发生 OOM/UnknownError，且随后 `checkCommitStatus` 也抛 OOM，新写入的 metadata 文件不会再被 `finally` 误删，避免了「HMS 指向已删除 metadata」的不可逆数据损坏；最坏情况退化为「遗留孤儿 metadata 文件」，可由后续 remove_orphan_files 清理。
- **影响范围**：仅 hive-metastore 模块两个 ops 类各加一行赋值，及两个测试类各加一个回归用例（约 55 行/类）。修复不改变正常路径行为。
- **回迁到 1.4.x 的注意事项**：1.4.x 的 Hive 集成同样存在该缺陷——OOM 路径下可能误删已成功提交的 metadata 文件，这是潜在的数据损坏风险，**强烈建议回迁**。回迁要点：
  1. `HiveTableOperations` 与 `HiveViewOperations` 在 1.4.x 中的 `doCommit` 结构与 main 基本一致，直接在 `catch (Throwable e)` 内、`checkCommitStatus` 之前加 `commitStatus = UNKNOWN` 即可；
  2. 视图侧若 1.4.x 中 `checkCommitStatus` 的父类层级与 main 不同，需相应调整反射路径（main 是 `getSuperclass().getSuperclass()`）；
  3. 测试用到 `org.junit.platform.commons.support.ReflectionSupport`，确认 1.4.x 测试依赖（junit-platform-commons）可用；Mockito 的 `spy` / `doAnswer` / `doThrow` API 在 1.4.x 通常已具备；
  4. 此修复是「更安全」的语义，不引入兼容性问题，可独立回迁。
