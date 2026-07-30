# 提交 2292：Docs: Correct typo in spec.md (#13427)

## 提交信息

- **序号**：2292 / 4088
- **哈希**：a69af4985fee4d6be428ba6f60c9e8f0c07da8fb
- **短哈希**：a69af4985
- **日期**：2025-06-30 12:11:49 +0200
- **作者**：Yu-Chuan Hung
- **提交说明**：Docs: Correct typo in spec.md (#13427)
- **PR/Issue**：#13427

## 总体目的

本提交修复了 Iceberg 规范文档 `spec.md` 中的一个拼写错误。在描述表升级到 v3 时行谱系（Row Lineage）行为的段落中，单词 "initailized" 被误拼为正确拼写 "initialized"。

规范文档是 Iceberg 项目的核心文档，定义了表格式的技术规范。文档的准确性对于社区开发者理解和使用 Iceberg 至关重要，即使是简单的拼写错误也应当及时修正。

## 如何达成设计目的

通过直接编辑 `format/spec.md` 文件，将拼写错误的单词 "initailized" 修改为正确的 "initialized"。修改位于"Row Lineage for Upgraded Tables"小节，该小节描述了当表升级到 v3 版本时 `next-row-id` 的初始化行为。

## 修改详情

### `format/spec.md` (+1/-1 lines)

**修改目的**：修复规范文档中的拼写错误。

**工作逻辑**：在 spec.md 第 477 行附近，"Row Lineage for Upgraded Tables" 小节的第一段中，将 "its `next-row-id` is initailized to 0" 修改为 "its `next-row-id` is initialized to 0"。修改仅涉及单个单词的拼写纠正，不改变任何技术内容或语义。

## 总结

这是一个简单的文档修复提交，修正了 Iceberg 规范文档中的一个拼写错误。虽然修改内容微小，但保持核心规范文档的准确性对项目质量具有重要意义。
