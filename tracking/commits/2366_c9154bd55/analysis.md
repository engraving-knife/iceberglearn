# 提交 2366：Docs: Document compute_partition_stats procedure (#13532)

## 提交信息

- **序号**：2366 / 4088
- **哈希**：c9154bd55e085e066e1405e36e138a3a73f3313a
- **短哈希**：c9154bd55
- **日期**：2025-07-17 11:16:27 -0700
- **作者**：Ajantha Bhat
- **提交说明**：Docs: Document compute_partition_stats procedure (#13532)
- **PR/Issue**：#13532

## 总体目的

这个提交为 Iceberg 的 `compute_partition_stats` Spark 过程补充了用户文档。该过程用于增量计算表的分区统计（partition stats），并将结果写入 `PartitionStatisticsFile` 并注册到表元数据中。

背景：Iceberg 的分区统计（partition statistics）是规范中定义的一种统计文件，记录各分区的统计信息，可用于查询优化时的分区裁剪。Spark 提供了 `compute_partition_stats` 过程来计算这些统计。该过程会从上一次拥有 `PartitionStatisticsFile` 的快照开始增量计算到目标快照（默认为当前快照），如果之前的分区统计文件不存在则执行全量计算。此前该过程缺少用户文档，用户无法从官方文档中了解其用法。本提交补充了完整的文档说明。

## 如何达成设计目的

在 `spark-procedures.md` 文档中新增 "Partition Statistics" 章节，包含过程描述、参数表、输出表和使用示例。

## 修改详情

### `docs/docs/spark-procedures.md` (+32/-0 lines)

**修改目的**：为 `compute_partition_stats` 过程补充文档。

**工作逻辑**：在 `compute_table_stats` 过程文档之后、`Table Replication` 章节之前新增 "Partition Statistics" 章节，包含：
- **过程描述**：说明 `compute_partition_stats` 增量计算分区统计（从上次有 `PartitionStatisticsFile` 的快照到目标快照），写入结果并注册到表元数据；无前置统计文件时执行全量计算。
- **参数表**：`table`（string，必填，表名）和 `snapshot_id`（string，可选，默认为当前快照 ID）。
- **输出表**：`partition_statistics_file`（string，生成的分区统计文件路径）。
- **示例**：两个使用示例——计算最新快照的分区统计（`CALL catalog_name.system.compute_partition_stats('my_table')`）和计算指定快照的分区统计（使用命名参数传入 `snapshot_id`）。

## 总结

该提交为 `compute_partition_stats` Spark 过程补充了完整的用户文档，包括过程描述、参数说明、输出格式和使用示例，使用户能了解并使用该过程来计算表的分区统计。纯文档变更，无代码改动。
