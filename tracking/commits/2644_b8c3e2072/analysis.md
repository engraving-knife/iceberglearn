# 提交 2644：Docs: Update Spark Structured Streaming docs for Rate Limiting & Triggers (#14030)

## 提交信息

- **序号**：2644 / 4088
- **哈希**：b8c3e20722728c8b16555e385d2c326d70a5d989
- **短哈希**：b8c3e2072
- **日期**：2025-09-16 09:51:35 -0700
- **作者**：Alex Prosak
- **提交说明**：Docs: Update Spark Structured Streaming docs for Rate Limiting & Triggers (#14030)
- **PR/Issue**：#14030

## 总体目的

Iceberg 的 Spark Structured Streaming 支持两个限流读取选项：`streaming-max-files-per-micro-batch`（每微批最大文件数）和 `streaming-max-rows-per-micro-batch`（每微批最大行数，软上限）。此前这些选项的文档说明存在不足：在 `spark-configuration.md` 的选项表中，`streaming-max-rows-per-micro-batch` 的描述放在表格下方的 warning 块中，不够直观；在 `spark-structured-streaming.md` 中，缺少关于限流选项的使用示例和与触发器（Trigger）配合使用的说明。

本提交改进了这两处文档：将行数限流的说明直接整合进选项表描述，并在结构化流文档中新增"Limit input rate"小节，提供代码示例并说明与 `Trigger.AvailableNow` 的配合使用。

## 如何达成设计目的

1. 在 `spark-configuration.md` 中，将原先独立的 warning 块内容合并到选项表 `streaming-max-rows-per-micro-batch` 行的描述中，使其更直观。
2. 在 `spark-structured-streaming.md` 中新增"Limit input rate"小节，介绍两个限流选项，提供 Scala 代码示例，并说明与 `Trigger.AvailableNow`/`Trigger.ProcessingTime` 的配合以及 `Trigger.Once` 不支持。

## 修改详情

### `docs/docs/spark-configuration.md` (+12/-17 lines)

**修改目的**：整合限流选项说明。

**工作逻辑**：重新格式化选项表（对齐列宽），将 `streaming-max-rows-per-micro-batch` 的描述从简短的"Maximum number of rows per microbatch"改为"Soft maximum number of rows per microbatch; always includes all rows in next unprocessed file, excludes additional files if their inclusion would exceed the soft max limit"。删除了表格下方单独的 warning 块（其内容已并入表格描述）。

### `docs/docs/spark-structured-streaming.md` (+27/-0 lines)

**修改目的**：新增限流选项使用说明和示例。

**工作逻辑**：在读取流警告之后新增"Limit input rate"小节，包含：
- 两个限流选项的说明（max files、soft max rows）。
- 说明两者同时设置时取先达到的限制。
- 两个 Scala 代码示例：分别演示设置 1 文件/微批和 1000 行/微批。
- info 提示：说明限流适用于默认触发器（`Trigger.ProcessingTime`）和 `Trigger.AvailableNow`（可将一次性处理拆分为多个微批以提升可扩展性），但 `Trigger.Once`（已废弃）会忽略限流选项。

## 总结

本提交改进了 Spark Structured Streaming 限流选项的文档：将分散的说明整合到选项表，并在结构化流文档中新增了带代码示例的限流使用指南，特别说明了与 `Trigger.AvailableNow` 的配合使用。这是纯文档改进，不影响代码逻辑。
