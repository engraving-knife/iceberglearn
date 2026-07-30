# 提交 3441：Docs: Document Spark SQL transform functions (#15697)

## 提交信息

- **序号**：3441 / 4088
- **哈希**：a8e9ad2ae02fee5766f71a0783f402680da8e219
- **短哈希**：a8e9ad2ae0
- **日期**：2026-03-22 09:22:27 -0700
- **作者**：jackylee
- **提交说明**：Docs: Document Spark SQL transform functions (#15697)
- **PR/Issue**：#15697

## 总体目的

为 Iceberg 的 Spark SQL 变换函数（transform functions）添加文档说明。Iceberg 为每个 Iceberg catalog 添加了 SQL 函数，用于在查询中检查变换结果以及编写与 Iceberg 分区变换匹配的过滤条件。这些函数通过 `system` 命名空间调用，但之前缺少文档说明。

## 如何达成设计目的

- 在 `spark-ddl.md` 中添加交叉引用，指向新的 Spark SQL 函数文档
- 在 `spark-queries.md` 中新增完整的 "Spark SQL functions" 章节，包括：
  - 函数的使用说明和命名空间
  - 调用示例
  - 完整的函数列表表格（函数名、输入类型、返回类型、示例）
  - 各函数的行为细节说明

## 修改详情

### `docs/docs/spark-ddl.md` (+3/-0 lines)

**修改目的**：在 DDL 文档的变换说明部分添加交叉引用，引导用户查看 Spark SQL 函数文档。

**工作逻辑**：
- 在变换说明（Supported transformations）段落之后，添加说明文字
- 指出这些变换也可通过 `system` 命名空间作为 Spark SQL 函数使用
- 提供到 `spark-queries.md#spark-sql-functions` 的链接

### `docs/docs/spark-queries.md` (+51/-0 lines)

**修改目的**：添加完整的 Spark SQL 函数文档章节。

**工作逻辑**：
- 新增 "Spark SQL functions" 章节，位于文件元数据表格之后
- 说明这些函数仅通过 Iceberg catalog 可用，不在 Spark 内置 catalog 中注册
- 展示通过 `system` 命名空间调用函数的 SQL 示例
- 提供带 catalog 限定的调用示例
- 重要提示：`PARTITIONED BY` 子句使用单数形式（如 `year(ts)`），而 SQL 函数使用复数形式（如 `system.years(ts)`）
- 提供函数表格，包含 7 个函数：
  - `system.iceberg_version()`：返回版本字符串
  - `system.bucket(numBuckets, col)`：桶变换
  - `system.years(col)`：返回自 1970-01-01 以来的年数
  - `system.months(col)`：返回自 1970-01 以来的月数
  - `system.days(col)`：返回日期部分
  - `system.hours(col)`：返回自 1970-01-01T00:00 以来的小时数
  - `system.truncate(width, col)`：截断变换
- 说明所有函数对 NULL 输入返回 NULL
- 详细解释 `years`、`months`、`days`、`hours` 返回的是 Iceberg 变换值而非日历字段
- 说明 `truncate` 对数值向下取整为 width 的倍数，对字符串/二进制保留前 width 个字符/字节

## 总结

该提交为 Iceberg 的 Spark SQL 变换函数添加了全面的文档，涵盖函数列表、使用方法、调用示例和行为细节，帮助用户理解如何在查询中使用这些与分区变换对应的 SQL 函数。
