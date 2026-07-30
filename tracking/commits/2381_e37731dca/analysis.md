# 提交 2381：Spec: Add manifest fields to Snapshot Summary metrics (#13238)

## 提交信息

- **序号**：2381 / 4088
- **哈希**：e37731dcaa6b4fd8ed4acec9532f237fb4f2cd1b
- **短哈希**：e37731dca
- **日期**：2025-07-22 12:31:40 +0200
- **作者**：Manu Zhang
- **提交说明**：Spec: Add manifest fields to Snapshot Summary metrics (#13238)
- **PR/Issue**：#13238

## 总体目的

本提交在 Iceberg 规范文档的快照摘要（Snapshot Summary）指标字段中新增了 4 个与 manifest（清单文件）相关的指标字段。快照摘要用于跟踪快照中数据变更的数值统计信息，此前已有 `total-data-files`、`total-delete-files`、`total-records` 等指标。

本次新增的指标字段使快照摘要能够更细粒度地跟踪 manifest 级别的操作信息，包括创建的 manifest 数量、保留的 manifest 数量、替换的 manifest 数量，以及处理的 manifest 条目数。这些指标对于理解快照提交时的 manifest 操作开销和效率非常有价值，特别是在数据压缩（compaction）、manifest 合并等维护操作场景下。

## 如何达成设计目的

通过在规范文档的快照摘要指标表中添加 4 个新的字段定义。这些字段都是可选的，由写入端在提交快照时按需填充。

## 修改详情

### `format/spec.md` (+4/-0 lines)

**修改目的**：在快照摘要指标字段表中添加 manifest 相关指标。

**工作逻辑**：在快照摘要的指标字段表（位于 `changed-partition-count` 字段之后）新增 4 个字段定义：
- **`manifests-created`**：快照中创建的 manifest 文件数量。
- **`manifests-kept`**：快照中保留的 manifest 文件数量。
- **`manifests-replaced`**：快照中替换的 manifest 文件数量。
- **`entries-processed`**：快照中处理的 manifest 条目数量。

这些字段与其他指标字段（如 `total-data-files`、`total-equality-deletes` 等）并列，遵循相同的表格格式定义。

## 总结

本提交是规范文档的扩展，在快照摘要指标中新增 4 个 manifest 相关字段（manifests-created、manifests-kept、manifests-replaced、entries-processed），仅新增 4 行。这些指标字段为快照提交时的 manifest 操作提供了更细粒度的可观测性，对于维护操作（如 compaction）的性能分析和监控具有重要意义。
