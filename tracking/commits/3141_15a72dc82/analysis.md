# 提交 3141：Core: Implement register view for REST catalog (#14870)

## 提交信息

- **序号**：3141 / 4088
- **哈希**：15a72dc829844a4ca2f004139c9acc5f1a922578
- **短哈希**：15a72dc82
- **日期**：2026-01-22
- **作者**：Ajantha Bhat
- **提交说明**：Core: Implement register view for REST catalog (#14870)
- **PR/Issue**：#14870

## 总体目的

Iceberg 的 REST catalog 此前已支持表（table）的 `registerTable`——即通过一个已存在的 metadata 文件位置把表“注册”进 catalog，而不创建新的表版本。但视图（view）侧只有 `createView`，缺少对应的 `registerView` 能力。这意味着在跨 catalog 迁移视图、或基于已落地的视图 metadata 文件做元数据级注册（不重新创建视图版本）时，REST catalog 无法胜任；`ViewCatalogTests` 中的 `registerView`、`registerExistingView`、`registerViewThatAlreadyExistsAsTable` 三个测试用例也因此被显式跳过（`assumeThat(catalog).isNotInstanceOf(RESTCatalog.class)`），说明 REST catalog 缺失该能力是已知缺口。

本提交为 REST catalog 补齐 `registerView` 全链路：在 `ViewSessionCatalog` 接口新增 `registerView` 默认方法（默认抛 `UnsupportedOperationException`），在 `BaseViewSessionCatalog` 的 session 包装器中委托到外层实现，在服务端 `CatalogHandlers` 新增 `registerView` 处理器，新增 REST 端点 `V1_REGISTER_VIEW`（POST `/v1/{prefix}/namespaces/{namespace}/register-view`）与 `ResourcePaths.registerView(Namespace)`，在客户端 `RESTSessionCatalog` 与 `RESTCatalog` 实现远程调用，并在测试适配器（`RESTCatalogAdapter`/`Route`）、`TestResourcePaths`、`ViewCatalogTests`、`TestRESTViewCatalogWithAssumedViewSupport` 中补齐路由、路径与端到端测试，同时移除此前对 REST catalog 的跳过假设，使 REST catalog 与表侧能力对齐。

## 如何达成设计目的

整体沿“接口默认方法 → session 基类委托 → 服务端处理器 → 端点/资源路径 → 客户端实现 → 测试适配”的分层顺序，把 register-view 作为 REST 协议的一等端点接入。设计上与既有 `registerTable` 端点对称：请求体为 `RegisterViewRequest`（含 `name` 与 `metadataLocation`），响应为 `LoadViewResponse`（含 `metadata` 与 `metadataLocation`），客户端拿到响应后用返回的 metadata 构造 `RESTViewOperations` 与 `BaseView` 返回。同时通过 `Endpoint.check` 机制保证服务端未声明该端点时客户端会给出清晰错误，便于老服务端与新客户端共存。

## 修改详情

### `api/src/main/java/org/apache/iceberg/catalog/ViewSessionCatalog.java` (+15/-0 lines)

**修改目的**：在 view session catalog 接口定义 `registerView` 契约。

**工作逻辑**：
新增带 `SessionContext` 的 default 方法 `registerView(SessionContext context, TableIdentifier ident, String metadataFileLocation)`，默认抛 `UnsupportedOperationException("Registering views is not supported")`。Javadoc 说明：当同名表/视图已存在时抛 `AlreadyExistsException`。default 实现保证不支持的 catalog 仍可编译与运行，仅在该能力被调用时报错。

### `core/src/main/java/org/apache/iceberg/catalog/BaseViewSessionCatalog.java` (+5/-0 lines)

**修改目的**：让 session 包装器把 `registerView` 委托到外层 catalog。

**工作逻辑**：
内部 `SessionViewCatalog`（带 `SessionContext` 的视图）新增 `registerView(TableIdentifier, String)` override，直接调用 `BaseViewSessionCatalog.this.registerView(context, identifier, metadataFileLocation)`，与既有 `invalidateView` 等方法的委托模式一致。

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+13/-0 lines)

**修改目的**：服务端 register-view 处理器。

**工作逻辑**：
新增静态方法 `registerView(ViewCatalog catalog, Namespace namespace, RegisterViewRequest request)`：先 `request.validate()`，用 `TableIdentifier.of(namespace, request.name())` 构造标识，调用 `catalog.registerView(identifier, request.metadataLocation())` 完成注册，再用 `ImmutableLoadViewResponse.builder()` 组装响应——`metadata` 取自 `asBaseView(view).operations().current()`（当前 view metadata），`metadataLocation` 取 `request.metadataLocation()`。这与表侧 `registerTable` 处理器结构对称。

### `core/src/main/java/org/apache/iceberg/rest/Endpoint.java` (+2/-0 lines)

**修改目的**：声明 register-view REST 端点。

