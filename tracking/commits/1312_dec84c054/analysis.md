# 提交 1312：Core: Remove credentials from LoadViewResponse (#11432)

## 提交信息

- **序号**：1312 / 4088
- **哈希**：dec84c0549e80b113b3bb6799dcaf3f6d3c59fed
- **短哈希**：dec84c054
- **日期**：2024-10-30（Wed Oct 30 18:30:55 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Remove credentials from LoadViewResponse (#11432)
- **PR/Issue**：#11432

## 总体目的

Iceberg REST Catalog 协议在加载视图（LoadView）流程中，原本允许 `LoadViewResponse` 在响应体中携带 `storage-credentials` 字段，以向客户端下发存储凭据（如 S3 access-key、GCS OAuth token 等）。这与 `LoadTableResponse` 早期设计类似：在 v1 协议里把凭据嵌入到 `LoadTableResponse`/`LoadViewResponse`，让客户端拿到表/视图元数据时一并拿到访问底层存储所需的临时凭据。

随着 Iceberg REST 规范演进，凭据下发机制被独立到专门的 `LoadCredentialsResponse`（对应路由 `/v1/{prefix}/namespaces/{namespace}/tables/{table}/credentials` 等端点），由客户端按需发起请求获取。为减少协议冗余、避免同一份凭据在多个响应中出现（也降低误用与泄露面），社区决定从 `LoadViewResponse` 中移除 `credentials` 字段，使其与已对齐的 `LoadTableResponse` 行为保持一致。

本提交即为该清理工作的"Core 端实现"部分，仅删除 `LoadViewResponse` 接口中的 `credentials()` 默认方法及对应的 JSON 序列化/反序列化逻辑，并移除相关测试。

## 如何达成设计目的

1. 在 `LoadViewResponse` 接口中删除 `credentials()` 默认方法及其相关 import（`List`、`ImmutableList`、`Credential`）。
2. 在 `LoadViewResponseParser` 中：
   - 删除常量 `STORAGE_CREDENTIALS`；
   - 删除 `toJson` 中写出 `storage-credentials` 数组的代码段；
   - 删除 `fromJson` 中读取 `storage-credentials` 并通过 `LoadCredentialsResponseParser.fromJson(json).credentials()` 反向填充的代码段；
   - 清理已不再使用的 import。
3. 删除测试类 `TestLoadViewResponseParser` 中专门覆盖 credentials 往返序列化的 `roundTripSerdeWithCredentials` 测试方法及其 import。

这是一次纯删除式重构，不引入新功能，也不改变 `LoadViewResponse` 的其余字段（`metadata-location`、`metadata`、`config`）的行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadViewResponse.java`

**修改目的**：从 REST 响应模型中移除 `credentials` 字段。

**工作逻辑**：原接口包含一个 `@Value.Default` 方法：

```java
default List<Credential> credentials() {
  return ImmutableList.of();
}
```

该方法依赖 Immutables 生成 `ImmutableLoadViewResponse` 中的 `addCredentials` / `addAllCredentials` 构建器方法。删除后：

- 接口不再暴露 `credentials()`；
- 同时移除 `import java.util.List;`、`ImmutableList`、`Credential` 三个不再使用的 import；
- 接口仅保留 `metadataLocation()`、`metadata()`、`config()` 三个字段以及无操作的 `validate()`。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadViewResponseParser.java`

**修改目的**：从 JSON 序列化/反序列化路径中移除 `storage-credentials`。

**工作逻辑**：

- 删除字符串常量 `STORAGE_CREDENTIALS = "storage-credentials"`。
- `toJson(LoadViewResponse, JsonGenerator, boolean)` 中删除原本条件写出 `storage-credentials` 数组的代码块：

  ```java
  if (!response.credentials().isEmpty()) {
    gen.writeArrayFieldStart(STORAGE_CREDENTIALS);
    for (Credential credential : response.credentials()) {
      CredentialParser.toJson(credential, gen);
    }
    gen.writeEndArray();
  }
  ```

  删除后序列化产物只剩下 `metadata-location`、`metadata`、`config` 字段。
- `fromJson(JsonNode)` 中删除读取 `storage-credentials` 的代码块：

  ```java
  if (json.hasNonNull(STORAGE_CREDENTIALS)) {
    builder.addAllCredentials(LoadCredentialsResponseParser.fromJson(json).credentials());
  }
  ```

  注意这里旧实现复用了 `LoadCredentialsResponseParser.fromJson(json)` 来解析同名字段——这本质上是把整份响应当成 `LoadCredentialsResponse` 再解析一次取出 credentials，逻辑上比较迂回。移除后反序列化路径更简洁。
- 清理 import：`Credential`、`CredentialParser`。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadViewResponseParser.java`

**修改目的**：移除已删除字段的覆盖测试。

**工作逻辑**：删除 `roundTripSerdeWithCredentials` 测试方法（约 110 行）。该方法原本构造一个带 3 个 `Credential`（s3、gs://custom-uri、gs）的 `LoadViewResponse`，断言其序列化 JSON 中包含 `storage-credentials` 数组并能往返反序列化。删除后该测试类只剩覆盖 `metadata`、`config`、`metadata-location` 字段的常规用例。同时移除 `ImmutableCredential` import。

## 小结

- **成效**：`LoadViewResponse` 与 REST Catalog 的 v1 协议演进保持一致——凭据获取改为通过独立的 `/credentials` 端点按需请求，加载视图响应不再内嵌 `storage-credentials`，减少了凭据冗余字段与潜在泄露面。
- **影响范围**：3 个文件，纯删除共 135 行。无新增 API、无新增行为；属于接口收窄式的不兼容变更（序列化产物不再含 `storage-credentials` 字段）。
- **回迁到 1.4.x 的注意事项**：
  - 此变更为协议层面的不兼容收窄，会改变 REST 响应体的 JSON 结构。1.4.x 若仍在用旧协议（响应含 `storage-credentials`），回迁此提交会让 REST 客户端从 `LoadViewResponse` 中再也读不到 `credentials`，必须改为调用独立的 credentials 端点。
  - 需要确认 1.4.x 客户端/服务端是否已支持 `LoadCredentialsResponse` 与对应路由；若未支持，则需配套回迁相关凭据端点的实现，否则会出现视图加载后无凭据可用的回归。
  - 还需同步回迁 1313（OpenAPI 文档侧）的对应改动，保持协议文档与代码一致。
  - 鉴于这是协议演进，回迁前建议检查 1.4.x 既有的 REST 客户端（如 Spark、Trino 等下游）对 `LoadViewResponse` 的依赖是否仅读 `metadata`/`config`，避免破坏正在使用 credentials 字段的下游。
