# 提交 2380：Docs: Fix spelling errors in v3 spec (#13622)

## 提交信息

- **序号**：2380 / 4088
- **哈希**：7a9079bee04bedb72037bc8f2189c810150e0f6c
- **短哈希**：7a9079bee
- **日期**：2025-07-22 07:57:51 +0200
- **作者**：slfan1989
- **提交说明**：Docs: Fix spelling errors in v3 spec (#13622)
- **PR/Issue**：#13622

## 总体目的

本提交修复了 Iceberg 规范文档（format/spec.md）中的多处拼写错误。规范文档是 Iceberg 项目的核心技术文档，定义了表格式规范，拼写错误的修复有助于提升文档的专业性和可读性。

具体修复了以下拼写错误：
1. "algoritm" 修正为 "algorithm"（算法）
2. "occured" 修正为 "occurred"（发生）
3. "the the" 修正为 "the"（重复单词删除，出现 2 处）

## 如何达成设计目的

通过直接修改 `format/spec.md` 文件中的拼写错误来完成。每处修改都是简单的文本替换，不涉及任何技术内容的变更。

## 修改详情

### `format/spec.md` (+4/-4 lines)

**修改目的**：修复 v3 规范文档中的拼写错误。

**工作逻辑**：共修复 4 处拼写错误：
1. **第 224 行**：geography 类型描述中，将 "edge-interpolation algoritm" 修正为 "edge-interpolation algorithm"。
2. **第 394 行**：`_commit_snapshot_id` 元数据列描述中，将 "the change occured" 修正为 "the change occurred"。
3. **第 835 行**：first_row_id 分配说明中，将 "using the the manifest's" 修正为 "using the manifest's"（删除重复的 "the"）。
4. **第 883 行**：浮点数分区值比较说明中，将 "only the the most significant" 修正为 "only the most significant"（删除重复的 "the"）。

## 总结

本提交是纯文档拼写错误修复，修改 4 处拼写问题（algoritm -> algorithm、occured -> occurred、2 处重复的 "the"）。虽然改动量极小，但规范文档是 Iceberg 项目的核心文档，保持其准确性对社区非常重要。
