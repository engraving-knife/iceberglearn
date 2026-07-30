# 提交 3144：Docs: Fix publish_changes wap_id parameter type (#15117)

## 提交信息

- **序号**：3144 / 4088
- **哈希**：73a26fc1f49e6749656a273b2e4d78eb9e64f19e
- **短哈希**：73a26fc1f
- **日期**：2026-01-22
- **作者**：Huaxin Gao
- **提交说明**：Docs: Fix publish_changes wap_id parameter type (#15117)
- **PR/Issue**：#15117

## 总体目的

Iceberg 官方文档中 `system.publish_changes` 存储过程的参数表把 `wap_id` 的类型错误地标成了 `long`，但实际实现里 `wap_id` 是一个字符串。WAP（Write-Audit-Publish）流程中，`wap_id` 是用户通过 Spark 配置 `spark.wap.id` 设置的字符串标识，写入时被记录到 staged 快照的 summary（`staged-wap-id`），发布时 `PublishChangesProcedure` 用该字符串与 `WapUtil.stagedWapId(snapshot)` 做相等匹配来定位要发布的快照。文档把类型标成 `long` 会误导用户：可能让他们误以为需要传一个数值（例如快照 ID），而实际上传数值会导致无法匹配到任何 staged 快照（抛 `Cannot apply unknown WAP ID`）。本提交把文档中的类型从 `long` 改为 `string`，使文档与实现一致，消除用户误解。

## 如何达成设计目的

直接修改 `docs/docs/spark-procedures.md` 中 `publish_changes` 过程的参数表：把 `wap_id` 行的 Type 列由 `long` 改为 `string`，同时对表格分隔符做格式化对齐（把 `| Type |` 与分隔线 `|------|` 调整为 `| Type   |` 与 `|--------|` 以容纳更长的列内容，保持 markdown 表格渲染整齐）。改动仅限文档，不涉及任何代码逻辑。

## 修改详情

### `docs/docs/spark-procedures.md` (+3/-3 lines)

**修改目的**：修正 `publish_changes` 过程 `wap_id` 参数类型并整理表格格式。

**工作逻辑**：
- 参数表表头与分隔行由 `| Argument Name | Required? | Type | Description |` / `|---------------|-----------|------|-------------|` 改为 `| Argument Name | Required? | Type   | Description |` / `|---------------|-----------|--------|-------------|`，加宽 Type 列以对齐。
- `wap_id` 行由 `| wap_id | ✔️ | long | The wap_id to be published from stage to prod |` 改为 `| wap_id | ✔️ | string | The wap_id to be published from stage to prod |`，把类型从 `long` 修正为 `string`。
- `table` 行内容不变（仍为 `string`），仅因列宽调整而重新对齐。

这与实际实现 `PublishChangesProcedure` 中 `ProcedureParameter.required("wap_id", DataTypes.StringType)` 以及测试中 `String wapId = "wap_id_1"`、`CALL ...system.publish_changes('%s', '%s')` 传字符串的行为完全一致，修正后文档准确反映了 `wap_id` 是字符串标识而非数值。

## 总结

该文档修复把 `publish_changes` 存储过程 `wap_id` 参数类型从错误的 `long` 更正为 `string`，使官方文档与 `PublishChangesProcedure` 的实际字符串参数定义及 WAP 流程的字符串标识语义保持一致，避免用户误传数值类型而无法发布 staged 快照，同时整理了表格列宽以保持渲染整齐。
