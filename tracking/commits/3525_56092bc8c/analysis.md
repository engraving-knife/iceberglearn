# 提交 3525：Parquet: Fix NPE in ParquetAvroWriter when schema contains variant type (#15934)

## 提交信息

- **序号**：3525 / 4088
- **哈希**：56092bc8cbfa19380cdd4513fc707c6d97ef8e36
- **短哈希**：56092bc8c
- **日期**：2026-04-13 10:44:37 -0700
- **作者**：Neelesh Salian
- **提交说明**：Parquet: Fix NPE in ParquetAvroWriter when schema contains variant type (#15934)
- **PR/Issue**：#15934

## 总体目的

`ParquetAvroWriter` 内部的 `WriteSupport` 类继承自 `ParquetValueWriters.Visitor`，需要为 Iceberg/Parquet 支持的各种类型提供 `visit` 方法实现。当 schema 中包含新增的 variant（变体）类型时，由于 `ParquetAvroWriter` 的 visitor 没有覆盖 `variant(GroupType)` 方法，会走父类默认实现——而默认实现通常返回 null，随后在写入流程中触发 `NullPointerException`。

variant 类型是 Iceberg 新引入的半结构化数据类型（用于存储 JSON/变体数据），Avro 写入器并不支持这种类型。直接抛 NPE 会让用户困惑，不知道根本原因。本提交通过显式覆盖 `variant` 方法并抛出带清晰消息的 `UnsupportedOperationException`，让用户立即明白 Avro writer 不支持 variant 类型。

## 如何达成设计目的

在 `ParquetAvroWriter.WriteSupport` 中新增 `variant(GroupType)` 方法重写，直接抛出 `UnsupportedOperationException("Avro writer does not support variant types")`。这样当 schema 含 variant 类型时，构建 writer 阶段就会快速失败并给出明确错误，而不是延迟到写入时 NPE。

同时新增测试 `testAvroWriterRejectsVariantType`，构造一个包含 variant 字段的 Parquet schema，调用 `ParquetAvroWriter.buildWriter(schema)` 并断言抛出 `UnsupportedOperationException` 且消息匹配。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetAvroWriter.java` (+5/-0 lines)

**修改目的**：显式拒绝 variant 类型，避免 NPE。

**工作逻辑**：
```java
@Override
public ParquetValueWriter<?> variant(GroupType variant) {
  throw new UnsupportedOperationException("Avro writer does not support variant types");
}
```
当 visitor 遍历到 variant 类型字段时，立即抛出明确异常，阻止 writer 构建完成。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (+24/-0 lines)

**修改目的**：回归测试 Avro writer 对 variant 类型的拒绝行为。

**工作逻辑**：
```java
@Test
public void testAvroWriterRejectsVariantType() {
  MessageType schema =
      org.apache.parquet.schema.Types.buildMessage()
          .optional(PrimitiveTypeName.INT32).named("id")
          .optionalGroup()
          .as(LogicalTypeAnnotation.variantType(Variant.VARIANT_SPEC_VERSION))
          .required(PrimitiveTypeName.BINARY).named("metadata")
          .required(PrimitiveTypeName.BINARY).named("value")
          .named("v")
          .named("table");

  assertThatThrownBy(() -> ParquetAvroWriter.buildWriter(schema))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Avro writer does not support variant types");
}
```
构造了一个含 `id` 整型字段和 `v` variant 字段的 Parquet schema，验证 `buildWriter` 抛出预期异常。新增了 `assertThatThrownBy`、`Variant`、`LogicalTypeAnnotation`、`PrimitiveTypeName` 等 import。

## 总结

本提交修复了 `ParquetAvroWriter` 在 schema 包含 variant 类型时抛出 NPE 的体验问题，改为在 writer 构建阶段抛出清晰的 `UnsupportedOperationException`，明确告知 Avro writer 不支持 variant 类型。改动小而精准，并配以回归测试，提升了错误诊断友好度。
