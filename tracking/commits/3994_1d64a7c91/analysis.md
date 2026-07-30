# 提交 3994：Flink: Backport: Resolve unpartitioned equality deletes across all partitions (#17018) (#17129)

## 提交信息

- **序号**：3994 / 4088
- **哈希**：1d64a7c9109cc03a3b445403be526fb59f59e2ed
- **短哈希**：1d64a7c91
- **日期**：2026-07-07 13:59:21 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Resolve unpartitioned equality deletes across all partitions (#17018) (#17129)
- **PR/Issue**：#17018, #17129

## 总体目的

本提交是 PR #17018（提交 3991）的 backport，将 unpartitioned equality delete 跨分区解析修复同步到 Flink v1.20 和 v2.0 模块。原修复仅应用于 flink v2.1，但维护版本的 Flink 模块也需要同样的分区演化正确性修复。

## 如何达成设计目的

将 v2.1 模块中的修复（索引键移除 spec ID、resolveSpecIds 状态、GLOBAL_DELETE_SPEC_ID 哨兵、测试用例）原样同步到 v1.20 和 v2.0 模块。

## 修改详情

### v1.20 和 v2.0 模块的同名文件

**修改目的**：同步修复到维护版本。

涉及文件（每个模块）：
- `EqualityConvertPKIndex.java`：新增 resolveSpecIds 状态和按 spec 范围应用 delete 逻辑。
- `EqualityConvertReader.java`：unpartitioned delete 使用 GLOBAL_DELETE_SPEC_ID，移除键中的 spec ID。
- `IndexCommand.java`：新增 deleteSpecId 字段和 GLOBAL_DELETE_SPEC_ID 常量。
- `StructLikeSerializer.java`：serializeKey 移除 specId 参数。
- `TestConvertEqualityDeletes.java`：新增跨分区演化测试。
- `TestEqualityConvertPKIndex.java`：新增参数化 spec scope 测试。
- `TestStructLikeSerializer.java`：更新键序列化测试。

## 总结

标准 backport 操作，将 v2.1 的分区演化正确性修复同步到 v1.20 和 v2.0 模块，确保所有维护的 Flink 版本都能正确处理 unpartitioned equality delete 跨分区删除。
