# 提交 2698：Docs: Document max-files-to-rewrite in Spark rewrite_data_files (#14211)

## 提交信息

- **序号**：2698 / 4088
- **哈希**：c95dbef9a679a858da225e1fbddc8e5da100ac38
- **短哈希**：c95dbef9a
- **日期**：2025-09-29 14:00:09 +0200
- **作者**：Alessandro Nori
- **提交说明**：Docs: Document max-files-to-rewrite in Spark rewrite_data_files (#14211)
- **PR/Issue**：#14211

## 总体目的

本提交为 Spark `rewrite_data_files` 存储过程补充文档中此前缺失的 `max-files-to-rewrite` 选项说明。`rewrite_data_files` 是 Iceberg 提供的数据文件压缩/重写过程，用于合并小文件、重组织数据布局等。该过程支持多个可选参数（如 `delete-ratio-threshold`、`output-spec-id`、`remove-dangling-deletes` 等），文档中以表格形式列出。

`max-files-to-rewrite` 选项在代码层面已存在，用于限制单次重写操作涉及的文件数量上限，便于在大表上控制重写作业的规模与资源消耗。但该选项此前未在用户文档的参数表中列出，导致用户难以发现和使用。本次补齐该表格行，使文档与实际可用选项保持一致。

## 如何达成设计目的

在 `docs/docs/spark-procedures.md` 中 `rewriteDataFiles` 过程的参数表格末尾新增一行，描述 `max-files-to-rewrite` 选项的默认值（null）与语义（设置被重写文件数的上限；未指定时重写所有符合条件的文件）。改动极小，仅一行表格行。

## 修改详情

### `docs/docs/spark-procedures.md` (+1/-0 lines)

**修改目的**：在 `rewriteDataFiles` 过程参数表中补充 `max-files-to-rewrite` 选项。

**工作逻辑**：在参数表格中，紧跟 `remove-dangling-deletes` 行后新增 `max-files-to-rewrite` 行：默认值 `null`，说明为"设置将被重写的合格文件数上限；未指定时重写所有合格文件"。表格列结构与已有行一致（参数名 | 默认值 | 说明）。

## 总结

本提交是纯文档补全，将 Spark `rewrite_data_files` 存储过程的 `max-files-to-rewrite` 选项纳入用户文档参数表，使用户能够发现并使用该选项控制重写作业规模。属于文档与代码能力对齐的小改进，不涉及代码逻辑变更。
