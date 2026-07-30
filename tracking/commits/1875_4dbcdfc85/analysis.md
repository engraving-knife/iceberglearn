# 提交 1875：Core, Spark 3.5: Apply Ignore Residuals to Delete Filtering (#12479)

## 提交信息

- **序号**：1875 / 4088
- **哈希**：4dbcdfc85a64dc1d97d7434e353c7f9e4c18e1b3
- **短哈希**：4dbcdfc85
- **日期**：2025-03-18 22:42:19 -0500
- **作者**：Russell Spitzer
- **提交说明**：Core, Spark 3.5: Apply Ignore Residuals to Delete Filtering (#12479)
- **PR/Issue**：#12479

## 总体目的

本提交修复 `DeleteFileIndex` 在构建时未考虑 `ignoreResiduals` 设置的问题，导致即使扫描请求忽略 residuals（即不在读取侧应用行级过滤），delete 文件仍会被行级过滤条件错误地过滤掉。

背景：Iceberg 扫描支持 `ignoreResiduals` 选项，表示扫描器不在读取侧应用行级过滤条件（residual expression），只依赖分区裁剪和 manifest 级别的统计信息过滤。这在 Spark 等引擎中很常见，因为引擎会在后续阶段自行处理行级过滤。

问题在于，`DeleteFileIndex.Builder` 在读取 delete manifest 时，会使用 `dataFilter`（用户行级过滤条件）来过滤 delete 文件条目（`filterRows(dataFilter)`）。当 `ignoreResiduals` 为 true 时，这意味着 delete 文件本应保留（因为读取侧不会应用行级过滤，需要 delete 文件覆盖所有可能的行），但实际却被 `dataFilter` 过滤掉了。这会导致部分 delete 文件丢失，进而导致已删除的行在查询结果中重新出现（正确性问题）。

本提交通过在 `DeleteFileIndex.Builder` 中新增 `ignoreResiduals` 标志，当其启用时将 delete 文件条目的行级过滤条件改为 `alwaysTrue()`（即不过滤），同时保留分区级裁剪（partitionFilter 仍生效，但用 inclusive projection 而非 dataFilter 本身）。在 `ManifestGroup.ignoreResiduals()` 中将该标志传递给 deleteIndexBuilder。

## 如何达成设计目的

整体设计思路：

1. **DeleteFileIndex.Builder 新增 ignoreResiduals 标志**：
   - 新增字段 `private boolean ignoreResiduals = false;` 和 setter `ignoreResiduals()`。
   - 在 `deleteManifestReaders()` 中，根据 `ignoreResiduals` 决定 delete 文件条目的行级过滤条件 `entryFilter`：若 ignoreResiduals 为 true 则用 `Expressions.alwaysTrue()`，否则用原 `dataFilter`。
   - 引入 `partExprCache`（LoadingCache）缓存每个 spec 的 `Projections.inclusive(spec, caseSensitive).project(dataFilter)` 结果，用于 manifest 级别的分区过滤。这是为了分离"manifest 级分区过滤"（始终基于 dataFilter 的 inclusive projection，用于裁剪 manifest）和"条目级行过滤"（受 ignoreResiduals 控制）。
   - `ManifestEvaluator`（manifest 级过滤）改为使用 `partExprCache.get(specId)`（dataFilter 的 inclusive projection）与 `partitionFilter` 的 AND，而非原先直接用 `dataFilter` 的 inclusive projection。
   - 读取 manifest 时，`filterRows(entryFilter)`（受 ignoreResiduals 控制），`filterPartitions(Expressions.and(partitionFilter, partExprCache.get(manifest.partitionSpecId())))`（manifest 内分区裁剪，始终基于 dataFilter projection）再加 `filterPartitions(partitionSet)`。

2. **ManifestGroup 传递标志**：`ManifestGroup.ignoreResiduals()` 中增加 `deleteIndexBuilder.ignoreResiduals();`。

3. **测试**：在 `TestDataFileIndexStatsFilters` 中新增三个测试验证行为；在 `TestCopyOnWriteDelete` 中新增端到端测试验证 equality delete 在 COW delete 场景下的保留。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java` (修改, +22/-6 lines)

**修改目的**：使 DeleteFileIndex 在 ignoreResiduals 时不过滤 delete 文件条目。

**工作逻辑**：
- Builder 新增 `ignoreResiduals` 字段和 setter 方法。
- `deleteManifestReaders()` 中：
  - `entryFilter = ignoreResiduals ? Expressions.alwaysTrue() : dataFilter;` —— 条目级行过滤。
  - 新增 `partExprCache` 缓存 `Projections.inclusive(spec, caseSensitive).project(dataFilter)`，用于 manifest 级和条目级分区裁剪。
  - `ManifestEvaluator.forPartitionFilter` 改为 `Expressions.and(partitionFilter, partExprCache.get(specId))`。
  - 读取 manifest 时 `filterRows(entryFilter)`，`filterPartitions(Expressions.and(partitionFilter, partExprCache.get(manifest.partitionSpecId())))` 再 `filterPartitions(partitionSet)`。
- 这样，ignoreResiduals 时 delete 条目不会被 dataFilter 过滤掉，但分区裁剪仍生效（避免读取完全不相关的分区）。

### `core/src/main/java/org/apache/iceberg/ManifestGroup.java` (修改, +1/-0 lines)

**修改目的**：将 ignoreResiduals 标志传递给 deleteIndexBuilder。

**工作逻辑**：在 `ignoreResiduals()` 方法中新增 `deleteIndexBuilder.ignoreResiduals();`。

### `data/src/test/java/org/apache/iceberg/data/FileHelpers.java` (修改, +7/-1 lines)

**修改目的**：扩展测试辅助方法支持带分区的数据文件写入。

**工作逻辑**：`writeDataFile(Table, OutputFile, List<Record>)` 重载委托新方法 `writeDataFile(Table, OutputFile, List<Record>, PartitionData partition)`，后者将 partition 传给 `factory.newDataWriter`（原来传 null）。

### `data/src/test/java/org/apache/iceberg/data/TestDataFileIndexStatsFilters.java` (修改, +147/-5 lines)

**修改目的**：验证 ignoreResiduals 对 delete 文件过滤的影响。

**工作逻辑**：新增三个测试：
- `testEqualityDeletePlanningStatsUserFilter`：用 `data >= "d"` 过滤，delete 文件只含 a/b/c，正常情况下 delete 文件被过滤掉（task.deletes 为空）。
- `testEqualityDeletePlanningStatsUserFilterIgnoreResidual`：同上但 `ignoreResiduals()`，delete 文件保留（task.deletes 有 1 个）。
- `testEqualityDeletePlanningStatsPartitionPruningIgnoreResidual`：分区表（category even/odd），用 `category = "even"` 过滤 + ignoreResiduals。验证：只产生 even 分区的 1 个 task，task 有 1 个 delete 文件；scan metrics 显示 2 个 delete manifest 中跳过 1 个（只含 odd delete 的），scanned 1 个 equality delete file，skipped 1 个 delete file entry（odd 的）。证明分区裁剪仍生效但行级过滤被忽略。

### `spark/v3.5/build.gradle` (修改, +1/-0 lines)

**修改目的**：让 spark 3.5 extensions 测试依赖 iceberg-data 的 testArtifacts，以使用 FileHelpers。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCopyOnWriteDelete.java` (修改, +37/-6 lines)

**修改目的**：端到端验证 equality delete 在 COW delete 场景下被正确保留。

**工作逻辑**：新增 `testEqualityDeletePreservation` 测试：创建分区表，插入 3 行 hr 部门数据，写入一个 equality delete（删除 id=2），验证查询只剩 id 1 和 3。然后执行 COW `DELETE FROM ... WHERE id = 3`，验证只剩 id 1。这验证了 Spark 在 COW delete 时（会 ignoreResiduals）不会错误丢弃已有的 equality delete。

## 总结

本提交修复了 `DeleteFileIndex` 未考虑 `ignoreResiduals` 导致 delete 文件被行级过滤条件错误丢弃的正确性问题。通过在 Builder 中新增标志，在 ignoreResiduals 时将条目级行过滤改为 alwaysTrue，同时保留基于 dataFilter inclusive projection 的分区裁剪。ManifestGroup 将标志传递给 deleteIndexBuilder。新增多个单元测试和 Spark 端到端测试覆盖该场景。
