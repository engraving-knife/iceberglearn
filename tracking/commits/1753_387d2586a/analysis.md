# 提交 1753：Core: Fallback to GET requests for namespace/table/view exists checks (#12314)

## 提交信息

- **序号**：1753 / 4088
- **哈希**：387d2586aa4951d68bfea9a1cea699846d50aba9
- **短哈希**：387d2586a
- **日期**：2025-02-19 10:09:16 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Fallback to GET requests for namespace/table/view exists checks (#12314)
- **PR/Issue**：#12314

## 总体目的

本提交旨在修复 REST Catalog 客户端在与旧版本（1.7.x 及更早）REST 服务器交互时的兼容性问题。

Iceberg REST Catalog 规范中定义了 HEAD 请求端点（`V1_TABLE_EXISTS`、`V1_NAMESPACE_EXISTS`、`V1_VIEW_EXISTS`）用于检查表/命名空间/视图是否存在。这些 HEAD 端点是在较新版本中引入的。之前的客户端实现使用 `Endpoint.check()` 方法来检查服务器是否支持这些端点——如果服务器不支持，`Endpoint.check()` 会直接抛出异常，导致 `tableExists`/`namespaceExists`/`viewExists` 方法在旧服务器上无法工作。

本提交将强制性的端点检查改为优雅降级（fallback）逻辑：如果服务器支持 HEAD 端点则使用 HEAD 请求，否则回退到 GET 请求（通过父类的 `loadTable`/`loadNamespaceMetadata`/`loadView` 方法）来判断存在性。这确保了客户端与 1.7.x 及更早版本的 REST 服务器的向后兼容。

## 如何达成设计目的

提交修改了 `RESTSessionCatalog` 中三个 `*Exists` 方法的实现策略：

对于每个方法（`tableExists`、`namespaceExists`、`viewExists`），将原来的 `Endpoint.check(endpoints, Endpoint.V1_*_EXISTS)` 强制检查替换为条件判断 `if (endpoints.contains(Endpoint.V1_*_EXISTS))`：
- 如果服务器在配置响应中声明支持 HEAD 端点，则使用 HEAD 请求（效率更高，不返回响应体）。
- 如果不支持，则回退到 `super.tableExists()` / `super.namespaceExists()` / `super.viewExists()`，这些方法内部会通过 GET 请求加载资源，根据是否抛出 `NotFoundException` 来判断存在性。

同时新增了相应的测试用例来验证三种场景：HEAD 请求正常工作、GET 回退正常工作、命名空间/表/视图的回退逻辑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`（修改, +22/-13 lines）

**修改目的**：为 `tableExists`、`namespaceExists`、`viewExists` 方法添加向后兼容的回退逻辑。

**工作逻辑**：三个方法的修改模式一致：

1. **`tableExists`**：移除 `Endpoint.check(endpoints, Endpoint.V1_TABLE_EXISTS)`，改为 `if (endpoints.contains(Endpoint.V1_TABLE_EXISTS))` 条件判断。支持时用 HEAD 请求 `paths.table(identifier)`，不支持时调用 `super.tableExists(context, identifier)`。注释说明回退是为了兼容 1.7.x 及更早版本的服务器。

2. **`namespaceExists`**：同样移除强制端点检查，改为条件判断。支持时用 HEAD 请求 `paths.namespace(namespace)`，不支持时调用 `super.namespaceExists(context, namespace)`。

3. **`viewExists`**：同样模式。支持时用 HEAD 请求 `paths.view(identifier)`，不支持时调用 `super.viewExists(context, identifier)`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java`（修改, +122/-0 lines）

**修改目的**：为 table 和 namespace 的存在性检查添加测试。

**工作逻辑**：新增三个测试方法：

1. **`testNamespaceExistsFallbackToGETRequest`**：模拟服务器只返回 `V1_LOAD_NAMESPACE` 端点（不包含 `V1_NAMESPACE_EXISTS`），验证 `namespaceExists` 会回退到 GET 请求 `v1/namespaces/non-existing`，而非 HEAD 请求。

2. **`testTableExistsViaHEADRequest`**：验证当服务器支持 `V1_TABLE_EXISTS` 时，`tableExists` 使用 HEAD 请求 `v1/namespaces/newdb/tables/table`。

3. **`testTableExistsFallbackToGETRequest`**：模拟服务器只返回 `V1_LOAD_TABLE` 端点，验证 `tableExists` 回退到 GET 请求。

测试通过 Mockito spy 拦截 `RESTCatalogAdapter.execute()`，自定义 `v1/config` 响应中的端点列表，然后验证实际发出的 HTTP 请求方法（GET vs HEAD）和路径。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTViewCatalog.java`（修改, +46/-0 lines）

**修改目的**：为 view 的存在性检查回退添加测试。

**工作逻辑**：新增 `viewExistsFallbackToGETRequest` 测试方法，模拟服务器只返回 `V1_LOAD_VIEW` 端点，验证 `viewExists` 回退到 GET 请求 `v1/namespaces/ns/views/view`。

## 小结

- **成效**：修复了 REST Catalog 客户端与旧版本（1.7.x 及更早）REST 服务器的兼容性，使 `tableExists`/`namespaceExists`/`viewExists` 在服务器不支持 HEAD 端点时自动回退到 GET 请求。
- **影响范围**：修改 Core 模块的 REST Catalog 实现，影响所有使用 REST Catalog 的场景。这是对客户端行为的向后兼容性改进。
- **回迁到 1.4.x 的注意事项**：此修复改善向后兼容性，回迁到 1.4.x 是合理且有益的。需注意 1.4.x 分支的 `RESTSessionCatalog` 代码结构是否与此修改一致，特别是 `endpoints` 字段的类型和 `contains` 方法是否可用。如果 1.4.x 中已有类似的端点检查逻辑，则需要相应调整。无特殊前置依赖。