**工作逻辑**：
新增常量 `V1_REGISTER_VIEW = Endpoint.create("POST", ResourcePaths.V1_VIEW_REGISTER)`，供客户端 `Endpoint.check` 校验服务端能力，并在端点协商中被纳入。

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java` (+5/-0 lines)

**修改目的**：定义 register-view 的 URL 路径模板与构造方法。

**工作逻辑**：
新增常量 `V1_VIEW_REGISTER = "/v1/{prefix}/namespaces/{namespace}/register-view"`（与 `register-table` 路径风格一致），以及实例方法 `registerView(Namespace ns)` 返回 `"v1/{prefix}/namespaces/{pathEncode(ns)}/register-view"`。客户端据此拼装请求 URL。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+45/-0 lines)

**修改目的**：客户端 register-view 远程调用实现。

**工作逻辑**：
- 新增 import `Strings`、`ImmutableRegisterViewRequest`、`RegisterViewRequest`。
- 实现 `registerView(SessionContext, TableIdentifier ident, String metadataFileLocation)`：①`Endpoint.check(endpoints, Endpoint.V1_REGISTER_VIEW)` 确保服务端声明该端点，否则抛含端点描述的 `UnsupportedOperationException`；②`checkViewIdentifierIsValid(ident)`；③`Preconditions.checkArgument(!Strings.isNullOrEmpty(metadataFileLocation), ...)`；④用 `ImmutableRegisterViewRequest.builder().name(ident.name()).metadataLocation(metadataFileLocation).build()` 构造请求；⑤获取 `contextualSession`，`client.withAuthSession(...).post(paths.registerView(ident.namespace()), request, LoadViewResponse.class, mutationHeaders, ErrorHandlers.viewErrorHandler())` 发起 POST；⑥用响应 config 与 contextualSession 构造 `tableSession`，再构造 `RESTViewOperations`（经 `newViewOps`），最终返回 `new BaseView(ops, ViewUtil.fullViewName(name(), ident))`。整体流程与 `createView` 的客户端实现平行，差异在于走 register 端点、请求体为 `RegisterViewRequest`。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalog.java` (+5/-0 lines)

**修改目的**：让非 session 的 `RESTCatalog`（面向 `ViewCatalog` 接口）暴露 `registerView`。

**工作逻辑**：
新增 override `registerView(TableIdentifier identifier, String metadataFileLocation)`，直接委托 `viewSessionCatalog.registerView(identifier, metadataFileLocation)`（用默认 session context），使 `RESTCatalog` 作为 `ViewCatalog` 也具备注册能力。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+12/-0 lines)

**修改目的**：测试用 HTTP 适配器接入 register-view 路由。

**工作逻辑**：
新增 import `RegisterViewRequest`；在请求分发 switch 中新增 `case REGISTER_VIEW`：当底层 catalog 是 view catalog 时，从路径变量解析 `namespace`，把 body cast 为 `RegisterViewRequest`，调用 `CatalogHandlers.registerView(asViewCatalog, namespace, request)` 并 cast 返回。使基于 `RESTCatalogAdapter` 的本地端到端测试能命中该端点。

### `core/src/test/java/org/apache/iceberg/rest/Route.java` (+6/-0 lines)

**修改目的**：定义测试路由枚举 `REGISTER_VIEW`。

**工作逻辑**：
新增枚举常量 `REGISTER_VIEW(HTTPRequest.HTTPMethod.POST, ResourcePaths.V1_VIEW_REGISTER, RegisterViewRequest.class, LoadViewResponse.class)`，声明方法、路径、请求体与响应体类型，供测试基础设施使用。

### `core/src/test/java/org/apache/iceberg/rest/TestResourcePaths.java` (+7/-0 lines)

**修改目的**：验证 `registerView(Namespace)` 路径构造。

**工作逻辑**：
新增 `testRegisterView`：断言带前缀时为 `v1/ws/catalog/namespaces/ns/register-view`，不带前缀时为 `v1/namespaces/ns/register-view`，确保前缀与 namespace 编码正确。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java` (+3/-14 lines)

**修改目的**：移除 REST catalog 跳过假设，使三个 register-view 用例对 REST catalog 生效。

**工作逻辑**：
- 删除 `registerView`、`registerExistingView`、`registerViewThatAlreadyExistsAsTable` 三处 `assumeThat(catalog).as("Registering a view is not yet supported for the REST catalog").isNotInstanceOf(RESTCatalog.class);`，并移除随之失效的 `import org.apache.iceberg.rest.RESTCatalog;`。
- 把原先直接强转 `(BaseViewOperations) ops` 的断言改为 `if (ops instanceof BaseViewOperations) { assertThat(((BaseViewOperations) ops).io().newInputFile(metadataLocation).exists()).isTrue(); }`，因为 REST catalog 的 view ops 是 `RESTViewOperations` 而非 `BaseViewOperations`，直接强转会抛 `ClassCastException`。该改动保留了“view metadata 文件在 drop 后仍存在（gc 禁用）”的断言语义，仅在 ops 类型匹配时执行。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalogWithAssumedViewSupport.java` (+29/-0 lines)

**修改目的**：验证旧客户端/不支持该端点的服务端会得到清晰错误。

**工作逻辑**：
override 三个 register-view 测试：在“假定视图支持”的测试场景下（模拟旧客户端不携带新端点），断言 `super::registerView` 等抛 `UnsupportedOperationException`，且消息以 `"Server does not support endpoint: POST /v1/{prefix}/namespaces/{namespace}/register-view"` 开头。这验证了 `Endpoint.check` 的端点协商机制对新端点生效，保证向后兼容的失败行为可预期。

## 总结

该提交为 REST catalog 补齐了与表侧对齐的视图注册能力，沿接口、session 基类、服务端处理器、端点/路径、客户端实现、测试适配的完整链路接入 `registerView`，使视图可基于已有 metadata 文件做元数据级注册；同时移除测试中针对 REST catalog 的跳过假设、修复 view ops 类型强转、并新增端点协商失败的兼容性测试，整体提升了 REST catalog 在视图管理与跨 catalog 迁移场景下的完备性。
