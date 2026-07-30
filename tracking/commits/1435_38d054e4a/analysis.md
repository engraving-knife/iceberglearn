# 提交 1435：Spark 3.4: Add procedure to compute table stats (#11652)

## 提交信息

- **序号**：1435 / 4088
- **哈希**：38d054e4a41ed30be6f7d3480b80150c50b2c3e8
- **短哈希**：38d054e4a
- **日期**：2024-11-27（Wed Nov 27 12:32:27 2024 +0530）
- **作者**：Soumya Banerjee <48854046+jeesou@users.noreply.github.com>
- **提交说明**：Spark 3.4: Add procedure to compute table stats (#11652)
- **PR/Issue**：#11652
- **作用模块**：Spark v3.4（procedures 主代码 + spark-extensions 测试）

## 总体目的

Iceberg 支持为表生成统计文件（statistics file），其中可包含 NDV（Number of Distinct Values，基数）的 theta sketch 等统计信息，用于查询引擎在生成执行计划时进行代价估算。Iceberg Core 已提供 `actions().computeTableStats(table)` 这一 actions API 来生成统计文件，但在 Spark 3.4 集成中，用户只能通过编程式调用 actions API 来触发统计计算，缺少像其它 Iceberg Spark procedure（如 `rewrite_data_files`、`remove_orphan_files` 等）那样可直接在 SQL 中通过 `CALL catalog.system.<proc>(...)` 调用的便捷入口。

本提交新增一个名为 `compute_table_stats` 的 Spark procedure，让用户可以通过 SQL 直接调用：

```sql
CALL catalog.system.compute_table_stats('db.table');
CALL catalog.system.compute_table_stats(table => 'db.table', columns => array('id'));
CALL catalog.system.compute_table_stats('db.table', 12345L);
```

从而把 Iceberg 的「计算表统计」能力暴露到 Spark SQL 层，降低使用门槛、提升与现有 procedures 体系的一致性。

## 如何达成设计目的

遵循 Iceberg Spark 3.4 既有的 procedure 体系约定（`BaseProcedure` + `SparkProcedures` 注册表 + `ProcedureBuilder`），实现一个新 procedure：

1. **新建 `ComputeTableStatsProcedure`**：继承 `BaseProcedure`，定义三个参数（必填的 `table`、可选的 `snapshot_id`、可选的 `columns`）与单列输出 `statistics_file`（统计文件路径）。在 `call()` 中解析参数，通过 `modifyIcebergTable(...)` 获取表后构造 `actions().computeTableStats(table)` action，按需设置 `snapshot` 与 `columns`，执行后将结果中的 `StatisticsFile.path()` 转成单行 `InternalRow` 返回；若结果为空（如空表无数据可统计）则返回空数组。
2. **在 `SparkProcedures` 注册**：将 `"compute_table_stats"` 字符串映射到 `ComputeTableStatsProcedure::builder`，使其可通过 `CALL ...system.compute_table_stats(...)` 触发。
3. **新建 `TestComputeTableStatsProcedure`**：覆盖空表、命名参数、位置参数（带 snapshot_id）、非法列名、非法 snapshot_id、非法表名等场景，并验证生成的统计文件 blob metadata 中包含 NDV theta sketch 属性。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/ComputeTableStatsProcedure.java`（新增，122 行）

**修改目的**：实现「计算表统计」的 Spark procedure。

**工作逻辑**：
- 继承 `BaseProcedure`，提供静态 `builder()` 工厂返回 `ProcedureBuilder`；
- 定义三个 `ProcedureParameter`：
  - `TABLE_PARAM`：必填，`StringType`，表标识；
  - `SNAPSHOT_ID_PARAM`：可选，`LongType`，目标快照；
  - `COLUMNS_PARAM`：可选，`STRING_ARRAY`，需统计的列名数组；
- 输出 `OUTPUT_TYPE` 为单列 `statistics_file`（`StringType`，nullable）；
- `call(InternalRow args)` 中通过 `ProcedureInput` 解析参数（`input.ident(TABLE_PARAM)`、`input.asLong(SNAPSHOT_ID_PARAM, null)`、`input.asStringArray(COLUMNS_PARAM, null)`），再在 `modifyIcebergTable(tableIdent, table -> {...})` 中：
  - 构造 `ComputeTableStats action = actions().computeTableStats(table)`；
  - 若 `snapshotId != null` 则 `action.snapshot(snapshotId)`；
  - 若 `columns != null` 则 `action.columns(columns)`；
  - 执行 `action.execute()` 拿到 `Result`，再通过 `toOutputRows(result)` 转换：若 `result.statisticsFile()` 非空则返回含一行（路径）的数组，否则返回空 `InternalRow[]`；
- `description()` 返回 `"ComputeTableStatsProcedure"`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java`

**修改目的**：将新 procedure 注册到 procedures map。

**工作逻辑**：在 `Builder` 链路中、`fast_forward` 之后追加一行：
```java
mapBuilder.put("compute_table_stats", ComputeTableStatsProcedure::builder);
```
仅 1 行新增，无其它改动。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestComputeTableStatsProcedure.java`（新增，140 行）

**修改目的**：验证 `compute_table_stats` procedure 的行为。

**工作逻辑**：继承 `SparkExtensionsTestBase`，`@After` 中删除测试表。覆盖如下场景：
- `testProcedureOnEmptyTable`：空表上调用 procedure，断言结果为空（无统计文件生成）；
- `testProcedureWithNamedArgs`：用命名参数 `table => ...`, `columns => array('id')`，断言返回的路径以 `.stats` 结尾，并通过 `verifyTableStats` 校验 blob metadata 中含 `NDVSketchUtil.APACHE_DATASKETCHES_THETA_V1_NDV_PROPERTY`；
- `testProcedureWithPositionalArgs`：用位置参数传入 `tableIdent` 与具体 `snapshot.snapshotId()`，同样断言路径与统计内容；
- `testProcedureWithInvalidColumns`：传入不存在的列 `id1`，断言抛 `IllegalArgumentException` 且消息含 `Can't find column id1`；
- `testProcedureWithInvalidSnapshot`：传入不存在的 snapshot_id `1234L`，断言抛 `IllegalArgumentException` 且消息含 `Snapshot not found`；
- `testProcedureWithInvalidTable`：传入不存在的表名，断言抛 `RuntimeException` 且消息含 `Couldn't load table`；
- `verifyTableStats` 工具方法：加载 Iceberg 表，取 `statisticsFiles().get(0)` 与其首个 `blobMetadata`，断言 properties 含 NDV theta sketch 属性键。

## 小结

- **成效**：新增 `compute_table_stats` Spark 3.4 procedure，将 Iceberg 的 `ComputeTableStats` action 暴露为 SQL `CALL` 入口，支持必填 `table`、可选 `snapshot_id` 与 `columns` 参数，返回 `statistics_file` 路径；测试覆盖空表、命名/位置参数、非法列名/snapshot_id/表名等边界与正常路径，并验证生成的统计文件包含 NDV theta sketch。共 3 个文件、新增 263 行。
- **影响范围**：Spark v3.4 模块的主代码 2 个文件（1 新增 procedure + 1 注册行）与测试 1 个文件（新增 140 行），属功能扩展，向后兼容（仅新增 procedure，未改动既有 procedure）。
- **回迁到 1.4.x 的注意事项**：本 procedure 依赖 Spark v3.4 已有的 `BaseProcedure`、`ProcedureInput`、`actions().computeTableStats(table)` action 与 `NDVSketchUtil` 等基础设施。回迁前需确认 1.4.x 的 Spark v3.4 模块是否已包含这些依赖：
  - 若 1.4.x 已有 `ComputeTableStats` action 与 `SparkActions#computeTableStats(Table)`，则 procedure 本身可较直接地回迁；
  - 若 1.4.x 的 `BaseProcedure` / `ProcedureInput` API 与 main 有差异（如方法签名、参数解析方式），需按 1.4.x 的既有写法适配（参考 1.4.x 中已有的 procedure 如 `remove_orphan_files` 的写法）；
  - 1.4.x 的统计文件功能（statistics file / NDV sketch）若尚未启用或不完整，回迁后 procedure 可能无法生成有效统计，需先确认 core 侧 `StatisticsFile` / `BlobMetadata` / `NDVSketchUtil` 在 1.4.x 中已具备。
  - 若 1.4.x 仅维护 Spark v3.3 或更早版本而无 v3.4 模块，则本提交不适用。
