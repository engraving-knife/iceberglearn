# 提交 2531：Hive: Throw NoSuchViewException when loading an Iceberg table as a view (#13847)

## 提交信息

- **序号**：2531 / 4088
- **哈希**：3e443d582ecd2fd03e998840e45aead3f329bdcd
- **短哈希**：3e443d582
- **日期**：2025-08-19 22:15:49 +0200
- **作者**：Gabriel Igliozzi
- **提交说明**：Hive: Throw NoSuchViewException when loading an Iceberg table as a view (#13847)
- **PR/Issue**：#13847

## 总体目的

在 Hive Metastore 中，Iceberg 表和 Iceberg 视图都通过 HMS 的 `Table` 对象表示，仅靠 `TABLE_TYPE` 参数和 `tableType` 字段（`EXTERNAL_TABLE` vs `VIRTUAL_VIEW`）区分。原有逻辑在加载时只校验"是不是 Iceberg 表/视图"，但不会处理"把表当视图加载"或"把视图当表加载"这种类型不匹配的情况，导致用户在表与视图同名同命名空间切换访问时得到模糊的异常（例如 `Not an iceberg table` 或 `Not an iceberg view`），而非语义正确的 `NoSuchTableException`/`NoSuchViewException`。

本提交在 `HiveTableOperations` 与 `HiveViewOperations` 加载 HMS 表对象的入口处增加交叉校验：
- 通过 `HiveViewOperations` 加载一个 Iceberg 表时，抛出 `NoSuchViewException`（因为对该视角而言"视图不存在"）。
- 通过 `HiveTableOperations` 加载一个 Iceberg 视图时，抛出 `NoSuchTableException`。

这种异常语义更符合 catalog 抽象的契约：用户调用 `loadTable` 期待表，若实际是视图则等同于"表不存在"；反之亦然。

## 如何达成设计目的

- 抽取两个辅助方法 `isValidIcebergView(Table)` 与 `isValidIcebergTable(Table)`，复用类型判断逻辑。
- 新增 `validateIcebergTableNotLoadedAsIcebergView`：当对象是有效 Iceberg 表但不是有效 Iceberg 视图时，抛 `NoSuchViewException`。
- 新增 `validateIcebergViewNotLoadedAsIcebergTable`：当对象是有效 Iceberg 视图但不是有效 Iceberg 表时，抛 `NoSuchTableException`。
- 在 `HiveTableOperations.loadTableMetadata` 与 `HiveViewOperations.loadViewMetadata` 中先调用上述交叉校验，再执行原有的 `validateTableIsIceberg`/`validateTableIsIcebergView` 校验，确保顺序合理且行为兼容。
- 在抽象测试基类 `ViewCatalogTests` 中新增两个测试用例，覆盖两种交叉加载场景，所有支持 table+view 的 catalog（Hive、REST、JDBC 等）都将自动继承该测试。

## 修改详情

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java` (+49)

**修改目的**：为交叉加载场景提供通用测试。

**工作逻辑**：
- `loadViewThatAlreadyExistsAsTable`：先创建表，再尝试以 view 加载，断言抛出 `NoSuchViewException`，且 `viewExists` 返回 false。
- `loadTableThatAlreadyExistsAsView`：先创建视图，再尝试以 table 加载，断言抛出 `NoSuchTableException`，且 `tableExists` 返回 false。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveOperationsBase.java` (+27/-7)

**修改目的**：抽取校验辅助方法并新增交叉校验。

**工作逻辑**：
- `isValidIcebergView` 校验 `tableType == VIRTUAL_VIEW` 且 `TABLE_TYPE_PROP == ICEBERG_VIEW_TYPE_VALUE`。
- `isValidIcebergTable` 校验 `TABLE_TYPE_PROP == ICEBERG_TABLE_TYPE_VALUE`。
- `validateTableIsIceberg` 与 `validateTableIsIcebergView` 改为基于上述辅助方法，行为不变。
- `validateIcebergTableNotLoadedAsIcebergView`：当不是 view 但是 table 时抛 `NoSuchViewException`。
- `validateIcebergViewNotLoadedAsIcebergTable`：当不是 table 但是 view 时抛 `NoSuchTableException`。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+4)

**修改目的**：在表加载入口增加视图交叉校验。

**工作逻辑**：在 `getTable` 之后、`validateTableIsIceberg` 之前调用 `validateIcebergViewNotLoadedAsIcebergTable`，先把"视图当表"的情况拦截掉。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveViewOperations.java` (+4)

**修改目的**：在视图加载入口增加表交叉校验。

**工作逻辑**：在 `getTable` 之后、`validateTableIsIcebergView` 之前调用 `validateIcebergTableNotLoadedAsIcebergView`，先把"表当视图"的情况拦截掉。

## 总结

通过在 Hive 表/视图加载入口增加交叉类型校验，使"将表当视图加载"或"将视图当表加载"时抛出语义正确的 `NoSuchViewException`/`NoSuchTableException`，而不是模糊的"不是 iceberg xx"异常。同时抽取公共校验方法、在通用测试基类中新增覆盖测试，提升了 catalog 行为的一致性与可测试性。
