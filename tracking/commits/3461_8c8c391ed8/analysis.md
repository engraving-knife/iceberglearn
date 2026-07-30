# 提交 3461：Docs: clarify SparkSessionCatalog function limitations (#15736)

## 提交信息

- **序号**：3461 / 4088
- **哈希**：8c8c391ed88dbfc617f3698bd7bc807cb6be5a7c
- **短哈希**：8c8c391ed8
- **日期**：2026-03-26 08:38:17 -0700
- **作者**：jackylee
- **提交说明**：Docs: clarify SparkSessionCatalog function limitations (#15736)
- **PR/Issue**：#15736

## 总体目的

澄清 SparkSessionCatalog 的函数限制。Spark 4.2.0 之前不支持在 session catalog 中使用 `V2Function`（参见 SPARK-54760），因此通过 `SparkSessionCatalog` 配置的 `spark_catalog` 无法使用 Iceberg 的 catalog 作用域 SQL 函数（如 `system.bucket`、`system.days`、`system.iceberg_version`）。文档需要明确说明这一限制并提供变通方案。

## 如何达成设计目的

- 在 `spark-configuration.md` 中添加 SparkSessionCatalog 的函数限制说明和变通方案
- 在 `spark-queries.md` 中添加对应的注意事项
- 引用 SPARK-54760 和相关 PR 提供详细信息

## 修改详情

### `docs/docs/spark-configuration.md` (+10/-0 lines)

**修改目的**：说明 SparkSessionCatalog 的 V2Function 限制。

**工作逻辑**：
- 在 SparkSessionCatalog 配置说明后添加 note 块
- 说明 Spark 4.2.0 之前不支持 session catalog 中的 V2Function
- 引用 SPARK-54760 和 apache/spark#53531
- 说明 `system.bucket`、`system.days`、`system.iceberg_version` 等函数不可用
- 变通方案：配置单独的 `SparkCatalog` 目录来调用这些函数

### `docs/docs/spark-queries.md` (+8/-0 lines)

**修改目的**：在 Spark SQL 函数文档中添加限制说明。

**工作逻辑**：
- 在 SQL 函数说明后添加 note 块
- 说明 `SELECT spark_catalog.system.bucket(16, id)` 等查询在 SparkSessionCatalog 下会失败
- 提供相同的变通方案

## 总结

该提交为文档添加了 SparkSessionCatalog 的 V2Function 限制说明。Spark 4.2.0 之前不支持在 session catalog 中使用 V2Function，导致 Iceberg 的 catalog 作用域 SQL 函数无法通过 `spark_catalog` 使用。文档提供了配置单独 SparkCatalog 的变通方案。
