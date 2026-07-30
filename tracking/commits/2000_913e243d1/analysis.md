# 提交 2000：Flink: Fix TriggerManager parameter list for recovery tests

## 提交信息

- **序号**：2000 / 4088
- **哈希**：913e243d1551932a31a0cd72d2318ee8cf96b0e0
- **短哈希**：913e243d1
- **日期**：2025-04-15 15:02:38 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Fix TriggerManager parameter list for recovery tests (#12800)
- **PR/Issue**：#12800

## 总体目的

本提交修复了 `TestTriggerManager` 中恢复测试（recovery tests）参数列表的一个错误。在 `parametersForTestRecovery()` 方法中，参数组合存在重复项 `Arguments.of(true, false)` 出现了两次，而缺少 `Arguments.of(true, true)` 这一组合。

该参数化方法为恢复测试提供四组布尔参数组合（两个布尔值代表两种不同的测试条件），正确的组合应该是所有四种可能的布尔对：(true, true)、(true, false)、(false, true)、(false, false)。但由于笔误，第一组被写成了 `(true, false)` 而非 `(true, true)`，导致 `(true, true)` 组合从未被测试，而 `(true, false)` 组合被重复测试。

此修复同时应用于 Flink 1.19 和 Flink 1.20 两个模块。

## 如何达成设计目的

将 `parametersForTestRecovery()` 方法中的第一个参数组合从 `Arguments.of(true, false)` 修正为 `Arguments.of(true, true)`，使四组参数覆盖所有布尔组合。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManager.java` (修改, +1/-1 lines)

**修改目的**：修正恢复测试参数列表中的错误组合。

**工作逻辑**：
将 `parametersForTestRecovery()` 中的第一个参数组合从：
```java
Arguments.of(true, false),
```
修正为：
```java
Arguments.of(true, true),
```
这样四组参数变为 (true, true)、(true, false)、(false, true)、(false, false)，完整覆盖所有布尔组合。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManager.java` (修改, +1/-1 lines)

**修改目的**：同上，修正 Flink 1.20 模块中的相同错误。

**工作逻辑**：与 Flink 1.19 完全相同的修正。

## 总结

本提交修复了 `TestTriggerManager` 恢复测试参数列表中的笔误，将重复的 `(true, false)` 组合修正为缺失的 `(true, true)` 组合，确保恢复测试覆盖所有四种布尔参数组合，同时应用于 Flink 1.19 和 1.20 两个模块。
