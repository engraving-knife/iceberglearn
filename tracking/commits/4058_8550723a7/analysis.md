# 提交 4058：Core: Read and write geometry and geography values in Avro (#17119)

## 提交信息

- **序号**：4058 / 4088
- **哈希**：8550723a766994118e2537a0c69d4742d61dce94
- **短哈希**：8550723a7
- **日期**：2026-07-16 18:46:34 -0700
- **作者**：Xin Huang
- **提交说明**：Core: Read and write geometry and geography values in Avro (#17119)
- **PR/Issue**：#17119

## 总体目的

这个提交为 Iceberg 的 Avro 读写器添加了对 geometry（几何）和 geography（地理）空间数据类型的支持。Iceberg 此前已经引入了 `GeometryType` 和 `GeographyType` 两种空间类型（基于 CRS84 坐标系，以 WKB—Well-Known Binary—格式存储为字节），但 Avro 格式的读写尚未完整支持这两种类型。

具体来说，`TypeToSchema`（Iceberg 类型到 Avro schema 的映射器）在处理 `GEOMETRY` 和 `GEOGRAPHY` 类型时缺少对应的 case 分支，导致这两种类型无法正确映射到 Avro 的 BINARY schema。本提交通过将这两种空间类型与 `BINARY` 类型统一处理（映射到 Avro `BINARY_SCHEMA`，底层以 `ByteBuffer`/`byte[]` 存储 WKB 字节），使 Avro 能够读写 geometry 和 geography 列。

同时，提交在多个测试层面验证了这一支持：测试辅助工具（`AvroTestHelpers`、`RandomAvroData`）增加对空间类型的处理，多个 Avro 测试类启用 `supportsGeospatial()` 标志，并新增了 Avro 和 Parquet 的 WKB 往返（round-trip）测试。

## 如何达成设计目的

核心改动是将 `TypeToSchema` 中 `BINARY` 的 case 扩展为同时覆盖 `GEOMETRY` 和 `GEOGRAPHY`，因为这三者在 Avro 层面的表示完全相同（都是字节缓冲区）。然后在测试基础设施中同步处理这两种类型：`RandomAvroData` 生成随机数据时将空间类型值包装为 `ByteBuffer`，`AvroTestHelpers` 在值比较时将空间类型与 `BINARY` 等同等对待。各 Avro 测试类通过覆写 `supportsGeospatial()` 返回 true 来启用空间类型测试。新增的 `testGeospatialWkbRoundTrip` 测试使用真实的 WKB 点数据验证端到端读写正确性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/TypeToSchema.java` (+2/-0 lines)

**修改目的**：让 Avro 类型映射器处理 geometry/geography 类型。

**工作逻辑**：
```java
case BINARY:
case GEOMETRY:
case GEOGRAPHY:
  primitiveSchema = BINARY_SCHEMA;
  break;
```
将 `GEOMETRY` 和 `GEOGRAPHY` 的 case 穿透到 `BINARY` 分支，统一映射到 Avro 的 `BINARY_SCHEMA`。因为空间类型在存储层面就是 WKB 字节，与 `BINARY` 的 Avro 表示一致。

### `core/src/test/java/org/apache/iceberg/avro/AvroTestHelpers.java` (+2/-0 lines)

**修改目的**：在测试值比较中处理空间类型。

**工作逻辑**：在值相等性断言的 switch 中，将 `GEOMETRY` 和 `GEOGRAPHY` 穿透到 `BINARY`/`FIXED`/`DECIMAL` 分支，使用 `assertThat(actual).isEqualTo(expected)` 直接比较，因为它们都是字节缓冲区。

### `core/src/test/java/org/apache/iceberg/avro/RandomAvroData.java` (+2/-0 lines)

**修改目的**：在随机数据生成中处理空间类型。

**工作逻辑**：
```java
case BINARY:
case GEOMETRY:
case GEOGRAPHY:
  return ByteBuffer.wrap((byte[]) result);
```
将空间类型的随机生成值（`byte[]`）包装为 `ByteBuffer`，与 `BINARY` 一致。

### `core/src/test/java/org/apache/iceberg/avro/TestAvroDataWriter.java` (+54/-0 lines)

**修改目的**：新增 Avro 空间数据 WKB 往返测试。

**工作逻辑**：`testGeospatialWkbRoundTrip` 测试：
- 创建含 `GeometryType.crs84()` 和 `GeographyType.crs84()` 列的 schema。
- 构造 3 条记录：完整点数据、geog 为 null、两者均 null。
- 通过 `Avro.writeData` 写入，再通过 `Avro.read` 读出，断言读写一致。
- 使用 `RandomUtil.wkbPoint(x, y)` 生成真实的 WKB 点数据。

### `core/src/test/java/org/apache/iceberg/avro/TestAvroEncoderUtil.java`、`TestGenericAvro.java`、`TestInternalAvro.java`、`data/src/test/java/org/apache/iceberg/data/avro/TestGenericData.java` (各 +5/-0 lines)

**修改目的**：在这些 Avro 测试类中启用空间类型支持。

**工作逻辑**：每个类覆写 `supportsGeospatial()` 返回 `true`，使继承自 `DataTestBase` 的通用测试用例包含 geometry/geography 类型的读写验证。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (+37/-0 lines)

**修改目的**：新增 Parquet 空间数据 WKB 往返测试。

**工作逻辑**：`testGeospatialWkbRoundTrip` 测试：
- 创建含 geometry 和 geography 列的 schema。
- 注释说明：Parquet 写入器会解析 WKB 值以构建地理空间统计信息，因此必须使用真实 WKB 点数据（任意字节会解析失败并被静默排除出统计）。
- 写入含数据和 null 的两条记录，读出后断言值一致。

## 总结

这个提交补齐了 Iceberg Avro 格式对 geometry 和 geography 空间数据类型的读写支持。核心改动极小（将两种空间类型穿透到 BINARY 处理），但测试覆盖全面——从随机数据生成、值比较辅助工具、多个 Avro 测试类启用空间支持，到 Avro 和 Parquet 的 WKB 往返测试。特别是 Parquet 测试注意到写入器会解析 WKB 构建地理空间统计这一细节，使用了真实 WKB 点数据。这使 Iceberg 的空间类型支持在 Avro 格式上完整可用。
