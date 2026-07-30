# 提交 3158：Core: Refactor test suite for freshness-aware loading (#15082)

## 提交信息

- **序号**：3158 / 4088
- **哈希**：4368c3a62d30cc352184d6e6279074945d1945e6
- **短哈希**：4368c3a62
- **日期**：2026-01-26 08:47:19 +0100
- **作者**：gaborkaszab
- **提交说明**：Core: Refactor test suite for freshness-aware loading
- **PR/Issue**：#15082

## 总体目的

Iceberg 的 REST Catalog 支持"freshness-aware loading"（新鲜度感知加载）：通过 ETag 与表缓存（`RESTTableCache`）机制，客户端在缓存未过期时可携带 ETag 发起条件请求，服务端若元数据未变化返回 304 Not Modified，从而避免重复传输完整表元数据。这部分逻辑此前全部作为测试内嵌在 `TestRESTCatalog` 中——这是一个体量巨大的综合测试类（继承 `CatalogTests<RESTCatalog>`），既承担通用 Catalog 合规性测试，又承载了大量针对 ETag、缓存过期、条件加载的专项用例，导致该类臃肿、职责混杂、维护成本高，且缓存相关测试难以独立运行与定位。

本提交将 freshness-aware loading 相关测试从 `TestRESTCatalog` 中剥离，迁入独立的 `TestFreshnessAwareLoading` 测试套件，并顺势做了三项配套重构：一是把原本散落在 `TestRESTCatalog` 内的请求匹配器（`reqMatcher`）抽到独立的 `RequestMatcher` 工具类，使其可跨测试套件复用；二是新建 `TestBaseWithRESTServer` 抽象基类，封装"启动 Jetty REST 服务 + 初始化后端 InMemoryCatalog + 构造 RESTCatalog + 往返序列化校验"这套被多个测试重复的脚手架；三是新增 `TestRESTTableCache` 套件，针对 `RESTTableCache` 缓存本身（容量、过期、关闭、多会话隔离）做单元测试，不再依赖完整 REST 服务。同时 `TestRESTScanPlanning` 改为继承新基类以消除重复代码，`TestRESTViewCatalog` 也更新了对请求匹配器的引用。

## 如何达成设计目的

整体思路是"提取 + 分层"：提取公共测试基础设施（REST 服务启动脚手架、请求匹配器）为可复用构件，再按关注点拆分测试套件（freshness-aware loading 端到端测试、RESTTableCache 单元测试、Scan Planning 测试）。涉及新增 3 个文件、修改 4 个文件，净增代码但消除了大量重复，使每个测试类聚焦单一职责。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/RequestMatcher.java` (+82/-0 lines, 新增)

**修改目的**：提供可跨测试套件复用的 Mockito 请求匹配器工具类。

**工作逻辑**：原 `TestRESTCatalog` 中有静态方法 `reqMatcher(...)`，用 `ArgumentMatchers.argThat(...)` 构造匹配 `HTTPRequest` 的参数匹配器。本类将其抽离为 `RequestMatcher`，提供一组重载的 `matches(...)` 静态方法，按 HTTP 方法、路径、headers、queryParameters、body 等维度逐级匹配；并提供 `containsHeaders(...)` 用于只校验请求包含指定 header 子集（而非完全相等）。匹配时通过 `HTTPHeaders.of(headers)` 规范化 header 比较与生产代码保持一致。这样 `TestRESTCatalog`、`TestFreshnessAwareLoading`、`TestRESTViewCatalog` 等套件均可通过 `static import` 复用，避免逻辑重复。

### `core/src/test/java/org/apache/iceberg/rest/TestBaseWithRESTServer.java` (+165/-0 lines, 新增)

**修改目的**：封装"启动真实 Jetty REST 服务并构造 RESTCatalog 客户端"的测试脚手架，供需要端到端 REST 通信的测试继承。

**工作逻辑**：抽象基类在 `@BeforeEach` 中创建 `InMemoryCatalog` 作为后端，用 `Mockito.spy` 包裹一个 `RESTCatalogAdapter`（其 `execute` 重写会对请求体与响应做 `roundTripSerialize` 往返 JSON 序列化，以验证序列化兼容性），将其挂载到 Jetty `ServletContextHandler` 的 `RESTCatalogServlet`，绑定到回环地址随机端口并启动服务；随后用指向该服务 URI 的属性初始化 `RESTCatalog`。`@AfterEach` 负责关闭 catalog、后端 catalog 与 HTTP 服务。子类只需实现抽象方法 `catalogName()` 返回 catalog 名称。基类还暴露 `backendCatalog`、`adapterForRESTServer`、`restCatalog`、`roundTripSerialize(...)` 等供子类使用。这把原本在 `TestRESTCatalog` 与 `TestRESTScanPlanning` 中各自重复实现的服务启动逻辑收敛到一处。

### `core/src/test/java/org/apache/iceberg/rest/TestFreshnessAwareLoading.java` (+806/-0 lines, 新增)

**修改目的**：承接从 `TestRESTCatalog` 迁出的 freshness-aware loading 全部端到端测试。

**工作逻辑**：继承 `TestBaseWithRESTServer`，复用 REST 服务脚手架。该套件聚焦 ETag 生成与条件加载语义：验证建表/加载表时响应携带 ETag（`eTagWithCreateAndLoadTable`）、不同表 ETag 不同（`eTagWithDifferentTables`）、数据更新与元数据更新后 ETag 变化（`eTagAfterDataUpdate`/`eTagAfterMetadataOnlyUpdate`）、注册表复用 ETag（`eTagWithRegisterTable`）、服务端返回 304 Not Modified 时的客户端处理（`notModifiedResponse`），以及缓存命中/过期/失效、`FakeTicker` 推进时间验证过期行为等场景。这些用例原先散落在 `TestRESTCatalog` 内，现集中在一个职责清晰的套件中，便于独立运行与排错。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTTableCache.java` (+200/-0 lines, 新增)

