# 提交 3302：Spark 4.1: Display write metrics on SQL UI (#15104)

## 提交信息

- **序号**：3302 / 4088
- **哈希**：68a347616e709cd45a0c3650f40883ec223df6a8
- **短哈希**：68a347616
- **日期**：2026-02-23
- **作者**：Manu Zhang
- **提交说明**：Spark 4.1: Display write metrics on SQL UI (#15104)
- **PR/Issue**：#15104

## 总体目的

该提交为 Iceberg 的 Spark 4.1 集成新增了写入指标（write metrics）在 Spark SQL UI 上的展示能力。在此提交之前，Iceberg Spark 集成已经在扫描（read）侧通过 `CustomSumMetric` 体系将扫描统计指标（如扫描的数据文件数、跳过的数据文件数等）暴露到 Spark SQL UI，方便用户通过 `EXPLAIN` 和 SQL UI 观察查询执行情况。但在写入（write）侧，INSERT、DELETE、MERGE 等写操作的提交指标（commit metrics，如新增数据文件数、删除记录数、总记录数等）无法在 SQL UI 上呈现，用户只能通过日志或 REST 报告获取这些信息，缺乏直观的可观测性。

写入操作的可观测性对生产环境至关重要：用户需要了解一次 INSERT 写入了多少数据和文件、一次 DELETE 产生了多少位置删除文件、一次 MERGE 重写了多少数据文件等，以便评估写入效率、排查数据倾斜和文件膨胀问题。Spark 4.1 的 DataSource V2 API 提供了 `supportedCustomMetrics()` 和 `reportDriverMetrics()` 两个接口，允许写入算子（`Write`）向 Spark SQL 框架声明自定义指标并在驱动端上报指标值。本提交正是利用这两个接口，将 Iceberg 提交（commit）阶段产生的 `CommitMetricsResult` 转换为 Spark 的 `CustomTaskMetric`，从而在 SQL UI 的指标面板中展示。

核心设计挑战在于：Iceberg 的提交指标在 commit 操作完成时由 `MetricsReporter` 报告，而 Spark 的 `reportDriverMetrics()` 是在写入完成后由框架回调。为了在回调时能获取到 commit 指标，本提交引入了 `InMemoryMetricsReporter` 作为"捕获器"，将其通过 `BaseTable.combineMetricsReporter()` 注入到表的报告器链中，使得 commit 产生的 `CommitReport` 被暂存在内存中，供 `reportDriverMetrics()` 回调时读取。

## 如何达成设计目的

整体设计分为三层：

1. **指标定义层**：新增 22 个 `CustomSumMetric` 子类（如 `AddedDataFiles`、`TotalRecords` 等），每个对应一个 commit 指标维度；同时将 16 个已有的扫描指标类的 `NAME` 常量从包级可见改为 `public`，以便测试和引用。

2. **指标采集层**：在 core 模块的 `BaseTable` 中新增 `combineMetricsReporter()` 方法，支持将额外的 reporter 合并到表的报告器链；`InMemoryMetricsReporter` 新增 `commitReport()` 方法暴露捕获到的 `CommitReport`。在每个 Spark `Write` 实现（`SparkWrite`、`SparkPositionDeltaWrite`、`SparkPositionDeletesRewrite`）的构造逻辑中，创建 `InMemoryMetricsReporter` 并通过 `combineMetricsReporter` 注入，使 commit 指标被内存报告器捕获。

3. **指标上报层**：在 `SparkWriteUtil` 中提供两个静态方法——`supportedCustomMetrics()` 返回所有支持的指标定义数组，`customTaskMetrics()` 从 `InMemoryMetricsReporter` 读取 `CommitReport` 并将每个 `CounterResult` 转换为 `CustomTaskMetric`。各 `Write` 实现重写 `supportedCustomMetrics()` 和 `reportDriverMetrics()` 委托给这两个方法。

涉及文件分布在 core 模块（2 个，提供报告器合并与内存捕获能力）、spark/v4.1 模块的写入主类（3 个）、指标定义类（38 个新增/修改）、工具类（1 个新增）和测试（1 个新增），共 45 个文件。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseTable.java` (+6/-1 lines)

**修改目的**：支持向表的报告器链动态追加 reporter。

**工作逻辑**：
将 `reporter` 字段从 `final` 改为非 final（`private MetricsReporter reporter;`），使其可在构造后替换。新增 `combineMetricsReporter(MetricsReporter metricsReporter)` 方法，调用 `MetricsReporters.combine(this.reporter, metricsReporter)` 将现有报告器与新报告器合并为一个组合报告器并赋值给 `this.reporter`。`MetricsReporters.combine` 会处理 null 情况并返回一个能向两个 reporter 都分发报告的组合实现。这样在 Spark Write 构造时注入 `InMemoryMetricsReporter` 后，后续的 commit 操作会同时向原有报告器（如 LoggingMetricsReporter）和内存报告器报告指标。

### `core/src/main/java/org/apache/iceberg/metrics/InMemoryMetricsReporter.java` (+10/-0 lines)

**修改目的**：暴露捕获到的 CommitReport。

**工作逻辑**：
新增 `commitReport()` 方法（标注 `@Nullable`），检查已存储的 `metricsReport` 是否为 `CommitReport` 类型，是则返回强类型转换结果，否则返回 null。`InMemoryMetricsReporter` 在 `report()` 被调用时将 `MetricsReport` 存入字段，已有 `scanReport()` 方法用于读取扫描报告，本提交补充了 `commitReport()` 以支持写入场景读取提交报告。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteUtil.java` (+109/-0 lines)

**修改目的**：提供写入指标的定义与上报工具方法。

**工作逻辑**：
新增两个核心静态方法：

- `supportedCustomMetrics()`：返回包含全部 22 个写入指标 `CustomMetric` 实例的数组，供各 Write 实现的 `supportedCustomMetrics()` 重写方法返回，使 Spark 框架知晓该写入算子支持哪些自定义指标并自动在 SQL UI 中创建对应的 `SQLMetric`。
- `customTaskMetrics(InMemoryMetricsReporter metricsReporter)`：从内存报告器获取 `CommitReport`，再取出 `CommitMetricsResult`，逐一调用 `addValue()` 将每个 `CounterResult`（如 `result.addedDataFiles()`、`result.totalRecords()` 等）转换为匿名 `CustomTaskMetric` 实现（提供 `name()` 和 `value()`），最终返回数组。`addValue()` 辅助方法在 `result` 非 null 时才创建 task metric，避免上报无意义的零值或空值指标。这些 driver 端指标会在写入完成后由 Spark 框架通过 `reportDriverMetrics()` 回调获取并展示到 SQL UI。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+21/-0 lines)

