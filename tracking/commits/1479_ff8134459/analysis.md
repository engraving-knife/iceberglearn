# 提交 1479：AWS: Enable RetryMode for AWS KMS client (#11420)

## 提交信息

- **序号**：1479 / 4088
- **哈希**：ff813445916bfd6ec1cc30a02b02f8bade7a26f6
- **短哈希**：ff8134459
- **日期**：2024-12-11（Wed Dec 11 09:20:12 2024 +0800）
- **作者**：hsiang-c <137842490+hsiang-c@users.noreply.github.com>
- **提交说明**：AWS: Enable RetryMode for AWS KMS client (#11420)
- **PR/Issue**：#11420

## 总体目的

Iceberg 的 `aws` 模块支持通过 AWS KMS（Key Management Service）做 envelope encryption——在写入数据文件时用数据密钥（DEK）加密文件内容，DEK 本身再用 KMS 上的主密钥（KEK）加密后存储在文件元数据中。读取时则反向调用 KMS `Decrypt` 解出 DEK。

KMS 客户端在调用 AWS 时可能遇到限流（ThrottlingException，HTTP 429）、暂时性 5xx 错误、网络抖动等情况。AWS SDK v2 默认的 retry 行为相对保守（默认 RetryMode 是 `LEGACY`，最多 3 次），在 KMS 高 QPS 场景下不一定能充分自愈。

AWS SDK v2 提供了 [`RetryMode`](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/core/retry/RetryMode.html) 这一更先进的重试策略，其中 `ADAPTIVE_V2` 模式会根据客户端侧的失败情况动态调整重试节奏，对限流场景有更好的容错能力（带退避和 token bucket）。

本提交的目的：把 Iceberg 创建的所有 AWS KMS client（包括 `AwsClientFactories` 默认工厂、`AssumeRoleAwsClientFactory`、`LakeFormationAwsClientFactory`）的 retry 模式统一设置为 `ADAPTIVE_V2`，提升 KMS 调用的健壮性，减少因瞬时错误导致的写入/读取失败。

实现方式是在已有的 `AwsClientProperties`（一个统一封装 AWS client 配置的 properties 类）上新增一个 `applyRetryConfigurations` 方法，让所有需要 KMS client 的工厂复用同一份配置逻辑。

## 如何达成设计目的

通过在 `AwsClientProperties` 上新增一个通用的 `applyRetryConfigurations` 方法，并把它通过 `applyMutation` 链应用到三处构造 KMS client 的位置。这样既统一了配置入口，又避免在多个工厂里重复写 `ClientOverrideConfiguration` 构建代码。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java`

**修改目的**：新增通用的 retry 配置方法。

**工作逻辑**：新增 import：

```java
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
```

并新增方法：

```java
/**
 * Configure RetryMode to ADAPTIVE_V2 for AWS clients
 *
 * <p>Sample usage:
 * <pre>
 *   KmsClient.builder().applyMutation(awsClientProperties::applyRetryConfigurations)
 * </pre>
 */
public <T extends AwsClientBuilder> void applyRetryConfigurations(T builder) {
  ClientOverrideConfiguration.Builder configBuilder =
      null != builder.overrideConfiguration()
          ? builder.overrideConfiguration().toBuilder()
          : ClientOverrideConfiguration.builder();

  builder.overrideConfiguration(configBuilder.retryStrategy(RetryMode.ADAPTIVE_V2).build());
}
```

要点：
- 方法签名 `<T extends AwsClientBuilder>` 让它能接受 `KmsClientBuilder`、`S3ClientBuilder` 等任何 AWS client builder。
- **保留已有 overrideConfiguration**：先取 builder 当前已有的 `overrideConfiguration`，若非空则用 `toBuilder()` 在其基础上扩展，避免覆盖其他配置（如已设置的 region、credentials）。若为空则新建一个 builder。这是为了避免"应用 retry 配置时把别的配置抹掉"的隐性 bug。
- **强制使用 `ADAPTIVE_V2`**：直接硬编码 `RetryMode.ADAPTIVE_V2`，没有暴露成可配置 property——这是一个 opinionated 的默认选择，对 KMS 调用场景普遍适用。
- 调用 `builder.overrideConfiguration(...)` 把新构建的 config 设置回 builder。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java`

**修改目的**：默认工厂的 KMS client 应用 retry 配置。

**工作逻辑**：在 `kms()` 方法（位于一个匿名/内部 factory 实现中）的 builder 链上追加一行：

```java
return KmsClient.builder()
    .applyMutation(awsClientProperties::applyClientRegionConfiguration)
    .applyMutation(httpClientProperties::applyHttpClientConfigurations)
    .applyMutation(awsClientProperties::applyClientCredentialConfigurations)
+   .applyMutation(awsClientProperties::applyRetryConfigurations)
    .build();
```

`applyMutation` 是 AWS SDK 的 fluent 风格——传入 `Consumer<Builder>`，返回 builder 本身，便于链式调用。这里把新方法插入到 credential 配置之后、`build()` 之前。

### `aws/src/main/java/org/apache/iceberg/aws/AssumeRoleAwsClientFactory.java`

**修改目的**：AssumeRole 工厂的 KMS client 应用 retry 配置，并暴露 `awsClientProperties()` 给子类用。

**工作逻辑**：

1. 新增字段 `private AwsClientProperties awsClientProperties;`，并在 `initialize()` 中创建实例：
   ```java
   this.awsClientProperties = new AwsClientProperties(properties);
   ```
   这样工厂实例就持有了一份 `AwsClientProperties`，可被多个 client 构造复用。

2. 在 `kms()` 方法的 builder 链上插入 `.applyMutation(awsClientProperties::applyRetryConfigurations)`：
   ```java
   return KmsClient.builder()
       .applyMutation(this::applyAssumeRoleConfigurations)
       .applyMutation(httpClientProperties::applyHttpClientConfigurations)
+      .applyMutation(awsClientProperties::applyRetryConfigurations)
       .build();
   ```

3. 新增 `protected AwsClientProperties awsClientProperties()` getter，让子类（`LakeFormationAwsClientFactory`）能复用同一份配置。这是一个面向子类扩展的设计——避免子类重复构造 `AwsClientProperties` 实例。

### `aws/src/main/java/org/apache/iceberg/aws/lakeformation/LakeFormationAwsClientFactory.java`

**修改目的**：LakeFormation 工厂覆盖的 KMS client 也应用 retry 配置。

**工作逻辑**：`LakeFormationAwsClientFactory` 继承自 `AssumeRoleAwsClientFactory`，它覆写了 `kms()` 方法（因为需要用 `LakeFormationCredentialsProvider`）。在该覆写方法的 builder 链上插入：

```java
if (isTableRegisteredWithLakeFormation()) {
  return KmsClient.builder()
      .applyMutation(httpClientProperties()::applyHttpClientConfigurations)
+     .applyMutation(awsClientProperties()::applyRetryConfigurations)
      .credentialsProvider(
          new LakeFormationCredentialsProvider(lakeFormation(), buildTableArn()))
      .region(Region.of(region()))
```

注意这里用的是 `awsClientProperties()`（父类新暴露的 getter），而不是自己 new 一个，确保与父类共享同一份 properties 实例。

### `aws/src/test/java/org/apache/iceberg/aws/kms/TestKmsClientProperties.java`（新增）

**修改目的**：单元测试验证 retry 配置生效。

**工作逻辑**：新建测试类，一个测试方法：

```java
@Test
public void testApplyRetryConfiguration() {
  AwsClientProperties awsClientProperties = new AwsClientProperties();

  KmsClientBuilder builder = KmsClient.builder();
  awsClientProperties.applyRetryConfigurations(builder);
  RetryMode retryPolicy = builder.overrideConfiguration().retryMode().get();

  assertThat(retryPolicy).as("retry mode should be ADAPTIVE_V2").isEqualTo(RetryMode.ADAPTIVE_V2);
}
```

构造一个空的 `AwsClientProperties`（不需要任何 properties），调用 `applyRetryConfigurations` 后从 builder 的 `overrideConfiguration().retryMode()`（Optional<RetryMode>）取出实际值，断言为 `ADAPTIVE_V2`。这是一个轻量级但直接的单元测试，能捕获方法未生效或写错 RetryMode 的情况。

## 小结

- **成效**：所有 AWS KMS client（默认工厂、AssumeRole 工厂、LakeFormation 工厂）现统一使用 `RetryMode.ADAPTIVE_V2`，对 KMS 调用的限流与瞬时错误有更好的自适应容错能力，减少 envelope encryption 场景下的失败率。
- **影响范围**：5 个文件（4 个生产代码 + 1 个新测试），新增 71 行。改动集中在 `aws` 模块，对其他模块无影响。设计上通过 `AwsClientProperties.applyRetryConfigurations` 这一通用方法实现复用，未来若要给 S3/Glue 等其他 client 启用同样配置也只需一行 `applyMutation`。
- **回迁到 1.4.x 的注意事项**：**可选回迁**。1.4.x 分支若也有同样的 `AwsClientProperties` / `AssumeRoleAwsClientFactory` / `LakeFormationAwsClientFactory` 结构（KMS envelope encryption 在 1.4.x 已支持），本提交是纯增强、不破坏现有 API，可安全 cherry-pick。但需注意：1.4.x 早期版本可能没有 `awsClientProperties()` 这个 protected getter，或者 `AwsClientFactories.kms()` 的 builder 链结构略有不同，需手工核对位置。回迁后建议跑 KMS 相关集成测试确认行为符合预期。
