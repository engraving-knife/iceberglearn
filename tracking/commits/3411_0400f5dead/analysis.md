# 提交 3411：Spark: Explicitly disallow migrating bucketed tables (#15429)

## 提交信息

- **序号**：3411 / 4088
- **哈希**：0400f5dead809a386637cefa35bde24d6ef2fd72
- **短哈希**：0400f5dead
- **日期**：2026-03-17 21:45:46 -0500
- **作者**：Rui Li
- **提交说明**：Spark: Explicitly disallow migrating bucketed tables (#15429)
- **PR/Issue**：#15429

## 总体目的

显式禁止将 Hive 桶表（bucketed tables）迁移为 Iceberg 表。此前迁移桶表时不会报错但会产生不正确的结果，因为 Iceberg 的分区规范与 Hive 的桶概念不兼容。现在在迁移操作开始前就检查源表是否为桶表，如果是则抛出明确的错误信息。

## 如何达成设计目的

1. 在 `BaseTableCreationSparkAction` 的 `checkSourceCatalog` 方法中新增 `Preconditions.checkArgument` 检查
2. 检查 `sourceCatalogTable.bucketSpec().isEmpty()`，若不为空则抛出包含 bucketSpec 信息的异常
3. 在所有 4 个 Spark 版本（3.4、3.5、4.0、4.1）中应用相同的改动
4. 为每个版本新增测试验证桶表迁移被拒绝

## 修改详情

### `spark/v{3.4,3.5,4.0,4.1}/spark/src/main/java/org/apache/iceberg/spark/actions/BaseTableCreationSparkAction.java` (+4 lines each)

**修改目的**：在表创建前检查源表是否为桶表。

**工作逻辑**：
```java
Preconditions.checkArgument(
    sourceCatalogTable.bucketSpec().isEmpty(),
    "Cannot create an Iceberg table from a bucketed source table: %s",
    (Object) sourceCatalogTable.bucketSpec().getOrElse(() -> null));
```
在已有的位置检查之后，新增桶表检查。如果 `bucketSpec()` 不为空，抛出 `IllegalArgumentException` 并显示 bucketSpec 信息。

### `spark/v{3.4,3.5,4.0,4.1}/spark/src/test/java/org/apache/iceberg/spark/extensions/TestMigrateTableProcedure.java` (+17 lines each)

**修改目的**：新增测试验证桶表迁移被拒绝。

**工作逻辑**：
- 每个版本新增测试方法，创建桶表后尝试迁移，验证抛出包含 "bucketed" 的错误信息

## 总结

本提交在 Spark 所有 4 个版本中显式禁止了桶表到 Iceberg 的迁移操作。通过在 `BaseTableCreationSparkAction` 中新增前置检查，在迁移开始前就拒绝桶表，避免产生不正确的结果。每个版本都新增了对应的测试。
