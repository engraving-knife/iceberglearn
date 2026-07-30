# 提交 1999：Flink: backport fix TriggerManager to unlock task execution when previous job left an orphaned lock for Flink 1.19

## 提交信息

- **序号**：1999 / 4088
- **哈希**：d7455390ea8d0f51d0604879391a76e540b264b7
- **短哈希**：d7455390e
- **日期**：2025-04-15 14:58:05 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Flink: backport fix TriggerManager to unlock task execution when previous job left an orphaned lock for Flink 1.19 (#12801)
- **PR/Issue**：#12801（backport #12794）

## 总体目的

本提交是将 PR #12794（提交 1998）的修改反向移植（backport）到 Flink 1.19 模块。修改内容与提交 1998 完全一致，仅作用目录不同：提交 1998 修改的是 `flink/v1.20/`，而本提交修改的是 `flink/v1.19/`。

该修复解决了前一个 Flink 作业异常终止后遗留孤立锁（orphaned lock）导致新作业无法执行维护任务的问题。

## 如何达成设计目的

将相同的 `TriggerManager.initialize()` 修复应用到 Flink 1.19 模块：
1. 在非状态恢复（全新启动）场景下主动释放维护锁和恢复锁
2. 添加对应的测试用例验证此行为

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java` (修改, +3/-0 lines)

**修改目的**：在新作业启动时释放可能残留的孤立锁。

**工作逻辑**：与提交 1998 完全一致。在 `initialize()` 方法的 `context.isRestored()` 判断中添加 `else` 分支，调用 `lock.unlock()` 和 `recoveryLock.unlock()`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManager.java` (修改, +18/-0 lines)

**修改目的**：添加测试验证新作业释放孤立锁的行为。

**工作逻辑**：与提交 1998 完全一致。新增 `testNewJobReleasesExistingLock()` 测试方法，模拟前一个作业遗留的孤立锁，验证新作业启动后锁被正确释放。

## 总结

本提交是提交 1998 的 Flink 1.19 反向移植，将相同的孤立锁释放修复应用到 `flink/v1.19/` 模块，确保所有支持的 Flink 版本都修复了前一个作业遗留孤立锁导致新作业无法执行维护任务的问题。
