# 提交 3752：Flink: Fix ALTER TABLE to add column to specific position (#16419)

## 提交信息

- **序号**：3752 / 4088
- **哈希**：684a1870d3cc0738ab57269ab1dcb8a8a3b267a3
- **短哈希**：684a1870d
- **日期**：2026-05-20 13:20:20 +0200
- **作者**：Stepan Stepanishchev
- **提交说明**：Flink: Fix ALTER TABLE to add column to specific position (#16419)
- **PR/Issue**：#16419

## 总体目的

本提交修复了 Flink 中通过 `ALTER TABLE ... ADD COLUMN` 添加列到指定位置（如 `FIRST`、`AFTER xxx`）时位置被忽略的 Bug。

在 Flink 的 `ALTER TABLE` 语法中，`ADD COLUMN` 支持指定新列的位置，例如 `ALTER TABLE tl ADD (col1 STRING FIRST)` 或 `ALTER TABLE tl ADD (col2 INT AFTER id)`。Flink 将该操作解析为 `TableChange.AddColumn` 对象，其中包含 `position` 字段表示新列应放置的位置。

然而 Iceberg 的 Flink 集成中 `FlinkAlterTableUtil.applySchemaChanges()` 方法在处理 `AddColumn` 变更时，只读取了列名、类型、是否可空和注释，却完全忽略了 `addColumn.getPosition()`。这意味着无论用户指定 `FIRST` 还是 `AFTER xxx`，新列都会被添加到 schema 末尾，与用户的预期不符。

本提交修复此问题，在添加列后检查 position，如果非 null 则通过 `applyModifyColumnPosition` 将列移动到指定位置。

## 如何达成设计目的

将原先内联在 `applySchemaChanges` 中的 `AddColumn` 处理逻辑提取为独立的 `applyAddColumn` 方法，在该方法中完成列添加后，检查 `addColumn.getPosition()` 是否非 null。如果非 null，则构造一个 `TableChange.ModifyColumnPosition` 对象（复用已有的 `applyModifyColumnPosition` 方法），将新添加的列移动到指定位置。这样复用了已有的位置移动逻辑，避免重复实现。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/util/FlinkAlterTableUtil.java` (+27/-14 lines)

**修改目的**：修复 `AddColumn` 忽略位置信息的问题。

**工作逻辑**：
- 将 `applySchemaChanges` 中处理 `AddColumn` 的内联逻辑提取为独立的 `applyAddColumn` 方法：
```java
private static void applyAddColumn(UpdateSchema pendingUpdate, TableChange.AddColumn addColumn) {
  Column flinkColumn = addColumn.getColumn();
  Preconditions.checkArgument(
      FlinkCompatibilityUtil.isPhysicalColumn(flinkColumn),
      "Unsupported table change: Adding computed column %s.",
      flinkColumn.getName());

  Type icebergType = FlinkSchemaUtil.convert(flinkColumn.getDataType().getLogicalType());

  if (flinkColumn.getDataType().getLogicalType().isNullable()) {
    pendingUpdate.addColumn(
        flinkColumn.getName(), icebergType, flinkColumn.getComment().orElse(null));
  } else {
    pendingUpdate.addRequiredColumn(
        flinkColumn.getName(), icebergType, flinkColumn.getComment().orElse(null));
  }

  if (addColumn.getPosition() != null) {
    TableChange.ColumnPosition position = addColumn.getPosition();
    TableChange.ModifyColumnPosition modifyColumnPosition =
        new TableChange.ModifyColumnPosition(addColumn.getColumn(), position);
    applyModifyColumnPosition(pendingUpdate, modifyColumnPosition);
  }
}
```
关键新增逻辑在方法末尾：当 `addColumn.getPosition()` 非 null 时，构造 `TableChange.ModifyColumnPosition` 对象（传入新列和位置），然后调用已有的 `applyModifyColumnPosition` 方法执行位置移动。这样复用了已有的列位置移动逻辑，确保 `FIRST`、`AFTER`、`LAST` 等位置语义正确应用。

- `applySchemaChanges` 中的调用简化为：
```java
if (change instanceof TableChange.AddColumn) {
  applyAddColumn(pendingUpdate, (TableChange.AddColumn) change);
}
```

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestFlinkCatalogTable.java` (+25/-0 lines)

**修改目的**：新增测试验证 `ALTER TABLE ADD COLUMN` 带位置时的正确行为。

**工作逻辑**：
新增 `testAlterTableAddColumnPosition` 测试：
1. 创建表 `tl(id BIGINT, name STRING)`，验证初始 schema（id=1, name=2）。
2. 执行 `ALTER TABLE tl ADD (col1 STRING FIRST)`：新列 col1 应添加到 schema 最前。
3. 执行 `ALTER TABLE tl ADD (col2 INT AFTER id)`：新列 col2 应添加到 id 列之后。
4. 验证最终 schema 顺序为：`col1(id=3) → id(id=1) → col2(id=4) → name(id=2)`。
```java
Schema schemaAfter = table("tl").schema();
assertThat(schemaAfter.asStruct())
    .isEqualTo(
        new Schema(
                Types.NestedField.optional(3, "col1", Types.StringType.get()),
                Types.NestedField.optional(1, "id", Types.LongType.get()),
                Types.NestedField.optional(4, "col2", Types.IntegerType.get()),
                Types.NestedField.optional(2, "name", Types.StringType.get()))
            .asStruct());
```
测试验证了 `FIRST` 和 `AFTER` 两种位置语义，以及新列的 field ID 分配（3 和 4）。

## 总结

本提交修复了 Flink `ALTER TABLE ADD COLUMN` 忽略列位置信息（`FIRST`、`AFTER`）的 Bug。修复方式是将 `AddColumn` 处理逻辑提取为独立方法，在添加列后检查 position 是否非 null，若是则构造 `ModifyColumnPosition` 复用已有的位置移动逻辑将列移到指定位置。新增的测试验证了 `FIRST` 和 `AFTER` 两种位置语义的正确性。这是一个面向 Flink schema 演进正确性的修复。
