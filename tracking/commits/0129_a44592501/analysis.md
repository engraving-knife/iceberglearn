# 提交 0129：Spark 3.5: Display more read metrics on Spark SQL UI (#8717)

## 提交信息

- **序号**：0129 / 4088
- **哈希**：a445925016f21ef51a1a4acac94689528134a74c
- **短哈希**：a44592501
- **日期**：2023-11-02 18:19:55 -0700
- **作者**：Karuppayya
- **提交说明**：Spark 3.5: Display more read metrics on Spark SQL UI (#8717)
- **PR/Issue**：#8717

## 总体目的

这个提交大幅扩展了 Iceberg 在 Spark 3.5 SQL UI 上展示的读取指标（read metrics），使开发者能在 Spark SQL UI 上观察到 Iceberg 规划阶段（planning）对数据文件与删除文件（delete files）的完整处理情况，尤其是新增了对 delete 文件相关指标的可见性。

在 Iceberg 的读取流程中，Spark 侧的 `SparkScan` 会将底层 `ScanReport` 中的规划指标转换为 Spark 的 `CustomMetric` / `CustomTaskMetric`，从而在 Spark SQL UI 上聚合展示。本提交前，UI 上仅能看到有限的几项指标：`totalFileSize`（总文件大小）、`totalPlanningDuration`（规划耗时）、`scannedDataManifests` / `skippedDataManifests`（扫描/跳过的数据 manifest 数）、`scannedDataFiles`（扫描的数据文件数）、`skippedDataFiles`（跳过的数据文件数），以及 task 级的 `numSplits`、`numDeletes`。这些指标只覆盖了数据（data）侧，且命名不够精确。

随着 Iceberg v2 表格式与 merge-on-read 删除模式的广泛使用，delete 文件（equality delete 与 positional delete）在读取规划中扮演越来越重要的角色——它们直接影响读取性能与正确性。然而此前 Spark SQL UI 对 delete 文件相关的规划指标完全不可见，开发者在排查读取性能问题（如为何读取变慢、delete 文件是否过多、是否需要触发清理）时缺乏关键数据支撑。

本提交通过三类改动解决这一问题：一是**重命名**已有指标使其语义更精确（`scannedDataFiles` → `resultDataFiles`，`totalFileSize` → `totalDataFileSize`），与底层 `ScanReport.ScanMetrics` 的计数器名称对齐；二是**新增**一整套 delete 侧指标，覆盖 delete manifest 的总数/扫描数/跳过数，以及 delete 文件的总大小/结果数/按类型（equality、positional、indexed）细分/跳过数；三是补充数据 manifest 的总数指标 `totalDataManifests`。所有新增指标都成对实现：一个继承 `CustomSumMetric` 的聚合指标类（用于在 UI 上跨 task 求和展示）和一个实现 `CustomTaskMetric` 的 task 级指标类（用于从每个 task 的 `ScanReport` 中提取并上报对应计数器值）。同时更新 `SparkScan` 注册这些指标，并扩展测试覆盖。

这一改动显著提升了 Iceberg + Spark 用户在 SQL UI 上的可观测性，对诊断 v2 表的 delete 文件影响、驱动维护决策（如何时执行 `rewrite_delete_files`）具有重要价值。

## 如何达成设计目的

整体设计遵循 Iceberg 已有的指标实现模式：每个指标由两个类配合——`XxxMetric extends CustomSumMetric`（定义指标名与描述，由 Spark 在 UI 聚合）和 `TaskXxx implements CustomTaskMetric`（带 `from(ScanReport)` 静态工厂，从 `ScanReport.scanMetrics()` 取对应 `CounterResult` 的值，null 时回退为 0）。改动结构上分三层：

1. **指标类层**（`metrics` 包）：删除 2 个语义不精确的旧类（`ScannedDataFiles`、`TotalFileSize` 及其 Task 配套类），重命名为 `ResultDataFiles`、`TotalDataFileSize`；新增 `TotalDataManifests` 及 9 个 delete 侧指标类，每个均含 Metric + TaskMetric 两个类。
2. **注册层**（`SparkScan`）：在 `supportedCustomMetrics()` 与 `driverMetrics()` 中按"common / data manifests / data files / delete manifests / delete files"分组注册全部指标，并用注释保持清晰。
3. **测试层**（`TestSparkReadMetrics`）：扩展两个已有测试断言全部新指标，并新增 `testDeleteMetrics` 验证 delete 场景下指标的真实填充。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java`

**修改目的**：注册全部新增与重命名后的指标，使它们在 Spark SQL UI 上可见。

**工作逻辑**：

1. **import 调整**：移除 `ScannedDataFiles`、`TaskScannedDataFiles`、`TotalFileSize`、`TaskTotalFileSize` 的 import，新增 `ResultDataFiles`、`TaskResultDataFiles`、`TotalDataFileSize`、`TaskTotalDataFileSize`，以及全部 delete 侧指标的 import（`EqualityDeleteFiles`、`IndexedDeleteFiles`、`PositionalDeleteFiles`、`ResultDeleteFiles`、`ScannedDeleteManifests`、`SkippedDeleteFiles`、`SkippedDeleteManifests`、`TotalDataManifests`、`TotalDeleteFileSize`、`TotalDeleteManifests` 及其 Task 配套类）。

2. **`driverMetrics()` 方法**：原先仅上报 6 项指标，现按分组上报 17 项，并用注释划分：
   - common：`TaskTotalPlanningDuration`
   - data manifests：`TaskTotalDataManifests`（新）、`TaskScannedDataManifests`、`TaskSkippedDataManifests`
   - data files：`TaskResultDataFiles`（由 `TaskScannedDataFiles` 重命名）、`TaskSkippedDataFiles`、`TaskTotalDataFileSize`（由 `TaskTotalFileSize` 重命名）
   - delete manifests：`TaskTotalDeleteManifests`、`TaskScannedDeleteManifests`、`TaskSkippedDeleteManifests`（均新增）
   - delete files：`TaskTotalDeleteFileSize`、`TaskResultDeleteFiles`、`TaskEqualityDeleteFiles`、`TaskIndexedDeleteFiles`、`TaskPositionalDeleteFiles`、`TaskSkippedDeleteFiles`（均新增）

   每个 `TaskXxx.from(scanReport)` 从 `ScanReport.scanMetrics()` 取对应 `CounterResult`，null 时回退 0L。

3. **`supportedCustomMetrics()` 方法**：返回的 `CustomMetric[]` 同步扩展，按相同分组注册聚合指标类，包含 task 级的 `NumSplits`、`NumDeletes`，以及上述 15 个 driver 侧聚合指标。这样 Spark 在 SQL UI 上能聚合展示所有指标。

### 重命名的指标类：`ScannedDataFiles` → `ResultDataFiles`、`TotalFileSize` → `TotalDataFileSize`

**修改目的**：使指标名与底层 `ScanReport.ScanMetrics` 计数器的真实语义对齐。

**工作逻辑**：

- 旧 `ScannedDataFiles`（name=`scannedDataFiles`，描述"number of scanned data files"）实际从 `scanReport.scanMetrics().resultDataFiles()` 取值，指标名与取值来源语义不符。新 `ResultDataFiles`（name=`resultDataFiles`，描述"number of result data files"）与计数器名 `resultDataFiles` 一致，准确表达"规划后实际要读取的结果数据文件数"。对应 Task 类 `TaskScannedDataFiles` 重命名为 `TaskResultDataFiles`，`name()` 返回 `ResultDataFiles.NAME`，`from()` 仍调用 `scanReport.scanMetrics().resultDataFiles()`。

- 旧 `TotalFileSize`（name=`totalFileSize`，描述"total file size (bytes)"）在新体系下需区分 data 与 delete，故重命名为 `TotalDataFileSize`（name=`totalDataFileSize`，描述"total data file size (bytes)"）。对应 Task 类 `TaskTotalFileSize` 重命名为 `TaskTotalDataFileSize`，`from()` 仍调用 `scanReport.scanMetrics().totalFileSizeInBytes()`。旧文件被删除，新文件创建。

### 新增的 data manifest 总数指标：`TotalDataManifests` / `TaskTotalDataManifests`

**修改目的**：补充数据 manifest 的总数指标，与 `scannedDataManifests` / `skippedDataManifests` 共同构成"总数-扫描-跳过"三角。

**工作逻辑**：`TotalDataManifests extends CustomSumMetric`，name=`totalDataManifest`，描述"total data manifests"。`TaskTotalDataManifests` 的 `from()` 调用 `scanReport.scanMetrics().totalDataManifests()`。这样用户能对比总数与扫描数，判断有多少 manifest 在规划阶段被跳过。

### 新增的 delete manifest 指标：`TotalDeleteManifests` / `ScannedDeleteManifests` / `SkippedDeleteManifests`

**修改目的**：将 delete manifest 的规划处理情况暴露到 UI。

**工作逻辑**：三个指标类均继承 `CustomSumMetric`，name 分别为 `totalDeleteManifests`、`scannedDeleteManifests`、`skippedDeleteManifests`。对应 Task 类分别从 `scanReport.scanMetrics().totalDeleteManifests()`、`.scannedDeleteManifests()`、`.skippedDeleteManifests()` 取值。结构与 data manifest 三件套完全对称。

### 新增的 delete 文件指标：`TotalDeleteFileSize` / `ResultDeleteFiles` / `EqualityDeleteFiles` / `IndexedDeleteFiles` / `PositionalDeleteFiles` / `SkippedDeleteFiles`

**修改目的**：暴露 delete 文件的总大小、结果数、按类型细分及跳过数，使 v2 表的 delete 影响可观测。

**工作逻辑**：

- `TotalDeleteFileSize`（name=`totalDeleteFileSize`，描述"total delete file size (bytes)"），Task 类 `from()` 调用 `scanReport.scanMetrics().totalDeleteFileSizeInBytes()`。这是 delete 文件的总字节大小，与 `TotalDataFileSize` 对称。
- `ResultDeleteFiles`（name=`resultDeleteFiles`，描述"number of result delete files"），Task 类 `from()` 调用 `scanReport.scanMetrics().resultDeleteFiles()`，表示规划后实际要应用的 delete 文件数。
- `EqualityDeleteFiles`（name=`equalityDeleteFiles`，描述"number of equality delete files"），Task 类 `from()` 调用 `scanReport.scanMetrics().equalityDeleteFiles()`，统计 equality delete 文件数。
- `IndexedDeleteFiles`（name=`indexedDeleteFiles`，描述"number of indexed delete files"），Task 类 `from()` 调用 `scanReport.scanMetrics().indexedDeleteFiles()`，统计被索引（将参与 delete 应用）的 delete 文件数。
- `PositionalDeleteFiles`（name=`positionalDeleteFiles`，描述"number of positional delete files"），Task 类 `from()` 调用 `scanReport.scanMetrics().positionalDeleteFiles()`，统计 positional delete 文件数。
- `SkippedDeleteFiles`（name=`skippedDeleteFiles`，描述"number of skipped delete files"），Task 类 `from()` 调用 `scanReport.scanMetrics().skippedDeleteFiles()`，统计规划阶段跳过的 delete 文件数。

所有 Task 类结构一致：私有构造持 `long value`、`name()` 返回对应 Metric 类的 `NAME` 常量、`value()` 返回值、`from(ScanReport)` 静态工厂取 `CounterResult`（null 回退 0L）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReadMetrics.java`

**修改目的**：验证全部新指标在 UI 上的正确性，覆盖纯数据场景与含 delete 场景。

**工作逻辑**：

1. **`testReadMetricsForV1Table`**：将建表语句的 `format-version` 从 2 改为 1（v1 表无 delete 文件，delete 侧指标应全为 0）。断言扩展为按分组验证：common 的 `totalPlanningDuration` 非 0；data manifest 的 `totalDataManifest`=2、`scannedDataManifests`=2、`skippedDataManifests`=0；data files 的 `resultDataFiles`=1、`skippedDataFiles`=1、`totalDataFileSize` 非 0；delete manifest 与 delete files 各项均为 0。这验证了指标注册正确且无 delete 时正确归零。

2. **第二个已有测试**（v2 表无 delete）：同样扩展断言，结构与上一测试一致，验证 v2 表在无 delete 时 delete 侧指标也为 0。

3. **新增 `testDeleteMetrics`**：创建 v2 表并设置 `write.delete.mode`/`write.update.mode`/`write.merge.mode` 均为 `merge-on-read`，写入 10000 行后执行 `DELETE FROM ... WHERE id = 1` 产生 delete 文件，再执行 `SELECT *` 触发读取。断言验证：`totalDataManifest`=1、`scannedDataManifests`=1、`resultDataFiles`=1、`skippedDataFiles`=0；delete 侧 `totalDeleteManifests`=1、`scannedDeleteManifests`=1、`skippedDeleteManifests`=0、`totalDeleteFileSize` 非 0、`resultDeleteFiles`=1、`equalityDeleteFiles`=0、`indexedDeleteFiles`=1、`positionalDeleteFiles`=1、`skippedDeleteFiles`=0。该测试证明 delete 场景下各项指标被真实填充，是本提交功能价值的核心验证。

## 小结

本提交通过重命名 2 项语义不精确的数据指标、新增 1 项数据 manifest 总数指标与 9 项 delete 侧指标（manifest 三件套 + 文件 6 项），并在 `SparkScan` 注册、在测试中覆盖，使 Iceberg v2 表的 delete 文件规划处理情况在 Spark 3.5 SQL UI 上全面可见，显著提升了读取性能诊断与表维护决策的可观测性。
