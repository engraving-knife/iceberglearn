# 提交 1646：Spec: Adds in missing ChangeLog Field IDs - Reassigns Row Lineage Field IDs (#12100)

## 提交信息

- **序号**：1646 / 4088
- **哈希**：af00d1fb13a89c8e9684c097d3ece9b05ed302bb
- **短哈希**：af00d1fb1
- **日期**：2025-01-27（Mon Jan 27 14:51:53 2025 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Spec: Adds in missing ChangeLog Field IDs - Reassigns Row Lineage Field IDs
- **PR/Issue**：#12100

## 总体目的

Iceberg 格式规范 `format/spec.md` 的"Metadata Columns"小节列出了所有预留的元数据列及其 field ID（按 `Integer.MAX_VALUE - N` 递减分配）。该表格此前遗漏了三个 ChangeLog（变更日志）相关元数据列：

- `_change_type`（string）：变更日志中的记录类型（INSERT、DELETE、UPDATE_BEFORE、UPDATE_AFTER）；
- `_change_ordinal`（int）：变更的顺序号；
- `_commit_snapshot_id`（long）：变更发生的快照 ID。

这三个列在 Iceberg 实现代码（`MetadataColumns.java`）中早已存在并使用（ID 分别为 `Integer.MAX_VALUE - 104/105/106`，即 2147483543/2147483542/2147483541），但 spec 文档表格没有列出它们，导致规范与实现不一致。

更麻烦的是：spec 表格中此前把 `_row_id`（行血缘）的 ID 标为 `2147483543`、`_last_updated_sequence_number` 标为 `2147483542`——这两个 ID 与代码中 ChangeLog 列的 ID 冲突（代码中 ChangeLog 占用了 2147483543/2147483542/2147483541）。也就是说，spec 给行血缘列分配的 ID 与代码实际使用的 ID 重叠，是规范层面的错误。

本提交修复 spec：

1. 补上三个缺失的 ChangeLog 列（`_change_type` 2147483543、`_change_ordinal` 2147483542、`_commit_snapshot_id` 2147483541）；
2. 把行血缘列 `_row_id` 的 ID 从 `2147483543` 重新分配为 `2147483540`、`_last_updated_sequence_number` 从 `2147483542` 重新分配为 `2147483539`，让出冲突的 ID 段给 ChangeLog 列；
3. 表格内顺序按 ID 降序排列（与原风格一致）。

修正后 spec 与 `MetadataColumns.java` 实现完全对齐。

## 如何达成设计目的

通过单次编辑 `format/spec.md` 的元数据列表格实现：

- 在 `row`（2147483544）行之后插入三行 ChangeLog 列（ID 2147483543/2147483542/2147483541）；
- 把原 `_row_id` 行的 ID 改为 2147483540、`_last_updated_sequence_number` 行的 ID 改为 2147483539。

## 修改详情

### `format/spec.md`（修改，+5/-2）

**修改目的**：补齐 ChangeLog 元数据列、修正行血缘列 ID 冲突。

**工作逻辑**：

原表格（节选）：
```
| 2147483546  file_path      | string       | ... |
| 2147483545  pos            | long         | ... |
| 2147483544  row            | struct<...>  | ... |
| 2147483543  _row_id        | long         | ... |
| 2147483542  _last_updated_sequence_number | long | ... |
```

新表格：
```
| 2147483546  file_path                       | string       | ... |
| 2147483545  pos                             | long         | ... |
| 2147483544  row                             | struct<...>  | ... |
| 2147483543  _change_type                    | string       | The record type in the changelog (INSERT, DELETE, UPDATE_BEFORE, or UPDATE_AFTER) |
| 2147483542  _change_ordinal                 | int          | The order of the change |
| 2147483541  _commit_snapshot_id             | long         | The snapshot ID in which the change occured |
| 2147483540  _row_id                         | long         | A unique long assigned when row-lineage is enabled, see Row Lineage |
| 2147483539  _last_updated_sequence_number   | long         | The sequence number which last updated this row when row-lineage is enabled |
```

要点：

- 新增三行 ChangeLog 列，描述与代码实现一致；
- `_row_id` ID 从 `2147483543` → `2147483540`（下移 3 位，让出 2147483543/2147483542/2147483541 给 ChangeLog）；
- `_last_updated_sequence_number` ID 从 `2147483542` → `2147483539`（下移 3 位）；
- 表格仍按 ID 降序排列，保持原有风格；
- 注：`occured` 是 `occurred` 的拼写变体（spec 原文如此，未在本提交中修正）。

## 小结

- **成效**：补齐了 spec 中缺失的三个 ChangeLog 元数据列（`_change_type`/`_change_ordinal`/`_commit_snapshot_id`），并修正了行血缘列（`_row_id`/`_last_updated_sequence_number`）的 field ID 与代码实现的冲突。修正后 spec 与 `MetadataColumns.java` 完全对齐，避免下游实现者按旧 spec 用错 ID。
- **影响范围**：仅 `format/spec.md` 一处文档。不影响任何运行时代码（代码中 ID 早已是正确值）。但这是一个**规范层面的 breaking change**：任何按旧 spec 实现（把 `_row_id` 当作 2147483543）的非 Iceberg 实现，在读取 Iceberg 写出的带行血缘的文件时会错把 `_change_type` 列当作 `_row_id`，反之亦然。由于行血缘（row lineage）在该时间点仍是实验特性（behind 表属性 `write.wap.enabled` 等），社区接受此 ID 重分配。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支的 `format/spec.md` 若仍有冲突的 ID 分配，应回迁此 spec 修正，让文档与代码一致；
  - 这是纯文档改动，无代码影响，cherry-pick 安全；
  - 需确认 1.4.x 上的 `MetadataColumns.java` 中 ChangeLog 列 ID 与本 spec 修正后一致（即 `Integer.MAX_VALUE - 104/105/106`）；若 1.4.x 上 ChangeLog 列 ID 不同，spec 修正需相应调整；
  - 若 1.4.x 上行血缘特性已发布给用户使用（写出了带 `_row_id` 字段的文件），回迁此 spec 修正会让"旧文件中的 _row_id 字段 ID"与"新 spec 中的 _row_id ID"不一致——但只要代码实现一直用 `Integer.MAX_VALUE - 104/105/106` 给 ChangeLog、用其他 ID 给行血缘，spec 修正只是对齐文档，不影响已写出的文件（文件里存的是实际 ID 值，不是 spec 文本）。
