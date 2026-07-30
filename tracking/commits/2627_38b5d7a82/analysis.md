# 提交 2627：Docs: Remove extra `\` in Kafka Connect Configuration docs (#13240)

## 提交信息

- **序号**：2627 / 4088
- **哈希**：38b5d7a820d31d512918ea6d09d538be64551098
- **短哈希**：38b5d7a82
- **日期**：2025-09-11 17:43:24 -0700
- **作者**：Raveendra Pujari
- **提交说明**：Docs: Remove extra `\` in Kafka Connect Configuration docs
- **PR/Issue**：#13240

## 总体目的

Kafka Connect 配置文档 `docs/docs/kafka-connect.md` 的配置属性表格中，表特定的属性名（如 `iceberg.table.<table name>.commit-branch`）使用了反斜杠转义尖括号写作 `\<table name\>`。这导致在渲染后的文档中多余的 反斜杠 可见，影响阅读体验，且 "table name" 含空格在配置键中也不够规范。

本提交修正这四处属性名的写法：移除 `<` 前的多余反斜杠，将 "table name"（带空格）改为 `_table-name_`（用下划线包裹表示斜体强调、用连字符连接），使渲染后显示为 `<table-name>`（斜体），更清晰规范。

## 如何达成设计目的

将表格中四行表特定属性名从 `iceberg.table.\<table name\>.<suffix>` 改为 `iceberg.table.<_table-name_\>.<suffix>`。具体：
- 移除 `<` 前的反斜杠（`\<` → `<`）。
- "table name" 改为 `_table-name_`（下划线斜体强调 + 空格改连字符）。
- 保留 `>` 前的反斜杠（`\>`），因为在 markdown 中 `>` 可能被解析为引用块，转义可确保正确渲染为字面量。

## 修改详情

### `docs/docs/kafka-connect.md` (+4/-4 lines)

**修改目的**：修正 Kafka Connect 配置表中表特定属性名的 markdown 转义写法。

**工作逻辑**：修改四行配置属性名：
- `iceberg.table.\<table name\>.commit-branch` → `iceberg.table.<_table-name_\>.commit-branch`
- `iceberg.table.\<table name\>.id-columns` → `iceberg.table.<_table-name_\>.id-columns`
- `iceberg.table.\<table name\>.partition-by` → `iceberg.table.<_table-name_\>.partition-by`
- `iceberg.table.\<table name\>.route-regex` → `iceberg.table.<_table-name_\>.route-regex`

每处改动一致：移除 `<` 前多余反斜杠，将 "table name" 改为 `_table-name_`（斜体强调 + 连字符），保留 `\>` 转义。其余描述文本不变。

## 总结

这是一次 Kafka Connect 配置文档的 markdown 转义修复，移除了表特定属性名中多余的反斜杠，并将 "table name" 规范化为斜体的 "table-name"，使渲染后的文档更清晰、无多余字符。纯文档修复，不影响功能。