**修改目的**：在主流写入算子上接入写入指标。

**工作逻辑**：
新增 `metricsReporter` 字段（`InMemoryMetricsReporter` 类型），在构造器中判断 `table instanceof BaseTable`，是则创建 `InMemoryMetricsReporter` 并调用 `((BaseTable) table).combineMetricsReporter(metricsReporter)` 注入到报告器链。重写 `supportedCustomMetrics()` 委托给 `SparkWriteUtil.supportedCustomMetrics()`，重写 `reportDriverMetrics()` 委托给 `SparkWriteUtil.customTaskMetrics(metricsReporter)`。instanceof 判断保证只有 `BaseTable`（即 Iceberg 原生表）才注入内存报告器，避免对非 Iceberg 表类型造成影响。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (+21/-0 lines)

**修改目的**：在位置增量写入（MERGE INTO）算子上接入写入指标。

**工作逻辑**：
与 `SparkWrite` 完全一致的模式——新增 `metricsReporter` 字段，构造器中通过 `combineMetricsReporter` 注入，重写 `supportedCustomMetrics()` 和 `reportDriverMetrics()` 委托给 `SparkWriteUtil`。`SparkPositionDeltaWrite` 处理 MERGE INTO 操作，其 commit 指标包含新增/删除的数据文件和删除文件，接入后用户可在 SQL UI 观察 MERGE 操作的文件级影响。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java` (+21/-0 lines)

**修改目的**：在位置删除文件重写（compaction）算子上接入写入指标。

**工作逻辑**：
同样采用一致模式——构造器中通过 instanceof 判断注入 `InMemoryMetricsReporter`，重写 `supportedCustomMetrics()` 和 `reportDriverMetrics()` 委托给 `SparkWriteUtil`。该类用于 `RewritePositionDeletes` 存储过程，接入指标后可在 SQL UI 观察删除文件压缩的产出情况。

### 22 个新增指标类 `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/metrics/*.java` (各 +36/-0 lines)

**修改目的**：定义写入侧的自定义求和指标。

涉及类：`AddedDataFiles`、`AddedDeleteFiles`、`AddedEqualityDeletes`、`AddedEqualityDeleteFiles`、`AddedFileSizeInBytes`、`AddedPositionalDeletes`、`AddedPositionalDeleteFiles`、`AddedRecords`、`RemovedDataFiles`、`RemovedDeleteFiles`、`RemovedRecords`、`RemovedEqualityDeleteFiles`、`RemovedEqualityDeletes`、`RemovedFileSizeInBytes`、`RemovedPositionalDeleteFiles`、`RemovedPositionalDeletes`、`TotalDataFiles`、`TotalDeleteFiles`、`TotalEqualityDeletes`、`TotalFileSizeInBytes`、`TotalPositionalDeletes`、`TotalRecords`。

**工作逻辑**：
每个类继承 Spark 的 `CustomSumMetric`，定义一个 `public static final String NAME` 常量（如 `AddedDataFiles.NAME = "addedDataFiles"`、`TotalRecords.NAME = "totalRecords"`），并实现 `name()` 返回该常量、`description()` 返回人类可读描述（如 "number of added data files"、"total number of records"）。`CustomSumMetric` 是 Spark 提供的基类，自动在多个分区/任务间对同名指标值求和，适合文件数、记录数等可加指标。指标分为三类：`Added*`（本次提交新增的）、`Removed*`（本次提交移除的）、`Total*`（表累计的），覆盖数据文件、删除文件（位置删除/等值删除）、记录数、文件大小等维度。

### 16 个已有指标类的 NAME 可见性修改 `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/metrics/*.java` (各 +1/-1 lines)

**修改目的**：将已有扫描指标类的 NAME 常量改为 public 以便测试引用。

涉及类：`EqualityDeleteFiles`、`IndexedDeleteFiles`、`PositionalDeleteFiles`、`ResultDataFiles`、`ResultDeleteFiles`、`ScannedDataManifests`、`ScannedDeleteManifests`、`SkippedDataFiles`、`SkippedDataManifests`、`SkippedDeleteFiles`、`SkippedDeleteManifests`、`TotalDataFileSize`、`TotalDataManifests`、`TotalDeleteFileSize`、`TotalDeleteManifests`、`TotalPlanningDuration`。

**工作逻辑**：
将每个类中的 `static final String NAME` 改为 `public static final String NAME`，值不变。这一改动使测试类 `TestSparkWriteMetrics` 能通过 `AddedDataFiles.NAME` 等常量引用指标名称进行断言，也便于其他代码引用。这是纯粹的可见性提升，无行为变化。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkWriteMetrics.java` (+223/-0 lines)

**修改目的**：验证写入指标在 SQL UI 上的正确展示。

**工作逻辑**：
新增测试类继承 `TestBaseWithCatalog`，使用 `ParameterizedTestExtension` 进行参数化测试，包含两个测试用例：

- `writeMetrics()`：创建表并执行 `INSERT INTO ... SELECT id FROM range(1000)`，通过 `result.queryExecution().executedPlan()` 获取执行计划的 `SQLMetric` 映射，断言 `addedDataFiles` 等于 2、`addedRecords` 等于 1000、`addedFileSizeInBytes` 大于 0、`totalDataFiles` 等于 2 等正向指标，同时验证未涉及的删除类指标为 0。考虑到执行计划可能被 `AdaptiveSparkPlanExec` 包装，提供了递归遍历 `children()` 和 `innerChildren()` 的 `findMetrics()` 辅助方法定位包含目标指标的节点。

- `deleteMetrics()`：创建 merge-on-read 模式表，先插入 100 条数据，再执行 `DELETE FROM ... WHERE id = 1`，断言 `addedPositionalDeleteFiles` 等于 1、`addedPositionalDeletes` 等于 1、`totalDeleteFiles` 等于 1、`totalRecords` 等于 100 等指标，验证删除场景下位置删除相关指标正确上报。

## 总结

本次提交为 Spark 4.1 集成实现了写入指标在 SQL UI 的完整展示能力，通过"内存报告器捕获 commit 指标 + reportDriverMetrics 回调上报"的设计，打通了 Iceberg 提交指标到 Spark SQL UI 的链路。新增 22 个写入指标类覆盖新增/移除/累计三个维度的文件数、记录数和大小，并通过详尽的测试验证 INSERT 和 DELETE 场景。这显著提升了写入操作的可观测性，使用户无需查阅日志即可在 SQL UI 直观了解写入对文件和记录的影响。
