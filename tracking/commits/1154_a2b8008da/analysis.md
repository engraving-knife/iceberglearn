# 提交 1154：Core: Allow servers to express supported endpoints via endpoint field in ConfigResponse (#10929)

## 提交信息

- **序号**：1154 / 4088
- **哈希**：a2b8008da7bc26e03248a35eeee60d1cc7e8499d
- **短哈希**：a2b8008da
- **日期**：2024-09-13（Fri Sep 13 18:04:14 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Allow servers to express supported endpoints via endpoint field in ConfigResponse (#10929)
- **PR/Issue**：#10929

## 总体目的

Iceberg REST Catalog 协议此前并未让服务器明确告诉客户端"我支持哪些 REST 端点"。客户端只能盲发请求，再通过 `UnsupportedOperationException` / `RESTException` 兜底判断（例如 `loadView` 中 try/catch `UnsupportedOperationException` 转为 `NoSuchViewException`），既不优雅也容易把 401/403 等授权错误误判成"view 不存在"。

本提交在 REST 协议层面引入 `Endpoint` 概念，并通过 `ConfigResponse.endpoints` 字段让服务器在 `/v1/config` 阶段就向客户端声明自己支持的全部 endpoint（HTTP method + 资源路径）。客户端在初始化时拿到该集合后缓存到 `RESTSessionCatalog.endpoints`，后续每次调用具体方法前用 `Endpoint.check(...)` 做前置校验：不支持就直接抛 `UnsupportedOperationException`（或自定义异常），避免无效的网络往返，也消除了"用 try/catch 区分 view 是否支持"的歧义路径。

对于不发 `endpoints` 字段的旧服务器，引入 `view-endpoints-supported` 客户端配置属性作为向后兼容开关；如果设为 `true`，则视为旧服务器也支持 view 端点（保持旧行为）。

## 如何达成设计目的

1. 新增 `Endpoint` 不可变值类型：封装 `(httpMethod, path)`，提供 `create` / `fromString` / `toString` / `equals` / `hashCode` / `check(supported, endpoint[, supplier])` 等方法，并预定义一组 `V1_*` 静态常量（namespace / table / view 三大类共 21 个端点）。
2. 在 `ResourcePaths` 中把所有 REST 资源路径（如 `/v1/{prefix}/namespaces/{namespace}` 等）抽为 `public static final String` 常量，供 `Endpoint.V1_*` 与测试侧 `RESTCatalogAdapter.Route` 共用，避免硬编码重复。
3. 在 `ConfigResponse` 中新增 `endpoints` 字段（`List<Endpoint>`）与 Builder 的 `withEndpoints` 方法；`ConfigResponseParser` 序列化时把 `endpoints` 写为字符串数组（每项 `"METHOD /path"`），反序列化时按 `" "` 拆分还原。
4. 在 `RESTSessionCatalog.initialize` 中读 `config.endpoints()`：非空则直接采用；为空（旧服务器）则按 `view-endpoints-supported` 属性决定是否合并 `VIEW_ENDPOINTS` 到 `DEFAULT_ENDPOINTS`，结果存入 `this.endpoints`。
5. 在 `RESTSessionCatalog` 的每个对外方法入口（`listTables` / `loadTable` / `dropTable` / `renameTable` / `registerTable` / `createTable` / `createTransaction` / `replaceTransaction` / `commitTransaction` / `listNamespaces` / `loadNamespaceMetadata` / `dropNamespace` / `updateNamespaceMetadata` / `createNamespace` / `listViews` / `loadView` / `dropView` / `renameView` / `createView` / `replaceView` 等）以及 `RESTTableOperations.refresh` / `commit`、`RESTViewOperations.refresh` / `commit` 中插入 `Endpoint.check(...)`；对于 `loadTable` / `loadView` 还提供自定义 `Supplier<RuntimeException>` 让其抛 `NoSuchTableException` / `NoSuchViewException` 而不是默认的 `UnsupportedOperationException`，保持 API 语义。
6. `RESTTableOperations` / `RESTViewOperations` 构造函数追加 `Set<Endpoint> endpoints` 参数，所有 `RESTSessionCatalog` 中创建 ops 的调用点同步更新；同时在 ops 内的 `refresh` / `commit` 也做 `Endpoint.check`，覆盖 ops 路径（如引擎通过 `table.refresh()` 触发的请求）。
7. 改造测试侧 `RESTCatalogAdapter`：把 `Route` 枚举里的路径全部改为引用 `ResourcePaths.V1_*` 常量；并在 `Route` 枚举构造里新增 `resourcePath` 字段保存原始 pattern；`CONFIG` 路由的响应里现在通过 `Arrays.stream(Route.values()).map(r -> Endpoint.create(r.method.name(), r.resourcePath))...` 返回所有 endpoint，让基于 `RESTCatalogAdapter` 的测试服务器也声明完整端点支持。同时把 `identFromPathVars` 拆为 `tableIdentFromPathVars`（取 `table` 字段）与 `viewIdentFromPathVars`（取 `view` 字段），因为新路径模板把变量名从通用的 `name` 改成了具体的 `table` / `view`。
8. 新增 `TestEndpoint` 单元测试覆盖 `Endpoint` 的校验、序列化、`check` 行为；在 `TestConfigResponseParser` 中新增 `endpointsOnly` / `invalidEndpoint` / `roundTripSerdeWithEndpoints` 三个用例覆盖 JSON 往返。
9. 将 `TestRESTViewCatalog` 中字段改为 `protected` 以便子类复用；新增 `TestRESTViewCatalogWithAssumedViewSupport` 子类，模拟旧服务器（不发 `endpoints` 字段）+ 客户端配置 `view-endpoints-supported=true` 的场景，复用父类全部 view 测试用例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/Endpoint.java`（新增）

**修改目的**：定义 endpoint 模型与校验入口。

**工作逻辑**：
- 21 个 `public static final Endpoint V1_*` 常量：覆盖 namespace（6 个）、table（7 个）、view（6 个）。每个常量通过 `Endpoint.create(method, ResourcePaths.V1_*)` 构造。
- 构造函数私有，强制走 `create(...)` 工厂；构造时用 `Method.normalizedValueOf(httpMethod)`（来自 Apache HttpClient Core5）规范化方法名（如 `"PuT"` → `"PUT"`），并校验 method 与 path 都非空。
- `toString()` / `fromString(String)` 用 `" "`（单个空格）作为分隔符；`fromString` 严格要求恰好 2 段，否则抛 `IllegalArgumentException`。
- `check(Set<Endpoint>, Endpoint)`：若 set 不包含则抛 `UnsupportedOperationException`，message 为 `"Server does not support endpoint: %s"`。
- `check(Set<Endpoint>, Endpoint, Supplier<RuntimeException>)`：重载版本，允许调用方提供自定义异常（如 `NoSuchTableException`、`NoSuchViewException`）。
- 标准 `equals` / `hashCode`，按 `(httpMethod, path)` 比较。

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java`

**修改目的**：把所有 REST 资源路径抽为公共常量。

**工作逻辑**：新增 12 个 `public static final String`：`V1_NAMESPACES`、`V1_NAMESPACE`、`V1_NAMESPACE_PROPERTIES`、`V1_TABLES`、`V1_TABLE`、`V1_TABLE_REGISTER`、`V1_TABLE_METRICS`、`V1_TABLE_RENAME`、`V1_TRANSACTIONS_COMMIT`、`V1_VIEWS`、`V1_VIEW`、`V1_VIEW_RENAME`。这些常量同时被 `Endpoint.V1_*` 与 `RESTCatalogAdapter.Route` 引用，保证 server / client 侧路径定义一致。

### `core/src/main/java/org/apache/iceberg/rest/responses/ConfigResponse.java`

**修改目的**：扩展 ConfigResponse 增加 `endpoints` 字段。

**工作逻辑**：
- 新增 `private List<Endpoint> endpoints;` 字段。
- 私有构造函数追加 `List<Endpoint> endpoints` 参数并赋值。
- 新增 `public List<Endpoint> endpoints()` getter，null 安全返回 `ImmutableList.of()`。
- `toString` 中追加 `"endpoints"` 字段。
- `Builder` 新增 `List<Endpoint> endpoints` 字段与 `withEndpoints(List<Endpoint>)` 方法（追加而非替换）；`build()` 调用新构造函数。

### `core/src/main/java/org/apache/iceberg/rest/responses/ConfigResponseParser.java`

**修改目的**：实现 endpoints 字段的 JSON 序列化与反序列化。

**工作逻辑**：
- 新增常量 `ENDPOINTS = "endpoints"`。
- `toJson` 中：若 `response.endpoints()` 非空，则用 `JsonUtil.writeStringArray(ENDPOINTS, endpoints.stream().map(Endpoint::toString).collect(toList()), gen)` 写出。每个 endpoint 序列化为 `"GET /v1/..."` 形式的字符串。
- `fromJson` 中：若 json 含 `ENDPOINTS` 且非 null，用 `JsonUtil.getStringList` 取出字符串列表，再 `stream().map(Endpoint::fromString).collect(toList())` 还原为 `Endpoint` 对象列表。
- 如果服务器未发送该字段，则不会进入此分支，`endpoints` 保持为空 list，由 `RESTSessionCatalog` 走向后兼容路径。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在客户端侧缓存并使用 endpoint 集合做前置校验。

**工作逻辑**：
- 删除 `import org.apache.iceberg.exceptions.RESTException;`（不再用 try/catch 兜底）。
- 新增常量 `VIEW_ENDPOINTS_SUPPORTED = "view-endpoints-supported"`（package-private，便于测试引用），并加注释说明用于"旧服务器不发 endpoints 字段但客户端想声明它支持 view"的兼容场景。
- 新增两个静态集合：`DEFAULT_ENDPOINTS`（13 个 table/namespace 端点）与 `VIEW_ENDPOINTS`（6 个 view 端点）。
- 新增实例字段 `private Set<Endpoint> endpoints;`。
- `initialize` 方法中：在拿到 `mergedProps` 后新增分支：若 `config.endpoints()` 为空，则按 `VIEW_ENDPOINTS_SUPPORTED` 属性决定是否把 `VIEW_ENDPOINTS` 合入 `DEFAULT_ENDPOINTS`，否则直接采用 `config.endpoints()`；结果赋给 `this.endpoints`。`initialize` 同时被加上 `@SuppressWarnings("checkstyle:CyclomaticComplexity")`。
- 在所有对外方法入口插入校验：
  - `listTables` / `listNamespaces` / `listViews`：用 `if (!endpoints.contains(Endpoint.V1_LIST_*)) return ImmutableList.of();`（列表类返回空列表，不抛异常，符合"无权限/不支持即无内容"的语义）。
  - `loadTable` / `loadView`：用 `Endpoint.check(endpoints, Endpoint.V1_LOAD_*, () -> new NoSuch*Exception(...))`，抛对应"不存在"异常以便引擎语义一致。
  - `dropTable` / `purgeTable` / `renameTable` / `registerTable` / `createNamespace` / `loadNamespaceMetadata` / `dropNamespace` / `updateNamespaceMetadata` / `dropView` / `renameView` / `commitTransaction`：用 `Endpoint.check(endpoints, Endpoint.V1_*)`，抛默认 `UnsupportedOperationException`。
  - 内部 `TableBuilder` / `ViewBuilder` 的 `create()` / `createTransaction()` / `replaceTransaction()` / `replace()` 等也加入对应 `V1_CREATE_TABLE` / `V1_UPDATE_TABLE` / `V1_CREATE_VIEW` / `V1_UPDATE_VIEW` 校验。
  - `loadInternal`：用 `Endpoint.check(endpoints, Endpoint.V1_LOAD_TABLE)`。
  - `metricsReporter`：除原本的 `reportingViaRestEnabled` 外，再加 `&& endpoints.contains(Endpoint.V1_REPORT_METRICS)`，避免向不支持的 server 发 metrics。
- 删除 `replaceTransaction` 中原本对 `viewExists` 的 try/catch（捕获 `RESTException | UnsupportedOperationException`）—现在 endpoint 集合已经明确，可以直接调 `viewExists`，不再需要兜底。
- 删除 `loadView` 中原本对 `client.get` 的 try/catch（捕获 `UnsupportedOperationException | RESTException` 转 `NoSuchViewException`）—现在前置 `Endpoint.check` 已经用 `NoSuchViewException` 表达"不支持"；HTTP 错误由 `ErrorHandlers.viewErrorHandler()` 处理。
- 所有创建 `RESTTableOperations` / `RESTViewOperations` 的调用点追加 `endpoints` 参数。

### `core/src/main/java/org/apache/iceberg/rest/RESTTableOperations.java`

**修改目的**：让 ops 内部的 `refresh` / `commit` 也走 endpoint 校验。

**工作逻辑**：
- 新增 `private final Set<Endpoint> endpoints;` 字段。
- 两个构造函数都追加 `Set<Endpoint> endpoints` 参数并赋值。
- `refresh()` 入口加 `Endpoint.check(endpoints, Endpoint.V1_LOAD_TABLE);`。
- `commit(...)` 入口加 `Endpoint.check(endpoints, Endpoint.V1_UPDATE_TABLE);`。

### `core/src/main/java/org/apache/iceberg/rest/RESTViewOperations.java`

**修改目的**：同上，对 view ops 做校验。

**工作逻辑**：
- 新增 `endpoints` 字段，构造函数追加该参数。
- `refresh()` 入口加 `Endpoint.check(endpoints, Endpoint.V1_LOAD_VIEW);`。
- `commit(...)` 入口加 `Endpoint.check(endpoints, Endpoint.V1_UPDATE_VIEW);`。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`

**修改目的**：让测试用的 in-process REST server 也声明支持的 endpoints，并修正路径变量名。

**工作逻辑**：
- `Route` 枚举的所有路径常量从硬编码字符串（如 `"v1/namespaces/{namespace}/tables/{name}"`）改为引用 `ResourcePaths.V1_*`，与生产代码统一。
- 新增 `resourcePath` 字段保存原始 pattern（因为 `ResourcePaths.V1_*` 含 `{prefix}` 段，而 `RESTCatalogAdapter` 的路由匹配需要把 `{prefix}` 段去掉；解析时通过 `pattern.replaceFirst("/v1/", "v1/").replace("/{prefix}", "")` 转换）。
- 新增 `method()` / `resourcePath()` getter，便于 CONFIG 路由枚举所有 endpoint。
- CONFIG 路由响应：从原本返回空 `ConfigResponse.builder().build()` 改为 `ConfigResponse.builder().withEndpoints(Arrays.stream(Route.values()).map(r -> Endpoint.create(r.method.name(), r.resourcePath)).collect(toList())).build()`，让客户端能拿到完整 endpoint 集合。
- 把原 `identFromPathVars` 拆为 `tableIdentFromPathVars`（取 `table` 变量）与 `viewIdentFromPathVars`（取 `view` 变量），对应新路径模板里 `{table}` / `{view}` 占位符；所有调用点同步更新。

### `core/src/test/java/org/apache/iceberg/rest/TestEndpoint.java`（新增）

**修改目的**：覆盖 `Endpoint` 类自身的契约。

**工作逻辑**：
- `invalidValues`：断言 `create(null, ...)` / `create("", ...)` / `create("invalid", "/")` 抛 `IllegalArgumentException`，分别校验 method 空、path 空、method 不是合法 HTTP 方法。
- `invalidFromString`（参数化）：覆盖 `"/path"`（缺 method）、`" GET /path"`（前导空格）、`"GET /path "`（末尾空格）、`"GET  /path"`（多个空格）、`"GET /path /other"`（多于两段）。
- `validFromString`：`"GET /path"` 还原为 method=`GET`、path=`/path`。
- `toStringRepresentation`：覆盖普通路径、根路径、大小写不敏感 method（`PuT` → `PUT`）、带占位符的路径。
- `supportedEndpoints` / `unsupportedEndpoints`：覆盖 `Endpoint.check` 的两种行为。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestConfigResponseParser.java`

**修改目的**：覆盖 endpoints 字段的 JSON 序列化往返。

**工作逻辑**：
- `endpointsOnly`：仅有 endpoints 字段时的 JSON 形态。
- `invalidEndpoint`：`"GET_v1/..."`（缺空格）和 `"GET v1/... INVALID"`（多于两段）都抛 `IllegalArgumentException`。
- `roundTripSerdeWithEndpoints`：与原有 `roundTripSerde` 类似但叠加了 endpoints 字段，验证完整 round-trip。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java`

**修改目的**：把字段改为 `protected` 以便子类继承复用。

**工作逻辑**：`temp` / `restCatalog` / `backendCatalog` / `httpServer` 从 `private` 改为 `protected`，让 `TestRESTViewCatalogWithAssumedViewSupport` 能在 `@BeforeEach` 中重新赋值。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalogWithAssumedViewSupport.java`（新增）

**修改目的**：覆盖"旧服务器 + 客户端声明 view-endpoints-supported=true"的兼容路径。

**工作逻辑**：继承 `TestRESTViewCatalog`，重写 `createCatalog()`：
- 自定义 `RESTCatalogAdapter.handleRequest`：当 route 是 `CONFIG` 时返回 `ConfigResponse.builder().build()`（即不发 endpoints 字段，模拟旧服务器）。
- 客户端初始化时显式设置 `RESTSessionCatalog.VIEW_ENDPOINTS_SUPPORTED=true`。
- 这样 `RESTSessionCatalog.initialize` 走"endpoints 为空 + view-endpoints-supported=true"分支，把 `DEFAULT_ENDPOINTS + VIEW_ENDPOINTS` 作为支持集合；随后复用父类全部 view 测试用例，验证该兼容路径下所有 view 操作仍可正常工作。

## 小结

- **成效**：REST Catalog 客户端现在能精确知道服务器支持哪些端点，避免无效请求与模糊的 try/catch 兜底；服务器可通过 `endpoints` 字段显式声明，旧服务器则通过客户端 `view-endpoints-supported` 属性保持兼容。错误语义更清晰：不支持 loadTable/loadView 时直接抛 `NoSuchTableException` / `NoSuchViewException`，与"表/视图不存在"语义一致。
- **影响范围**：核心 `core` 模块的 REST 子系统。新增 `Endpoint` 类、`ResourcePaths` 常量、`ConfigResponse.endpoints` 字段与 parser；`RESTSessionCatalog` / `RESTTableOperations` / `RESTViewOperations` 增加 endpoint 校验。属于 REST 协议层面的增量字段，对未发送 `endpoints` 的旧服务器保持完全向后兼容。
- **回迁到 1.4.x 的注意事项**：
  1. 这是 REST Catalog 协议的增量增强，回迁到 1.4.x 是有价值的（可让 1.4.x 客户端也能消费新服务器的 `endpoints` 字段）。
  2. 必须整体回迁：`Endpoint` + `ResourcePaths` 常量 + `ConfigResponse`/`ConfigResponseParser` + `RESTSessionCatalog` + `RESTTableOperations` + `RESTViewOperations` + 测试侧 `RESTCatalogAdapter` 路径常量与变量名拆分（`tableIdentFromPathVars` / `viewIdentFromPathVars`），缺一不可，否则编译失败或测试失败。
  3. 1.4.x 若已有对 `RESTCatalogAdapter` 的自定义测试，需要注意 `Route` 路径变量名从 `name` 改为 `table` / `view`，可能影响其他依赖该 adapter 的测试。
  4. 协议层面前向兼容：未升级的旧客户端遇到新服务器下发的 `endpoints` 字段会忽略（Jackson 反序列化未知字段默认忽略），不影响功能；新客户端遇到旧服务器（无 `endpoints` 字段）会走 `view-endpoints-supported` 兼容路径，默认行为与 1.4.x 之前一致。
  5. 该改动不改变表元数据格式，不影响非 REST catalog（HiveCatalog / JdbcCatalog 等），无数据迁移风险。
