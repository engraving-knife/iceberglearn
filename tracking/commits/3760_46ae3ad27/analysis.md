# 提交 3760：Docs: Update information about metrics mode (#16391)

## 提交信息

- **序号**：3760 / 4088
- **哈希**：46ae3ad2749cdcb7877d86babd0357b42f05823b
- **短哈希**：46ae3ad27
- **日期**：2026-05-21 07:44:27 +0200
- **作者**：Vrishabh
- **提交说明**：Docs: Update information about metrics mode (#16391)
- **PR/Issue**：#16391

## 总体目的

这个提交更新了 Iceberg 配置文档中关于 metrics mode（指标模式）的说明。原来文档在 `write.metadata.metrics.default` 和 `write.metadata.metrics.column.col1` 两个配置项的描述中只是简单列出了可选值（`none`、`counts`、`truncate(length)`、`full`），但没有详细解释每种模式具体持久化哪些指标。这对用户理解不同模式的行为差异造成了困难。

## 如何达成设计目的

在配置表格后新增一个 Notes（注释）段落，用脚注 `[1]` 的方式引用表格中的两行，详细解释四种指标模式各自持久化的指标内容。

## 修改详情

### `docs/docs/configuration.md` (+10/-2 lines)

**修改目的**：为 metrics mode 配置项添加详细的模式说明。

**工作逻辑**：
1. 在表格中 `write.metadata.metrics.default` 和 `write.metadata.metrics.column.col1` 两行的描述末尾添加 `[1]` 脚注引用。
2. 在表格之后新增 Notes 段落，详细说明四种模式：
   - **none**：不持久化任何指标
   - **counts**：仅持久化计数指标（value_counts、null_value_counts、nan_value_counts）
   - **truncate(length)**：持久化计数加截断的边界（lower_bounds、upper_bounds）。截断只适用于 string 和 binary 类型，其他类型按原值存储
   - **full**：持久化所有指标，包括完整的 lower_bounds 和 upper_bounds

## 总结

这是一个纯文档改进提交，通过为 metrics mode 配置项添加详细的模式说明，帮助用户理解不同指标模式之间的差异，从而根据实际需求选择合适的模式。特别是明确了 `truncate(length)` 模式只对 string 和 binary 类型进行截断，其他类型存储完整值这一重要细节。
