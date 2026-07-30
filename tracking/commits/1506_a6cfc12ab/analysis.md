# 提交序号 1506 短哈希 a6cfc12ab 分析

## 提交信息
- 哈希：a6cfc12ab080e3dafcec05e10db665c6a843fe4c
- 日期：2024-12-18
- 作者：Alexandre Dutra <adutra@users.noreply.github.com>
- 消息：Auth Manager API part 1: HTTPRequest, HTTPHeader (#11769)

## 总体目的

本提交是 Auth Manager API 系列工作的第一部分，引入了两个新的不可变值对象（value object）：`HTTPHeaders` 和 `HTTPRequest`，用于在 REST 客户端层面统一表示 HTTP 请求及其头部。这是为后续 Auth Manager（认证管理器）机制打基础的前置工作——Auth Manager 需要能够拦截、检查并修改即将发出的 HTTP 请求（例如注入 Authorization 头部、刷新令牌等），因此首先需要一个结构化、可扩展的请求表示模型。

在修改之前，Iceberg 的 REST 客户端直接使用底层 HTTP 客户端（Apache HttpClient）的请求对象，缺少一个中间层的、与具体 HTTP 客户端实现解耦的请求抽象。这导致难以在请求发出前统一地注入认证信息。本提交通过引入 `HTTPRequest`/`HTTPHeaders` 这一对不可变模型，为后续 Auth Manager 提供了清晰的切入点：Auth Manager 可以接收 `HTTPRequest`，基于其内容决定认证策略，并返回一个可能修改过头部的新 `HTTPRequest`。

这两个类都基于 Immutables 库生成不可变实现，强调线程安全和安全日志（敏感字段如 body、header value 标记为 `@Value.Redacted`，避免在日志中泄露敏感信息）。

## 如何达成设计目的

本提交通过新增 4 个文件来达成目的：2 个主代码文件（`HTTPHeaders.java`、`HTTPRequest.java`）和 2 个测试文件（`TestHTTPHeaders.java`、`TestHTTPRequest.java`）。主代码定义不可变模型，测试覆盖其行为契约。

### 修改详情

#### core/src/main/java/org/apache/iceberg/rest/HTTPHeaders.java（新增）

该文件定义了 HTTP 头部集合的不可变模型，包含一个外层接口 `HTTPHeaders` 和一个内层接口 `HTTPHeader`。

`HTTPHeaders` 接口：
- 使用 `@Value.Immutable` 生成不可变实现 `ImmutableHTTPHeaders`，使用 `@Value.Style(depluralize = true)` 使 builder 方法名更自然（`addEntry` 而非 `addEntries` 单数形式）。
- 提供 `entries()` 返回所有头部条目的集合。
- 提供按名称查询的默认方法：`entries(String name)` 返回指定名称（大小写不敏感）的所有条目；`contains(String name)` 判断是否包含某名称的头部。大小写不敏感符合 RFC 2616 对 HTTP 头部名称比较的规定。
- 提供 `putIfAbsent` 系列方法：`putIfAbsent(HTTPHeader)` 和 `putIfAbsent(HTTPHeaders)`，在头部不存在时才添加，返回新实例。这是 Auth Manager 场景的关键能力——可以安全地注入认证头部而不覆盖已存在的头部。实现上基于 `ImmutableHTTPHeaders.builder().from(this).addEntry(...)` 构建新实例。
- 提供静态工厂 `of(HTTPHeader...)`。
- 常量 `EMPTY = of()` 表示空头部集合。

`HTTPHeader` 内层接口：
- 表示单个头部名值对，`name()` 和 `value()`。
- `value()` 标记 `@Value.Redacted` 且 style 设 `redactedMask = "****"`，防止敏感头部值（如 token）在日志/toString 中泄露。
- `@Value.Check` 校验方法 `check()` 确保头部名称非空（抛 `IllegalArgumentException`）。
- 静态工厂 `of(String name, String value)`。

#### core/src/main/java/org/apache/iceberg/rest/HTTPRequest.java（新增）

该文件定义 HTTP 请求的不可变模型，是 Auth Manager 操作的核心对象。

- 枚举 `HTTPMethod`：GET、HEAD、POST、DELETE。
- 属性：`baseUri()`（REST 客户端配置的基础 URI）、`method()`、`path()`、`queryParameters()`（Map）、`headers()`（默认 `HTTPHeaders.EMPTY`）、`body()`（可空，`@Value.Redacted`）、`mapper()`（默认 `RESTObjectMapper.mapper()`）。
- `requestUri()` 是 `@Value.Lazy` 计算属性：将 baseUri、path、queryParameters 组合成完整 URI。逻辑上，若 path 以 `http://` 或 `https://` 开头则视为绝对路径直接使用，否则拼接 `baseUri/path`；再用 `URIBuilder` 添加查询参数。失败时抛 `RESTException`。
- `encodedBody()` 是 `@Value.Lazy` 计算属性：将 body 编码为字符串。若 body 是 Map，则用 `RESTUtil.encodeFormData` 编码为表单数据（用于 OAuth token 交换等场景）；否则用 `ObjectMapper` 序列化为 JSON。失败抛 `RESTException`。
- `@Value.Check` 校验方法 `check()`：若 path 以 `/` 开头则抛异常，因为 Iceberg REST 路径约定不以斜杠开头。
- body 和 encodedBody 均标记 `@Value.Redacted`，防止请求体（可能含敏感凭据）泄露到日志。

#### core/src/test/java/org/apache/iceberg/rest/TestHTTPHeaders.java（新增）

测试 `HTTPHeaders` 的行为契约：
- `entries()`：验证条目获取及重复条目去重（Immutables Set 语义）。
- `entriesByName()`：验证按名称查询的大小写不敏感性，以及 null 安全。
- `contains()`：验证包含判断的大小写不敏感与 null 安全。
- `putIfAbsentHTTPHeader()` / `putIfAbsentHTTPHeaders()`：验证"不存在才添加"语义——若同名头部已存在则返回原实例（`isSameAs`），否则返回含新头部的新实例；并验证 null 参数抛 `NullPointerException`。
- `invalidHeader()`：验证 null name/value 抛 NPE、空 name 抛 `IllegalArgumentException`。

#### core/src/test/java/org/apache/iceberg/rest/TestHTTPRequest.java（新增）

测试 `HTTPRequest` 的行为契约：
- `requestUriSuccess()`：参数化测试，验证完整 URI 构造——包括路径末尾斜杠去除、绝对路径（http/https）直接使用、查询参数拼接。
- `malformedPath()`：验证以 `/` 开头的路径抛 `RESTException`。
- `invalidPath()`：验证非法路径构造 URI 失败时抛 `RESTException`。
- `encodedBodyJSON()`：验证对象体被序列化为 JSON。
- `encodedBodyJSONInvalid()`：用 Mockito 模拟 ObjectMapper 抛异常，验证包装为 `RESTException`。
- `encodedBodyFormData()`：验证 Map 体被编码为 URL 编码的表单数据（OAuth token 交换格式）。
- `encodedBodyFormDataNullKeysAndValues()`：验证 Map 中 null 键值被编码为字符串 "null"。
- `encodedBodyNull()`：验证无 body 时 `encodedBody()` 返回 null。

## 小结

本提交作为 Auth Manager API 的第一部分，引入了 `HTTPHeaders` 和 `HTTPRequest` 两个不可变值对象，为 REST 客户端提供了与具体 HTTP 客户端实现解耦的请求表示模型。其设计要点包括：大小写不敏感的头部处理（符合 RFC 2616）、`putIfAbsent` 语义支持安全的头部注入、敏感字段 Redacted 防止日志泄露、Lazy 计算的 URI 构造与 body 编码、以及完善的校验逻辑。配套测试覆盖了正常路径与各种边界/异常场景。这为后续 Auth Manager 拦截并修改请求（如注入认证令牌）奠定了类型安全的基础设施。
