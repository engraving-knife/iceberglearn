# 提交 1090：Flink: Port #10484 to v1.20 (#10989)

## 提交信息

- **序号**：1090 / 4088
- **哈希**：f2d62757e210df5498f147aa126926477ac6c156
- **短哈希**：f2d62757e
- **日期**：2024-08-23 15:42:24 +0200
- **作者**：pvary
- **提交说明**：Flink: Port #10484 to v1.20 (#10989)
- **PR/Issue**：#10989（移植 #10484）

## 总体目的

提交 1085（#10484）已经在 Flink v1.19 模块引入了 `TriggerManager` 维护调度算子及其全套支撑（`TriggerEvaluator`、`TriggerLockFactory`/`JdbcLockFactory`、`Trigger`、`TableMaintenanceMetrics`、`TableChange` Builder、大量测试）。本提交把完全相同的改动移植到 Flink v1.20 模块，使 v1.20 与 v1.19 的 maintenance 能力保持同步。这是 Iceberg 多 Flink 版本并行维护的常规操作：每个新功能需要在所有受支持的 Flink 版本模块（v1.18/v1.19/v1.20）中各落地一份。

## 如何达成设计目的

与 1085 完全相同的改动，只是目标目录由 `flink/v1.19/` 换成 `flink/v1.20/`。文件清单、行数、逻辑与 1085 一致（17 个文件，2081 行新增、16 行删除）：

1. `flink/v1.20/build.gradle`：测试引入 sqlite jdbc。
2. 新增主代码：`JdbcLockFactory`、`Trigger`、`TriggerEvaluator`、`TriggerLockFactory`、`TriggerManager`、`TableMaintenanceMetrics`。
3. 改造 `TableChange`（加 Builder、收紧构造器可见性）、`MonitorSource`（收紧可见性）。
4. 新增/增强测试：`TestTriggerManager`、`TestJdbcLockFactory`、`TestLockFactoryBase`、`OperatorTestBase`、`ConstantsForTests`、`MetricsReporterFactoryForTests` 及 SPI 注册文件、`TestMonitorSource` 适配。

详细的各文件工作逻辑请参考序号 1085 的分析（两者完全一致，仅模块版本不同）。

## 修改详情

各文件修改目的与工作逻辑与 1085 完全相同，此处仅列出文件清单（路径前缀为 `flink/v1.20/`）：

- `flink/v1.20/build.gradle`：引入 sqlite 测试依赖。
- `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java`（新增）：维护调度核心算子。
- `flink/v1.20/.../Trigger.java`（新增）：触发消息载体。
- `flink/v1.20/.../TriggerEvaluator.java`（新增）：触发条件评估器。
- `flink/v1.20/.../TriggerLockFactory.java`（新增）：锁工厂接口。
- `flink/v1.20/.../JdbcLockFactory.java`（新增）：JDBC 锁实现。
- `flink/v1.20/.../TableMaintenanceMetrics.java`（新增）：metric 常量。
- `flink/v1.20/.../TableChange.java`：加 Builder、收紧构造器。
- `flink/v1.20/.../MonitorSource.java`：收紧可见性。
- 测试文件：`TestTriggerManager`、`TestJdbcLockFactory`、`TestLockFactoryBase`、`OperatorTestBase`、`ConstantsForTests`、`MetricsReporterFactoryForTests`、SPI 文件、`TestMonitorSource`。

## 小结

- **成效**：把 `TriggerManager` 维护调度功能从 v1.19 移植到 v1.20，使两个 Flink 版本的 maintenance 能力一致。
- **影响范围**：仅 `flink/v1.20` 模块，17 个文件，2081 行新增；与 1085 内容一致。属于新功能。
- **回迁到 1.4.x 的注意事项**：不建议回迁，理由同 1085：这是 main 分支上的新功能（Flink Maintenance 框架），1.4.x 不具备该 maintenance operator 包与前置基础设施，强行回迁工作量极大且与维护分支定位不符。
