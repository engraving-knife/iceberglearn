# 提交 2318：docs: Mark Rust ADLS FileIO as implemented in the status page (#13258)

## 提交信息

- **序号**：2318 / 4088
- **哈希**：586c02204e3c6bc7b093a5ecb0c84e29734783dd
- **短哈希**：586c02204
- **日期**：2025-07-06 18:04:44 -0700
- **作者**：Jannik Steinmann
- **提交说明**：docs: Mark Rust ADLS FileIO as implemented in the status page (#13258)
- **PR/Issue**：#13258

## 总体目的

这个提交更新了 Iceberg 网站的状态页面，将 Rust 语言的 ADLS（Azure Data Lake Storage）FileIO 实现状态从"未实现（N）"标记为"已实现（Y）"。

Iceberg 的状态页面展示了各语言库对不同存储系统的支持情况。ADLS 是 Azure 云存储服务，是 Iceberg 支持的重要存储后端之一。此前 Rust 实现的 ADLS FileIO 尚未完成，状态标记为 N（No）。随着 Rust 实现的完善，ADLS FileIO 已可用，因此需要在状态页面中反映这一变化。

## 如何达成设计目的

通过修改 `site/docs/status.md` 文件中 FileIO 支持矩阵的 ADLS 行，将 Rust 列的值从 N 改为 Y。

## 修改详情

### `site/docs/status.md` (+1/-1 lines)

**修改目的**：更新 Rust ADLS FileIO 实现状态。

**工作逻辑**：在 FileIO 支持矩阵中，`ADLS Compatible` 行的 Rust 列从 `N` 改为 `Y`。该行其他语言（Java、Python、Go）已标记为 Y。

## 总结

这是一个纯文档更新提交，反映 Rust 语言库已完成 ADLS FileIO 实现的事实。变更极小（1 行修改），不影响任何代码逻辑。
