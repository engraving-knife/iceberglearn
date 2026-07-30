# 提交 0218：Flink: Document watermark generation feature (#9179)

## 提交信息

- **序号**：0218 / 4088
- **哈希**：8519224de33b26cfd7c539ffe6f123ea66165711
- **短哈希**：8519224de
- **日期**：2023-12-05
- **作者**：pvary
- **提交说明**：Flink: Document watermark generation feature (#9179)
- **PR/Issue**：#9179

## 总体目的

Iceberg 的 Flink `IcebergSource` 已经具备一项较新但尚未在文档中说明的能力：源端可以基于 Iceberg 数据文件的列级 min-max 统计信息自行生成并发射 Flink `Watermark`，而不需要用户在算子里手动用 `WatermarkStrategy.forBoundedOutOfOrderness` 之类的方式生成。本提交是纯文档提交（`docs/flink-queries.md` 新增 69 行），目的是把这一"source 发射 watermark"特性正式写入用户文档，让用户知道如何启用、何时启用、有哪些注意事项。

该特性的价值在于：（1）配合 Flink 的 [Watermark Alignment](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/event-time/generating_watermarks/#watermark-alignment) 抑制多源间 watermark 漂移；（2）在并发读取多个数据文件时避免 window 被过早触发——因为 source 知道每个 split（数据文件）的全局时间范围，可以基于列统计给出更"准确"的下界 watermark，而不是基于逐条记录。文档同时坦率说明该特性依赖列级 stats、对小文件合并有副作用等限制，并给出推荐配置。

## 如何达成设计目的

整篇新增章节插入到 `docs/flink-queries.md` 的 "Emitting watermarks" 小节（紧跟 Avro GenericRecord 读取示例之后、Options 之前）。结构上先讲动机（为什么需要 source 发 watermark），再讲启用方式（`watermarkColumn` + `watermarkTimeUnit`），然后讲原理与限制（基于列 stats、每个 split 发一次、小文件合并的影响），最后给出两段完整可运行的代码示例（timestamp 列与 long 列），覆盖最常见的两种使用场景。

## 修改详情

### `docs/flink-queries.md`

**修改目的**：新增 "Emitting watermarks" 章节介绍 Flink `IcebergSource` 的 watermark 生成能力。

**工作逻辑**：新增内容包括：

1. **动机**：source 发 watermark 可用于 Watermark Alignment，或避免并发读多文件时 window 触发过早。

2. **启用方式**：在 `IcebergSource` builder 上设置 `watermarkColumn`。支持三种列类型：`timestamp`、`timestamptz`（Iceberg 类型自带精度，无需额外配置）、`long`（不含时间单位，需用 `watermarkTimeUnit` 指定转换，如 `TimeUnit.MILLI_SCALE`）。

3. **原理与限制**：
   - watermark 基于数据文件列级 min-max stats，每个 split 发一次。
   - 若多个小文件被合并到一个 split，会增加乱序度与 Flink state 缓存。因此建议把 `read.split.open-file-cost` 调到非常大（如 `TableProperties.SPLIT_SIZE_DEFAULT`），阻止小文件合并。代价是读吞吐下降（尤其小文件多时），但状态型作业里源吞吐通常不是瓶颈，是合理取舍。
   - 该特性依赖列级 min-max stats，写入阶段要确保 watermark 列开了 stats。默认只对前 100 列收集 metrics，否则需用 `write.metadata.metrics.*` 写属性开启（链接到 `configuration.md#write-properties`）。

4. **代码示例一**（timestamp 列 + windowing）：用 `IcebergSource.forRowData().tableLoader(...).watermarkColumn("timestamp_column").build()`，配合 `WatermarkStrategy.<RowData>noWatermarks()`（因为 watermark 由 source 发，无需手动生成）+ `.withTimestampAssigner(...)` 从记录中提取 event time。

5. **代码示例二**（long 列 + watermark alignment）：在 builder 上额外 `.set(FlinkReadOptions.SPLIT_FILE_OPEN_COST, String.valueOf(TableProperties.SPLIT_SIZE_DEFAULT))` 阻止小文件合并，`.watermarkColumn("long_column").watermarkTimeUnit(TimeUnit.MILLI_SCALE)`，`WatermarkStrategy` 用 `.withWatermarkAlignment(watermarkGroup, maxAllowedWatermarkDrift)` 启用对齐。

## 小结

本提交为 Flink `IcebergSource` 的源端 watermark 生成能力补齐用户文档，让一项已实现但缺乏说明的特性变得可被发现与正确使用，降低用户在事件时间作业上的踩坑成本。
