# 提交 2892：Flink: Backport DynamicSink support dvs to Flink 2.0 and 1.20 (#14623)

## 提交信息

- **序号**：2892 / 4088
- **哈希**：3615fae6158e76e1ccf61c03ca5e91f5cfc538dd
- **短哈希**：3615fae61
- **日期**：2025-11-19 13:47:45 +0100
- **作者**：GuoYu
- **提交说明**：Flink: Backport DynamicSink support dvs to Flink 2.0 and 1.20 (#14623)
- **PR/Issue**：#14623

## 总体目的

本提交是提交 2890（`4ee507d57`，"Flink: DynamicSink support dvs"）的 backport，将动态 sink 对 DV（删除向量）的支持从 Flink 2.1 版本移植到 Flink 2.0 和 Flink 1.20 版本。

原始提交 2890 在 Flink 2.1 (`flink/v2.1/`) 目录下实现了 V3 表的 DV 支持，包括：
- 移除 `DynamicWriter` 对 V3+ 表 upsert 模式的禁止
- `DynamicWriteResultAggregator` 动态获取格式版本而非硬编码为 2
- `DynamicCommitter` 校验 V3+ 表的 position delete 必须是 DV

本 backport 将完全相同的修改应用到 `flink/v2.0/` 和 `flink/v1.20/` 两个目录，确保三个 Flink 版本的动态 sink 功能一致。

## 如何达成设计目的

直接将 2890 的代码变更复制到 Flink 2.0 和 1.20 对应的目录结构中。涉及 10 个文件（每个版本 5 个，含 main 和 test），共计 +766/-40 行。每个版本的修改内容与 2890 完全相同：

1. `DynamicCommitter.java`：增加 V3+ 表 position delete 的 DV 校验
2. `DynamicWriteResultAggregator.java`：缓存格式版本，动态使用
3. `DynamicWriter.java`：移除 V3+ upsert 禁止检查
4. `TestDynamicCommitter.java`：增加格式版本相关测试
5. `TestDynamicIcebergSink.java`：增加 V3 upsert 和多格式版本集成测试

## 修改详情

### `flink/v2.0/` 和 `flink/v1.20/` 下的对应文件 (每个版本 +366/-20 lines)

**修改目的**：与提交 2890 相同，为 Flink 2.0 和 1.20 提供动态 sink 的 DV 支持。

**工作逻辑**：与 2890 完全一致，请参考提交 2890 的分析文档了解详细的工作逻辑。两个 Flink 版本的修改内容完全相同，分别应用到各自的目录路径下。

## 总结

本提交是 2890 的机械性 backport，将动态 sink 的 DV 支持扩展到 Flink 2.0 和 1.20 版本，确保多版本一致性。无新增设计内容，具体分析请参见提交 2890。
