# 提交 1995：Flink: Move unlock from MemoryLock open to TestCase Before

## 提交信息

- **序号**：1995 / 4088
- **哈希**：6d3b38bc66e71be9924dc56c919a26a1d5de4f70
- **短哈希**：6d3b38bc6
- **日期**：2025-04-15 09:06:37 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Move unlock from MemoryLock open to TestCase Before (#12793)
- **PR/Issue**：#12793

## 总体目的

本提交修复了 Flink 维护（maintenance）模块测试中锁清理逻辑的位置问题。此前的实现中，`MemoryLockFactory` 的 `open()` 方法会在每次调用时自动解锁维护锁（MAINTENANCE_LOCK）和恢复锁（RECOVERY_LOCK），但这种做法存在时序问题：`open()` 在测试基类的 `@BeforeEach` 中被调用，但此时锁的状态可能尚未被前一个测试设置，或者解锁时机不正确导致测试间状态污染。

正确做法是将解锁操作从 `MemoryLockFactory.open()` 中移除，改为在 `@BeforeEach` 方法中显式创建锁实例并解锁。这样确保在每个测试开始前，所有锁都处于释放状态，且解锁操作与测试执行时序严格对齐。同时修复了 `TestTriggerManager` 子类未调用 `super.before()` 的问题，导致父类的初始化逻辑（包括锁清理）被跳过。

## 如何达成设计目的

1. **将解锁逻辑从工厂的 `open()` 移到测试基类的 `@BeforeEach`**：在 `OperatorTestBase.before()` 中，调用 `LOCK_FACTORY.open()` 后，显式创建锁并调用 `unlock()` 来清理可能残留的锁状态。

2. **清空 `MemoryLockFactory.open()` 方法**：将原 `open()` 中的解锁逻辑移除，改为 `// do nothing`，避免在工厂初始化时产生副作用。

3. **修复子类未调用父类 `before()` 的问题**：在 `TestTriggerManager.before()` 中添加 `super.before()` 调用，确保父类的锁清理逻辑被执行。

## 修改详情

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (修改, +5/-2 lines)

**修改目的**：将锁清理逻辑从 MemoryLockFactory.open() 移到测试基类的 @BeforeEach 方法。

**工作逻辑**：
在 `@BeforeEach void before()` 方法中，`LOCK_FACTORY.open()` 之后新增两行：
```java
LOCK_FACTORY.createLock().unlock();
LOCK_FACTORY.createRecoveryLock().unlock();
```
通过创建新的锁实例并立即解锁，确保内存中所有同名的锁都被释放。

同时将 `MemoryLockFactory` 内部类的 `open()` 方法从：
```java
public void open() {
    MAINTENANCE_LOCK.unlock();
    RECOVERY_LOCK.unlock();
}
```
改为：
```java
public void open() {
    // do nothing
}
```
避免工厂初始化时产生解锁副作用。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTriggerManager.java` (修改, +1/-0 lines)

**修改目的**：修复子类 @BeforeEach 未调用父类初始化方法的问题。

**工作逻辑**：
在 `TestTriggerManager.before()` 方法的第一行添加 `super.before();`，确保父类 `OperatorTestBase.before()` 中的锁清理逻辑被正确执行。此前子类覆盖了 `before()` 但未调用 `super.before()`，导致父类的 `LOCK_FACTORY.open()` 和新增的解锁逻辑被跳过。

## 总结

本提交修复了 Flink 维护模块测试中锁清理逻辑的位置问题，将解锁操作从 `MemoryLockFactory.open()` 移至测试基类的 `@BeforeEach` 方法中，并修复了子类未调用 `super.before()` 的缺陷，确保每个测试开始前锁状态被正确清理，避免测试间的状态污染。
