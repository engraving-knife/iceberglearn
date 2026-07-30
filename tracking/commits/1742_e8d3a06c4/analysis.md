# 提交 1742：Core: Fix Enabling row-lineage during Create Table (#12307)

## 提交信息

- **序号**：1742 / 4088
- **哈希**：e8d3a06c4516973fc2fd4d8dd1d908f7378d903b
- **短哈希**：e8d3a06c4
- **日期**：2025-02-18 10:03:10 -0600
- **作者**：Tom Tanaka
- **提交说明**：Core: Fix Enabling row-lineage during Create Table (#12307)
- **PR/Issue**：#12307

## 总体目的

Iceberg 的行级血缘（row-lineage）功能允许跟踪表中每一行的血缘关系，通过为每行分配一个唯一的行 ID 来实现。该功能可以通过表属性 `TableProperties.ROW_LINEAGE`（即 `write.metadata.row-lineage`）设置为 `true` 来启用。

然而，在创建新表时，`TableMetadata.newTableMetadata` 方法没有读取表属性中的 `ROW_LINEAGE` 配置，导致即使用户在创建表时设置了 `row-lineage=true` 属性，行血缘功能也不会被启用。用户必须在建表后额外执行一次 `updateProperties` 操作才能启用，这不符合预期。

本提交的目标是修复这个问题，使 `newTableMetadata` 方法在建表时读取 `ROW_LINEAGE` 属性并正确设置到 `TableMetadata` 中。

## 如何达成设计目的

提交通过以下修改达成目标：

1. **核心修复**：在 `TableMetadata.newTableMetadata` 方法中，使用 `PropertyUtil.propertyAsBoolean` 从表属性中读取 `ROW_LINEAGE` 配置（默认值为 `DEFAULT_ROW_LINEAGE`），并通过 `TableMetadata.Builder.setRowLineage()` 方法设置到构建的 metadata 中。

2. **测试辅助**：在 `TestTables` 中新增一个 `create` 方法重载，支持传入表属性（`Map<String, String>`），使测试能够在建表时指定属性。

3. **测试验证**：在 `TestRowLineageMetadata` 中新增 `testEnableRowLineageViaPropertyAtTableCreation` 测试方法，验证通过属性在建表时启用行血缘功能。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`（修改, +6 lines）

**修改目的**：在 `newTableMetadata` 方法中读取并设置行血缘配置。

**工作逻辑**：在 `newTableMetadata` 方法的 metrics 配置校验之前，新增以下代码：
```java
Boolean rowLineage = PropertyUtil.propertyAsBoolean(
    properties, TableProperties.ROW_LINEAGE, DEFAULT_ROW_LINEAGE);
```
然后在 `TableMetadata.Builder` 的构建链中添加 `.setRowLineage(rowLineage)` 调用，将读取到的行血缘配置设置到 metadata 中。

### `core/src/test/java/org/apache/iceberg/TestTables.java`（修改, +20 lines）

**修改目的**：新增支持表属性的 `create` 方法重载，供测试使用。

**工作逻辑**：新增一个静态方法 `create(File temp, String name, Schema schema, Map<String, String> properties, int formatVersion)`，该方法：
1. 创建 `TestTableOperations` 实例。
2. 检查表是否已存在，若存在则抛出 `AlreadyExistsException`。
3. 调用 `newTableMetadata` 方法创建包含指定属性的表 metadata。
4. 通过 `ops.commit` 提交 metadata。
5. 返回新的 `TestTable` 实例。

### `core/src/test/java/org/apache/iceberg/TestRowLineageMetadata.java`（修改, +15 lines）

**修改目的**：验证通过表属性在建表时启用行血缘功能。

**工作逻辑**：新增 `testEnableRowLineageViaPropertyAtTableCreation` 测试方法：
1. 首先假设 format 版本 >= `MIN_FORMAT_VERSION_ROW_LINEAGE`（行血缘功能需要 v3）。
2. 使用 `TestTables.create` 方法创建表，传入 `ImmutableMap.of(TableProperties.ROW_LINEAGE, "true")` 作为属性。
3. 断言 `table.ops().current().rowLineageEnabled()` 返回 `true`，验证行血缘已通过属性启用。

新增了 `import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap` 导入。

## 小结

- **成效**：修复了建表时无法通过表属性启用行血缘功能的问题。现在用户可以在创建表时设置 `write.metadata.row-lineage=true` 属性，行血缘功能会自动启用，无需额外的 `updateProperties` 操作。
- **影响范围**：涉及 core 模块的 `TableMetadata` 类（核心元数据构建逻辑）和测试辅助类。影响所有通过属性配置行血缘的建表场景。
- **回迁到 1.4.x 的注意事项**：此提交依赖行血缘功能（`ROW_LINEAGE` 属性、`setRowLineage` 方法、`MIN_FORMAT_VERSION_ROW_LINEAGE` 等），需确认 1.4.x 分支已支持行血缘功能。如果 1.4.x 分支尚不支持行血缘，则不应回迁。如果已支持，则回迁风险低。建议在确认行血缘功能存在后回迁。
