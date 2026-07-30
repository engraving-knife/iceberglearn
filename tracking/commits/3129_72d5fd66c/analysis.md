# 提交 3129：Core: Freshness-aware table loading in REST catalog (#14398)

## 提交信息

- **序号**：3129 / 4088
- **哈希**：72d5fd66c527c27c79880e051b7eaf46deb2fed6
- **短哈希**：72d5fd66c
- **日期**：2026-01-18
- **作者**：gaborkaszab
- **提交说明**：Core: Freshness-aware table loading in REST catalog (#14398)
- **PR/Issue**：#14398

## 总体目的

本提交为 REST Catalog 实现客户端"新鲜度感知"（freshness-aware）的表加载机制，核心目标是减少不必要的表元数据重复拉取，降低 REST 服务端负载与网络开销，同时保证客户端拿到的表对象不会过期。

背景在于：REST Catalog 模式下，每次 `loadTable` 都会向服务端发起 `GET /v1/{prefix}/namespaces/{ns}/tables/{table}` 请求并回传完整的 `LoadTableResponse`（含表元数据、配置、凭证等）。对于频繁加载同一表的引擎（如 Flink/Spark 反复进行 scan planning 或 commit），这意味着大量重复的元数据传输与服务端重建开销，而表元数据其实在两次加载之间往往并未变化。

本提交引入标准的 HTTP 条件请求机制来解决该问题：服务端在 `LoadTableResponse` 中附带 `ETag` 响应头；客户端将该 ETag 与表对象一同缓存，并在下次 `loadTable` 时通过 `If-None-Match` 请求头回传；若服务端判定表未变化则返回 `304 Not Modified`（无响应体），客户端直接返回缓存中对应的表对象。这样在表未变更时避免了元数据序列化与传输，在表变更时仍能拿到最新数据。整个机制是可选且向后兼容的——服务端不发送 ETag 时，客户端退化为原有行为。

## 如何达成设计目的

设计上新增 `RESTTableCache` 作为按 `(sessionId, tableIdentifier)` 维度缓存 `Supplier<BaseTable>` + ETag 的 Caffeine 缓存，挂载在 `RESTSessionCatalog` 上。`loadTable` 流程改造为：先查缓存拿到 ETag，构造 `If-None-Match` 请求头发起条件加载；`loadInternal` 签名扩展以支持传入请求头与接收响应头回调；当响应为空（304）时返回缓存的表对象，否则解析新响应并把 ETag 与新的表供应商写入缓存。同时在 rename/drop/tableExists 等变更路径上调用 `invalidateTable` 失效缓存，保证一致性。新增缓存配置属性，并通过可测试子类注入 `Ticker` 以验证过期行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableCache.java` (+129 lines)

**修改目的**：新增表缓存实现，按会话与表标识缓存表供应商与 ETag。

**工作逻辑**：
该类基于 Caffeine 构建缓存，键为不可变值类型 `SessionIdTableId`（`sessionId` + `TableIdentifier`），值为 `TableWithETag`（`Supplier<BaseTable>` + `eTag`），二者均用 Immutables 生成。构造时读取两个配置项：`rest-table-cache.expire-after-write-ms`（默认 5 分钟）与 `rest-table-cache.max-entries`（默认 100），并对非法值做 `Preconditions.checkArgument` 校验。提供 `getIfPresent`/`put`/`invalidate`/`close` 方法，并开启 `recordStats` 与 `removalListener` 以便测试观测命中与驱逐。值得注意缓存的是 `Supplier<BaseTable>` 而非表对象本身——这样每次从缓存命中时可重新构造 `RESTTableOperations` 与 `BaseTable`，避免复用已关闭的 FileIO 或陈旧操作对象。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+11 lines)

**修改目的**：新增表缓存配置项常量与默认值。

**工作逻辑**：
新增两个属性：`TABLE_CACHE_EXPIRE_AFTER_WRITE_MS = "rest-table-cache.expire-after-write-ms"`，默认 `TimeUnit.MINUTES.toMillis(5)`（5 分钟）；`TABLE_CACHE_MAX_ENTRIES = "rest-table-cache.max-entries"`，默认 100。二者供 `RESTTableCache` 构造时读取。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+约130/-30 lines)

**修改目的**：集成表缓存到 `loadTable` 流程，并在变更路径失效缓存。

**工作逻辑**：
- 新增成员 `tableCache`，在 `initialize` 中通过 `createTableCache(mergedProps)` 创建并注册为 closeable。
- `loadInternal` 签名扩展：增加 `Map<String,String> headers` 与 `Consumer<Map<String,String>> responseHeaders` 参数，使调用方能注入 `If-None-Match` 并接收 `ETag` 响应头。
- `loadTable` 主流程：先 `tableCache.getIfPresent(sessionId, identifier)` 取缓存条目，经 `headersForLoadTable(cachedTable)` 构造请求头（若有 ETag 则放入 `If-None-Match`）；发起加载后若 `response == null`（即 304），则 `return cachedTable.supplier().get()` 返回缓存表。否则解析响应，并通过 `createTableSupplier` 把构造逻辑封装为 `Supplier<BaseTable>`，若响应带 ETag 则 `tableCache.put` 写入缓存。对元数据表（metadata table）的回退加载路径也做了同样的缓存处理。
- `headersForLoadTable` 辅助方法：缓存条目为空返回空 Map，否则返回 `Map.of(IF_NONE_MATCH, eTag)`。
- `invalidateTable` 由空实现改为 `tableCache.invalidate(sessionId, ident)`；并在 `tableExists`（HEAD 与 GET 回退两条路径）、`renameTable`、`dropTable` 的 `finally`/异常分支中调用失效，确保表被删除/重命名后不会命中脏缓存。
- `createTableSupplier` 把原先内联的 `newTableOps`/`trackFileIO`/`restTableForScanPlanning`/`new BaseTable` 逻辑抽出为延迟供应商，使缓存命中时可重新生成表实例。

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (+10 lines)

**修改目的**：对 304 响应增加防护校验。

**工作逻辑**：
在 `emptyBody` 分支中，若响应码为 `304 NOT_MODIFIED` 但请求头中并未携带 `IF_NONE_MATCH`，则抛出 `RESTException`。这防止服务端在客户端未发起条件请求时错误返回 304 而被静默忽略，便于尽早暴露协议异常。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalog.java` (+4 lines)

**修改目的**：暴露 `sessionCatalog()` 访问器以支持测试。

**工作逻辑**：
新增 `protected RESTSessionCatalog sessionCatalog()` 返回成员 `sessionCatalog`，供测试类（`TestableRESTCatalog`）在 `initialize` 前后访问并注入 Ticker。

### `core/src/test/java/org/apache/iceberg/rest/TestableRESTCatalog.java` (+52 lines)

**修改目的**：新增可注入 Caffeine `Ticker` 的 `RESTCatalog` 测试子类。

**工作逻辑**：
重写 `newSessionCatalog` 返回 `TestableRESTSessionCatalog`，并在 `initialize` 前把测试用的 `Ticker` 设置进去，从而允许测试用虚拟时钟推进缓存过期而无需真实等待。注释说明因构造期间 `ticker` 尚未赋值，需延迟传递。

### `core/src/test/java/org/apache/iceberg/rest/TestableRESTSessionCatalog.java` (+44 lines)

**修改目的**：重写 `createTableCache` 以注入测试 Ticker。

**工作逻辑**：
继承 `RESTSessionCatalog`，持有可设置的 `ticker`，在 `createTableCache` 中调用 `new RESTTableCache(props, ticker)`，使缓存使用测试时钟。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+约600 lines)

