# 提交 1190：Parquet: update PruneColumns to inherit from TypeWithSchemaVisitor to have Iceberg type (#11179)

## 提交信息

- **序号**：1190 / 4088
- **哈希**：2d9c344b5e24e511f1b8e6e3b492119a5960c8a1
- **短哈希**：2d9c344b5
- **日期**：2024-09-26（Thu Sep 26 09:47:28 2024 -0700）
- **作者**：Aihua Xu <aihuaxu@gmail.com>
- **提交说明**：Parquet: update PruneColumns to inherit from TypeWithSchemaVisitor to have Iceberg type (#11179)
- **PR/Issue**：#11179

## 总体目的

Iceberg 在读取 Parquet 文件时，需要根据"期望读取的 schema"（expected schema）对 Parquet 文件的 schema 做列裁剪（column pruning），只读取需要的列。这一逻辑由 `ParquetSchemaUtil.pruneColumns(MessageType fileSchema, Schema expectedSchema)` 入口和 `PruneColumns` 访问器实现。

此前 `PruneColumns` 继承自 `ParquetTypeVisitor<Type>`，该访问器**只遍历 Parquet schema 树**，回调方法（`message`/`struct`/`list`/`map`/`primitive`）只接收 Parquet 类型节点，不接收 Iceberg 类型。这意味着 `PruneColumns` 在裁剪时无法直接获取"期望的 Iceberg 类型"信息——它只能依赖 Parquet schema 中的字段 ID（`selectedIds`）来判断哪些列需要保留。

但在某些场景下（例如 schema 演进、类型变更、Parquet 文件缺少字段 ID 需要回退匹配），仅凭 Parquet schema 的 ID 信息不足以正确裁剪，需要参考 Iceberg 期望类型。本提交的目的是把 `PruneColumns` 的基类从 `ParquetTypeVisitor` 切换为 `TypeWithSchemaVisitor`，后者**同时遍历 Iceberg 类型与 Parquet 类型**，在每个节点把两者都传给回调方法。这样 `PruneColumns` 在裁剪时就能访问到对应的 Iceberg 类型（`StructType`/`ListType`/`MapType`/`PrimitiveType`），为后续基于 Iceberg 类型的裁剪逻辑（例如处理类型不匹配、缺失字段、ID 回退等）打下基础。

## 如何达成设计目的

1. **修改 `ParquetSchemaUtil.pruneColumns` 的调用方式**：原来用 `ParquetTypeVisitor.visit(fileSchema, new PruneColumns(selectedIds))`（只传 Parquet schema），改为 `TypeWithSchemaVisitor.visit(expectedSchema.asStruct(), fileSchema, new PruneColumns(selectedIds))`（同时传 Iceberg 期望 schema 的 `StructType` 与 Parquet file schema）。`TypeWithSchemaVisitor.visit` 会按字段 ID 对齐两个 schema 的字段，并在每个节点回调时把 Iceberg 类型作为第一个参数传入。
2. **修改 `PruneColumns` 的基类与回调签名**：基类从 `ParquetTypeVisitor<Type>` 改为 `TypeWithSchemaVisitor<Type>`；5 个回调方法（`message`/`struct`/`list`/`map`/`primitive`）的签名各增加一个 Iceberg 类型参数（`StructType expected`/`ListType expected`/`MapType expected`/`Type.PrimitiveType expected`）。
3. **回调方法体保持不变**：本次提交只切换基类与签名，方法体逻辑未改动（仍基于 `selectedIds` 判断是否保留列）。这是有意为之——本提交是"先把 Iceberg 类型暴露出来"，后续提交才会利用它做更复杂的裁剪决策。
4. **新增 import**：引入 `Types.ListType`、`Types.MapType`、`Types.StructType` 以支持新的方法签名。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetSchemaUtil.java`

**修改目的**：切换裁剪入口的访问器调用。

**工作逻辑**：`pruneColumns` 方法原实现：
```java
public static MessageType pruneColumns(MessageType fileSchema, Schema expectedSchema) {
  Set<Integer> selectedIds = TypeUtil.getProjectedIds(expectedSchema);
  return (MessageType) ParquetTypeVisitor.visit(fileSchema, new PruneColumns(selectedIds));
}
```
新实现：
```java
public static MessageType pruneColumns(MessageType fileSchema, Schema expectedSchema) {
  Set<Integer> selectedIds = TypeUtil.getProjectedIds(expectedSchema);
  return (MessageType)
      TypeWithSchemaVisitor.visit(
          expectedSchema.asStruct(), fileSchema, new PruneColumns(selectedIds));
}
```
关键变化：把 `expectedSchema.asStruct()`（Iceberg 期望 schema 的 `StructType`）作为第一个参数传给 `TypeWithSchemaVisitor.visit`，使其与 `fileSchema`（Parquet 文件 schema）联合遍历。`TypeWithSchemaVisitor.visit` 内部会按字段 ID 对齐两者的字段，并对每个节点回调带 Iceberg 类型的访问器方法。

### `parquet/src/main/java/org/apache/iceberg/parquet/PruneColumns.java`

**修改目的**：切换访问器基类，使回调方法能接收 Iceberg 类型。

**工作逻辑**：
- 新增 import：`org.apache.iceberg.types.Types.ListType`、`org.apache.iceberg.types.Types.MapType`、`org.apache.iceberg.types.Types.StructType`。
- 基类声明：`class PruneColumns extends ParquetTypeVisitor<Type>` → `class PruneColumns extends TypeWithSchemaVisitor<Type>`。
- 5 个回调方法签名变更（方法体不变）：
  - `message(MessageType message, List<Type> fields)` → `message(StructType expected, MessageType message, List<Type> fields)`
  - `struct(GroupType struct, List<Type> fields)` → `struct(StructType expected, GroupType struct, List<Type> fields)`
  - `list(GroupType list, Type element)` → `list(ListType expected, GroupType list, Type element)`
  - `map(GroupType map, Type key, Type value)` → `map(MapType expected, GroupType map, Type key, Type value)`
  - `primitive(PrimitiveType primitive)` → `primitive(org.apache.iceberg.types.Type.PrimitiveType expected, PrimitiveType primitive)`
- 方法体逻辑（基于 `selectedIds` 与 `getId` 判断保留/裁剪列）完全未改动。`primitive` 仍返回 `null`（表示叶子节点不主动保留，由父节点决定）。

**关于两个访问器的差异**：
- `ParquetTypeVisitor.visit(Type, visitor)` 只遍历 Parquet schema，回调只传 Parquet 节点。
- `TypeWithSchemaVisitor.visit(iType, type, visitor)` 同时遍历 Iceberg `iType` 与 Parquet `type`，按字段 ID 对齐，回调传两者。对于 Iceberg 期望 schema 中有但 Parquet 文件中没有的字段，`iType` 非空但 `type` 为 null（反之亦然），访问器内部会处理这种"单边存在"的情况。

## 小结

- **成效**：`PruneColumns` 现继承自 `TypeWithSchemaVisitor`，在列裁剪遍历时可访问每个节点对应的 Iceberg 期望类型（`StructType`/`ListType`/`MapType`/`PrimitiveType`）。本次提交只切换基类与签名，未改变实际裁剪行为（仍基于 `selectedIds`），是为后续基于 Iceberg 类型的裁剪逻辑（如类型不匹配处理、缺失字段回退等）做基础设施准备。
- **影响范围**：parquet 模块的 2 个 Java 文件，约 13 行新增、7 行删除。属于内部重构，对外 API（`ParquetSchemaUtil.pruneColumns` 签名）不变，裁剪结果不变。
- **回迁到 1.4.x 的注意事项**：这是为后续列裁剪增强做铺垫的基础重构，**单独回迁价值有限**——本提交不改变任何实际裁剪行为，只是把 Iceberg 类型暴露给访问器。如果 1.4.x 不打算引入后续基于 Iceberg 类型的裁剪逻辑（例如处理 schema 演进下的类型不匹配），则无需回迁。若 1.4.x 需要回迁后续相关修复，则需先回迁本提交作为基础。回迁时需注意：(1) `TypeWithSchemaVisitor` 类需在 1.4.x 中已存在（这是较早的基础设施，1.4.x 应已具备）；(2) 切换基类后，`TypeWithSchemaVisitor.visit` 的字段对齐行为（按字段 ID）与原 `ParquetTypeVisitor.visit` 的遍历顺序需一致——对于字段 ID 完整的 Parquet 文件，两者遍历结果相同，但对于无 ID 的旧文件，`TypeWithSchemaVisitor` 可能因 `iType` 与 `type` 对齐方式不同而表现略有差异，回迁后建议在 1.4.x CI 上跑一遍 Parquet 读取相关测试（特别是无 ID 文件、schema 演进场景）确认无回归；(3) 本提交未修改 `pruneColumnsFallback`（处理无 ID 文件的回退路径），该路径仍用 `ParquetTypeVisitor`，回迁时无需变动。
