# 提交 2054：Flink: Add lockFactory open in LockRemover for table maintenance (#12900)

## 提交信息

- **序号**：2054 / 4088
- **哈希**：e1ab42591488f25471a850547da9952aa9de48c2
- **短哈希**：e1ab42591
- **日期**：2025-04-29 12:03:46 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Add lockFactory open in LockRemover for table maintenance (#12900)
- **PR/Issue**：#12900

## 总体目的

Flink v2.0 表维护算子 `LockRemover` 在 `open()` 生命周期中创建锁（`createLock()` / `createRecoveryLock()`），但此前未调用 `lockFactory.open()` 来初始化锁工厂本身。对于像 `JdbcLockFactory` 这类有状态、需要在 `open()` 中建立连接或初始化资源的实现，这会导致锁工厂尚未初始化就被用于创建锁，从而抛出异常或行为不正确。

本提交在 `LockRemover.open()` 中、创建具体锁之前补上 `lockFactory.open()` 调用，使锁工厂的初始化生命周期与算子初始化对齐，确保 `createLock()` / `createRecoveryLock()` 调用时工厂已经处于就绪状态。

## 如何达成设计目的

修改非常聚焦：在 `LockRemover` 的 `open()` 方法中，紧接在指标注册之后、`createLock()` 之前，插入一行 `lockFactory.open();`。这样保证锁工厂先完成自身初始化，再创建锁实例。

测试侧增强 `TestingLockFactory`：引入 `open` 布尔状态，`open()` 将其置 true，`close()` 置 false，`createLock()` / `createRecoveryLock()` 在未 open 时抛 `IllegalStateException`。这使测试能够捕获"未 open 就 createLock"的回归。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemover.java` (修改, +1/-0 lines)

**修改目的**：在算子初始化时初始化锁工厂。

**工作逻辑**：
`LockRemover.open()` 中新增 `lockFactory.open();`，位于指标 gauge 注册之后、`this.lock = lockFactory.createLock();` 之前。这样 `createLock` 与 `createRecoveryLock` 调用时工厂已完成 `open` 初始化。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemover.java` (修改, +13/-2 lines)

**修改目的**：增强 `TestingLockFactory` 以验证 open 生命周期。

**工作逻辑**：
- 新增私有字段 `private boolean open = false;`
- `open()` 由空实现改为 `open = true;`
- `createLock()` 与 `createRecoveryLock()` 入口处增加 `if (!open) throw new IllegalStateException("Lock factory not open");` 守卫
- `close()` 由空实现改为 `open = false;`

这样若 `LockRemover` 未调用 `lockFactory.open()` 直接 create lock，测试会因 `IllegalStateException` 失败，从而守护正确调用顺序。

## 总结

本提交修复了 `LockRemover` 算子未调用 `lockFactory.open()` 的遗漏，在有状态的 `JdbcLockFactory` 等实现下会引发运行时错误。改动一行生产代码 + 测试侧增强守护，使锁工厂生命周期与算子初始化对齐。
