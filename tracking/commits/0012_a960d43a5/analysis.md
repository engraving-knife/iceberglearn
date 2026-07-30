# 提交 0012：Docs: Document publish_changes procedure (#8706)

## 提交信息

- **序号**：0012 / 4088
- **哈希**：a960d43a58cc201183027c38ce679f7bf3a43891
- **短哈希**：a960d43a5
- **日期**：2023-10-05 08:36:34 +0200
- **作者**：Naveen Kumar
- **提交说明**：Docs: Document publish_changes procedure (#8706)
- **PR/Issue**：#8706

## 总体目的

这个提交为 Iceberg 的 Spark 存储过程（stored procedures）文档补充了 `publish_changes` 过程的说明。Iceberg 在 Spark 中提供了一系列系统存储过程（通过 `CALL catalog_name.system.<procedure>(...)` 调用），用于执行表维护与版本管理操作，如 `cherrypick_snapshot`、`fast_forward`、`expire_snapshots`、`rewrite_data_files` 等。这些过程是用户操作 Iceberg 表的主要入口之一，因此文档的完整性直接影响用户体验。

`publish_changes` 过程对应 Iceberg 的 WAP（Write-Audit-Publish，写入-审计-发布）工作流。WAP 是一种数据质量控制模式：写入方先以一个 `wap_id` 将数据"暂存（stage）"为一个 staged snapshot，此时数据不会成为表的当前可见状态；审计方对该 staged 数据进行检查；通过审计后，再通过 `publish_changes` 把该 staged snapshot 发布为表的当前状态。这种模式把"写入"与"发布"解耦，让数据在对外可见前有机会被审计，是数据治理中的常见需求。

在本提交之前，`publish_changes` 过程虽然在 Spark 实现中存在，但文档中并未列出，用户难以发现和正确使用。本提交在 `docs/spark-procedures.md` 的 `cherrypick_snapshot` 与 `fast_forward` 之间新增了 `publish_changes` 小节，完整说明了其语义、参数、输出与用法示例，补齐了 WAP 工作流文档化的缺口。

## 如何达成设计目的

整体设计思路是纯文档补充，不涉及任何代码改动。在 `docs/spark-procedures.md` 文件中，按照该文档既有的过程描述体例（标题、说明、Usage 参数表、Output 输出表、Examples 示例），在 `cherrypick_snapshot` 小节之后、`fast_forward` 小节之前插入 `publish_changes` 小节，使其与其它版本管理类过程（cherrypick、fast_forward）归类相邻，符合文档的逻辑组织。

## 修改详情

### `docs/spark-procedures.md`

**修改目的**：新增 `publish_changes` 存储过程的完整文档说明。

**工作逻辑**：在 `cherrypick_snapshot` 示例之后插入新的 `### publish_changes` 小节，共 38 行，内容结构如下：

1. **语义说明**：`publish_changes` 用于将某个 staged WAP ID 的变更发布到表的当前状态；它会基于已存在的 staged snapshot 创建一个新 snapshot，而不修改或删除原始 snapshot，因此是"发布"而非"移动"。并明确限制：只有 append 和 dynamic overwrite 类型的 snapshot 才能被成功发布。

2. **缓存提示**：用一个 `{{< hint info >}}` 提示块说明该过程会使所有引用受影响表的已缓存 Spark 计划失效，与其它修改表的过程保持一致的行为说明。

3. **Usage 参数表**：列出两个必填参数（标 ✔️）——`table`（string，要更新的表名）和 `wap_id`（long，要从 stage 发布到 prod 的 wap_id）。注意此处类型标注为 long，而示例中传入的是字符串形式的 wap_id，这与 WAP ID 通常作为字符串暂存的实现一致。

4. **Output 输出表**：列出两个输出字段——`source_snapshot_id`（long，发布变更前表的当前 snapshot ID）和 `current_snapshot_id`（long，应用变更后创建的新 snapshot ID）。这两个字段让调用方能追踪发布前后的 snapshot 变化。

5. **Examples 示例**：给出两种调用形式——位置参数形式 `CALL catalog_name.system.publish_changes('my_table', 'wap_id_1')` 和命名参数形式 `CALL catalog_name.system.publish_changes(wap_id => 'wap_id_2', table => 'my_table')`，与文档中其它过程的示例风格一致。

## 小结

本提交通过在 Spark 存储过程文档中补充 `publish_changes` 小节，完整记录了 WAP 工作流中"发布暂存变更"这一关键操作的使用方式，使用户能够发现并正确使用该能力，补齐了 WAP 功能文档化的缺口。
