# 提交 0593：澄清 max-inferred-column-defaults 表属性文档

## 提交信息

- **序号**：0593 / 4088
- **哈希**：969d4ee60f8196d10758d03680bc0157b9d61fa8
- **短哈希**：969d4ee60
- **日期**：2024-03-14
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Docs: Clarify table property on metrics for inferred column defaults (#9865)
- **PR/Issue**：#9865

## 总体目的

本提交是纯文档澄清，目标是修正 `write.metadata.metrics.max-inferred-column-defaults` 表属性说明中的歧义，使其准确反映 `MetricsConfig` 的实际实现行为。

原说明为"Defines the maximum number of columns for which metrics are collected"（定义收集 metrics 的最大列数）。这句话容易被误解为"实际存储的 metrics 条目数上限"，但实际实现中该限制只作用于**顶层列**（top-level columns）的数量，而不是底层存储的 metrics 条目数。对于包含嵌套字段（nested fields）的表，启用一个顶层 struct 列的 metrics 会连带收集其所有嵌套子字段的 metrics，因此最终存储的 metrics 条目数会大于该上限值。

这种文档歧义会让用户在配置宽表（尤其是含嵌套字段的表）时产生困惑：明明设置了上限为 100，实际存储的 metrics 条目却可能远超 100，导致用户误以为配置失效或出现 bug。

## 如何达成设计目的

通过修改 `docs/docs/configuration.md` 中该属性行的描述，明确两点：

1. **限制作用于顶层列**：将"the maximum number of columns"改为"the maximum number of top level columns"，明确限制对象是顶层列而非所有列。
2. **说明嵌套场景下的实际行为**：追加一句"Number of stored metrics can be higher than this limit for a table with nested fields"，告知用户含嵌套字段时实际存储的 metrics 数量会高于此上限，避免误解。

这种澄清与 `MetricsConfig.from(props, schema, order)` 的实现逻辑一致：实现中通过 `schema.columns().size()` 判断顶层列数是否超过 `maxInferredDefaultColumns`，若超过则只对前 N 个顶层列（`schema.columns().subList(0, maxInferredDefaultColumns)`）启用默认 metrics 模式，再通过 `TypeUtil.getProjectedIds(subSchema)` 收集这些顶层列下的所有嵌套字段 ID 并逐一设置 metrics 模式。因此实际存储的 metrics 条目数 = 选中顶层列及其所有嵌套子字段的总数，可能远大于顶层列数上限。

## 修改详情

### `docs/docs/configuration.md`

**修改目的**：澄清 `write.metadata.metrics.max-inferred-column-defaults` 属性的语义。

**工作逻辑**：该属性行（位于表属性表格第 64 行附近）的描述由：

```
Defines the maximum number of columns for which metrics are collected
```

改为：

```
Defines the maximum number of top level columns for which metrics are collected. Number of stored metrics can be higher than this limit for a table with nested fields
```

改动共两处：
1. "columns" 前加 "top level"，限定限制对象为顶层列。
2. 追加第二句说明嵌套字段场景下实际存储 metrics 数量会更高。

## 小结

本提交是 1 行文档修改（1 增 1 删），影响范围仅限 `docs/docs/configuration.md` 一个文件。

**成效**：
- 消除了 `max-inferred-column-defaults` 属性说明的歧义，明确限制对象是顶层列而非 metrics 条目总数。
- 对含嵌套字段的表的 metrics 行为提供了准确预期，避免用户误判配置失效。
- 与 `MetricsConfig` 的实际实现逻辑（基于 `schema.columns().size()` 与 `TypeUtil.getProjectedIds`）保持一致。

**回迁到 1.4.x 的注意事项**：
- 该提交为纯文档修改，回迁风险极低，不依赖任何代码变更。
- 回迁前应确认 1.4.x 分支的 `MetricsConfig` 实现是否同样基于顶层列计数（`schema.columns().size()`）而非全部列计数。若 1.4.x 实现不同（例如直接按全部列计数），则该文档澄清不适用，需要根据 1.4.x 实际行为调整描述。
- 该属性最早由 PR #5215（commit a25381773，2022-07-08）引入，1.4.x 应已具备此属性，文档行位置应能直接对应。
