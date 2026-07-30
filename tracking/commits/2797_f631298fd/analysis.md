# 提交 2797：Core: Return 304 from reference IRC (#14035)

## 提交信息

- **序号**：2797 / 4088
- **哈希**：f631298fd43c7425dc08f23c5b85a91d8c373aa2
- **短哈希**：f631298fd
- **日期**：2025-10-27 09:30:48 -0600
- **作者**：gaborkaszab
- **提交说明**：Core: Return 304 from reference IRC (#14035)
- **PR/Issue**：#14035

## 总体目的

本提交为 Iceberg REST Catalog（IRC）的引用（reference）加载添加 HTTP 304 Not Modified 响应支持，实现基于 ETag 的条件请求（conditional request）机制。

HTTP 304 Not Modified 是 HTTP 协议中的条件请求机制：客户端在请求资源时携带 `If-None-Match` 头（值为之前获取的 ETag），如果服务端判断资源未发生变化，则返回 304 状态码，不返回响应体，客户端继续使用缓存的版本。这可以减少网络带宽消耗和序列化/反序列化开销。

Iceberg REST Catalog 在加载表时已经支持返回 ETag 响应头，但之前服务端不会处理客户端发来的 `If-None-Match` 请求头，客户端也不会发送该头。本提交在服务端（RESTCatalogAdapter/RESTCatalogServlet）实现了 ETag 匹配检查和 304 响应，在客户端（HTTPClient）增加了对 304 状态码的处理。这是"freshness-aware loading"（ freshness 感知加载）机制的服务端部分。

## 如何达成设计目的

整体设计分为服务端和客户端两部分：

1. **服务端（RESTCatalogAdapter）**：在加载表时，检查请求中的 `If-None-Match` 头，如果与当前表的 ETag（基于 metadata location）匹配，则返回 null（不返回响应体）。
2. **服务端（RESTCatalogServlet）**：当响应体为 null 且路由为 `LOAD_TABLE` 时，设置 HTTP 304 状态码。
3. **客户端（HTTPClient）**：将 304 状态码加入成功状态码列表，并新增 `emptyBody` 方法判断响应体是否为空（包括 204 No Content、304 Not Modified、以及无响应类型的成功响应）。
4. **HTTPHeaders**：新增 `firstEntry` 方法，支持按名称查找第一个匹配的 HTTP 头。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (+10/-3 lines)

**修改目的**：客户端支持处理 304 Not Modified 响应。

**工作逻辑**：
- 在 `isSuccessful` 方法中增加 `HttpStatus.SC_NOT_MODIFIED`（304）为成功状态码。
- 新增 `emptyBody` 方法，将原来内联的空响应体判断逻辑提取为独立方法，并增加 304 状态码的判断：当响应码为 204 或 304，或响应类型为 null 且请求成功时，认为响应体为空，跳过解析。

### `core/src/main/java/org/apache/iceberg/rest/HTTPHeaders.java` (+6/-0 lines)

**修改目的**：新增按名称获取第一个匹配 HTTP 头的方法。

**工作逻辑**：新增 `firstEntry(String name)` 默认方法，通过流式遍历 `entries()`，大小写不敏感地匹配头名称，返回第一个匹配项的 `Optional<HTTPHeader>`。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+13/-1 lines)

**修改目的**：服务端适配器实现 ETag 条件请求检查。

**工作逻辑**：在加载表的逻辑中，先获取请求的 `If-None-Match` 头。计算当前表的 ETag（基于 metadata location）。如果 `If-None-Match` 头存在且与当前 ETag 匹配，则返回 null（表示资源未修改），不设置 ETag 响应头也不返回响应体。否则正常返回响应并设置 ETag 响应头。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogServlet.java` (+9/-0 lines)

**修改目的**：Servlet 层在响应体为空且为 LOAD_TABLE 路由时返回 304 状态码。

**工作逻辑**：当响应体为 null 时，通过 `Route.from(request.method(), request.path())` 解析路由，如果路由为 `LOAD_TABLE`，则将响应状态码设置为 `SC_NOT_MODIFIED`（304）。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+118/-29 lines)

**修改目的**：添加 304 Not Modified 场景的测试。

**工作逻辑**：
- 将 `RESTCatalogAdapter` 改为使用 Mockito.spy 包装，以便在测试中拦截和验证请求。
- 新增 `testNotModified` 测试：创建表后获取其 ETag，然后通过 Mockito 拦截器在加载表请求中注入 `If-None-Match` 头。验证服务端返回 304 时客户端的行为（当前客户端尚未实现 freshness-aware loading，会抛出 NullPointerException，标注了 TODO 表示未来客户端实现后会改变行为）。同时验证元数据表的加载也会触发底层表的加载请求。

## 总结

本提交为 Iceberg REST Catalog 实现了基于 ETag 的条件请求机制的服务端部分。当客户端发送 `If-None-Match` 头且表未变化时，服务端返回 304 Not Modified，避免不必要的响应体传输。客户端 HTTPClient 也相应增加了对 304 状态码的处理。这是 freshness-aware loading 机制的基础，测试中标注了 TODO 表明客户端完整实现将在后续提交中完成。
