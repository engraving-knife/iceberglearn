# 提交 0740：Core: Add pagination when listing namespaces/tables/views

## 提交信息
- **序号**：0740 / 4088
- **哈希**：7600ba74faef15b4d5593e9ada255e6dc999d0ed
- **短哈希**：7600ba74f
- **日期**：2024-05-02 23:35:38 -0700
- **作者**：Rahil C <32500120+rahil-c@users.noreply.github.com>
- **提交说明**：Core: Add pagination when listing namespaces/tables/views (#9782)
- **PR/Issue**：#9782

## 总体目的

本提交为 REST Catalog 的列表操作（列出命名空间、列出表、列出视图）引入分页（pagination）能力。当表/命名空间/视图数量庞大时，单次列表请求可能返回过大的响应体，导致网络传输压力、内存占用过高和响应延迟。分页机制允许客户端按固定页大小分批拉取列表数据，提升大规模元数据场景下的稳定性和可控性。

### 背景与设计动机

在 REST Catalog 架构中，客户端（`RESTSessionCatalog`）通过 HTTP GET 请求向 REST 服务端拉取命名空间/表/视图列表。此前的实现是一次性返回所有结果——`listTables()` 发送一个 GET 请求，服务端返回包含全部表标识符的 `ListTablesResponse`，客户端直接返回 `response.identifiers()`。

这种"全量返回"模式在元数据规模较大时存在问题：
- 单次 HTTP 响应体可能达到数十 MB，造成网络带宽和内存压力。
- 服务端需要一次性加载并序列化全部列表项，峰值内存高。
- 无流式或分批能力，客户端无法控制拉取粒度。

本提交通过标准的 page-token 分页模式解决这些问题：客户端可配置 `rest-page-size`，客户端自动循环拉取所有页面并聚合为完整列表，对上层调用方完全透明。

## 如何达成设计目的

### 分页协议设计

采用经典的 **page-token（页令牌）+ page-size（页大小）** 模式，分页发生在 REST 客户端与服务端之间：

**请求参数（query params）**：
- `pageToken`：字符串形式的页令牌。空字符串 `""` 表示从第一页开始；非空时为一个整数偏移量（如 `"10"`、`"20"`），表示从该偏移位置开始取。
- `pageSize`：字符串形式的页大小，解析为整数，表示每页返回的条目数。

**响应字段**：
- `nextPageToken`：下一页的令牌。若还有更多数据，设为当前页结束偏移量的字符串形式（如 `"10"`）；若已是最后一页，设为 `null`。

**客户端循环逻辑**（以 `listTables` 为例）：
```
pageToken = ""  // 初始为空
do:
    发送 GET 请求，附带 pageToken 和 pageSize 参数
    收到响应，将 identifiers 加入结果集
    pageToken = response.nextPageToken()
while (pageToken != null)
返回聚合后的完整列表
```

**服务端分页逻辑**（`CatalogHandlers`）：
```
results = catalog.listNamespaces(parent)  // 先加载全部
start = pageToken == "" ? 0 : parseInt(pageToken)
end = start + parseInt(pageSize)
subResults = results.subList(start, end)
nextToken = (end >= results.size()) ? null : String.valueOf(end)
返回 subResults + nextToken
```

### 关键设计决策

**1. 对上层调用方透明**

`RESTSessionCatalog` 的 `listTables()`、`listNamespaces()`、`listViews()` 方法签名不变，仍返回完整的 `List`。分页循环在方法内部完成，上层代码无需感知分页存在。这意味着分页是纯优化，不改变 API 契约。

**2. 通过配置开关启用**

引入新配置项 `rest-page-size`（常量 `REST_PAGE_SIZE`）。在 `initialize()` 中从属性读取并校验（必须为正整数）。若未配置（`pageSize == null`），客户端不发送 `pageSize` 参数，服务端走非分页路径。这保证了向后兼容：旧服务端不识别 `pageSize` 时忽略它，返回全部结果且 `nextPageToken` 为 null（JSON 中无此字段），客户端循环执行一次即止。

**3. pageToken 始终发送**

即使 `pageSize` 未配置，客户端也始终发送 `pageToken=""`。这是无害的——旧服务端会忽略未知的 query 参数，新服务端在 `pageSize` 缺失时走非分页路径（见 `RESTCatalogAdapter` 的分支逻辑）。这种设计确保客户端代码路径统一，无需根据是否配置 pageSize 走不同分支。

**4. 服务端先全量加载再切片**

`CatalogHandlers` 的分页方法先调用底层 catalog 的 `listNamespaces()`/`listTables()`/`listViews()` 加载全部结果到内存，再用 `subList(start, end)` 切片。这是一种简单的内存分页，未利用底层 catalog 的游标能力。对于大多数 catalog 实现（如 Hive Metastore、JDBC），底层 list 操作本身返回全量列表，因此内存分页是合理的。若未来需要更高效的分页，可由底层 catalog 提供原生分页支持。

### 向后兼容性分析

- **旧客户端 + 新服务端**：旧客户端不发送 `pageToken`/`pageSize`，服务端走非分页路径，返回全部结果，行为不变。
- **新客户端 + 旧服务端**：新客户端发送 `pageToken=""`，旧服务端忽略该参数返回全部结果，响应中无 `nextPageToken` 字段，Jackson 反序列化为 null，客户端循环一次即止，行为不变。
- **新客户端（未配置 pageSize）+ 新服务端**：客户端不发送 `pageSize`，`RESTCatalogAdapter` 走非分页路径返回全部结果，`nextPageToken` 为 null，循环一次即止。
- **新客户端（配置 pageSize）+ 新服务端**：完整分页流程，客户端循环拉取所有页面。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java`
**修改目的**：在服务端处理层新增分页版本的列表方法。

**具体改动**：

1. 新增常量 `private static final String INTIAL_PAGE_TOKEN = "";`（注意：原代码中 "INTIAL" 为拼写错误，应为 "INITIAL"，但此处保持与提交一致）。

2. **新增 `listNamespaces(SupportsNamespaces, Namespace, String pageToken, String pageSize)`**：分页版命名空间列表。先按 parent 是否为空选择 `listNamespaces()` 或 `listNamespaces(parent)` 加载全部，再用 `subList` 切片，返回带 `nextPageToken` 的响应。

3. **新增 `listTables(Catalog, Namespace, String pageToken, String pageSize)`**：分页版表列表。逻辑同上，针对 `catalog.listTables(namespace)` 结果切片。

4. **新增 `listViews(ViewCatalog, Namespace, String pageToken, String pageSize)`**：分页版视图列表。逻辑同上，针对 `catalog.listViews(namespace)` 结果切片。

三个方法的分页逻辑完全一致：`start = pageToken=="" ? 0 : parseInt(pageToken)`，`end = start + parseInt(pageSize)`，`subList(start, end)`，`nextToken = end >= size ? null : String.valueOf(end)`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`
**修改目的**：在 REST 客户端实现分页拉取循环，并新增 `rest-page-size` 配置。

**具体改动**：

1. **新增常量**：`public static final String REST_PAGE_SIZE = "rest-page-size";`（public 以便测试引用）。

2. **新增字段**：`private Integer pageSize = null;`。

3. **`initialize()` 方法**：从 `mergedProps` 读取 `REST_PAGE_SIZE` 为可空整数，若非 null 则校验 `pageSize > 0`，否则抛出 `IllegalArgumentException`。

4. **`listTables()` 方法重构**：从单次 GET 改为 do-while 循环：
   - 构建 `queryParams`（HashMap），若 `pageSize != null` 则放入 `pageSize`。
   - 循环：放入 `pageToken`，发送 GET，收集 `identifiers`，更新 `pageToken = response.nextPageToken()`。
   - `pageToken == null` 时退出，返回聚合的 `ImmutableList`。

5. **`listNamespaces()` 方法重构**：类似 listTables 的循环改造。保留原有 `parent` 参数处理（非空时放入 `parent` query param），新增 pageSize 和 pageToken 处理。将原 `ImmutableMap` 改为可变的 `Maps.newHashMap()` 以支持循环中更新 `pageToken`。

6. **`listViews()` 方法重构**：同样的 do-while 循环改造。

### `core/src/main/java/org/apache/iceberg/rest/responses/ListNamespacesResponse.java`
**修改目的**：为命名空间列表响应新增 `nextPageToken` 字段。

**具体改动**：
- 新增 `private String nextPageToken;` 字段。
- 私有构造函数增加 `nextPageToken` 参数。
- 新增 `public String nextPageToken()` getter。
- `toString()` 增加 `next-page-token` 输出。
- `Builder` 新增 `nextPageToken` 字段、`nextPageToken(String)` 方法，`build()` 传入该值。

### `core/src/main/java/org/apache/iceberg/rest/responses/ListTablesResponse.java`
**修改目的**：为表列表响应新增 `nextPageToken` 字段。

**具体改动**：与 `ListNamespacesResponse` 完全对称——新增字段、构造参数、getter、toString、Builder 方法。注意 `ListTablesResponse` 同时用于表列表和视图列表（视图列表也返回 `ListTablesResponse`）。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`
**修改目的**：在测试用的 REST 适配器中路由分页参数到对应的分页 handler。

**具体改动**：对 `LIST_NAMESPACES`、`LIST_TABLES`、`LIST_VIEWS` 三个路由，从请求变量中提取 `pageToken` 和 `pageSize`。若 `pageSize != null`，调用 `CatalogHandlers` 的分页方法；否则调用原非分页方法。这使得测试适配器能模拟真实服务端的分页/非分页行为分支。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`
**修改目的**：验证分页功能的正确性和配置校验。

**新增测试**：

1. **`testInvalidPageSize()`**：验证 `rest-page-size=-1` 抛出 `IllegalArgumentException`，消息为 "Invalid value for rest-page-size, must be a positive integer"。

2. **`testPaginationForListNamespaces()`**：创建 30 个命名空间，配置 `pageSize=10`，调用 `listNamespaces()`。用 Mockito 验证：
   - 共 3 次 `handleRequest` 调用，pageToken 分别为 `""`、`"10"`、`"20"`，pageSize 均为 `"10"`。
   - 最终返回 30 个命名空间。

3. **`testPaginationForListTables()`**：创建 30 个表，配置 `pageSize=10`，调用 `listTables()`。验证 3 次请求的 pageToken 递进（`""`→`"10"`→`"20"`），最终返回 30 个表。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java`
**修改目的**：验证视图列表的分页功能。

**新增测试**：`testPaginationForListViews()`：创建 30 个视图，配置 `pageSize=10`，验证 3 次请求的 pageToken 递进，最终返回 30 个视图。逻辑与 `testPaginationForListTables` 对称。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestListNamespacesResponse.java` 和 `TestListTablesResponse.java`
**修改目的**：验证响应类新增 `nextPageToken` 字段的序列化/反序列化。

**具体改动**：
- 更新 `testRoundTripSerDe` 中的 JSON 期望值，加入 `"next-page-token":null`。
- 新增 `testWithNullPaginationToken()`：验证 `nextPageToken=null` 的 round-trip。
- 新增 `testWithPaginationToken()`：验证 `nextPageToken="token"` 的 round-trip。
- `allFieldsFromSpec()` 增加 `"next-page-token"` 字段。

## 小结

- **成效**：成功为 REST Catalog 的三个列表操作（namespaces/tables/views）引入分页能力。通过 `rest-page-size` 配置启用，客户端自动循环拉取所有页面并聚合，对上层完全透明。设计保持了与旧服务端和旧客户端的双向向后兼容。测试覆盖了配置校验、三种列表操作的分页请求序列、响应序列化等场景。
- **影响范围**：仅影响 `core` 模块的 REST 相关代码。涉及服务端处理层（`CatalogHandlers`）、客户端（`RESTSessionCatalog`）、响应模型（两个 Response 类）、测试适配器和测试。不影响非 REST 的 catalog 实现（如 HiveCatalog、JdbcCatalog 等的本地列表操作）。
- **潜在局限**：
  - 服务端分页为内存分页（先全量加载再 `subList` 切片），未利用底层 catalog 的原生分页能力，对超大列表仍有内存压力。
  - `subList(start, end)` 中 `end = start + pageSize`，当最后一页条目数不足 `pageSize` 时 `end` 可能超过 `results.size()`，理论上会抛出 `IndexOutOfBoundsException`。现有测试使用 30 项 / pageSize=10（恰好整除）未触发此问题，但非整除场景（如 25 项 / pageSize=10）存在风险。后续提交可能需要用 `Math.min(end, results.size())` 修复。
- **回迁到 1.4.x 的注意事项**：
  - 需确认 1.4.x 分支的 `ListNamespacesResponse` 和 `ListTablesResponse` 结构与本提交前一致，以便直接应用 `nextPageToken` 字段新增改动。
  - 需确认 1.4.x 的 `RESTSessionCatalog` 的 `listTables`/`listNamespaces`/`listViews` 方法结构与 main 分支一致。若 1.4.x 有差异（如不同的 query param 处理方式），需相应调整循环逻辑。
  - `REST_PAGE_SIZE` 常量声明为 `public`，回迁时需保持以兼容测试引用。
  - `CatalogHandlers` 的新增方法是重载（不替换原方法），回迁安全。`RESTCatalogAdapter` 的分支逻辑（`pageSize != null` 时走分页路径）需确保 1.4.x 的适配器结构与 main 一致。
  - 此特性为可选优化（默认不启用），回迁风险较低。但若 1.4.x 的 REST 客户端会被旧版服务端使用，需验证旧服务端对未知 `pageToken` query 参数的容忍度（通常 REST 服务端忽略未知 query 参数，但需确认）。
