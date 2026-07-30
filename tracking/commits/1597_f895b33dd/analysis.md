# 提交 1597：Spec: Add added-rows field to Snapshot (#11976)

## 提交信息

- **序号**：1597 / 4088
- **哈希**：f895b33dd0e3f6baa16d9e233cd4a44d056ac0be
- **短哈希**：f895b33dd
- **日期**：2025-01-17（Fri Jan 17 15:56:26 2025 -0600）
- **作者**：Russell Spitzer <russell.spitzer@gmail.com>
- **提交说明**：Spec: Add added-rows field to Snapshot (#11976)
- **PR/Issue**：#11976

## 总体目的

Iceberg 规范在 v3 引入了 Row Lineage（行血缘）机制，通过为每行分配全局唯一的 `_row_id` 来支持跨快照的行级追踪。Row Lineage 依赖两个已定义的快照级字段：`first-row-id`（本快照第一个数据文件第一行被分配的起始 `_row_id`）和表的 `next-row-id`（下一个快照可用的起始 id）。规范示例中已经在 Row Lineage 章节描述了一次提交如何为新增数据文件 `data1` / `data2` / `data3` 分配 `first_row_id`，并在提交时把 `next-row-id` 推进 225（100 + 0 + 125）。

但规范此前缺少一个"快照内总新增行数"的快照级聚合字段——读者要从 next-row-id 推进量反推，或需要遍历 manifest list 中所有新增 manifest 的 `added_rows_count` 自行求和。这在 Row Lineage 场景下尤其不便，因为快照本身需要一个明确的"本次新增了多少行"的元数据以便于校验、统计与 reader 决策。

本提交在 Iceberg 规范的 Snapshot 字段表中新增一个可选字段 `added-rows`，定义为本快照所有新增 manifest 中 `added_rows_count` 的总和；并要求当 Row Lineage 启用时该字段为必填。同时在 Row Lineage 章节的示例与字段说明处补充该字段的语义与计算示例。这是一个纯规范变更（spec.md），不包含实现代码。

## 如何达成设计目的

修改 `format/spec.md` 三处：

1. **Row Lineage 示例段补充计算说明**：在已有的"为 data2 / data3 计算 first_row_id"段落之后，新增一句说明本快照基于 manifest 中新增行的求和填入 `added-rows`（示例值 100，即 50 + 50，对应 manifest 级聚合；同时与 `next-row-id` 推进的 225 行示例相互区分——前者是 manifest 中 declared 的 added_rows_count 求和，后者是数据文件级别的实际行数求和）。
2. **Snapshot 字段表新增行**：在 `first-row-id` 行之后插入 `added-rows` 行，说明它是 v3 optional 字段，类型为 long，语义是"本快照所有新增 manifest 的 `added_rows_count` 之和"，并指明"启用 Row Lineage 时为 required"。同时把 `first-row-id` 行末尾的多余空格去掉以保持对齐。
3. **Row Lineage 字段说明段补充定义**：在"`first-row-id` 是 snapshot 的 manifest list 中分发的起始 first_row_id"之后，新增一句"`added-rows` 是所有新增 manifest 的 `added_rows_count` 之和"。

### 修改详情

#### `format/spec.md`

**修改目的**：在规范层面为 Snapshot 引入 `added-rows` 字段，完善 Row Lineage 的元数据模型。

**工作逻辑**：

- 在 Row Lineage 示例段（约第 411 行）追加：

```markdown
The snapshot then populates the total number of `added-rows` based on the sum of all added rows in the manifests: 100 (50 + 50)
```

  与下文"next-row-id 推进 225（100 + 0 + 125）"形成对照，明确 `added-rows`（manifest 级聚合）与 `next-row-id` 推进量（文件级聚合）的区别。

- 在 Snapshot 字段表（约第 666 行）的 `first-row-id` 行后插入：

```markdown
|            |            | _optional_ | **`added-rows`**             | Sum of the [`added_rows_count`](#manifest-lists) from all manifests added in this snapshot. Required if [Row Lineage](#row-lineage) is enabled |
```

  该字段在 v1 / v2 列均为空（仅 v3 引入），在 v3 列为 `_optional_`，但通过描述中"Required if Row Lineage is enabled"绑定到 Row Lineage 启用条件。

- 在 Row Lineage 字段说明段（约第 698 行）追加：

```markdown
The snapshot's `added-rows` is the sum of all the  [`added_rows_count`](#manifest-lists) in all added manifests.
```

## 小结

- **成效**：规范层面补齐了 Row Lineage 所需的快照级"本次新增行数"元数据，使 reader / 校验逻辑 / 统计工具可以直接从 snapshot 元数据读取该值，而无需遍历 manifest list 自行求和。同时与已有 `first-row-id`、`next-row-id` 共同构成完整的行级血缘元数据三联。
- **影响范围**：仅修改 `format/spec.md`，新增约 7 行 markdown，不涉及任何代码实现。这是规范先行的改动，后续实现需要在 `Snapshot` 数据模型、reader、writer 与 metadata parser 中支持该字段。
- **回迁到 1.4.x 的注意事项**：规范文档变更不直接影响 1.4.x 运行时产物，但 1.4.x 若仅支持到 v2 表格式则该字段（v3 only）不会出现在 1.4.x 写出的快照中。1.4.x 文档可选择同步此规范段落以保持与 main 一致，但非必需。真正影响发生在后续实现 PR 中（在 `Snapshot` 模型新增 `added-rows` 解析与序列化），届时需要按 1.4.x 是否支持 v3 评估回迁必要性。
