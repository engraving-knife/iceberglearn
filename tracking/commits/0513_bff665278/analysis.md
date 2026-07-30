# 提交 0513：Aliyun: Add security token to client properties

## 提交信息

- **序号**：0513 / 4088
- **哈希**：bff665278245128a71982ba5ac5981a9e71c4509
- **短哈希**：bff665278
- **日期**：2024-02-19（Mon Feb 19 15:39:11 2024 +0800）
- **作者**：Gang Wu（ustcwg）
- **提交说明**：Aliyun: Add security token to client properties (#9671)
- **PR/Issue**：#9671

## 总体目的

Iceberg 的 `aliyun` 模块通过 `AliyunClientFactories.DefaultAliyunClientFactory` 创建阿里云 OSS 客户端，原先只支持“AccessKey ID + AccessKey Secret”这种长期凭证（long-term credential）鉴权方式。但在生产环境中，许多企业出于安全合规要求不希望把长期 AccessKey 散布到各个计算节点或配置中，而是使用阿里云的 STS（Security Token Service）颁发**临时访问凭证**——即 AccessKey ID + AccessKey Secret + Security Token 的三元组，凭证有时效（一般 15 分钟～12 小时），过期后需要重新获取。这种方式显著提升了数据安全性，避免长期密钥泄露风险。

本提交的目的就是在 `AliyunProperties` 与默认工厂实现中新增一个 `client.security-token` 配置项，让用户能够把 STS 颁发的安全 token 通过 catalog 属性传入，并由 `DefaultAliyunClientFactory` 在构造 OSS 客户端时调用 `OSSClientBuilder.build(endpoint, accessKeyId, accessKeySecret, securityToken)` 这个四参重载，使 OSS 客户端能以 STS 临时凭证进行鉴权。未配置该项时回退到原三参重载，保持完全向后兼容。

## 如何达成设计目的

设计思路非常直接：

1. **配置层新增字段**：在 `AliyunProperties` 中新增常量 `CLIENT_SECURITY_TOKEN = "client.security-token"`，新增实例字段 `securityToken`，在构造器中从 properties map 解析该字段，并提供 `securityToken()` 访问器。这样所有从 catalog 属性加载出的 `AliyunProperties` 都能携带这个 token。

2. **默认工厂按需选择 build 重载**：在 `DefaultAliyunClientFactory.newOSSClient()` 中根据 `securityToken` 是否存在分支：
   - 若为 null 或空（`Strings.isNullOrEmpty`）：仍走原三参 `OSSClientBuilder.build(endpoint, accessKeyId, accessKeySecret)`，保证旧用户行为不变。
   - 若有值：走四参 `OSSClientBuilder.build(endpoint, accessKeyId, accessKeySecret, securityToken)`，把 STS token 注入到客户端鉴权链路中。

3. **测试覆盖**：
   - `TestAliyunClientFactories`：补充断言，未配置 token 时 `securityToken()` 为 null；配置 `client.security-token=token` 时 `securityToken()` 等于 "token"，且工厂仍为 `DefaultAliyunClientFactory`（验证不影响工厂选择）。
   - `TestOSSFileIO`：在原有“验证 access key/secret 已正确传给 OSS 凭证”的基础上，新增断言“无 STS 场景下 securityToken 为 null”，确保默认路径不会误传 token。

**消费链路**（自上而下）：
- 引擎/catalog 把 `client.security-token` 等键放入 properties map。
- `OSSFileIO.initialize(properties)` 调用 `AliyunClientFactories.from(properties)` → 反射加载 `DefaultAliyunClientFactory` 并 `initialize(properties)` → 内部 `new AliyunProperties(properties)` 解析出 `securityToken`。
- `OSSFileIO` 把 `factory::newOSSClient` 作为 `Supplier<OSS>` 保存到 `this.oss`，每次需要 OSS 客户端时调用 `newOSSClient()` 拿到一个带 STS 凭证的新 OSS 实例。
- 后续所有 OSS 文件读写（readFile/deleteFile/listObjects 等）都通过这个客户端进行，鉴权由 STS token 完成。

注意：本提交只覆盖 `DefaultAliyunClientFactory`，自定义工厂实现（用户通过 `client.factory` 指定自己的 `AliyunClientFactory` 子类）不受影响——它们本来就可以自行实现任意鉴权方式。

## 修改详情

### `aliyun/src/main/java/org/apache/iceberg/aliyun/AliyunProperties.java`

**修改目的**：新增 STS security token 的配置常量、字段、解析与访问器。

**工作逻辑**：
- 新增常量：
  ```java
  /**
   * Aliyun supports Security Token Service (STS) to generate temporary access credentials to
   * authorize a user to access the Object Storage Service (OSS) resources within a specific period
   * of time. In this way, user does not have to share the AccessKey pair and ensures higher level
   * of data security.
   *
   * <p>For more information about how to obtain a security token, see:
   * https://www.alibabacloud.com/help/en/vod/user-guide/sts-tokens
   */
  public static final String CLIENT_SECURITY_TOKEN = "client.security-token";
  ```
  注释清晰说明了 STS 的用途与官方文档链接。
- 新增实例字段 `private final String securityToken;`。
- 在 `AliyunProperties(Map properties)` 构造器中新增 `this.securityToken = properties.get(CLIENT_SECURITY_TOKEN);`，与其他 access key 字段并列解析。
- 新增访问器 `public String securityToken() { return securityToken; }`，与 `accessKeyId()` / `accessKeySecret()` 风格一致。

### `aliyun/src/main/java/org/apache/iceberg/aliyun/AliyunClientFactories.java`

**修改目的**：让默认工厂在构造 OSS 客户端时根据是否配置 STS token 选择正确的 `OSSClientBuilder.build` 重载。

**工作逻辑**：
- 新增 import `org.apache.iceberg.relocated.com.google.common.base.Strings`（Guava 风格的空判断工具，Iceberg 把 Guava 重打包到 `relocated` 命名空间以避免冲突）。
- `DefaultAliyunClientFactory.newOSSClient()` 由原来直接 `new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret)` 改为：
  ```java
  if (Strings.isNullOrEmpty(aliyunProperties.securityToken())) {
    return new OSSClientBuilder()
        .build(
            aliyunProperties.ossEndpoint(),
            aliyunProperties.accessKeyId(),
            aliyunProperties.accessKeySecret());
  } else {
    return new OSSClientBuilder()
        .build(
            aliyunProperties.ossEndpoint(),
            aliyunProperties.accessKeyId(),
            aliyunProperties.accessKeySecret(),
            aliyunProperties.securityToken());
  }
  ```
  即无 token 走三参重载（保持兼容），有 token 走四参重载（启用 STS）。`Preconditions.checkNotNull(aliyunProperties, ...)` 校验保留在分支之前，行为不变。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/TestAliyunClientFactories.java`

**修改目的**：验证 `AliyunProperties` 能正确解析并暴露 `client.security-token` 配置。

**工作逻辑**：
- 在原有“无配置时 factory.aliyunProperties() 为 null”的断言后，新增对默认 factory 的 `securityToken()` 为 null 的断言。
- 把 `defaultFactoryWithConfig` 的初始化属性从仅 `CLIENT_ACCESS_KEY_ID -> "key"` 扩展为同时包含 `CLIENT_SECURITY_TOKEN -> "token"`，并新增断言 `defaultFactoryWithConfig.aliyunProperties().securityToken()` 等于 "token"。这覆盖了“配置被正确解析、工厂类型仍是默认实现”的关键路径。

### `aliyun/src/test/java/org/apache/iceberg/aliyun/oss/TestOSSFileIO.java`

**修改目的**：在已有的 OSS 客户端凭证校验中补充“无 STS 时 securityToken 为 null”的反向断言。

**工作逻辑**：在原本验证 `getCredentials().getSecretAccessKey()` 等于 accessSecret 之后，新增：
```java
Assertions.assertThat(oss.getCredentialsProvider().getCredentials().getSecurityToken())
    .as("Should have no security token")
    .isNull();
```
确保在没有配置 STS token 的默认场景下，OSS 客户端的凭证对象里 securityToken 字段为 null，与生产代码的分支一致，避免回归。

## 小结

本提交是 aliyun 模块的小型功能增强：通过新增一个 `client.security-token` 配置项 + 默认工厂中的二分支构造，让 Iceberg 在阿里云 OSS 上能直接使用 STS 临时凭证进行鉴权，而无需用户自定义 `AliyunClientFactory` 实现。这降低了安全部署的门槛（短期 token 替代长期 AccessKey），同时完全向后兼容（不配置时行为不变）。

**影响范围**：
- 仅 `aliyun` 模块。新增一个用户可见的配置项 `client.security-token`。
- 不改变任何已有配置语义与默认行为。
- 自定义 `AliyunClientFactory` 实现不受影响（仍可通过反射加载自定义类）。

**回迁到 1.4.x 注意事项**：
1. 这是纯增强，回迁风险极低。只需把 4 个文件的改动同步过来即可。
2. 回迁后 1.4.x 用户即可在 aliyun catalog 属性中使用 `client.security-token` 配置 STS 临时凭证。
3. 阿里云 OSS SDK 中 `OSSClientBuilder.build(String endpoint, String accessKeyId, String accessKeySecret, String securityToken)` 四参重载在 1.4.x 依赖的 SDK 版本中应当可用，回迁前可快速确认 `aliyun/build.gradle` 中 OSS SDK 版本 ≥ 该四参重载引入的版本（早期 SDK 即支持）。
4. 注意 STS token 有时效性，本提交并不负责 token 自动刷新——若 token 过期，需要由上层（引擎或外部调度）重新拉取并重建 FileIO/catalog 实例。回迁时也应在文档中明确这一点。
