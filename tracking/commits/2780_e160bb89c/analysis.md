# 提交 2780：Flink: Backport Support writing DVs in IcebergSink to Flink 2.0 and 1.20 (#14390)

## 提交信息

- **序号**：2780 / 4088
- **哈希**：e160bb89c949b73685d908823b439b416bec272e
- **短哈希**：e160bb89c
- **日期**：2025-10-21 17:49:47 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport Support writing DVs in IcebergSink to Flink 2.0 and 1.20 (#14390)
- **PR/Issue**：#14390（backport #14197）

## 总体目的

本提交是 PR #14197（提交 2778）向 Flink 1.20 和 Flink 2.0 版本的 backport。

2778 为 Flink IcebergSink 添加了 Deletion Vector（DV）写入支持，使 V3+ 格式的表能自动使用 PUFFIN 格式的 DV 文件替代传统的位置删除文件。该修复最初只应用于 Flink 2.1 版本。本提交将相同的修复同步到 Flink 1.20 和 2.0 两个版本。

注意：此 backport 只涉及 Flink 模块的代码（`flink/v1.20` 和 `flink/v2.0` 目录），因为核心的 `BaseTaskWriter` 改动（在 `core` 模块中）和 `TestTaskEqualityDeltaWriter` 改动（在 `data` 模块中）是跨版本共享的，已在 2778 中完成。

## 如何达成设计目的

将 2778 中 Flink 2.1 版本的所有修改完整复制到 `flink/v1.20` 和 `flink/v2.0` 两个目录下。涉及的文件和修改与 2778 的 Flink 部分完全一致，只是目录路径不同。

## 修改详情

### `flink/v1.20/` 和 `flink/v2.0/` 下各 13 个文件（共 26 个文件，+178/-72 lines）

**修改目的**：将 2778 的 Flink sink DV 支持同步到 Flink 1.20 和 2.0 版本。

**工作逻辑**：每个版本下的修改与 2778 中 Flink 2.1 版本的修改完全一致，详见 2778 的分析文档。主要修改包括：
- `BaseDeltaTaskWriter`、`PartitionedDeltaWriter`、`UnpartitionedDeltaWriter`：新增 `useDv` 参数，传递 `dvFileWriter()`
- `RowDataTaskWriterFactory`：根据 `TableUtil.formatVersion(table) > 2` 设置 `useDv`
- `FlinkManifestUtil.writeCompletedFiles`：新增 `formatVersion` 参数
- `IcebergFilesCommitter`、`IcebergWriteAggregator`：传递格式版本
- `DynamicWriter`：禁止 V3+ 表的 upsert 模式
- `DynamicWriteResultAggregator`：硬编码 formatVersion=2
- 测试文件：参数化扩展到 V2 和 V3+

## 总结

本提交是 2778（#14197）向 Flink 1.20 和 2.0 的 backport，确保三个支持的 Flink 版本都获得了 DV 写入支持。修改内容与原始 PR 的 Flink 部分完全一致，仅目录路径不同。核心模块（`BaseTaskWriter`）和 data 模块（`TestTaskEqualityDeltaWriter`）的改动在 2778 中已完成，无需重复。
