# 提交 2552：Flink: Backport Refactor ZkLockFactory to improve code readability and maintainability. (#13906)

## 提交信息

- **序号**：2552 / 4088
- **哈希**：6f0ecfc092380db180dbf92ce8b6fbebe93f00e0
- **短哈希**：6f0ecfc09
- **日期**：2025-08-24 08:58:43 +0200
- **作者**：slfan1989
- **提交说明**：Flink: Backport Refactor ZkLockFactory to improve code readability and maintainability. (#13906)
- **PR/Issue**：#13906（backport #13795）

## 总体目的

该提交是对 PR #13795 的反向移植（backport），对 Flink 维护模块中基于 ZooKeeper 的锁工厂 `ZkLockFactory` 进行重构，以提升代码的可读性和可维护性。`ZkLockFactory` 用于 Flink 表维护（Table Maintenance）任务中的并发控制，通过 ZooKeeper 的 `SharedCount` 实现分布式锁，确保同一个维护任务不会被多个任务实例同时执行。

此次重构主要解决以下问题：
1. **参数校验缺失**：构造函数未对超时时间和重试次数等参数进行非负校验，可能传入非法值导致运行时异常。
2. **重复打开问题**：`open()` 方法没有幂等性保护，重复调用可能导致重复创建 ZooKeeper 客户端和 SharedCount。
3. **资源泄漏风险**：`open()` 方法在初始化失败时未清理已创建的资源（如 client、SharedCount）。
4. **多表锁隔离**：原有代码中 `ZkLock` 内部类未持有锁路径信息，日志无法区分是哪个锁路径的操作，不利于排查多表场景下的问题。
5. **常量作用域**：`LOCKED` 和 `UNLOCKED` 常量原本定义在类级别，但实际只在 `ZkLock` 内部类中使用，作用域过大。
6. **日志不足**：锁的获取和释放过程缺少足够的日志，不利于运维排查。

同时，重构还改进了测试基类，新增多表锁隔离测试，确保不同表的锁互不干扰。

## 如何达成设计目的

- 在构造函数中为所有数值参数添加 `Preconditions.checkArgument` 校验，确保参数合法。
- 引入 `isOpen` 标志位，使 `open()` 方法具备幂等性，避免重复初始化。
- 在 `open()` 失败时调用新增的 `closeQuietly()` 方法清理已创建的资源。
- 将锁路径字符串提取为 `getTaskSharePath()` 和 `getRecoverySharedPath()` 方法，并在 `ZkLock` 中持有 `lockPath`，使日志能输出具体路径。
- 将 `LOCKED`/`UNLOCKED` 常量移至 `ZkLock` 内部类，缩小作用域。
- 增强 `tryLock`、`unlock` 方法的日志输出，区分 debug 和 warn 级别。
- 测试基类 `TestLockFactoryBase` 抽象方法改为接收 `tableName` 参数，新增 `testMultiTableLock` 测试验证多表锁隔离。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (+62/-15)

**修改目的**：重构 ZkLockFactory 提升可读性和可维护性。

**工作逻辑**：
- 构造函数新增 4 个参数校验（sessionTimeoutMs、connectionTimeoutMs、baseSleepTimeMs、maxRetries 均需 >= 0）。
- 新增 `isOpen` 字段，`open()` 方法开头检查是否已打开，已打开则直接返回。
- `open()` 方法中 SharedCount 路径改用 `getTaskSharePath()`/`getRecoverySharedPath()` 方法获取。
- `open()` 失败时调用 `closeQuietly()` 清理资源。
- 新增 `getTaskSharePath()`、`getRecoverySharedPath()`、`closeQuietly()` 私有方法。
- `createLock()`/`createRecoveryLock()` 传入锁路径给 `ZkLock` 构造函数。
- `close()` 方法重置 `isOpen = false`。
- `ZkLock` 内部类新增 `lockPath` 字段和 `LOCKED`/`UNLOCKED` 常量，`tryLock`/`unlock` 方法增加路径相关日志。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestLockFactoryBase.java` (+14/-3)

**修改目的**：改进测试基类支持多表锁测试。

**工作逻辑**：抽象方法 `lockFactory()` 改为 `lockFactory(String tableName)`，`before()` 中传入 "tableName"。新增 `testMultiTableLock` 测试，创建两个不同表名的锁工厂，验证两个锁可以同时获取（互不干扰）。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestJdbcLockFactory.java` (+6/-12)

**修改目的**：适配测试基类签名变更。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestZkLockFactory.java` (+10/-7)

**修改目的**：适配测试基类签名变更并支持表名参数。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (+62/-15)

**修改目的**：同 v1.20，为 Flink 1.19 版本应用相同重构。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestLockFactoryBase.java` (+14/-3)

**修改目的**：同 v1.20 的测试基类改进。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestJdbcLockFactory.java` (+6/-12)

**修改目的**：同 v1.20 的测试适配。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestZkLockFactory.java` (+10/-7)

**修改目的**：同 v1.20 的测试适配。

## 总结

该提交对 Flink 维护模块的 `ZkLockFactory` 进行了全面重构，增加了参数校验、幂等性保护、资源清理、路径隔离日志等改进，并通过多表锁测试验证了正确性。重构同时应用于 Flink 1.19 和 1.20 两个版本。