**修改目的**：为新鲜度感知加载与缓存失效添加覆盖测试。

**工作逻辑**：
新增多个测试：`testFreshnessAwareLoading` 验证首次加载写缓存、带 `If-None-Match` 二次加载命中 304 并返回缓存、缓存命中计数递增、过期后重新加载；`testFreshnessAwareLoadingMetadataTables` 覆盖元数据表路径；`testCustomTableOperationsWithFreshnessAwareLoading` 验证缓存命中时仍使用注入的自定义 `RESTTableOperations`（因 Supplier 重新构造）；`testInvalidTableCacheParameters` 校验非法缓存参数抛异常；`testRenameTableInvalidatesTable`、`testDropTableInvalidatesTable`、`testTableExistViaHeadRequestInvalidatesTable`、`testTableExistViaGetRequestInvalidatesTable`、`testLoadTableInvalidatesCache`、`testLoadTableWithMetadataTableNameInvalidatesCache` 等通过 `runTableInvalidationTest` 公共方法验证各变更操作正确失效缓存。测试还断言第二次请求头中包含 `If-None-Match` 且值等于首次响应的 ETag。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+20 lines)

**修改目的**：补充 REST scan planning 与新鲜度加载的集成测试。

**工作逻辑**：
新增 `remoteScanPlanningWithFreshnessAwareLoading`：首次 `loadTable` 后缓存大小为 1，第二次 `loadTable` 由缓存应答，并验证返回的表仍是 `RESTTable` 且 `newScan()` 返回 `RESTTableScan`，确保缓存命中路径不破坏 scan planning 行为。

## 总结

本提交为 REST Catalog 引入了基于 HTTP ETag/If-None-Match 的客户端表缓存机制，通过新增 `RESTTableCache`（Caffeine）与改造 `loadTable` 流程，在表元数据未变化时以 304 命中缓存避免重复传输，并在 rename/drop/exists 等变更路径主动失效缓存保证一致性，配置可选且默认开启、向后兼容，配套完善了时钟注入测试与缓存失效测试，显著降低了高频加载场景下的服务端与网络开销。
