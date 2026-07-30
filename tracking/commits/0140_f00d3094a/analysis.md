# 提交 0140：Spark 3.4: Display more read metrics on Spark SQL UI (#9009)

## 提交信息

- **序号**：0140 / 4088
- **哈希**：f00d3094a1728b60dba96fc251707dd08d8a6310
- **短哈希**：f00d3094a
- **日期**：2023-11-08 16:31:28 -0800
- **作者**：Karuppayya
- **提交说明**：Spark 3.4: Display more read metrics on Spark SQL UI (#9009)
- **PR/Issue**：#9009（cherry-pick 自 #8717）

## 总体目的

这个提交把此前在 Spark 3.5（PR #8717）上引入的"更丰富的读指标"能力 cherry-pick 到 Spark 3.4 模块，让 Iceberg 在 Spark 3.4 SQL UI 上也能展示出与数据清单/删除清单/删除文件相关的细粒度读侧指标。这是一次跨 Spark 版本的能力对齐，目的是让 3.4 用户与 3.5 用户获得一致的读性能可观测性。

背景与动机：在此之前，Iceberg 在 Spark 3.4 的 `SparkScan` 只暴露了很少几项自定义指标：`NumSplits`、`NumDeletes`、`TotalFileSize`、`TotalPlanningDuration`、`ScannedDataManifests`、`SkippedDataManifests`、`ScannedDataFiles`、`SkippedDataFiles`。这套指标对"读时发生了什么"的可见性有限，尤其缺少对删除清单（delete manifests）与删除文件（delete files）的统计，也无法区分相等删除（equality delete）、位置删除（positional delete）、索引删除（indexed delete）等不同删除类型，难以定位 merge-on-read 场景下的性能瓶颈。而 Spark 3.5 上已经通过 #8717 把指标体系扩展到覆盖数据清单/数据文件/删除清单/删除文件四大类，并细分了删除类型与"已扫描/已跳过/结果/总量"等维度。为了让 Spark 3.4 用户也能在 SQL UI 上看到这套更完整的读指标，本次提交把同样的改动以 cherry-pick 的形式应用到 `spark/v3.4/spark` 模块。

具体能力上，改动后的 Spark 3.4 `SparkScan` 会同时上报 driver 端的 `CustomTaskMetric`（来自 `ScanReport`）和声明支持的 `CustomMetric`（聚合维度），覆盖四组指标：

- 通用：`totalPlanningDuration`（规划耗时）。
- 数据清单（data manifests）：`totalDataManifests`、`scannedDataManifests`、`skippedDataManifests`。
- 数据文件（data files）：`resultDataFiles`、`skippedDataFiles`、`totalDataFileSize`。
- 删除清单（delete manifests）：`totalDeleteManifests`、`scannedDeleteManifests`、`skippedDeleteManifests`。
- 删除文件（delete files）：`totalDeleteFileSize`、`resultDeleteFiles`、`equalityDeleteFiles`、`indexedDeleteFiles`、`positionalDeleteFiles`、`skippedDeleteFiles`。

这套指标让用户在 Spark SQL UI 上既能看到规划阶段扫描了多少清单、跳过多少清单，也能看到删除文件的分类构成（相等删除/位置删除/索引删除各多少），对排查 merge-on-read 读放大、删除文件过多等问题非常有用。对 Iceberg 演进的意义是把 Spark 3.4 的读侧可观测性提升到与 3.5 一致的水平，缩小两个维护版本之间的能力差距，方便仍停留在 3.4 的用户在生产环境做性能诊断。

## 如何达成设计目的

整体设计沿用 Spark 3.5 #8717 的方案：把指标按"驱动端任务级快照值"（`CustomTaskMetric` 实现，由 `ScanReport` 提取）和"执行端聚合声明"（`CustomSumMetric` 子类，由 Spark 在 SQL UI 上聚合所有任务的值）两层组织。改动结构上分三块：

1. 在 `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/metrics/` 下新增一组指标类，每个指标一对：一个 `CustomSumMetric` 子类（声明 `name()` 与 `description()`，供 `supportedCustomMetrics()` 注册），一个对应的 `Task*` 类实现 `CustomTaskMetric`，提供静态 `from(ScanReport)` 方法从 `scanReport.scanMetrics()` 取出对应 `CounterResult` 的值。
2. 把原有的 `ScannedDataFiles`/`TotalFileSize`/`TaskScannedDataFiles`/`TaskTotalFileSize` 重命名为更准确的 `ResultDataFiles`/`TotalDataFileSize`/`TaskResultDataFiles`/`TaskTotalDataFileSize`，并把 `TotalFileSize` 改名为 `TotalDataManifests`（语义从"总文件大小"变成"数据清单总数"，原"总文件大小"被新的 `TotalDataFileSize` 取代）。
3. 在 `SparkScan` 中重写 `driverMetrics`（任务级）与 `supportedCustomMetrics`（聚合声明）两个列表，按"通用/数据清单/数据文件/删除清单/删除文件"分组注册新增的指标类，并在 `TestSparkReadMetrics` 中扩展断言覆盖所有新增指标，新增 `testDeleteMetrics` 验证 merge-on-read 删除指标。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java`

**修改目的**：在 Spark 3.4 的 `SparkScan` 中注册并上报新增的读指标，使它们出现在 Spark SQL UI 上。

**工作逻辑**：

- import 区：移除 `ScannedDataFiles`、`TaskScannedDataFiles`、`TotalFileSize`、`TaskTotalFileSize`，新增 `EqualityDeleteFiles`、`IndexedDeleteFiles`、`PositionalDeleteFiles`、`ResultDataFiles`、`ResultDeleteFiles`、`ScannedDeleteManifests`、`SkippedDeleteFiles`、`SkippedDeleteManifests`、`TaskEqualityDeleteFiles`、`TaskIndexedDeleteFiles`、`TaskPositionalDeleteFiles`、`TaskResultDataFiles`、`TaskResultDeleteFiles`、`TaskScannedDeleteManifests`、`TaskSkippedDataFiles`、`TaskSkippedDeleteManifests`、`TaskTotalDataFileSize`、`TaskTotalDataManifests`、`TaskTotalDeleteFileSize`、`TaskTotalDeleteManifests`、`TotalDataFileSize`、`TotalDataManifests`、`TotalDeleteFileSize`、`TotalDeleteManifests`。这些 import 直接对应到下方注册的指标类。

- `driverMetrics` 构建：原来只 `add` 四项（`TaskTotalFileSize`、`TaskTotalPlanningDuration`、`TaskSkippedDataFiles`、`TaskScannedDataFiles`、`TaskSkippedDataManifests`、`TaskScannedDataManifests`）。改动后按分组重写为：

  - 通用：`TaskTotalPlanningDuration`。
  - 数据清单：`TaskTotalDataManifests`、`TaskScannedDataManifests`、`TaskSkippedDataManifests`。
  - 数据文件：`TaskResultDataFiles`、`TaskSkippedDataFiles`、`TaskTotalDataFileSize`。
  - 删除清单：`TaskTotalDeleteManifests`、`TaskScannedDeleteManifests`、`TaskSkippedDeleteManifests`。
  - 删除文件：`TaskTotalDeleteFileSize`、`TaskResultDeleteFiles`、`TaskEqualityDeleteFiles`、`TaskIndexedDeleteFiles`、`TaskPositionalDeleteFiles`、`TaskSkippedDeleteFiles`。

  每一项都通过对应 `Task*.from(scanReport)` 从 `ScanReport` 取值，分组之间有注释分隔。所有值统一 `toArray` 返回给 Spark。

- `supportedCustomMetrics()`：原来返回 `NumSplits`、`NumDeletes`、`TotalFileSize`、`TotalPlanningDuration`、`ScannedDataManifests`、`SkippedDataManifests`、`ScannedDataFiles`、`SkippedDataFiles`。改动后同样按"通用/数据清单/数据文件/删除清单/删除文件"分组返回，包含全部新增的 `CustomSumMetric` 子类。注意 `NumSplits`、`NumDeletes` 保留，但分组注释里把它们放在 task metrics 段。

### 新增的 `CustomSumMetric` 子类（聚合声明层）

**修改目的**：声明新的聚合指标，使 Spark SQL UI 能在执行计划节点上展示这些指标名及描述。

**工作逻辑**：下列新增类都继承 `org.apache.spark.sql.connector.metric.CustomSumMetric`，实现 `name()` 返回静态 `NAME` 常量，`description()` 返回描述字符串。每个类的 `NAME` 即对应 `ScanReport.scanMetrics()` 中某个计数器的字段名。

- `EqualityDeleteFiles`（`equalityDeleteFiles`，"number of equality delete files"）。
- `IndexedDeleteFiles`（`indexedDeleteFiles`，"number of indexed delete files"）。
- `PositionalDeleteFiles`（`positionalDeleteFiles`，"number of positional delete files"）。
- `ResultDeleteFiles`（`resultDeleteFiles`，"number of result delete files"）。
- `ScannedDeleteManifests`（`scannedDeleteManifests`，"number of scanned delete manifests"）。
- `SkippedDeleteFiles`（`skippedDeleteFiles`，"number of skipped delete files"）。
- `SkippedDeleteManifests`（`skippedDeleteManifests`，"number of skipped delete manifest"）。
- `TotalDataFileSize`（`totalDataFileSize`，"total data file size (bytes)"）。
- `TotalDeleteFileSize`（`totalDeleteFileSize`，"total delete file size (bytes)"）。
- `TotalDeleteManifests`（`totalDeleteManifests`，"total delete manifests"）。

### 新增的 `CustomTaskMetric` 实现类（任务级快照层）

**修改目的**：从 `ScanReport` 中提取对应计数器的当前值，作为 driver 端任务级指标上报给 Spark。

**工作逻辑**：下列新增类都实现 `org.apache.spark.sql.connector.metric.CustomTaskMetric`，模式一致：持有一个 `long value` 字段，私有构造函数赋值，`name()` 返回对应聚合类的 `NAME`，`value()` 返回该值；静态 `from(ScanReport scanReport)` 通过 `scanReport.scanMetrics().<对应方法>()` 取出 `CounterResult`，当结果为 `null` 时取 `0L`，否则取 `counter.value()`，再用私有构造包成实例返回。

- `TaskEqualityDeleteFiles` → 取 `equalityDeleteFiles()`。
- `TaskIndexedDeleteFiles` → 取 `indexedDeleteFiles()`。
- `TaskPositionalDeleteFiles` → 取 `positionalDeleteFiles()`。
- `TaskResultDeleteFiles` → 取 `resultDeleteFiles()`。
- `TaskScannedDeleteManifests` → 取 `scannedDeleteManifests()`。
- `TaskSkippedDeleteFiles` → 取 `skippedDeleteFiles()`。
- `TaskSkippedDeleteManifests` → 取 `skippedDeleteManifests()`。
- `TaskTotalDataManifests` → 取 `totalDataManifests()`。
- `TaskTotalDeleteFileSize` → 取 `totalDeleteFileSizeInBytes()`。
- `TaskTotalDeleteManifests` → 取 `totalDeleteManifests()`。

### 重命名/语义调整的指标类

**修改目的**：把原有指标类改名为更准确的名字，与新增的删除侧指标在命名上对齐。

**工作逻辑**：

- `ScannedDataFiles.java` 重命名为 `ResultDataFiles.java`：`NAME` 由 `scannedDataFiles` 改为 `resultDataFiles`，描述由 "number of scanned data files" 改为 "number of result data files"。语义从"已扫描数据文件"细化为"结果数据文件"，与 `ScanReport.scanMetrics().resultDataFiles()` 对齐。
- `TaskScannedDataFiles.java` 重命名为 `TaskResultDataFiles.java`：类名、构造函数、`from` 方法名同步改名；`name()` 返回 `ResultDataFiles.NAME`；`from` 内部仍调用 `scanReport.scanMetrics().resultDataFiles()`。
- `TaskTotalFileSize.java` 重命名为 `TaskTotalDataFileSize.java`：`name()` 返回 `TotalDataFileSize.NAME`，`from` 仍取 `totalFileSizeInBytes()`。语义聚焦到"数据文件总大小"，与新增的 `TaskTotalDeleteFileSize` 对称。
- `TotalFileSize.java` 重命名为 `TotalDataManifests.java`：`NAME` 由 `totalFileSize` 改为 `totalDataManifest`，描述由 "total file size (bytes)" 改为 "total data manifests"。这里把原本被复用为"总文件大小"聚合的类，改义为"数据清单总数"聚合，配合新增的 `TotalDataFileSize` 承担"数据文件总大小"语义。注意 `NAME` 字面量是 `totalDataManifest`（单数），与 `TaskTotalDataManifests` 引用的常量一致，但与新增的 `TotalDeleteManifests.NAME = "totalDeleteManifests"`（复数）在命名风格上略有不一致，属本次 cherry-pick 保留的原始实现细节。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReadMetrics.java`

**修改目的**：扩展测试覆盖新增指标，并新增 merge-on-read 删除指标的测试用例。

**工作逻辑**：

- `testReadMetricsForV1Table`：建表版本由 `format-version=2` 改为 `1`（确保无删除文件场景），写入 10000 行后读取并断言所有指标。新增断言覆盖：通用 `totalPlanningDuration` 不为 0；数据清单 `totalDataManifest`（注意断言用 `totalDataManifest` 与 `NAME` 字面量一致）等于 2、`scannedDataManifests` 等于 2、`skippedDataManifests` 等于 0；数据文件 `resultDataFiles` 等于 1、`skippedDataFiles` 等于 1、`totalDataFileSize` 不为 0；删除清单 `totalDeleteManifests`、`scannedDeleteManifests`、`skippedDeleteManifests` 全为 0；删除文件 `totalDeleteFileSize`、`resultDeleteFiles`、`equalityDeleteFiles`、`indexedDeleteFiles`、`positionalDeleteFiles`、`skippedDeleteFiles` 全为 0。
- `testReadMetricsForV2Table`：同样扩展断言结构，验证 v2 表在无删除时的指标值与 v1 一致（删除侧全为 0）。
- 新增 `testDeleteMetrics`：建表时设置 `write.delete.mode`/`write.update.mode`/`write.merge.mode` 均为 `merge-on-read` 且 `format-version=2`，写入 10000 行后执行 `DELETE FROM ... WHERE id = 1`，再读取并断言：删除清单 `totalDeleteManifests`、`scannedDeleteManifests` 均为 1、`skippedDeleteManifests` 为 0；删除文件 `resultDeleteFiles` 为 1、`indexedDeleteFiles` 为 1、`positionalDeleteFiles` 为 1、`equalityDeleteFiles` 为 0、`skippedDeleteFiles` 为 0、`totalDeleteFileSize` 不为 0。这个用例是验证删除侧指标端到端可用的关键测试。

## 小结

把 Spark 3.5 #8717 的扩展读指标能力 cherry-pick 到 Spark 3.4，新增数据清单/数据文件/删除清单/删除文件四大类、覆盖相等/位置/索引删除的细分指标，并把原 `ScannedDataFiles`/`TotalFileSize` 等重命名为更准确的 `ResultDataFiles`/`TotalDataFileSize`/`TotalDataManifests`，让 Spark 3.4 用户在 SQL UI 上获得与 3.5 一致的读性能可观测性。
