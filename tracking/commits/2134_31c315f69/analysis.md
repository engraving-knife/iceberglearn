# 提交 2134：Flink: Support zookeeper lock in TableMaintenance

## 提交信息

- **序号**：2134 / 4088
- **哈希**：31c315f695aad544a096a5a2ffdde54a97b90b28
- **短哈希**：31c315f69
- **日期**：2025-05-15 16:20:04 +0800
- **作者**：GuoYu
- **提交说明**：Flink: Support zookeeper lock in TableMaintenance (#12810)
- **PR/Issue**：#12810

## 总体目的

这个提交为 Flink 的表维护（TableMaintenance）功能新增了基于 Zookeeper 的分布式锁支持。在 Flink 的维护任务执行过程中，需要一种分布式锁机制来防止多个任务实例同时执行相同的维护操作（如数据文件压缩、快照清理等），避免重复操作和潜在的数据一致性问题。此前 Iceberg 已经提供了基于内存的锁工厂实现，但在分布式部署场景下，内存锁无法跨 JobManager/TaskManager 实例协调。这个提交通过引入基于 Zookeeper 的 ZkLockFactory，使得维护任务能够在集群环境下安全地协调执行。

## 如何达成设计目的

1. 新增 ZkLockFactory 类，实现 TriggerLockFactory 接口，利用 Flink 内置的 shaded Curator 5 客户端与 Zookeeper 交互。
2. 使用 Curator 的 SharedCount 机制实现分布式锁，通过乐观锁（版本化值）的方式尝试获取锁。
3. 提供两种锁：任务锁（task lock）和恢复锁（recovery lock），分别对应正常任务执行和故障恢复场景。
4. 通过配置参数（连接字符串、会话超时、连接超时、重试策略等）使锁工厂可灵活配置。
5. 更新 checkstyle 抑制规则，允许 ZkLockFactory 使用 Flink 的 shaded Curator 依赖（项目中通常禁止使用 shaded 类）。
6. 新增 TestZkLockFactory 测试类，验证锁工厂的基本功能。

## 修改详情

### `.baseline/checkstyle/checkstyle-suppressions.xml` (修改, +3/-0 lines)

**修改目的**：允许 ZkLockFactory 类使用 Flink 的 shaded Curator 依赖。

**工作逻辑**：添加了一条 suppress 规则，匹配 org.apache.iceberg.flink.maintenance.api.ZkLockFactory 类，抑制 id 为 BanShadedClasses 的 checkstyle 检查。这是因为 ZkLockFactory 需要使用 Flink 内置的 org.apache.flink.shaded.curator5 包中的类，而项目默认禁止使用 shaded 类。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (新增, +178 lines)

**修改目的**：实现基于 Zookeeper 的分布式锁工厂，为 Flink 维护任务提供分布式锁支持。

**工作逻辑**：
- ZkLockFactory 实现了 TriggerLockFactory 接口，包含连接字符串、锁 ID、会话超时、连接超时、重试参数等配置。
- `open()` 方法初始化 CuratorFramework 客户端并创建两个 SharedCount 实例（taskSharedCount 和 recoverySharedCount），分别用于任务锁和恢复锁，锁路径基于 `/iceberg/flink/maintenance/locks/{lockId}/task` 和 `/recovery`。
- `createLock()` 和 `createRecoveryLock()` 方法分别返回基于对应 SharedCount 的 ZkLock 实例。
- 内部 ZkLock 类实现 Lock 接口，使用 SharedCount 的版本化值（VersionedValue）实现乐观锁：`tryLock()` 先检查当前值是否为 LOCKED(1)，如果不是则通过 `trySetCount(versionedValue, LOCKED)` 尝试原子性地设置锁状态；`isHeld()` 检查当前值是否为 LOCKED；`unlock()` 通过 `setCount(UNLOCKED)` 释放锁。
- `close()` 方法依次关闭 SharedCount 和 CuratorFramework 客户端。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestZkLockFactory.java` (新增, +55 lines)

**修改目的**：为 ZkLockFactory 提供单元测试，验证锁的基本功能。

**工作逻辑**：测试类验证 ZkLockFactory 的锁获取、持有检查、释放等功能，确保分布式锁在各种场景下正确工作。

## 总结

这个提交为 Flink 表维护功能增加了基于 Zookeeper 的分布式锁支持，填补了分布式部署场景下锁协调能力的空白。通过利用 Flink 内置的 Curator shaded 客户端，避免了额外的依赖引入，设计上使用了 SharedCount 的乐观锁机制，适合维护任务的低频锁竞争场景。后续该功能被 backport 到 Flink 1.19 和 1.20 版本。