**修改目的**：对 `RESTTableCache` 缓存做不依赖 REST 服务的纯单元测试。

**工作逻辑**：直接构造 `RESTTableCache` 实例进行测试，覆盖：非法配置校验（`invalidProperties`，过期时间为 0/负数、最大条目数为负抛 `IllegalArgumentException`）、基本 put/get（`basicPutAndGet`）、缓存未命中返回 null（`notFoundInCache`）、多会话隔离（`tableInMultipleSessions`，按 `SessionIdTableId` 区分）、达到最大条目数淘汰（`maxEntriesReached`/`configureMaxEntriesReached`，基于 Caffeine 容量限制）、关闭缓存（`cacheTurnedOff`，maxEntries=0 时put 后缓存为空）、基于 `FakeTicker` 的过期验证（`entryExpires`/`configureExpiration`，验证 expireAfterWrite 行为与可配置过期间隔）。这把原先只能通过完整 REST 链路间接测试的缓存行为下沉为快速单元测试。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+?/-1032 lines, 大幅瘦身)

**修改目的**：移除迁出的 freshness-aware loading 测试与重复脚手架，改为引用新工具类。

**工作逻辑**：删除了与 freshness-aware loading 相关的全部测试方法、相关字段（`TABLE_EXPIRATION`、`HALF_OF_TABLE_EXPIRATION`、`DEFAULT_SESSION_CONTEXT` 等）及不再需要的 import（`FakeTicker`、`Cache`、`Objects`、`BaseMetadataTable`、`NoSuchTableException`、`SessionIdTableId`、`TableWithETag` 等）。原本内嵌的静态 `reqMatcher(...)` 方法被移除，所有调用点改为 `static import RequestMatcher.matches` / `containsHeaders`。`DEFAULT_SESSION_CONTEXT` 在仍需使用处改为内联构造。改造后该类回归"REST Catalog 合规性与基础行为测试"职责，体量大幅缩减。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+?/-? lines, 改为继承新基类)

**修改目的**：消除与 REST 服务启动相关的重复脚手架代码，复用 `TestBaseWithRESTServer`。

**工作逻辑**：原先该类自行实现 `@BeforeEach`/`@AfterEach` 中创建 `InMemoryCatalog`、`Mockito.spy(RESTCatalogAdapter)`、启动 Jetty `Server`、初始化 `RESTCatalog` 等逻辑（与 `TestRESTCatalog` 重复）。改造后改为继承 `TestBaseWithRESTServer`，删除这些重复字段与方法，复用基类提供的 `backendCatalog`、`restCatalog`、`adapterForRESTServer` 等。同时移除了不再需要的 import（`ObjectMapper`、`InetSocketAddress`、`Server`、`GzipHandler`、`ServletContextHandler` 等）。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+?/-? lines, 引用更新)

**修改目的**：将对旧 `reqMatcher` 的引用替换为新 `RequestMatcher.matches`。

**工作逻辑**：将 `import static org.apache.iceberg.rest.TestRESTCatalog.reqMatcher` 改为 `import static org.apache.iceberg.rest.RequestMatcher.matches`，并将多处 `reqMatcher(HTTPMethod.GET, ...)` 调用统一改为 `matches(HTTPMethod.GET, ...)`。因为 `reqMatcher` 已从 `TestRESTCatalog` 移除，这是必需的跟随性改动，本身不改变测试行为。

## 总结

本提交通过提取 `RequestMatcher` 工具类与 `TestBaseWithRESTServer` 测试基类、新增 `TestFreshnessAwareLoading` 与 `TestRESTTableCache` 两个职责清晰的测试套件，将 freshness-aware loading 测试从臃肿的 `TestRESTCatalog` 中剥离并分层（端到端测试与缓存单元测试分离），同时让 `TestRESTScanPlanning` 复用新基类消除重复脚手架。重构显著改善了 REST Catalog 测试的可维护性、可定位性与运行效率，为后续 freshness-aware loading 能力的迭代提供了更健康的测试结构，且不改变任何生产代码与测试覆盖语义。
