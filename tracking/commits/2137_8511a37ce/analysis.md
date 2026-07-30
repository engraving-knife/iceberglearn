# 提交 2137：Flink: Backport support zookeeper lock in TableMaintenance

## 提交信息

- **序号**：2137 / 4088
- **哈希**：8511a37ce4a7075592da37575f4239d34c29101a
- **短哈希**：8511a37ce
- **日期**：2025-05-17 02:44:23 +0800
- **作者**：GuoYu
- **提交说明**：Flink: Backport support zookeeper lock in TableMaintenance (#13063)
- **PR/Issue**：#13063（backport #12810）

## 总体目的

这个提交是将 #12810（提交 2134）中新增的 Zookeeper 分布式锁支持 backport 到 Flink 1.19 和 1.20 版本。原始的 ZkLockFactory 功能最初只在 Flink 2.0 中实现，但 Flink 1.19 和 1.20 作为仍然广泛使用的版本，同样需要分布式锁支持来保证表维护任务在集群环境下的安全执行。这个 backport 确保了旧版本 Flink 用户也能使用基于 Zookeeper 的分布式锁。

## 如何达成设计目的

1. 将 Flink 2.0 中的 ZkLockFactory.java 和 TestZkLockFactory.java 复制到 Flink 1.19 和 1.20 对应的目录中。
2. 代码内容与原始实现完全一致，利用 Flink 的 shaded Curator 5 客户端实现分布式锁。
3. 由于是 backport，不包含 checkstyle 抑制规则的修改（该修改已在 #12810 中应用于 main 分支的 checkstyle-suppressions.xml）。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (新增, +178 lines)

**修改目的**：为 Flink 1.19 提供 Zookeeper 分布式锁工厂实现。

**工作逻辑**：与提交 2134 中 Flink 2.0 的 ZkLockFactory 完全相同。实现 TriggerLockFactory 接口，使用 Curator 的 SharedCount 机制实现乐观锁，提供任务锁和恢复锁两种锁类型。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestZkLockFactory.java` (新增, +55 lines)

**修改目的**：为 Flink 1.19 的 ZkLockFactory 提供测试。

**工作逻辑**：与 Flink 2.0 版本的测试类相同。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ZkLockFactory.java` (新增, +178 lines)

**修改目的**：为 Flink 1.20 提供 Zookeeper 分布式锁工厂实现。

**工作逻辑**：与 Flink 1.19 版本完全相同。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestZkLockFactory.java` (新增, +55 lines)

**修改目的**：为 Flink 1.20 的 ZkLockFactory 提供测试。

**工作逻辑**：与 Flink 1.19 版本的测试类相同。

## 总结

这个提交是 #12810 的 backport，将 Zookeeper 分布式锁支持扩展到 Flink 1.19 和 1.20 版本，确保了旧版本 Flink 用户也能在分布式部署场景下安全地运行表维护任务。代码与原始实现完全一致。
