# 提交 2006：Core: Test loading table/view with non-existing namespace

## 提交信息

- **序号**：2006 / 4088
- **哈希**：c338323b2e9cd8a862fdf328ceacc1b59ea6275a
- **短哈希**：c338323b2
- **日期**：2025-04-16 13:06:53 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Test loading table/view with non-existing namespace (#12812)
- **PR/Issue**：#12812

## 总体目的

本提交为目录测试基类 `CatalogTests` 和视图目录测试基类 `ViewCatalogTests` 新增测试用例，验证当尝试加载不存在的命名空间（namespace）下的表或视图时，目录实现能正确抛出异常。

此前，测试套件中缺少对"加载不存在的命名空间下的表/视图"这一边界场景的测试覆盖。虽然 `tableExists()`/`viewExists()` 应返回 false，但 `loadTable()`/`loadView()` 应抛出 `NoSuchTableException`/`NoSuchViewException`。通过添加这两个测试用例，确保所有目录实现（REST、Hive、JDBC、Nessie 等）在此场景下行为一致。

由于 `CatalogTests` 和 `ViewCatalogTests` 是抽象基类，所有具体的目录测试实现都会自动继承并运行这些新测试，因此一次添加即可覆盖所有目录实现。

## 如何达成设计目的

在 `CatalogTests` 中添加 `testLoadTableWithNonExistingNamespace()` 测试方法，在 `ViewCatalogTests` 中添加 `loadViewWithNonExistingNamespace()` 测试方法。两个测试都使用一个明确不存在的命名空间（`"non-existing"`）来验证行为。

## 修改详情

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (新增, +9 lines)

**修改目的**：添加加载不存在命名空间下的表的测试。

**工作逻辑**：
```java
@Test
public void testLoadTableWithNonExistingNamespace() {
    TableIdentifier ident = TableIdentifier.of("non-existing", "tbl");
    assertThat(catalog().tableExists(ident)).as("Table should not exist").isFalse();
    assertThatThrownBy(() -> catalog().loadTable(ident))
        .isInstanceOf(NoSuchTableException.class)
        .hasMessageStartingWith("Table does not exist: %s", ident);
}
```
测试逻辑：
1. 构造一个位于不存在命名空间 `"non-existing"` 下的表标识符
2. 断言 `tableExists()` 返回 false（表不存在）
3. 断言 `loadTable()` 抛出 `NoSuchTableException`，且消息以 "Table does not exist:" 开头

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java` (新增, +9 lines)

**修改目的**：添加加载不存在命名空间下的视图的测试。

**工作逻辑**：
```java
@Test
public void loadViewWithNonExistingNamespace() {
    TableIdentifier ident = TableIdentifier.of("non-existing", "view");
    assertThat(catalog().viewExists(ident)).as("View should not exist").isFalse();
    assertThatThrownBy(() -> catalog().loadView(ident))
        .isInstanceOf(NoSuchViewException.class)
        .hasMessageStartingWith("View does not exist: %s", ident);
}
```
测试逻辑：
1. 构造一个位于不存在命名空间 `"non-existing"` 下的视图标识符
2. 断言 `viewExists()` 返回 false（视图不存在）
3. 断言 `loadView()` 抛出 `NoSuchViewException`，且消息以 "View does not exist:" 开头

## 总结

本提交为 `CatalogTests` 和 `ViewCatalogTests` 抽象测试基类各添加一个测试用例，验证加载不存在命名空间下的表/视图时能正确抛出 `NoSuchTableException`/`NoSuchViewException` 异常。由于这些是抽象基类，所有目录实现自动获得此测试覆盖。
