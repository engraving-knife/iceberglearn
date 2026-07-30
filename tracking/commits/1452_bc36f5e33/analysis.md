# 提交 1452：REST: Use `HEAD` request to check table existence (#10999)

## 提交信息

- **序号**：1452 / 4088
- **哈希**：bc36f5e33fd71335e91f04aa70a199243fd15897
- **短哈希**：bc36f5e33
- **日期**：2024-12-02（Mon Dec 2 14:25:10 2024 +0900）
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：REST: Use `HEAD` request to check table existence (#10999)
- **PR/Issue**：#10999

## 总体目的

Iceberg REST Catalog 在客户端调用 `tableExists` 时，原本走的是父类 `BaseSessionCatalog` 的默认实现——通过 `loadTable`（即 `GET /v1/{prefix}/namespaces/{namespace}/tables/{table}`）来"试探"表是否存在：加载成功就认为存在，捕获 `NoSuchTableException` 就认为不存在。这种做法虽然语义正确，但有两个明显代价：

1. **网络开销大**：`GET` 加载表会返回完整的 `LoadTableResponse`（含表元数据、配置、可能的快照信息等），对于"只关心存不存在"的场景属于大量冗余传输；
2. **服务端开销大**：服务端需要构造完整的 `LoadTableResponse`，包括读取/解析元数据文件、构建 `TableMetadata`、可能还要初始化表级 `FileIO` 等，而调用方根本不会用到这些信息。

本提交把 `tableExists` 改为发送 `HEAD /v1/{prefix}/namespaces/{namespace}/tables/{table}`：HTTP `HEAD` 方法按约定不返回响应体，仅通过状态码（200 表示存在、404 表示不存在）告知结果，既能减少网络传输、降低服务端负担，也更符合 RESTful 语义——"探测存在性"本就该用 `HEAD`。同时为 REST Catalog 的服务端路由（`RESTCatalogAdapter`）补齐 `HEAD /v1/.../tables/{table}` 路由与服务端处理器 `CatalogHandlers.tableExists`，使整条链路贯通。

## 如何达成设计目的

通过三处协同改动实现：

1. **客户端发 `HEAD`**：在 `RESTSessionCatalog` 中重写 `tableExists(SessionContext, TableIdentifier)`，调用 `client.head(paths.table(identifier), headers(context), ErrorHandlers.tableErrorHandler())`；返回值只有 `true`，若服务端 404 则 `tableErrorHandler` 抛 `NoSuchTableException`，在 `try/catch` 中转换为 `false`。
2. **服务端处理器**：新增 `CatalogHandlers.tableExists(Catalog, TableIdentifier)`，调用底层 `catalog.tableExists(ident)`，不存在时抛 `NoSuchTableException`（HTTP 404）。
3. **测试用路由**：在 `RESTCatalogAdapter`（用于测试的 RESTClient 实现）中注册 `TABLE_EXISTS = HEAD V1_TABLE` 路由，并在 `execute` 中分发到 `CatalogHandlers.tableExists`，使测试链路与生产链路一致。
4. **测试断言更新**：把 `TestRESTCatalog` 中所有验证 `tableExists` 行为的断言从期望 `GET` + `LoadTableResponse.class` 改为期望 `HEAD`，并相应调整注释。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java`（修改，+7 行）

**修改目的**：为 REST Catalog 服务端提供"判断表存在性"的处理器入口。

**工作逻辑**：新增静态方法 `tableExists(Catalog catalog, TableIdentifier ident)`：

```java
public static void tableExists(Catalog catalog, TableIdentifier ident) {
  boolean exists = catalog.tableExists(ident);
  if (!exists) {
    throw new NoSuchTableException("Table does not exist: %s", ident);
  }
}
```

注意方法返回 `void`——存在时不抛异常（HTTP 200），不存在时抛 `NoSuchTableException`，由 `tableErrorHandler` 映射为 HTTP 404。这种"以异常表达不存在"的风格与同文件中 `loadTable` 等方法保持一致，便于复用统一的错误处理链路。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`（修改，+12 行）

**修改目的**：让 REST Catalog 客户端在判断表存在性时改用 `HEAD` 请求，避免 `GET` 加载完整表元数据的开销。

**工作逻辑**：重写父类 `BaseSessionCatalog` 的 `tableExists`：

```java
@Override
public boolean tableExists(SessionContext context, TableIdentifier identifier) {
  checkIdentifierIsValid(identifier);

  try {
    client.head(paths.table(identifier), headers(context), ErrorHandlers.tableErrorHandler());
    return true;
  } catch (NoSuchTableException e) {
    return false;
  }
}
```

关键点：
- `paths.table(identifier)` 复用与 `loadTable`/`dropTable` 相同的 URL 模板 `v1/{prefix}/namespaces/{ns}/tables/{table}`，只是 HTTP 方法不同；
- `ErrorHandlers.tableErrorHandler()` 在 404 时会抛 `NoSuchTableException`（且能区分 `NoSuchNamespaceException`），在 409 时抛 `AlreadyExistsException`，其余走 `DefaultErrorHandler`；
- `HTTPClient.head` 内部调用 `execute(Method.HEAD, path, null, null, null, headers, errorHandler)`，由于 `responseType == null` 且响应成功，会直接返回 `null` 而不解析响应体——这正是 `HEAD` 不返回 body 的语义体现。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`（修改，+8 行）

**修改目的**：在测试用的 `RESTCatalogAdapter`（实现 `RESTClient`、把 REST 调用直接路由到内存中的 `Catalog`）中注册 `HEAD` 路由，使测试链路也能跑通 `tableExists`。

**工作逻辑**：
- 在路由表 `REQUEST_PARAMETERS` 中新增条目 `TABLE_EXISTS(HTTPMethod.HEAD, ResourcePaths.V1_TABLE)`，与已有的 `LOAD_TABLE(GET, V1_TABLE)` 等并列；
- 在 `execute` 的 `switch` 中新增 `case TABLE_EXISTS`：从路径变量解析出 `TableIdentifier`，调用 `CatalogHandlers.tableExists(catalog, ident)`，返回 `null`（HEAD 无响应体）。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`（修改，约 +27/-27 行）

**修改目的**：把所有针对 `tableExists` 的 Mockito 验证从期望 `GET` + `LoadTableResponse.class` 更新为期望 `HEAD`，并修正注释。

**工作逻辑**：在 9 处 `Mockito.verify(adapter).execute(...)` 调用中：
- `eq(HTTPMethod.GET)` → `eq(HTTPMethod.HEAD)`；
- `eq(LoadTableResponse.class)` → `any()`（因为 `HEAD` 不传 `responseType`，`HTTPClient.head` 调用时 `responseType` 为 `null`，但 `RESTCatalogAdapter.execute` 的签名仍带该参数，用 `any()` 匹配更宽松）；
- 同步把注释从 "use the context token for table load" 改为 "use the context token for table existence check"。

这些测试覆盖了多种认证场景（catalog token、context token、token 交换、basic auth、refreshed token 等），确保改用 `HEAD` 后各类鉴权头仍按预期传递。

## 小结

- **成效**：REST Catalog 的 `tableExists` 由"加载整张表"降级为"发 HEAD 探测状态码"，显著降低网络与服务端开销，且更贴合 RESTful 语义；服务端路由与测试链路同步打通。
- **影响范围**：仅 `core` 模块。生产代码改动集中在 `RESTSessionCatalog`（客户端）与 `CatalogHandlers`（服务端处理器，供自建 REST 服务方调用），不改变 `Catalog` 接口契约；测试侧 `RESTCatalogAdapter` 注册新路由、`TestRESTCatalog` 更新断言。
- **回迁到 1.4.x 的注意事项**：本提交依赖 `RESTClient.head` 接口与 `HTTPClient.head` 实现已存在（1.4.x 中已具备），可直接 cherry-pick。需确认 1.4.x 分支的 `RESTCatalogAdapter` 路由表结构与 `REQUEST_PARAMETERS`/`execute` switch 分支与 main 一致；若 1.4.x 上游已自建 REST Catalog 服务端，需要服务端同步支持 `HEAD /v1/.../tables/{table}` 端点（否则客户端 `HEAD` 会拿到 405 Method Not Allowed）。另外，`BaseSessionCatalog.tableExists` 的默认实现仍走 `loadTable`，本提交只是给 REST Catalog 覆盖了更优实现，不影响其他 Catalog 实现。
