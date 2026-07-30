# 提交 1877：Core: Replace withFailMessage() with as() (#12570)

## 提交信息

- **序号**：1877 / 4088
- **哈希**：5ce86a3e8d1082172c3b5dcaaa9e8443ec9449d3
- **短哈希**：5ce86a3e8
- **日期**：2025-03-19 09:35:05 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Replace withFailMessage() with as() (#12570)
- **PR/Issue**：#12570

## 总体目的

本提交将测试中 AssertJ 的 `withFailMessage()` 调用替换为功能等价的 `as()` 方法。

背景：AssertJ 中 `withFailMessage()` 和 `as()` 在设置失败消息上功能相同，但 `withFailMessage()` 在某些 AssertJ 版本中被标记为废弃或不再推荐，官方推荐使用 `as()`。本提交统一使用 `as()` 以保持代码风格一致并避免废弃 API 警告。

## 如何达成设计目的

直接将 `TestSnapshotProducer` 中 `assertThat(writerCount).withFailMessage(errMsg).isEqualTo(...)` 的 `withFailMessage(errMsg)` 替换为 `as(errMsg)`，两者语义完全相同。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestSnapshotProducer.java` (修改, +1/-1 lines)

**修改目的**：将 `withFailMessage()` 替换为 `as()`。

**工作逻辑**：在 `assertManifestWriterCount` 方法中，`assertThat(writerCount).withFailMessage(errMsg).isEqualTo(expectedManifestWriterCount)` 改为 `assertThat(writerCount).as(errMsg).isEqualTo(expectedManifestWriterCount)`。两者都为断言设置失败时的描述消息，行为一致。

## 总结

本提交是单行测试代码清理，将 AssertJ 的 `withFailMessage()` 替换为推荐的 `as()`，无功能变更。
