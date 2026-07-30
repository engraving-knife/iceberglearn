# 提交 2199：Docs: Add custom table location description for Flink (#13214)

## 提交信息

- **序号**：2199 / 4088
- **哈希**：a1a9ba12301320692887e88ee24879089ce724fc
- **短哈希**：a1a9ba123
- **日期**：2025-06-03 22:55:40 +0900
- **作者**：Tom Tanaka
- **提交说明**：Docs: Add custom table location description for Flink (#13214)
- **PR/Issue**：#13214

## 总体目的

这个提交为 Flink DDL 文档补充了关于自定义表位置（custom table location）的说明。Iceberg 的 Flink 集成支持通过 `WITH ('location'='fully-qualified-uri')` 在创建表时指定表的存储位置，但此前的 Flink DDL 文档没有明确说明这一用法，用户可能不清楚如何自定义表路径。本提交在 `flink-ddl.md` 文档中新增了一段说明和 SQL 示例，展示如何通过 `WITH` 子句指定 `location` 属性来设置自定义表位置，提升了文档的完整性。

## 如何达成设计目的

- 在 `docs/docs/flink-ddl.md` 文档中，在已有的 create clauses 说明（COMMENT、WITH 配置）之后，新增一段说明文字和 SQL 代码示例，展示使用 `WITH ('location'='...')` 指定表位置的用法。

## 修改详情

### `docs/docs/flink-ddl.md` (修改, +12/-0 lines)

**修改目的**：补充 Flink DDL 中自定义表位置的文档说明。

**工作逻辑**：在 create 表语句的常用子句说明之后，新增一段文字说明"To specify the table location, use `WITH ('location'='fully-qualified-uri')`:"，并附上一个完整的 SQL CREATE TABLE 示例，演示在 WITH 子句中同时设置 `format-version` 和 `location` 属性（如 `'location'='hdfs//nn:8020/custom-path'`）。

## 总结

这是一次纯文档改进，为 Flink DDL 补充了自定义表位置的用法说明和示例，帮助用户了解如何通过 `location` 属性指定 Iceberg 表的存储路径，对代码功能无影响。
