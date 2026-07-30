# 提交 2753：Spark 4.0: Add variant round trip test for Spark (#14276)

## 提交信息

- **序号**：2753 / 4088
- **哈希**：03ebc8cc90cc0469b9815d9b1a354e1193ef3d6d
- **短哈希**：03ebc8cc9
- **日期**：2025-10-15 16:51:04 -0700
- **作者**：Huaxin Gao
- **提交说明**：Spark 4.0: Add variant round trip test for Spark (#14276)
- **PR/Issue**：#14276

## 总体目的

本提交为 Spark 4.0 模块新增 Variant（变体）类型的"往返"（round trip）读测试，验证 Iceberg 能够正确地通过 Spark 写入 Variant 数据并读回。

背景在于：Variant 类型是 Iceberg 表格式 v3 引入的一种新的逻辑类型，用于存储半结构化数据（类似 JSON）。Spark 4.0 原生支持 `VARIANT` 类型。Iceberg 在 Spark 4.0 模块中已经实现了 Variant 的读写支持，但此前缺少端到端的集成测试来验证"写入 → 读取"的往返一致性。

由于 Variant 向量化（vectorized）Parquet 读取尚未实现，测试通过 `assumeThat(vectorized).isFalse()` 跳过向量化场景，只验证非向量化的读取路径。测试覆盖了 Variant 列的投影、不带 Variant 列的投影、对 Variant 列的 IS NULL / IS NOT NULL 过滤、以及 Variant 空值（NULL）的投影等场景，确保读回的数据内容与写入一致。

## 如何达成设计目的

新增一个独立的测试类 `TestSparkVariantRead`，使用 Hadoop 类型的 SparkCatalog（避免 Hive schema 转换，因为 Hive 尚不支持 VARIANT）。测试表使用 `format-version=3` 并包含 `id BIGINT, v1 VARIANT, v2 VARIANT` 三列。通过 `parse_json` 写入 JSON 字符串作为 Variant 值，再用 Spark SQL 读取并断言读回的 `VariantVal` 内容。

测试用 `@ParameterizedTest` 配合 `@ValueSource(booleans = {false, true})` 来分别覆盖非向量化和向量化两种读取模式，但向量化场景通过 `assumeThat` 跳过。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkVariantRead.java` (+172/-0 lines, 新文件)

**修改目的**：新增 Variant 类型的 Spark 往返读测试。

**工作逻辑**：

- **测试环境搭建**：
  - `@BeforeAll setupCatalog()`：配置一个名为 `local` 的 Hadoop 类型 SparkCatalog，关闭缓存，warehouse 指向临时目录。使用 Hadoop catalog 是为了避免 Hive schema 转换（Hive 不支持 VARIANT）。
  - `@BeforeEach setupTable()`：删除并重建表 `local.default.var`，schema 为 `(id BIGINT, v1 VARIANT, v2 VARIANT) USING iceberg TBLPROPERTIES ('format-version'='3')`，并插入两行数据：id=1 时 v1=`{"a":1}`、v2=`{"x":10}`；id=2 时 v1=`{"b":2}`、v2=`{"y":20}`。
  - `@AfterEach cleanup()`：删除测试表。

- **`testVariantColumnProjection_singleVariant`**：
  - 跳过向量化场景。
  - 查询 `select id, v1` 并按 id 排序，断言 schema 只含 `id`、`v1`，行数为 2。
  - 取回的 `v1` 应为 `VariantVal` 类型，通过 `new Variant(value, metadata)` 构造 Spark `Variant` 对象，再用 `getFieldByKey` 取出字段并断言值：行 1 的 `a`=1L，行 2 的 `b`=2L。

- **`testVariantColumnProjectionNoVariant`**：
  - 验证只投影非 Variant 列（`select id`）时读回正常，id 为 1 和 2。

- **`testFilterOnVariantColumnOnWholeValue`**：
  - 插入一行 `id=3, NULL, NULL`。
  - `where v1 IS NULL` 应只返回 id=3；`where v1 IS NOT NULL` 应返回 id 1 和 2。
  - 对非空行用 `to_json(v1)` 验证内容：行 1 为 `{"a":1}`，行 2 为 `{"b":2}`。

- **`testVariantNullValueProjection`**：
  - 插入一行 `id=10, NULL, NULL`，查询 `select id, v1 where id = 10`，断言读回的 `v1` 为 null（`row.isNullAt(1)`）。这一用例专门针对 Variant 空值投影的边界情况。

## 总结

本提交为 Spark 4.0 模块新增了 Variant 类型的端到端往返读测试，覆盖了单列投影、无 Variant 列投影、基于整值的 NULL 过滤、以及 Variant 空值投影等场景。测试使用 Hadoop catalog 绕过 Hive 不支持 VARIANT 的限制，并通过参数化方式预留了对向量化读取的扩展点（当前因未实现而跳过）。这为 Iceberg v3 的 Variant 类型在 Spark 上的读写正确性提供了回归保障。提交说明显示该 PR 经过多次 review 迭代（address comments 多次），最终移除了未使用的 import。
