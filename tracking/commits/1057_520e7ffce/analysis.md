# 提交 1057：API: Fix JavaDoc typos in Transaction API

## 提交信息

- **序号**：1057 / 4088
- **哈希**：520e7ffce7caed3df6e614bf3921ce2db944d503
- **短哈希**：520e7ffce
- **日期**：2024-08-13 08:35:41 -0600
- **作者**：dongwang
- **提交说明**：API: Fix JavaDoc typos in Transaction API
- **PR/Issue**：无（提交说明中未带 PR 编号）

## 总体目的

`Transaction` 接口是 Iceberg API 模块中用于组织多个表更新操作并以事务方式提交的核心接口。该接口的 JavaDoc 注释中存在几处与实际方法行为不符的描述错误：注释里说"commit the change"或"replace files / manage snapshots"，但对应方法实际做的是设置排序规则、删除文件、过期快照等操作。这些不准确的描述会误导使用者在阅读 API 文档时对方法语义产生误解。

本提交修复了 `Transaction` 接口中三处 JavaDoc 用词错误，使文档描述与对应方法的真实语义保持一致，提升 API 文档的准确性。

## 如何达成设计目的

逐行核对 `Transaction.java` 中相关方法的 JavaDoc 描述，将不准确或多余的表述替换为与方法语义匹配的动词。改动均为纯文档注释修改，不涉及任何代码逻辑变更，对运行时行为零影响。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Transaction.java` (+3/-3 lines)

**修改目的**：修正三处方法 JavaDoc 描述与实际语义不符的拼写/用词错误。

**工作逻辑**：
三处具体修改如下：

1. `updateSortOrder` 方法的 JavaDoc：将 "set a table sort order and commit the change" 改为 "set a table sort order"。
   原因：该方法只是创建一个用于设置排序规则的 `ReplaceSortOrder` 对象，提交动作由调用者后续触发，注释里"and commit the change"是多余且不准确的。

2. `newDelete` 方法的 JavaDoc：将 "Create a new {@link DeleteFiles delete API} to replace files in this table" 改为 "to delete files in this table"。
   原因：`DeleteFiles` 是删除文件 API，用 "replace" 描述与实际语义不符，应为 "delete"。

3. `expireSnapshots` 方法的 JavaDoc：将 "Create a new {@link ExpireSnapshots expire API} to manage snapshots in this table" 改为 "to expire snapshots in this table"。
   原因：`ExpireSnapshots` 是过期快照 API，"manage" 过于宽泛，应明确为 "expire"。

## 总结

这是一次纯文档修复提交，纠正了 `Transaction` 接口三处 JavaDoc 描述与实际方法语义不一致的问题。虽然没有代码逻辑变更，但对 API 使用者正确理解接口行为有正面价值，属于提升文档质量的低风险改动。
