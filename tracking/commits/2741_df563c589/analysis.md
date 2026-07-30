# 提交 2741：Core: Refactor: Separate Route from RESTCatalogAdapter

## 提交信息

- **序号**：2741 / 4088
- **哈希**：df563c589c40f49767a0f42b2c3c4f4071ff5e70
- **短哈希**：df563c589
- **日期**：2025-10-13 09:20:34 -0600
- **作者**：gaborkaszab
- **提交说明**：Core: Refactor: Separate Route from RESTCatalogAdapter
- **PR/Issue**：#14313

## 总体目的

这是一个纯重构提交，将 `RESTCatalogAdapter` 中内嵌的 `Route` 枚举提取为独立的顶级类文件。`RESTCatalogAdapter` 是 Iceberg REST 测试基础设施的核心类，用于将 REST 请求适配为 Catalog API 调用。其中的 `Route` 枚举定义了所有 REST API 路由（如 TOKENS、CONFIG、LIST_NAMESPACES、CREATE_TABLE 等），包含路由匹配、路径变量解析等逻辑，代码量近 150 行。

随着 REST Catalog 规范的扩展和路由数量的增加，`Route` 枚举的体积已经使其与 `RESTCatalogAdapter` 的主逻辑（请求处理和 Catalog API 调用）耦合度过高，降低了代码可读性。将 `Route` 分离为独立的类文件有助于：提升代码组织清晰度，使 `RESTCatalogAdapter` 专注于适配逻辑，便于后续对路由匹配逻辑的独立修改和测试，减少 `RESTCatalogAdapter` 的编译依赖。

## 如何达成设计目的

重构步骤：
1. 创建新的 `Route.java` 文件，将 `Route` 枚举及其所有字段、构造函数、方法原样移出
2. 将原先引用 `RESTCatalogAdapter.Route` 的地方改为直接引用 `Route`
3. 将 `Route` 内部使用的 `SLASH` 常量从 `RESTCatalogAdapter` 的静态字段改为 `Route` 内部的内联调用（`Splitter.on('/')`）
4. 更新 `RESTCatalogAdapter` 中对 `Route` 成员的访问方式：`r.method` 改为 `r.method()`，`r.resourcePath` 改为 `r.resourcePath()`（因为跨类访问需要通过方法而非直接字段访问）
5. 清理不再需要的 import

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/Route.java` (新文件, +197 lines)

**修改目的**：将 Route 枚举提取为独立的顶级类。

**工作逻辑**：包含原先内嵌在 `RESTCatalogAdapter` 中的全部 `Route` 枚举定义——所有路由常量（TOKENS、CONFIG、LIST_NAMESPACES 等 26 个路由）、字段（method、requiredLength、requirements、variables、requestClass、responseClass、resourcePath）、构造函数（解析路由模式为 requirements 和 variables）、`matches()` 方法（匹配 HTTP 方法和路径）、`variables()` 方法（提取路径变量）、`from()` 静态方法（从请求查找匹配路由）、getter 方法。`SLASH` 常量不再作为类级静态字段，改为内联使用 `Splitter.on('/')`。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+4/-152 lines)

**修改目的**：移除内嵌的 Route 枚举，改为引用独立的 Route 类。

**工作逻辑**：
- 删除 `Route` 枚举的全部定义（约 147 行）
- 删除 `SLASH` 静态常量和相关 import（`Splitter`、多个 response 类的 import）
- 将 `Route.values()` 中的 `r.method` 和 `r.resourcePath` 改为方法调用 `r.method()` 和 `r.resourcePath()`（因为 Route 现在是独立类，字段为 private，需通过 getter 访问）

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogServlet.java` (+0/-1 lines)

**修改目的**：更新 import 引用。

**工作逻辑**：移除 `import org.apache.iceberg.rest.RESTCatalogAdapter.Route`，因为 Route 现在与 RESTCatalogServlet 在同一包中，可直接引用。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+6/-6 lines)

**修改目的**：更新测试中对 Route 的引用。

**工作逻辑**：将 `RESTCatalogAdapter.Route.LIST_TABLES` 改为 `Route.LIST_TABLES`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java` (+3/-3 lines)

**修改目的**：更新测试中对 Route 的引用。

**工作逻辑**：将 `RESTCatalogAdapter.Route.LIST_VIEWS` 改为 `Route.LIST_VIEWS`（3 处）。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalogWithAssumedViewSupport.java` (+2/-2 lines)

**修改目的**：更新测试中对 Route 的引用。

**工作逻辑**：移除 `import static org.apache.iceberg.rest.RESTCatalogAdapter.Route.CONFIG`，将 `CONFIG == route` 改为 `Route.CONFIG == route`。

## 总结

本提交是纯粹的代码重构，将 `RESTCatalogAdapter` 中内嵌的 `Route` 枚举提取为独立的顶级类文件。不改变任何功能逻辑，仅改善代码组织和可维护性。重构后 `RESTCatalogAdapter` 减少了约 150 行代码，职责更加清晰。此类重构为后续 REST Catalog 相关的独立开发和测试奠定了更好的代码结构基础。
