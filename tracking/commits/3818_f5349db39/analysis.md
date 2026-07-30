# 提交 3818：Arrow: Fix vectorized reads of decimal columns with default values (#16501)

## 提交信息

- **序号**：3818 / 4088
- **哈希**：f5349db39ea92cee9585f10fee0d5a91d858f22e
- **短哈希**：f5349db39
- **日期**：2026-06-02 13:26:58 -0600
- **作者**：Hao Jiang <hao.jiang@databricks.com>
- **提交说明**：Arrow: Fix vectorized reads of decimal columns with default values (#16501)
- **PR/Issue**：#16501

## 总体目的

本提交修复 Iceberg Arrow 向量化读取器在读取"带有默认值的 decimal 列"时发生的类型转换错误。当 Iceberg schema 中的 decimal 列定义了 `initialDefault` 或 `writeDefault`（类型为 decimal 的 `Literal`，例如 `new BigDecimal("0.00")`），`VectorizedArrowReader` 在构造 Parquet 物理类型对应的 `NestedField` 时，会原样保留这些 default 值。然而，default 值是按逻辑类型（decimal）定义的，而读取器构造的物理类型可能是底层存储类型（如 INT32 用于低精度 decimal、INT64 用于中等精度、FIXED_LEN_BYTE_ARRAY 用于高精度）。这导致 default 值（BigDecimal）无法被强制转换为物理类型（如 long），在运行时抛出 `ClassCastException`，使读取失败。

修复方式是在构造物理类型的 `NestedField` 时，显式丢弃 `initialDefault` 与 `writeDefault`（置为 null），因为 default 值是面向逻辑类型的，不应传播到物理类型字段上。读取器只关心物理存储布局，default 值由上层逻辑层处理。

## 如何达成设计目的

在 `VectorizedArrowReader` 构建物理 `NestedField` 的位置，通过 `Types.NestedField.from(logicalType).ofType(type)` 链式调用后，追加 `.withInitialDefault(null).withWriteDefault(null)`，确保物理字段不携带逻辑类型的 default 值。同时新增专门的测试类 `TestVectorizedDefaultValues`，构造一个包含三种精度（int-backed、long-backed、fixed-backed）且都带默认值的 decimal 列的表，写入数据后用向量化读取，验证不会抛出类型转换异常且读回的值正确。此外在 `TestParquetVectorizedReads` 中补充覆盖。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (+8/-1 lines)

**修改目的**：在构造 decimal 物理类型字段时丢弃逻辑类型的 default 值，避免 ClassCastException。

**工作逻辑**：
原代码：
```java
physicalType = Types.NestedField.from(logicalType).ofType(type).build();
```
修改为：
```java
// drop initialDefault/writeDefault: they are typed for the logical (decimal) type and
// cannot be cast to the underlying physical type
physicalType =
    Types.NestedField.from(logicalType)
        .ofType(type)
        .withInitialDefault(null)
        .withWriteDefault(null)
        .build();
```
注释明确说明：default 值是按逻辑（decimal）类型定义的，无法转换为底层物理类型。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/TestVectorizedDefaultValues.java` (+140/-0 lines, new file)

**修改目的**：新增专门针对带默认值 decimal 列的向量化读取测试。

**工作逻辑**：
`testDecimalWithDefaultValueNotDictionaryEncoded`：
- 构造 schema 含 4 列：`id`(long)、`int_backed`(decimal(5,2))、`long_backed`(decimal(15,2))、`fixed_backed`(decimal(25,2))，后三列均设置 `initialDefault` 与 `writeDefault` 为 `BigDecimal("0.00")`，覆盖三种物理存储（INT32、INT64、FIXED_LEN_BYTE_ARRAY）。
- 写入 5 行数据，关闭字典编码（`ENABLE_DICTIONARY=false`）。
- 用 `VectorizedTableScanIterable` 向量化读取，逐行断言三列 decimal 读回值与写入值相等。
- 该测试在修复前会因 ClassCastException 失败。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetVectorizedReads.java` (+46/-0 lines)

**修改目的**：在 Parquet 向量化读取测试中补充带默认值 decimal 列的覆盖。

**工作逻辑**：
补充一个测试，构造带默认值的 decimal 列并验证向量化读取正确性，与 Arrow 模块测试形成呼应。

## 总结

本提交修复了一个影响带默认值 decimal 列向量化读取的运行时错误：逻辑类型的 default 值被错误地传播到物理类型字段上，导致 ClassCastException。修复通过在物理字段构造时丢弃 default 值，使读取器只关注物理存储布局。新增测试覆盖三种精度的 decimal 列，确保修复稳固。这是 Arrow 向量化读取对 schema 默认值特性兼容性的重要修复。
