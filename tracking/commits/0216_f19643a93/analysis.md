# 提交 0216：Core: Add View support for REST catalog (#7913)

## 提交信息

- **序号**：0216 / 4088
- **哈希**：f19643a93f5dac99bbdbc9881ef19c89d7bcd3eb
- **短哈希**：f19643a93
- **日期**：2023-12-05
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add View support for REST catalog (#7913)
- **PR/Issue**：#7913

## 总体目的

Iceberg 在此之前已经具备 View 的核心模型（`ViewCatalog`、`ViewMetadata`、`ViewVersion` 等 API/视图元数据抽象），也提供了面向会话的 `SessionCatalog`/`BaseSessionCatalog` 抽象，但 REST Catalog 这一最通用的远程目录实现尚未支持 View 操作。本提交把 View 的全生命周期管理（list/load/create/replace/rename/drop/invalidate）端到端地接入了 REST Catalog：在客户端侧补齐 ViewSessionCatalog 实现，在服务端侧补齐 `CatalogHandlers` 的视图处理逻辑，并把对应的 REST 资源路径、请求/响应模型、序列化器、错误处理器全部补齐，最后通过 `RESTCatalogAdapter` 把 HTTP 路由映射到 `CatalogHandlers`，使一个普通的 `ViewCatalog`（如 InMemoryCatalog）能被 REST 客户端透明访问。

这是 Iceberg 把 View 作为"一等公民"在 REST 协议层落地的大型提交（29 个文件、约 2722 行新增），与同期推进的 Spark/Flink 视图集成直接配套：只有 REST Catalog 原生支持 View，多引擎、多语言客户端才能通过统一的 OpenAPI 规范访问视图，从而真正实现"元数据层与计算引擎解耦"。提交同时更新了 `open-api/rest-catalog-open-api.yaml` 与自动生成的 `rest-catalog-open-api.py`，正式把 view 相关端点纳入 REST Catalog OpenAPI 规范，使第三方实现可据此生成客户端/服务端代码。

值得注意的是，本提交不仅新增端点，还顺手修复了 `ViewMetadata.Builder` 在 schema 引用（`LAST_ADDED`）、并发替换视图版本时的若干行为问题，并新增了 `UpdateRequirement` 对 `ViewMetadata` 的校验重载，使 REST 提交视图变更时具备与表一致的乐观并发控制（UUID 断言 + retry on `CommitFailedException`）。

## 如何达成设计目的

整体设计沿用了已有 Table 在 REST Catalog 中的分层：`ViewSessionCatalog` 接口（API 层）→ `BaseViewSessionCatalog`（按 sessionId 缓存 ViewCatalog 适配器）→ `RESTSessionCatalog`（真正的 HTTP 客户端实现，包含 `RESTViewBuilder` 和 `RESTViewOperations`）→ `RESTCatalog`（无会话外观，把所有视图操作转发给 `viewSessionCatalog`）。服务端则通过 `CatalogHandlers` 把 `ViewCatalog` 的能力封装成 REST 响应（`LoadViewResponse`/`ListTablesResponse`/`UpdateTableRequest` 复用），由 `RESTCatalogAdapter`（测试用 in-process 适配器）将 HTTP 路由分派到对应 handler。新增的 `ResourcePaths.views/view/renameView` 定义了与 table 平行但独立的 URL 命名空间，`ErrorHandlers` 增加两组 view 专用错误处理器把 HTTP 状态码映射到 `NoSuchViewException`/`AlreadyExistsException`/`CommitFailedException`/`CommitStateUnknownException`，与表的处理保持一致。

## 修改详情

### `api/src/main/java/org/apache/iceberg/catalog/ViewCatalog.java`

**修改目的**：补全 `renameView` 的 javadoc，声明当目标 namespace 不存在时抛出 `NoSuchNamespaceException`。

这是配合 REST 服务端 `renameView` 错误语义的文档化收尾，确保接口契约和实现一致。

### `api/src/main/java/org/apache/iceberg/catalog/ViewSessionCatalog.java`（新文件）

**修改目的**：引入面向会话的视图目录接口，对齐 `SessionCatalog` 的 Table 体系。

**工作逻辑**：定义 `ViewSessionCatalog` 接口，方法签名与 `ViewCatalog` 一一对应，但每个方法都额外接收 `SessionCatalog.SessionContext context` 参数，用于在 REST 多租户场景下携带 sessionId/credentials/properties。包含 `listViews/loadView/viewExists/buildView/dropView/renameView/invalidateView/initialize` 八个方法，其中 `viewExists` 与 `invalidateView` 给出默认实现（前者通过 try-catch `NoSuchViewException`，后者为空实现，供子类按需覆盖）。

### `core/src/main/java/org/apache/iceberg/catalog/BaseViewSessionCatalog.java`（新文件）

**修改目的**：为 `ViewSessionCatalog` 提供 SessionContext → ViewCatalog 的适配基类。

**工作逻辑**：继承 `BaseSessionCatalog`，实现 `ViewSessionCatalog`。内部用 Caffeine 缓存（10 分钟过期）按 `context.sessionId()` 缓存 `AsViewCatalog` 实例。内部类 `AsViewCatalog` 实现 `ViewCatalog`，每个方法把调用委托回 `BaseViewSessionCatalog.this.xxx(context, ...)`，使子类只需实现带 `SessionContext` 的方法即可同时获得无 context 版本。`initialize` 默认抛 `UnsupportedOperationException`，因为会话型 catalog 通常不重复初始化。

### `core/src/main/java/org/apache/iceberg/UpdateRequirement.java`

**修改目的**：让 `UpdateRequirement` 既能校验表元数据，也能校验视图元数据。

**工作逻辑**：在接口上加默认方法 `default void validate(ViewMetadata base)`，默认抛 `ValidationException`（说明该 requirement 不支持视图）。`AssertTableUUID` 重写该方法，比对 `base.uuid()`，不匹配则抛 `CommitFailedException`，使视图提交具备与表一致的乐观锁断言能力。这是 `RESTViewOperations.commit` 通过 `UpdateTableRequest` 提交视图变更时的关键校验。

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java`

**修改目的**：服务端视图操作入口，把 `ViewCatalog` 能力封装成 REST 响应。

**工作逻辑**：新增五个静态方法，与 table handler 平行：
- `listViews(ViewCatalog, Namespace)` → `ListTablesResponse`（复用表响应类型，因为视图标识符结构与表一致）。
- `createView(ViewCatalog, Namespace, CreateViewRequest)`：校验 request，过滤出 `SQLViewRepresentation`（其它类型当前抛 `IllegalStateException`），调用 `ViewBuilder.withQuery/withSchema/withDefaultNamespace/withDefaultCatalog/withLocation/withProperties` 后 `create()`，再通过 `asBaseView(view).operations().current()` 取出 `ViewMetadata`，包装成 `ImmutableLoadViewResponse`（含 metadata + metadataLocation）。
- `loadView(ViewCatalog, TableIdentifier)` → `LoadViewResponse`。
- `updateView(ViewCatalog, TableIdentifier, UpdateTableRequest)`：加载现有 view，调用新增的 `commit(ViewOperations, UpdateTableRequest)` 进行乐观并发提交。
- `renameView`/`dropView`：薄封装，`dropView` 在不存在时抛 `NoSuchViewException`。

私有 `commit(ViewOperations, UpdateTableRequest)` 与 table 版本几乎一致：使用 `Tasks` 重试（`COMMIT_NUM_RETRIES_DEFAULT` + 指数退避），首次用 `current()`、重试用 `refresh()`，先校验所有 `requirements`（用 `ValidationFailureException` 包装以避免被重试吞掉），再 `applyTo(metadataBuilder)` 应用 `updates`，`changes().isEmpty()` 时跳过提交。这是视图提交并发安全的核心。

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java`

**修改目的**：为视图操作提供 HTTP 错误码到 Iceberg 异常的映射。

**工作逻辑**：新增 `viewErrorHandler()` 与 `viewCommitHandler()`，分别对应 `ViewErrorHandler` 与 `ViewCommitErrorHandler`。
- `ViewErrorHandler`：404 时按 `error.type()` 区分 `NoSuchNamespaceException` 与 `NoSuchViewException`；409 抛 `AlreadyExistsException`。
- `ViewCommitErrorHandler`：404 抛 `NoSuchViewException`；409 抛 `CommitFailedException`；500/502/504 抛 `CommitStateUnknownException`（包装 `ServiceFailureException`），与 table commit 一致。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalog.java`

**修改目的**：让面向客户端的 `RESTCatalog` 实现 `ViewCatalog`，作为无会话外观暴露视图操作。

**工作逻辑**：`RESTCatalog` 现在实现 `Catalog, ViewCatalog, SupportsNamespaces`。构造时通过 `sessionCatalog.asViewCatalog(context)` 拿到 `viewSessionCatalog`，新增的 `listViews/loadView/buildView/dropView/renameView/viewExists/invalidateView` 全部转发给它。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：REST Catalog 服务端会话实现的核心扩展，把 `BaseSessionCatalog` 升级为 `BaseViewSessionCatalog` 并实现所有视图方法。

**工作逻辑**：
- 类继承由 `extends BaseSessionCatalog` 改为 `extends BaseViewSessionCatalog`，自动获得 `asViewCatalog` 缓存能力。
- `listViews`：GET `paths.views(namespace)`，使用 `namespaceErrorHandler`。
- `loadView`：GET `paths.view(identifier)`，得到 `LoadViewResponse`，用 `tableSession(response.config(), session(context))` 构造鉴权会话（与表共用 OAuth2 配置下发机制），创建 `RESTViewOperations` 并返回 `BaseView`。
- `dropView`：DELETE `paths.view(identifier)`，捕获 `NoSuchViewException` 返回 false。
- `renameView`：POST `paths.renameView()`，body 为 `RenameTableRequest`（与表共用，因结构相同）。
- 内部类 `RESTViewBuilder` 实现 `ViewBuilder`：
  - `create()`：组装 `ImmutableViewVersion`（versionId=1，summary 来自 `EnvironmentContext.get()`），POST `paths.views(namespace)` 携带 `CreateViewRequest`，返回新 `BaseView`。
  - `createOrReplace()`：try load → 失败回退 `create()`；成功则走 `replace()`。
  - `replace(LoadViewResponse)`：基于现有 metadata 计算新的 `maxVersionId + 1` 作为新版本号，`ViewMetadata.buildFrom(metadata).setCurrentVersion(viewVersion, schema)` 构造替换元数据，通过 `RESTViewOperations.commit(metadata, replacement)` 提交。
  - `loadView()` 私有方法用于 `replace` 前先拉取最新元数据。
- 新增 `checkViewIdentifierIsValid`：拒绝 namespace 为空的标识符（抛 `NoSuchViewException`），与表校验对称。

### `core/src/main/java/org/apache/iceberg/rest/RESTViewOperations.java`（新文件）

**修改目的**：视图元数据的 REST 操作句柄，对应 table 体系中的 `RESTTableOperations`。

**工作逻辑**：实现 `ViewOperations`，持有 `RESTClient`、`path`、`headers`（`Supplier<Map>`，由会话提供鉴权头）、当前 `ViewMetadata`。
- `current()` 直接返回缓存。
- `refresh()`：GET `path` 得到 `LoadViewResponse`，调用 `updateCurrentMetadata` 仅在 `metadataFileLocation` 变化时替换缓存（避免无谓更新）。
- `commit(ViewMetadata base, ViewMetadata metadata)`：构造 `UpdateTableRequest`（`requirements` 包含 `AssertTableUUID(base.uuid())`，`updates` 用 `metadata.changes()`），POST `path`，使用 `viewCommitHandler`，提交后用响应更新本地缓存。

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java`

**修改目的**：定义视图的 REST 资源 URL。

**工作逻辑**：新增三个方法，与 table 路径平行：
- `views(Namespace)` → `v1/{prefix}/namespaces/{encNs}/views`
- `view(TableIdentifier)` → `v1/{prefix}/namespaces/{encNs}/views/{encName}`
- `renameView()` → `v1/{prefix}/views/rename`

namespace 用 `RESTUtil.encodeNamespace` 编码，name 用 `RESTUtil.encodeString` 编码，与 table 一致，支持含 `/` 的多段命名空间。

### `core/src/main/java/org/apache/iceberg/rest/requests/CreateViewRequest.java`（新文件）

**修改目的**：创建视图的 REST 请求模型。

**工作逻辑**：Immutables 接口，字段：`name`、可空 `location`、`schema`、`viewVersion`（`ViewVersion`）、`properties`（Map）。`validate()` 空实现（不可构造非法实例）。

### `core/src/main/java/org/apache/iceberg/rest/requests/CreateViewRequestParser.java`（新文件）

**修改目的**：`CreateViewRequest` 的 Jackson 序列化器。

**工作逻辑**：手动用 `JsonGenerator` 写出 `name/location/view-version/schema/properties` 五个字段，`view-version` 用 `ViewVersionParser.toJson`、`schema` 用 `SchemaParser.toJson`。反序列化时按字段读取并构造 `ImmutableCreateViewRequest.Builder`。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadViewResponse.java`（新文件）

**修改目的**：加载视图的 REST 响应模型，对应 table 的 `LoadTableResponse`。

**工作逻辑**：字段：`metadataLocation`、`metadata`（`ViewMetadata`）、`config`（Map，用于下发鉴权/配置）。`validate()` 空实现。

### `core/src/main/java/org/apache/iceberg/rest/responses/LoadViewResponseParser.java`（新文件）

**修改目的**：`LoadViewResponse` 的 Jackson 序列化器。

**工作逻辑**：写出 `metadata-location/metadata/config`。反序列化时有个特殊处理：当 `metadata.metadataFileLocation()` 为 null 时，用 `ViewMetadata.buildFrom(metadata).setMetadataLocation(metadataLocation).build()` 回填 metadata-location，兼容服务端只在响应顶层给出 metadataLocation 的情况。

### `core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java`

**修改目的**：把新模型注册到 REST 全局 ObjectMapper。

**工作逻辑**：在 `RESTObjectMapper.mapper()` 中为 `CreateViewRequest`/`ImmutableCreateViewRequest`/`LoadViewResponse`/`ImmutableLoadViewResponse` 各注册 Serializer + Deserializer，新增四个静态内部类（`CreateViewRequestSerializer/Deserializer`、`LoadViewResponseSerializer/Deserializer`）委托给对应的 Parser。

### `core/src/main/java/org/apache/iceberg/rest/requests/RenameTableRequest.java`

**修改目的**：文档措辞调整。

**工作逻辑**：把 "A REST request to rename a table." 改为 "A REST request to rename a table or a view."，因为视图重命名复用了同一个请求类型。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java`

**修改目的**：修复 `ViewMetadata.Builder` 在 schema 引用与版本变更记录上的若干问题。

**工作逻辑**：
- 新增 `lastAddedSchemaId` 字段，在 `addSchemaInternal` 中记录最后新增的 schemaId。
- 重构 `addVersionInternal`：先把"重写 versionId"逻辑提前到方法开头，再处理 `newVersion.schemaId() == LAST_ADDED`（即 -1，表示引用最近新增的 schema）的情况——若 `lastAddedSchemaId` 为 null 则抛 `ValidationException`，否则用 `lastAddedSchemaId` 替换 schemaId。
- 调整 `MetadataUpdate.AddViewVersion` 记录策略：当 `version.schemaId() == lastAddedSchemaId`（即版本引用的是刚新增的 schema）时，记录的 update 中 schemaId 改写为 `LAST_ADDED`（-1），表示"绑定到最近新增的 schema"。这样服务端在回放 update 时能把 schemaId 正确解析为新加 schema 的 id，避免 id 冲突。
- 把"是否重写 versionId"的判断与"加入 versions/versionsById/history/changes"的代码块顺序整理清楚，减少副作用顺序问题。

### `core/src/main/java/org/apache/iceberg/view/ViewMetadataParser.java`、`ViewVersionParser.java`、`ViewVersionReplace.java`

**修改目的**：可见性调整以支持 REST 路径调用。

**工作逻辑**：把 `ViewMetadataParser.toJson(ViewMetadata, JsonGenerator)` 由包级改为 `public`，`ViewVersionParser.toJson(ViewVersion)` 由包级改为 `public`，`ViewVersionReplace.internalApply()` 由 `private` 改为包级，供 `CatalogHandlers`/测试/`RESTSessionCatalog` 在提交或断言时直接调用。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`

**修改目的**：测试用 in-process REST 适配器新增视图路由。

**工作逻辑**：
- 新增 `NoSuchViewException → 404` 映射；构造时若 catalog 实现了 `ViewCatalog` 则保存为 `asViewCatalog`。
- 新增 6 个路由枚举：`LIST_VIEWS`(GET `/v1/namespaces/{namespace}/views`)、`LOAD_VIEW`(GET `/v1/namespaces/{namespace}/views/{name}`)、`CREATE_VIEW`(POST)、`UPDATE_VIEW`(POST)、`RENAME_VIEW`(POST `/v1/views/rename`)、`DROP_VIEW`(DELETE)。同时把原 table 路由路径变量从 `{table}` 统一改名 `{name}`，并把 `identFromPathVars` 取的 key 从 `table` 改为 `name`（与 view 共用同一解析逻辑）。
- `handleRequest` switch 新增 6 个 case，分别调用 `CatalogHandlers.listViews/createView/loadView/updateView/renameView/dropView`，每个 case 都先判 `asViewCatalog != null`，否则跳过到 default（与现有 namespace/table 的判空模式一致）。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java`（新文件）

**修改目的**：REST 视图目录端到端集成测试。

**工作逻辑**：继承 `ViewCatalogTests<RESTCatalog>`，启动一个 Jetty `Server`，用 `InMemoryCatalog` 作为后端，包装 `RESTCatalogAdapter` 并在每次请求/响应上做一次 `roundTripSerialize`（先序列化为 JSON 再反序列化回来），确保 REST 协议的 JSON 往返无损。`supportsServerSideRetry()` 返回 true，使基类 `concurrentReplaceViewVersion` 测试走"重试成功"分支。

### `core/src/test/java/org/apache/iceberg/rest/TestResourcePaths.java`

**修改目的**：覆盖新增 view 路径方法的编码行为。

**工作逻辑**：新增 6 个测试，验证 `views/view` 在带 prefix/不带 prefix、含 `/` 命名空间、多段命名空间下的 URL 形态（`%2F`、`%1F` 编码）。

### `core/src/test/java/org/apache/iceberg/rest/requests/TestCreateViewRequestParser.java`、`responses/TestLoadViewResponseParser.java`（新文件）

**修改目的**：序列化器单元测试。

**工作逻辑**：分别覆盖 `CreateViewRequest`/`LoadViewResponse` 的 JSON 序列化与反序列化，包括字段顺序、可空字段、嵌套 `ViewVersion`/`Schema`/`ViewMetadata`。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java`

**修改目的**：适配 `ViewMetadata` 变更记录语义调整，并补充边界测试。

**工作逻辑**：原断言中 `MetadataUpdate.AddViewVersion.viewVersion()` 与原始 `viewVersionN` 直接相等，但因为新逻辑会把 schemaId 替换为 `LAST_ADDED`（-1），断言改为 `ImmutableViewVersion.builder().from(viewVersionN).schemaId(-1).build()`。新增 `lastAddedSchemaFailure` 测试，验证在没有 add schema 的情况下引用 `LAST_ADDED` 会抛 `ValidationException`。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：扩展视图目录通用测试基类。

**工作逻辑**：
- 新增 `supportsServerSideRetry()` 钩子（默认 false），允许子类声明是否支持服务端重试。
- 调整"重复方言"测试断言，由精确匹配 `IllegalArgumentException` 改为 `Exception` + `hasMessageContaining`，兼容 REST 路径下包装后的异常。
- 新增 `concurrentReplaceViewVersion`：模拟两个并发 `replaceViewVersion`（一个 trino、一个 spark + 不同 schema），第一次提交成功，第二次提交：支持服务端重试时断言成功且产生 3 个版本；否则断言抛 `CommitFailedException` 并保持 2 个版本。这同时验证了 `ViewMetadata.Builder` 对并发追加版本号和 schema 的处理。

### `open-api/rest-catalog-open-api.yaml`、`rest-catalog-open-api.py`

**修改目的**：把视图端点正式纳入 REST Catalog OpenAPI 规范。

**工作逻辑**：yaml 新增约 595 行，包括：
- 新增路径 `GET /v1/{prefix}/namespaces/{namespace}/views`（listViews）、`POST` 同路径（createView，body 为 `CreateViewRequest`，响应 `LoadViewResponse`）。
- 新增路径 `/v1/{prefix}/namespaces/{namespace}/views/{view}` 的 `GET`(loadView)/`POST`(updateView，body `UpdateTableRequest`)/`DELETE`(dropView)。
- 新增路径 `POST /v1/{prefix}/views/rename`（renameView，body `RenameTableRequest`）。
- 新增 components/schemas：`CreateViewRequest`、`LoadViewResponse`、`ViewMetadata`、`ViewVersion`、`ViewHistoryEntry`、`ViewRepresentation`、`SQLViewRepresentation`、`ViewSummary`，以及 `view` 路径参数、`ViewAlreadyExistsError`/`NoSuchViewError` 示例、`LoadViewResponse` 响应。
- 把 `RENAME_TABLE` 的 409 描述从"target table identifier to rename to already exists"改为"target identifier to rename to already exists as a table or view"。
- `.py` 是从 yaml 自动生成的 Python 客户端模型，同步新增对应 dataclass（约 111 行）。

## 小结

本提交把 View 的全生命周期（list/load/create/replace/rename/drop/invalidate）端到端接入 REST Catalog，从客户端 `RESTSessionCatalog`/`RESTViewOperations` 到服务端 `CatalogHandlers`、再到 OpenAPI 规范全面落地，是 Iceberg 视图能力在协议层成为"一等公民"的里程碑式提交。
