# 提交 2992：Spark: Test all simple types in TestSelect (#14804)

## 提交信息

- **序号**：2992 / 4088
- **哈希**：482d850c252c38f507476233227d1f2cc0e69eda
- **短哈希**：482d850c2
- **日期**：2025-12-10
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Test all simple types in TestSelect (#14804)
- **PR/Issue**：#14804

## 总体目的

Iceberg 的 Spark 集成需要正确处理 Iceberg 类型系统所支持的全部简单类型（primitive types）在过滤谓词中的下推与读取。`TestSelect` 是 Spark 模块中验证 `SELECT` 查询行为的核心测试类，但此前的测试用例主要集中在少数类型（如整型、字符串）上，对 `boolean`、`float`、`double`、`date`、`timestamp` 等类型在 `WHERE` 过滤条件下的行为缺乏系统性覆盖。

不同类型在 Spark SQL 到 Iceberg 表达式的转换、统计信息匹配、行级过滤等环节可能存在差异（例如浮点数比较、日期/时间戳的边界处理），缺乏覆盖意味着相关回归无法被及时捕获。本提交新增一个 `simpleTypesInFilter` 测试，在一个 Iceberg 表中同时包含所有简单类型列，并对每一列分别施加过滤条件，验证查询返回结果与预期一致，从而补齐类型覆盖缺口。该测试同步添加到 Spark v3.5 与 v4.0 两个模块。

## 如何达成设计目的

新增一个参数化风格（但实为单方法）的测试 `simpleTypesInFilter`：建表时声明覆盖全部简单类型的列（`bigint`、`boolean`、`integer`、`long`、`float`、`double`、`string`、`date`、`timestamp`），插入 3 行具有区分度的数据，然后对每个类型列分别执行带 `>` 或 `=` 谓词的 `SELECT`，用 AssertJ 断言返回行集合精确匹配预期。由于 v3.5 与 v4.0 的 `TestSelect` 内容一致，两个模块各加一份相同的测试。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+36/-0 lines)

**修改目的**：为 Spark v3.5 新增覆盖全部简单类型过滤的测试。

**工作逻辑**：

新增 `@TestTemplate simpleTypesInFilter()`：

1. 建表 `simple_types_table`，列包含 `id bigint, boolean boolean, integer integer, long long, float float, double double, string string, date date, timestamp timestamp`，`USING iceberg`。
2. 插入 3 行数据，每行各列值递增且具区分度（如 boolean 交替 true/false，date/timestamp 跨年）。
3. 依次断言：
   - `SELECT id WHERE id > 1` → `[2, 3]`
   - `SELECT id, boolean WHERE boolean = true` → `[(1,true),(3,true)]`
   - `SELECT long WHERE long > 1` → `[2, 3]`
   - `SELECT float WHERE float > 1.1f` → `[2.2, 3.3]`
   - `SELECT double WHERE double > 1.3` → `[2.4, 3.6]`
   - `SELECT string WHERE string > '1.5'` → `["2.6","3.9"]`
   - `SELECT date WHERE date > to_date('2021-01-01')` → 2022、2023 两个 `java.sql.Date`
   - `SELECT timestamp WHERE timestamp > to_timestamp('2021-01-01')` → 对应两个 `java.sql.Timestamp`
4. 末尾 `DROP TABLE` 清理。

同时新增 `import java.sql.Timestamp`。该测试覆盖了数值比较、布尔等值、字符串字典序、日期与时间戳边界等多种过滤语义。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+36/-0 lines)

**修改目的**：为 Spark v4.0 同步新增相同测试。

**工作逻辑**：与 v3.5 文件改动完全一致，同一测试方法与同样的建表、插入、断言逻辑，确保两个 Spark 版本对全部简单类型的过滤行为都有回归保护。

## 总结

本提交通过一个覆盖 Iceberg 全部简单类型列的过滤测试，补齐了 `TestSelect` 在 `boolean`/`float`/`double`/`date`/`timestamp` 等类型上的覆盖缺口，能够有效捕获类型转换、统计匹配与行级过滤在这些类型上的回归，并在 v3.5 与 v4.0 两个 Spark 分支保持一致覆盖。
