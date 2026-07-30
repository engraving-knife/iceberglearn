# 提交 1367：Core: Support commits with DVs (#11495)

## 提交信息

- **序号**：1367 / 4088
- **哈希**：af3fbfe0786f9016f9cc645a80d0925c7334eb33
- **短哈希**：af3fbfe07
- **日期**：2024-11-11（Mon Nov 11 22:08:23 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Support commits with DVs (#11495)
- **PR/Issue**：#11495

## 总体目的

Deletion Vector（DV，删除向量）是 Iceberg v3 规范引入的新删除编码方式。与 v2 的 position delete file（逐行列出被删行号，Avro 格式）不同，DV 用紧凑的位图（bitmap）编码被删行号，存储在 Puffin 文件中，并通过 `referencedDataFile` 显式关联到某个数据文件。DV 相比传统 position delete 的优势：体积更小、读取更快（按位操作）、与数据文件一一对应便于增量维护。

随着 v3 规范推进，Iceberg core 需要在提交链路（commit pipeline）中支持 DV：

1. **格式版本约束**：v2 表必须继续使用传统 position delete file（不能混用 DV）；v3 表的 position delete 必须使用 DV（不再接受传统 position delete file）。equality delete 在两个版本中均可使用。需要在校验链路中强制这些约束，防止用户在 v2 表上提交 DV 或在 v3 表上提交旧式 position delete；
2. **并发冲突检测**：DV 与数据文件一一对应。如果两个并发 RowDelta 提交都为同一个数据文件添加 DV，就会产生冲突（两个 DV 都声称"修改了同一数据文件的删除状态"），后者提交时必须检测到冲突并失败。这类似于已有的 `validateNoNewDeleteFiles`（检测并发新增 delete file 冲突），但粒度更细——针对 DV 的 `referencedDataFile` 而非整个分区。

本提交在 `MergingSnapshotProducer`（合并型快照生产者，RowDelta/OverwriteFiles/DeleteFiles 等的基类）与 `BaseRowDelta`（RowDelta 实现）中补齐 DV 提交支持：新增 `newDVRefs` 跟踪本操作新增的 DV 引用的数据文件、`validateNewDeleteFile` 按格式版本校验删除文件类型、`validateAddedDVs` 检测并发 DV 冲突。

## 如何达成设计目的

通过三层改动实现：

1. **删除文件格式校验**（`validateNewDeleteFile`）：在 `add(DeleteFile)` 入口按 `formatVersion()` 校验——v1 拒绝所有删除文件、v2 拒绝 DV 格式的 position delete、v3 拒绝非 DV 格式的 position delete；equality delete 两版本均允许；
2. **DV 引用跟踪**（`newDVRefs`）：`add` 删除文件到 `newDeleteFilesBySpec` 时，若 `ContentFileUtil.isDV(file)` 为真，把 `file.referencedDataFile()` 记入 `newDVRefs` 集合，供后续冲突检测使用；
3. **并发 DV 冲突检测**（`validateAddedDVs`）：在 `BaseRowDelta.validate()` 中，已有的 `validateNoNewDeleteFiles` 之后调用 `validateAddedDVs`——读取 starting snapshot 到 parent snapshot 之间的 delete manifests，检查是否有并发提交也为 `newDVRefs` 中的同一数据文件添加了 DV，若有则抛 `ValidationException`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`（修改）

**修改目的**：DV 引用跟踪、格式校验、并发冲突检测。

**工作逻辑**：

- **新增 `newDVRefs` 字段**：`private final Set<String> newDVRefs = Sets.newHashSet()`，跟踪本操作新增 DV 引用的数据文件路径。
- **新增 `VALIDATE_ADDED_DVS_OPERATIONS`**：`ImmutableSet.of(DataOperations.OVERWRITE, DataOperations.DELETE, DataOperations.REPLACE)`——DV 可在 overwrite/delete/replace 操作中添加，比 delete file 的操作集合多了 REPLACE。
- **`add(DeleteFile)` / `add(DeleteFile, long)` 改用 `validateNewDeleteFile`**：原来只做 `Preconditions.checkNotNull`，现在委托新方法做格式版本校验。
- **`add(PendingDeleteFile)` 内增加 DV 跟踪**：当 `ContentFileUtil.isDV(file)` 为真时，`newDVRefs.add(file.referencedDataFile())`。
- **新增 `validateNewDeleteFile(DeleteFile)`**：按 `formatVersion()` switch：
  - v1：抛 `"Deletes are supported in V2 and above"`；
  - v2：`file.content() == EQUALITY_DELETES || !isDV(file)`——v2 的 position delete 不能是 DV；
  - v3：`file.content() == EQUALITY_DELETES || isDV(file)`——v3 的 position delete 必须是 DV；
  - 默认：抛 unsupported。
- **新增 `validateAddedDVs(base, startingSnapshotId, conflictDetectionFilter, parent)`**：
  - 若 `parent == null` 或 `newDVRefs.isEmpty()`，跳过（无并发风险）；
  - 调用既有 `validationHistory(...)` 取 starting→parent 之间的新 delete manifests 与 snapshot IDs（操作集合用 `VALIDATE_ADDED_DVS_OPERATIONS`）；
  - 用 `Tasks.foreach(newDeleteManifests).executeWith(workerPool())` 并行读 manifest；
  - 对每个 entry，若 `entry.snapshotId` 属于新 snapshot 集合且 `isDV(file)`，检查 `newDVRefs.contains(file.referencedDataFile())`——若包含说明并发提交也为同一数据文件添加了 DV，抛 `ValidationException`。
- **新增 `formatVersion()`**：`return ops.current().formatVersion()`。

### `core/src/main/java/org/apache/iceberg/BaseRowDelta.java`（修改）

**修改目的**：在 RowDelta 校验链路中触发 DV 冲突检测。

**工作逻辑**：在 `validate()` 方法中，已有的 `validateNoNewDeleteFiles` 之后新增 `validateAddedDVs(base, startingSnapshotId, conflictDetectionFilter, parent)` 调用。这样 RowDelta 提交时既检测并发新增 delete file 冲突，也检测并发新增 DV 冲突。

### `core/src/test/java/org/apache/iceberg/TestBase.java`（修改）

**修改目的**：测试基础设施支持 DV。

**工作逻辑**：
- 新增 `FILE_A_DV` / `FILE_B_DV` 静态常量：DV 格式的 position delete 文件（`.puffin` 扩展名，带 `referencedDataFile`/`contentOffset`/`contentSizeInBytes`）；
- 新增 `fileADeletes()` / `fileBDeletes()` / `newDeletes(DataFile)` 辅助方法：`formatVersion >= 3` 时返回 DV，否则返回传统 position delete file。使测试代码一套逻辑同时覆盖 v2/v3。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java`（修改，主要测试文件）

**修改目的**：扩展 RowDelta 测试覆盖 v3 + DV。

**工作逻辑**：
- `parameters()` 新增 v3 参数（`{3, "main"}`, `{3, "testBranch"}`），使全部测试在 v2/v3 两个版本下运行；
- 既有测试把硬编码的 `FILE_A_DELETES`/`FILE_B_DELETES` 替换为 `fileADeletes()`/`fileBDeletes()`，按版本自动选 DV 或传统 position delete；
- 新增多个 DV 专属测试：验证 v3 上 position delete 必须是 DV、v2 上不能使用 DV、并发 DV 冲突检测等场景。

### 其余测试文件（修改）

**修改目的**：适配 DV 测试基础设施。

- `DeleteFileIndexTestBase`、`ScanPlanningAndReportingTestBase`、`TestCommitReporting`、`TestMetadataTableScans`、`TestRewriteManifests`、`TestSnapshot`、`TestSnapshotSummary` 等：把硬编码的 position delete 常量替换为按版本选择的 helper 方法，或调整断言以适应 DV 格式差异（如 `.puffin` 扩展名、`referencedDataFile` 字段）。
- `TestRewriteFiles`、`io/TestDVWriters`：增加 DV 相关测试覆盖。
- Spark 测试（`TestSparkDistributedDataScanDeletes` 等）与 `TestRewriteManifestsAction`：适配 DV。

## 小结

- **成效**：补齐 Iceberg v3 提交链路对 Deletion Vector 的支持——v2 表拒绝 DV、v3 表强制 position delete 使用 DV、并发提交为同一数据文件添加 DV 时检测冲突并失败。这是 v3 规范落地的核心一环，使 RowDelta 等操作能正确处理 DV 格式的删除文件，保障乐观并发控制的正确性。
- **影响范围**：核心 `MergingSnapshotProducer`/`BaseRowDelta` 两个生产类改动，涉及所有继承 `MergingSnapshotProducer` 的写入操作（RowDelta/OverwriteFiles/DeleteFiles/ReplacePartitions 等）的删除文件校验路径。测试基础设施（`TestBase` 等）全面适配 v3+DV，大量测试新增 v3 参数化运行。新增 506 行、删除 212 行。
- **回迁到 1.4.x 的注意事项**：本提交依赖 v3 规范支持（`ContentFileUtil.isDV`、`DeleteFile.referencedDataFile`、`FileContent`、Puffin 格式等），1.4.x 上若 v3 支持尚未完整回迁则无法直接 cherry-pick。需先确认 1.4.x 是否已有 `ContentFileUtil.isDV`、`FileMetadata.withReferencedDataFile/withContentOffset/withContentSizeInBytes`、`FileGenerationUtil.generateDV` 等 API——这些都是 v3 DV 基础设施，可能需要先回迁前置 PR。`validateNewDeleteFile` 中 v2 拒绝 DV、v3 强制 DV 的约束是行为变更，回迁后 1.4.x 用户若此前在 v3 表上使用了传统 position delete（非 DV），提交将被拒绝——需评估是否有此类用户。并发 DV 冲突检测（`validateAddedDVs`）是新增校验，可能使此前能成功的并发提交变为失败，属正确性增强但需注意行为变化。
