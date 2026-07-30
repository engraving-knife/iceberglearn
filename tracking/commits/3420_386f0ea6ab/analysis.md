# 提交 3420：Flink: Backport: Add branch support to RewriteDataFiles maintenance task (#15672) (#15690)

## 提交信息

- **序号**：3420 / 4088
- **哈希**：386f0ea6abe6cd988178ee922b7cceaaf0d2d46b
- **短哈希**：386f0ea6ab
- **日期**：2026-03-19 15:49:42 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Add branch support to RewriteDataFiles maintenance task (#15672) (#15690)
- **PR/Issue**：#15690（backport of #15672）

## 总体目的

这是提交 3418（PR #15672）的 backport，将 Flink RewriteDataFiles 维护任务的分支支持功能应用到 Flink 的另一个版本分支。

## 如何达成设计目的

- 将 PR #15672 的所有改动复制到 Flink 的另一个版本目录
- 包含完全相同的文件和改动内容

## 修改详情

本 backport 包含与提交 3418 完全相同的改动，涉及以下文件（每个 Flink 版本一份）：

### 主要源文件
- `RewriteDataFiles.java` (+20/-2 lines)：Builder 新增 branch 字段和 branch() 方法
- `DataFileRewritePlanner.java` (+24/-4 lines)：从指定分支获取快照进行规划
- `DataFileRewriteCommitter.java` (+19/-3 lines)：将分支传递给提交管理器
- `IcebergSink.java` (+5/-1 lines)：传递分支信息
- `DataFileRewriteRunner.java` (+2/-1 lines)：适配 PlannedGroup
- `RewriteUtil.java` (+4/-1 lines)：适配 PlannedGroup

### 测试文件
- `TestRewriteDataFiles.java` (+35 lines)
- `TestDataFileRewritePlanner.java` (+45/-1 lines)
- `TestDataFileRewriteCommitter.java` (+4/-1 lines)
- `TestDataFileRewriteRunner.java` (+4/-1 lines)

## 总结

本提交是 PR #15672 的 backport，将 Flink RewriteDataFiles 维护任务的分支支持功能应用到 Flink 的另一个版本分支。改动内容与原始 PR 完全一致，详见提交 3418 的分析。
