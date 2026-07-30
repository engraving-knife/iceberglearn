# 提交 4026：Flink: Backport: Remove converted equality deletes for same-branch conversion (#17189) (#17190)

## 提交信息

- **序号**：4026 / 4088
- **哈希**：f696640356d5233b6ae1fd82d2573efbc22989c7
- **短哈希**：f69664035
- **日期**：2026-07-13 17:02:21 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Remove converted equality deletes for same-branch conversion (#17189) (#17190)
- **PR/Issue**：#17190（backport of #17189）

## 总体目的

本提交是将提交 4024（#17189，"Flink: Remove converted equality deletes for same-branch conversion"）backport 到 Flink 1.20 和 2.0 两个版本。原提交只在 Flink 2.1 中修复了同分支就地转换时 equality delete 未被移除的问题，本次将相同改动应用到 `flink/v1.20` 和 `flink/v2.0`。

## 如何达成设计目的

将 4024 在 `flink/v2.1` 下的全部 6 个文件改动原样复制到 `flink/v1.20` 和 `flink/v2.0` 对应路径，共 12 个文件。代码逻辑与 4024 完全一致，仅包路径前缀不同。

## 修改详情

### `flink/v1.20/flink/...` (6 files)

**修改目的**：backport 到 Flink 1.20。

**工作逻辑**：与 4024 相同的改动：
- `EqualityConvertCommitter.java`：同分支转换时 `rowDelta.removeDeletes(eqDeleteFile)`，新增 `removedEqDeleteNum` 指标。
- `EqualityConvertPlan.java`：record 新增 `eqDeleteFiles` 字段。
- `EqualityConvertPlanner.java`：构建 plan 时传入 eqDeleteFiles。
- 三个测试文件：E2E、committer 单元测试、DVWriter 适配。

### `flink/v2.0/flink/...` (6 files)

**修改目的**：backport 到 Flink 2.0。

**工作逻辑**：与 Flink 1.20 完全相同的改动。

## 总结

本提交是 4024 的跨版本 backport，将"同分支转换移除 equality delete"的修复推广到 Flink 1.20 和 2.0，确保三个支持的 Flink 版本功能一致。代码逻辑无差异，仅是并行维护多个 Flink 版本分支的常规操作。
