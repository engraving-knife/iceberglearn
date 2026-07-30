# 提交 3762：Flink: Backport handle table comments in FlinkSQL (#16503)

## 提交信息

- **序号**：3762 / 4088
- **哈希**：56013b72db456f774efb696e237b58de88c2e94e
- **短哈希**：56013b72d
- **日期**：2026-05-21 10:15:08 +0200
- **作者**：Stepan Stepanishchev
- **提交说明**：Flink: Backport handle table comments in FlinkSQL (#16503)
- **PR/Issue**：#16503（backports #16423）

## 总体目的

这个提交是将提交 #16423（即本批次第 3759 号提交）中"Flink SQL 处理表注释"的修复回移植到 Flink v1.20 和 v2.0 版本分支。原始修复只合入了 Flink v2.1 分支，但 v1.20 和 v2.0 作为已发布的长期支持版本也需要这个修复。

问题背景与 3759 号提交相同：`FlinkCatalog.createTable` 方法在创建 Iceberg 表时没有将 Flink SQL 中的 `COMMENT` 注释传递给 Iceberg 表属性，导致注释信息丢失。

## 如何达成设计目的

与 3759 号提交完全相同的修改，对 Flink v1.20 和 v2.0 两个版本分支分别应用：在 `FlinkCatalog.createTable` 中新增注释处理逻辑，并添加相应的测试用例。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (+6/-0 lines)

**修改目的**：在 Flink v1.20 的 `createTable` 方法中将表注释写入 Iceberg 属性。

**工作逻辑**：
```java
String comment = table.getComment();
if (comment != null && !comment.isEmpty()) {
  properties.put(TableProperties.COMMENT, comment);
}
```
在调用 `icebergCatalog.createTable` 之前检查并写入注释属性。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+19/-0 lines)

**修改目的**：为 v1.20 添加表注释的创建和修改测试。

**工作逻辑**：新增 `testCreateTableWithTableComment` 和 `testAlterTableModifyTableComment` 两个测试方法，与 v2.1 版本的测试一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkCatalog.java` (+6/-0 lines)

**修改目的**：在 Flink v2.0 的 `createTable` 方法中将表注释写入 Iceberg 属性。

**工作逻辑**：与 v1.20 完全相同的修改。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+19/-0 lines)

**修改目的**：为 v2.0 添加表注释的创建和修改测试。

**工作逻辑**：与 v1.20 完全相同的测试。

## 总结

这是 3759 号提交的 backport，将 Flink SQL 表注释处理修复应用到 v1.20 和 v2.0 两个版本分支，确保所有维护中的 Flink 版本都支持表注释功能。这是典型的多版本维护工作，保证不同 Flink 版本之间的功能一致性。
