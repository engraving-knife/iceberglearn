# 提交 3756：Flink: Backport add column fix to flink v1.20, v2.0 (#16447)

## 提交信息

- **序号**：3756 / 4088
- **哈希**：90c2c4c539095b066c683483792a750cef37c592
- **短哈希**：90c2c4c53
- **日期**：2026-05-20 17:25:07 -0700
- **作者**：Stepan Stepanishchev
- **提交说明**：Flink: Backport add column fix to flink v1.20, v2.0 (#16447)
- **PR/Issue**：#16447

## 总体目的

这个提交是将"添加列时支持列位置"的修复回移植（backport）到 Flink v1.20 和 v2.0 两个版本分支。原始的修复已经合入主分支（main），但 Flink 1.20 和 2.0 这两个长期支持的版本分支也需要这个修复，以保证用户在这些 Flink 版本上使用 Iceberg 时能够正确地通过 `ALTER TABLE ADD` 语句指定新列的位置（如 `FIRST` 或 `AFTER`）。

在修复之前，Flink 的 `FlinkAlterTableUtil.applySchemaChanges` 方法在处理 `TableChange.AddColumn` 时，只调用了 `pendingUpdate.addColumn` 或 `pendingUpdate.addRequiredColumn` 添加新列，完全没有处理 `addColumn.getPosition()` 返回的列位置信息。这导致即使用户在 SQL 中写了 `ALTER TABLE tl ADD (col1 STRING FIRST)`，新列实际上只是被追加到表的末尾，而不是按照用户指定的位置插入。

## 如何达成设计目的

设计思路是将原本内联在 `applySchemaChanges` 中的添加列逻辑抽取成一个独立的方法 `applyAddColumn`，并在其中新增对列位置的处理：当 `addColumn.getPosition()` 不为 null 时，构造一个 `TableChange.ModifyColumnPosition` 对象，并复用已有的 `applyModifyColumnPosition` 方法来调整列的顺序。

这种复用 `ModifyColumnPosition` 路径的方式很巧妙，因为 Iceberg 的 `UpdateSchema` API 中添加列和移动列是两个独立的操作，Flink 的 `AddColumn` 变更将这两者合并表达，所以需要在应用时拆分成两个底层操作。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/util/FlinkAlterTableUtil.java` (+28/-12 lines)

**修改目的**：将添加列的逻辑抽取为独立方法，并增加对列位置的支持。

**工作逻辑**：
原来在 `applySchemaChanges` 方法中，对 `TableChange.AddColumn` 类型的处理是直接内联的代码块，只做了类型转换和 `addColumn`/`addRequiredColumn` 调用。修改后改为调用新抽取的 `applyAddColumn(pendingUpdate, (TableChange.AddColumn) change)` 方法。

新方法 `applyAddColumn` 包含原有逻辑（校验是否为物理列、类型转换、根据可空性选择 addColumn 或 addRequiredColumn），并新增了位置处理逻辑：

```java
if (addColumn.getPosition() != null) {
  TableChange.ColumnPosition position = addColumn.getPosition();
  TableChange.ModifyColumnPosition modifyColumnPosition =
      new TableChange.ModifyColumnPosition(addColumn.getColumn(), position);
  applyModifyColumnPosition(pendingUpdate, modifyColumnPosition);
}
```

通过构造 `ModifyColumnPosition` 并复用已有的 `applyModifyColumnPosition` 方法，将添加列和移动列两个操作组合起来，实现"在指定位置添加列"的语义。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+25/-0 lines)

**修改目的**：新增测试用例验证添加列时位置参数生效。

**工作逻辑**：
新增 `testAlterTableAddColumnPosition` 测试方法。先创建表 `tl(id BIGINT, name STRING)`，然后分别执行 `ALTER TABLE tl ADD (col1 STRING FIRST)` 和 `ALTER TABLE tl ADD (col2 INT AFTER id)`，最后断言 schema 的字段顺序为 `col1, id, col2, name`，验证 `FIRST` 和 `AFTER` 两种位置语义都正确生效。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/util/FlinkAlterTableUtil.java` (+28/-12 lines)

**修改目的**：对 Flink v2.0 应用与 v1.20 完全相同的修复。

**工作逻辑**：与 v1.20 版本的修改完全一致，将添加列逻辑抽取为 `applyAddColumn` 方法并增加位置处理。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+25/-0 lines)

**修改目的**：对 Flink v2.0 应用与 v1.20 完全相同的测试。

**工作逻辑**：与 v1.20 版本的测试方法一致。

## 总结

这个提交通过抽取 `applyAddColumn` 方法并复用 `applyModifyColumnPosition`，修复了 Flink v1.20 和 v2.0 中 `ALTER TABLE ADD` 语句忽略列位置参数的问题。修复后用户可以在 SQL 中使用 `FIRST` 和 `AFTER` 关键字精确控制新列在表 schema 中的位置，与 Flink 主版本和 Spark 引擎的行为保持一致。这是典型的 backport 提交，确保多个 Flink 版本分支功能对齐。
