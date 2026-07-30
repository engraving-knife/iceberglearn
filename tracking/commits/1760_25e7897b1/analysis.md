# 提交 1760：Docs: Add documentation for Rate limiting in Spark Structured Streaming (#12217)

## 提交信息

- **序号**：1760 / 4088
- **哈希**：25e7897b1161d5548ff428e0ec9016d5934635a1
- **短哈希**：25e7897b1
- **日期**：2025-02-19 14:49:00 -0600
- **作者**：Prashant Singh
- **提交说明**：Docs: Add documentation for Rate limiting in Spark Structured Streaming (#12217)
- **PR/Issue**：#12217

## 总体目的

本提交旨在为 Spark Structured Streaming 中的速率限制（Rate limiting）功能补充文档说明。

Iceberg 的 Spark Structured Streaming 读取器支持两个速率限制选项：`streaming-max-files-per-micro-batch`（每个微批最大文件数）和 `streaming-max-rows-per-micro-batch`（每个微批最大行数）。这些选项允许用户控制流处理的吞吐量，避免单个微批处理过多数据导致资源不足或延迟过大。

在此之前，这两个流处理选项虽然在代码层面已实现，但在 Spark 配置文档中缺少说明，用户难以发现和使用。本提交通过在 `spark-configuration.md` 的读取选项表格中新增这两个配置项的说明来补齐文档缺口。

## 如何达成设计目的

提交在 `spark-configuration.md` 的读取选项表格（read options）中新增两行，分别描述 `streaming-max-files-per-micro-batch` 和 `streaming-max-rows-per-micro-batch` 两个配置项的默认值和作用说明。同时添加了一个 `!!! warning` 提示框，强调 `streaming-max-rows-per-micro-batch` 的使用注意事项。

## 修改详情

### `docs/docs/spark-configuration.md`（修改, +8/-0 lines）

**修改目的**：为 Spark Structured Streaming 的速率限制选项补充文档。

**工作逻辑**：在读取选项表格中，紧接 `stream-from-timestamp` 行之后新增两行配置说明：

1. **`streaming-max-files-per-micro-batch`**：
   - 默认值：`INT_MAX`（无限制）
   - 说明：每个微批处理的最大文件数。

2. **`streaming-max-rows-per-micro-batch`**：
   - 默认值：`INT_MAX`（无限制）
   - 说明：每个微批处理的最大行数。

3. **警告提示**：通过 `!!! warning` 提示框说明，`streaming-max-rows-per-micro-batch` 应始终大于表中任何数据文件中的记录数。因为流处理的最小单位是单个文件，如果某个数据文件的记录数超过了此限制，流将卡在该文件处无法继续（因为即使一个文件也无法通过限制检查）。

## 小结

- **成效**：为 Spark Structured Streaming 的两个速率限制配置项补充了文档说明，并提供了重要的使用注意事项警告。
- **影响范围**：仅涉及文档，不影响代码逻辑。影响使用 Spark Structured Streaming 读取 Iceberg 表的用户。
- **回迁到 1.4.x 的注意事项**：纯文档变更，无代码依赖，可安全回迁。需确认 1.4.x 分支中是否已实现这两个速率限制选项（`streaming-max-files-per-micro-batch` 和 `streaming-max-rows-per-micro-batch`），如果未实现则文档与代码不匹配。建议先确认实现状态再决定是否回迁文档。
