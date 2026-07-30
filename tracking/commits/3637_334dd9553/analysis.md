# 提交 3637：Core, AWS: Adapt code to S3 signing endpoint promotion (#15451)

## 提交信息

- **序号**：3637 / 4088
- **哈希**：334dd95535b4170fe8b2c75d48204ac3413a5cd6
- **短哈希**：334dd9553
- **日期**：2026-05-04 10:30:55 +0200
- **作者**：Alexandre Dutra
- **提交说明**：Core, AWS: Adapt code to S3 signing endpoint promotion (#15451)
- **PR/Issue**：#15451

## 总体目的

这个提交将 Iceberg 的远程签名（remote signing）机制从 S3 专用扩展为通用的远程签名能力。此前，Iceberg 的 REST Catalog 远程签名功能是 S3 专用的，相关请求/响应类（`S3SignRequest`、`S3SignResponse`）和配置属性（`s3.signer.uri`、`s3.signer.endpoint`）都硬编码为 S3 场景。随着社区在 dev 邮件列表中的讨论（参见 PR #15450 的 REST 规范变更），决定将 S3 签名端点"提升"（promotion）为通用的远程签名端点，使其能够支持 S3 之外的存储提供者（如 GCS、Azure 等）。

此次变更的核心动机是解耦签名机制与具体存储提供者：将原本位于 AWS 模块的签名请求/响应模型迁移到 Core 模块，引入新的 `RemoteSignRequest`/`RemoteSignResponse` 通用模型，并通过 `provider` 字段标识签名提供者（如 "s3"）。同时为保证向后兼容，旧的 S3 专用类和属性被标记为 `@Deprecated`（计划在 1.12.0 移除），但仍可继续使用。

## 如何达成设计目的

1. 在 Core 模块新增通用的 `RemoteSignRequest`、`RemoteSignRequestParser`、`RemoteSignResponse`、`RemoteSignResponseParser`，结构上与原 S3 专用类对应，但新增 `body` 和 `provider` 字段。
2. 在 `Endpoint` 和 `ResourcePaths` 中新增 `V1_TABLE_REMOTE_SIGN` 端点（路径 `.../tables/{table}/sign`），替代旧的 `v1/aws/s3/sign`。
3. 在 `RESTCatalogProperties` 中新增通用属性 `signer.uri` 和 `signer.endpoint`。
4. 改造 `S3V4RestSignerClient`：优先使用新的通用属性和 `RemoteSignRequest`/`RemoteSignResponse`，同时对旧属性保持兼容并输出弃用警告。
5. 将测试用的 `S3SignerServlet` 重构为抽象父类 `RemoteSignerServlet`，便于复用于其他提供者。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/requests/RemoteSignRequest.java` (+51 lines, new)

**修改目的**：定义通用的远程签名请求模型。

**工作逻辑**：使用 Immutables 定义接口，包含 `region`、`method`、`uri`、`headers`、`properties`、`body`（可选）、`provider`（可选）字段。`provider` 字段用于标识签名提供者（如 "s3"），是本次提升的关键新增字段。

### `core/src/main/java/org/apache/iceberg/rest/requests/RemoteSignRequestParser.java` (+141 lines, new)

**修改目的**：通用远程签名请求的 JSON 序列化/反序列化。

**工作逻辑**：提供 `toJson`/`fromJson` 方法，处理 region、method、uri、headers（值为字符串数组）、properties、body、provider 字段。headers 采用 `Map<String, List<String>>` 结构以符合 HTTP 头多值语义。

### `core/src/main/java/org/apache/iceberg/rest/responses/RemoteSignResponse.java` (+35 lines, new)

**修改目的**：定义通用远程签名响应模型，包含 `uri` 和 `headers` 字段。

### `core/src/main/java/org/apache/iceberg/rest/responses/RemoteSignResponseParser.java` (+71 lines, new)

**修改目的**：通用远程签名响应的 JSON 解析器。

### `core/src/main/java/org/apache/iceberg/rest/Endpoint.java` (+2 lines)

**修改目的**：新增远程签名端点常量。

**工作逻辑**：
```java
public static final Endpoint V1_TABLE_REMOTE_SIGN =
    Endpoint.create("POST", ResourcePaths.V1_TABLE_REMOTE_SIGN);
```

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java` (+13 lines)

**修改目的**：新增远程签名路径常量和构造方法。

**工作逻辑**：新增路径 `/v1/{prefix}/namespaces/{namespace}/tables/{table}/sign`，以及 `remoteSign(TableIdentifier)` 方法用于拼接具体表的签名 URL。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+13 lines)

