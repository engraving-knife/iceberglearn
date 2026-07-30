# 提交 3183：AWS, Azure, GCP: Configure headers in HTTPClient from properties (#15110)

## 提交信息

- **序号**：3183 / 4088
- **哈希**：b2f312f4396d1b5674e2378dbef3887fb2d789b6
- **短哈希**：b2f312f43
- **日期**：2026-01-30
- **作者**：Thomas Powell
- **提交说明**：AWS, Azure, GCP: Configure headers in HTTPClient from properties (#15110)
- **PR/Issue**：#15110

## 总体目的

Iceberg REST Catalog 允许用户通过以 `header.` 为前缀的配置属性向 REST 请求注入自定义 HTTP 头（例如 `header.X-Custom-Auth=xxx`）。主 REST Catalog 客户端（`RESTSessionCatalog`）在初始化时通过 `RESTUtil.configHeaders(properties)`（内部调用 `extractPrefixMap(properties, "header.")`）提取这些头并应用到所有 catalog 请求中。这些自定义头在实际场景中用途广泛：通过代理路由请求、注入链路追踪头、满足 REST Catalog 服务端的额外认证或路由要求等。

然而，三大云平台模块中存在四类**辅助 HTTP 客户端**，它们负责凭证刷新与请求签名，同样向 REST Catalog 端点发起 HTTP 请求，但此前并未应用用户配置的自定义头：

1. **AWS S3 `VendedCredentialsProvider`**：当 S3 使用 vended credentials（由 catalog 下发的临时凭证）时，向 catalog endpoint 请求刷新凭证。
2. **AWS S3 `S3V4RestSignerClient`**：S3 V4 REST 签名客户端，向签名服务发起签名请求。
3. **Azure ADLS `VendedAdlsCredentialProvider`**：当 ADLS 使用 vended credentials 时，向 catalog endpoint 请求刷新凭证。
4. **GCP GCS `OAuth2RefreshCredentialsHandler`**：GCS OAuth2 令牌刷新处理器，向 catalog endpoint 请求刷新令牌。

这些辅助客户端仅通过 `HTTPClient.builder(properties)` 继承了基础属性（如 URI、SSL 配置等），但遗漏了 `header.*` 前缀的自定义头。结果是：当用户的 REST Catalog 部署依赖自定义头进行路由或认证时（例如经过需要特定头的反向代理），主 catalog 请求可以正常工作，但这些辅助请求因缺少必要头而失败，导致凭证刷新或签名中断，进而影响数据读写。

本提交统一为这四个辅助 HTTP 客户端补充 `.withHeaders(RESTUtil.configHeaders(properties))`，确保用户配置的自定义头一致地传播到所有内部 HTTP 请求。

## 如何达成设计目的

改动涉及 AWS、Azure、GCP 三个模块的四个文件，每个文件在构建 `HTTPClient` 时追加 `.withHeaders(RESTUtil.configHeaders(properties))` 调用。`RESTUtil.configHeaders` 从 properties 中提取所有 `header.` 前缀的键值对并去除前缀，生成头 map；`HTTPClient.builder().withHeaders()` 将其设为客户端默认请求头。四处改动模式一致，均是在已有的 `.uri(catalogEndpoint)` 或 `.withObjectMapper(...)` 之后链式追加 `.withHeaders(...)`。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/VendedCredentialsProvider.java` (+5/-1 lines)

**修改目的**：为 S3 vended credentials 刷新客户端补充自定义请求头。

**工作逻辑**：
在 `VendedCredentialsProvider` 的延迟初始化块中，构建 `HTTPClient` 时，原代码为 `HTTPClient.builder(properties).uri(catalogEndpoint).build()`。修改后追加 `.withHeaders(RESTUtil.configHeaders(properties))`：
```java
HTTPClient httpClient =
    HTTPClient.builder(properties)
        .uri(catalogEndpoint)
        .withHeaders(RESTUtil.configHeaders(properties))
        .build();
```
同时新增 `import org.apache.iceberg.rest.RESTUtil`。该客户端用于向 catalog endpoint 请求 S3 临时凭证刷新，补充头后可正确通过代理或满足服务端头要求。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java` (+4/-1 lines)

**修改目的**：为 S3 V4 REST 签名客户端补充自定义请求头。

**工作逻辑**：
在 `S3V4RestSignerClient` 的懒加载初始化块中，构建 `httpClient` 时追加 `.withHeaders(RESTUtil.configHeaders(properties()))`：
```java
httpClient =
    HTTPClient.builder(properties())
        .withHeaders(RESTUtil.configHeaders(properties()))
        .withObjectMapper(S3ObjectMapper.mapper())
        .build();
```
新增 `import org.apache.iceberg.rest.RESTUtil`。该客户端用于向 S3 REST 签名服务发送签名请求。注意此处使用的是 `properties()`（实例方法返回的属性），而非独立变量。该客户端注释说明不设置 base URI，因为可能用于联系不同的 catalog。

### `azure/src/main/java/org/apache/iceberg/azure/adlsv2/VendedAdlsCredentialProvider.java` (+5/-1 lines)

**修改目的**：为 ADLS vended credentials 刷新客户端补充自定义请求头。

**工作逻辑**：
在 `VendedAdlsCredentialProvider` 的延迟初始化块中，构建 `HTTPClient` 时追加 `.withHeaders(RESTUtil.configHeaders(properties))`，模式与 AWS `VendedCredentialsProvider` 完全一致。新增 `import org.apache.iceberg.rest.RESTUtil`。该客户端用于向 catalog endpoint 请求 ADLS 临时凭证刷新。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/OAuth2RefreshCredentialsHandler.java` (+5/-1 lines)

**修改目的**：为 GCS OAuth2 令牌刷新客户端补充自定义请求头。

**工作逻辑**：
在 `OAuth2RefreshCredentialsHandler` 的延迟初始化块中，构建 `HTTPClient` 时追加 `.withHeaders(RESTUtil.configHeaders(properties))`，模式与上述 AWS/Azure 改动一致。新增 `import org.apache.iceberg.rest.RESTUtil`。该客户端用于向 catalog endpoint 请求 GCS OAuth2 令牌刷新。

## 总结

本提交修复了三大云平台模块中四类辅助 HTTP 客户端（S3 vended credentials 刷新、S3 V4 REST 签名、ADLS vended credentials 刷新、GCS OAuth2 令牌刷新）未传播用户自定义 `header.*` 请求头的问题。通过统一追加 `.withHeaders(RESTUtil.configHeaders(properties))`，确保所有内部 HTTP 请求与主 REST Catalog 客户端行为一致，解决了代理路由、链路追踪和服务端额外认证头场景下辅助请求失败的问题。改动模式统一、范围清晰，是一处重要的配置传播一致性修复。
