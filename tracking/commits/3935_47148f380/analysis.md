# 提交 3935：AWS: Handle duplicate column names in IcebergToGlueConverter comment map (#16853)

## 提交信息

- **序号**：3935 / 4088
- **哈希**：47148f380eaf3e806ec0cc628b9661a8f57a8fc3
- **短哈希**：47148f380
- **日期**：2026-06-23 17:43:42 -0700
- **作者**：Wyatt H
- **提交说明**：AWS: Handle duplicate column names in IcebergToGlueConverter comment map (#16853)
- **PR/Issue**：#16853

## 总体目的

这次提交修复了一个严重的 AWS Glue 集成 bug，该 bug 会导致表的存储描述符（StorageDescriptor）被置为 null，造成破坏性写入。

问题根因：`IcebergToGlueConverter.setTableInputInformation` 在构建 Glue `TableInput` 时，会从已有 Glue 表的列中构建一个 `column name -> comment` 的映射（用于恢复没有 `doc()` 的字段的注释）。原代码使用 `Collectors.toMap(Column::name, Column::comment)`，当出现重复的 column name 时会抛出 `IllegalStateException`。

重复 column name 的产生路径：当用户执行仅大小写不同的 `RENAME COLUMN`（如 `id -> id__tmp -> Id`）时，converter 会将历史 schema 名称与当前名称一起写入 `Columns[]`。由于 Glue 在持久化时对列名做大小写不敏感的归一化，`Id` 和 `id` 会被折叠为两个 `name="id"` 的行。当这两个行都带有非 null 的 comment 时，`Collectors.toMap` 就会因重复 key 抛异常。

更严重的是，该异常被外层 `catch (RuntimeException e) { LOG.warn(...) }` 吞掉，导致 `tableInputBuilder.storageDescriptor` 未被设置（为 null），随后 `glue.updateTable()` 带着空的 storageDescriptor 执行更新，使表的存储描述符被破坏，所有后续读取都无法定位数据。

修复通过为 `Collectors.toMap` 添加 first-seen-wins 合并函数，使重复 key 折叠而非抛异常。

## 如何达成设计目的

将 `Collectors.toMap(Column::name, Column::comment)` 改为 `Collectors.toMap(Column::name, Column::comment, (existing, duplicate) -> existing)`，即遇到重复 key 时保留先出现的值。first-seen-wins 是正确的语义，因为 `toColumns` 方法先写入当前 schema 的列再写入历史 schema 的列，所以当前 schema 的 comment 会被优先保留。

修复后，已损坏的表在下一次 commit 时会自动愈合——无需手动去重。converter 的 emit 行为不变，Glue 仍然会收到包含历史名称的 `Columns[]`，时间旅行和 Hive fallback 消费者不受影响。

同时将变量名 `existingColumnMap` 重命名为 `existingColumnNameToComment` 以更准确反映其内容。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/glue/IcebergToGlueConverter.java` (+28/-11 lines)

**修改目的**：为 toMap 添加 merge 函数并重命名变量。

**工作逻辑**：
1. 将 `existingColumnMap` 重命名为 `existingColumnNameToComment`，涉及 `setTableInputInformation`、`toColumns`、`addColumnWithDedupe` 三处方法签名和调用点。
2. 在构建映射时添加合并函数：
```java
existingColumnNameToComment =
    existingColumns.stream()
        .filter(column -> column.comment() != null)
        .collect(
            Collectors.toMap(
                Column::name, Column::comment, (existing, duplicate) -> existing));
```
3. 添加注释说明 first-seen-wins 语义的原因。

### `aws/src/test/java/org/apache/iceberg/aws/glue/TestIcebergToGlueConverter.java` (+53/-0 lines)

**修改目的**：验证重复列名场景下 storage descriptor 正确设置。

**工作逻辑**：
新增 `testDuplicateExistingColumnsKeepFirstComment` 测试方法，构造一个已有 Glue 表，其 `Columns[]` 包含两个 `name="id"` 的列（分别带 "current comment" 和 "historical comment"），调用 `setTableInputInformation` 后断言：
- `storageDescriptor` 不为 null（修复前为 null）。
- 列的 comment 为 "current comment"（first-seen-wins）。

## 总结

这次提交修复了一个高严重性的 AWS Glue 集成 bug：重复列名导致 `IllegalStateException` 被吞掉，进而使 `glue.updateTable()` 带着 null storageDescriptor 执行破坏性写入。通过为 toMap 添加 first-seen-wins 合并函数，既解决了异常抛出问题，又自动愈合了已损坏的表。修复保持 converter 的 emit 行为不变，不影响时间旅行和 Hive fallback 消费者。
