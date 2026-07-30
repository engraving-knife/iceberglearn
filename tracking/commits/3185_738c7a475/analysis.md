# 提交 3185：AWS: Set retry policy on glue and dynamo clients (#15094)

## 提交信息

- **序号**：3185 / 4088
- **哈希**：738c7a4753552ff990401a5f736fa29ad26d5c60
- **短哈希**：738c7a475
- **日期**：2026-01-31
- **作者**：Edward Gao
- **提交说明**：AWS: Set retry policy on glue and dynamo clients (#15094)
- **PR/Issue**：#15094

## 总体目的

Iceberg AWS 模块中的 `AwsClientProperties.applyRetryConfigurations` 方法会将 AWS SDK 客户端的重试策略设置为 `RetryMode.ADAPTIVE_V2`。该自适应重试策略（`DefaultAdaptiveRetryStrategy`）根据客户端的重试预算和限流反馈动态调整重试行为，相比 AWS SDK 默认的传统重试策略，在面对限流（throttling）和瞬时故障时更具弹性——它会采用指数退避并动态调整重试速率，避免在服务端过载时雪崩式重试。

该方法此前已通过 `applyMutation` 链应用到 S3 和 KMS 客户端（在 `AwsClientFactories.DefaultAwsClientFactory` 的 `s3()` 和 `kms()` 方法中），但 **Glue 和 DynamoDB 客户端遗漏了这一配置**。这意味着：

1. **Glue 客户端**（用于 Glue Catalog 元数据操作，如获取表、更新表等）在遇到限流或瞬时错误时使用 SDK 默认重试策略，弹性不足。
2. **DynamoDB 客户端**（用于 DynamoDB 目录锁和 committing 表操作）同样缺乏自适应重试，在 DynamoDB 限流时可能导致锁获取失败或提交中断。

这两个客户端恰恰是 Iceberg 元数据管理的关键路径：Glue Catalog 的所有表元数据读写、DynamoDB 的锁管理都依赖它们的可靠性。缺少自适应重试可能导致在高并发或服务端限流场景下操作失败率上升。

本提交在两个客户端工厂类（`AwsClientFactories` 默认工厂和 `AssumeRoleAwsClientFactory` 跨账号角色工厂）的 `glue()` 和 `dynamo()` 方法中补充 `.applyMutation(awsClientProperties::applyRetryConfigurations)`，使所有 AWS 客户端的重试策略保持一致。

## 如何达成设计目的

改动涉及两个工厂类和测试文件。在 `AwsClientFactories.java` 的 `DefaultAwsClientFactory` 中，为 `glue()` 和 `dynamo()` 方法的 builder 链追加 `.applyMutation(awsClientProperties::applyRetryConfigurations)`；在 `AssumeRoleAwsClientFactory.java` 中，为对应的 `glue()` 和 `dynamo()` 方法做相同追加。`applyRetryConfigurations` 方法获取或创建 `ClientOverrideConfiguration`，设置 `RetryMode.ADAPTIVE_V2`，再通过 `overrideConfiguration` 应用到 builder。测试通过反射式检查验证 Glue、KMS、DynamoDB 三个客户端均使用了 `DefaultAdaptiveRetryStrategy`。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AssumeRoleAwsClientFactory.java` (+2/-0 lines)

**修改目的**：为 AssumeRole 工厂的 Glue 和 DynamoDB 客户端补充自适应重试策略。

**工作逻辑**：
在 `glue()` 方法中，`GlueClient.builder()` 的 `applyMutation` 链（已有 `applyAssumeRoleConfigurations` 和 `applyHttpClientConfigurations`）之后追加 `.applyMutation(awsClientProperties::applyRetryConfigurations)`。

在 `dynamo()` 方法中，`DynamoDbClient.builder()` 的 `applyMutation` 链（已有 `applyAssumeRoleConfigurations`、`applyHttpClientConfigurations`、`applyDynamoDbEndpointConfigurations`）之后追加 `.applyMutation(awsClientProperties::applyRetryConfigurations)`。

`applyRetryConfigurations` 的内部逻辑：获取 builder 现有的 `overrideConfiguration`（若有则转 builder，否则新建），设置 `retryStrategy(RetryMode.ADAPTIVE_V2)`，再写回 builder。`ADAPTIVE_V2` 对应 SDK 的 `DefaultAdaptiveRetryStrategy` 实现。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java` (+2/-0 lines)

**修改目的**：为默认工厂的 Glue 和 DynamoDB 客户端补充自适应重试策略。

**工作逻辑**：
在 `DefaultAwsClientFactory.glue()` 方法中，builder 链（已有 `applyClientRegionConfiguration`、`applyHttpClientConfigurations`、`applyGlueEndpointConfigurations`、`applyClientCredentialConfigurations`）之后追加 `.applyMutation(awsClientProperties::applyRetryConfigurations)`。

在 `DefaultAwsClientFactory.dynamo()` 方法中，builder 链（已有 `applyClientRegionConfiguration`、`applyHttpClientConfigurations`、`applyClientCredentialConfigurations`、`applyDynamoDbEndpointConfigurations`）之后追加 `.applyMutation(awsClientProperties::applyRetryConfigurations)`。

与 `AssumeRoleAwsClientFactory` 的改动对称，确保无论用户使用默认工厂还是 AssumeRole 工厂，Glue 和 DynamoDB 客户端均获得一致的重试策略。

### `aws/src/test/java/org/apache/iceberg/aws/TestAwsClientFactories.java` (+40/-0 lines)

**修改目的**：验证 Glue、KMS、DynamoDB 客户端均设置了自适应重试策略。

**工作逻辑**：
新增三个测试方法和一个辅助断言方法：

1. `testGlueClientSetsAdaptiveRetryPolicy`：通过 `DummyValidProvider` 获取工厂，调用 `factory.glue()` 获取 GlueClient，断言其重试策略。
2. `testKmsClientSetsAdaptiveRetryPolicy`：同上，获取 KmsClient 并断言（KMS 此前已配置重试，此测试补全覆盖）。
3. `testDynamoClientSetsAdaptiveRetryPolicy`：同上，获取 DynamoDbClient 并断言。

辅助方法 `assertAwsClientSetsAdaptiveRetryPolicy(AwsClient client)`：
```java
Optional<RetryStrategy> retryStrategy =
    client.serviceClientConfiguration().overrideConfiguration().retryStrategy();
assertThat(retryStrategy).isPresent();
assertThat(retryStrategy.get()).isInstanceOf(DefaultAdaptiveRetryStrategy.class);
```
通过 `serviceClientConfiguration().overrideConfiguration().retryStrategy()` 从客户端配置中提取重试策略，断言其存在且类型为 `DefaultAdaptiveRetryStrategy`（即 `ADAPTIVE_V2` 模式对应的实现类）。新增导入 `Optional`、`AwsClient`、`RetryStrategy`、`DefaultAdaptiveRetryStrategy`。

## 总结

本提交修复了 Glue 和 DynamoDB 客户端缺少自适应重试策略的遗漏，在默认工厂和 AssumeRole 工厂中统一补充 `applyRetryConfigurations` 调用，使所有 AWS 客户端（S3、KMS、Glue、DynamoDB）均使用 `ADAPTIVE_V2` 重试模式。这提升了 Glue Catalog 元数据操作和 DynamoDB 锁管理在面对限流与瞬时故障时的弹性，测试通过检查 `DefaultAdaptiveRetryStrategy` 实例类型验证配置生效。改动精简但影响关键路径可靠性。
