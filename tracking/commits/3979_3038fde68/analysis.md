# 提交 3979：Core: Fix thread-unsafe duplicate delete handling in ManifestFilterManager (#16686)

## 提交信息

- **序号**：3979 / 4088
- **哈希**：3038fde68307bb5bc95de256670cb0b38ed6de71
- **短哈希**：3038fde68
- **日期**：2026-07-03 08:27:28 -0600
- **作者**：Zehua Zou
- **提交说明**：Core: Fix thread-unsafe duplicate delete handling in ManifestFilterManager (#16686)
- **PR/Issue**：#16686

## 总体目的

本提交修复了 `ManifestFilterManager` 中重复删除（duplicate delete）计数的线程安全问题。此前，`duplicateDeleteCount` 是一个实例级别的 `int` 字段，在多线程并发过滤 manifest 时会被多个线程同时递增，导致竞态条件（非原子操作）。

此外，`deleteFiles` 集合在 manifest 过滤过程中被直接添加（`deleteFiles.add(fileCopy)`），但 `deleteFiles` 是一个共享集合，在多线程环境下添加操作虽然 `ConcurrentHashMap` 支持但时序不正确——应该在过滤完成后统一添加，而非在过滤过程中逐条添加。

还有一个问题：`filteredManifestToDeletedFiles` 缓存存储的是 `Iterable<F>`，但 duplicate count 是全局的，当多个 manifest 被过滤时，count 无法正确归属到各个 manifest。这导致在构建 snapshot summary 时，duplicate delete count 被错误地全局累加，而非按 manifest 分配。

## 如何达成设计目的

1. 将 `duplicateDeleteCount` 从实例字段改为方法内的 `AtomicInteger` 局部变量，确保每个 manifest 过滤有自己的计数。
2. 将 `filteredManifestToDeletedFiles`（存储 `Iterable<F>`）改为 `filteredManifestResults`（存储 `Pair<Set<F>, Integer>`），将每个 manifest 的删除文件集合和 duplicate count 一起缓存。
3. 在构建 summary 时，从每个 manifest 的 result 中分别取出 deleted files 和 duplicate count，而非使用全局 count。
4. 在 `filterManifests` 完成后统一将 `deletedFiles` 添加到 `deleteFiles` 集合。
5. 在 manifest 过滤方法中，不再在过滤过程中直接添加到 `deleteFiles`，而是仅在完成后添加。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java` (+31/-17 lines)

**修改目的**：修复线程安全的 duplicate delete 计数。

**工作逻辑**：
- 移除实例字段 `private int duplicateDeleteCount = 0`。
- 将缓存类型从 `Map<ManifestFile, Iterable<F>>` 改为 `Map<ManifestFile, Pair<Set<F>, Integer>>`。
- 在 `filterManifests` 中新增 `deleteFiles.addAll(deletedFiles(filtered))` 统一收集。
- 在 `buildSummary` 中，从每个 manifest 的 result pair 中分别取出 deleted files 和 duplicate count：
```java
Pair<Set<F>, Integer> result = filteredManifestResults.get(manifest);
if (result != null) {
  for (F file : result.first()) {
    summaryBuilder.deletedFile(manifestSpec, file);
  }
  summaryBuilder.incrementDuplicateDeletes(result.second());
}
```
- 在 manifest 过滤方法中，`AtomicInteger duplicateDeleteCount = new AtomicInteger(0)` 作为局部变量，通过 `incrementAndGet()` 原子递增。
- 缓存写入：`filteredManifestResults.put(filtered, Pair.of(deletedFiles, duplicateDeleteCount.get()))`。
- 移除了过滤过程中对 `deleteFiles.add(fileCopy)` 的直接调用。

### `core/src/test/java/org/apache/iceberg/TestSnapshotSummary.java` (+47/-0 lines)

**修改目的**：验证并发删除重试场景下的正确性。

**工作逻辑**：新增 `deleteDuplicateFilesWithConcurrentDeleteRetry` 测试，通过自定义 `TestTableOperations` 注入一个 `CommitFailedException`，在 commit 失败前模拟一个并发删除操作（`deleteFromRowFilter(Expressions.alwaysTrue())`）。验证最终 snapshot summary 不包含 `DELETED_DUPLICATE_FILES` 和 `DELETED_FILES_PROP` 键（因为并发删除已经处理了这些文件）。

## 总结

本提交修复了 `ManifestFilterManager` 中三个相关的线程安全问题：非原子的 duplicate count 递增、过滤过程中不安全的 `deleteFiles` 添加、以及全局 count 无法按 manifest 归属。修复方案通过将 count 改为局部 `AtomicInteger` 并与 deleted files 一起缓存在 Pair 中，确保了线程安全和正确的 summary 统计。这是一个重要的并发正确性修复。
