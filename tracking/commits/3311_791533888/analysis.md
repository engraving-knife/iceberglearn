# 提交 3311：OpenAPI: Add SetPartitionStatisticsUpdate/RemovePartitionStatisticsUpdate to TableUpdate (#14957)

## 提交信息

- **序号**：3311 / 4088
- **哈希**：791533888bdaf3b5a051cc8dbcba57378d1ab657
- **短哈希**：791533888
- **日期**：2026-02-25
- **作者**：feefs
- **提交说明**：OpenAPI: Add SetPartitionStatisticsUpdate/RemovePartitionStatisticsUpdate to TableUpdate (#14957)
- **PR/Issue**：#14957

## 总体目的

PR #14957 的原始意图是修复 OpenAPI 规范中的一个遗漏：在之前的重构（#9240 将 `TableUpdate` 从 `enum:` 改为 `anyOf:`）以及分区统计功能添加（#9690 添加 `set-partition-statistics` 和 `remove-partition-statistics` 到 `BaseUpdate` 的 `discriminator` 序列）中，这两个分区统计相关的 update 类型被添加到了 `BaseUpdate` 的 action enum 中，但没有被同步添加到 `TableUpdate` 的 `anyOf` 引用列表中。这导致基于 OpenAPI 规范生成的 JSON Schema 会错误地拒绝包含 `set-partition-statistics` 或 `remove-partition-statistics` 的 `CommitTableRequest` 载荷。

然而，经 `git diff-tree` 和 tree 哈希比对验证，本提交的 tree 哈希（`237781a7c107a96f40af968310b761d159f352b1`）与其父提交 3310（`9534c9b3a`）的 tree 哈希完全相同，即本提交是一个**空提交（empty commit）**，实际未引入任何文件变更。当前 `open-api/rest-catalog-open-api.yaml` 中的 `TableUpdate` anyOf 列表和 `BaseUpdate` action enum 中均不包含 `set-partition-statistics` / `remove-partition-statistics`，说明分区统计相关的 update 类型在此 PR 合并前可能已被其他提交从规范中移除，导致合并时 PR 的改动变为无操作（no-op）。

## 如何达成设计目的

PR 原计划在 `open-api/rest-catalog-open-api.yaml` 的 `TableUpdate` anyOf 列表中添加两行 `$ref` 引用（`SetPartitionStatisticsUpdate` 和 `RemovePartitionStatisticsUpdate`），共 +8/-0 行改动。但由于合并时底层规范已发生变化（分区统计 update 类型已不在 `BaseUpdate` enum 中），实际合并结果为空提交，未产生任何代码变更。

## 修改详情

### 无文件变更（空提交）

**修改目的**：本提交未修改任何文件。

**工作逻辑**：
经 `git show`、`git diff-tree -r` 和 tree 哈希比对确认，本提交不包含任何文件变更。PR #14957 在 GitHub 上声明 +8/-0 行改动，但 squash merge 后的提交树与父提交完全一致。这通常发生在 PR 开发期间目标分支已发生变更使得 PR 改动变为冗余或不适用的场景。当前规范中 `BaseUpdate` 的 action enum（行 1640-1654）和 `TableUpdate` 的 anyOf 列表（行 1811-1825）均不含分区统计相关条目，佐证了这一判断。

## 总结

本提交在形式上是一个空提交。PR #14957 原意是将 `SetPartitionStatisticsUpdate` 和 `RemovePartitionStatisticsUpdate` 补充到 OpenAPI 规范的 `TableUpdate` anyOf 列表中，但由于合并时分区统计 update 类型已不在规范中，实际未产生任何代码变更。该提交的存在主要保留了 PR 合并的记录和关联。
