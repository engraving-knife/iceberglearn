# 提交 3281：Spark 4.1: Remove unnecessary stats reporting from scan builder (#15364)

## 提交信息

- **序号**：3281 / 4088
- **哈希**：eff136ba8f43b26788308e8554d06dbf270b800a
- **短哈希**：eff136ba8
- **日期**：2026-02-18
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Remove unnecessary stats reporting from scan builder (#15364)
- **PR/Issue**：#15364

## 总体目的

本提交从 `SparkScanBuilder` 中移除了对 Spark `SupportsReportStatistics` 接口的实现及其两个方法 `estimateStatistics()` 和 `readSchema()`。在此之前，`SparkScanBuilder` 实现了 `SupportsReportStatistics`，其 `estimateStatistics()` 和 `readSchema()` 的实现都是先调用 `build()` 构建出真正的 `Scan` 对象，再将其强转为 `SupportsReportStatistics` 委托调用。这种做法存在两个问题：一是 `build()` 是一个较重的操作，会根据读取配置构建 BatchScan/IncrementalScan/ChangelogScan 等具体扫描，在查询计划阶段 Spark 可能多次调用 `estimateStatistics()`/`readSchema()`，导致扫描被反复构建，浪费开销；二是实际上 `build()` 返回的各个 `Scan` 实现（如 `SparkBatchQueryScan`、`SparkCopyOnWriteScan`、`SparkChangelogScan`、`SparkLocalScan` 等）自身已经实现了 `SupportsReportStatistics`，Spark 完全可以直接从最终 `Scan` 对象获取统计信息，构建器层的委托纯属多余。

这是继 #15360 引入 `BaseSparkScanBuilder` 重构后的进一步清理，使扫描构建器职责更单一——只负责构建扫描，不再承担统计上报职责。

## 如何达成设计目的

从 `SparkScanBuilder` 的 `implements` 列表中删除 `SupportsReportStatistics`，删除 `estimateStatistics()` 和 `readSchema()` 两个覆写方法及其相关 import。改动仅涉及一个文件。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+0/-13 lines)

**修改目的**：移除构建器中冗余的统计上报委托逻辑。

**工作逻辑**：
类声明中删除 `implements ... SupportsReportStatistics ...`，使其不再声明该 mix-in 接口。删除两个 `@Override` 方法：
- `estimateStatistics()`：原实现为 `return ((SupportsReportStatistics) build()).estimateStatistics();`，即先构建扫描再委托获取统计。
- `readSchema()`：原实现为 `return build().readSchema();`，同样先构建扫描再获取读 schema。

删除后，Spark 在查询计划阶段不会再向 builder 询问统计信息，而是等 `build()` 返回 `Scan` 后，由 `Scan` 自身实现的 `SupportsReportStatistics` 提供统计。同时移除不再使用的 `Statistics` 与 `SupportsReportStatistics` 两个 import。这避免了 builder 内部为回答统计查询而提前触发 `build()` 的副作用与重复构建开销。

## 总结

本提交移除了 `SparkScanBuilder` 中冗余的 `SupportsReportStatistics` 实现及其委托方法，将统计上报职责交还给 `build()` 产出的 `Scan` 对象自身，简化了构建器职责并避免了查询计划阶段对 `build()` 的多余调用。
