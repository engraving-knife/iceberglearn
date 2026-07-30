# 提交 3912：Flink: Backport: Add equality delete conversion committer (#16874) (#16888)

## 提交信息

- **序号**：3912 / 4088
- **哈希**：2a2d5c0a6230a1df08bbca8dc5c93d53122081fd
- **短哈希**：2a2d5c0a6
- **日期**：2026-06-20 11:19:25 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Add equality delete conversion committer (#16874) (#16888)
- **PR/Issue**：#16874, #16888

## 总体目的

将提交 3911（PR #16874）的 equality delete 转换提交器算子代码从 Flink 2.1 backport（向后移植）到 Flink 1.20 和 2.0 版本。这确保了 `EqualityConvertCommitter` 算子在所有受支持的 Flink 版本上同步可用，完成了 equality delete 转换功能在三个 Flink 版本上的完整交付。

## 如何达成设计目的

将 Flink 2.1 中新增的 2 个文件完整复制到 Flink 1.20 和 2.0 的对应目录下，文件内容和功能完全一致。

## 修改详情

### Flink 1.20 和 2.0 新增文件（各 2 个文件，共 4 个文件，+1774 lines）

以下文件从 `flink/v2.1/` 复制到 `flink/v1.20/` 和 `flink/v2.0/` 的对应路径：

- `flink/maintenance/operator/EqualityConvertCommitter.java` (+363 lines each)
- `flink/maintenance/operator/TestEqualityConvertCommitter.java` (+524 lines each)

**修改目的**：将 equality delete 转换提交器同步到 Flink 1.20 和 2.0。

**工作逻辑**：
所有文件内容与提交 3911（Flink 2.1 版本）完全一致，包括提交器实现和详细测试类。

## 总结

将 equality delete 转换的提交器算子从 Flink 2.1 backport 到 Flink 1.20 和 2.0，确保三个 Flink 版本的功能一致性。共新增 4 个文件（每个 Flink 版本 2 个），内容与原始提交完全一致。至此，equality delete 转换的完整处理链路（数据模型 -> Reader/PK Index -> DV Writer -> Committer）已在所有三个 Flink 版本上同步交付。有关提交器的详细分析，请参见提交 3911 的分析文档。
