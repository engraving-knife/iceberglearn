# 提交 2782：Flink: backport PR #14381 for TestStatisticsOrRecordSerializer (#14393)

## 提交信息

- **序号**：2782 / 4088
- **哈希**：c8732b88a829e1acf29d64610a41f7de1cfe9aef
- **短哈希**：c8732b88a
- **日期**：2025-10-21 10:07:58 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Flink: backport PR #14381 for TestStatisticsOrRecordSerializer (#14393)
- **PR/Issue**：#14393（backport #14381）

## 总体目的

本提交是 PR #14381（提交 2780）向 Flink 1.20 和 Flink 2.0 版本的 backport。

2780 为 Flink 2.1 的 `StatisticsOrRecordSerializer` 添加了基于 `SerializerTestBase` 的序列化测试，并为 `StatisticsOrRecord` 类补充了 `equals`/`hashCode` 方法。本提交将相同的修改同步到 Flink 1.20 和 2.0 两个版本。

## 如何达成设计目的

将 2780 的所有修改完整复制到 `flink/v1.20` 和 `flink/v2.0` 两个目录下。涉及的文件和修改与 2780 完全一致：

1. `StatisticsOrRecord.java`：添加 `equals` 和 `hashCode` 方法
2. `Fixtures.java`：添加 `STATISTICS_OR_RECORD_SERIALIZER` 常量，`ROW_SERIALIZER` 类型改为 `RowDataSerializer`
3. `TestStatisticsOrRecordSerializer.java`：新增继承 `SerializerTestBase` 的测试类

## 修改详情

### `flink/v1.20/` 和 `flink/v2.0/` 下各 3 个文件（共 6 个文件，+190/-4 lines）

**修改目的**：将 2780 的序列化器测试同步到 Flink 1.20 和 2.0 版本。

**工作逻辑**：每个版本下的修改与 2780 中 Flink 2.1 版本的修改完全一致，详见 2780 的分析文档。

## 总结

本提交是 2780（#14381）向 Flink 1.20 和 2.0 的 backport，确保三个支持的 Flink 版本都有 `StatisticsOrRecordSerializer` 的测试覆盖。修改内容与原始 PR 完全一致，仅目录路径不同。
