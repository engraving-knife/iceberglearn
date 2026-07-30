# 提交 1938：Docs: Fix quote for rewrite_table_path example (#12628)

## 提交信息

- **序号**：1938 / 4088
- **哈希**：d8251755c4f78361cf0d114774fe227dc23b805a
- **短哈希**：d8251755c
- **日期**：2025-03-30 22:50:16 -0700
- **作者**：slfan1989
- **提交说明**：Docs: Fix quote for rewrite_table_path example (#12628)
- **PR/Issue**：#12628

## 总体目的

这是一个文档修复提交。在 Spark procedures 文档 `docs/docs/spark-procedures.md` 中，`system.rewrite_table_path` 存储过程的示例 SQL 使用了双引号（`"..."`）包裹字符串字面量，但 Spark SQL 的字符串字面量应使用单引号（`'...'`）。双引号在 Spark SQL 中通常表示标识符（identifier）而非字符串，因此按照文档示例直接执行会报语法错误或行为异常。

本提交将 `rewrite_table_path` 的两个示例中的所有字符串参数值（`source_prefix`、`target_prefix`、`start_version`、`end_version`、`staging_location`）的双引号统一改为单引号，使示例可直接复制粘贴执行，符合 Spark SQL 语法规范。

## 如何达成设计目的

直接在文档 markdown 代码块中将双引号替换为单引号，保持其余内容不变。

## 修改详情

### `docs/docs/spark-procedures.md` (修改, +7/-7 lines)

**修改目的**：修正 `rewrite_table_path` 示例 SQL 的字符串引号。

**工作逻辑**：在两个示例代码块中：
- 示例 1（基本用法）：`source_prefix` 与 `target_prefix` 的值由双引号 `"..."` 改为单引号 `'...'`。
- 示例 2（带 staging 与版本范围）：`source_prefix`、`target_prefix`、`start_version`、`end_version`、`staging_location` 五个参数的值由双引号改为单引号。

共修改 7 处字符串引号，使示例符合 Spark SQL 字符串字面量语法。

## 总结

本次提交为文档修复，将 `rewrite_table_path` 存储过程示例中的双引号字符串改为单引号，使示例符合 Spark SQL 语法规范、可直接执行。共修改 7 处引号。
