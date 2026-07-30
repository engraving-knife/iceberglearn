# 提交 3791：Docs: Document adaptive split sizing configurations

## 提交信息

- **序号**：3791 / 4088
- **哈希**：ebd0100b398dc9b8f60350866f9f064b4750cfc1
- **短哈希**：ebd0100b3
- **日期**：2026-05-27 14:23:02 -0500
- **作者**：Pratham Manja
- **提交说明**：Docs: Document adaptive split sizing configurations
- **PR/Issue**：无 PR 编号

## 总体目的

这个提交为 Spark 配置文档添加了自适应分片大小（adaptive split sizing）的配置项说明。这些配置项允许 Spark 在读取 Iceberg 表时自动调整分片（split）大小，基于扫描大小和并行度来优化读取性能。之前这些配置项在代码中已存在但未在文档中记录。

## 如何达成设计目的

在 Spark 配置文档的读取配置表格中新增两行配置项说明。

## 修改详情

### `docs/docs/spark-configuration.md` (+2/-0 lines)

**修改目的**：记录自适应分片大小配置项。

**工作逻辑**：在配置表格中新增两行：
1. **`spark.sql.iceberg.read.adaptive-split-size.enabled`**：默认值为 Table default。启用读取操作的自适应分片大小。启用后，分片大小会根据扫描大小和并行度自动调整。
2. **`spark.sql.iceberg.read.adaptive-split-size.parallelism`**：默认值为 `max(spark.default.parallelism, spark.sql.shuffle.partitions)`。覆盖自适应分片大小的并行度，必须大于 0。

## 总结

这是一个纯文档提交，为已有的自适应分片大小 Spark 配置项添加文档说明，使用户能够了解和使用这些优化读取性能的配置。
