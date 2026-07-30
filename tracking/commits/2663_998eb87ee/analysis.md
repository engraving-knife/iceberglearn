# 提交 2663：BigQuery: Add table validity check for BigQueryMetastoreCatalog (#14113)

## 提交信息

- **序号**：2663 / 4088
- **哈希**：998eb87ee295cb82e02107ca25d147c31a29256c
- **短哈希**：998eb87ee
- **日期**：2025-09-19 13:46:19 -0700
- **作者**：Thomas
- **提交说明**：BigQuery: Add table validity check for BigQueryMetastoreCatalog (#14113)
- **PR/Issue**：#14113
- **共同作者**：Eduard Tudenhoefner

## 总体目的

本提交为 `BigQueryMetastoreCatalog` 添加了表标识符有效性检查方法 `isValidIdentifier`。BigQuery Metastore 不支持多层命名空间（multi-layer namespaces），只支持 `dataset.table` 形式的单层命名空间。在此之前，当用户尝试使用多层命名空间（如 `level1.level2.table`）访问表时，系统会在后续操作中抛出异常，而不是在早期就拒绝无效的标识符。

通过覆写 `isValidIdentifier` 方法，catalog 可以在操作早期就判断标识符是否有效——如果命名空间不是单层的，直接返回 `false`，从而避免后续不必要的操作和更友好的错误处理。这是 `BaseMetastoreCatalog` 提供的扩展点，子类可以覆写以实现特定于后端的有效性检查逻辑。

此外，本提交还重新启用了一个之前被禁用的测试 `testLoadMetadataTable`（移除了 `@Disabled` 注解），因为添加有效性检查后该测试可以正常通过。

## 如何达成设计目的

通过两个文件的修改完成：

1. 在 `BigQueryMetastoreCatalog.java` 中覆写 `isValidIdentifier` 方法，通过调用 `validateNamespace` 来检查命名空间是否为单层，如果抛出 `IllegalArgumentException` 则返回 `false`。
2. 在 `TestBigQueryCatalog.java` 中新增四个测试用例验证不同命名空间场景下的有效性判断，同时移除 `testLoadMetadataTable` 的 `@Disabled` 注解。

## 修改详情

### `bigquery/src/main/java/org/apache/iceberg/gcp/bigquery/BigQueryMetastoreCatalog.java` (+10/-0 lines)

**修改目的**：添加表标识符有效性检查。

**工作逻辑**：覆写 `isValidIdentifier(TableIdentifier identifier)` 方法。该方法尝试调用 `validateNamespace(identifier.namespace())` 来验证命名空间。如果验证通过（即命名空间是有效的单层命名空间），返回 `true`；如果抛出 `IllegalArgumentException`（即命名空间是多层或空的），捕获异常并返回 `false`。这种方法利用了已有的 `validateNamespace` 方法的一致性检查逻辑，避免重复实现验证规则。

### `bigquery/src/test/java/org/apache/iceberg/gcp/bigquery/TestBigQueryCatalog.java` (+32/-4 lines)

**修改目的**：添加有效性检查的测试用例，并重新启用之前禁用的测试。

**工作逻辑**：
- 导入 `assertThat`
- 移除 `testLoadMetadataTable` 的 `@Disabled("BigQuery Metastore does not support multi layer namespaces")` 注解和空方法体（因为有了有效性检查，该测试现在可以正常运行）
- 新增四个测试方法：
  - `testIsValidIdentifierWithValidSingleLevelNamespace`：验证 `dataset1.table1`（单层命名空间）返回 `true`
  - `testIsValidIdentifierWithInvalidMultiLevelNamespace`：验证 `level1.level2.table1`（双层命名空间）返回 `false`
  - `testIsValidIdentifierWithThreeLevelNamespace`：验证三层命名空间返回 `false`
  - `testIsValidIdentifierWithEmptyNamespace`：验证空命名空间返回 `false`

## 总结

本提交为 BigQueryMetastoreCatalog 添加了表标识符有效性检查，使系统能够在操作早期就拒绝不支持的多层命名空间标识符。通过覆写 `isValidIdentifier` 方法并利用已有的 `validateNamespace` 逻辑，实现简洁有效。同时重新启用了之前因多层命名空间问题被禁用的 `testLoadMetadataTable` 测试，并新增了四个针对性的测试用例覆盖各种命名空间场景。
