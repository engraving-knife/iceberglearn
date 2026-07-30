# 提交 2887：Add variant type support to ParquetTypeVisitor (#14588)

## 提交信息

- **序号**：2887 / 4088
- **哈希**：b6b89268de26b119817db9ce20b540baa349c541
- **短哈希**：b6b89268d
- **日期**：2025-11-18 10:39:14 -0800
- **作者**：Tamas Mate
- **提交说明**：Add variant type support to ParquetTypeVisitor (#14588)
- **PR/Issue**：#14588

## 总体目的

Iceberg 规范已引入 `variant` 类型（用于存储半结构化数据，类似 JSON/Variant）。在 Parquet 格式中，variant 类型使用 Parquet 的 `VARIANT` 逻辑类型注解，物理上是一个包含 `metadata` 和 `value` 两个 BINARY 字段的 group 类型。

`ParquetTypeVisitor` 是 Iceberg Parquet 模块中的核心访问者基类，负责遍历 Parquet schema 并对各类型节点执行操作。它的子类包括 `MessageTypeToType`（Parquet → Iceberg 类型转换）、`ApplyNameMapping`（应用名称映射分配字段 ID）、`RemoveIds`（移除字段 ID）等。

此前，`ParquetTypeVisitor` 没有识别 variant 逻辑类型的逻辑。当遇到 variant 类型的 Parquet group 时，会将其当作普通的 `struct` 类型处理，导致：
1. `MessageTypeToType` 无法将 Parquet variant 正确转换为 Iceberg `VariantType`。
2. `ApplyNameMapping` 无法为 variant 字段正确分配 ID。
3. `RemoveIds` 无法正确移除 variant 字段的 ID。
4. `ParquetSchemaUtil` 的 ID 检查无法正确处理 variant 类型。

此提交为 `ParquetTypeVisitor` 及其所有子类添加了 variant 类型的访问者方法支持。

## 如何达成设计目的

1. 在 `ParquetTypeVisitor` 的 `visit` 方法中，新增对 variant 逻辑类型的识别：检查 group 的逻辑类型注解是否为 `LogicalTypeAnnotation.variantType(Variant.VARIANT_SPEC_VERSION)`，如果是则调用 `visitVariant()`。
2. 在 `ParquetTypeVisitor` 基类中新增 `variant(GroupType)` 方法，默认返回 null。
3. 在各子类中 override `variant` 方法实现具体逻辑。
4. 新增测试验证 variant 类型的 schema 转换和名称映射。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetTypeVisitor.java` (+12/-0 lines)

**修改目的**：为基类添加 variant 类型识别和访问方法。

**工作逻辑**：
- 在 `visit(GroupType group, ParquetTypeVisitor<T> visitor)` 方法中，新增判断：如果逻辑类型注解等于 `LogicalTypeAnnotation.variantType(Variant.VARIANT_SPEC_VERSION)`，则调用 `visitVariant(group, visitor)`。该判断位于 list 和 map 判断之后、struct 之前。
- 新增私有静态方法 `visitVariant(GroupType variant, ParquetTypeVisitor<T> visitor)`，调用 `visitor.variant(variant)`。
- 新增 public 方法 `T variant(GroupType variant)`，默认返回 null（与 `primitive`、`struct` 等方法的默认行为一致）。
- 引入 `org.apache.iceberg.variants.Variant` 导入，使用 `Variant.VARIANT_SPEC_VERSION` 常量指定 variant 规范版本。

### `parquet/src/main/java/org/apache/iceberg/parquet/MessageTypeToType.java` (+5/-0 lines)

**修改目的**：将 Parquet variant group 转换为 Iceberg VariantType。

**工作逻辑**：override `variant(GroupType variant)` 方法，返回 `Types.VariantType.get()`。这使得 Parquet schema 中的 variant 字段能被正确转换为 Iceberg 的 variant 类型。

### `parquet/src/main/java/org/apache/iceberg/parquet/ApplyNameMapping.java` (+6/-0 lines)

**修改目的**：为 variant 字段应用名称映射分配 ID。

**工作逻辑**：override `variant(GroupType variant)` 方法，通过 `nameMapping.find(currentPath())` 查找当前路径对应的映射字段。如果找到则返回 `variant.withId(field.id())`，否则返回原 variant（不修改 ID）。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetSchemaUtil.java` (+5/-0 lines)

**修改目的**：在 ID 检查中正确处理 variant 类型。

**工作逻辑**：在内部的 `HasIds` 访问者类中 override `variant(GroupType variant)` 方法，返回 `variant.getId() != null`，与 `primitive` 方法的逻辑一致，检查 variant group 是否有字段 ID。

### `parquet/src/main/java/org/apache/iceberg/parquet/RemoveIds.java` (+10/-0 lines)

**修改目的**：移除 variant 字段的 ID。

**工作逻辑**：override `variant(GroupType variant)` 方法，使用 `Types.buildGroup()` 重建 group 类型，保留 repetition 和逻辑类型注解，但重新添加所有子字段（子字段的 ID 会被递归移除），不设置 ID。返回 `builder.named(variant.getName())`。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetSchemaUtil.java` (+88/-0 lines)

**修改目的**：验证 variant 类型的 schema 转换和名称映射。

**工作逻辑**：
- `testAssignIdsToVariantTypesByNameMapping`：构建包含 variant 列（顶层、struct 内、list 元素、map value）的 Iceberg schema，转换为 Parquet MessageType 后移除 ID，再通过名称映射恢复 ID，验证结果与原始 MessageType 一致。
- `testVariantTypesWithoutAssigningIds`：构建包含 variant 的 Parquet MessageType（部分有 ID、部分无 ID），转换为 Iceberg Schema（使用 `convertAndPrune`），验证结果正确。注意 variant 字段在 schema 转换中被保留，但无 ID 的嵌套 variant 字段会被裁剪。
- 新增私有辅助方法 `variant(Integer id, String name, Repetition repetition)`，构建 Parquet variant group 类型（包含 `metadata` 和 `value` 两个 BINARY 字段，使用 `VARIANT` 逻辑类型注解）。

## 总结

该提交为 Iceberg 的 Parquet 模块添加了 variant 类型在 schema 访问者模式中的完整支持。通过在 `ParquetTypeVisitor` 基类中新增 variant 识别逻辑和访问方法，并在 `MessageTypeToType`、`ApplyNameMapping`、`RemoveIds`、`ParquetSchemaUtil` 四个子类中实现具体处理，确保了 variant 类型在 Parquet ↔ Iceberg 类型转换、名称映射、ID 移除等操作中的正确性。这是 Iceberg variant 类型端到端支持的重要一环，使 variant 列能在 Parquet 文件中正确读写。
