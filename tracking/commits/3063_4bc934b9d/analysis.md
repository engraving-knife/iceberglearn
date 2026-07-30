# 提交 3063：Spark 3.4 | 3.5: Enable remote scan planning (#14963)

## 提交信息

- **序号**：3063 / 4088
- **哈希**：4bc934b9d3c4394566658cb2b11639f4daef7c8c
- **短哈希**：4bc934b9d
- **日期**：2026-01-05
- **作者**：Prashant Singh
- **提交说明**：Spark 3.4 | 3.5: Enable remote scan planning (#14963)
- **PR/Issue**：#14963

## 总体目的

本提交将此前仅在 Spark 4.0/4.1 落地的远程扫描计划（remote scan planning）能力回移（backport）到 Spark 3.4 与 3.5。远程扫描计划允许将表的 `planFiles`（扫描任务规划）工作下推到 REST Catalog 服务端执行，而非在 Spark 客户端本地完成。这对元数据规模较大或客户端资源受限的场景非常重要——服务端拥有更完整的元数据与计算资源，可以显著降低客户端内存压力并加速规划。

此前在提交 3026 中，已在 core 模块引入标记接口 `RequiresRemoteScanPlanning`，让 `RESTTable` 实现该接口，并在 Spark 4.0/4.1 的 `SparkScanBuilder.newBatchScan()` 中优先判断该接口：若表实现了 `RequiresRemoteScanPlanning`，则直接调用 `table.newBatchScan()`（由 `RESTTableScan` 在内部与服务端交互完成远程计划），从而绕过本地的 `SparkDistributedDataScan`。但 Spark 3.4 与 3.5 的 `SparkScanBuilder` 仍停留在旧逻辑——仅判断 `table instanceof BaseTable && readConf.distributedPlanningEnabled()`，没有识别 `RequiresRemoteScanPlanning`，导致 REST Catalog 即使服务端开启了远程扫描计划，Spark 3.4/3.5 客户端也无法走远程计划路径。

本提交把与 4.0/4.1 相同的分支逻辑同步到 3.4/3.5：在 `newBatchScan()` 中优先判断 `table instanceof RequiresRemoteScanPlanning`，命中则 `table.newBatchScan()`，否则保留原有的分布式/本地分支。同时把 4.0/4.1 中已做的 `TestSelect` 稳定性修复（追加 `ORDER BY id`、部分断言改 `containsExactlyInAnyOrder`）同步到 3.4/3.5，因为远程扫描计划同样会导致返回行顺序不确定。此外，为 3.4/3.5 各新增一个 `TestRemoteScanPlanning` 测试套件，继承 `TestSelect` 并在 catalog 配置中开启 `REST_SCAN_PLANNING_ENABLED=true`，复用 `TestSelect` 的全部用例验证远程扫描计划在 3.4/3.5 下的正确性；其中 `testBinaryInFilter` 被标记为 `@Disabled`，因为 `ExpressionParser.fromJSON` 在解析 binary filter 时缺少 Schema 上下文，这是已知限制。

需要强调的是，3.4/3.5 的 `TestSelect` 改动范围比 4.0/4.1（见 3058）更广：除了 `testSelect`、`testSplitSize`、`testProjection`、`testAggPushDown`、`testSchemaEvolutionAndFilter`、`testFilter` 外，还覆盖了 `testSnapshotInTableName`、`testSnapshotAtTimestamp`、`testVersionAsOf`、`testTagReference`、`testBranchReference`、`testTimestampAsOf` 等时间旅行用例的 `ORDER BY id` 修正，因为 3.4/3.5 此前未做过这些修复（4.0/4.1 在 3029 已做）。

## 如何达成设计目的

整体思路是"标记接口分支 + 测试稳定性修复 + 远程计划测试套件"。涉及 `spark/v3.4` 与 `spark/v3.5` 两套对称改动：`SparkScanBuilder.java`（主代码分支逻辑）、`TestSelect.java`（断言稳定性）、`TestRemoteScanPlanning.java`（新增测试套件）。改动方向与 3026/3029 对 4.0/4.1 的处理一致。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+4/-1 lines)

**修改目的**：让 Spark 3.4 优先识别需要远程扫描计划的表。

