# 提交 3567：AWS: Add proxy system property and environment variable configuration for HTTP clients (#15506)

## 提交信息

- **序号**：3567 / 4088
- **哈希**：1f0579fb642603887b39a6d55ba79d57be540879
- **短哈希**：1f0579fb6
- **日期**：2026-04-21 09:00:24 -0700
- **作者**：Mervyn Lobo
- **提交说明**：AWS: Add proxy system property and environment variable configuration for HTTP clients (#15506)
- **PR/Issue**：#15506

## 总体目的

该提交为 Iceberg AWS 模块的 HTTP 客户端添加了通过系统属性和环境变量配置代理的能力。之前，AWS HTTP 客户端的代理配置仅支持通过 `http-client.proxy-endpoint` 显式指定代理端点。然而，AWS SDK 的 `ProxyConfiguration` 本身支持从 Java 系统属性（`http.proxyHost`、`http.proxyPort`、`http.nonProxyHosts` 等）和环境变量（`HTTP_PROXY`、`HTTPS_PROXY`、`NO_PROXY` 等）读取代理配置，且默认启用。

之前 Iceberg 的实现中，仅当 `proxyEndpoint` 非空时才会创建 `ProxyConfiguration`，这意味着用户无法显式控制系统属性/环境变量代理配置的使用，也无法在不需要代理时禁用这些默认行为。该提交新增两个配置项 `http-client.proxy-use-system-property-values` 和 `http-client.proxy-use-environment-variable-values`，允许用户显式启用或禁用从系统属性和环境变量读取代理配置。

## 如何达成设计目的

设计方案在 `HttpClientProperties` 中新增两个配置常量，在 Apache 和 UrlConnection 两种 HTTP 客户端配置类中新增对应字段，并重构代理配置逻辑：只要任一代理相关配置（proxyEndpoint、proxyUseSystemPropertyValues、proxyUseEnvironmentVariableValues）非空，就创建 `ProxyConfiguration` 并设置对应属性。这使用户可以在不指定 `proxyEndpoint` 的情况下仅通过系统属性或环境变量使用代理，或显式禁用这些默认行为。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/ApacheHttpClientConfigurations.java` (+33/-3 lines)

**修改目的**：为 Apache HTTP 客户端添加系统属性和环境变量代理配置支持。

**工作逻辑**：
- 新增 `proxyUseSystemPropertyValues` 和 `proxyUseEnvironmentVariableValues` 两个 Boolean 字段。
- 从配置 map 中读取这两个属性（使用 `propertyAsNullableBoolean`）。
- 重构 `configureApacheHttpClientBuilder` 中的代理逻辑为独立方法 `configureProxy`：当任一代理配置非空时创建 `ProxyConfiguration.Builder`，按需设置 endpoint、useSystemPropertyValues、useEnvironmentVariableValues。
- 在 `toString` 的 keyComponents 中添加这两个新字段（用于客户端标识/缓存键）。

### `aws/src/main/java/org/apache/iceberg/aws/HttpClientProperties.java` (+24/-0 lines)

**修改目的**：定义新的代理配置属性常量。

**工作逻辑**：
新增两个 public 常量：
- `PROXY_USE_SYSTEM_PROPERTY_VALUES = "http-client.proxy-use-system-property-values"`：控制是否从 Java 系统属性读取代理配置，默认为 true。
- `PROXY_USE_ENVIRONMENT_VARIABLE_VALUES = "http-client.proxy-use-environment-variable-values"`：控制是否从环境变量读取代理配置，默认为 true。

每个常量都带有详细的 Javadoc，说明用途并链接到 AWS SDK 的 `ProxyConfiguration` 文档。

### `aws/src/main/java/org/apache/iceberg/aws/UrlConnectionHttpClientConfigurations.java` (+33/-3 lines)

**修改目的**：为 UrlConnection HTTP 客户端添加相同的系统属性和环境变量代理配置支持。

**工作逻辑**：
与 `ApacheHttpClientConfigurations` 的改动对称：新增两个字段、从配置读取、重构 `configureProxy` 方法、在 keyComponents 中添加字段。注意 UrlConnection 的 `ProxyConfiguration` 是不同的类（`software.amazon.awssdk.http.urlconnection.ProxyConfiguration`），方法名为 `useEnvironmentVariablesValues`（注意有 's'）。

### `aws/src/test/java/org/apache/iceberg/aws/TestHttpClientConfigurations.java` (+37/-0 lines)

**修改目的**：验证新的代理配置触发逻辑。

**工作逻辑**：
新增两个参数化测试：
- `testApacheProxyFlagTriggersProxyConfig`：设置 `PROXY_USE_SYSTEM_PROPERTY_VALUES` 或 `PROXY_USE_ENVIRONMENT_VARIABLE_VALUES` 为 false，验证即使没有 `proxyEndpoint`，也会调用 `proxyConfiguration`。
- `testUrlConnectionProxyFlagTriggersProxyConfig`：同上，针对 UrlConnection 客户端。

### `docs/docs/aws.md` (+10/-5 lines)

**修改目的**：文档化新增的代理配置属性。

**工作逻辑**：
在 HTTP 客户端配置表中新增两行，描述 `http-client.proxy-use-system-property-values`（默认 null/启用）和 `http-client.proxy-use-environment-variable-values`（默认 null/启用）属性，说明它们分别控制系统属性和环境变量代理配置的读取。

## 总结

该提交增强了 Iceberg AWS 模块的代理配置灵活性，使用户能够通过 Java 系统属性和环境变量配置 HTTP 代理，并允许显式启用或禁用这些行为。这在企业环境中特别有用，因为这些环境通常通过系统属性或环境变量统一配置代理。改动覆盖 Apache 和 UrlConnection 两种 HTTP 客户端实现，并包含文档和测试更新。
