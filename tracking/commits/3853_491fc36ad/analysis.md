# 提交 3853：Data: Add TCK coverage for reader default values (#16638)

## 提交信息

- **序号**：3853 / 4088
- **哈希**：491fc36ad439d8eb31a5cd0cb043bec4e25d3b0c
- **短哈希**：491fc36ad
- **日期**：2026-06-10 15:02:28 +0200
- **作者**：Joy Haldar
- **提交说明**：Data: Add TCK coverage for reader default values (#16638)
- **PR/Issue**：#16638

## 总体目的

本提交大幅扩展了 Iceberg TCK（Technology Compatibility Kit）中关于读取器默认值（reader default values）的测试覆盖。Iceberg 支持在 schema 演进时为新增列指定默认值（`initialDefault`），当读取旧数据文件（不含新列）时，读取器应自动用默认值填充该列。

在此之前，TCK 中只有一个简单的 `testReaderSchemaEvolutionNewColumnWithDefault` 测试，仅覆盖了顶层 String 和 Integer 列的默认值场景。这远不够全面，无法发现各引擎在处理更复杂场景（如嵌套 struct 中的默认值、Map value 的默认值、List element 的默认值、各种基本类型的默认值、缺少默认值的必填列报错等）时的兼容性问题。

本提交通过新增 8 个测试用例和一个 `PrimitiveDefaults` 数据生成器，全面覆盖了读取器默认值的各种场景，确保各引擎实现正确遵循规范。

## 如何达成设计目的

整体设计分两部分：

1. **新增 `PrimitiveDefaults` 数据生成器**：在 `DataGenerators` 中定义一个包含 14 种基本类型（Boolean、Integer、Long、Float、Double、Date、TimestampTz、Timestamp、String、UUID、Binary、Decimal、Time）且每种都有默认值的 schema，以及一个只含 `id` 列的写入 schema。

2. **新增 8 个测试用例**：在 `BaseFormatModelTests` 中覆盖各种默认值场景，包括基本默认值、null 默认值、嵌套 struct 默认值、Map 嵌套默认值、List 嵌套默认值、缺少默认值的必填列报错、基本类型默认值全面覆盖、默认值不被错误应用（列已存在时）。

同时将原有的 `testReaderSchemaEvolutionNewColumnWithDefault` 测试替换为更全面的 `testDefaultValues` 测试。所有测试使用 `assumeSupports(fileFormat, FEATURE_READER_DEFAULT)` 跳过不支持的格式。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+412/-26 lines)

**修改目的**：新增全面的读取器默认值测试。

**工作逻辑**：

新增 8 个测试用例（替换原有的 1 个）：

1. **`testDefaultValues`**：基础场景。写入 schema 含 `id` 和 `data`（有 `initialDefault` 但写入时存在），读取 schema 新增 `missing_str`（默认 "orange"）和 `missing_int`（默认 34）。验证新列填入默认值，已有列不受影响。关键点：`data` 字段的 `initialDefault` 不应被应用（因为列已存在于写入 schema 中）。

2. **`testNullDefaultValue`**：新增可选列 `missing_date` 无默认值，验证读取时为 null。

3. **`testNestedDefaultValue`**：嵌套 struct 场景。写入 schema 有 `nested.inner`，读取 schema 新增 `nested.missing_inner_float`（默认 -0.0F）。验证嵌套 struct 中新增字段的默认值被正确应用。

4. **`testMapNestedDefaultValue`**：Map value 为 struct，新增 value 中的 `value_int`（默认 34）。验证 Map 中每个 value 的 struct 都填入默认值。

5. **`testListNestedDefaultValue`**：List element 为 struct，新增 element 中的 `element_int`（默认 34）。验证 List 中每个 element 的 struct 都填入默认值。

6. **`testMissingRequiredWithoutDefault`**：新增必填列 `missing_str` 无默认值，验证抛出 `IllegalArgumentException("Missing required field: missing_str")`。

7. **`testPrimitiveDefaultValues`**：使用 `PrimitiveDefaults` 生成器，写入只有 `id` 的 schema，读取含 14 种基本类型默认值的 schema。验证每种类型的默认值都被正确应用。

8. **`testPrimitiveDefaultValuesNotApplied`**：反向验证——用完整 schema（含默认值列）写入并读取，验证实际数据值不被默认值覆盖。

### `data/src/test/java/org/apache/iceberg/data/DataGenerators.java` (+76/-0 lines)

**修改目的**：新增 `PrimitiveDefaults` 数据生成器。

**工作逻辑**：

定义 `PrimitiveDefaults` 内部类，包含：
- `READ_SCHEMA`：14 种基本类型列，每种都有 `initialDefault`（Boolean=false, Integer=34, Long=4900000000L, Float=12.21F, Double=-0.0D, Date=2024-12-17, TimestampTz/Timestamp=2024-12-17T23:59:59.999999, String="iceberg", UUID=random, Binary={0x0a,0x0b}, Decimal=12.34, Time=23:59:59.999999）。
- `WRITE_SCHEMA`：仅含 `id` 列。
- `optionalWithDefault()` 辅助方法使用 builder 模式构建带默认值的可选字段。

注意 FIXED 类型被排除（Spark 的 InternalRowConverter 期望 ByteBuffer 但生成器产生 byte[]），标注 TODO 待修复。

## 总结

本提交是 TCK 测试覆盖的重要提升，将读取器默认值测试从单一简单场景扩展到 8 个全面测试用例，覆盖基本类型、嵌套 struct、Map value、List element、必填列缺失报错、默认值不误应用等各种场景。新增的 `PrimitiveDefaults` 生成器覆盖了 14 种基本类型的默认值。这对于保证各引擎在 schema 演进时正确处理默认值的互操作性具有重要意义。
