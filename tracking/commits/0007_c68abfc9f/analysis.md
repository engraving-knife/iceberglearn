# 提交 0007：AWS: avoid static global credentials provider which doesn't play well with lifecycle management (#8677)

## 提交信息

- **序号**：0007 / 4088
- **哈希**：c68abfc9fd3956077b43aba20441f089bb8b93d6
- **短哈希**：c68abfc9f
- **日期**：2023-10-01 18:55:31 -0700
- **作者**：Kristin Cowalcijk
- **提交说明**：AWS: avoid static global credentials provider which doesn't play well with lifecycle management (#8677)
- **PR/Issue**：#8677

## 总体目的

这个提交解决了 Iceberg AWS 模块中凭证提供者（credentials provider）的生命周期管理问题。

在 AWS SDK v2 中，`DefaultCredentialsProvider.create()` 返回的是一个**静态的全局单例**实例。AWS SDK 内部对默认凭证提供者做了缓存复用：所有调用 `create()` 的地方拿到的都是同一个共享实例。这种全局单例模式与 Iceberg 的使用场景存在冲突——Iceberg 在运行时会为不同的 catalog、不同的客户端实例创建多个 AWS 客户端（S3、Glue、DynamoDB、KMS 等），并且这些客户端可能拥有不同的配置、不同的生命周期。当某个客户端被关闭或其所属 catalog 被销毁时，如果凭证提供者是全局共享单例，就可能影响到其他仍在使用的客户端，导致凭证刷新、关闭等生命周期行为出现难以预测的问题。

具体而言，全局共享的 `DefaultCredentialsProvider` 不允许每个客户端独立管理自己的凭证提供者生命周期。例如，当一个短生命周期的 catalog 关闭其 AWS 客户端时，可能会连带影响到长生命周期的 catalog 所依赖的凭证提供者；又或者在动态凭证场景（如 STS 会话凭证、Web Identity 等）下，共享单例无法独立刷新。

本提交将 `DefaultCredentialsProvider.create()` 替换为 `DefaultCredentialsProvider.builder().build()`，后者每次调用都会创建一个**全新的独立实例**，使每个 AWS 客户端都能独立管理自己凭证提供者的生命周期，互不干扰。这是一个对正确性和健壮性有实质意义的改进，尤其在多 catalog、多引擎、长生命周期服务的部署场景下更为重要。

## 如何达成设计目的

设计思路是：将三处返回默认凭证提供者的代码从 `DefaultCredentialsProvider.create()`（返回全局单例）改为 `DefaultCredentialsProvider.builder().build()`（返回新实例）。涉及三个文件：`AwsClientFactories`、`AwsClientProperties`、`AwsProperties`，这三处分别对应不同的凭证获取入口。同时在 `AwsClientPropertiesTest` 中新增测试，断言连续两次调用 `credentialsProvider(null, null, null)` 返回的不是同一个对象实例，从而保证"每次创建新实例"的契约被测试覆盖。改动小而精准，三处修改 + 一个测试，结构清晰。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientFactories.java`

**修改目的**：让 `AwsClientFactories` 在未配置显式凭证时为每个客户端创建独立的默认凭证提供者实例。

**工作逻辑**：在 `loadCredentialsProvider`（或类似方法）的 else 分支中，将 `return DefaultCredentialsProvider.create();` 改为 `return DefaultCredentialsProvider.builder().build();`，并添加注释 `// Create a new credential provider for each client`。这样工厂在为每个 AWS 客户端装配凭证时，都会得到一个独立的提供者实例，避免共享单例带来的生命周期耦合。

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java`

**修改目的**：让 `AwsClientProperties` 的 `credentialsProvider` 方法在返回默认提供者时创建新实例。

**工作逻辑**：`AwsClientProperties` 是较新的、用于承载客户端级配置的类（相对于历史悠久的 `AwsProperties`）。其 `credentialsProvider(...)` 方法在用户未指定 `credentials-provider-class`、也未设置 access key 等静态凭证时，会走到默认分支。将该分支的 `DefaultCredentialsProvider.create()` 改为 `DefaultCredentialsProvider.builder().build()`，并添加相同注释。`AwsClientProperties` 是 Iceberg 推荐使用的新配置入口，因此这里的修复对面向未来的配置方式尤为重要。

### `aws/src/main/java/org/apache/iceberg/aws/AwsProperties.java`

**修改目的**：让历史配置类 `AwsProperties` 的 `credentialsProvider` 方法同样返回独立实例。

**工作逻辑**：`AwsProperties` 是较旧的配置承载类，仍被大量代码使用。其 `credentialsProvider(...)` 方法结构与 `AwsClientProperties` 类似，本提交做了完全相同的改动：将默认分支的 `create()` 改为 `builder().build()`，并加注释。这样无论是新代码还是旧代码路径，都能获得独立的凭证提供者实例，保证行为一致。

### `aws/src/test/java/org/apache/iceberg/aws/AwsClientPropertiesTest.java`

**修改目的**：验证每次调用都会创建新的默认凭证提供者实例。

**工作逻辑**：新增测试方法 `testCreatesNewInstanceOfDefaultCredentialsConfiguration()`。构造一个 `AwsClientProperties`，连续两次调用 `credentialsProvider(null, null, null)`（传入 null 表示不指定凭证类、不指定 access key、不指定 secret key，从而走默认分支），然后用 AssertJ 的 `isNotSameAs` 断言两次返回的对象不是同一个引用，失败信息为 "Should create a new instance in each call"。该测试直接守护了"每次创建新实例"这一行为契约，防止未来回归到 `create()` 单例模式。

## 小结

该提交将 AWS 默认凭证提供者从全局静态单例改为每次新建独立实例，使每个 AWS 客户端能独立管理凭证提供者生命周期，避免多 catalog / 多客户端场景下的生命周期冲突，提升了 Iceberg AWS 模块在复杂部署环境下的健壮性。
