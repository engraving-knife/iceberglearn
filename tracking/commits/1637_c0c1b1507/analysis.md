# 提交 1637 c0c1b1507 分析

## 提交信息
- 哈希：c0c1b150721bd9dfba08bc9a7530ee21363bf3dc
- 日期：2025-01-24 12:42:53 -0800
- 作者：Honah J.
- 消息：Spec: Document Snapshot Summary Optional Fields for Standardization (#11660)

## 总体目的

本提交在 Iceberg 表格式规范（`format/spec.md`）中新增一个附录章节“Optional Snapshot Summary Fields”（可选快照摘要字段），系统性地文档化快照 summary 中可选字段的名称与含义，分为“Metrics（指标）”与“Other Fields（其它字段）”两类。

此前，这些可选字段散落在规范各处或仅存在于实现代码中（如 `SnapshotSummary` 类常量、各引擎写入逻辑），缺乏统一的、面向规范读者的集中文档。不同引擎/实现对字段命名和语义的理解可能存在偏差。本提交通过在规范层面集中文档化，推动各实现（Spark、Flink、Trino、各厂商引擎）对这些字段达成一致，实现“标准化（Standardization）”目标——这也是 PR 标题中 Standardization 的含义。

同时，本提交在快照正文中新增一句指向新附录的链接，使读者从快照说明能顺畅跳转到可选字段清单。

## 如何达成设计目的

设计思路：在规范末尾新增附录章节，用两张表格分别罗列“指标类”与“其它类”可选字段。每行给出字段名（加粗并带反引号）与描述，必要时给出示例值。表格形式便于检索与对照，也便于实现者据此补齐或校验自身的 summary 输出。

### 修改详情

#### format/spec.md

1. 在快照 `operation` 字段说明之后新增一句引用：
   `For other optional snapshot summary fields, see [Appendix F](#optional-snapshot-summary-fields).`
   使正文与新增附录建立交叉引用。同时删除了正文与“Snapshot Row IDs”之间的一处多余空行，保持格式整洁。

2. 在规范末尾（Appendix E 时间旅行查询说明之后）新增章节 `### Optional Snapshot Summary Fields`：

   开篇说明：snapshot summary 可包含用于追踪数值指标的 metrics 字段（见 Metrics 小节）以及操作性细节字段（见 Other Fields 小节），所有这些字段的值应为字符串类型（例如 `"120"`）。这一“字符串化数值”的约定统一了序列化表示，避免不同实现把数字写成不同 JSON 类型。

3. 子章节 `#### Metrics`：一张表，列出 26 个指标字段，覆盖：
   - 数据文件：`added-data-files`、`deleted-data-files`、`total-data-files`
   - 删除文件（区分位置/等值/删除向量 DV）：`added-delete-files`、`added-equality-delete-files`、`removed-equality-delete-files`、`added-position-delete-files`、`removed-position-delete-files`、`added-dvs`、`removed-dvs`、`removed-delete-files`、`total-delete-files`
   - 记录数：`added-records`、`deleted-records`、`total-records`
   - 文件大小：`added-files-size`、`removed-files-size`、`total-files-size`
   - 位置/等值删除记录数：`added-position-deletes`、`removed-position-deletes`、`total-position-deletes`、`added-equality-deletes`、`removed-equality-deletes`、`total-equality-deletes`
   - 其它：`deleted-duplicate-files`（被删除的重复文件数，重复指在 manifest 中被记录多次）、`changed-partition-count`（本次有文件增删的分区数）
   值得注意的细节：`added-delete-files` / `removed-delete-files` / `total-delete-files` 的描述明确把“deletion vectors（DV）”与位置/等值删除文件并列计入，体现了 DV 已成为一类与 position/equality delete 并列的删除载体；而 `added-dvs` / `removed-dvs` 则单独统计 DV 数量。

4. 子章节 `#### Other Fields`：一张表，列出 5 个操作性字段及示例：
   - `wap.id`：Write-Audit-Publish 暂存快照的 id（示例 "12345678"）
   - `published-wap-id`：已发布的 WAP 快照 id
   - `source-snapshot-id`：cherry-pick 场景下原快照 id
   - `engine-name`：创建快照的引擎名（示例 "spark"）
   - `engine-version`：引擎版本（示例 "3.5.4"）
   这些字段不属指标，但携带操作上下文，便于审计与溯源。

工作逻辑：本提交为纯文档，不引入代码逻辑。其“工作逻辑”体现在为各实现提供契约——引擎在写入快照时应按本表命名并填入对应语义的字符串值，读取端可据此解析。标准化后，跨引擎的快照 summary 可被统一消费（如观测平台统计、WAP 工作流、cherry-pick 追溯）。

## 小结

- 成效：补齐了规范长期缺失的“可选快照摘要字段”集中文档，覆盖 26 个指标字段与 5 个操作字段，明确了字段名、含义与“值为字符串”的约定，为多引擎/多实现的一致性提供权威依据。同时把 deletion vectors（DV）相关字段纳入规范，反映了 DV 已是一等公民的删除载体。
- 影响范围：仅 `format/spec.md` 文档，46 行新增、1 行删除。不改变任何运行时行为，但会引导后续各实现校准其 summary 输出。
- 回迁到 1.4.x 的注意事项：纯文档变更，回迁零技术风险，可直接 cherry-pick。需注意：1.4.x 的 spec.md 与本提交基线可能存在差异（如 DV 字段、WAP 字段在 1.4.x 是否已存在或命名不同），回迁后应核对正文交叉引用锚点 `#optional-snapshot-summary-fields` 与新增章节标题一致，避免锚点失配。若 1.4.x 不支持 deletion vectors，则表中涉及 `*-dvs` / `added-delete-files`（含 DV）等字段的描述可能需酌情调整以匹配 1.4.x 实际语义，但作为“前瞻性规范文档”保留也无害。
