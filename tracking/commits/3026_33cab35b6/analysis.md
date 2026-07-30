# 提交 3026：Spark: Enable remote scan planning with REST catalog (#14822)

## 提交信息

- **序号**：3026 / 4088
- **哈希**：33cab35b67e3f58d06fc17f8f1ba8c3a99622c94
- **短哈希**：33cab35b6
- **日期**：2025-12-18
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Enable remote scan planning with REST catalog (#14822)
- **PR/Issue**：#14822

## 总体目的

本提交是 Iceberg 远程扫描计划（remote scan planning）功能在 Spark 引擎与 REST Catalog 集成上的关键一步。远程扫描计划允许将表的扫描计划（planFiles）工作下推到 REST Catalog 服务端执行，而非在客户端本地完成，这对于元数据规模较大或客户端资源受限的场景非常重要，可以显著降低客户端的内存与计算压力。

在此提交之前，Spark 的 `SparkScanBuilder.newBatchScan()` 仅在 `table instanceof BaseTable && readConf.distributedPlanningEnabled()` 时使用 `SparkDistributedDataScan`（分布式扫描计划），否则直接调用 `table.newBatchScan()`。这种逻辑没有区分"需要远程扫描计划"的表（如 `RESTTable`）与普通本地表，导致 REST Catalog 即使在服务端开启了远程扫描计划，Spark 端也无法正确走远程计划路径。

本提交通过引入一个标记接口 `RequiresRemoteScanPlanning`，让 `RESTTable` 显式声明自己需要远程扫描计划，并修改 `SparkScanBuilder` 的扫描选择逻辑：当表实现了该接口时，直接调用 `table.newBatchScan()`（由 `RESTTableScan` 在内部与服务端交互完成远程计划），从而绕过本地的 `SparkDistributedDataScan`。同时新增了针对 REST Catalog 的测试套件 `TestRemoteScanPlanning`，复用 `TestSelect` 的用例验证远程扫描计划在各种查询场景下的正确性，并暂时禁用了两个已知不支持的场景（binary filter 与 metadata tables）。

## 如何达成设计目的

整体思路是"标记接口 + 条件分支"。在 core 模块新增空标记接口 `RequiresRemoteScanPlanning`，让 `RESTTable` 实现它；在 Spark 模块的 `SparkScanBuilder.newBatchScan()` 中优先判断该标记接口，命中则走 `table.newBatchScan()` 的远程计划路径。测试侧通过继承 `TestSelect` 并配置 `REST_SCAN_PLANNING_ENABLED=true` 来覆盖 REST Catalog 场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/RequiresRemoteScanPlanning.java` (+22/-0 lines)

**修改目的**：新增标记接口，用于声明表需要远程扫描计划。

**工作逻辑**：
这是一个空接口（marker interface），仅包含类声明 `public interface RequiresRemoteScanPlanning {}`，没有任何方法。其作用是利用 Java 的类型系统让调用方可以通过 `instanceof` 判断一个 `Table` 实例是否应该走远程扫描计划路径，避免依赖具体类型（如直接判断 `RESTTable`）或配置标志，实现 core 模块与 Spark 模块之间的松耦合。

### `core/src/main/java/org/apache/iceberg/rest/RESTTable.java` (+2/-1 lines)

**修改目的**：让 `RESTTable` 实现远程扫描计划标记接口。

**工作逻辑**：
将类声明从 `class RESTTable extends BaseTable` 改为 `class RESTTable extends BaseTable implements RequiresRemoteScanPlanning`，并新增对应 import。这样所有通过 REST Catalog 加载的表都会被 `SparkScanBuilder` 识别为需要远程扫描计划。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+4/-1 lines)

**修改目的**：修改扫描构建逻辑，优先走远程扫描计划路径。

**工作逻辑**：
`newBatchScan()` 方法原先只有一个分支：当 `table instanceof BaseTable && readConf.distributedPlanningEnabled()` 时返回 `SparkDistributedDataScan`，否则返回 `table.newBatchScan()`。现在在最前面新增了一个优先分支 `if (table instanceof RequiresRemoteScanPlanning) { return table.newBatchScan(); }`。对于 `RESTTable`，`newBatchScan()` 返回的是 `RESTTableScan`，它会在内部与服务端交互完成远程计划，因此不需要也不应该走 Spark 本地的分布式扫描计划。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoteScanPlanning.java` (+64/-0 lines)

**修改目的**：新增 REST Catalog 远程扫描计划的测试套件。

**工作逻辑**：
该测试类继承 `TestSelect`，通过 `@Parameters` 配置使用 REST Catalog 并显式设置 `RESTCatalogProperties.REST_SCAN_PLANNING_ENABLED = "true"`（注释说明该标志通常由服务端设置，这里从客户端设置用于测试）。这样 `TestSelect` 中所有查询用例都会在远程扫描计划启用的情况下运行。同时用 `@Disabled` 标注了两个已知不支持的用例：`testBinaryInFilter`（因 `ExpressionParser.fromJSON` 缺少 Schema 无法正确解析 binary filter）和 `testMetadataTables`（元数据表暂不支持）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+32/-1 lines)

**修改目的**：新增 variant 类型过滤测试用例。

**工作逻辑**：
新增 `variantTypeInFilter()` 测试，使用 `assumeThat(validationCatalog).isNotInstanceOf(HiveCatalog.class)` 跳过不支持 Variant 类型的 Hive Catalog。测试创建含 `VARIANT` 列的 format-version 3 表，插入 JSON 数据后通过 `try_variant_get` 提取字段并做过滤查询，验证结果正确性。该用例补充了 variant 类型在远程扫描计划场景下的覆盖（虽然也适用于所有非 Hive catalog）。

## 总结

本提交通过引入 `RequiresRemoteScanPlanning` 标记接口，让 Spark 能正确识别 `RESTTable` 并走远程扫描计划路径，同时新增了完整的测试套件验证该功能，并标记了两个待支持的场景。这是远程扫描计划功能在 Spark 引擎落地的核心一环，为后续修复 binary filter 和 metadata tables 支持奠定了基础。
