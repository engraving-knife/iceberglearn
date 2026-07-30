# 提交 2088：Flink: Backport Maintenance - RewriteDataFiles to Flink 1.19

## 提交信息

- **序号**：2088 / 4088
- **哈希**：4bb60aa0bc873c15e4446a0960d381d24fcbe8be
- **短哈希**：4bb60aa0b
- **日期**：2025-05-06 22:52:15 -0700
- **作者**：Gyula Fora
- **提交说明**：Flink: Backport Maintenance - RewriteDataFiles to Flink 1.19
- **PR/Issue**：Backports #11497

## 总体目的

本提交是提交 2083（PR #11497，"Flink: Maintenance - RewriteDataFiles"）向 Flink 1.19 模块的后向移植（backport）。提交 2083 为 Flink 1.20 模块新增了基于流式维护框架的数据文件重写（RewriteDataFiles / Compaction）能力，使 Flink 用户能够通过流式作业持续自动地压缩数据文件。

Iceberg 同时维护多个 Flink 版本的集成模块（v1.19、v1.20 等），不同版本的 Flink API 可能有细微差异，因此需要为每个支持的版本分别移植功能。本提交将同样的 RewriteDataFiles 维护任务移植到 `flink/v1.19/` 模块，使 Flink 1.19 用户也能使用数据文件自动压缩能力。

该移植的内容与提交 2083 完全一致——25 个文件，2587 行新增，108 行删除，仅目标目录从 `flink/v1.20/flink/` 变为 `flink/v1.19/flink/`。

## 如何达成设计目的

与提交 2083 完全相同的设计，将相同的代码移植到 Flink 1.19 模块。整体设计采用 Flink DataStream 流水线模式，每个 `Trigger` 事件触发一次完整的重写迭代：

- **`RewriteDataFiles.Builder`**（API 入口）：配置重写参数，构建 DataStream 流水线。
- **`DataFileRewritePlanner`**（规划算子）：使用 `BinPackRewriteFilePlanner` 规划需要重写的文件分组。
- **`DataFileRewriteRunner`**（执行算子）：并行执行实际的数据文件重写。
- **`DataFileRewriteCommitter`**（提交算子）：使用 `RewriteDataFilesCommitManager` 提交重写结果，支持部分进度提交。
- **`TaskResultAggregator`**（聚合算子）：聚合任务执行结果，处理错误并输出 TaskResult。

流水线流向：`Trigger → Planner (forceNonParallel) → rebalance → Runner (parallel) → Committer (forceNonParallel) → Aggregator (forceNonParallel) → TaskResult`。

## 修改详情

### 与提交 2083 完全一致的文件列表（仅路径前缀从 `flink/v1.20/` 改为 `flink/v1.19/`）

**新增源文件**：
- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/RewriteDataFiles.java` (+234)：API 入口和 Builder。
- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+206)：规划算子。
- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteRunner.java` (+253)：执行算子。
- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewriteCommitter.java` (+199)：提交算子。
- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResultAggregator.java` (+101)：结果聚合算子。
- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java` (+30)：监控指标。
- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LogUtil.java` (+26)：日志工具。

**修改源文件**：
- `MaintenanceTaskBuilder.java` (+2/-2)：调整 `append` 方法参数顺序。
- `TableMaintenance.java` (+4/-3)：适配参数顺序和 Locale 格式化。
- `ExpireSnapshots.java` (+1/-1)：适配参数变更。
- `DeleteFilesProcessor.java`、`ExpireSnapshotsProcessor.java`、`LockRemover.java`、`TriggerManager.java`：统一日志格式和错误处理改进。

**新增测试文件**：
- `TestRewriteDataFiles.java` (+417)、`OperatorTestBase.java` (+113)、`RewriteUtil.java` (+83)、`TestDataFileRewriteCommitter.java` (+278)、`TestDataFileRewritePlanner.java` (+193)、`TestDataFileRewriteRunner.java` (+355)。

**修改测试文件**：
- `MaintenanceTaskTestBase.java`、`TestExpireSnapshots.java`、`TestMaintenanceE2E.java`、`MaintenanceTaskInfraExtension.java`、`TestDeleteFilesProcessor.java`。

每个文件的具体修改内容与提交 2083 完全一致，请参考提交 2083 的分析文档获取详细的工作逻辑说明。

## 总结

本提交将提交 2083（PR #11497）的 Flink 数据文件重写维护任务后向移植到 Flink 1.19 模块。移植内容与原提交完全一致（25 个文件，+2587/-108 行），仅目标目录从 `flink/v1.20/flink/` 变为 `flink/v1.19/flink/`。使 Flink 1.19 用户也能使用流式自动数据文件压缩能力，保持多版本 Flink 集成的功能一致性。