**工作逻辑**：
新增 `import org.apache.iceberg.RequiresRemoteScanPlanning;`。`newBatchScan()` 方法的判断链由原先的 `if (table instanceof BaseTable && readConf.distributedPlanningEnabled()) {...} else {...}` 改为三段：先 `if (table instanceof RequiresRemoteScanPlanning) { return table.newBatchScan(); }`，再 `else if (table instanceof BaseTable && readConf.distributedPlanningEnabled()) { return new SparkDistributedDataScan(...); }`，最后 `else { return table.newBatchScan(); }`。这样 `RESTTable`（实现了 `RequiresRemoteScanPlanning`）会直接走 `table.newBatchScan()`，由 `RESTTableScan` 与服务端交互完成远程计划，绕过本地分布式扫描。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoteScanPlanning.java` (+58/-0 lines)

**修改目的**：为 Spark 3.4 新增远程扫描计划测试套件。

**工作逻辑**：
新测试类 `TestRemoteScanPlanning extends TestSelect`，用 `@ExtendWith(ParameterizedTestExtension.class)` 参数化。`parameters()` 返回一组 REST Catalog 配置：catalog 名、实现类取自 `SparkCatalogConfig.REST`，properties 在 REST 默认属性基础上 `put(CatalogProperties.URI, restCatalog.properties().get(CatalogProperties.URI))` 并 `put(RESTCatalogProperties.REST_SCAN_PLANNING_ENABLED, "true")`（注释说明该标志通常由服务端设置，测试中从客户端设置以方便验证），并准备一个 binary 表名。因为继承 `TestSelect`，所有 `TestSelect` 的用例都会在远程扫描计划开启的 REST Catalog 下运行。`testBinaryInFilter` 用 `@Disabled` 标注，原因注释为"binary filter that is used by Spark is not working because ExpressionParser.fromJSON doesn't have the Schema to properly parse the filter expression"。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+71/-43 lines)

**修改目的**：消除 Spark 3.4 下 `TestSelect` 在远程扫描计划启用后的随机失败。

**工作逻辑**：
分两类改动。第一类是追加 `ORDER BY id`：覆盖 `testSelect`、`testSplitSize`、`testProjection`、`testAggPushDown` 的 LIMIT 部分（`SELECT * FROM %s ORDER BY id LIMIT 1/2/3`）、以及全部时间旅行用例——`testSnapshotInTableName`（含 `snapshot_id_` 前缀查询与 DataFrameReader `snapshot-id` option 的 `.orderBy("id")`）、`testSnapshotAtTimestamp`（含 `at_timestamp_` 前缀与 `as-of-timestamp` option）、`testVersionAsOf`（含 `VERSION AS OF`、`FOR SYSTEM_VERSION AS OF` 与 `version-as-of` option）、`testTagReference`（含 `VERSION AS OF 'test_tag'`、`tag_` 前缀与 `tag` option）、`testBranchReference`（含 `VERSION AS OF 'test_branch'`、`branch_` 前缀与 `branch` option）、`testTimestampAsOf`（含 `TIMESTAMP AS OF` 长格式/日期格式与 `FOR SYSTEM_TIME AS OF`）。第二类是放宽顺序约束：`testSchemaEvolutionAndFilter` 改 `containsExactlyInAnyOrderElementsOf`，`testSchemaEvolutionAndFilter` 中 branch 查询的 `containsExactly`/`containsExactlyInAnyOrder` 调整，`testFilter` 中 8 处 `containsExactly` 改 `containsExactlyInAnyOrder`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+4/-1 lines)

**修改目的**：让 Spark 3.5 优先识别需要远程扫描计划的表。

**工作逻辑**：
与 v3.4 完全相同的改动：新增 `RequiresRemoteScanPlanning` import，`newBatchScan()` 改为三段判断，优先 `instanceof RequiresRemoteScanPlanning` 走 `table.newBatchScan()`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRemoteScanPlanning.java` (+58/-0 lines)

**修改目的**：为 Spark 3.5 新增远程扫描计划测试套件。

**工作逻辑**：
与 v3.4 完全一致的新测试类，继承 `TestSelect`，配置 REST Catalog 并开启 `REST_SCAN_PLANNING_ENABLED=true`，`testBinaryInFilter` 标记 `@Disabled`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+81/-38 lines)

**修改目的**：消除 Spark 3.5 下 `TestSelect` 在远程扫描计划启用后的随机失败。

**工作逻辑**：
与 v3.4 的 `TestSelect` 改动一致：追加 `ORDER BY id` 覆盖所有顺序敏感用例（含全部时间旅行用例），`testSchemaEvolutionAndFilter` 与 `testFilter` 改用 `containsExactlyInAnyOrder` 系列。v3.5 与 v3.4 的增删行数略有差异（81/-38 vs 71/-43），主要源于两版本 `TestSelect` 原始代码的细微排版差异，但改动范围与方法一致。

## 总结

本提交将远程扫描计划能力（`RequiresRemoteScanPlanning` 标记接口与 `newBatchScan()` 分支判断）从 Spark 4.0/4.1 回移到 3.4/3.5，使 REST Catalog 在这两个较旧版本下也能将扫描规划下推到服务端；同时同步了 `TestSelect` 的稳定性修复（`ORDER BY id` 与 `containsExactlyInAnyOrder`）并新增 `TestRemoteScanPlanning` 测试套件验证远程计划正确性，是远程扫描计划功能在多 Spark 版本上完整落地的关键一步。
