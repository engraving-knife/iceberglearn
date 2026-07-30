# 提交 3759：Flink: Handle table comments in FlinkSQL (#16423)

## 提交信息

- **序号**：3759 / 4088
- **哈希**：693e8a787fd77d327f7d482c156f82ae711ea0d0
- **短哈希**：693e8a787
- **日期**：2026-05-21 07:29:43 +0200
- **作者**：Stepan Stepanishchev
- **提交说明**：Flink: Handle table comments in FlinkSQL (#16423)
- **PR/Issue**：#16423

## 总体目的

这个提交修复了 Flink SQL 中创建表时表注释（table comment）丢失的问题。在 Flink SQL 中，用户可以通过 `CREATE TABLE tl(id BIGINT) COMMENT 'table comment'` 语句为表添加注释。然而，Iceberg 的 `FlinkCatalog` 在创建表时没有将这个注释传递给 Iceberg 表属性，导致注释信息丢失。

Iceberg 中表注释通过 `TableProperties.COMMENT` 属性存储。Flink 的 `CatalogTable` 通过 `getComment()` 方法提供注释信息，但 `FlinkCatalog.createTable` 方法在将 Flink 表定义转换为 Iceberg 表创建参数时，忽略了注释字段。

## 如何达成设计目的

在 `FlinkCatalog.createTable` 方法中，在构建表属性（properties）的过程中，检查 `table.getComment()` 返回的注释值，如果非空则将其放入 `TableProperties.COMMENT` 属性中。这样 Iceberg 在创建表时就会将注释作为表属性持久化。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (+6/-0 lines)

**修改目的**：在创建 Iceberg 表时将 Flink 表注释写入表属性。

**工作逻辑**：
在 `createTable` 方法中，在调用 `icebergCatalog.createTable` 之前，新增注释处理逻辑：

```java
String comment = table.getComment();
if (comment != null && !comment.isEmpty()) {
  properties.put(TableProperties.COMMENT, comment);
}
```

其中 `table` 是 Flink 的 `CatalogTable`，`properties` 是正在构建的 Iceberg 表属性 `Map`。通过将注释放入 `TableProperties.COMMENT`（即 `"comment"` 键），Iceberg 会在元数据中持久化该注释。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+19/-0 lines)

**修改目的**：测试创建表和修改表注释的功能。

**工作逻辑**：
新增两个测试方法：
1. `testCreateTableWithTableComment`：执行 `CREATE TABLE tl(id BIGINT) COMMENT 'table comment'`，验证表属性中包含 `TableProperties.COMMENT` 且值为 `"table comment"`。
2. `testAlterTableModifyTableComment`：先创建带注释的表，再执行 `ALTER TABLE tl SET('comment' = 'new comment')` 修改注释，验证属性值更新为 `"new comment"`。

第二个测试验证修改注释的功能也正常工作（这部分逻辑应该已经在已有代码中支持）。

## 总结

这个提交修复了 Flink SQL 中表注释丢失的问题，通过在 `FlinkCatalog.createTable` 中将 Flink 表注释写入 Iceberg 的 `TableProperties.COMMENT` 属性，确保用户通过 SQL 创建表时指定的注释能够正确持久化。这是一个小但重要的用户体验改进，使 Flink SQL 的表注释功能与 Iceberg 的元数据管理正确对接。
