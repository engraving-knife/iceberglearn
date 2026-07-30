# 提交 3671：Spec: Update formatting in tables to use material content tabs (#14656)

## 提交信息

- **序号**：3671 / 4088
- **哈希**：b84d4468979bb29350cd0ab4246a2c56787f8998
- **短哈希**：b84d44689
- **日期**：2026-05-08 10:32:37 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Spec: Update formatting in tables to use material content tabs (#14656)
- **PR/Issue**：#14656

## 总体目的

这个提交将 Iceberg 规范文档（`format/spec.md`）中的表格格式改为使用 Material for MkDocs 的 content tabs 语法，并恢复了格式重构过程中意外丢失的内容。

Iceberg 规范文档中有许多按格式版本（v1/v2/v3）分列的表格，此前这些版本列直接内嵌在表格中（如 `| v1 | v2 | v3 |`），当版本增多时表格变宽难以阅读。本提交将这些表格改为使用 MkDocs 的 `=== "v1 - v3"` content tabs 语法，将不同版本的内容放入可切换的标签页中，改善阅读体验。对于 v1/v2/v3 差异不大的表格，合并为单个 "v1 - v3" tab。

在格式重构过程中，有四部分内容被意外删除，本提交也将其恢复：
1. `column_sizes`：恢复 "Does not include bytes necessary to read other columns, like footers." 句子。
2. `partitions`：恢复 "(see below)" 交叉引用。
3. `partition-spec`：恢复 writers 使用此字段但 readers 使用 manifest 文件中的 specs 的说明。
4. `properties`：恢复 `commit.retry.num-retries` 示例。

## 如何达成设计目的

1. 将 spec.md 中所有按版本分列的表格用 `=== "v1 - v3"` content tabs 包裹，表格内容缩进 4 空格。
2. 对于 v1/v2/v3 列合并为 "v2 and v3" 等简化列标题。
3. 恢复格式重构中丢失的四部分内容。

## 修改详情

### `format/spec.md` (+166/-156 lines)

**修改目的**：表格改用 content tabs 格式，恢复丢失内容。

**工作逻辑**：
1. manifest 文件元数据表、manifest_entry struct 表、data_file struct 表等多个表格改为 content tabs：
```markdown
=== "v1 - v3"
    | v1         | v2 and v3  | Key                 | Value ... |
    |------------|------------|---------------------|-----------|
    | _required_ | _required_ | `schema`            | ...       |
```
2. 恢复丢失内容示例：
   - `column_sizes` 描述恢复 "Does not include bytes necessary to read other columns, like footers."
   - `partition-spec` 恢复 "Writers use this field but readers use specs from manifest files" 说明。
   - `properties` 恢复 `commit.retry.num-retries` 示例。

## 总结

这个提交将 Iceberg 规范文档中的版本分列表格改为 MkDocs content tabs 格式，改善了多版本表格的阅读体验。同时恢复了格式重构过程中意外丢失的四部分内容，确保文档完整性。这是一个纯文档格式优化提交，不改变规范的技术内容。
