# 提交 1888：Use correct statistics file in SparkScan::estimateStatistics(Snapshot) (#12482)

## 提交信息

- **序号**：1888 / 4088
- **哈希**：ff5004ef6d88c1fb7d985fbab818ff9e6bcc4c3f
- **短哈希**：ff5004ef6
- **日期**：2025-03-20 06:46:10 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Use correct statistics file in SparkScan::estimateStatistics(Snapshot) (#12482)
- **PR/Issue**：#12482

## 总体目的

本提交修复 `SparkScan.estimateStatistics(Snapshot)` 方法在表存在多个 statistics 文件时，错误地使用第一个 statistics 文件而非与目标 snapshot 匹配的 statistics 文件的 bug。

背景：`Table.statisticsFiles()` 返回一个 List，包含表上所有 snapshot 的 statistics 文件。当表经历多次提交（每次提交产生新 snapshot）并各自附加了 statistics 文件后，该列表会有多个条目。

原代码在 `estimateStatistics(Snapshot)` 中直接使用 `files.get(0)` 获取 statistics 文件，这意味着：
1. 如果目标 snapshot 不是第一个 statistics 文件对应的 snapshot，会使用错误的统计信息。
2. 随着表增长，统计信息可能严重不匹配，导致 Spark CBO（基于代价的优化）做出错误的查询计划决策。

本提交通过按 `snapshotId` 过滤，找到与目标 snapshot 匹配的 `StatisticsFile`，确保使用正确的统计信息。若找不到匹配的 statistics 文件则不报告列统计。

## 如何达成设计目的

1. **按 snapshotId 过滤**：将 `files.get(0)` 替换为 `files.stream().filter(f -> f.snapshotId() == snapshot.snapshotId()).findFirst()`，获取 `Optional<StatisticsFile>`。

2. **条件处理**：若 `file.isPresent()` 则使用该文件的 blobMetadata 构建列统计映射；若不存在则跳过（colStatsMap 保持为空 Map）。

3. **测试增强**：原测试只创建一个 snapshot 和一个 statistics 文件，无法暴露此 bug。修改后测试创建两个 snapshot（通过追加数据产生第二个），各自附加不同的 statistics 文件（ndv 分别为 4 和 6），验证 `estimateStatistics` 使用与当前 snapshot 匹配的 statistics 文件（ndv=6）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java` (修改, +5/-2 lines)

**修改目的**：按 snapshotId 选择正确的 statistics 文件。

**工作逻辑**：
- 导入 `java.util.Optional`。
- `estimateStatistics(Snapshot)` 方法中：
  - 原：`List<StatisticsFile> files = table.statisticsFiles(); if (!files.isEmpty()) { List<BlobMetadata> metadataList = (files.get(0)).blobMetadata(); ... }`
  - 新：`Optional<StatisticsFile> file = files.stream().filter(f -> f.snapshotId() == snapshot.snapshotId()).findFirst(); if (file.isPresent()) { List<BlobMetadata> metadataList = file.get().blobMetadata(); ... }`
- 这样只有 snapshotId 匹配的 statistics 文件才会被使用。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java` (修改, +43/-14 lines)

**修改目的**：增强测试以覆盖多 statistics 文件场景。

**工作逻辑**：
- 重写 `testReportStatistics` 测试（原仅一个 snapshot + 一个 statistics 文件）：
  - 创建第一个 snapshot 并附加 statisticsFile（snapshotId1，ndv=4）。
  - 追加新数据产生第二个 snapshot（snapshotId2），附加 statisticsFile2（snapshotId2，ndv=6）。
  - 构建 scan，验证 `estimateStatistics` 报告的行数为 6（第二个 snapshot 的数据量），ndv 为 6（来自 statisticsFile2）。
  - 同时验证禁用 reportColumnStats 时不报告列统计。
- 测试逻辑调整：将 scan 构建移到两个 statistics 文件都创建之后，配置 map 移到使用处。

## 总结

本提交修复 `SparkScan.estimateStatistics(Snapshot)` 错误使用 `files.get(0)` 的 bug，改为按 `snapshotId` 过滤找到匹配的 statistics 文件。原实现在表有多个 statistics 文件时会使用错误的统计信息，影响 Spark CBO 查询计划。增强后的测试通过创建两个 snapshot 各自附加不同 statistics 文件来验证正确性。
