# 提交 0763：Make proxy endpoint configurable for s3 Http clients (#10332)

## 提交信息

- **序号**：0763 / 4088
- **哈希**：5e08f886f367e548834afe1d43f99e0cb8d3ef5e
- **短哈希**：5e08f886f
- **日期**：2024-05-14 10:29:24 -0700
- **作者**：Yufei Gu
- **提交说明**：Make proxy endpoint configurable for s3 Http clients (#10332)
- **PR/Issue**：#10332

## 总体目的

本提交为 Iceberg AWS 模块的 S3 HTTP 客户端新增**代理端点（proxy endpoint）配置能力**。此前，Iceberg 的 AWS 集成允许用户配置 HTTP 客户端类型（`apache` 或 `urlconnection`）以及连接超时、socket 超时、最大连接数等参数，但**不支持配置代理**。在企业环境中，VPC 内的服务访问外部 S3 端点通常需要经过代理（如公司出口代理、安全审计代理、AWS VPC 端点代理等），缺乏代理配置能力会导致 Iceberg 在这些环境中无法正常访问 S3/Glue/Sts 等 AWS 服务。

本提交引入统一的配置项 `http-client.proxy-endpoint`，使两种 HTTP 客户端（Apache HttpClient 和 URL Connection HttpClient）都能通过同一属性指定代理端点 URL（如 `http://proxy:8080`），从而让 Iceberg 在需要代理的网络环境中正常工作。

## 如何达成设计目的

### 设计逻辑：统一配置项 + 分客户端应用

Iceberg AWS 模块的 HTTP 客户端配置采用"属性集中定义 + 配置类分散应用"的模式：

- **`HttpClientProperties`**：集中定义所有 HTTP 客户端相关的配置键常量（如 `CLIENT_TYPE`、`APACHE_CONNECTION_TIMEOUT_MS` 等），是配置项的"单一事实来源"。
- **`ApacheHttpClientConfigurations`**：专用于 Apache HttpClient 的配置类，从 properties 解析 Apache 特有参数并应用到 `ApacheHttpClient.Builder`。
- **`UrlConnectionHttpClientConfigurations`**：专用于 URL Connection HttpClient 的配置类，从 properties 解析 URL Connection 特有参数并应用到 `UrlConnectionHttpClient.Builder`。

代理端点这一配置需求的特点是：**两种 HTTP 客户端都需要支持代理，且配置项应统一**（用户不应因切换客户端类型而换配置键）。因此设计上：

1. 在 `HttpClientProperties` 中定义**一个统一的常量** `PROXY_ENDPOINT = "http-client.proxy-endpoint"`，并在 Javadoc 中注明它同时被 `UrlConnectionHttpClient.Builder` 和 `ApacheHttpClient.Builder` 使用。
2. 在两个客户端配置类中**各自解析并应用**该属性——因为两个客户端 SDK 的 `ProxyConfiguration` 类型不同（`software.amazon.awssdk.http.urlconnection.ProxyConfiguration` vs `software.amazon.awssdk.http.apache.ProxyConfiguration`），不能共用同一段应用代码，但解析逻辑（`PropertyUtil.propertyAsString`）和配置键是共享的。

### 应用逻辑：条件式应用 + URI 转换

在两个配置类的 `apply` 方法中，代理端点的应用遵循与其他可选参数一致的模式——**仅当配置非 null 时才设置**：

```java
if (proxyEndpoint != null) {
    apacheHttpClientBuilder.proxyConfiguration(
        ProxyConfiguration.builder().endpoint(URI.create(proxyEndpoint)).build());
}
```

这种"null 则跳过"的设计保证了：未配置代理时行为完全不变（不调用 `proxyConfiguration`，SDK 使用默认无代理设置），向后兼容；配置了代理时才构造 `ProxyConfiguration` 并注入。`URI.create(proxyEndpoint)` 将字符串形式的端点（如 `http://proxy:8080`）转为 AWS SDK 期望的 `URI` 类型。

### 配置类内部状态管理

两个配置类都在自己的字段列表中新增了 `private String proxyEndpoint` 字段，并在构造时（`from` 方法，对应 `Map<String, String> httpClientProperties` 参数）通过 `PropertyUtil.propertyAsString(httpClientProperties, HttpClientProperties.PROXY_ENDPOINT, null)` 解析。这里默认值传 `null` 是关键——`PropertyUtil.propertyAsString` 在键不存在时返回 null，使后续 `apply` 中的 null 检查能正确跳过代理设置。

注意两个配置类各自独立持有 `proxyEndpoint` 字段（而非共享），这是该模块既有设计风格（每个配置类管理自己的字段），保持一致性。

### 测试验证

测试通过 Mockito spy 验证两种行为：
1. **配置代理时**：构造 properties 包含 `PROXY_ENDPOINT = "http://proxy:8080"`，验证 `ApacheHttpClient.Builder` 和 `UrlConnectionHttpClient.Builder` 的 `proxyConfiguration(...)` 方法**被调用**（用 `Mockito.verify(spyBuilder).proxyConfiguration(Mockito.any(ProxyConfiguration.class))`）。
2. **未配置代理时**：properties 不含 `PROXY_ENDPOINT`，验证 `proxyConfiguration(...)` 方法**从未被调用**（`Mockito.verify(spyBuilder, Mockito.never()).proxyConfiguration(...)`）。

这种正反双向验证确保了"配置即生效、不配即不影响"的语义。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/HttpClientProperties.java`

**修改目的**：定义统一的代理端点配置键常量。

**工作逻辑**：
- 新增 import 无（常量是 String）。
- 新增 public 常量 `PROXY_ENDPOINT = "http-client.proxy-endpoint"`，带 Javadoc 说明它被两种 HTTP 客户端 Builder 共用。
- 新增 7 行。

### `aws/src/main/java/org/apache/iceberg/aws/ApacheHttpClientConfigurations.java`

**修改目的**：为 Apache HttpClient 解析并应用代理端点。

**工作逻辑**：
- 新增 import `java.net.URI`、`software.amazon.awssdk.http.apache.ProxyConfiguration`。
- 新增字段 `private String proxyEndpoint`。
- 在 `from(Map)` 解析方法中新增 `this.proxyEndpoint = PropertyUtil.propertyAsString(httpClientProperties, HttpClientProperties.PROXY_ENDPOINT, null)`。
- 在 `apply(ApacheHttpClient.Builder)` 方法中新增条件块：`if (proxyEndpoint != null) { apacheHttpClientBuilder.proxyConfiguration(ProxyConfiguration.builder().endpoint(URI.create(proxyEndpoint)).build()); }`。
- 新增 10 行。

### `aws/src/main/java/org/apache/iceberg/aws/UrlConnectionHttpClientConfigurations.java`

**修改目的**：为 URL Connection HttpClient 解析并应用代理端点（与 Apache 版本对称）。

**工作逻辑**：
- 新增 import `java.net.URI`、`software.amazon.awssdk.http.urlconnection.ProxyConfiguration`。
- 新增字段 `private String proxyEndpoint`。
- 在 `from(Map)` 解析方法中新增 `this.proxyEndpoint = PropertyUtil.propertyAsString(httpClientProperties, HttpClientProperties.PROXY_ENDPOINT, null)`。
- 在 `apply(UrlConnectionHttpClient.Builder)` 方法中新增条件块：`if (proxyEndpoint != null) { urlConnectionHttpClientBuilder.proxyConfiguration(ProxyConfiguration.builder().endpoint(URI.create(proxyEndpoint)).build()); }`。
- 新增 10 行。

### `aws/src/test/java/org/apache/iceberg/aws/TestHttpClientConfigurations.java`

**修改目的**：验证代理配置在两种客户端上的正反向行为。

**工作逻辑**：
- 新增 import `software.amazon.awssdk.http.apache.ProxyConfiguration`。
- 在已有的 URL Connection 正向测试中新增 `properties.put(HttpClientProperties.PROXY_ENDPOINT, "http://proxy:8080")`，并 verify `proxyConfiguration` 被调用。
- 在 URL Connection 反向测试（不配置任何超时）中 verify `proxyConfiguration` 从未被调用。
- 在已有的 Apache 正向测试中新增 `properties.put(HttpClientProperties.PROXY_ENDPOINT, "http://proxy:8080")`，并 verify `proxyConfiguration` 被调用。
- 在 Apache 反向测试中 verify `proxyConfiguration` 从未被调用。
- 新增 13 行。

### `docs/docs/aws.md`

**修改目的**：在文档配置表中补充代理端点配置项。

**工作逻辑**：
- 将原 HTTP Client 类型配置表（1 行 `http-client.type`）扩展为 2 行表格，新增 `http-client.proxy-endpoint` 行（默认 null，描述为"An optional proxy endpoint to use for the HTTP client"）。
- 调整表格前的引导语从"Configure the following property to set the type of HTTP client"改为"Configurations for the HTTP client can be set via catalog properties. Below is an overview of available configurations"，使表格涵盖多个配置项。
- 新增 9 行、删除 4 行。

## 小结

- **成效**：Iceberg AWS 模块的两种 S3 HTTP 客户端（Apache 和 URL Connection）均支持通过统一配置项 `http-client.proxy-endpoint` 设置代理端点，解决了需要代理才能访问 AWS 服务的网络环境下的可用性问题。配置向后兼容（未配置时行为不变），并通过文档和测试覆盖保证可发现性和正确性。
- **影响范围**：影响 AWS 模块的 HTTP 客户端初始化路径。所有使用 Iceberg AWS 集成（S3FileIO、GlueCatalog、AwsClientFactories 等）的用户在配置了 `http-client.proxy-endpoint` 后，其底层 S3/Glue/Sts 等 AWS SDK 客户端将经过指定代理访问 AWS 端点。未配置该属性时行为完全不变。
- **回迁注意事项**：
  1. 改动集中在 `aws` 模块的 4 个 Java 文件和 1 个文档文件，无跨模块依赖，cherry-pick 到 1.4.x 分支冲突风险低。
  2. 依赖 AWS SDK 的 `ProxyConfiguration` 类（`software.amazon.awssdk.http.apache.ProxyConfiguration` 和 `software.amazon.awssdk.http.urlconnection.ProxyConfiguration`），1.4.x 分支的 AWS SDK 版本中这两个类已存在（AWS SDK 2.x 早期版本即支持），无需升级依赖。
  3. 配置项 `http-client.proxy-endpoint` 是新增的 catalog 属性，回迁后用户可在 catalog 配置中直接使用，无破坏性变更。
  4. 测试使用 Mockito 验证 Builder 方法调用，依赖 1.4.x 分支已有的 Mockito 测试框架，无需新增测试依赖。
  5. 文档表格扩展是独立的小改动，若 1.4.x 分支 `aws.md` 文档结构有差异，可手动补全配置表行。
