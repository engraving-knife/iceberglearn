# 提交 3726：Core, Flink: Add UUID to DataTestBase SUPPORTED_PRIMITIVES (#16364)

## 提交信息

- **序号**：3726 / 4088
- **哈希**：e1705f4956f4b92bb6f00b593c8287bdbf61aa6c
- **短哈希**：e1705f495
- **日期**：2026-05-18 10:13:27 +0200
- **作者**：Joy Haldar
- **提交说明**：Core, Flink: Add UUID to DataTestBase SUPPORTED_PRIMITIVES (#16364)
- **PR/Issue**：#16364

## 总体目的

本提交旨在补全 Iceberg 数据读写测试中对 UUID 类型的覆盖。`DataTestBase` 是 Iceberg core 模块中一个抽象测试基类，定义了 `SUPPORTED_PRIMITIVES` 这一标准 schema，被多个子模块（如 Flink、Spark 等）的读写测试继承用于验证数据在不同格式（如 Parquet、ORC、Avro）下的序列化/反序列化正确性。

原先 `SUPPORTED_PRIMITIVES` 中虽然包含 `fixed`、`bytes`、`decimal`、`time` 等多种类型，但却缺少了 `UUIDType`。这意味着 Flink 等模块的 Parquet 读取器在读写 UUID 类型数据时缺乏统一的回归测试覆盖。由于 Parquet 中 UUID 以 `FIXED_LEN_BYTE_ARRAY(16)` + `LogicalTypeAnnotation.uuidType()` 表示，添加该测试可以确保 Flink Parquet 读取器正确处理 UUID 的编解码逻辑。

## 如何达成设计目的

在 `DataTestBase.SUPPORTED_PRIMITIVES` 中新增字段 `required(111, "uuid", Types.UUIDType.get())`，将该字段插入到 `s`（String，id 110）和 `fixed`（id 112）之间。同时同步更新 Flink 1.20、2.0、2.1 三个版本的 `TestFlinkParquetReader` 中手工构造的 Parquet schema，添加对应的 UUID 字段定义，并调整后续字段的注释编号以保持一致。

## 修改详情

### `core/src/test/java/org/apache/iceberg/data/DataTestBase.java` (+1/-0 lines)

**修改目的**：在标准测试 schema 中新增 UUID 字段。

**工作逻辑**：
在 `SUPPORTED_PRIMITIVES` schema 定义中，于 `s`（id 110）和 `fixed`（id 112）之间新增：
```java
required(111, "uuid", Types.UUIDType.get()),
```
这样所有继承 `DataTestBase` 的测试都会自动包含 UUID 字段的读写验证。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java` (+13/-6 lines)

**修改目的**：在 Flink 1.20 Parquet 读取器测试中同步添加 UUID 字段的 Parquet schema 定义。

**工作逻辑**：
在手工构造的 Parquet schema 中新增 UUID 字段：
```java
// 11: required(111, "uuid", Types.UUIDType.get())
primitive(
        PrimitiveType.PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY, Type.Repetition.REQUIRED)
    .id(111)
    .length(16)
    .as(LogicalTypeAnnotation.uuidType())
    .named("uuid"),
```
UUID 在 Parquet 中表示为 `FIXED_LEN_BYTE_ARRAY`，长度为 16 字节，并使用 `uuidType()` 逻辑类型注解。同时将后续字段的注释编号（`fixed`、`bytes`、`dec_9_0`、`dec_11_2`、`dec_38_10`、`time`）依次顺延，保持注释与实际顺序的一致性。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java` (+13/-6 lines)

**修改目的**：在 Flink 2.0 Parquet 读取器测试中同步添加 UUID 字段。改动与 v1.20 完全相同。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java` (+13/-6 lines)

**修改目的**：在 Flink 2.1 Parquet 读取器测试中同步添加 UUID 字段。改动与 v1.20 完全相同。

## 总结

本提交通过在 `DataTestBase.SUPPORTED_PRIMITIVES` 中新增 UUID 字段，并同步更新 Flink 1.20/2.0/2.1 三个版本的 Parquet 读取器测试 schema，补全了对 UUID 类型数据的读写测试覆盖。这有助于及早发现 Flink Parquet 读取器在处理 UUID 类型时的潜在 Bug，提升数据类型的兼容性与正确性保障。改动均为测试代码，不影响产品运行时行为。
