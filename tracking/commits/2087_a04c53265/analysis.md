# 提交 2087：Core: Enable HTTP proxy support for the client used by REST Catalog

## 提交信息

- **序号**：2087 / 4088
- **哈希**：a04c532650156c0e3cb0de56cee48ddfb8e8fd2a
- **短哈希**：a04c53265
- **日期**：2025-05-06 15:53:57 -0700
- **作者**：Akhil Lawrence
- **提交说明**：Core: Enable HTTP proxy support for the client used by REST Catalog (#12406)
- **PR/Issue**：#12406

## 总体目的

Iceberg 的 REST Catalog 客户端（`HTTPClient`）使用 Apache HttpClient 5 与 REST Catalog 服务通信。在企业环境中，出站 HTTP 请求通常需要通过 HTTP 代理（如公司防火墙代理、安全审计代理等）才能访问外部服务。此前 `HTTPClient` 虽然已有 `withProxy`/`withProxyCredentialsProvider` 的内部构建器方法，但没有通过配置属性暴露代理设置——用户无法通过 Iceberg 的标准属性配置（如 catalog properties）来指定代理主机、端口和认证信息，只能通过编程方式自定义。

本提交为 `HTTPClient` 新增四个配置属性，使用户能够通过 properties 配置 HTTP 代理：
- `rest.client.proxy.hostname`：代理主机名
- `rest.client.proxy.port`：代理端口
- `rest.client.proxy.username`：代理认证用户名（可选）
- `rest.client.proxy.password`：代理认证密码（可选）

当配置了主机名和端口时，自动启用代理；当同时配置了用户名和密码时，使用 Basic 认证。这使 REST Catalog 客户端能在受限网络环境中正常工作。

## 如何达成设计目的

在 `HTTPClient` 的 Builder 构建逻辑中读取代理配置属性，调用已有的 `withProxy` 和 `withProxyCredentialsProvider` 方法配置 Apache HttpClient 的代理设置。关键组件协作关系如下：

- **配置属性**：四个 `rest.client.proxy.*` 常量定义代理参数键。
- **`PropertyUtil`**：从 properties Map 中读取代理主机、端口、用户名、密码。
- **`withProxy(hostname, port)`**：已有方法，设置 HttpClient 的代理主机和端口。
- **`BasicCredentialsProvider` + `UsernamePasswordCredentials`**：Apache HttpClient 的认证提供者，构建 Basic 认证凭据。
- **`withProxyCredentialsProvider`**：已有方法，将凭据提供者注入 HttpClient。
- **MockServer 测试**：使用 MockServer 启动模拟代理服务器，验证无认证和有认证两种场景下请求确实通过代理转发。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (修改, +34/-0 lines)

**修改目的**：通过配置属性启用 HTTP 代理支持。

**工作逻辑**：
- 新增 Apache HttpClient 认证相关的 import：`AuthScope`、`UsernamePasswordCredentials`、`BasicCredentialsProvider`。
- 新增 Guava `Strings` import。
- 定义四个配置属性常量：`REST_PROXY_HOSTNAME`、`REST_PROXY_PORT`、`REST_PROXY_USERNAME`、`REST_PROXY_PASSWORD`。
- 在 Builder 的构建逻辑中（设置客户端 header 之后）：
  1. 通过 `PropertyUtil.propertyAsString` 读取 `rest.client.proxy.hostname`。
  2. 通过 `PropertyUtil.propertyAsNullableInt` 读取 `rest.client.proxy.port`。
  3. 当主机名非空且端口非 null 时，调用 `withProxy(proxyHostname, proxyPort)` 启用代理。
  4. 进一步读取用户名和密码，当两者都非空时，创建 `BasicCredentialsProvider`，设置 `AuthScope`（主机+端口）和 `UsernamePasswordCredentials`，调用 `withProxyCredentialsProvider` 注入认证。注释说明当前仅支持 Basic 认证。
  5. 之后的 `proxyCredentialsProvider != null` 检查会验证 proxy 已设置。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java` (修改, +59/-0 lines)

**修改目的**：为代理配置新增集成测试。

**工作逻辑**：
新增两个测试，使用 MockServer 作为模拟代理服务器：
- `testClientWithProxyProps`：配置代理主机名（localhost）和端口（1070），创建带代理属性的 `HTTPClient`，向 REST Catalog URI 发送 HEAD 请求。通过 `proxyServer.verify` 验证请求确实经过了代理服务器（代理收到 1 次请求）。
- `testClientWithAuthProxyProps`：配置代理主机、端口、用户名和密码。MockServer 启动时配置代理认证（用户名/密码）。验证带认证的代理请求成功通过代理。这验证了 `BasicCredentialsProvider` 的正确配置。

## 总结

本提交为 REST Catalog 的 `HTTPClient` 新增通过配置属性启用 HTTP 代理的能力，定义四个属性（hostname、port、username、password），在 Builder 构建时读取并配置 Apache HttpClient 的代理和 Basic 认证。使用 MockServer 新增无认证和有认证两种代理场景的集成测试，验证请求确实通过代理转发。使 REST Catalog 客户端能在需要代理的企业网络环境中正常工作。
