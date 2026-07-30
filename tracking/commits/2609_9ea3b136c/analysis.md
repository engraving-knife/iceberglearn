# 提交 2609：Core: Extended header support for RESTClient implementations (#12194)

## 提交信息

- **序号**：2609 / 4088
- **哈希**：9ea3b136c2d06af1d5167900b7253e7b850db9f4
- **短哈希**：9ea3b136c
- **日期**：2025-09-08 14:00:06 +0200
- **作者**：gaborkaszab
- **提交说明**：Core: Extended header support for RESTClient implementations (#12194)
- **PR/Issue**：#12194

## 总体目的

本次提交为 Iceberg 的 RESTClient 实现添加了对 HTTP 响应头的处理能力，特别是引入了 ETag 机制来支持表的缓存和乐观并发控制。

在 REST Catalog 架构中，客户端通过 HTTP 与 REST Catalog 服务器通信。之前的 RESTClient 接口只关注响应体（body），无法获取响应头（headers）。这限制了一些重要的 HTTP 语义功能，特别是 ETag（实体标签）机制。

ETag 是 HTTP 协议中的标准头字段，用于标识资源的特定版本。在 Iceberg 的场景中，ETag 可以基于表的 metadata location 生成，用于：
1. **缓存验证**：客户端可以通过 ETag 判断表元数据是否发生变化，避免不必要的完整加载
2. **乐观并发控制**：在更新表时可以通过 If-Match 头进行条件更新，防止并发写入冲突
3. **变更检测**：客户端可以快速比较 ETag 来检测表是否被其他操作修改

本次提交实现了 ETag 的生成和传递机制，使 REST Catalog 服务器在返回表操作响应（如 create、load、register、update）时附带 ETag 头。

## 如何达成设计目的

整体设计分为三层：

1. **RESTClient 接口扩展**：在 `RESTClient` 接口中新增带 `responseHeaders` 回调参数的 `get` 方法重载，允许调用方接收响应头。`BaseHTTPClient` 实现了该方法。

2. **ETag 生成**：新增 `ETagProvider` 工具类，使用 Murmur3 哈希算法对表的 metadata location 生成 ETag 值。

3. **服务器端集成**：在 `RESTCatalogAdapter` 的 `handleRequest` 中，为表操作（CREATE_TABLE、LOAD_TABLE、REGISTER_TABLE、UPDATE_TABLE）生成 ETag 并通过 `responseHeaders` 回调返回。`RESTCatalogServlet` 将这些响应头设置到 HTTP 响应中。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/BaseHTTPClient.java` (+12/-0 lines)

**修改目的**：实现带响应头回调的 GET 方法。

**工作逻辑**：新增 `get` 方法重载，接受 `Consumer<Map<String, String>> responseHeaders` 参数。该方法构建 GET 请求后调用 `execute`，将 responseHeaders 消费者传递下去，使调用方能够接收 HTTP 响应头。`execute` 方法内部在处理响应时会调用该消费者，传入响应头映射。

### `core/src/main/java/org/apache/iceberg/rest/ETagProvider.java` (+37/-0 lines, 新文件)

**修改目的**：提供 ETag 生成功能。

**工作逻辑**：使用 Guava 的 `Hashing.murmur3_32_fixed()` 哈希函数对 metadata location 字符串进行哈希，生成 32 位哈希值作为 ETag。选择 Murmur3 是因为它速度快且分布均匀，适合作为非加密用途的 ETag 生成。对 null 和空字符串输入进行参数校验，抛出 `IllegalArgumentException`。该类为包级可见（package-private），仅在 REST 模块内部使用。

### `core/src/main/java/org/apache/iceberg/rest/RESTClient.java` (+24/-0 lines)

**修改目的**：扩展 RESTClient 接口以支持响应头回调。

**工作逻辑**：新增两个 `get` 方法的 default 实现：
1. 接受 `Supplier<Map<String, String>> headers` 和 `Consumer<Map<String, String>> responseHeaders` 的版本，委托给接受 Map 的版本。
2. 接受 `Map<String, String> headers` 和 `Consumer<Map<String, String>> responseHeaders` 的版本，如果 responseHeaders 非 null 则抛出 `UnsupportedOperationException`（因为 default 实现不支持返回响应头，需要子类覆盖）。

这种设计保持了向后兼容：现有实现不需要修改，只有需要支持响应头的实现（如 `BaseHTTPClient`）才覆盖新方法。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+44/-10 lines)

**修改目的**：在测试用 Catalog Adapter 中集成 ETag 生成。

**工作逻辑**：
1. `handleRequest` 方法签名变更：从接受 `Object body` 改为接受完整的 `HTTPRequest httpRequest` 和 `Consumer<Map<String, String>> responseHeaders`，从中提取 body。
2. 在四个表操作的路由处理中（CREATE_TABLE、LOAD_TABLE、REGISTER_TABLE、UPDATE_TABLE），获取 `LoadTableResponse` 后，使用 `ETagProvider.of(resp.metadataLocation())` 生成 ETag，并通过 `responseHeaders.accept(ImmutableMap.of(HttpHeaders.ETAG, eTag))` 返回。
3. `execute` 方法将 responseHeaders 传递给 `handleRequest`。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogServlet.java` (+10/-5 lines)

**修改目的**：在 Servlet 中传递动态响应头。

**工作逻辑**：
1. 将原来固定的 `responseHeaders` 改名为 `DEFAULT_RESPONSE_HEADERS`（只包含 Content-Type）。
2. 在 `execute` 方法中，创建一个可变的 `Map<String, String> responseHeaders`，将其作为 `responseHeaders::putAll` 回调传递给 adapter 的 `execute` 方法。
3. adapter 处理完后，将 responseHeaders 中的所有头设置到 HTTP 响应中。
4. 这样既保留了默认的 Content-Type 头，又能动态添加 ETag 等头。

### `core/src/test/java/org/apache/iceberg/rest/TestETagProvider.java` (+50/-0 lines, 新文件)

**修改目的**：测试 ETagProvider 的正确性。

**工作逻辑**：
1. `testNullInput`：验证 null 输入抛出 IllegalArgumentException。
2. `testEmptyInput`：验证空字符串输入抛出 IllegalArgumentException。
3. `testETagContent`：验证已知输入产生预期的哈希值（如长路径产生 "1f865717"，短路径 "/short/path" 产生 "55faa5d9"），确保哈希结果的确定性和一致性。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java` (+3/-2 lines)

**修改目的**：适配 GET 方法签名变更。

**工作逻辑**：在测试的 GET 调用中增加 `ImmutableMap.of()` 作为 queryParams 参数和 responseHeaders 参数，适配新增的方法签名。同时修正了注释中 "HttpRESTClient" 为 "HTTPClient"。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+135/-4 lines)

**修改目的**：添加 ETag 功能的端到端测试。

**工作逻辑**：
1. 新增辅助方法 `catalogWithResponseHeaders`，创建一个能捕获响应头的 RESTCatalogAdapter，将响应头存入外部 Map 供测试验证。
2. 新增 5 个测试用例：
   - `testETagWithCreateAndLoadTable`：验证创建表和加载表返回相同的 ETag（因为 metadata location 相同）。
   - `testETagWithDifferentTables`：验证不同表返回不同的 ETag。
   - `testETagAfterDataUpdate`：验证数据更新（append）后 ETag 变化（因为 metadata location 变了）。
   - `testETagAfterMetadataOnlyUpdate`：验证 schema 更新后 ETag 变化。
   - `testETagWithRegisterTable`：验证注册表时返回与原表相同的 ETag（因为指向同一 metadata location）。
3. 修改了分页相关测试的 Mockito verify 调用，增加 `any()` 参数以匹配新增的 responseHeaders 参数。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+3/-3 lines)

**修改目的**：适配分页测试中 verify 调用的参数变更。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalogWithAssumedViewSupport.java` (+6/-2 lines)

**修改目的**：适配 handleRequest 方法签名变更。

**工作逻辑**：更新 `handleRequest` 的覆盖实现，从旧的 `(Route, Map, Object, Class)` 签名改为新的 `(Route, Map, HTTPRequest, Class, Consumer<Map<String, String>>)` 签名，并传递新参数。

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTServerCatalogAdapter.java` (+5/-1 lines)

**修改目的**：适配 handleRequest 方法签名变更。

**工作逻辑**：更新 `handleRequest` 覆盖实现以匹配新签名，将参数传递给父类方法。

## 总结

本次提交为 RESTClient 引入了响应头处理能力，核心应用是 ETag 支持。通过基于表 metadata location 的 Murmur3 哈希生成 ETag，REST Catalog 服务器在表操作响应中返回 ETag 头，客户端可用于缓存验证和变更检测。设计上保持了向后兼容（default 方法 + UnsupportedOperationException 模式），测试覆盖了 ETag 的一致性、差异性和变更场景。这是 REST Catalog 向更完善的 HTTP 语义支持迈进的重要一步，为后续的乐观并发控制等高级功能奠定了基础。
