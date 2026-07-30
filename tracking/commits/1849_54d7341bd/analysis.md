# 提交 1849：Core: Make reporter() public so that it can be accessed by Trino for BaseTable creation (#12519)

## 提交信息

- **序号**：1849 / 4088
- **哈希**：54d7341bd7f4b816a8aacc7c4c3a99ee2dbc1ee7
- **短哈希**：54d7341bd
- **日期**：2025-03-14 09:04:07 +0100
- **作者**：Pucheng Yang
- **提交说明**：Core: Make reporter() public so that it can be accessed by Trino for BaseTable creation (#12519)
- **PR/Issue**：#12519

## 总体目的

`BaseTable` 在内部持有一个 `MetricsReporter` 字段，用于在表扫描/提交过程中收集并上报指标。此前 `BaseTable#reporter()` 方法的可见性是包级私有（package-private，即未加 `public` 修饰符），只有在 `org.apache.iceberg` 包内的代码才能调用。

Trino（Presto 的活跃分支）在自定义 `BaseTable` 创建流程中需要拿到 `MetricsReporter` 实例，以便把扫描指标回传给 Iceberg 的指标体系。由于 Trino 的代码位于 `io.trino` 包，无法访问包级私有的 `reporter()`，导致其必须借助反射或复制 `BaseTable` 实例的变通手段，既不优雅也存在维护风险。

本提交将 `reporter()` 的可见性从包级私有提升为 `public`，让外部模块（如 Trino）能够直接调用该方法获取 `MetricsReporter`，从而在自建 `BaseTable` 时正确传递指标上报器，避免指标丢失。

## 如何达成设计目的

仅修改一行代码：在 `core/src/main/java/org/apache/iceberg/BaseTable.java` 中，把 `MetricsReporter reporter()` 改为 `public MetricsReporter reporter()`。可见性提升不影响调用语义、不影响现有调用方，但解锁了包外访问能力，属于最小化的 API 暴露调整。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseTable.java` (修改, 1 line)

**修改目的**：将 `reporter()` 方法从包级私有提升为 `public`，使外部模块（如 Trino）能在自建 `BaseTable` 时直接获取并复用该表持有的 `MetricsReporter`。

**工作逻辑**：`BaseTable` 在构造时通过 `Preconditions.checkNotNull(reporter, "reporter cannot be null")` 强制要求传入非空的 `MetricsReporter`，并在 `newScan()`、`newIncrementalAppendScan()` 等扫描方法中把 `reporter` 注入到扫描上下文（`ImmutableTableScanContext.builder().metricsReporter(reporter)`）。外部模块若自行创建 `BaseTable` 实例（例如 Trino 的 Iceberg 集成层为了绕过 Catalog 直接构造表对象），需要拿到这个 `reporter` 才能让后续扫描的指标正确上报。修改前包外代码无法调用 `reporter()`，只能拿到 null 或重新构造一个 reporter；修改后可直接调用 `table.reporter()` 复用同一个实例。

## 小结

- **成效**：解除了 `reporter()` 的包级访问限制，让 Trino 等外部集成可以直接复用 `BaseTable` 内部的 `MetricsReporter`，避免指标丢失或重复创建 reporter。
- **影响范围**：仅暴露一个已有方法的可见性，不影响任何现有调用方（包内调用依然有效），属于纯增量式 API 扩展。
- **回迁到 1.4.x 的注意事项**：单行修改，无前置依赖，回迁安全。需注意 1.4.x 分支的 `BaseTable` 中是否已存在 `reporter()` 方法（如果该字段/方法是较新引入的，需先确认其存在）；若 Trino 的 1.4.x 集成也需要此访问能力，建议回迁。
