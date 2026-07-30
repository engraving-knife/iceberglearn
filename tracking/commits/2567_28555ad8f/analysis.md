# 提交 2567：Core: Parallelize determining of files to cleanup in IncrementalFileCleanup (#13926)

## 提交信息

- **序号**：2567 / 4088
- **哈希**：28555ad8fbad77a4067b6ee2afbdea15428dea26
- **短哈希**：28555ad8f
- **日期**：2025-08-27 20:37:25 -0600
- **作者**：gtrettenero
- **提交说明**：Core: Parallelize determining of files to cleanup in IncrementalFileCleanup (#13926)
- **PR/Issue**：#13926

## 总体目的

该提交对 `IncrementalFileCleanup` 中确定待清理文件的过程进行并行化优化，提升快照过期后文件清理的性能。`IncrementalFileCleanup` 是 Iceberg 核心模块中处理增量文件清理的策略类，负责在快照过期后识别和删除不再被引用的 manifest 文件、manifest list 和数据文件。

此前，清理过程中有两个关键的 `Tasks.foreach` 循环是串行执行的：
1. 遍历所有快照（snapshots），查找仍被有效快照引用但由过期快照写入的 manifest（`validManifests` 和 `manifestsToScan`）。
2. 遍历过期前的快照（`beforeExpiration.snapshots()`），查找仅被过期快照引用的 manifest（`manifestListsToDelete`、`manifestsToDelete`、`manifestsToRevert`）。

这些遍历操作涉及读取 manifest 文件内容，是 I/O 密集型操作。当表有大量快照和 manifest 时，串行遍历会成为性能瓶颈。该提交通过使用 `planExecutorService` 并行执行这些任务，并使用线程安全的集合类型来支持并发写入。

## 如何达成设计目的

- 将两个 `Tasks.foreach` 循环中的普通 `HashSet`（`Sets.newHashSet()`）替换为 `ConcurrentHashMap.newKeySet()`，支持并发写入。
- 在两个 `Tasks.foreach` 中添加 `.executeWith(planExecutorService)`，使用计划线程池并行执行任务。
- `planExecutorService` 是 `FileCleanupStrategy` 基类中已存在的线程池，用于并行执行计划任务。

## 修改详情

### `core/src/main/java/org/apache/iceberg/IncrementalFileCleanup.java` (+7/-5)

**修改目的**：并行化文件清理的确定过程。

**工作逻辑**：
- 第一处 `Tasks.foreach(snapshots)`：将 `validManifests` 和 `manifestsToScan` 从 `Sets.newHashSet()` 改为 `ConcurrentHashMap.newKeySet()`，添加 `.executeWith(planExecutorService)` 使遍历并行执行。
- 第二处 `Tasks.foreach(beforeExpiration.snapshots())`：将 `manifestListsToDelete`、`manifestsToDelete`、`manifestsToRevert` 从 `Sets.newHashSet()` 改为 `ConcurrentHashMap.newKeySet()`，添加 `.executeWith(planExecutorService)` 使遍历并行执行。

## 总结

该提交通过将 `IncrementalFileCleanup` 中确定待清理文件的两个串行遍历改为并行执行，提升了快照过期后文件清理的性能。使用 `ConcurrentHashMap.newKeySet()` 替代 `HashSet` 保证线程安全，通过 `planExecutorService` 实现并行执行。修改仅涉及 1 个文件，7 行新增 5 行删除。
