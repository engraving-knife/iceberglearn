# 提交 1487：Core: Use HEAD request to check if namespace exists (#11761)

## 提交信息

- **序号**：1487 / 4088
- **哈希**：1e126e24e2c4b639e2509d3f8194e5ceaace6d56
- **短哈希**：1e126e24e
- **日期**：2024-12-12（Thu Dec 12 18:17:14 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Use HEAD request to check if namespace exists (#11761)
- **PR/Issue**：#11761

## 总体目的

这是与前一个提交 #11760（view 走 HEAD）配对的姊妹修复，把同样的优化应用到 namespace（命名空间）存在性检查上。

Iceberg REST Catalog 协议中，namespace 的存在性检查应当走 `HEAD /v1/namespaces/{namespace}`，但实际实现里：

- `SupportsNamespaces.namespaceExists(Namespace)` 接口默认实现是 `try { loadNamespaceMetadata(namespace); return true; } catch (NoSuchNamespaceException) { return false; }`，即通过 `GET /v1/namespaces/{namespace}` 拉取完整 namespace 属性再判断。
- `RESTSessionCatalog` 此前未覆盖 `namespaceExists(SessionContext, Namespace)`，`RESTCatalog` 也未覆盖 `namespaceExists(Namespace)`，二者都走默认的 `loadNamespaceMetadata` 路径——每次"判断 namespace 是否存在"都把 namespace 的全部属性（如 location、owner、自定义 properties）下载一遍。

namespace 属性通常较小，但存在性检查往往在高频路径上发生（如建表前校验父 namespace、列举子 namespace 前校验、引擎元数据刷新等），频繁 GET 仍是不必要的序列化与网络开销，且与 REST 规范"HEAD 做存在性检查"的语义不一致。

本提交补齐 namespace 侧的 HEAD 实现，与 table、view 的存在性检查统一：

1. `CatalogHandlers.namespaceExists` 新增服务端处理器。
2. `RESTSessionCatalog.namespaceExists` 覆盖客户端方法，发 HEAD 请求。
3. `RESTCatalog.namespaceExists` 新增覆盖，把调用委托给 `nsDelegate`（最终走到 `RESTSessionCatalog` 的 HEAD 实现）。
4. `RESTCatalogAdapter` 注册 `NAMESPACE_EXISTS` 路由。
5. `TestRESTCatalog` 新增测试，校验 HEAD 请求被发出。

## 如何达成设计目的

整体沿用与 #11760（view）和更早的 table 侧完全相同的"三层 + 测试"模式：

- **服务端** `CatalogHandlers.namespaceExists(SupportsNamespaces, Namespace)`：调底层 `catalog.namespaceExists`，不存在则抛 `NoSuchNamespaceException`（→ REST 层转 404）。
- **客户端** `RESTSessionCatalog.namespaceExists(SessionContext, Namespace)`：发 HEAD 请求到 `v1/namespaces/{namespace}`，捕获 `NoSuchNamespaceException` 转 `false`。
- **RESTCatalog 入口** `RESTCatalog.namespaceExists(Namespace)`：委托 `nsDelegate.namespaceExists(namespace)`。`nsDelegate` 实际是 `BaseSessionCatalog.AsCatalog` 实例（由 `RESTSessionCatalog.asCatalog(context)` 产生），其 `namespaceExists(Namespace)` 会进一步委托到 `RESTSessionCatalog.namespaceExists(context, namespace)`——即 HEAD 实现。此前 `RESTCatalog` 未覆盖该方法，走的是 `SupportsNamespaces` 默认实现（loadNamespaceMetadata GET）。
- **协议层** `RESTCatalogAdapter` 注册 `NAMESPACE_EXISTS(HTTPMethod.HEAD, ResourcePaths.V1_NAMESPACE)` 路由并 switch 到 `CatalogHandlers.namespaceExists`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java`

**修改目的**：新增服务端 namespace 存在性检查处理器。

**工作逻辑**：

```java
public static void namespaceExists(SupportsNamespaces catalog, Namespace namespace) {
  if (!catalog.namespaceExists(namespace)) {
    throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
  }
}
```

返回 `void`：存在则正常返回（REST 层回 204），不存在则抛 `NoSuchNamespaceException`（REST 层转 404）。与已有的 `tableExists`、`viewExists` 处理器风格完全一致。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalog.java`

**修改目的**：在 `RESTCatalog`（用户最常用入口）层面覆盖 `namespaceExists`，使其走 HEAD 路径。

**工作逻辑**：

```java
@Override
public boolean namespaceExists(Namespace namespace) {
  return nsDelegate.namespaceExists(namespace);
}
```

`nsDelegate` 是构造时通过 `sessionCatalog.asCatalog(context)` 得到的 `AsCatalog` 实例（实现了 `SupportsNamespaces`），其 `namespaceExists(Namespace)` 内部委托到 `RESTSessionCatalog.namespaceExists(context, namespace)`——即下面新增的 HEAD 实现。

此前 `RESTCatalog` 未覆盖该方法，会走 `SupportsNamespaces` 接口的默认实现（`loadNamespaceMetadata` GET），这是本次优化的关键改动点。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在 session 维度的 catalog 上用 HEAD 请求实现 `namespaceExists`。

**工作逻辑**：

```java
@Override
public boolean namespaceExists(SessionContext context, Namespace namespace) {
  checkNamespaceIsValid(namespace);

  try {
    client.head(
        paths.namespace(namespace), headers(context), ErrorHandlers.namespaceErrorHandler());
    return true;
  } catch (NoSuchNamespaceException e) {
    return false;
  }
}
```

- `checkNamespaceIsValid` 做命名空间合法性预校验（与同文件其它 namespace 方法一致）。
- `client.head(paths.namespace(namespace), ...)` 发 HEAD 请求到 `v1/namespaces/{namespace}`，无请求/响应体。
- `ErrorHandlers.namespaceErrorHandler()` 把 404 翻译成 `NoSuchNamespaceException`，被 catch 后返回 `false`。
- 其它错误（401/403/500 等）正常抛出，不被吞掉。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`

**修改目的**：在测试用 REST 服务端适配器中注册 NAMESPACE_EXISTS 路由。

**工作逻辑**：

1. 路由枚举新增：
   ```java
   NAMESPACE_EXISTS(HTTPMethod.HEAD, ResourcePaths.V1_NAMESPACE),
   ```
   紧邻 `LOAD_NAMESPACE` 之前，与已有 `TABLE_EXISTS`、`VIEW_EXISTS` 写法一致。

2. `execute` 方法 switch 新增 case：
   ```java
   case NAMESPACE_EXISTS:
     if (asNamespaceCatalog != null) {
       CatalogHandlers.namespaceExists(asNamespaceCatalog, namespaceFromPathVars(vars));
       return null;
     }
     break;
   ```
   后端实现了 `SupportsNamespaces` 时调用 `CatalogHandlers.namespaceExists`，否则走默认错误路径。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`

**修改目的**：验证 `namespaceExists` 通过 HEAD 请求实现。

**工作逻辑**：新增测试 `testNamespaceExistsViaHEADRequest`：

1. Mockito.spy 包装 `RESTCatalogAdapter`。
2. 构造 `RESTCatalog` 并 initialize。
3. 调用 `catalog.namespaceExists(Namespace.of("non-existing"))`，断言返回 `false`。
4. `Mockito.verify(adapter).execute(...)` 校验：
   - 第一次 `execute` 是 `GET v1/config`（catalog 初始化拉取配置）；
   - 第二次 `execute` 是 **`HEAD v1/namespaces/non-existing`**（namespace 存在性检查确实走了 HEAD 方法与正确路径）。

测试从客户端视角锁死"namespaceExists 必须发 HEAD 而非 GET"的行为契约。

## 小结

- **成效**：REST Catalog 的 namespace 存在性检查现在与 table、view 一致，走 HTTP HEAD 请求，避免拉取 namespace 属性，降低网络开销并使协议语义统一。服务端、客户端、RESTCatalog 入口、测试适配器、单元测试五处全部补齐。
- **影响范围**：5 个文件、共 61 行新增，无删除。属于功能增强（新增 HEAD 端点能力），向后兼容：旧客户端仍可走 GET `loadNamespaceMetadata`；新客户端若连旧服务端（不支持 HEAD namespace 端点），需 `ErrorHandlers.namespaceErrorHandler` 能正确处理 4xx/405 错误。
- **回迁到 1.4.x 的注意事项**：
  - 与 #11760（view HEAD）是同一系列优化，**建议一并回迁**，使 1.4.x 的 REST Catalog 在 table/view/namespace 三类资源的存在性检查上行为统一。
  - **客户端 / 服务端需配对升级**：1.4.x 客户端 cherry-pick 后若连接的服务端尚未支持 `HEAD /v1/namespaces/{namespace}`，会收到 4xx/405 错误。需确认 1.4.x 的 `ErrorHandlers.namespaceErrorHandler` 能把这类错误正确翻译成 `NoSuchNamespaceException`（否则 `namespaceExists` 会抛非预期异常）。
  - 1.4.x 中 `RESTCatalog` 若与 main 偏离较多，需手工确认 `nsDelegate` 字段与 `asCatalog` 委托链在 1.4.x 中存在且一致；`BaseSessionCatalog.AsCatalog.namespaceExists` 的委托模式在 1.4.x 中应已具备（历史较久）。
  - 测试用例的 Mockito verify 模式依赖 1.4.x 中 `RESTCatalogAdapter.execute` 方法签名与 main 一致（7 个参数：method、path、body、queryParams、responseType、headers、config）。若 1.4.x 签名不同需调整。
  - 若不回迁，1.4.x 用户调用 `namespaceExists` 仍走 `loadNamespaceMetadata`，功能正确但性能稍差，不影响正确性。
