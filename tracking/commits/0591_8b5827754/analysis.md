# 提交 0591：Docs: Enhance create_changelog_view usage

## 提交信息

- **序号**：0591 / 4088
- **哈希**：8b58277540fb9c5bc1936dcbc1a13793a72a6fee
- **短哈希**：8b5827754
- **日期**：2024-03-14（Thu Mar 14 00:54:20 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Enhance create_changelog_view usage (#9889)
- **PR/Issue**：#9889

## 总体目的

本提交是一个纯文档改动，目标是完善 Iceberg Spark 过程（procedure）`create_changelog_view` 的使用说明文档。该过程用于创建一个包含源表变更日志（changelog）的视图，是用户在 Spark 中查询 Iceberg 表行级变更的核心入口之一。

在此次改动之前，文档中存在两处不够准确或不友好的描述：

1. **参数默认值描述不准确**：原描述只说 `net_changes` 与 `compute_updates` 默认为 false，但实际实现中：
   - 当 `compute_updates=true` 时，`net_changes` 必须为 false（两者互斥，否则会重复处理变更语义）；
   - 当提供 `identifier_columns` 时，`compute_updates` 实际默认为 true（因为要计算 update 的 pre/post image 必须依赖 identifier 列）。

2. **示例段落缺少标题、过渡生硬**：原 changelog 净变更（net changes）的示例直接以一段说明 + 代码块的形式给出，没有小标题，读者在浏览长文档时不易定位。

本次提交通过修订这两处描述，让用户更准确地理解参数语义与默认行为，减少误用导致的运行时错误或不符合预期的查询结果。

## 如何达成设计目的

提交者通过修改单一 Markdown 文件 `docs/docs/spark-procedures.md` 达成目标，改动分为两部分：

1. **修订参数表描述**：在 `net_changes` 与 `compute_updates` 两行末尾补充约束条件与默认值规则。具体为：
   - `net_changes`：补充 "It must be false when `compute_updates` is true."，明确两者互斥。
   - `compute_updates`：将默认值描述从 "Defaults to false." 改为 "Defaults to true if `identifier_columns` are provided; otherwise, defaults to false."，准确反映与 identifier 列的联动关系。

2. **重构示例段落**：在 net changes 示例之前增加 `#### Net Changes` 小标题，并把原来紧贴代码块的说明拆分为独立的段落，使示例更易在文档大纲中被检索和定位。

这种"小步快跑"的文档增量改进方式符合开源项目中持续优化用户体验的常见做法，不引入任何代码变更，回迁风险极低。

## 修改详情

### `docs/docs/spark-procedures.md`

**修改目的**：让 `create_changelog_view` 的参数语义和示例展示更加准确清晰。

**工作逻辑**：

1. **参数表（约 756-760 行）**：
   - `net_changes` 行从原来的 `Whether to output net changes (see below for more information). Defaults to false.` 改为 `... Defaults to false. It must be false when compute_updates is true.`
   - `compute_updates` 行从 `... Defaults to false.` 改为 `... Defaults to true if identifier_columns are provided; otherwise, defaults to false.`

   这两处修订对齐了文档与代码实现。当用户启用 `compute_updates`（计算 update 的 pre/post image）时，必须先去重中间变更才能正确还原"旧值/新值"对，因此 `net_changes` 必须为 false；而 `compute_updates` 的默认行为本身依赖是否提供 identifier 列（无 identifier 列则无法定位行身份、无法计算 update），因此默认值会随 `identifier_columns` 的存在与否而变化。

2. **示例段落（约 823-830 行）**：将原本紧接表格之后的段落 `Create a changelog view that computes net changes. It removes intermediate changes and only outputs the net changes.` 替换为：
   - 新增 `#### Net Changes` 四级标题；
   - 新增过渡段落 `The procedure can remove intermediate changes across multiple snapshots, and only outputs the net changes. Here is an example to create a changelog view that computes net changes.`

   这样该示例在生成的文档站点上会被纳入目录大纲，便于读者跳转，并通过更完整的句子说明了 net changes 跨多个 snapshot 的语义。

## 小结

本提交是一个低风险的纯文档改动，主要修订了 `create_changelog_view` 过程中两个布尔参数的默认值与互斥约束说明，并给 net changes 示例段落加上小标题与更清晰的过渡说明。

- **影响范围**：仅文档站点，无任何代码或行为变化。
- **回迁到 1.4.x 的注意事项**：可直接 cherry-pick，不需要冲突解决（除非 1.4.x 分支的 spark-procedures.md 在相同行已有不同改动）。回迁后建议核对 1.4.x 中 `create_changelog_view` 实现是否已支持 `compute_updates` 与 `identifier_columns` 参数；若 1.4.x 实现尚未引入对应功能，则应连同功能 PR 一并回迁，否则文档会描述尚未实现的行为。
