# 提交 1926：Spark 3.4: Use correct statistics file in SparkScan::estimateStatistics(Snapshot) (#12647)

## 提交信息

- **序号**：1926 / 4088
- **哈希**：d54d81ecc1968eb0ec16a1f266e1d0b29e53b2ef
- **短哈希**：d54d81ecc
- **日期**：2025-03-27 11:37:20 +0100
- **作者**：Soumya Banerjee
- **提交说明**：Spark 3.4: Use correct statistics file in SparkScan::estimateStatistics(Snapshot) (#12647)
- **PR/Issue**：#12647（回移 #12482 到 Spark 3.4）

## 总体目的

`SparkScan.estimateStatistics(Snapshot snapshot)` 在 CBO（基于代价的优化）开启且要求上报列统计时，会从表的统计文件（`StatisticsFile`）中读取列 NDV 等统计信息，供 Spark 优化器估算。原实现取统计文件时直接用 `table.statisticsFiles().get(0)`——即"第一个统计文件"，而没有按快照 ID 过滤。

问题：一个表可以有多个统计文件，每个绑定不同的快照（`snapshotId`）。当表经历多次提交后，当前快照对应的统计文件未必是列表中的第一个。`get(0)` 取到的可能是旧快照的统计文件，导致 `estimateStatistics` 用了错误快照的列统计（NDV 等），进而让 Spark CBO 基于过时/错误的统计做决策。

本提交（回移 #12482）修复该缺陷：在 `estimateStatistics(Snapshot)` 中，按传入的 `snapshot.snapshotId()` 过滤 `statisticsFiles()`，找到匹配当前快照的统计文件再使用，而非盲目取第一个。

## 如何达成设计目的

1. `SparkScan.estimateStatistics(Snapshot)` 中，把 `files.get(0)` 改为用 stream 过滤 `f.snapshotId() == snapshot.snapshotId()` 取第一个匹配项（`Optional<StatisticsFile>`），存在时才使用其 `blobMetadata()`。
2. 测试 `TestSparkScan` 改造：构造两个不同快照的统计文件（先 append 一批数据生成 snapshot1 并 set statistics，再 append 一批生成 snapshot2 并 set statistics），然后用当前快照（snapshot2）构建 scan，断言 `estimateStatistics` 报告的是 snapshot2 的 NDV（6）而非 snapshot1 的（4），从而验证按快照过滤的正确性。同时保留 `reportColumnStats` 开关与 CBO 开关的断言路径。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java` (修改, +5/-2 lines)

**修改目的**：按快照 ID 选取统计文件。

**工作逻辑**：

```java
Optional<StatisticsFile> file =
    files.stream().filter(f -> f.snapshotId() == snapshot.snapshotId()).findFirst();
if (file.isPresent()) {
  List<BlobMetadata> metadataList = file.get().blobMetadata();
  ...
}
```

替换原来的 `if (!files.isEmpty()) { List<BlobMetadata> metadataList = (files.get(0)).blobMetadata(); ... }`。这样只有绑定当前快照的统计文件才会被用来读取列统计，避免误用其它快照的统计。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java` (修改, +43/-15 lines)

**修改目的**：验证使用当前快照对应的统计文件。

**工作逻辑**：

测试先写入 4 条记录生成 snapshot1，为其 set 一个 NDV=4 的 `GenericStatisticsFile`；再 append 2 条记录生成 snapshot2，为其 set 一个 NDV=6 的 `GenericStatisticsFile`。然后基于当前表（snapshot2）构建 `SparkScan`：

- 关闭列统计上报时：`checkColStatisticsNotReported(scan, 6L)`（行数 6）。
- CBO 开启、列统计上报开启时：`checkColStatisticsReported(scan, 6L, {"id": 6L})`，断言 NDV 报告的是 snapshot2 的 6，而非 snapshot1 的 4。

从而确保 `estimateStatistics` 按当前快照选取统计文件。

## 总结

本提交把 #12482 回移到 Spark 3.4：修复 `SparkScan.estimateStatistics(Snapshot)` 盲目取 `statisticsFiles().get(0)` 导致可能使用错误快照统计文件的问题，改为按 `snapshot.snapshotId()` 过滤选取匹配的统计文件，确保 CBO 拿到的是当前快照的列统计。测试通过构造两个快照各自的统计文件，验证当前快照使用的是其对应的 NDV。
