# 提交 1486：Core: Use HEAD request to check if view exists (#11760)

## 提交信息

- **序号**：1486 / 4088
- **哈希**：3053540c5dc7199d84a6a9bfbe0d3e37efe87990
- **短哈希**：3053540c5
- **日期**：2024-12-12（Thu Dec 12 18:07:04 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Use HEAD request to check if view exists (#11760)
- **PR/Issue**：#11760

## 总体目的

Iceberg 的 REST Catalog 协议为每个资源定义了对应的 HTTP 方法语义，其中"判断资源是否存在"应使用 `HEAD` 请求——只返回状态码，不返回响应体，开销最小。表（table）这一侧早已在 `RESTSessionCatalog.tableExists` 中通过 `client.head(paths.table(identifier), ...)` 走 HEAD 路径，但视图（view）这一侧一直没有对应实现：

- `ViewCatalog` 接口提供的 `viewExists` 默认实现是 `try { loadView(identifier); return true; } catch (NoSuchViewException) { return false; }`，即通过 `GET /v1/namespaces/{ns}/views/{view}` 拉取完整视图定义，再判断是否抛 `NoSuchViewException`。
- `RESTSessionCatalog` 此前未覆盖 `viewExists(SessionContext, TableIdentifier)`，因此最终走的就是上面这个默认实现，每次"判断视图是否存在"都会把整个视图的 SQL 文本、schema、属性等元数据全部下载一遍。

这在视图元数据较大、或频繁检查视图存在性（如 CREATE OR REPLACE VIEW、写入前的预检、引擎元数据刷新）时会造成不必要的网络与序列化开销，且语义上与 REST 规范"用 HEAD 做存在性检查"的约定不一致。

本提交为 REST Catalog 的视图存在性检查补上 HEAD 路径，与表侧实现对齐：

1. `RESTSessionCatalog` 覆盖 `viewExists(SessionContext, TableIdentifier)`，发 `HEAD /v1/namespaces/{ns}/views/{view}` 请求，根据是否抛 `NoSuchViewException` 返回布尔值。
2. `CatalogHandlers` 新增服务端 `viewExists(ViewCatalog, TableIdentifier)` 处理方法，存在则正常返回、不存在则抛 `NoSuchViewException`。
3. `RESTCatalogAdapter`（测试用 REST 服务端适配器）新增 `VIEW_EXISTS` 路由，把 HEAD 请求路由到 `CatalogHandlers.viewExists`。
4. `TestRESTViewCatalog` 新增测试，验证客户端 `viewExists` 调用确实发出 HEAD 请求而非 GET。

## 如何达成设计目的

整体沿用 `tableExists` 已有的成熟模式，把"HEAD 请求 + viewErrorHandler + 捕获 NoSuchViewException"组合搬到视图侧。三层职责清晰：

- **客户端** `RESTSessionCatalog.viewExists`：只发 HEAD 请求，捕获 `NoSuchViewException` 翻译成 `false`，否则 `true`。不解析响应体。
- **服务端** `CatalogHandlers.viewExists`：调用底层 `ViewCatalog.viewExists`（可由后端 catalog 高效实现），不存在则抛 `NoSuchViewException` 让 REST 层转成 404。
- **协议层** `RESTCatalogAdapter`：在路由表中注册 `VIEW_EXISTS(HTTPMethod.HEAD, ResourcePaths.V1_VIEW)`，并在 `execute` switch 中处理该 case，调用 `CatalogHandlers.viewExists`。

`RESTCatalog.viewExists` 已经委托给 `viewSessionCatalog.viewExists`，所以通过 `RESTCatalog` 入口（用户最常用路径）也会自动走新的 HEAD 实现，无需改动 `RESTCatalog`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java`

**修改目的**：新增服务端视图存在性检查处理器。

**工作逻辑**：新增静态方法

```java
public static void viewExists(ViewCatalog catalog, TableIdentifier viewIdentifier) {
  if (!catalog.viewExists(viewIdentifier)) {
    throw new NoSuchViewException("View does not exist: %s", viewIdentifier);
  }
}
```

返回类型为 `void`：视图存在时正常返回（REST 层会回 204 No Content），不存在时抛 `NoSuchViewException`（REST 层会转成 404）。这与同文件中已有的 `tableExists` 处理器模式一致。底层 `catalog.viewExists` 由具体 catalog 实现（如 JdbcCatalog、HadoopCatalog 等可各自高效实现）。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在客户端用 HEAD 请求实现 `viewExists`。

**工作逻辑**：新增方法

```java
@Override
public boolean viewExists(SessionContext context, TableIdentifier identifier) {
  checkViewIdentifierIsValid(identifier);

  try {
    client.head(paths.view(identifier), headers(context), ErrorHandlers.viewErrorHandler());
    return true;
  } catch (NoSuchViewException e) {
    return false;
  }
}
```

- `checkViewIdentifierIsValid` 做标识符合法性预校验（与其它视图方法一致）。
- `client.head(paths.view(identifier), ...)` 发送 HEAD 请求到 `v1/namespaces/{ns}/views/{view}`，不携带请求体、不解析响应体。
- `ErrorHandlers.viewErrorHandler()` 把 404 翻译成 `NoSuchViewException`，被 catch 后返回 `false`。
- 其它错误（如 401/403/500）会按 error handler 抛出对应异常，不被吞掉。

该方法覆盖了 `BaseViewSessionCatalog` 中走默认 `loadView` 实现的 `viewExists`，使 REST 客户端从此走 HEAD 路径。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`

**修改目的**：在测试用 REST 服务端适配器中注册 VIEW_EXISTS 路由。

**工作逻辑**：

1. 在路由枚举中新增：
   ```java
   VIEW_EXISTS(HTTPMethod.HEAD, ResourcePaths.V1_VIEW),
   ```
   注意该枚举项只有 HTTP 方法和路径模板，无请求体/响应类型（HEAD 不带 body），与已有的 `TABLE_EXISTS` 写法一致。

2. 在 `execute` 方法的 switch 中新增 case：
   ```java
   case VIEW_EXISTS:
     {
       if (null != asViewCatalog) {
         CatalogHandlers.viewExists(asViewCatalog, viewIdentFromPathVars(vars));
         return null;
       }
       break;
     }
   ```
   当后端 catalog 实现了 `ViewCatalog` 时，调用 `CatalogHandlers.viewExists`；否则 break 走默认错误路径。`return null` 对应 HEAD 响应无 body。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java`

**修改目的**：验证 `viewExists` 通过 HEAD 请求实现。

**工作逻辑**：新增测试方法 `viewExistsViaHEADRequest`：

1. 用 Mockito.spy 包装 `RESTCatalogAdapter`，以便校验对其 `execute` 的调用。
2. 通过 lambda 把 adapter 作为底层 RESTClient 注入 `RESTCatalog`，并 `initialize`。
3. 创建 namespace `ns`。
4. 调用 `catalog.viewExists(TableIdentifier.of("ns", "view"))`，断言返回 `false`（视图不存在）。
5. 用 `Mockito.verify(adapter).execute(...)` 校验：
   - 第一次 `execute` 是 `GET v1/config`（catalog 初始化时的 config 拉取）；
   - 第二次 `execute` 是 **`HEAD v1/namespaces/ns/views/view`**（即视图存在性检查确实走了 HEAD 方法与正确路径），且不关心请求/响应体类型（全用 `any()`）。

这从客户端视角锁死了"viewExists 必须发 HEAD 而非 GET"这一行为契约，防止未来回退。

新增 import：`org.junit.jupiter.api.Test`（该测试类原本只用了 `@ParameterizedTest`）。

## 小结

- **成效**：REST Catalog 的视图存在性检查现在与表一致，走 HTTP HEAD 请求，避免拉取完整视图定义，降低网络与序列化开销，并与 REST 协议语义对齐；服务端、客户端、测试适配器、单元测试四层都补齐了对应支持。
- **影响范围**：4 个文件、共 60 行新增，无删除。属于功能增强（新增 HEAD 端点能力），向后兼容：旧客户端仍可走 GET `loadView` 路径；新客户端若连旧服务端（不支持 HEAD view 端点），HEAD 请求会得到 405 Method Not Allowed，需 `ErrorHandlers.viewErrorHandler` 能正确处理该错误（实际部署中服务端应同步升级）。
- **回迁到 1.4.x 的注意事项**：
  - 这是性能与语义对齐的改进，**建议回迁**，但需要谨慎评估 1.4.x 的 REST 协议兼容性。
  - **客户端 / 服务端需配对升级**：1.4.x 客户端 cherry-pick 本提交后，若连接的服务端（如生产环境已有的 REST catalog server）尚未支持 `HEAD /v1/namespaces/{ns}/views/{view}` 端点，会收到 4xx/405 错误。需要确认 `ErrorHandlers.viewErrorHandler` 在 1.4.x 上对这类错误能正确翻译成 `NoSuchViewException`（否则 `viewExists` 会抛非预期异常）。
  - 若 1.4.x 的 RESTCatalogAdapter / CatalogHandlers 与 main 偏离较大，需手工对照调整 case 分支与路由枚举的位置。
  - 测试中用到的 Mockito spy + `execute` 校验模式依赖 1.4.x 中 `RESTCatalogAdapter.execute` 的方法签名与 main 一致；若签名不同需调整 verify 参数。
  - 若不回迁，1.4.x 用户调用 `viewExists` 仍走 `loadView`，功能正确但性能稍差，不影响正确性。
