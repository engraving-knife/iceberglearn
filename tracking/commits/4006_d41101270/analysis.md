# 提交 4006：AWS: Use assumed-role credentials for REST SigV4 signing (#16794)

## 提交信息

- **序号**：4006 / 4088
- **哈希**：d41101270ed4f8738c0769e05e37f5126972ab33
- **短哈希**：d41101270
- **日期**：2026-07-09 14:20:37 -0700
- **作者**：Gabriel Baldez
- **提交说明**：AWS: Use assumed-role credentials for REST SigV4 signing (#16794)
- **PR/Issue**：#16794（关联 issue #16667）

## 总体目的

本提交修复 AWS REST catalog 在使用 SigV4 签名时的凭证不一致 bug。当 catalog 配置为 `AssumeRoleAwsClientFactory` 时，assumed role（扮演角色）会正确应用到 S3/Glue/KMS/DynamoDB 客户端，但 `RESTSigV4AuthSession` 签名 REST 请求时使用的 `AwsProperties.restCredentialsProvider()` 决策链没有 assume-role 分支，导致 REST 调用静默回退到默认凭证链，与其它 AWS 客户端使用的扮演角色凭证不一致。

这会造成：在需要扮演角色才能访问 REST catalog 的环境中，REST 请求可能因凭证不足而失败，或使用了错误的身份（违反审计/权限隔离要求）。

本提交在 `restCredentialsProvider()` 的决策链中，于 custom-provider 和 default-chain 之间新增 assume-role 分支，返回基于现有 `client.assume-role.*` 配置构建的 `StsAssumeRoleCredentialsProvider`，与 `AssumeRoleAwsClientFactory` 使用的凭证保持一致。同时重构 `AssumeRoleAwsClientFactory` 复用 `AwsProperties` 中的共享逻辑，减少代码重复。

## 如何达成设计目的

设计思路：
1. 将 assume-role 凭证创建逻辑从 `AssumeRoleAwsClientFactory` 移到 `AwsProperties`，作为 package-private 方法 `assumeRoleCredentialsProvider()`，供 `restCredentialsProvider()` 和 `AssumeRoleAwsClientFactory` 共用。
2. 在 `restCredentialsProvider()` 中，当 `clientAssumeRoleArn` 非空且 `clientAssumeRoleRegion` 非空时，返回 `assumeRoleCredentialsProvider()`；若 region 缺失则 warn 并回退到默认凭证链。
3. `AwsProperties` 持有一个稳定的 `UUID uuid`（每个实例一个），用于在未配置 session name 时生成稳定的随机 session name（替代原来每次 `genSessionName()` 调用 `UUID.randomUUID()` 导致的不稳定）。
4. `AssumeRoleAwsClientFactory` 删除本地的 `sts()`/`genSessionName()`/`createCredentialsProvider()`/`createAssumeRoleRequest()`/`roleSessionName` 字段，改为调用 `awsProperties.assumeRoleCredentialsProvider()`。
5. 补充文档说明 SigV4 REST 凭证配置和 assume-role 配置。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsProperties.java` (+62/-0 lines)

**修改目的**：新增 assume-role 凭证创建逻辑并在 REST 签名链中加入该分支。

**工作逻辑**：
- 新增 `LOG`、`httpClientProperties`、`uuid` 字段。
- 构造函数初始化 `httpClientProperties` 和 `uuid = UUID.randomUUID()`（保证每个 `AwsProperties` 实例 session name 稳定）。
- `restCredentialsProvider()` 在 custom-provider 分支之后新增：
  ```java
  if (!Strings.isNullOrEmpty(this.clientAssumeRoleArn)) {
    if (!Strings.isNullOrEmpty(this.clientAssumeRoleRegion)) {
      return assumeRoleCredentialsProvider();
    }
    LOG.warn("Cannot assume role {} ... because {} is not set; falling back ...",
        this.clientAssumeRoleArn, CLIENT_ASSUME_ROLE_REGION);
  }
  ```
- `assumeRoleCredentialsProvider()`：构建 `StsAssumeRoleCredentialsProvider`，STS client 应用 http client 配置并指定 region，refresh request 通过 `createAssumeRoleRequest()` 构建。
- `createAssumeRoleRequest()`：构建 `AssumeRoleRequest`，session name 优先用配置值，否则用 `String.format("iceberg-aws-%s", uuid)`。

### `aws/src/main/java/org/apache/iceberg/aws/AssumeRoleAwsClientFactory.java` (+3/-39 lines)

**修改目的**：复用 `AwsProperties` 的共享逻辑，删除重复代码。

**工作逻辑**：
- 删除 `roleSessionName` 字段、`sts()`、`genSessionName()`、`createCredentialsProvider()`、`createAssumeRoleRequest()` 方法及相关 import。
- 三个 `applyAssumeRoleConfigurations` 重载中 `.credentialsProvider(createCredentialsProvider())` 改为 `.credentialsProvider(awsProperties.assumeRoleCredentialsProvider())`。
- 初始化时移除 `this.roleSessionName = genSessionName();`。

### `aws/src/test/java/org/apache/iceberg/aws/TestAwsProperties.java` (+62/-0 lines)

**修改目的**：新增 `AwsProperties` assume-role 凭证逻辑的测试。

**工作逻辑**：测试覆盖 assume-role 分支的正确触发、region 缺失时回退、session name 稳定性等（具体见文件）。

### `docs/docs/aws.md` (+2/-0 lines)

**修改目的**：在 AWS 文档中添加指向 SigV4 REST catalog 配置的说明。

### `docs/docs/catalog-properties.md` (+22/-0 lines)

**修改目的**：新增 SigV4 REST 凭证配置和 assume-role 配置文档。

## 总结

本提交修复了 AWS REST catalog SigV4 签名路径不使用 assume-role 凭证的不一致 bug，使 REST 请求与 S3/Glue 等客户端使用相同的扮演角色身份。通过将 assume-role 凭证创建逻辑集中到 `AwsProperties` 并在 REST 签名链中接入，消除了代码重复并保证了凭证一致性。同时引入稳定的 per-instance UUID 用于 session name，避免每次调用生成随机值。配套补充了文档和测试。这对需要角色扮演的合规/多租户环境至关重要。
