# 提交 0571：为重命名表到不存在命名空间添加测试

## 提交信息

- **序号**：0571 / 4088
- **哈希**：afb200041b17a3f8cce792848ca3abc2bab9bd75
- **短哈希**：afb200041
- **日期**：2024-03-08 09:28:59 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Add test for renaming table to a non-existing namespace (#9895)
- **PR/Issue**：#9895

## 总体目的

本提交旨在补齐 Iceberg 各 Catalog 在「重命名表到目标命名空间不存在」这一场景下的行为契约，并通过统一测试用例强制约束所有 Catalog 实现都应抛出 `NoSuchNamespaceException`。

背景动机：在 `CatalogTests`（作为所有 Catalog 的共享测试基类）此前已存在 `testRenameTableDestinationTableAlreadyExists`、`testRenameTableSourceTableDoesNotExist` 等用例，但缺少「目标命名空间不存在」这一边界条件的测试。不同 Catalog 实现在该场景下的行为并不一致——尤其是 `HiveCatalog` 在重命名表时如果目标数据库（HMS 层面对应 namespace）不存在，并不会立即抛出 `NoSuchNamespaceException`，而是依赖底层 HMS 抛出更含糊的异常。这种行为与 Iceberg Catalog 接口契约不一致，会让上层调用方难以判断失败原因。

## 如何达成设计目的

提交采取「测试先行 + 实现跟进」的方式：

1. 在 `CatalogTests`（抽象测试基类）中新增 `renameTableNamespaceMissing` 测试，所有继承它的 Catalog 子类测试都会自动跑这个用例，从而把契约写入测试基线。
2. 修改 `HiveCatalog.renameTable` 的实现，在调用 HMS `alterTable` 之前显式检查目标命名空间是否存在，若不存在则直接抛出包含明确信息的 `NoSuchNamespaceException`，让 Hive 的行为与契约对齐。

这种「共享测试 + 针对性修复」的方式确保契约可被所有 Catalog 实现统一验证，避免出现「主仓测试通过但下游 Catalog 仍带病运行」的情况。

## 修改详情

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：在共享测试基类中加入「重命名表到不存在命名空间」的用例，约束所有 Catalog 实现的行为契约。

**工作逻辑**：新增 `renameTableNamespaceMissing` 测试方法，逻辑如下：
- 准备源表 `ns.table`：若 `requiresNamespaceCreate()` 为真（即 Catalog 需要显式创建命名空间），先调用 `createNamespace(from.namespace())`；然后通过 `catalog().buildTable(from, SCHEMA).create()` 创建源表，并断言源表确实存在。
- 准备目标 `non_existing.renamedTable`：故意不创建 `non_existing` 命名空间。
- 调用 `catalog().renameTable(from, to)` 并断言抛出 `NoSuchNamespaceException`，且异常消息包含 `"Namespace does not exist: non_existing"`，从而同时约束异常类型和异常文本。

该方法紧邻 `testRenameTableSourceTableDoesNotExist` 与 `testRenameTableDestinationTableAlreadyExists` 之间放置，与既有 rename 系列用例形成完整的「源不存在 / 目标已存在 / 目标命名空间不存在」三角覆盖。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：让 `HiveCatalog.renameTable` 在目标命名空间不存在时主动抛出 `NoSuchNamespaceException`，而非把判断推迟到 HMS 调用阶段。

**工作逻辑**：在 `renameTable` 方法中，参数校验（`isValidIdentifier`）之后、真正调用 HMS `alterTable` 之前，插入一段前置检查：

```java
if (!namespaceExists(to.namespace())) {
  throw new NoSuchNamespaceException(
      "Cannot rename %s to %s. Namespace does not exist: %s", from, to, to.namespace());
}
```

- 通过 `namespaceExists(to.namespace())` 复用 HiveCatalog 既有方法判断目标命名空间是否存在。
- 若不存在，使用 `NoSuchNamespaceException`（`org.apache.iceberg.exceptions.NoSuchNamespaceException`）抛出，消息格式 `Cannot rename %s to %s. Namespace does not exist: %s`，把源、目标和缺失命名空间都带上，便于定位。
- 该异常消息中的 `Namespace does not exist: non_existing` 子串正好对应测试断言中的 `hasMessageContaining("Namespace does not exist: non_existing")`，保证测试稳定通过。

修复点放在 `alterTable` 之前，可以避免 HMS 在内部产生含糊异常（例如 `InvalidOperationException` 或 `NoSuchObjectException`），让上层调用方拿到的是符合 Iceberg 契约的语义化异常。

## 小结

- **成效**：通过新增共享测试，把「目标命名空间不存在时 renameTable 必须抛 NoSuchNamespaceException」这一契约固化为可执行测试；同时修复了 `HiveCatalog` 不符合契约的实现，使其行为与其它 Catalog 对齐。
- **影响范围**：`CatalogTests` 是被多个 Catalog 测试继承的抽象基类（包括 `TestHiveCatalog`、`TestJdbcCatalog`、`TestNessieCatalog` 等），新增测试会自动覆盖所有继承它的 Catalog 实现，因此该 PR 可能暴露其它 Catalog 实现在该场景下的潜在 bug（但本次只修复 Hive）。
- **回迁到 1.4.x 注意事项**：本提交改动小、风险低，主要是新增一个测试方法和一段前置校验，没有 API 破坏性变更，回迁非常安全。需确认 1.4.x 分支的 `CatalogTests` 与 `HiveCatalog.renameTable` 的代码上下文与本提交基于的版本一致（即已存在 `namespaceExists` 方法和 `buildTable` API），否则需做小幅适配。另外，回迁后应跑一遍所有 Catalog 子类的测试套件，确认没有因为契约收紧而导致其它 Catalog 测试失败。
