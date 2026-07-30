# 提交 1947：Spark: Use delimited column names in CreateChangelogViewProcedure (#12418)

## 提交信息

- **序号**：1947 / 4088
- **哈希**：9635fb415e1fdb3e268a852da067fb3f78b76825
- **短哈希**：9635fb415
- **日期**：2025-04-01 10:03:11 -0700
- **作者**：Andriy Onyshchuk
- **提交说明**：Spark: Use delimited column names in CreateChangelogViewProcedure (#12418)
- **PR/Issue**：#12418

## 总体目的

此提交修复 Spark 的 `CreateChangelogViewProcedure` 在表包含非标准列名（如含空格、点号等特殊字符的列名，例如 `the id`、`the.data`）时无法正确构建 changelog view 的 bug。

`CreateChangelogViewProcedure` 负责为 Iceberg 表创建一个变更日志视图（changelog view），其内部需要根据表的标识符字段（identifier field names）和保留列构建 repartition spec（用于 `df.repartition`）以及 identifier columns 数组。原代码直接使用原始列名字符串调用 `df.col(columnName)`。但 Spark SQL 在解析列名时，如果列名包含特殊字符（空格、点号等），不加分隔符的引用会被误解析：例如 `the.data` 会被解释为"列 `the` 的 `data` 字段"而非名为 `the.data` 的列；`the id` 因含空格根本无法作为裸标识符解析。这导致 `create_changelog_view` 存储过程在含此类列名的表上执行时报错或生成错误结果。

Spark SQL 的解决方案是使用 delimited identifier（用反引号 `` ` `` 包裹列名，按 Spark 规则反引号本身需双写转义）。本提交新增 `delimitedName` 辅助方法，将列名用反引号包裹（并转义内部反引号），并在构建 repartition spec 与 identifier columns 数组时应用，使非标准列名能被 Spark 正确引用。改动同步应用到 Spark v3.4 与 v3.5。

## 如何达成设计目的

设计思路是引入一个静态辅助方法 `delimitedName(columnName)`，按 Spark delimited identifier 规则处理列名：若列名已被反引号包裹则原样返回（避免双重包裹），否则用反引号包裹并转义内部的反引号（`` ` `` → `` `` ``）。在两处使用列名引用的地方应用此转换：
1. 构建 repartition spec 时：对 `df.columns()` 过滤后的列名先 `map(delimitedName)` 再 `df::col`。
2. 构建 identifier columns 数组时：对 `schema.identifierFieldNames()` 先 `map(delimitedName)` 再转数组。

这样生成的列引用（如 `` `the.data` ``）能被 Spark SQL 正确解析为单一列名，而非嵌套字段访问。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/CreateChangelogViewProcedure.java` (修改, +29/-1 lines)

**修改目的**：对列名应用 delimited identifier 处理。

**工作逻辑**：
- 新增私有静态方法 `delimitedName(String columnName)`：
  - 检查列名是否已被反引号包裹（`startsWith("`") && endsWith("`")`），若是则原样返回（避免双重包裹）。
  - 否则返回 `` "`" + columnName.replaceAll("`", "``") + "`" ``，即用反引号包裹，并将内部反引号双写转义（Spark delimited identifier 规则）。
  - Javadoc 说明：确保含非标准字符的列名可被安全引用，并链接到 Spark 3.5 delimited identifier 文档。
- 构建 repartition spec 处：在 `df.columns()` 流中增加 `.map(CreateChangelogViewProcedure::delimitedName)` 步骤（在 `filter(columnsToKeep)` 之后、`df::col` 之前），使 `df.col(...)` 接收到的是 delimited 列名。
- 构建 identifier columns 数组处：将 `table.schema().identifierFieldNames().toArray(new String[0])` 改为先 `.stream().map(CreateChangelogViewProcedure::delimitedName).toArray(String[]::new)`，使 identifier 列名也被 delimited。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCreateChangelogViewProcedure.java` (修改, +44 lines)

**修改目的**：新增非标准列名场景的回归测试。

**工作逻辑**：新增 `testNonStandardColumnNames` 测试：
- 建表 `CREATE TABLE ... (`the id` INT, `the.data` STRING) USING iceberg`，并 `ALTER TABLE ... ADD PARTITION FIELD `the.data``。
- 插入两行后记录 snap1，再做 `INSERT OVERWRITE` 产生 snap2。
- 调用 `system.create_changelog_view` 指定 start/end snapshot。
- 查询 `select * from cdc_view`，验证 schema 字段名按顺序为 `["the id", "the.data", "_change_type", "_change_ordinal", "_commit_snapshot_id"]`（即非标准列名被正确保留）。
- 验证结果行数为 2。
- 该测试在修复前会因 `df.col("the.data")` 等被误解析而失败。

### `spark/v3.4/spark/...` 下同名的 `CreateChangelogViewProcedure.java` 与测试 (同上)

**修改目的**：对 Spark v3.4 应用完全相同的修复与测试。

**工作逻辑**：v3.4 与 v3.5 改动一致。

## 总结

本次提交修复 Spark `CreateChangelogViewProcedure` 在表含非标准列名（如 `the id`、`the.data`）时无法正确构建 changelog view 的 bug。新增 `delimitedName` 辅助方法按 Spark delimited identifier 规则用反引号包裹列名（并转义内部反引号），在构建 repartition spec 与 identifier columns 数组时应用，使含特殊字符的列名能被 Spark SQL 正确引用。改动同步应用到 Spark v3.4 与 v3.5，并新增非标准列名的回归测试。
