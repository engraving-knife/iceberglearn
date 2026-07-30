# 提交 2547：Flink: Refactor ZkLockFactory to improve code readability and maintainability. (#13795)

## 提交信息

- **序号**：2547 / 4088
- **哈希**：86665c9b868bae8953ff819b183735a68e6f39cc
- **短哈希**：86665c9b8
- **日期**：2025-08-22 15:59:29 +0200
- **作者**：slfan1989
- **提交说明**：Flink: Refactor ZkLockFactory to improve code readability and maintainability. (#13795)
- **PR/Issue**：#13795

## 总体目的

`ZkLockFactory` 是 Flink maintenance 框架中基于 ZooKeeper 的锁工厂，用 `SharedCount` 实现 task 锁与 recovery 锁。原实现存在几个可读性与健壮性问题：
1. `LOCKED`/`UNLOCKED` 常量定义在外层类，但只有内部 `ZkLock` 使用，作用域过宽。
2. `open()` 没有幂等保护，重复调用会重新创建 Curator client 与 SharedCount，可能导致资源泄漏或状态混乱。
3. 构造参数没有校验，负数超时/重试参数会延后到运行时才暴露问题。
4. `open()` 失败时未清理已创建的 client，资源泄漏。
5. 锁路径拼接散落在多处（`LOCK_BASE_PATH + lockId + "/task"`），重复且易错。
6. 日志缺乏锁路径上下文，排查困难；`tryLock` 失败用 `debug` 而异常用 `debug`，级别不合理。
7. 测试中多表锁场景只在 `TestJdbcLockFactory` 中存在，`ZkLockFactory` 没有覆盖，且测试基类签名不支持传入表名。

本提交重构 `ZkLockFactory` 解决上述问题，并把多表锁测试上提到基类 `TestLockFactoryBase`，让 JDBC 与 Zk 两种锁工厂都覆盖该场景。

## 如何达成设计目的

**主代码 `ZkLockFactory`**：
- 新增 `isOpen` 字段，`open()` 幂等：已打开则 debug 日志后直接返回。
- 构造函数增加 `Preconditions.checkArgument` 校验 `sessionTimeoutMs`/`connectionTimeoutMs`/`baseSleepTimeMs`/`maxRetries` 非负。
- 抽取 `getTaskSharePath()` 与 `getRecoverySharedPath()` 方法集中拼接锁路径，避免重复。
- `open()` 失败时调用新增的 `closeQuietly()` 清理已创建资源（client、sharedCount），避免泄漏。
- `close()` 成功后置 `isOpen=false`。
- `LOCKED`/`UNLOCKED` 常量移入内部类 `ZkLock`，缩小作用域。
- `ZkLock` 新增 `lockPath` 字段，构造时传入路径，日志中带上路径上下文。
- `tryLock` 成功/失败都打 debug 日志（带路径）；异常改为 `warn` 级别。
- `unlock` 成功打 debug、失败打 warn 并抛异常。
- `open()` 成功打 info 日志。

**测试**：
- `TestLockFactoryBase` 把抽象方法签名改为 `lockFactory(String tableName)`，`before` 用 `"tableName"`，并新增 `testMultiTableLock` 测试：创建第二个锁工厂（不同表名），验证两个工厂的锁互不影响（都能 `tryLock` 成功），并正确 `unlock`/`close`。
- `TestJdbcLockFactory`：移除自身的 `testMultiTableLock`（上移到基类），`lockFactory` 改为接收 `tableName` 参数并提升为包级可见。
- `TestZkLockFactory`：`lockFactory` 改为接收 `tableName`；`@After` 改为 JUnit 5 的 `@AfterEach`；移除硬编码的 `testLockId` 字段。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (+55/-12)

**修改目的**：重构 ZkLockFactory 提升可读性与健壮性。

**工作逻辑**：
- 构造函数新增 4 个参数校验。
- `open()` 幂等化（`isOpen` 检查），失败时 `closeQuietly()` 清理。
- 抽取 `getTaskSharePath()`/`getRecoverySharedPath()`/`closeQuietly()` 方法。
- `LOCKED`/`UNLOCKED` 移入 `ZkLock` 内部类。
- `ZkLock` 新增 `lockPath` 字段，日志带路径；`tryLock`/`unlock` 日志级别与上下文优化。
- `close()` 置 `isOpen=false`。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestLockFactoryBase.java` (+14/-1)

**修改目的**：抽象签名改为带 tableName，新增多表锁测试。

**工作逻辑**：`lockFactory()` → `lockFactory(String tableName)`；`before` 用 `"tableName"`；新增 `testMultiTableLock` 验证两个不同表名锁工厂互不影响。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestJdbcLockFactory.java` (+2/-16)

**修改目的**：移除上移的多表锁测试，签名对齐。

**工作逻辑**：删除自身的 `testMultiTableLock` 与 `lockFactory()` 无参版本，`lockFactory(String tableName)` 改为包级可见；移除多余 import。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestZkLockFactory.java` (+10/-7)

**修改目的**：签名对齐并迁移到 JUnit 5。

**工作逻辑**：`lockFactory(String tableName)` 用参数构造工厂；`@After` 改为 `@AfterEach`；移除 `testLockId` 字段。

## 总结

重构 `ZkLockFactory`：增加构造参数校验、`open()` 幂等与失败清理、抽取路径方法与 `closeQuietly`、缩小常量作用域、为 `ZkLock` 增加 `lockPath` 并优化日志级别与上下文。测试侧把多表锁场景上移到 `TestLockFactoryBase` 让 JDBC 与 Zk 都覆盖，`TestZkLockFactory` 迁移到 JUnit 5 `@AfterEach`。整体提升可读性、健壮性与测试覆盖一致性。