**修改目的**：新增通用签名属性。

**工作逻辑**：
```java
public static final String SIGNER_URI = "signer.uri";
public static final String SIGNER_ENDPOINT = "signer.endpoint";
```
`SIGNER_URI` 默认回退到 `CatalogProperties.URI`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java` (+51 lines)

**修改目的**：注册新的 `RemoteSignRequest`/`RemoteSignResponse` 序列化器。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3V4RestSignerClient.java` (+80/-22 lines)

**修改目的**：适配新的通用签名机制，同时保留对旧 S3 专用属性和类的兼容。

**工作逻辑**：
1. 新增 `S3_PROVIDER = "s3"` 常量。
2. 将 `S3_SIGNER_URI`、`S3_SIGNER_ENDPOINT`、`S3_SIGNER_DEFAULT_ENDPOINT` 标记为 `@Deprecated`（1.12.0 移除）。
3. `baseSignerUri()` 和 `endpoint()` 方法优先读取旧属性（向后兼容），其次读取新的 `RESTCatalogProperties.SIGNER_URI`/`SIGNER_ENDPOINT`，最后回退到 `CatalogProperties.URI` 和默认端点。
4. `check()` 方法校验至少一种 URI 属性存在，并对使用旧属性或缺失 endpoint 的情况输出弃用警告。
5. 签名请求改用 `ImmutableRemoteSignRequest`，并设置 `provider(S3_PROVIDER)`：
```java
RemoteSignRequest remoteSigningRequest =
    ImmutableRemoteSignRequest.builder()
        .method(request.method().name())
        .region(signerParams.signingRegion().id())
        .uri(request.getUri())
        .headers(request.headers())
        .properties(requestPropertiesSupplier().get())
        .body(bodyAsString(request))
        .provider(S3_PROVIDER)
        .build();
```
6. 移除 `S3ObjectMapper.mapper()` 的使用，改用标准 ObjectMapper。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3SignRequest.java` (+5/-27 lines)

**修改目的**：将 `S3SignRequest` 标记为 `@Deprecated`，简化为继承 `RemoteSignRequest`。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3SignRequestParser.java` 等 S3 专用解析器

**修改目的**：标记为 `@Deprecated`，委托给新的 `RemoteSignRequestParser`/`RemoteSignResponseParser`。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3ObjectMapper.java` (+4 lines)

**修改目的**：标记为 `@Deprecated`，计划在 1.12.0 移除。

### `core/src/main/java/org/apache/iceberg/rest/RemoteSignerServlet.java` (+202 lines, new)

**修改目的**：从 `S3SignerServlet` 抽象出的通用测试用 Servlet 父类，可复用于其他签名提供者。

### `aws/src/main/java/org/apache/iceberg/aws/s3/signer/S3SignerServlet.java` (+20/-178 lines)

**修改目的**：重构为继承 `RemoteSignerServlet`，移除重复逻辑。

### 测试文件

- `TestS3V4RestSignerClient.java`：增强测试覆盖新旧属性兼容场景。
- `TestResourcePaths.java`：新增 `remoteSign` 路径测试。
- `TestRemoteSignRequestParser.java`、`TestRemoteSignResponseParser.java`：从 AWS 模块迁移到 Core 模块并扩展。

## 总结

这个提交是 Iceberg REST Catalog 远程签名机制的重要架构演进，将原本 S3 专用的签名端点提升为通用的远程签名能力，为支持多种存储提供者（GCS、Azure 等）的远程签名奠定基础。通过在 Core 模块引入通用的请求/响应模型和端点定义，同时以 `provider` 字段区分不同提供者，实现了签名机制与具体存储的解耦。对旧的 S3 专用类和属性采用弃用策略（1.12.0 移除）以保证向后兼容，体现了良好的 API 演进实践。
