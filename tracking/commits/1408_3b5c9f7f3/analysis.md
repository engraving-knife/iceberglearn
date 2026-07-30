# 提交 1408：Spark 3.5: Procedure to compute table stats (#10986)

## 提交信息

- **序号**：1408 / 4088
- **哈希**：3b5c9f7f37b269c5e2df0feb11376333f29380e2
- **短哈希**：3b5c9f7f3
- **日期**：2024-11-20（Wed Nov 20 15:10:26 2024 -0800）
- **作者**：Karuppayya <karuppayya1990@gmail.com>
- **提交说明**：Spark 3.5: Procedure to compute table stats (#10986)
- **PR/Issue**：#10986

## 总体目的

Iceberg 在 Spark 3.5 模块已有 `SparkActions#computeTableStats(Table)` action，可通过 Java/Scala API 计算表统计（NDV sketch 等）并写入 `StatisticsFile`。但缺少一个 SQL 存储过程入口，用户无法直接通过 `CALL` 语句触发统计计算。

本提交的目的是：在 Spark 3.5 中新增一个存储过程 `system.compute_table_stats`，把已有的 `SparkActions#computeTableStats(Table)` action 暴露为 SQL 调用，参数包括表名、可选的 `snapshot_id`、可选的 `columns` 数组，返回生成的 statistics 文件路径。

## 如何达成设计目的

1. 新增 `ComputeTableStatsProcedure` 类，继承 `BaseProcedure`，实现 `Procedure` 接口：
   - 声明三个参数：`table`（必填，String）、`snapshot_id`（可选，Long）、`columns`（可选，String 数组）；
   - 输出 schema 为单列 `statistics_file: String`；
   - 在 `call(...)` 中解析参数，调用 `actions().computeTableStats(table)`，按需 `.snapshot(snapshotId)` / `.columns(columns)`，执行后把返回的 `StatisticsFile.path()` 包装为单行 `InternalRow` 返回；
   - 通过 `modifyIcebergTable(tableIdent, table -> {...})` 进入写上下文，使统计文件作为表元数据的一部分被提交；
   - 若 `result.statisticsFile()` 为 null（例如空表无数据可统计），返回 0 行。
2. 在 `SparkProcedures` 的 builder map 中注册 `"compute_table_stats" -> ComputeTableStatsProcedure::builder`，让 Spark SQL 解析器能识别该过程名。
3. 新增测试类 `TestComputeTableStatsProcedure` 覆盖：空表、命名参数、位置参数、无效列名、无效 snapshot_id、无效表名等场景。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/ComputeTableStatsProcedure.java`（新增）

**修改目的**：实现 `system.compute_table_stats` 存储过程。

**工作逻辑**：

- 静态字段：
  - `TABLE_PARAM = ProcedureParameter.required("table", DataTypes.StringType)`：必填表名参数；
  - `SNAPSHOT_ID_PARAM = ProcedureParameter.optional("snapshot_id", DataTypes.LongType)`：可选快照 ID，缺省时使用当前快照；
  - `COLUMNS_PARAM = ProcedureParameter.optional("columns", STRING_ARRAY)`：可选列名数组，限定只统计指定列；
  - `PARAMETERS = {TABLE_PARAM, SNAPSHOT_ID_PARAM, COLUMNS_PARAM}`；
  - `OUTPUT_TYPE = StructType({statistics_file: String})`：返回单列统计文件路径。
- `builder()`：返回 `Builder<ComputeTableStatsProcedure>`，在 `doBuild()` 中 `new ComputeTableStatsProcedure(tableCatalog())`。
- 构造函数 `ComputeTableStatsProcedure(TableCatalog)` 调用 `super(tableCatalog)`。
- `parameters()` / `outputType()`：返回声明的参数与输出 schema。
- `call(InternalRow args)`：
  ```java
  ProcedureInput input = new ProcedureInput(spark(), tableCatalog(), PARAMETERS, args);
  Identifier tableIdent = input.ident(TABLE_PARAM);
  Long snapshotId = input.asLong(SNAPSHOT_ID_PARAM, null);
  String[] columns = input.asStringArray(COLUMNS_PARAM, null);
  return modifyIcebergTable(tableIdent, table -> {
    ComputeTableStats action = actions().computeTableStats(table);
    if (snapshotId != null) action.snapshot(snapshotId);
    if (columns != null) action.columns(columns);
    Result result = action.execute();
    return toOutputRows(result);
  });
  ```
- `toOutputRows(Result)`：从 `result.statisticsFile()` 取路径，构造 `newInternalRow(UTF8String.fromString(path))` 返回单行；若 `statisticsFile` 为 null，返回空数组（无行）。
- `description()`：返回 `"ComputeTableStatsProcedure"`。

通过 `modifyIcebergTable` 进入 Iceberg 表的修改上下文，由 `BaseProcedure` 负责事务/锁/提交后清理；action 内部生成的 `StatisticsFile` 会被绑定到表的当前或指定快照，作为表元数据的一部分提交。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java`

**修改目的**：把新过程注册到 Spark SQL 过程表。

**工作逻辑**：在 `builder()` 静态初始化的 `mapBuilder` 中追加：

```java
mapBuilder.put("compute_table_stats", ComputeTableStatsProcedure::builder);
```

注册后，Spark SQL 解析器看到 `CALL <catalog>.system.compute_table_stats(...)` 时会路由到 `ComputeTableStatsProcedure.builder()` 构造的过程实例。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestComputeTableStatsProcedure.java`（新增）

**修改目的**：覆盖新过程的各类调用场景。

**工作逻辑**：继承 `ExtensionsTestBase`，使用 `@ExtendWith(ParameterizedTestExtension.class)` 跨 catalog 参数化。共 6 个测试用例：

1. `testProcedureOnEmptyTable`：空表上调用，断言结果为空（无 statistics 文件生成）。
2. `testProcedureWithNamedArgs`：分区表插入 4 行，`CALL ... compute_table_stats(table => '...', columns => array('id'))`，断言返回的路径以 `.stats` 结尾，并调用 `verifyTableStats` 校验生成的 statistics 文件含 NDV sketch 属性（`APACHE_DATASKETCHES_THETA_V1_NDV_PROPERTY`）。
3. `testProcedureWithPositionalArgs`：分区表插入 4 行，先 `Spark3Util.loadIcebergTable` 拿到当前 snapshotId，再用位置参数 `CALL ... compute_table_stats('...', <snapshotId>L)` 调用，断言返回 `.stats` 路径并通过 `verifyTableStats` 校验。
4. `testProcedureWithInvalidColumns`：传不存在的列 `id1`，断言抛 `IllegalArgumentException` 并包含 `Can't find column id1`。
5. `testProcedureWithInvalidSnapshot`：传不存在的 `snapshot_id => 1234L`，断言抛 `IllegalArgumentException` 并包含 `Snapshot not found`。
6. `testProcedureWithInvalidTable`：传不存在的表，断言抛 `RuntimeException` 并包含 `Couldn't load table`。

辅助方法 `verifyTableStats(String tableName)`：通过 `Spark3Util.loadIcebergTable` 加载表，取 `table.statisticsFiles().get(0)`，断言其第一个 blob 的 properties 中包含 `NDVSketchUtil.APACHE_DATASKETCHES_THETA_V1_NDV_PROPERTY`，证明 NDV sketch 被正确写入。

`@AfterEach removeTable()` 在每个用例后 `DROP TABLE IF EXISTS` 清理。

## 小结

- **成效**：Spark 3.5 用户现可通过 `CALL <catalog>.system.compute_table_stats(table => '<table>'[, snapshot_id => <id>][, columns => array('c1','c2')])` 直接计算并写入 Iceberg 表统计（NDV sketch），返回 statistics 文件路径。底层复用已有的 `SparkActions#computeTableStats` action，无重复实现。
- **影响范围**：新增 1 个过程类（122 行）、1 个测试类（137 行）、`SparkProcedures` 注册一行。无对现有过程的修改。
- **回迁到 1.4.x 的注意事项**：取决于 1.4.x 是否已有 `SparkActions#computeTableStats(Table)` action 以及 `ComputeTableStats` / `Result` / `StatisticsFile` API。如果 1.4.x 已具备底层 action 与 `ProcedureInput` / `BaseProcedure` 等基础设施，则过程本身可平滑回迁。回迁要点：
  1. 确认 `org.apache.iceberg.actions.ComputeTableStats` 及其 `Result` 在 1.4.x 中存在；
  2. 确认 `actions().computeTableStats(table)` / `action.snapshot(...)` / `action.columns(...)` 在 1.4.x 中可用；
  3. `ProcedureInput.asStringArray` 与 `STRING_ARRAY` 常量在 1.4.x `BaseProcedure` 中是否存在；
  4. `NDVSketchUtil.APACHE_DATASKETCHES_THETA_V1_NDV_PROPERTY` 在 1.4.x 中是否存在；
  5. 若 1.4.x 缺少底层 action，则该过程无法独立回迁——需要先回迁 action 与 statistics 文件写入逻辑。
