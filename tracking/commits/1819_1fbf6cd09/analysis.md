# 提交 1819：Core: Add Variant logical type for Avro (#12238)

## 提交信息

- **序号**：1819 / 4088
- **哈希**：1fbf6cd097a5dfd0a5ac27e09f0e866efb3925a8
- **短哈希**：1fbf6cd09
- **日期**：2025-03-04 13:03:41 -0800
- **作者**：Aihua Xu
- **提交说明**：Core: Add Variant logical type for Avro (#12238)
- **PR/Issue**：#12238

## 总体目的

该提交为 Iceberg 的 Avro 模块引入 Variant 逻辑类型（Variant logical type）支持，使 Iceberg 的 Variant 数据类型能够在 Avro 文件格式中正确序列化和反序列化。

Variant 是一种半结构化数据类型（类似于 JSON），包含 metadata 和 value 两部分二进制数据。在 Iceberg 中 Variant 类型存储为一个包含 `metadata`（bytes）和 `value`（bytes）两个字段的结构。在此之前，Iceberg 的 Avro 模块没有对 Variant 类型提供专门的处理，schema 访问器会将 Variant 误认为普通 record 类型进行遍历，导致字段投影、name mapping、schema 转换等操作行为不正确。

本提交通过引入 Avro LogicalType 机制标记 Variant record，并让所有 Avro schema 访问器在遇到 Variant 逻辑类型时走专门的 `variant()` 分支，确保 Variant 类型在 Avro 层面被正确识别和处理。这是 Iceberg 支持 Variant 类型的系列工作之一（配合后续的 Parquet writer、Spark 集成等提交）。

## 如何达成设计目的

整体设计基于 Avro 的 LogicalType 扩展机制。新建 `VariantLogicalType` 类继承 Avro `LogicalType`，以名称 `"variant"` 标记 Variant record schema。在 `TypeToSchema` 中将 Iceberg VariantType 转换为带 VariantLogicalType 标记的 Avro record（含 metadata/value 两个 bytes 字段）。在两个 schema 访问器（`AvroSchemaVisitor` 和 `AvroCustomOrderSchemaVisitor`）的 RECORD 分支中，优先检查逻辑类型是否为 Variant，若是则校验 schema 结构并调用 `visitor.variant()` 分支，避免按普通 record 遍历字段。同时在多个访问器子类（PruneColumns、ApplyNameMapping、RemoveIds、HasIds 等）中实现 variant 方法。

## 修改详情

### core/src/main/java/org/apache/iceberg/avro/VariantLogicalType.java (新增, 43 lines)

新建类，继承 Avro `LogicalType`，定义逻辑类型名 `"variant"`。采用单例模式（INSTANCE）。`validate()` 方法调用 `AvroSchemaUtil.isVariantSchema()` 校验目标 schema 是否为合法的 Variant record（RECORD 类型、恰好 2 个字段、metadata 和 value 均为 bytes 类型）。

### core/src/main/java/org/apache/iceberg/avro/AvroSchemaUtil.java (修改, 14 lines)

新增 `isVariantSchema(Schema schema)` 静态方法，判断一个 Avro schema 是否为 Variant record：类型为 RECORD、字段数为 2、含名为 `metadata` 和 `value` 的字段且均为 bytes 类型。

### core/src/main/java/org/apache/iceberg/avro/TypeToSchema.java (修改, 15 lines)

新增 `variant(Types.VariantType)` 方法重写，将 Iceberg VariantType 转换为 Avro record schema：record 名为 `r<fieldId>` 或 `variant`，包含 metadata（BINARY_SCHEMA）和 value（BINARY_SCHEMA）两个字段，并通过 `VariantLogicalType.get().addToSchema(schema)` 添加逻辑类型标记。

### core/src/main/java/org/apache/iceberg/avro/AvroSchemaVisitor.java (修改, 40 lines)

在 RECORD 分支中增加逻辑类型判断：若 schema 的 logicalType 是 VariantLogicalType，校验 schema 合法性后调用 `visitor.variant(schema, visit(metadata), visit(value))`，不再按普通 record 遍历字段。新增 `variant()` 默认方法抛出 UnsupportedOperationException。

### core/src/main/java/org/apache/iceberg/avro/AvroCustomOrderSchemaVisitor.java (修改, 40 lines)

同样的逻辑类型判断改造，针对自定义遍历顺序的访问器。Variant 分支使用 `VisitFuture` 包装 metadata 和 value 的访问，新增 `variant()` 默认方法。

### 其余 Avro 访问器子类 (修改, 各 5-16 lines)

- `ApplyNameMapping.java`、`BuildAvroProjection.java`、`HasIds.java`、`MissingIds.java`、`RemoveIds.java`、`SchemaToType.java`：各新增 variant 方法处理。
- `PruneColumns.java`：调整 variant 投影逻辑。
- `Avro.java`、`AvroEncoderUtil.java`：注册/支持 VariantLogicalType。

### 测试文件 (多个, 共约 200+ lines)

- `TestPruneColumns.java`（114 行）：测试 Variant 字段的列裁剪行为。
- `TestSchemaConversions.java`（32 行）：测试 Variant 类型与 Avro schema 的双向转换。
- `TestAvroNameMapping.java`、`TestAvroSchemaProjection.java`、`TestHasIds.java`：补充 Variant 相关测试。

## 小结

该提交为 Iceberg Avro 模块建立了 Variant 类型的完整支持链路，从 schema 转换、schema 访问器遍历到列裁剪和 name mapping。影响范围集中在 core 模块的 avro 包。回迁到 1.4.x 分支时需注意：1.4.x 分支可能尚无 VariantType 类型定义（Types.VariantType），需先确认 core 模块已包含 Variant 类型的基本定义；各 Avro 访问器子类的结构需逐一核对。该提交是 Variant 支持的基础设施，建议与后续 Variant 相关提交（1822、1825 等）协同回迁。
