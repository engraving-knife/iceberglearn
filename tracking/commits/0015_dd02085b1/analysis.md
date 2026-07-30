# 提交 0015：Revert "Spec: Mark added_snapshot_id as optional (#8600)" (#8726)

## 提交信息

- **序号**：0015 / 4088
- **哈希**：dd02085b12e9b509efef2d9a8b9fe730c0c60b03
- **短哈希**：dd02085b1
- **日期**：2023-10-05 13:05:51 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Revert "Spec: Mark added_snapshot_id as optional (#8600)" (#8726)
- **PR/Issue**：#8726（还原了 #8600，对应提交 f7a7eb2c）

## 总体目的

这个提交还原了约两周前合并的 PR #8600（提交 f7a7eb2c），将 manifest list 中 `503 added_snapshot_id` 字段在 v1 下的要求性（requiredness）恢复为 `_required_`。要理解这个还原，需要先回顾 #8600 的来龙去脉。

`added_snapshot_id`（字段 503）是 manifest list 中每个 manifest 条目的一个字段，语义为"该 manifest 文件被添加时所对应的 snapshot ID"。在 Iceberg 的规范表（`format/spec.md`）中，该字段在 v1 与 v2 下均标注为 `_required_`。然而在 Java 实现（`ManifestFile.java`）与 Python 实现中，该字段被定义为 `optional`；并且 `V1Metadata` 把它作为 optional 使用，`V2Metadata` 才把它作为 required。也就是说，规范（required）与实现（v1 optional）之间存在不一致。

PR #8600（由 nastra 提交，2023-09-21 合并）采取的修正是"让规范向实现对齐"——把 spec.md 中 v1 列从 `_required_` 改为 `_optional_`，使规范承认 v1 下该字段可缺失。但合并后 Fokko 于 2023-09-29 表示"second thoughts"，认为该字段"应该保持 required"，并提出了另一个方向：不改规范，而是把实现改为 required（对应 PR #8673 "Core: Mark 503: added_snapshot_id as required"）。最终社区采纳了 Fokko 的判断——`added_snapshot_id` 在语义上必须存在，因为每个 manifest 都必然由某个 snapshot 添加，该 ID 是维护 manifest 与 snapshot 关系、支撑快照/时间旅行与清理（expire）逻辑的关键信息；允许它在 v1 下缺失会破坏不变量，给读取端带来歧义与潜在错误。

因此本还原提交把 spec.md 中该字段恢复为 `_required_`，使规范重新要求 v1 manifest list 必须携带 `added_snapshot_id`。Fokko 在 PR 中明确写道 "This should stay required."，rdblue 审核后表示 "Good to get this fixed quickly."。这是一次快速的规范纠偏：撤销了一个本会让规范"降低要求"以迁就实现的改动，转而坚持规范应有的严格性（后续再通过单独工作让实现向规范看齐）。该还原也被从 1.4.0 milestone 中移除，作为独立修复处理。

## 如何达成设计目的

整体设计思路是直接通过 `git revert` 撤销 #8600 引入的单行规范改动，把 spec.md 表格中 `503 added_snapshot_id` 行的 v1 列从 `_optional_` 改回 `_required_`。改动极小（一行一字符级变更），但意义在于恢复规范对 v1 下该字段必须存在的硬性要求，从而维护 manifest-snapshot 关系的完整性约束。值得注意的是，本提交只还原了规范文档，并未同步修改 Java/Python 实现中该字段的 optional 定义——实现侧的对齐留待后续（如 PR #8673 所探索的方向），本提交聚焦于先把规范纠正回来。

## 修改详情

### `format/spec.md`

**修改目的**：将 `503 added_snapshot_id` 字段在 v1 下的要求性从 `_optional_` 恢复为 `_required_`，撤销 #8600 的改动。

**工作逻辑**：在 manifest list 的 `manifest_file` 结构字段表中，该行原本（#8600 之前）为 `| _required_ | _required_ | **503 added_snapshot_id** | long | ID of the snapshot where the manifest file was added |`；#8600 把第一列（v1）改为 `_optional_`；本提交将其改回 `_required_`，即 `| _required_ | _required_ | **503 added_snapshot_id** | long | ID of the snapshot where the  manifest file was added |`。注意 v2 列（第二列）始终为 `_required_`，本次未变。还原后，v1 与 v2 均要求该字段必须存在，与字段语义（manifest 必然由某个 snapshot 添加）一致。该行描述文本中 "where the  manifest" 有两个空格，这是 #8600 之前就存在的既有文本，本次还原原样保留。

## 小结

本提交还原了 #8600 对规范的放宽，恢复 `503 added_snapshot_id` 在 v1 下的 `_required_` 要求，坚持了"每个 manifest 必须记录其添加 snapshot"这一语义不变量，避免了规范为迁就实现而降低严格性所带来的潜在正确性风险。
