# 提交 3978：Flink: Backport: Hold back equality delete converter watermark until completion (#17038) (#17067)

## 提交信息

- **序号**：3978 / 4088
- **哈希**：11706a286bc94f852aa6046d2d1ec6c3b85039a5
- **短哈希**：11706a286
- **日期**：2026-07-03 12:13:24 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Hold back equality delete converter watermark until completion (#17038) (#17067)
- **PR/Issue**：#17038, #17067

## 总体目的

本提交是 PR #17038（提交 3977）的 backport，将 equality delete converter 水印延迟修复同步到 Flink v1.20 和 v2.0 模块。原修复仅应用于 flink v2.1，但维护版本的 Flink 模块也需要同样的并发安全修复，以消除 CI 测试 `TestConvertEqualityDeletesE2E` 的 flakiness。

## 如何达成设计目的

将 v2.1 模块中的修复（`processWatermark` 重构和 `holdsBackWatermarkUntilCommit` 测试）原样同步到 v1.20 和 v2.0 模块。

## 修改详情

### `flink/v1.20/flink/src/main/java/.../EqualityConvertCommitter.java` 和 `flink/v2.0/flink/...` (+26/-18 lines each)

**修改目的**：延迟水印转发直到 cycle 完成。

**工作逻辑**：与提交 3977 完全相同——在 `processWatermark` 中，当 `planResult == null` 或水印 < doneTimestamp 时直接返回（hold back），仅在水印 >= doneTimestamp 时执行 commit 并转发水印。

### 对应的测试文件 (+45/-0 lines each)

**修改目的**：验证水印延迟转发行为。

## 总结

标准 backport 操作，将 v2.1 的并发安全修复同步到 v1.20 和 v2.0 模块，确保所有维护的 Flink 版本都不会因阶段水印提前释放维护锁而导致 cycle 重叠执行。
