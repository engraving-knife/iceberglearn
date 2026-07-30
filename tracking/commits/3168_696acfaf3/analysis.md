# 提交 3168：API, Spark 4.1: Add `orphanFilesCount` to `DeleteOrphanFiles.Result` (#14886)

## 提交信息

- **序号**：3168 / 4088
- **哈希**：696acfaf3f14dcdee44e101e187318c7cf4bf500
- **短哈希**：696acfaf3
- **日期**：2026-01-27
- **作者**：Alessandro Nori
- **提交说明**：API, Spark 4.1: Add `orphanFilesCount` to `DeleteOrphanFiles.Result` (#14886)
- **PR/Issue**：#14886

## 总体目的

Iceberg 提供 `DeleteOrphanFiles` action 用于清理表中不再被任何快照引用的"孤儿文件"（orphan files，即残留的数据/元数据文件）。该 action 执行后返回一个 `Result` 对象，此前只通过 `orphanFileLocations()` 暴露被处理（删除或识别）的孤儿文件路径列表（`Iterable<String>`）。然而在实际运维场景中，调用方往往只需要知道孤儿文件的数量，而不需要遍历整个路径列表。当孤儿文件数量巨大时，强制通过 `orphanFileLocations()` 返回完整列表并在调用端计数，既浪费内存又增加序列化开销，尤其在 Spark 分布式执行场景下结果需要收集回 driver 时更为明显。

本次提交由 Alessandro Nori（来自 Datadog）通过 PR #14886 引入，目的是在 `DeleteOrphanFiles.Result` 接口中新增一个 `orphanFilesCount()` 方法，直接返回孤儿文件总数（`long`）。这样调用方可以以 O(1) 的代价获取计数，无需遍历整个文件列表。值得注意的是，实现中 `DeleteOrphanFilesSparkAction` 在执行删除时本身就已经维护了一个 `filesCount` 计数器（用于日志输出 `LOG.info("Deleted {} orphan files", filesCount)`），因此将其一并写入 Result 是顺理成章且几乎零成本的。

从接口设计看，新方法以 `default` 方法形式加入 API 接口并默认返回 `0`，保证了向后兼容——现有的其他 `DeleteOrphanFiles` 实现无需立即改造即可编译通过。该改动仅针对 Spark 4.1 模块进行了实现落地与测试覆盖。

## 如何达成设计目的

改动分三层：API 层在 `DeleteOrphanFiles.Result` 接口新增带默认实现的 `orphanFilesCount()`；core 层在 `BaseDeleteOrphanFiles.Result`（Immutables 生成的接口）中覆盖该方法以支持 `@Value.Default`；Spark 4.1 实现层在 `DeleteOrphanFilesSparkAction` 构建 Result 时传入已有的 `filesCount`；测试层在 `TestRemoveOrphanFilesAction` 与其子类 `TestRemoveOrphanFilesAction3` 中为大量既有测试用例补充了对 `orphanFilesCount()` 的断言验证。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/DeleteOrphanFiles.java` (+5/-0 lines)

**修改目的**：在 API 接口的 Result 中声明孤儿文件计数方法。

**工作逻辑**：
在 `Result` 接口中，紧接 `orphanFileLocations()` 之后新增：
```java
/** Returns the total number of orphan files. */
default long orphanFilesCount() {
  return 0;
}
```
使用 `default` 方法并返回 `0`，确保接口向后兼容，不破坏既有实现。API 注释明确其语义为"孤儿文件总数"。

### `core/src/main/java/org/apache/iceberg/actions/BaseDeleteOrphanFiles.java` (+8/-1 lines)

**修改目的**：让 core 层的 Immutable Result 支持设置 orphanFilesCount。

**工作逻辑**：
`BaseDeleteOrphanFiles.Result` 是一个 `@Value.Immutable` 接口，由 Immutables 框架生成 `ImmutableDeleteOrphanFiles.Result` 构造器。原先是空接口 `interface Result extends DeleteOrphanFiles.Result {}`，现在覆盖 `orphanFilesCount()` 并标注 `@Value.Default`，默认仍返回 `0`：
```java
@Override
@Value.Default
default long orphanFilesCount() {
  return 0;
}
```
这使得 Immutables 生成的 builder 暴露 `.orphanFilesCount(long)` 方法，允许实现层在构建 Result 时显式传入计数值；若不设置则默认为 0，保持兼容。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/DeleteOrphanFilesSparkAction.java` (+5/-1 lines)

**修改目的**：在 Spark 4.1 实现中将已统计的 filesCount 写入 Result。

**工作逻辑**：
原构建语句 `ImmutableDeleteOrphanFiles.Result.builder().orphanFileLocations(orphanFileList).build()` 被扩展为追加 `.orphanFilesCount(filesCount)`。`filesCount` 是该 action 在执行删除流程中已维护的计数器（用于 `LOG.info("Deleted {} orphan files", filesCount)`），代表实际删除的孤儿文件数。将其写入 Result 使调用方能直接读取，无需遍历 `orphanFileLocations()`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java` (+45/-0 lines)

**修改目的**：为既有测试用例补充 orphanFilesCount 断言。

**工作逻辑**：
在多个测试方法（涵盖默认 olderThan、dry-run、流式执行、删除隐藏目录外文件、trash 文件清理、stats 文件删除、流式采样等场景）中，紧接既有 `orphanFileLocations()` 断言后新增 `assertThat(result.orphanFilesCount())...isEqualTo(...)` 断言。预期值与 `orphanFileLocations()` 的 size 一致（如 0L、1L、2L、4L、`(long) invalidFiles.size()`、`(long) invalidFilePaths.size()`），验证计数与文件列表同步。其中一个流式采样场景的断言注释为 "Deleted 10 files"（预期 10），即使 `orphanFileLocations()` 因采样仅返回 5 个，但实际删除了全部 10 个，体现了 count 与 locations 语义上的差异——count 反映实际删除总数而非采样返回数。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction3.java` (+5/-0 lines)

**修改目的**：为 Spark 3 专属测试补充 orphanFilesCount 断言。

**工作逻辑**：
在 5 个涉及 trash 文件清理的测试方法中，各新增一行 `assertThat(results.orphanFilesCount()).as("trash file should be removed").isEqualTo(1L);`，与既有 `orphanFileLocations()` 断言对齐，验证每次删除一个 trash 文件时计数为 1。

## 总结

本次提交为 `DeleteOrphanFiles.Result` 新增 `orphanFilesCount()` 方法，使调用方能够直接获取孤儿文件总数而无需遍历文件路径列表，提升了大文件集场景下的效率与易用性。通过 `default` 方法保证向后兼容，在 Spark 4.1 实现中复用已有计数器落地，并为大量既有测试补充了计数断言，确保新 API 的正确性。该增强对 Iceberg 的表维护运维场景具有实际价值。
