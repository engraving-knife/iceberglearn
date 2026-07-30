# 提交 2427：Core: Fix incorrect selection of incremental cleanup in expire snapshots (#13614)

## 提交信息

- **序号**：2427 / 4088
- **哈希**：f7e6a2775bd5a9e202465e6281c56d8c41fd7f0e
- **短哈希**：f7e6a2775
- **日期**：2025-07-28 12:21:20 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Fix incorrect selection of incremental cleanup in expire snapshots (#13614)
- **PR/Issue**：#13614

## 总体目的

本提交修复了 Iceberg 快照过期（expire snapshots）操作中增量清理（incremental cleanup）选择逻辑的一个缺陷。

Iceberg 在过期快照时有两种文件清理策略：
- **增量清理（Incremental cleanup）**：只清理被过期快照与当前快照之间的差异文件，效率高但适用范围有限
- **全量清理（Full cleanup）**：扫描所有快照，清理不再被任何快照引用的文件，更安全但开销更大

此前，是否使用增量清理的判断逻辑存在缺陷：仅基于 `refs().size() == 1`（表中只有一个 ref）来决定。这个条件不充分，因为即使只有一个 ref（main 分支），表中仍可能存在不在 main 分支祖先链上的快照（如从已删除分支留下的孤儿快照、失败提交的快照等）。增量清理只处理 main 分支祖先链上的快照，会遗漏清理非 main 链上快照的文件，导致文件泄漏。

本提交引入了更精确的判断条件：检查是否有非 main 祖先链上的快照被移除，以及当前是否还存在非 main 链上的快照。

## 如何达成设计目的

1. 新增 `hasRemovedNonMainAncestors` 方法：比较过期前后的表元数据，检查是否有不在 main 分支祖先链上的快照被移除
2. 新增 `hasNonMainSnapshots` 方法：检查当前表元数据中是否存在不在 main 分支祖先链上的快照
3. 新增 `mainAncestors` 辅助方法：获取当前快照的所有祖先快照 ID 集合
4. 修改 `cleanExpiredSnapshots` 中的增量清理选择逻辑：
   - 当显式请求增量清理时（`incrementalCleanup == true`），调用 `validateCleanupCanBeIncremental` 验证条件，不满足则抛出异常
   - 当自动选择时（`incrementalCleanup == null`），只有在未指定特定快照 ID、无非 main 祖先被移除、无非 main 快照存在时才使用增量清理
5. 修改 `IncrementalFileCleanup`：移除了对 `refs().size() > 1` 的异常检查，改为直接使用 `currentSnapshot()` 获取最新快照
6. 在 `commit` 方法中增加 `!base.snapshots().isEmpty()` 检查，避免在无快照时执行清理

## 修改详情

### `core/src/main/java/org/apache/iceberg/IncrementalFileCleanup.java` (+3/-8 lines)

**修改目的**：移除对多 ref 的限制，改为直接使用当前快照。

**工作逻辑**：
- 移除了 `afterExpiration.refs().size() > 1` 时抛出 `UnsupportedOperationException` 的检查
- 将原来通过 `Iterables.getFirst(beforeExpiration.refs().values(), null)` 获取第一个 ref 再获取其快照的方式，改为直接使用 `beforeExpiration.currentSnapshot()`
- 移除了不再需要的 `Iterables` 导入

### `core/src/main/java/org/apache/iceberg/RemoveSnapshots.java` (+83/-8 lines)

**修改目的**：修复增量清理的选择逻辑，确保只在安全条件下使用增量清理。

**工作逻辑**：
- `commit` 方法：在 `cleanExpiredFiles` 条件中增加 `!base.snapshots().isEmpty()` 检查，避免在无快照时执行清理
- `cleanExpiredSnapshots` 方法：重写增量清理选择逻辑
  - 当 `incrementalCleanup` 为 `true` 时，调用 `validateCleanupCanBeIncremental` 进行验证（检查是否指定了快照 ID、是否有非 main 祖先被移除、是否有非 main 快照存在）
  - 当 `incrementalCleanup` 为 `null` 时，自动判断：只有在未指定快照 ID、无非 main 祖先被移除、无非 main 快照存在时才使用增量清理
- 新增 `validateCleanupCanBeIncremental` 方法：在显式请求增量清理但不满足条件时抛出 `UnsupportedOperationException`
- 新增 `hasRemovedNonMainAncestors` 方法：遍历过期前的所有快照，检查是否有不在 main 祖先链上且被移除的快照
- 新增 `hasNonMainSnapshots` 方法：检查当前表元数据中是否有不在 main 祖先链上的快照
- 新增 `mainAncestors` 方法：使用 `SnapshotUtil.ancestorsOf` 获取当前快照的所有祖先 ID 集合

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java` (+134/-8 lines)

**修改目的**：为修复后的逻辑添加测试。

**工作逻辑**：新增多个测试用例验证不同场景下的增量清理行为，包括存在非 main 快照时不应使用增量清理、显式请求增量清理时的验证逻辑等。

## 总结

本提交修复了快照过期操作中增量清理选择逻辑的一个关键缺陷。原逻辑仅基于 ref 数量判断，可能在存在非 main 链快照时错误地选择增量清理，导致文件泄漏。新逻辑通过检查 main 分支祖先链来精确判断是否可以安全使用增量清理，确保所有被过期快照引用的文件都能被正确清理。
