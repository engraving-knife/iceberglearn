# 提交 3080：Spark 4.1: Initial support for MERGE INTO schema evolution (#14970)

## 提交信息

- **序号**：3080 / 4088
- **哈希**：99f14e7ebed010c4542cfdc98cd6f62e3922bc07
- **短哈希**：99f14e7eb
- **日期**：2026-01-07
- **作者**：Szehon Ho
- **提交说明**：Spark 4.1: Initial support for MERGE INTO schema evolution (#14970)
- **PR/Issue**：#14970

## 总体目的

Spark 4.1 引入了对 `MERGE INTO` 语句自动模式演进（schema evolution）的原生支持，允许在 MERGE 操作中当源表（source）与目标表（target）的 schema 不一致时，自动将目标表的模式进行扩展或调整（例如新增源表多出的列、对类型进行宽化等），而无需用户手动执行 `ALTER TABLE`。

Iceberg 的 Spark 4.1 集成此前未声明支持这一能力。要让 Iceberg 表能参与 Spark 4.1 的 `MERGE WITH SCHEMA EVOLUTION` 语法，需要在 `SparkTable`（Iceberg 表在 Spark 中的 DataSource V2 表实现）的能力集合（`TableCapability`）中声明 `AUTOMATIC_SCHEMA_EVOLUTION`，告知 Spark 该表支持自动模式演进。本提交正是完成这一声明的"初始支持"。

同时，本提交新增了一个完整的测试类 `TestMergeSchemaEvolution`，覆盖了源表列多于目标表、源表列少于目标表、通过 DataFrame API 触发、嵌套 struct 演进、类型宽化（type widening）等多种场景，验证 Iceberg 表在 Spark 4.1 下能正确响应 MERGE INTO 的模式演进。

## 如何达成设计目的

整体思路非常简洁：在 `SparkTable` 的 `CAPABILITIES` 静态集合中加入 `TableCapability.AUTOMATIC_SCHEMA_EVOLUTION`，使 Spark 4.1 的查询规划器识别到 Iceberg 表支持该能力。随后通过新增测试类，使用 Spark SQL 的 `MERGE WITH SCHEMA EVOLUTION INTO` 语法和 DataFrame API 的 `.withSchemaEvolution()` 方法，验证各种模式演进场景下数据写入和 schema 变更的正确性。

## 修改详情

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeSchemaEvolution.java` (+227/-0 lines, 新文件)

**修改目的**：为 Spark 4.1 的 MERGE INTO 模式演进功能新增端到端测试。

**工作逻辑**：
新建测试类继承 `SparkRowLevelOperationsTestBase`，使用 `@ExtendWith(ParameterizedTestExtension.class)` 支持参数化测试。包含 5 个测试方法：

1. `testMergeWithSchemaEvolutionSourceHasMoreColumns`：目标表有 `id, dep` 两列，源表有 `id, dep, salary` 三列。执行 `MERGE WITH SCHEMA EVOLUTION INTO ... WHEN MATCHED THEN UPDATE SET * WHEN NOT MATCHED THEN INSERT *`，验证目标表自动新增 `salary` 列，已匹配行更新含 salary，未匹配原有行 salary 为 null，新增行含 salary。测试前用 `assumeThat(branch).isNull()` 跳过分支场景（注释说明 "Schema evolution does not work for branches currently"）。

2. `testMergeWithSchemaEvolutionSourceHasFewerColumns`：目标表有 `id, dep, salary`，源表只有 `id, dep`。验证 MERGE 后匹配行更新时保留原 salary 值，新增行 salary 为 null。

3. `testMergeWithSchemaEvolutionUsingDataFrameApi`：使用 DataFrame API `spark.table("source").mergeInto(...).whenMatched().updateAll().whenNotMatched().insertAll().withSchemaEvolution().merge()` 触发模式演进，验证与 SQL 方式效果一致。

4. `testMergeWithSchemaEvolutionNestedStruct`：目标表含 `s STRUCT<c1:INT,c2:STRING>`，源表含 `s STRUCT<c1:INT,c2:STRING,c3:INT>`。验证嵌套 struct 自动新增 `c3` 字段。

5. `testMergeWithSchemaEvolutionTypeWidening`：目标表 `value INT`，源表 `value LONG`。验证 INT 自动宽化为 LONG，已有行的值被提升为 long。

每个测试通过 `createAndInitTable` 和 `createOrReplaceView` 准备数据，执行 MERGE 后用 `assertEquals` 比对查询结果。`@AfterEach` 清理测试表。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+1/-0 lines)

**修改目的**：在 Iceberg Spark 4.1 表的能力声明中新增 `AUTOMATIC_SCHEMA_EVOLUTION`。

**工作逻辑**：
在 `CAPABILITIES` 这个 `ImmutableSet` 中，于首位新增 `TableCapability.AUTOMATIC_SCHEMA_EVOLUTION`。`CAPABILITIES` 是 `SparkTable` 声明其支持的所有表能力的集合，Spark 引擎在查询规划时会检查该集合以决定是否启用特定优化或功能路径。加入此能力后，当 Spark 4.1 执行 `MERGE WITH SCHEMA EVOLUTION` 时，会识别到 Iceberg 表支持自动模式演进，从而在写入时自动调整 Iceberg 表的 schema（新增列、宽化类型等）以匹配源数据。

## 总结

本提交为 Iceberg 的 Spark 4.1 集成添加了 MERGE INTO 自动模式演进的初始支持，核心改动仅一行——在 `SparkTable.CAPABILITIES` 中声明 `AUTOMATIC_SCHEMA_EVOLUTION` 能力，但配套了覆盖列增减、嵌套 struct 演进、类型宽化、SQL 与 DataFrame API 双路径的完整测试。这使得 Iceberg 表能充分利用 Spark 4.1 的新特性，在 MERGE 操作中自动适配源表 schema 变化，减少了用户手动 ALTER TABLE 的运维负担。
