# 提交 2173：Flink: Backport remove the MiniClusterWithClientResource dependency (#13165)

## 提交信息

- **序号**：2173 / 4088
- **哈希**：c3eaaf5c885039f0d6fc02c4548861a8dba7182a
- **短哈希**：c3eaaf5c8
- **日期**：2025-05-28 21:37:13 +0900
- **作者**：JeonDaehong
- **提交说明**：Flink: Backport remove the MiniClusterWithClientResource dependency (#13165)
- **PR/Issue**：#13165（backport #13021）

## 总体目的

此提交是 PR #13021 的反向移植（backport），目的是移除 Flink 测试中对 `MiniClusterWithClientResource` 的依赖。`MiniClusterWithClientResource` 是 Flink 旧版的测试工具类，在较新的 Flink 版本中已被弃用或移除。通过迁移到使用 `@InjectMiniCluster` 注解的方式注入 MiniCluster，测试代码能更好地与 Flink 5.x 的 JUnit 5 扩展机制集成。这降低了测试对已弃用 API 的依赖，提高了代码的可维护性和向前兼容性。

## 如何达成设计目的

- 用 `@InjectMiniCluster` 注解和 `MiniCluster` 参数替换原来手动创建和管理 `MiniClusterWithClientResource` 的方式
- 添加 `@BeforeEach` 和 `@AfterEach` 方法来管理 MiniCluster 的生命周期（启动和关闭）
- 将原来通过 `runTestWithNewMiniCluster` 包装方法调用的测试方法，改为直接接收 `MiniCluster` 参数
- 删除不再需要的 `runTestWithNewMiniCluster` 工具方法和相关 import
- 修改覆盖了 Flink v1.19 和 v1.20 两个版本

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java` (修改, +26/-28 lines)

**修改目的**：移除 Flink 1.19 测试中对 `MiniClusterWithClientResource` 的依赖。

**工作逻辑**：
- 移除 `MiniClusterWithClientResource` 和 `ThrowingConsumer` 的 import，添加 `InjectMiniCluster` 和 `AfterEach` 的 import
- 添加 `startMiniCluster` 和 `stopMiniCluster` 方法，使用 `@InjectMiniCluster` 注解注入 MiniCluster，在 `@BeforeEach` 中启动、`@AfterEach` 中关闭
- 四个测试方法（`testBoundedWithTaskManagerFailover`、`testBoundedWithJobManagerFailover`、`testContinuousWithTaskManagerFailover`、`testContinuousWithJobManagerFailover`）改为直接接收 `@InjectMiniCluster MiniCluster` 参数，不再通过包装方法调用
- 删除 `runTestWithNewMiniCluster` 私有工具方法，该方法原来负责创建、启动、调用和清理 `MiniClusterWithClientResource`

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java` (修改, +26/-28 lines)

**修改目的**：移除 Flink 1.20 测试中对 `MiniClusterWithClientResource` 的依赖。

**工作逻辑**：与 v1.19 的修改完全相同，应用相同的迁移模式。

## 总结

此提交将 Flink 1.19 和 1.20 的 IcebergSource 故障转移测试从使用已弃用的 `MiniClusterWithClientResource` 迁移到使用 JUnit 5 的 `@InjectMiniCluster` 注解机制。这不仅移除了对弃用 API 的依赖，还简化了测试代码结构，使 MiniCluster 的生命周期管理更加清晰。
