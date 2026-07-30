# 提交 3859：Docs: Clarify Spark `spark.sql.adaptive.advisoryPartitionSizeInBytes` (#16721)

## 提交信息

- **序号**：3859 / 4088
- **哈希**：fe91c9e3695c872096f67bed6d3e2af17a554972
- **短哈希**：fe91c9e36
- **日期**：2026-06-11 09:39:33 -0700
- **作者**：Cheng Pan
- **提交说明**：Docs: Clarify Spark `spark.sql.adaptive.advisoryPartitionSizeInBytes` (#16721)
- **PR/Issue**：#16721

## 总体目的

本提交澄清了 Iceberg 文档中关于 Spark 的 `spark.sql.adaptive.advisoryPartitionSizeInBytes` 参数的说明。该参数是 Spark AQE（Adaptive Query Execution）的核心配置，控制 Spark 在 exchange 阶段合并和拆分任务时的目标任务大小。

原有文档对该参数影响范围的描述不够准确，容易让用户产生误解：
1. 原文说该设置"也会影响用户执行的 repartition 或 sort"，但实际影响范围更广（所有非写入阶段）。
2. 原文将参数描述为"in-memory Spark row size"，但实际上它是"estimated Spark input shuffle data size"，两者的压缩特性不同——shuffle 数据通常是行式且压缩比较低，而写入文件是列式且压缩比较高。

本提交修正这些描述，帮助用户更准确地理解该参数与 `write.target-file-size-bytes` 的关系，从而正确设置参数值（通常需要比目标文件大小更大的值）。

## 如何达成设计目的

重写 `docs/docs/spark-writes.md` 中相关段落的措辞，使其更准确地反映 Spark AQE 的实际行为。

## 修改详情

### `docs/docs/spark-writes.md` (+6/-5 lines)

**修改目的**：澄清参数说明。

**工作逻辑**：

原文修改对比：

原文：
> These settings will also affect any user performed re-partitions or sorts.
> It is important again to note that this is the in-memory Spark row size and not the on disk
> columnar-compressed size, so a larger value than the target file size will need to be specified.

修改后：
> These settings will also affect other non-writing stages.
> It is important again to note that this is the estimated Spark input shuffle data size (typically,
> is row-based and compressed with a lower ratio) and not the write file size (typically, is columnar
> and compressed with a higher ratio), so a larger value than the target file size will need to be
> specified.

关键改动：
1. "any user performed re-partitions or sorts" → "other non-writing stages"：影响范围更准确。
2. "in-memory Spark row size" → "estimated Spark input shuffle data size"：更准确地描述数据类型。
3. 补充说明两种大小的压缩特性差异：shuffle 数据（行式、低压缩比）vs 写入文件（列式、高压缩比）。

## 总结

这是一次文档澄清，更准确地描述了 Spark AQE 的 `advisoryPartitionSizeInBytes` 参数的影响范围和数据特性。帮助用户理解为何该参数值通常需要大于 `write.target-file-size-bytes`——因为 shuffle 数据的压缩比低于列式文件。属于文档质量提升工作。
