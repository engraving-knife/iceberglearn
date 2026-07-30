# 提交 3947：Kafka Connect: Fix avro schema conversion for UUID (#16828)

## 提交信息

- **序号**：3947 / 4088
- **哈希**：3c604fb6073d1c11095f0a6708c8dca5731430aa
- **短哈希**：3c604fb60
- **日期**：2026-06-25 10:24:34 +0200
- **作者**：Thomas Thornton
- **提交说明**：Kafka Connect: Fix avro schema conversion for UUID (#16828)
- **PR/Issue**：#16828

## 总体目的

这次提交修复了 Kafka Connect 集成中 Avro schema 到 Iceberg schema 转换时 UUID 类型的处理缺陷。Avro 中 UUID 可以以两种方式表示：
1. `LogicalTypes.uuid()` 应用于 `Schema.Type.STRING`——UUID 作为字符串存储。
2. `LogicalTypes.uuid()` 应用于 `Schema.Type.FIXED`（固定长度 16 字节）——UUID 作为 16 字节固定长度二进制存储。

原代码在 `SchemaUtils.toIcebergType` 中处理 Kafka Connect 的 STRING 类型时，直接 fall-through 到 `default` 分支返回 `StringType`，没有检查 schema 是否带有 `uuid` 逻辑类型名称。这导致 UUID 字段被错误地映射为 Iceberg 的 `StringType` 而非 `UUIDType`，造成类型信息丢失。

修复在 STRING case 中添加了对 `"uuid"` 逻辑类型名称的检查：如果 schema 名称为 `"uuid"`，则返回 `UUIDType.get()`，否则返回 `StringType.get()`。同时在 Avro 的 `AvroSchemaUtil.convert` 中也添加了对应的测试，确保 Avro STRING + uuid 逻辑类型能正确转换为 Iceberg `UUIDType`。

## 如何达成设计目的

在 `SchemaUtils.toIcebergType` 方法的 STRING case 中，在 fall-through 到 default 之前插入对 `"uuid"` 逻辑类型名称的判断。这是通过 Kafka Connect Schema 的 `name()` 方法获取逻辑类型名称（Kafka Connect 使用 schema name 来标识逻辑类型，如 `ConnectSchema.LOGICAL_NAME_UUID` = `"uuid"`）。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SchemaUtils.java` (+5/-0 lines)

**修改目的**：在 STRING 类型转换中识别 UUID 逻辑类型。

**工作逻辑**：
在 `toIcebergType` 方法的 `case STRING:` 分支中添加：
```java
case STRING:
  if ("uuid".equals(valueSchema.name())) {
    return UUIDType.get();
  }
  return StringType.get();
default:
  return StringType.get();
```
当 schema name 为 `"uuid"` 时返回 `UUIDType`，否则返回 `StringType`。新增 `UUIDType` 的 import。

### `core/src/test/java/org/apache/iceberg/avro/TestSchemaConversions.java` (+6/-0 lines)

**修改目的**：验证 Avro STRING + uuid 逻辑类型转换为 Iceberg UUIDType。

**工作逻辑**：
新增 `testAvroToIcebergUUIDTypeOnString` 测试，构造 `LogicalTypes.uuid().addToSchema(Schema.create(Schema.Type.STRING))`，断言 `AvroSchemaUtil.convert()` 返回 `Types.UUIDType.get()`。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/TestRecordConverter.java` (+16/-0 lines)

**修改目的**：验证 UUID 字符串值正确转换为 UUID。

**工作逻辑**：
新增 `testUUIDStringConversion` 测试，构造一个 UUIDType 的 Iceberg schema 和带 `uuid` 逻辑类型的 Connect string schema，验证转换后 record 的 uuid 字段为 UUID 值。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/TestSchemaUtils.java` (+9/-0 lines)

**修改目的**：验证 SchemaUtils 正确识别 UUID 逻辑类型。

**工作逻辑**：
新增 `testToIcebergTypeUUIDLogicalTypeOnString` 测试，构造 `SchemaBuilder.string().name("uuid").build()`，断言 `SchemaUtils.toIcebergType()` 返回 `UUIDType` 实例。

## 总结

这次提交修复了 Kafka Connect 中 Avro UUID 类型（STRING + uuid 逻辑类型）到 Iceberg schema 的转换缺陷。原本 UUID 被错误映射为 StringType，修复后正确映射为 UUIDType，保留了类型语义信息。同时在 Avro 核心层和 Kafka Connect 层都添加了测试覆盖。
