# 提交 2196：Hive: Throw NSNE when listing a non-existing namespace (#13130)

## 提交信息

- **序号**：2196 / 4088
- **哈希**：e5b97d34495f26ba5854a01be5471063e18c5854
- **短哈希**：e5b97d344
- **日期**：2025-06-03 01:49:18 -0400
- **作者**：Jayanth Kumar M J
- **提交说明**：Hive: Throw NSNE when listing a non-existing namespace (#13130)
- **PR/Issue**：#13130

## 总体目的

这个提交修复了 HiveCatalog 在列出不存在的命名空间（namespace）时的行为不符合规范的问题。根据 Iceberg Catalog 接口契约，当调用 `listNamespaces(Namespace)` 列出一个不存在的命名空间时，应当抛出 `NoSuchNamespaceException`（NSNE）。然而 HiveCatalog 此前的实现只检查了命名空间是否"合法"（`isValidateNamespace`），但没有检查命名空间是否"存在"（`namespaceExists`），导致列出一个合法但不存在的不为空命名空间时，Hive 会返回空列表而非抛出异常。这违反了 Catalog 接口规范。本提交修改了条件判断逻辑，使 HiveCatalog 在列出不存在的命名空间时正确抛出 NSNE，并移除了之前因该 bug 而被 `@Disabled` 禁用的测试方法。

## 如何达成设计目的

- 修改 `HiveCatalog.listNamespaces` 方法中的条件判断：当命名空间不为空，且（命名空间不合法 或 命名空间不存在）时，抛出 `NoSuchNamespaceException`。原来的条件仅在不合法且不为空时抛出，现在增加了"不存在"的判断。
- 在 `TestHiveCatalog` 中移除 `@Disabled` 注解和被禁用的 `testListNonExistingNamespace` 测试方法，因为该测试现在可以通过了。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (修改, +1/-1 lines)

**修改目的**：修复列出不存在命名空间时未抛出 NSNE 的问题。

**工作逻辑**：将 `listNamespaces` 方法中的条件从 `if (!isValidateNamespace(namespace) && !namespace.isEmpty())` 改为 `if (!namespace.isEmpty() && (!isValidateNamespace(namespace) || !namespaceExists(namespace)))`。新逻辑先判断命名空间不为空，再判断不合法或不存在（二者满足其一即抛出 NSNE）。调用顺序的调整（先判断 `!namespace.isEmpty()`）也避免了空命名空间时多余的 `namespaceExists` 调用。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java` (修改, +0/-8 lines)

**修改目的**：移除此前因 bug 被禁用的测试方法，使其恢复运行。

**工作逻辑**：
- 移除 `import org.junit.jupiter.api.Disabled;` 导入。
- 移除被 `@Disabled("Hive currently returns an empty list instead of throwing a NoSuchNamespaceException")` 标记的 `testListNonExistingNamespace()` 测试方法。该测试继承自 `CatalogTests` 基类，现在 HiveCatalog 行为已符合规范，基类中的同名测试将正常执行并通过。

## 总结

该提交修复了 HiveCatalog `listNamespaces` 不符合 Catalog 接口规范的行为：列出不存在的命名空间时现在会正确抛出 `NoSuchNamespaceException` 而非返回空列表。修复后，之前因该问题被禁用的测试也得以恢复。这是一个行为正确性修复，可能影响依赖旧有（错误）行为的调用方。
