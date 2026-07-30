# 提交 3429：Flink: Backport: Fix non-deterministic operator UIDs in DynamicIcebergSink (#15687) (#15702)

## 提交信息

- **序号**：3429 / 4088
- **哈希**：0e5428cdc41d591f84164073a25f54c2dd5df9b6
- **短哈希**：0e5428cdc4
- **日期**：2026-03-20 14:30:17 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Fix non-deterministic operator UIDs in DynamicIcebergSink (#15687) (#15702)
- **PR/Issue**：#15702（backport of #15687）

## 总体目的

这是提交 3425（PR #15687）的 backport，将 DynamicIcebergSink 中 pre-commit 拓扑 operator UID 非确定性问题的修复应用到 Flink 的另一个版本分支。

## 如何达成设计目的

- 将 PR #15687 的改动复制到 Flink 的另一个版本目录
- 包含完全相同的文件和改动内容

## 修改详情

本 backport 包含与提交 3425 完全相同的改动（每个 Flink 版本一份）：

### 主要源文件
- `DynamicIcebergSink.java` (+2/-1 lines)：将 pre-commit 拓扑 UID 从 `prefixIfNotNull(uidPrefix, sinkId + "-pre-commit-topology")` 改为 `prefixIfNotNull(uidPrefix, "-pre-commit-topology")`，移除随机 sinkId

### 测试文件
- `TestDynamicIcebergSink.java` (+63 lines)：新增 `testOperatorUidsAreDeterministic` 和 `testOperatorUidsFormat` 测试

## 总结

本提交是 PR #15687 的 backport，将 DynamicIcebergSink operator UID 确定性修复应用到 Flink 的另一个版本分支。改动内容与原始 PR 完全一致，详见提交 3425 的分析。
