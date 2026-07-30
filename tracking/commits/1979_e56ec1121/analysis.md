# 提交 1979：Core: Allow HTTPClient to parse headers from properties (#12595)

## 提交信息

- **序号**：1979 / 4088
- **哈希**：e56ec11215e7b9fec6ffd1ea435e008852454db0
- **短哈希**：e56ec1121
- **日期**：2025-04-09 23:12:10 +0200
- **作者**：Juichang Lu
- **提交说明**：Core: Allow HTTPClient to parse headers from properties (#12595)
- **PR/Issue**：#12595

## 总体目的

本提交让 REST Catalog 的默认 `HTTPClient` 构建过程能够从配置属性中解析并以 `header.` 前缀开头的自定义 HTTP 头，将其作为客户端的 base headers 一并发出。

在此之前，`RESTCatalog` 与 `RESTSessionCatalog` 的无参构造函数所使用的默认 client builder 只设置了 URI，并未调用 `withHeaders(...)`。这意味着用户通过 `header.X=Y` 属性配置的自定义请求头不会被注入到 HTTPClient 的基础头中，只有在 `RESTSessionCatalog` 内部发起 config 请求时才会用到一个私有的 `configHeaders` 方法提取这些头。这导致：用户期望对所有 REST 请求都附加的自定义头（如鉴权、追踪、代理头等）在默认构造路径下实际不会生效，需要自定义 client builder 才能实现，体验不一致且不直观。

本提交将 `configHeaders` 逻辑提取为 `RESTUtil.configHeaders` 公共方法，并在两个 Catalog 的默认无参构造的 client builder 中调用 `withHeaders(RESTUtil.configHeaders(config))`，使 `header.` 前缀属性在默认路径下也能被正确解析为 base headers，对所有请求生效。

## 如何达成设计目的

设计思路是统一 header 解析入口并在默认 client 构建时应用：

1. **提取公共方法**：将原 `RESTSessionCatalog` 私有的 `configHeaders(Map)`（实质是 `RESTUtil.extractPrefixMap(properties, "header.")`）迁移到 `RESTUtil` 作为公共静态方法 `configHeaders`。
2. **默认构造应用 headers**：在 `RESTCatalog()` 与 `RESTSessionCatalog()` 无参构造的 client builder lambda 中，于 `.uri(...)` 之后追加 `.withHeaders(RESTUtil.configHeaders(config))`，使从属性解析出的头成为 HTTPClient 的 base headers。
3. **替换调用点**：`RESTSessionCatalog` 内部发起 config 请求处由调用本地 `configHeaders(properties)` 改为调用 `RESTUtil.configHeaders(properties)`，并删除私有方法，避免重复。
4. **测试**：新增 `testDefaultHeadersPropagated` 验证通过无参构造 + `header.` 属性初始化后，自定义头确实进入 client 的 baseHeaders；并更新既有测试在属性与 client builder 中加入 `header.test-header` 以覆盖该路径。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalog.java` (修改, +5/-1 lines)

**修改目的**：默认无参构造的 client builder 解析并应用 header 属性。

**工作逻辑**：`RESTCatalog()` 无参构造中，client builder lambda 由 `HTTPClient.builder(config).uri(config.get(CatalogProperties.URI)).build()` 改为追加 `.withHeaders(RESTUtil.configHeaders(config))`，将 `header.` 前缀属性转为 base headers。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +9/-5 lines)

**修改目的**：默认构造应用 headers，并复用公共方法。

**工作逻辑**：
- `RESTSessionCatalog()` 无参构造的 client builder lambda 同样追加 `.withHeaders(RESTUtil.configHeaders(config))`。
- 发起 config 请求处 `configHeaders(properties)` 改为 `RESTUtil.configHeaders(properties)`。
- 删除私有静态方法 `configHeaders(Map)`（逻辑已迁移到 `RESTUtil`）。

### `core/src/main/java/org/apache/iceberg/rest/RESTUtil.java` (修改, +4/-0 lines)

**修改目的**：提供公共的 header 属性解析方法。

**工作逻辑**：新增 `public static Map<String, String> configHeaders(Map<String, String> properties)`，实现为 `return RESTUtil.extractPrefixMap(properties, "header.");`，即提取所有以 `header.` 开头的属性键（去掉前缀）及其值。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (修改, +40/-5 lines)

**修改目的**：验证 header 属性在默认与自定义 client builder 路径下均能传播。

**工作逻辑**：
- 新增 `testDefaultHeadersPropagated`：用无参 `new RESTCatalog()`，配置 `header.test-header` 与 `header.test-header2` 属性初始化，通过反射式断言（`extracting("sessionCatalog.client.baseHeaders")`）验证 `test-header2` 等头已进入 client baseHeaders。
- 既有测试中给 catalogHeaders/contextHeaders 增加 `test-header`，并在 properties 与自定义 client builder 中加入 `header.test-header` 及 `withHeaders(RESTUtil.configHeaders(config))`，覆盖自定义 builder 路径。

## 总结

本提交让 REST Catalog 默认的 HTTPClient 构建能够从 `header.` 前缀属性解析自定义 HTTP 头并作为 base headers 应用于所有请求。通过将 header 解析逻辑提取为 `RESTUtil.configHeaders` 公共方法，并在 `RESTCatalog`/`RESTSessionCatalog` 无参构造的 client builder 中调用 `withHeaders`，统一了默认与自定义路径的 header 处理，新增测试覆盖默认构造下的头传播。
