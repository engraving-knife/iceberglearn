# 提交 2158：Flink: Remove the MiniClusterWithClientResource dependency (#13021)

## 提交信息

- **序号**：2158 / 4088
- **哈希**：d32ba358c0976a58229ca62dc175e15b16fda619
- **短哈希**：d32ba358c
- **日期**：2025-05-23 17:58:27 +0900
- **作者**：JeonDaehong
- **提交说明**：Flink: Remove the MiniClusterWithClientResource dependency (#13021)
- **PR/Issue**：#13021

## 总体目的

`MiniClusterWithClientResource` 是 Flink 旧版（基于 JUnit 4）的测试工具类，用于在测试中启动和管理 MiniCluster。随着 Flink 迁移到 JUnit 5，该类已被标记为废弃，且可能在未来的 Flink 版本中被移除。Iceberg 的 Flink v2.0 集成测试 `TestIcebergSourceFailover` 仍在使用该类来为每个故障恢复测试创建和管理独立的 MiniCluster。该提交移除了对 `MiniClusterWithClientResource` 的依赖，改用 Flink JUnit 5 扩展提供的 `@InjectMiniCluster` 注解方式来注入和管理 MiniCluster，使测试代码与 Flink 的新版测试基础设施保持一致，提升向前兼容性。

## 如何达成设计目的

- 移除 `MiniClusterWithClientResource` 和 `ThrowingConsumer` 的导入，替换为 `InjectMiniCluster` 和 `MiniCluster` 相关导入。
- 新增 `@BeforeEach` 的 `startMiniCluster` 方法和 `@AfterEach` 的 `stopMiniCluster` 方法，通过 `@InjectMiniCluster` 注入 MiniCluster 实例，在每个测试前后启动和关闭集群。
- 将四个故障恢复测试方法（`testBoundedWithTaskManagerFailover` 等）的签名修改为接收 `@InjectMiniCluster MiniCluster` 参数，直接使用注入的集群，而非通过 `runTestWithNewMiniCluster` 包装方法。
- 删除 `runTestWithNewMiniCluster` 工具方法，该方法原来负责手动创建、启动和销毁 `MiniClusterWithClientResource`。

## 修改详情

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java` (修改, +26/-28 lines)

**修改目的**：移除对废弃的 `MiniClusterWithClientResource` 的依赖，改用 JUnit 5 的 `@InjectMiniCluster` 注解方式。

**工作逻辑**：
- 导入变更：移除 `MiniClusterWithClientResource`、`ThrowingConsumer`，新增 `InjectMiniCluster`、`AfterEach`。
- 新增 `startMiniCluster(@InjectMiniCluster MiniCluster)` 方法（`@BeforeEach`），若集群未运行则启动它。
- 新增 `stopMiniCluster(@InjectMiniCluster MiniCluster)` 方法（`@AfterEach`），关闭集群。
- 四个测试方法（`testBoundedWithTaskManagerFailover`、`testBoundedWithJobManagerFailover`、`testContinuousWithTaskManagerFailover`、`testContinuousWithJobManagerFailover`）改为直接接收 `@InjectMiniCluster MiniCluster` 参数，并直接调用 `testBoundedIcebergSource`/`testContinuousIcebergSource` 方法。
- 删除 `runTestWithNewMiniCluster` 方法，该方法原负责通过 `MiniClusterWithClientResource` 手动管理集群生命周期。

## 总结

该提交将 Flink v2.0 的故障恢复测试从废弃的 `MiniClusterWithClientResource` 迁移到 JUnit 5 的 `@InjectMiniCluster` 注解方式，是测试基础设施现代化的必要步骤，确保 Iceberg 的 Flink 集成测试能与 Flink 的演进保持兼容。
