# 提交 3826：Data: Add TCK for Writer builder in FileFormat API (#16575)

## 提交信息

- **序号**：3826 / 4088
- **哈希**：719776595988a0444a90a60a09fdb0d42ab5d7c1
- **短哈希**：719776595
- **日期**：2026-06-05 12:41:52 +0200
- **作者**：GuoYu <511955993@qq.com>
- **提交说明**：Data: Add TCK for Writer builder in FileFormat API (#16575)
- **PR/Issue**：#16575

## 总体目的

本提交为 Iceberg 的 `FileFormat` 写入器构建器（Writer builder）API 引入一套 TCK（Technology Compatibility Kit，技术兼容性套件）风格的测试基础设施。在此之前，`data` 模块的 `BaseFormatModelTests` 中混杂了大量针对 Avro、ORC、Parquet 三种格式的格式特定写入与校验逻辑（如用 `AvroParquetWriter` 写 Parquet、用 `OrcFile.Writer` 写 ORC、用 `DataFileWriter` 写 Avro），导致测试类庞大、格式间逻辑纠缠，新增格式或调整写入行为时需要在多处修改。

本提交提取一个 `FileFormatTestSupport` 接口作为各格式的测试支持抽象，并为 Avro、ORC、Parquet 各提供一个实现类（`AvroFormat`、`OrcFormat`、`ParquetFormat`），把格式特定的"无 field id 写入"、"测试属性设置与校验"、"元数据读取"、"split size 属性"等行为封装到各自的支持类中。`BaseFormatModelTests` 改为通过 `FileFormatTestSupport` 间接使用这些能力，从而消除格式特定的分支代码，使测试更统一、可扩展。这本质上是为 FileFormat 写入器建立一套可被各格式实现遵循的兼容性测试契约。

## 如何达成设计目的

设计上采用策略模式：定义 `FileFormatTestSupport` 接口，声明 `format()`、`writeRecordsWithoutFieldIds(...)`、`testPropertiesToSet()`、`checkTestProperties(...)`、`metadataValue(...)`、`splitSizeProperty()` 等方法；三种格式各实现一个支持类。接口提供静态 `formats()` 与 `forFormat(FileFormat)` 工厂便于测试枚举与查找。`BaseFormatModelTests` 移除内联的格式特定写入逻辑（大量 import 与分支代码），改为委托给对应的支持类，使测试主体聚焦于 Writer builder 的行为契约而非格式细节。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/FileFormatTestSupport.java` (+61/-0 lines, new file)

**修改目的**：定义格式测试支持抽象接口，作为 FileFormat 写入器 TCK 的契约。

**工作逻辑**：
- 接口声明：`format()` 返回 `FileFormat`；`writeRecordsWithoutFieldIds(OutputFile, Schema, List<Record>)` 写入不含 field id 的记录；`testPropertiesToSet()` 返回测试属性；`checkTestProperties(InputFile)` 校验属性是否写入；`metadataValue(InputFile, key)` 读取元数据值；`splitSizeProperty()` 返回 split size 属性名。
- 静态字段 `ALL` 持有 Avro/Orc/Parquet 三个实现实例。
- 静态方法 `formats()` 返回所有支持的 `FileFormat` 数组；`forFormat(FileFormat)` 按格式查找对应支持类。

### `data/src/test/java/org/apache/iceberg/data/avro/AvroFormat.java` (+101/-0 lines, new file)

**修改目的**：提供 Avro 格式的测试支持实现。

**工作逻辑**：
实现 `FileFormatTestSupport`，`format()` 返回 `FileFormat.AVRO`；`writeRecordsWithoutFieldIds` 用 Avro `DataFileWriter` 写入（去除 field id）；`testPropertiesToSet`/`checkTestProperties`/`metadataValue`/`splitSizeProperty` 提供 Avro 特定的属性（如 codec、schema）。

### `data/src/test/java/org/apache/iceberg/data/orc/OrcFormat.java` (+130/-0 lines, new file)

**修改目的**：提供 ORC 格式的测试支持实现。

**工作逻辑**：
实现 `FileFormatTestSupport`，`format()` 返回 `FileFormat.ORC`；`writeRecordsWithoutFieldIds` 用 `OrcFile.Writer` 写入；其它方法提供 ORC 特定属性（如 stripe size、compression）。

### `data/src/test/java/org/apache/iceberg/data/parquet/ParquetFormat.java` (+129/-0 lines, new file)

**修改目的**：提供 Parquet 格式的测试支持实现。

**工作逻辑**：
实现 `FileFormatTestSupport`，`format()` 返回 `FileFormat.PARQUET`；`writeRecordsWithoutFieldIds` 用 `AvroParquetWriter` 写入（先通过 `AvroTestHelpers.removeIds` 去除 field id）；`checkTestProperties` 用 `ParquetFileReader` 读取 page 元数据校验；`metadataValue` 读取 Parquet 文件 KV 元数据；`splitSizeProperty` 返回 Parquet 的 split size 属性名。

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+155/-204 lines, net -49 with refactor)

**修改目的**：重构测试基类，移除格式特定内联逻辑，改为委托给 `FileFormatTestSupport`。

**工作逻辑**：
- 移除大量格式特定的 import（`AvroParquetWriter`、`OrcFile`、`DataFileWriter`、`ParquetFileReader` 等）。
- `FILE_FORMATS` 改为 `FileFormatTestSupport.formats()`。
- 原本内联的写入逻辑（构造 `FileWriterBuilder`、写入、断言 `dataFile`）提取为 `writeEngineRecords` 等辅助方法，并在需要格式特定行为时通过 `FileFormatTestSupport.forFormat(fileFormat)` 委托。
- 测试主体聚焦于 Writer builder 的行为契约（如写入后 `toDataFile` 返回正确记录数与格式、属性下推、元数据写入等），而非格式细节。

## 总结

本提交为 FileFormat 写入器 API 建立了 TCK 风格的测试基础设施，通过 `FileFormatTestSupport` 抽象与三种格式实现，将格式特定逻辑从测试基类中剥离，使测试更统一、可扩展。这是测试架构的一次重要重构，为后续新增格式或调整写入行为提供了清晰的契约边界，降低了维护成本。新增的格式支持类也让各格式的写入/校验逻辑集中管理，便于独立演进。
