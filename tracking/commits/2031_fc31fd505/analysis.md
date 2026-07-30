# 提交 2031：Core: Add test when listing namespaces with a non-existing namespace

## 提交信息

- **序号**：2031 / 4088
- **哈希**：fc31fd5056b47dd2f3044d2569d7594027de0417
- **短哈希**：fc31fd505
- **日期**：2025-04-23 16:44:56 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add test when listing namespaces with a non-existing namespace (#12873)
- **PR/Issue**：#12873

## 总体目的

Iceberg 的 Catalog 抽象约定：当调用 `listNamespaces` 列出一个不存在的命名空间时，应当抛出 `NoSuchNamespaceException`。然而此前测试套件中并没有专门覆盖这一边界场景的测试用例，导致各 Catalog 实现对该约定的遵守情况无法被自动验证。

本提交在通用的 `CatalogTests` 抽象测试基类中新增一个测试用例 `testListNonExistingNamespace`，用于验证列出不存在的命名空间时会抛出 `NoSuchNamespaceException`。由于所有继承 `CatalogTests` 的 Catalog 实现都会自动继承该测试，这一改动统一强化了对 Catalog 契约的覆盖。

同时，对于目前尚未符合该契约的两个 Catalog 实现（Hive 和 Nessie），通过 `@Disabled` 注解并附说明的方式暂时跳过该测试，明确记录了它们当前返回空列表而非抛出异常的已知差异，为后续修复留下追踪线索。

## 如何达成设计目的

设计思路遵循 Iceberg 测试体系的既有模式：

1. 在 `CatalogTests`（所有 Catalog 的通用抽象测试基类）中添加通用测试方法 `testListNonExistingNamespace`，断言对不存在的命名空间 `Namespace.of("non-existing")` 调用 `listNamespaces` 会抛出 `NoSuchNamespaceException`，且消息为 "Namespace does not exist: non-existing"。

2. 对于当前行为不符合该契约的 `TestHiveCatalog` 和 `TestNessieCatalog`，重写该方法并用 `@Disabled` 注解标记，注明"目前返回空列表而非抛出 NoSuchNamespaceException"，使测试在统一基类中默认启用、在已知不兼容的实现中被显式跳过。

关键组件协作：
- `CatalogTests`：定义通用契约测试，所有 Catalog 实现继承。
- `TestHiveCatalog` / `TestNessieCatalog`：继承 `CatalogTests`，针对各自已知差异禁用特定测试。

## 修改详情

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (修改, +7/-0 lines)

**修改目的**：在通用 Catalog 测试基类中新增列出不存在的命名空间的契约测试。

**工作逻辑**：
新增测试方法 `testListNonExistingNamespace`，使用 AssertJ 的 `assertThatThrownBy` 断言调用 `catalog().listNamespaces(Namespace.of("non-existing"))` 会抛出 `NoSuchNamespaceException`，并验证异常消息为 "Namespace does not exist: non-existing"。由于该基类被所有 Catalog 实现的测试类继承，此测试会自动应用到所有 Catalog 实现上。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java` (修改, +8/-0 lines)

**修改目的**：对 Hive Catalog 暂时禁用该契约测试，并记录已知差异。

**工作逻辑**：
引入 `org.junit.jupiter.api.Disabled` 导入，并重写 `testListNonExistingNamespace` 方法，使用 `@Override` 和 `@Disabled("Hive currently returns an empty list instead of throwing a NoSuchNamespaceException")` 注解，方法体仅调用 `super.testListNonExistingNamespace()`（实际不会执行，因为被禁用）。这表明 Hive Metastore 当前对不存在的命名空间返回空列表而非抛出异常，属于已知的不符合契约的行为，留待后续修复。

### `nessie/src/test/java/org/apache/iceberg/nessie/TestNessieCatalog.java` (修改, +7/-0 lines)

**修改目的**：对 Nessie Catalog 暂时禁用该契约测试，并记录已知差异。

**工作逻辑**：
与 Hive Catalog 的处理方式一致，重写 `testListNonExistingNamespace` 方法并标注 `@Disabled("Nessie currently returns an empty list instead of throwing a NoSuchNamespaceException")`，方法体调用 `super.testListNonExistingNamespace()`。表明 Nessie 当前同样返回空列表而非抛出异常，留待后续修复。

## 总结

本提交通过在通用 `CatalogTests` 基类中新增"列出不存在的命名空间应抛出 `NoSuchNamespaceException`"的契约测试，统一强化了对 Catalog 行为的验证覆盖。对于当前尚不符合该契约的 Hive 和 Nessie 两个实现，通过 `@Disabled` 注解明确记录已知差异，既保证了测试套件的运行，又为后续修复提供了清晰的追踪信息。属于测试覆盖增强类提交。
