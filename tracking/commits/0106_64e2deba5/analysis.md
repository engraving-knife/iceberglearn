# 提交 0106：Core: Fix NPE when calling InMemoryLockManager#release using Hadoop catalog (#8494)

## 提交信息

- **序号**：0106 / 4088
- **哈希**：64e2deba53f696882f517a6010b81d83b55389b7
- **短哈希**：64e2deba5
- **日期**：2023-10-30 14:33:48 +0100
- **作者**：wangtaohz
- **提交说明**：Core: Fix NPE when calling InMemoryLockManager#release using Hadoop catalog (#8494)
- **PR/Issue**：#8494

## 总体目的

这个提交修复了使用 Hadoop catalog 时，调用 `InMemoryLockManager#release` 抛出 `NullPointerException` 的缺陷。

Iceberg 的 `InMemoryLockManager` 是一个基于进程内并发 Map 的锁管理器实现，仅适用于测试或单 JVM 内的提交互斥。它在内部维护两个静态 Map：`LOCKS`（保存锁实体内容）和 `HEARTBEATS`（保存锁对应的定时心跳任务 `ScheduledFuture`）。`release(entityId, ownerId)` 方法在校验持有者后会执行 `HEARTBEATS.remove(entityId).cancel(false)` 来取消心跳任务并移除锁条目。

问题在于：`HEARTBEATS.remove(entityId)` 在 `entityId` 不在 `HEARTBEATS` Map 中时返回 `null`，随后对 `null` 调用 `.cancel(false)` 就会抛出 NPE。这种情况在使用 Hadoop catalog 时会发生。

具体到 Hadoop catalog 的调用路径：`HadoopTableOperations#renameToFinal` 在 `finally` 块中无条件调用 `lockManager.release(dst.toString(), src.toString())`，即使 `lockManager.acquire(...)` 未能成功获取锁（例如 `acquireOnce` 抛出 `IllegalStateException` 导致 `acquire` 返回 `false`）。当 `acquire` 失败时，`acquireOnce` 不会执行成功分支里的 `HEARTBEATS.put(...)`，因此 `HEARTBEATS` 中不存在该 `entityId`，后续 `release` 里 `HEARTBEATS.remove(entityId)` 返回 `null`，导致 NPE。这个 NPE 会掩盖原本的提交失败原因，干扰问题定位。

这个修复对 Iceberg 的健壮性有正向意义：它确保 `release` 在锁从未被成功获取（或心跳未注册）时也能安全返回，而不会用 NPE 掩盖真实的提交失败。

## 如何达成设计目的

整体思路非常直接：将 `HEARTBEATS.remove(entityId).cancel(false)` 改为对 `remove` 返回值做 null 判断后再调用 `cancel`。具体使用 `Optional.ofNullable(HEARTBEATS.remove(entityId)).ifPresent(future -> future.cancel(false))`，这样无论 `entityId` 是否存在于 `HEARTBEATS` 中都不会抛 NPE，且当确实存在心跳任务时仍然会正确取消。改动只涉及一处代码，不改变方法签名、不影响锁的语义，仅修正了边界情况下的空指针行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/LockManagers.java`

**修改目的**：修复 `InMemoryLockManager#release` 在 `HEARTBEATS` 中不存在对应 `entityId` 时抛出 NPE 的问题。

**工作逻辑**：

- 新增 `import java.util.Optional;`。
- 在 `release(String entityId, String ownerId)` 方法中（位于 [LockManagers.java:347](../core/src/main/java/org/apache/iceberg/util/LockManagers.java)），将原来的：

  ```java
  HEARTBEATS.remove(entityId).cancel(false);
  ```

  改为：

  ```java
  Optional.ofNullable(HEARTBEATS.remove(entityId)).ifPresent(future -> future.cancel(false));
  ```

  原代码假设 `entityId` 一定在 `HEARTBEATS` 中（即 `acquire` 一定成功并注册了心跳），但这在使用 Hadoop catalog 时并不成立——`HadoopTableOperations#renameToFinal` 的 `finally` 块会无条件调用 `release`，即使 `acquire` 失败。`HEARTBEATS.remove(entityId)` 在缺失时返回 `null`，直接 `.cancel(false)` 即抛 NPE。

  修改后通过 `Optional.ofNullable(...)` 包裹 `remove` 的返回值，仅当存在（非 null）时才执行 `cancel(false)`。这与 `acquireOnce` 中已存在的防御性写法 `if (HEARTBEATS.containsKey(entityId)) { HEARTBEATS.remove(entityId).cancel(false); }` 思路一致，但 `release` 这里更进一步用 `Optional` 简化了对 `remove` 返回值的处理，避免了 `containsKey` + `remove` 两次访问的轻微竞态（虽然在此场景下问题不大）。

  紧随其后的 `LOCKS.remove(entityId)` 保持不变，确保锁实体本身仍被正确移除。

## 小结

这个一行级的修复消除了 Hadoop catalog 提交路径上 `InMemoryLockManager#release` 的空指针缺陷，使锁释放逻辑在“锁未成功获取”的边界场景下也能安全执行，不再以 NPE 掩盖真实的提交失败原因，提升了 Iceberg 提交链路的健壮性。
