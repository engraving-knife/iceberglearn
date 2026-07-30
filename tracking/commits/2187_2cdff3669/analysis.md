# 提交 2187：chore: Update status.md (#13182)

## 提交信息

- **序号**：2187 / 4088
- **哈希**：2cdff366982b30685f6410c290cbd16aed274caf
- **短哈希**：2cdff3669
- **日期**：2025-06-01 14:25:36 -0400
- **作者**：Matt Topol
- **提交说明**：chore: Update status.md (#13182)
- **PR/Issue**：#13182

## 总体目的

此提交更新 Iceberg 项目网站的功能状态文档（status.md），反映 iceberg-go v0.3.0 发布后带来的功能支持变化。status.md 文档记录了各个 Iceberg 实现（Java、PyIceberg、Rust、Go）对不同功能的支持情况。随着 iceberg-go v0.3.0 的发布，Go 实现新增了一些功能支持，包括 Rewrite manifests、Append data 以及多个 SQL Catalog 的命名空间操作。此提交将这些更新反映到状态文档中，使文档与实际实现保持同步。

## 如何达成设计目的

- 更新 status.md 中多个功能表格里 Go 列的状态从 N 改为 Y
- 涉及 Catalog 操作和表读写操作两类功能

## 修改详情

### `site/docs/status.md` (修改, +11/-11 lines)

**修改目的**：更新 Go 实现的功能支持状态。

**工作逻辑**：
- 将 Rewrite manifests 功能的 Go 支持从 N 改为 Y（出现在两个不同的表格中）
- 将 Append data 功能的 Go 支持从 N 改为 Y（出现在 Spec V1 和 V2 两个表格中）
- 将 SQL Catalog 的 loadNamespaceMetadata 功能的 Go 支持从 N 改为 Y
- 将 REST Catalog 的多个命名空间操作（listNamespaces、createNamespace、dropNamespace、namespaceExists、loadNamespaceMetadata）的 Go 支持从 N 改为 Y
- 修复文件末尾缺少换行符的问题

## 总结

此提交更新了 Iceberg 网站的功能状态文档，反映了 iceberg-go v0.3.0 发布后 Go 实现新增的功能支持，包括 Rewrite manifests、Append data 以及多个 SQL/REST Catalog 命名空间操作。这是文档与实现保持同步的常规维护工作。
