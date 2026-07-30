# 提交 1100：Docs: `_commit_snapshot_id` instead of `_change_snapshot_id` (#11000)

## 提交信息

- **序号**：1100 / 4088
- **哈希**：524fbb895c1f940dd4f5c7bca0b8a8d870993d6f
- **短哈希**：524fbb895
- **日期**：2024-08-26 10:49:36 +0200（作者本地时区 +0900）
- **作者**：Yuya Ebihara
- **提交说明**：Docs: `_commit_snapshot_id` instead of `_change_snapshot_id` (#11000)
- **PR/Issue**：#11000

## 总体目的

本提交修正 Spark Procedures 文档中 changelog（变更日志）示例表格的列名错误：将 `_change_snapshot_id` 改为 `_commit_snapshot_id`。

Iceberg 的 `create_changelog_view` Spark 过程生成的变更日志中，记录变更所属快照 ID 的隐藏列实际名称是 `_commit_snapshot_id`（表示该变更对应的提交快照 ID）。但文档示例表格的表头误写为 `_change_snapshot_id`，与实际生成的列名不一致，会误导用户在查询时使用错误的列名。

本提交将 `docs/docs/spark-procedures.md` 中两处示例表格的表头从 `_change_snapshot_id` 修正为 `_commit_snapshot_id`，使文档与实际行为一致。

## 如何达成设计目的

直接修改 Markdown 文档中两处示例表格的表头列名，将 `_change_snapshot_id` 替换为 `_commit_snapshot_id`。两处分别对应：第一处展示完整变更日志示例（含 INSERT/DELETE 两条记录），第二处展示 net changes（净变更）后的结果（仅剩一条 INSERT 记录）。

## 修改详情

### `docs/docs/spark-procedures.md`

**修改目的**：修正 changelog 示例表格表头列名，使其与实际隐藏列名 `_commit_snapshot_id` 一致。

**工作逻辑**：在文档中两处示例表格的表头行，将 `| _change_snapshot_id |` 改为 `| _commit_snapshot_id |`。表格内容（示例快照 ID 数值 `5390529835796506035` 等）保持不变，仅修正列名。

## 小结

- **成效**：修正了 Spark Procedures 文档中 changelog 示例的列名错误，使文档与 `create_changelog_view` 实际生成的隐藏列名 `_commit_snapshot_id` 一致。
- **影响范围**：仅修改 `docs/docs/spark-procedures.md` 一个文件，2 处表格表头（纯文档修正）。
- **回迁到 1.4.x 的注意事项**：纯文档修正，无代码影响，**可安全回迁到 1.4.x**。需确认 1.4.x 的文档中是否存在同样的列名错误；若 1.4.x 的 `create_changelog_view` 实际列名确为 `_commit_snapshot_id`，则回迁此文档修正是有益的。若 1.4.x 的列名实现与 main 不同（较旧版本可能使用不同命名），则需先核实 1.4.x 实际列名再决定是否回迁。
