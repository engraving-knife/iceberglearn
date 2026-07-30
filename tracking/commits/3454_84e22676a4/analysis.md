# 提交 3454：Core, Data, Delta, Flink, Kafka, Spark: Migrate callers off deprecated Snapshot file-access methods to Snapshot Changes (#15656)

## 提交信息

- **序号**：3454 / 4088
- **哈希**：84e22676a43dda8a3d26d96c3298993905732a90
- **短哈希**：84e22676a4
- **日期**：2026-03-24 12:25:47 -0500
- **作者**：Russell Spitzer
- **提交说明**：Core, Data, Delta, Flink, Kafka, Spark: Migrate callers off deprecated Snapshot file-access methods to Snapshot Changes (#15656)
- **PR/Issue**：#15656

## 总体目的

将所有调用者从已弃用的 Snapshot 文件访问方法迁移到新的 Snapshot Changes API。Snapshot 上的旧方法（如 `dataFiles()`、`deleteFiles()`、`addedDataFiles()` 等）已被弃用，新代码应使用 `Snapshot.changes()` 方法来获取快照中的文件变更信息。

这是一个大规模重构提交，涉及 Core、Data、Delta、Flink、Kafka、Spark 等多个模块，共修改 95 个文件。

## 如何达成设计目的

- 将所有使用旧 Snapshot 文件访问方法的代码改为使用 `Snapshot.changes()` API
- 在 Core 模块的工具类中添加辅助方法来简化迁移
- 在各引擎模块（Flink、Spark、Kafka）的测试和生产代码中统一迁移
- 涉及多个 Spark 版本（3.4、3.5、4.0、4.1）的同步修改

## 修改详情

### Core 模块

#### `core/src/main/java/org/apache/iceberg/CatalogUtil.java` (+2/-5 lines)
**修改目的**：迁移 CatalogUtil 中的 Snapshot 文件访问方法调用。

#### `core/src/main/java/org/apache/iceberg/CherryPickOperation.java` (+48/-10 lines)
**修改目的**：迁移 CherryPick 操作中的文件访问逻辑，使用 Snapshot Changes API 获取文件变更。

#### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+2/-1 lines)
**修改目的**：迁移分区统计处理中的文件访问。

#### `core/src/main/java/org/apache/iceberg/ReachableFileCleanup.java` (+11/-4 lines)
**修改目的**：迁移可达文件清理中的文件访问逻辑。

#### `core/src/main/java/org/apache/iceberg/util/SnapshotUtil.java` (+8/-2 lines)
**修改目的**：在 SnapshotUtil 中添加辅助方法支持迁移。

#### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+18/-5 lines)
**修改目的**：更新测试以使用新 API。

#### `core/src/test/java/org/apache/iceberg/io/TestDVWriters.java` (+15/-6 lines)
**修改目的**：更新 DV 写入器测试。

### Delta 模块

#### `delta/src/test/java/org/apache/iceberg/delta/TestSnapshotDeltaLakeTable.java` (+5/-3 lines)
**修改目的**：更新 Delta Lake 表快照测试。

### Flink 模块（3个版本）

每个 Flink 版本修改以下文件：
- `RewriteUtil.java`：迁移重写工具中的文件访问
- `TestDataFileRewriteCommitter.java`：更新测试
- `TestMonitorSource.java`：更新监控源测试
- `TestFlinkIcebergSinkV2.java`：更新 Sink V2 测试
- `TestIcebergSinkV2.java`：更新 Sink V2 测试
- `TestMetadataTableReadableMetrics.java`：更新元数据表测试

### Kafka 模块

#### `kafka-connect/src/test/java/org/apache/iceberg/connect/IntegrationTestBase.java` (+10/-3 lines)
**修改目的**：更新集成测试基类。

#### `kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCoordinator.java` (+8/-3 lines)
**修改目的**：更新协调器测试。

### Spark 模块（4个版本：3.4、3.5、4.0、4.1）

每个 Spark 版本修改以下文件：
- `PlanningBenchmark.java`、`TaskGroupPlanningBenchmark.java`：更新基准测试
- `TestBranchDDL.java`：更新分支 DDL 测试
- `TestMergeOnReadDelete.java`：更新删除测试
- `TestMergeOnReadUpdate.java`：更新更新测试
- `TestRewriteTablePathProcedure.java`：更新表路径重写测试
- 多个 Benchmark 文件：更新基准测试
- `TestExpireSnapshotsAction.java`：更新快照过期测试
- `TestRewriteDataFilesAction.java`：更新数据文件重写测试
- `TestRewriteManifestsAction.java`：更新清单重写测试
- `TestRewriteTablePathsAction.java`：更新表路径重写测试
- `DataFrameWriteTestBase.java`：更新 DataFrame 写入测试基类
- `TestDataSourceOptions.java`：更新数据源选项测试
- `TestIcebergSourceTablesBase.java`：更新源表测试基类
- `TestMetadataTableReadableMetrics.java`：更新元数据表测试
- `TestRefreshTable.java`：更新刷新表测试

Spark 4.1 还额外修改了 `MicroBatchUtils.java`。

## 总结

该提交是一个大规模 API 迁移重构，将所有模块从已弃用的 Snapshot 文件访问方法迁移到新的 `Snapshot.changes()` API。涉及 Core、Delta、Flink（3版本）、Kafka、Spark（4版本）共 95 个文件的修改，确保整个代码库统一使用新的文件变更 API。
