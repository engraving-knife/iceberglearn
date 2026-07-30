# 提交 3650：Core: Fix JdbcCatalog & InMemoryCatalog to prevent dropping parent namespaces with children (#16061)

## 提交信息

- **序号**：3650 / 4088
- **哈希**：ef077f45881d7700160eedfd4c129e64bd3aa93c
- **短哈希**：ef077f458
- **日期**：2026-05-06 07:49:17 -0700
- **作者**：sagib-sqream
- **提交说明**：Core: Fix JdbcCatalog & InMemoryCatalog to prevent dropping parent namespaces with children (#16061)
- **PR/Issue**：#16061（修复 #16060）

## 总体目的

这个提交修复了 `JdbcCatalog` 和 `InMemoryCatalog` 在删除命名空间时未检查子命名空间的问题（issue #16060）。

Iceberg 支持嵌套命名空间（如 `parent.child`）。当用户尝试删除一个父命名空间时，如果该命名空间下还有子命名空间，应当抛出 `NamespaceNotEmptyException` 阻止删除，这与"命名空间下有表时不可删除"的语义一致。然而，`JdbcCatalog` 和 `InMemoryCatalog` 的 `dropNamespace` 方法此前只检查了该命名空间下是否有表（`listTables`），却未检查是否有子命名空间（`listNamespaces`）。这导致用户可以删除一个仍含子命名空间的父命名空间，造成子命名空间成为"孤儿"，破坏 catalog 的数据一致性。

本提交在两个 catalog 的 `dropNamespace` 方法中，于检查表之前新增对子命名空间的检查，确保父命名空间非空（含子命名空间）时抛出异常。

## 如何达成设计目的

在 `JdbcCatalog.dropNamespace` 和 `InMemoryCatalog.dropNamespace` 方法中，在现有的 `listTables` 检查之前，新增 `listNamespaces(namespace)` 检查：若返回的子命名空间列表非空，则抛出 `NamespaceNotEmptyException`，提示包含多少个子命名空间。同时在 `CatalogTests` 基类中新增通用的 `testDropNamespaceWithNestedNamespace` 测试，覆盖该场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java` (+7 lines)

**修改目的**：InMemoryCatalog 删除命名空间前检查子命名空间。

**工作逻辑**：
```java
List<Namespace> childNamespaces = listNamespaces(namespace);
if (!childNamespaces.isEmpty()) {
  throw new NamespaceNotEmptyException(
      "Namespace %s is not empty. Contains %d child namespace(s).",
      namespace, childNamespaces.size());
}
```
该检查置于 `listTables` 检查之前。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` (+7 lines)

**修改目的**：JdbcCatalog 删除命名空间前检查子命名空间。

**工作逻辑**：
```java
List<Namespace> childNamespaces = listNamespaces(namespace);
if (childNamespaces != null && !childNamespaces.isEmpty()) {
  throw new NamespaceNotEmptyException(
      "Namespace %s is not empty. Contains %d child namespace(s).",
      namespace, childNamespaces.size());
}
```
与 InMemoryCatalog 类似，多了 null 检查以适配 JdbcCatalog 的返回值语义。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (+38 lines)

**修改目的**：新增通用的嵌套命名空间删除测试。

**工作逻辑**：`testDropNamespaceWithNestedNamespace` 测试（仅在 `supportsNestedNamespaces()` 为 true 时执行）：
1. 创建父命名空间 `parent` 和子命名空间 `parent.child`。
2. 尝试删除父命名空间，断言抛出 `NamespaceNotEmptyException`。
3. 验证父和子命名空间仍存在。
4. 删除子命名空间，验证成功。
5. 删除父命名空间，验证成功。

### `core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryCatalog.java` (+5 lines)

**修改目的**：启用嵌套命名空间支持标志。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java` (+2/-2 lines)

**修改目的**：调整测试配置。

## 总结

这个提交修复了 `JdbcCatalog` 和 `InMemoryCatalog` 在删除含子命名空间的父命名空间时未阻止的 bug，通过在 `dropNamespace` 中新增 `listNamespaces` 检查确保命名空间完整性。修复与"有表不可删除"的语义保持一致，避免了子命名空间成为孤儿的数据一致性问题。同时在通用 `CatalogTests` 基类中新增测试，确保该行为在所有支持嵌套命名空间的 catalog 中得到验证。
