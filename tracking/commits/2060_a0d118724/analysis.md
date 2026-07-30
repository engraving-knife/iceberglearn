# 提交 2060：Flink: Backport Add lockFactory open in LockRemover for table maintenance to Flink 1.20 and 1.19 (#12929)

## 提交信息

- **序号**：2060 / 4088
- **哈希**：a0d11872444aaadf4227666f8e7d2541ca5b93ba
- **短哈希**：a0d118724
- **日期**：2025-04-30 12:47:10 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport Add lockFactory open in LockRemover for table maintenance to Flink 1.20 and 1.19 (#12929) — backports #12900
- **PR/Issue**：#12929（backport #12900）

## 总体目的

这是对 PR #12900（即前述 2054 提交）的回溯，把"`LockRemover` 算子 `open()` 中需要在创建锁之前调用 `lockFactory.open()`"这一修复同步到 Flink 1.19 与 Flink 1.20 两个维护分支。Flink 多版本模块各自维护一份 `LockRemover` 与 `TestLockRemover` 副本，主分支修复后需要逐版本回溯，使 1.19/1.20 版本上的 `JdbcLockFactory` 等有状态锁工厂也能正确初始化。

## 如何达成设计目的

与 2054 提交的修复完全一致，仅作用于不同的 Flink 版本目录：
- 在 `LockRemover.open()` 中、`createLock()` 之前插入 `lockFactory.open();`。
- 在 `TestLockRemover.TestingLockFactory` 中加入 `open` 布尔状态守卫，`createLock`/`createRecoveryLock` 在未 open 时抛 `IllegalStateException`，`close()` 重置状态。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemover.java` (修改, +1/-0 lines)

**修改目的**：在 Flink 1.19 版本的 `LockRemover.open()` 中补加 `lockFactory.open()`。

**工作逻辑**：
在指标注册之后、`this.lock = lockFactory.createLock();` 之前插入 `lockFactory.open();`，确保锁工厂先初始化再创建锁。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemover.java` (修改, +13/-2 lines)

**修改目的**：在 Flink 1.19 的 `TestingLockFactory` 中加入 open 状态守卫。

**工作逻辑**：
新增 `open` 字段，`open()` 置 true，`close()` 置 false，`createLock()`/`createRecoveryLock()` 在未 open 时抛 `IllegalStateException`，以守护正确调用顺序。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemover.java` (修改, +1/-0 lines)

**修改目的**：在 Flink 1.20 版本的 `LockRemover.open()` 中补加 `lockFactory.open()`。

**工作逻辑**：
同 Flink 1.19，插入 `lockFactory.open();`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemover.java` (修改, +13/-2 lines)

**修改目的**：在 Flink 1.20 的 `TestingLockFactory` 中加入 open 状态守卫。

**工作逻辑**：
同 Flink 1.19，加入 open 字段与守卫。

## 总结

本提交将 #12900 的修复回溯到 Flink 1.19 与 1.20 两个维护分支，使 `LockRemover` 算子在创建锁之前先调用 `lockFactory.open()`，避免有状态锁工厂（如 `JdbcLockFactory`）未初始化就被使用。两个版本的修改与主分支完全一致。
