# 提交 1745：Core: Add variant type support to utils and visitors (#11831)

## 提交信息

- **序号**：1745 / 4088
- **哈希**：0b47faaada2aafa42c118be78445f6c40fc0ead6
- **短哈希**：0b47faaad
- **日期**：2025-02-18 08:27:38 -0800
- **作者**：Aihua Xu
- **提交说明**：Core: Add variant type support to utils and visitors (#11831)
- **PR/Issue**：#11831

## 总体目的

Iceberg 正在引入 `VariantType`（变体类型）支持，这是一种用于存储半结构化数据（类似 JSON/Variant）的新类型。`VariantType` 之前已在类型系统中定义，但 Iceberg 的各种工具类和类型访问器（visitor）尚未对其进行完整支持。

Iceberg 的类型系统使用访问者模式（Visitor Pattern）来遍历和处理类型树。此前，访问者接口中的 `variant()` 方法是无参的（不接收 `VariantType` 实例），且许多访问者实现没有覆盖该方法，导致默认返回 `null`，无法正确处理 Variant 类型。此外，Schema 序列化/反序列化、Schema 更新、类型修复、名称映射等核心工具也没有处理 Variant 类型。

本提交的目标是全面为 Variant 类型添加工具类和访问器支持，使其能够在 Schema 解析、序列化、列裁剪、ID 分配、兼容性检查等所有类型操作中被正确处理。

## 如何达成设计目的

提交通过以下几个层面的修改达成目标：

1. **Type 接口扩展**：在 `Type` 接口中新增 `asVariantType()` 和 `isVariantType()` 默认方法，并在 `VariantType` 类中覆盖它们。

2. **访问者接口改进**：将 `TypeUtil.SchemaVisitor` 和 `TypeUtil.CustomOrderSchemaVisitor` 中的 `variant()` 方法改为 `variant(Types.VariantType variant)`，传入实际的 VariantType 实例。旧的无参 `variant()` 方法被标记为 `@Deprecated` 并委托给新方法。

3. **访问者实现更新**：更新所有访问者实现类，覆盖新的 `variant(Types.VariantType)` 方法，根据各访问者的语义提供合适的行为（如返回类型本身、返回 null 表示不处理、或进行兼容性检查）。

4. **Schema 解析支持**：更新 `SchemaParser` 以正确序列化和反序列化 Variant 类型（作为字符串处理）。

5. **类型工具支持**：更新 `Types` 类中的类型映射和解析方法，支持 Variant 类型的名称查找。

6. **其他核心类支持**：更新 `SchemaUpdate`、`FixupTypes`、`MappingUtil`、`SchemaWithPartnerVisitor`、`UnionByNameVisitor`、`Spark3Util` 等类。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Type.java`（修改, +8 lines）

**修改目的**：在 Type 接口中新增 Variant 类型的识别和转换方法。

**工作逻辑**：
- 新增 `default Types.VariantType asVariantType()`：默认抛出 `IllegalArgumentException`，表示当前类型不是 Variant 类型。
- 新增 `default boolean isVariantType()`：默认返回 `false`。

### `api/src/main/java/org/apache/iceberg/types/Types.java`（修改, +22/-4 lines）

**修改目的**：扩展 Types 工具类以支持 Variant 类型的名称解析。

**工作逻辑**：
- 将 `TYPES` 映射的类型从 `ImmutableMap<String, PrimitiveType>` 改为 `ImmutableMap<String, Type>`，并添加 `VariantType.get()` 条目。
- 新增 `fromTypeName(String typeString)` 方法，返回 `Type` 类型（替代 `fromPrimitiveString` 的角色），支持所有类型包括 Variant。
- 保留 `fromPrimitiveString(String typeString)` 方法，内部委托给 `fromTypeName`，但额外检查返回类型是否为原始类型，若为 Variant 则抛出异常。

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java`（修改, +15/-2 lines）

**修改目的**：改进访问者接口以传递 VariantType 实例。

**工作逻辑**：
- 在 `SchemaVisitor` 中：将 `variant()` 标记为 `@Deprecated`，新增 `variant(Types.VariantType variant)` 方法（默认抛出 `UnsupportedOperationException`）。旧方法委托给新方法，传入 `Types.VariantType.get()`。在 `visit` 方法中将 `case VARIANT` 的调用从 `visitor.variant()` 改为 `visitor.variant(type.asVariantType())`。
- 在 `CustomOrderSchemaVisitor` 中：同样新增 `variant(Types.VariantType variant)` 方法，并在 `visit` 方法中更新调用。

### `api/src/main/java/org/apache/iceberg/types/` 下的访问者实现（修改, 多文件）

以下访问者类均新增了 `variant(Types.VariantType variant)` 方法的覆盖：

- **`AssignFreshIds.java`**（+5）：返回 `variant`（保留原类型，无需重新分配 ID）。
- **`AssignIds.java`**（+5）：返回 `variant`。
- **`ReassignDoc.java`**（+5）：返回 `variant`（无需重新分配文档）。
- **`ReassignIds.java`**（+5）：返回 `variant`。
- **`PruneColumns.java`**（+5）：返回 `null`（Variant 在列裁剪时被当作原始类型处理，不递归）。
- **`GetProjectedIds.java`**（+9）：返回 `null`，并在 `field` 方法中增加 `isVariantType()` 检查，使 Variant 字段的 ID 被添加到投影 ID 集合中。
- **`IndexById.java`**（+5）：返回 `null`（Variant 无嵌套字段）。
- **`IndexByName.java`**（修改）：将 `variant()` 改为 `variant(Types.VariantType variant)`，返回 `nameToId`。
- **`IndexParents.java`**（修改）：将 `variant()` 改为 `variant(Types.VariantType variant)`，返回 `idToParent`。
- **`FindTypeVisitor.java`**（修改）：将 `variant()` 改为 `variant(Types.VariantType variant)`，使用传入的 `variant` 参数替代创建新实例。
- **`PrimitiveHolder.java`**（修改）：将 `readResolve` 中的 `fromPrimitiveString` 改为 `fromTypeName`。
- **`Accessors.java`**（+5）：返回 `null`（Variant 无访问器）。
- **`CheckCompatibility.java`**（+10）：检查当前类型是否为 Variant，若是则兼容，否则不支持类型提升到 Variant。

### `core/src/main/java/org/apache/iceberg/SchemaParser.java`（修改, +3/-2 lines）

**修改目的**：支持 Variant 类型的 JSON 序列化和反序列化。

**工作逻辑**：
- 在 `toJson` 方法中，将 `isPrimitiveType()` 条件改为 `isPrimitiveType() || isVariantType()`，Variant 类型作为字符串写入 JSON。
- 在 `typeFromJson` 方法中，将 `Types.fromPrimitiveString` 改为 `Types.fromTypeName`，以支持解析 Variant 类型字符串。

### `core/src/main/java/org/apache/iceberg/SchemaUpdate.java`（修改, +5 lines）

**修改目的**：在 Schema 更新中支持 Variant 类型。

**工作逻辑**：在内部访问者中新增 `variant(Types.VariantType variant)` 方法，返回 `variant`（保留原类型）。

### `core/src/main/java/org/apache/iceberg/types/FixupTypes.java`（修改, +6 lines）

**修改目的**：在类型修复中支持 Variant 类型。

**工作逻辑**：新增 `variant(Types.VariantType variant)` 方法，返回 `variant`（无需修复）。

### `core/src/main/java/org/apache/iceberg/mapping/MappingUtil.java`（修改, +5 lines）

**修改目的**：在名称映射中支持 Variant 类型。

**工作逻辑**：新增 `variant(Types.VariantType variant)` 方法，返回 `null`（Variant 无嵌套字段，不需要映射）。

### `core/src/main/java/org/apache/iceberg/schema/SchemaWithPartnerVisitor.java`（修改, +7 lines）

**修改目的**：在带伙伴的 Schema 访问者中支持 Variant 类型。

**工作逻辑**：在 `visit` 方法中新增 `case VARIANT` 分支，调用 `visitor.variant(type.asVariantType(), partner)`。新增 `variant(Types.VariantType variant, P partner)` 方法（默认抛出 `UnsupportedOperationException`）。

### `core/src/main/java/org/apache/iceberg/schema/UnionByNameVisitor.java`（修改, +5 lines）

**修改目的**：在按名称合并 Schema 时支持 Variant 类型。

**工作逻辑**：新增 `variant(Types.VariantType variant, Integer partnerId)` 方法，返回 `partnerId == null`（表示该字段在伙伴 Schema 中不存在时需要添加）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/Spark3Util.java`（修改, +5 lines）

**修改目的**：在 Spark 3.5 的 Schema 转字符串工具中支持 Variant 类型。

**工作逻辑**：新增 `variant(Types.VariantType variant)` 方法，返回字符串 `"variant"`。

### 测试文件（多文件, +177 lines）

新增了多个测试方法覆盖 Variant 类型在各种工具操作中的行为，包括 `TestReadabilityChecks`、`TestSerializableTypes`、`TestTypeUtil`、`TestTypes`、`TestSchemaParser`、`TestSchemaUnionByFieldName`、`TestNameMapping`、`TestSpark3Util` 等。

## 小结

- **成效**：全面为 Variant 类型添加了工具类和访问器支持，涉及 31 个文件的修改。Variant 类型现在可以在 Schema 解析、序列化、列裁剪、ID 分配、兼容性检查、名称映射、Schema 更新和合并等所有类型操作中被正确处理。访问者接口从无参 `variant()` 改为有参 `variant(Types.VariantType)`，使访问者能够接收实际的类型实例。
- **影响范围**：涉及 api、core 和 spark 3.5 三个模块。API 变更（visitor 方法签名改变）可能影响外部自定义访问者实现，但旧方法被保留为 `@Deprecated` 以确保向后兼容。
- **回迁到 1.4.x 的注意事项**：此提交是 Variant 类型支持的核心部分，回迁需要确认 1.4.x 分支已引入 `VariantType` 类定义。此提交与提交 1743（Reject unknown type for required fields）、1746（Fix CI: Update tests with UnknownType）和 1748（Parquet: Implement Variant readers）有关联，建议作为一组一起回迁。回迁时需注意 visitor 方法签名变更可能影响 1.4.x 中已有的自定义访问者。建议回迁。
