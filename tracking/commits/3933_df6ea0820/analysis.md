# 提交 3933：Data: Add metrics reporter to generic scan builder (#16664)

## 提交信息

- **序号**：3933 / 4088
- **哈希**：df6ea0820030d217d5f645469419cdbb3f54a41f
- **短哈希**：df6ea0820
- **日期**：2026-06-23 18:24:38 +0200
- **作者**：Aleksandr Efimov
- **提交说明**：Data: Add metrics reporter to generic scan builder (#16664)
- **PR/Issue**：#16664

## 总体目的

这次提交为 `IcebergGenerics`（Iceberg 的通用数据读取 API）的 scan builder 添加了 `metricsReporter` 方法，使使用 `IcebergGenerics.read(table)` 进行表扫描的用户能够注册 metrics reporter 来收集扫描过程中的指标（如扫描的文件数、结果数据文件数、扫描耗时等）。

在此修改之前，`IcebergGenerics.ScanBuilder` 没有暴露设置 metrics reporter 的入口，而底层的 `TableScan` 已经支持 `metricsReporter(reporter)`。这意味着通过 `IcebergGenerics` 读取数据的用户无法获取扫描指标，不利于性能监控和查询优化分析。此提交补齐了这一 API 缺口。

## 如何达成设计目的

在 `IcebergGenerics.ScanBuilder` 中新增 `metricsReporter(MetricsReporter reporter)` 方法，直接委托给底层的 `tableScan.metricsReporter(reporter)`，保持与 `TableScan` API 的一致性。这样 reporter 会在 `TableScanIterable` 构建扫描迭代时被调用，收集并报告扫描指标。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/IcebergGenerics.java` (+6/-0 lines)

**修改目的**：为 ScanBuilder 添加 metricsReporter 方法。

**工作逻辑**：
新增 import `org.apache.iceberg.metrics.MetricsReporter`，在 `ScanBuilder` 内部类中新增方法：
```java
public ScanBuilder metricsReporter(MetricsReporter reporter) {
  this.tableScan = tableScan.metricsReporter(reporter);
  return this;
}
```
该方法将 reporter 委托给 `tableScan`，并返回 `this` 以支持链式调用。

### `data/src/test/java/org/apache/iceberg/data/TestLocalScan.java` (+19/-0 lines)

**修改目的**：验证 metricsReporter 功能正常工作。

**工作逻辑**：
新增 `testScanMetricsReporter` 测试方法，使用 `InMemoryMetricsReporter` 收集指标，通过 `IcebergGenerics.read(sharedTable).metricsReporter(reporter).build()` 构建扫描，读取全部 9 条记录后验证：
- `scanReport` 不为 null。
- `scanReport.tableName()` 等于表名。
- `scanReport.snapshotId()` 等于当前快照 ID。
- `scanReport.scanMetrics().resultDataFiles().value()` 等于 3（即扫描涉及 3 个数据文件）。

## 总结

这次提交为 `IcebergGenerics` 的 scan builder 补齐了 `metricsReporter` 方法，使通用数据读取 API 的用户也能收集扫描指标。这是一个小而实用的 API 增强，保持与底层 `TableScan` 的功能对等，并通过测试验证了指标收集的正确性。
