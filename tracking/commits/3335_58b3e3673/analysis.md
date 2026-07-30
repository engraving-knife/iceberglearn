# 提交 3335：Spark 4.0: Display write metrics on SQL UI (#15468)

## 提交信息

- **序号**：3335 / 4088
- **哈希**：58b3e3673107e9cb474213d3b66348ceb3b206d4
- **短哈希**：58b3e3673
- **日期**：2026-03-02 11:27:59 +0100
- **作者**：Manu Zhang
- **提交说明**：Spark 4.0: Display write metrics on SQL UI (#15468)
- **PR/Issue**：#15468（back-port of #15104）

## 总体目的

本提交是 main 分支上 PR #15104 的回移（back-port），目的是让 Iceberg 在 Spark 4.0 中把写操作（write）产生的提交度量指标显示在 Spark SQL UI 上。

背景是：Iceberg 在每次写操作（INSERT/UPDATE/DELETE/MERGE、position deletes 重写等）提交时会产出一份 `CommitReport`，其中包含丰富的度量信息——新增/删除/总计的数据文件数、删除文件数（区分 positional 与 equality）、记录数、文件总字节数等。这些指标对用户理解写入成本、诊断数据布局与合并行为至关重要。然而在 Spark 中，这些提交度量是在驱动端（driver）的 commit 阶段计算出来的，而非在 executor 上执行的 task 中产生。Spark 原生的 task metric 机制只能收集 executor 上的度量，因此 Iceberg 的提交度量此前无法出现在 SQL UI 的 metrics 面板里，用户只能通过日志或 REST catalog 报告查看，体验割裂。

Spark 4.0 的 DataSource V2 API 为此引入了两个新接口方法：`Write.supportedCustomMetrics()`（声明该 write 支持哪些自定义度量）和 `Write.reportDriverMetrics()`（上报在驱动端计算的度量）。本提交正是利用这两个 API，把 Iceberg 的提交度量桥接到 Spark 的 `CustomMetric`/`CustomTaskMetric` 体系，使其自动聚合并展示在 SQL UI 的 metrics 列表中。

之所以需要 back-port 到 `spark/v4.0/` 模块，是因为该模块专门面向 Spark 4.0，而 `supportedCustomMetrics`/`reportDriverMetrics` 是 Spark 4.0 才有的 API；早期 Spark 版本（3.x）的 DataSource V2 没有这些方法，无法实现。本提交将 main 上已合入的能力同步到 1.4.x 维护线对应的 Spark 4.0 模块，使该发布线用户也能在 SQL UI 看到写度量。

## 如何达成设计目的

整体设计分为三层：

1. **度量定义层**：新增 22 个继承 `CustomSumMetric`（Spark 4.0 提供的可求和自定义度量基类）的度量类，分别对应 `CommitMetricsResult` 中的各个计数器（added/removed/total 的 data files、delete files、records、positional/equality deletes、file size in bytes）；同时把 16 个既有的扫描/规划度量类的 `NAME` 常量可见性从包级改为 `public`，以便在新工具类中引用。

2. **桥接工具层**：在 `SparkWriteUtil` 中新增 `supportedCustomMetrics()`（返回 22 个新度量类的实例数组，供 Spark 注册）与 `customTaskMetrics(InMemoryMetricsReporter)`（从 reporter 中读取 `CommitReport`，将每个 `CounterResult` 转换为 `CustomTaskMetric`）。

3. **写入集成层**：在三个写实现（`SparkWrite`、`SparkPositionDeltaWrite`、`SparkPositionDeletesRewrite`）中各注入一个 `InMemoryMetricsReporter`，通过 `BaseTable.combineMetricsReporter()` 把它挂到表上以捕获提交报告，然后实现 `supportedCustomMetrics()` 与 `reportDriverMetrics()` 两个 Spark 4.0 接口方法，委托给 `SparkWriteUtil`。

涉及 43 个文件：1 个工具类、3 个写实现、22 个新度量类、16 个既有度量类可见性调整、1 个测试类。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkWriteUtil.java` (+109 lines)

**修改目的**：提供度量声明与驱动端度量转换的集中式工具方法，供三个写实现复用。

**工作逻辑**：
新增两个 public static 方法与一个 private static 辅助方法：

- `supportedCustomMetrics()`：返回包含 22 个新度量类实例的 `CustomMetric[]`，对应 Iceberg 提交报告中的全部计数器。Spark 会据此在 SQL UI 注册这些度量名。

- `customTaskMetrics(InMemoryMetricsReporter metricsReporter)`：接收写入时挂载的内存度量报告器。当 reporter 非空且其 `commitReport()` 非空时，取出 `CommitMetricsResult`，对其中 22 个 `CounterResult`（如 `result.addedDataFiles()`、`result.removedRecords()`、`result.totalFileSizeInBytes()` 等）逐一调用 `addValue` 转换为 `CustomTaskMetric` 列表。每个 `CounterResult` 仅在非空时才加入，避免空指针。

- `addValue(CustomMetric metric, CounterResult result, List<CustomTaskMetric> taskMetrics)`：私有辅助方法，当 `result != null` 时构造一个匿名 `CustomTaskMetric`，其 `name()` 返回对应 `CustomMetric.name()`、`value()` 返回 `result.value()`，并加入列表。这样把 Iceberg 的 `CounterResult`（long 值）适配为 Spark 期望的 `CustomTaskMetric` 接口。

该工具类是整个特性的核心桥接点，把 Iceberg 的 `CommitMetricsResult` 模型映射为 Spark 的 `CustomMetric`/`CustomTaskMetric` 模型。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+21 lines)

**修改目的**：在主写入实现中接入提交度量上报，使其出现在 SQL UI。

**工作逻辑**：
- 新增字段 `private InMemoryMetricsReporter metricsReporter`。
- 在构造器末尾判断 `this.table instanceof BaseTable`，若是则创建 `InMemoryMetricsReporter` 并通过 `((BaseTable) this.table).combineMetricsReporter(metricsReporter)` 注册到表上。这样表在 commit 时产生的 `CommitReport` 会被该 reporter 捕获。`BaseTable` 类型判断是必要的，因为传入的 `table` 可能是事务性表（`TransactionTable`）等非 `BaseTable` 实现，它们不暴露 `combineMetricsReporter`，此时跳过以避免类转换异常。
- 实现 `supportedCustomMetrics()`，直接委托 `SparkWriteUtil.supportedCustomMetrics()`。
- 实现 `reportDriverMetrics()`，直接委托 `SparkWriteUtil.customTaskMetrics(metricsReporter)`。该方法由 Spark 在驱动端调用（非 task 端），从而把提交阶段计算的度量推送到 SQL UI。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (+21 lines)

**修改目的**：在 position delta 写入（用于 MERGE/UPDATE/DELETE 的 row-level 操作）中接入提交度量上报。

**工作逻辑**：
与 `SparkWrite` 完全同构：新增 `metricsReporter` 字段，构造器中通过 `BaseTable` 类型判断后 `combineMetricsReporter` 注册，并实现 `supportedCustomMetrics()` 与 `reportDriverMetrics()` 两个方法委托给 `SparkWriteUtil`。该类实现 `DeltaWrite`（Spark 4.0 的行级操作写入接口），覆盖 MERGE 等场景，确保这些操作同样能在 SQL UI 显示度量。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeletesRewrite.java` (+21 lines)

**修改目的**：在 position deletes 文件重写（如 `REWRITE POSITIONAL DELETES` 维护操作）中接入提交度量上报。

**工作逻辑**：
与前两者同构：新增 `metricsReporter` 字段，构造器中通过 `BaseTable` 判断后注册 reporter，并实现 `supportedCustomMetrics()` 与 `reportDriverMetrics()` 委托给 `SparkWriteUtil`。该类实现 `Write` 接口，用于重写已有的 position delete 文件以优化读取性能，使此类维护操作的度量也能在 SQL UI 可见。

### 新增度量类（22 个文件，每个 +36 lines）

**修改目的**：定义 Iceberg 提交度量在 Spark 侧的 `CustomMetric` 表示。

以下 22 个新文件均位于 `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/metrics/` 目录，结构完全一致——继承 Spark 4.0 的 `org.apache.spark.sql.connector.metric.CustomSumMetric`，声明一个 `public static final String NAME` 常量，并实现 `name()`（返回 NAME）与 `description()`（返回简短英文描述）：

- `AddedDataFiles.java`（NAME=`addedDataFiles`，描述"number of added data files"）
- `AddedDeleteFiles.java`（`addedDeleteFiles`，"number of added delete files"）
- `AddedEqualityDeleteFiles.java`（`addedEqualityDeleteFiles`，"number of added equality delete files"）
- `AddedEqualityDeletes.java`（`addedEqualityDeletes`，"number of added equality deletes"）
- `AddedFileSizeInBytes.java`（`addedFileSizeInBytes`，"size of added files in bytes"）
- `AddedPositionalDeleteFiles.java`（`addedPositionalDeleteFiles`，"number of added positional delete files"）
- `AddedPositionalDeletes.java`（`addedPositionalDeletes`，"number of added positional deletes"）
- `AddedRecords.java`（`addedRecords`，"number of added records"）
- `RemovedDataFiles.java`（`removedDataFiles`，"number of removed data files"）
- `RemovedDeleteFiles.java`（`removedDeleteFiles`，"number of removed delete files"）
- `RemovedEqualityDeleteFiles.java`（`removedEqualityDeleteFiles`，"number of removed equality delete files"）
- `RemovedEqualityDeletes.java`（`removedEqualityDeletes`，"number of removed equality deletes"）
- `RemovedFileSizeInBytes.java`（`removedFileSizeInBytes`，"size of removed files in bytes"）
- `RemovedPositionalDeleteFiles.java`（`removedPositionalDeleteFiles`，"number of removed positional delete files"）
- `RemovedPositionalDeletes.java`（`removedPositionalDeletes`，"number of removed positional deletes"）
- `RemovedRecords.java`（`removedRecords`，"number of removed records"）
- `TotalDataFiles.java`（`totalDataFiles`，"total number of data files"）
- `TotalDeleteFiles.java`（`totalDeleteFiles`，"total number of delete files"）
- `TotalEqualityDeletes.java`（`totalEqualityDeletes`，"total number of equality deletes"）
- `TotalFileSizeInBytes.java`（`totalFileSizeInBytes`，"total size of files in bytes"）
- `TotalPositionalDeletes.java`（`totalPositionalDeletes`，"total number of positional deletes"）
- `TotalRecords.java`（`totalRecords`，"total number of records"）

**工作逻辑**：
`CustomSumMetric` 是 Spark 4.0 提供的基类，表示"跨任务/分区求和"的度量聚合方式。选择 `CustomSumMetric` 是因为文件数、记录数、字节数等都是可加量——多个 task 各自写入的文件数相加即为总写入文件数。每个类通过 `name()` 暴露稳定字符串标识（与 `CommitMetricsResult` 各字段的语义对应），Spark 用此名称在 metrics map 中聚合与展示。这些 NAME 同时被测试类引用以断言度量值。

### 既有度量类 NAME 可见性调整（16 个文件，每个 +1/-1 lines）

**修改目的**：将既有扫描/规划度量类的 `NAME` 常量从包级可见改为 `public`，供测试及其他模块引用。

以下 16 个文件均位于 `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/metrics/` 目录，改动完全一致——将 `static final String NAME = "..."` 改为 `public static final String NAME = "..."`：

- `EqualityDeleteFiles.java`（NAME=`equalityDeleteFiles`）
- `IndexedDeleteFiles.java`（`indexedDeleteFiles`）
- `PositionalDeleteFiles.java`（`positionalDeleteFiles`）
- `ResultDataFiles.java`（`resultDataFiles`）
- `ResultDeleteFiles.java`（`resultDeleteFiles`）
- `ScannedDataManifests.java`（`scannedDataManifests`）
- `ScannedDeleteManifests.java`（`scannedDeleteManifests`）
- `SkippedDataFiles.java`（`skippedDataFiles`）
- `SkippedDataManifests.java`（`skippedDataManifests`）
- `SkippedDeleteFiles.java`（`skippedDeleteFiles`）
- `SkippedDeleteManifests.java`（`skippedDeleteManifests`）
- `TotalDataFileSize.java`（`totalDataFileSize`）
- `TotalDataManifests.java`（`totalDataManifests`）
- `TotalDeleteFileSize.java`（`totalDeleteFileSize`）
- `TotalDeleteManifests.java`（`totalDeleteManifests`）
- `TotalPlanningDuration.java`（`totalPlanningDuration`）

**工作逻辑**：
这些是既有的扫描端度量类（用于读取/规划阶段），本提交未改变其度量逻辑，仅放宽 `NAME` 的访问级别。原因是新增的测试类 `TestSparkWriteMetrics` 位于 `org.apache.iceberg.spark.source` 包（与度量类的 `...source.metrics` 子包不同），需要以 `AddedDataFiles.NAME` 等形式引用度量名做断言；而既有度量类的 `NAME` 此前是包级私有，跨包不可见，故统一改为 `public`。这不影响运行时行为，仅为访问控制调整。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkWriteMetrics.java` (+223 lines)

**修改目的**：验证写操作的提交度量能正确出现在 Spark 执行计划的 metrics 中且数值正确。

**工作逻辑**：
新增测试类 `TestSparkWriteMetrics`，继承 `TestBaseWithCatalog` 并使用 `ParameterizedTestExtension` 参数化测试扩展。包含两个测试方法与一个辅助方法：

- `writeMetrics()`：创建 iceberg 表后执行 `INSERT INTO ... SELECT id FROM range(1000)`，通过 `result.queryExecution().executedPlan()` 获取执行计划，取其 `metrics()` map。若根计划不包含目标度量（如存在 `AdaptiveSparkPlanExec` 包装），则用 `findMetrics` 递归在子计划与 innerChildren 中查找。断言：`addedDataFiles=2`、`addedRecords=1000`、`addedFileSizeInBytes>0`、`totalDataFiles=2`、`totalRecords=1000`、`totalFileSizeInBytes>0`；并验证所有删除相关度量（addedDeleteFiles、addedEqualityDeleteFiles、removed*、totalDeleteFiles 等）均为 0。该用例覆盖纯追加写入场景。

- `deleteMetrics()`：创建带 `write.delete.mode=merge-on-read` 的表，先追加 100 条，再执行 `DELETE FROM ... WHERE id = 1`。断言：`addedPositionalDeleteFiles=1`、`removedDataFiles=0`（merge-on-read 不删除原数据文件）、`addedPositionalDeletes=1`、`totalDeleteFiles=1`、`totalPositionalDeletes=1`、`addedDeleteFiles=1`、`addedFileSizeInBytes>0`、`totalDataFiles=1`、`totalRecords=100`、`totalFileSizeInBytes>0`；并验证 equality 相关与 removed 相关度量均为 0。该用例覆盖 position delete 写入场景。

- `findMetrics(SparkPlan plan, String metricName)`：私有辅助方法，先检查当前 plan 的 metrics 是否包含目标名，否则递归遍历 `children()` 与 `innerChildren()`（处理 SparkPlan 嵌套结构）查找包含该度量的子计划，返回其 metrics map 或 null。该工具方法解决 AQE（自适应查询执行）下度量可能不在根节点的问题。

测试通过直接读取 Spark `SQLMetric` 的值，端到端验证了从 Iceberg 提交报告到 Spark SQL UI metrics 的完整链路。

## 总结

本次提交回移 main 分支 PR #15104，利用 Spark 4.0 新增的 `supportedCustomMetrics()`/`reportDriverMetrics()` DataSource V2 API，把 Iceberg 写操作的提交度量（新增/删除/总计的数据文件、删除文件、记录、字节数等）桥接到 Spark 的自定义度量体系，使其自动聚合并展示在 SQL UI。核心设计是在三个写实现中挂载 `InMemoryMetricsReporter` 捕获 `CommitReport`，由 `SparkWriteUtil` 将 `CommitMetricsResult` 转换为 `CustomTaskMetric`，并新增 22 个 `CustomSumMetric` 度量类定义度量元信息。该特性显著提升了写入成本的可观测性，用户无需查日志即可在 SQL UI 了解 Iceberg 写入的文件数、记录数与字节数等关键指标。
