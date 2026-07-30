# 提交 0967：Spec: Clarify time travel implementation in Iceberg (#8982)

## 提交信息

- **序号**：0967 / 4088
- **哈希**：b7693912825c11e6da4d7d4ae24dae6b8b3f049a
- **短哈希**：b76939128
- **日期**：2024-07-23 14:01:47 -0700
- **作者**：emkornfield
- **提交说明**：Spec: Clarify time travel implementation in Iceberg (#8982)
- **PR/Issue**：#8982

## 总体目的

Iceberg 表元数据中存在两套"历史"信息：

1. **`snapshot-log`**：记录了"表当前快照（current-snapshot-id）随时间的演进序列"，每条记录形如 `(timestamp-ms, snapshot-id)`，描述"在某个时间点表指向的 current snapshot 是哪一个"。这是按时间点做 time travel 的核心依据。
2. **`snapshots` 列表中的父子 lineage**：每个 snapshot 通过 `parent-id` 字段构成一棵快照树（dag），描述数据写入的因果派生关系。

这两套历史在大多数场景下是一致的，但在某些操作下会产生不一致——例如 `replacePartitions`、`rollback`、`branch` 切换、外部直接设置 `current-snapshot-id` 等操作会把表的 current snapshot 切到一个不一定是当前 parent lineage 上游的快照（可能是某个 branch 上的快照，也可能是完全没有 parent 关系的孤立快照）。这种情况下，给定一个时间戳，如果实现方误用 `snapshots` 的 parent 链做查找，得到的 snapshot-id 就会与"那时表的真实可见状态"不一致，从而导致 time travel 查询读到了错误的数据版本。

本提交的目标是在 Iceberg 规范（`format/spec.md`）中明确推荐实现方在处理 point-in-time 查询（即 time travel 的 `TIMESTAMP AS OF` 形式）时**使用 `snapshot-log` 而非 snapshots lineage**，从而统一各引擎（Spark、Flink、Trino、Athena 等）的行为，避免不同实现对 time travel 给出不一致结果。这是规范层面的"实现说明"，不是强制要求，但强烈推荐遵循。

## 如何达成设计目的

实现方式是给 `format/spec.md` 文档新增一个"附录 F：实现说明（Appendix F: Implementation Notes）"章节，专门承载"规范未强制但推荐遵循"的实现建议。在该章节下新增"Point in Time Reads (Time Travel)"小节，用一段文字说明：

- Iceberg 同时维护 `snapshot-log`（表 current snapshot 演进历史）与 `snapshots` 中的 parent-child lineage 两种历史；
- 这两种历史对同一个时间戳可能给出不同的 snapshot-id，原因是某些表操作（如直接更新 `current-snapshot-id`）可以把表的当前快照切到任意快照，可能是来自分支或没有 lineage 的快照；
- 处理 point-in-time 查询时，实现方应当用 `snapshot-log` 来查找给定时间点之前最近的快照，以保证 time travel 反映的是当时表的真实可见状态；
- 举例 `SELECT * FROM prod.db.table TIMESTAMP AS OF '1986-10-26 01:21:00Z';` 的处理流程：在 snapshot-log 中找到该时间戳之前最近的条目，使用其 snapshot-id 的元数据进行 scan；
- 若给定时间戳之前没有任何 snapshot-log 条目，或 `snapshot-log` 字段为空（该字段是可选的），实现方应抛出带有清晰信息的错误。

通过把这一推荐写入规范，引擎实现者就有了权威依据来统一行为，下游用户也能预期到不同 Iceberg 引擎在 time travel 上给出一致结果。

## 修改详情

### `format/spec.md`

**修改目的**：在 Iceberg 规范末尾新增"附录 F：实现说明"章节及其中第一小节"Point in Time Reads (Time Travel)"，澄清 time travel 应基于 `snapshot-log` 实现。

**工作逻辑**：在文件末尾追加约 11 行 markdown 内容。开头一段说明"附录 F：实现说明"用于承载规范未强制但推荐的实现建议；随后"Point in Time Reads (Time Travel)"小节用 3 段文字解释两套历史的差异来源、推荐使用 `snapshot-log`、给出 SQL 示例、并要求在缺失 `snapshot-log` 时抛出明确错误。注意文件结尾没有换行符（`\ No newline at end of file`），与原文件风格一致。

## 小结

- **成效**：在 Iceberg 规范中正式澄清了 time travel 的实现建议——应基于 `snapshot-log` 而非 snapshots lineage 查找历史快照，并要求在 `snapshot-log` 缺失或时间点早于最早记录时抛出明确错误。这为各引擎实现 time travel 提供了统一的行为依据。
- **影响范围**：仅规范文档 `format/spec.md` 一个文件，新增约 11 行 markdown，无任何代码、构建、配置或测试改动。
- **回迁到 1.4.x 的注意事项**：本提交是纯文档变更，**完全适合也推荐回迁到 1.4.x 分支**，无任何风险。规范文档应跨所有维护分支保持一致，cherry-pick 时若 1.4.x 上 spec.md 的尾部略有差异（例如已存在其他附录）可手动把新章节追加到合适位置。建议同时检查 1.4.x 是否已有类似澄清，避免重复。
