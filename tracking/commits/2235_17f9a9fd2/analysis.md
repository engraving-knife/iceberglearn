# 提交 2235：REST: Add property for configuring user agent in http client

## 提交信息

- **序号**：2235 / 4088
- **哈希**：17f9a9fd28bbcb37745f13ebfcd57cd5a96e0a5d
- **短哈希**：17f9a9fd2
- **日期**：2025-06-13 12:08:33 -0700
- **作者**：Prashant Singh
- **提交说明**：REST: Add property for configuring user agent in http client
- **PR/Issue**：#13234

## 总体目的

本提交为 Iceberg REST HTTP 客户端添加了自定义 User-Agent 的配置能力。在此修改之前，REST 客户端使用 Apache HttpClient 的默认 User-Agent，服务端无法识别客户端的具体身份和版本信息。添加 `rest.client.user-agent` 配置属性后，用户可以设置自定义的 User-Agent 字符串，使 REST 服务端能够识别请求来源、进行客户端区分、流量统计或兼容性管理。这在多客户端共用同一 REST 服务的场景下尤为重要，例如不同的计算引擎或应用可以通过 User-Agent 标识自己。

## 如何达成设计目的

- 在 `HTTPClient` 类中新增 `REST_USER_AGENT` 常量定义配置键名 `rest.client.user-agent`。
- 在 HTTP 客户端构建逻辑中，从属性中读取 `rest.client.user-agent`，如果非空则通过 `clientBuilder.setUserAgent(userAgent)` 设置到 HttpClient 构建器上。
- 在测试中验证 User-Agent 被正确发送到服务端。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (修改, +6/-0 lines)

**修改目的**：添加 User-Agent 配置属性的读取和设置逻辑。

**工作逻辑**：新增常量 `REST_USER_AGENT = "rest.client.user-agent"`。在构造函数中，使用 `PropertyUtil.propertyAsString(properties, REST_USER_AGENT, null)` 读取配置值，默认为 null。如果值非空，调用 `clientBuilder.setUserAgent(userAgent)` 将自定义 User-Agent 设置到 Apache HttpClient 的构建器上，HttpClient 会在每个请求的 `User-Agent` 头中自动携带该值。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java` (修改, +8/-2 lines)

**修改目的**：验证 User-Agent 配置被正确发送。

**工作逻辑**：在测试类中新增 `USER_AGENT = "User-Agent"` 和 `TEST_USER_AGENT = "Test-User-Agent"` 常量。在 `beforeClass` 中构建 HTTPClient 时传入 `REST_USER_AGENT` 属性配置。在请求验证断言中新增 `.withHeader(USER_AGENT, TEST_USER_AGENT)`，确保 HTTP 请求确实携带了自定义的 User-Agent 头。

## 总结

本提交为 Iceberg REST HTTP 客户端添加了通过 `rest.client.user-agent` 属性配置自定义 User-Agent 的能力，使 REST 服务端能够识别和区分不同的客户端，便于流量管理和兼容性处理。改动简洁，包含完整的测试验证。
