# 提交 2613：docs: flink: Fix updated link (#14024)

## 提交信息

- **序号**：2613 / 4088
- **哈希**：4f48b51666a3fe43f519eaa1fffc05835396a39f
- **短哈希**：4f48b5166
- **日期**：2025-09-08 08:33:25 -0700
- **作者**：Fokko Driesprong
- **提交说明**：docs: flink: Fix updated link
- **PR/Issue**：#14024

## 总体目的

这是一次文档链接修复。先前有一次提交（`2df7dc76`）将 Flink 文档中的 `flink-actions.md` 重命名为 `flink-maintenance.md`，但 `flink.md` 主文档中"Rewrite files action"行的链接仍指向旧文件名 `flink-actions.md#rewrite-files-action`，导致该链接失效（404）。

本提交将 `flink.md` 中该处链接更新为新的 `flink-maintenance.md#rewrite-files-action`，使文档跳转恢复正确。这属于文档一致性维护，避免用户在阅读 Flink 集成文档时遇到死链。

## 如何达成设计目的

直接修改 `docs/docs/flink.md` 中功能矩阵表格里"Rewrite files action"一行的 markdown 链接，将目标从 `flink-actions.md#rewrite-files-action` 改为 `flink-maintenance.md#rewrite-files-action`，锚点 `#rewrite-files-action` 保持不变（因为重命名只改了文件名，未改标题）。

## 修改详情

### `docs/docs/flink.md` (+1/-1 lines)

**修改目的**：修复因文件重命名导致的失效文档链接。

**工作逻辑**：在 Flink 功能矩阵表格中，将 `[Rewrite files action](flink-actions.md#rewrite-files-action)` 修改为 `[Rewrite files action](flink-maintenance.md#rewrite-files-action)`。仅替换文件名部分，锚点保持一致。

## 总结

这是一次简单的文档链接修复，使 `flink.md` 中指向"Rewrite files action"的链接与重命名后的 `flink-maintenance.md` 保持一致，避免死链，改善文档用户体验。
