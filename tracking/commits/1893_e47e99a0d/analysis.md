# 提交 1893：Core: Handle NamespaceNotEmptyException in NamespaceErrorHandler (#12505)

## 提交信息

- **序号**：1893 / 4088
- **哈希**：e47e99a0dd3a500134633df640522c8c8fb56a2b
- **短哈希**：e47e99a0d
- **日期**：2025-03-21 07:09:27 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Handle NamespaceNotEmptyException in NamespaceErrorHandler (#12505)
- **PR/Issue**：#12505

## 总体目的

这个提交修复了 Iceberg REST Catalog 在删除非空命名空间（namespace）时的错误处理问题。当用户尝试删除一个包含表或其他对象的命名空间时，REST 服务器应返回适当的错误响应，客户端应正确解析并抛出 `NamespaceNotEmptyException`。

在此提交之前，存在两个问题：

1. **错误码不一致**：`RESTCatalogAdapter`（测试用的 REST 服务器适配器）将 `NamespaceNotEmptyException` 映射为 HTTP 400 状态码，但根据 REST 规范，删除非空命名空间应返回 409 Conflict（因为命名空间存在，但其状态不允许删除操作）。同时 `NamespaceErrorHandler` 只处理 404 和 409，不处理 400 中的 `NamespaceNotEmptyException`。

2. **缺少专用错误处理器**：通用的 `NamespaceErrorHandler` 被用于所有命名空间操作（包括删除），但删除操作需要特殊处理——当命名空间非空时，REST 服务器返回的错误需要被转换为 `NamespaceNotEmptyException`。原有处理器在收到 409 时会抛出 `AlreadyExistsException`，这与删除非空命名空间的语义不匹配。

## 如何达成设计目的

整体设计思路是：

1. 新增一个 `DropNamespaceErrorHandler`，继承自 `NamespaceErrorHandler`，专门处理删除命名空间操作中的 409 Conflict 响应，将其转换为 `NamespaceNotEmptyException`。

2. 在 `RESTSessionCatalog` 的 `dropNamespace` 方法中，使用新的 `dropNamespaceErrorHandler()` 替代通用的 `namespaceErrorHandler()`。

3. 同时增强 `NamespaceErrorHandler`，使其能处理 HTTP 400 中 type 为 `NamespaceNotEmptyException` 的响应（兼容不同 REST 服务器实现）。

4. 修正 `RESTCatalogAdapter` 中的错误码映射，将 `NamespaceNotEmptyException` 从 400 改为 409。

5. 新增测试用例验证非空命名空间删除会抛出正确的异常。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (修改, +26/-1 lines)

**修改目的**：新增删除命名空间专用的错误处理器，并增强通用命名空间错误处理器。

**工作逻辑**：

1. 新增 `dropNamespaceErrorHandler()` 静态工厂方法，返回 `DropNamespaceErrorHandler` 实例。

2. 修改 `NamespaceErrorHandler` 的注释，明确其用于 create-read-update 操作（而非删除）。在 `accept` 方法中新增对 HTTP 400 的处理：当 error type 为 `NamespaceNotEmptyException` 时抛出该异常，否则抛出 `BadRequestException`。

3. 新增 `DropNamespaceErrorHandler` 内部类，继承 `NamespaceErrorHandler`。它重写 `accept` 方法：当 HTTP 状态码为 409 时，抛出 `NamespaceNotEmptyException`；其他情况委托给父类处理。这样删除非空命名空间时会正确返回 `NamespaceNotEmptyException` 而非 `AlreadyExistsException`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (修改, +2/-1 lines)

**修改目的**：在删除命名空间操作中使用专用的错误处理器。

**工作逻辑**：将 `dropNamespace` 方法中的 `ErrorHandlers.namespaceErrorHandler()` 改为 `ErrorHandlers.dropNamespaceErrorHandler()`，确保删除非空命名空间时能正确抛出 `NamespaceNotEmptyException`。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +25/-0 lines)

**修改目的**：新增删除非空命名空间的测试用例。

**工作逻辑**：新增 `testDropNonEmptyNamespace` 测试方法。测试步骤：创建命名空间 → 在其中创建表 → 尝试删除命名空间，验证抛出 `NamespaceNotEmptyException` 且消息包含 "is not empty" → 删除表 → 再次删除命名空间，验证成功返回 true → 验证命名空间不再存在。这个测试确保所有 Catalog 实现都能正确处理删除非空命名空间的场景。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (修改, +2/-1 lines)

**修改目的**：修正 `NamespaceNotEmptyException` 的 HTTP 状态码映射。

**工作逻辑**：将 `NamespaceNotEmptyException` 的映射从 HTTP 400 改为 HTTP 409，符合 REST 规范中 "Conflict" 的语义——资源存在但因当前状态无法执行删除。同时移除了原有的 TODO 注释。

## 总结

本提交通过新增专用的 `DropNamespaceErrorHandler` 和修正 HTTP 状态码映射，使 Iceberg REST Catalog 在删除非空命名空间时能正确抛出 `NamespaceNotEmptyException`，提升了错误处理的准确性和 REST API 规范的符合性。新增的测试用例确保了该行为在所有 Catalog 实现中的一致性。
