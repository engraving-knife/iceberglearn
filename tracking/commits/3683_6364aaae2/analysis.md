# 提交 3683：Data: Add TCK tests for Schema Evolution in BaseFormatModelTests (#15843)

## 提交信息

- **序号**：3683 / 4088
- **哈希**：6364aaae20c50b8bfeb550ec89b3b14feedd51b0
- **短哈希**：6364aaae2
- **日期**：2026-05-11 12:36:36 +0200
- **作者**：GuoYu
- **提交说明**：Data: Add TCK tests for Schema Evolution in BaseFormatModelTests (#15843)
- **PR/Issue**：#15843

## 总体目的

这个提交为 Iceberg 的数据格式模块添加了全面的 Schema Evolution（模式演进）TCK（Technology Compatibility Kit）测试。Schema Evolution 是 Iceberg 核心能力之一，允许用户在不重写数据的情况下修改表结构（如添加列、删除列、重命名列、类型提升等），并通过字段 ID 而非字段名来匹配数据。

此前 `BaseFormatModelTests` 作为格式兼容性测试基类，主要覆盖基本的数据读写场景，但对于 Schema Evolution 这一关键能力缺乏系统性测试。本提交补全了这一空白，确保所有文件格式（Parquet、Avro、ORC）在各种 schema 变更场景下都能正确读取历史数据，从而保证不同引擎（Spark、Flink 等）的实现符合 Iceberg 规范。

## 如何达成设计目的

通过在 `BaseFormatModelTests` 抽象测试基类中新增一系列参数化测试方法，覆盖 Schema Evolution 的各种场景。这些测试是参数化的，会针对所有文件格式（Parquet、Avro、ORC）自动运行，所有继承该基类的引擎实现（Spark、Flink、Core 等）都会自动获得这些测试覆盖。

为支持测试，还需要新增辅助工具方法：
- 在 `AvroTestHelpers` 中暴露 `hasIds` 和 `removeIds` 方法
- 在 `TestORCSchemaUtil` 中暴露 `removeIds` 和 `hasIds` 方法
- 新建 `OrcWritingTestUtils` 和 `ParquetFileTestUtils` 工具类
- 在各模块的 `build.gradle` 中添加测试依赖

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+450 lines)

**修改目的**：新增 Schema Evolution 系列测试方法和支持工具方法。

**工作逻辑**：

新增的测试用例覆盖了以下 Schema Evolution 场景：

1. **`testSchemaEvolutionAddColumn`**：添加列测试。用原始 schema 写入数据，用扩展后的 schema（新增两个 optional 列）读取。新增列应为 null。

2. **`testSchemaEvolutionProjection`**：列裁剪/投影测试。用完整 schema 写入，仅用首尾两列的 schema 读取，验证可以正确投影读取。

3. **`testSchemaEvolutionDropAndReAddSameNameColumn`**：删除并重添加同名列测试。原始 `col_b` 的 field ID 为 2，新 schema 中 `col_b` 使用 field ID 6。由于 Iceberg 基于 field ID 匹配，重添加的列应读取为 null（旧数据中没有该 ID 的列）。

4. **`testSchemaEvolutionTypePromotionIntToLong`** / **`testSchemaEvolutionTypePromotionFloatToDouble`** / **`testSchemaEvolutionTypePromotionDecimalPrecision`**：类型提升测试。验证 Integer→Long、Float→Double、Decimal 精度提升等合法的类型扩展。

5. **`testSchemaEvolutionReorderColumns`**：列重排序测试。验证读取时列顺序与写入时不同，数据仍能正确匹配。

6. **`testSchemaEvolutionRenameColumn`**：列重命名测试。利用 Iceberg 基于 field ID 而非名称匹配的特性，验证重命名后仍能读取原数据。

7. **`testSchemaEvolutionRequiredToOptional`**：约束放宽测试。验证 required 列变为 optional 后仍能正确读取。

8. **`testSchemaEvolutionEmptyProjection`**：空投影测试。用空 schema 读取，验证返回正确行数但无数据列。

9. **`testReadFileWithoutFieldIdsUsingNameMapping`**：NameMapping 测试。验证当数据文件不含 Iceberg field ID（如外部工具写入）时，通过 NameMapping 仍能正确读取。

支持性工具方法包括：
- `writeRecordsWithoutFieldIds`：分派到各格式的无 ID 写入方法
- `writeAvroWithoutFieldIds` / `writeParquetWithoutFieldIds` / `writeOrcWithoutFieldIds`：分别用原始 Avro/Parquet/ORC API 写入不含 Iceberg field ID 的文件
- `runTypePromotionCheck`：通用类型提升测试辅助方法
- `readAndAssertEngineRecords`：通用的读取并断言方法

### `core/src/test/java/org/apache/iceberg/avro/AvroTestHelpers.java` (+8 lines)

**修改目的**：暴露 Avro schema 的 ID 操作工具方法。

**工作逻辑**：

```java
public static boolean hasIds(Schema schema) {
  return AvroSchemaUtil.hasIds(schema);
}

public static Schema removeIds(org.apache.iceberg.Schema schema) {
  return RemoveIds.removeIds(schema);
}
```

新增两个静态方法，分别用于检查 Avro schema 是否包含 field ID，以及从 Iceberg schema 生成不含 ID 的 Avro schema。这些方法用于测试外部写入场景。

### `orc/src/test/java/org/apache/iceberg/orc/OrcWritingTestUtils.java` (new file, +35 lines)

**修改目的**：提供 ORC 写入测试所需的文件系统工具方法。

**工作逻辑**：

新建工具类，提供 `outputFileSystem(OutputFile)` 和 `inputFileSystem(InputFile)` 方法，将 Iceberg 的 `InputFile`/`OutputFile` 抽象包装为 Hadoop `FileSystem`，以便 ORC 原生 API 进行读写。

### `orc/src/test/java/org/apache/iceberg/orc/TestORCSchemaUtil.java` (+8 lines)

**修改目的**：暴露 ORC schema 的 ID 操作工具方法。

**工作逻辑**：

```java
public static TypeDescription removeIds(TypeDescription type) {
  return ORCSchemaUtil.removeIds(type);
}

public static boolean hasIds(TypeDescription orcSchema) {
  return ORCSchemaUtil.hasIds(orcSchema);
}
```

### `parquet/src/test/java/org/apache/iceberg/parquet/ParquetWritingTestUtils.java` (+1/-1 lines)

**修改目的**：将类可见性从包级改为 public。

**工作逻辑**：

```java
-class ParquetWritingTestUtils {
+public class ParquetWritingTestUtils {
```

将类从包私有改为 public，以便其他模块（如 iceberg-data）的测试可以访问。

### `parquet/src/testFixtures/java/org/apache/iceberg/parquet/ParquetFileTestUtils.java` (new file, +36 lines)

**修改目的**：提供 Parquet 文件 IO 的测试工具方法。

**工作逻辑**：

新建工具类，放在 `testFixtures` 目录下（可被其他模块共享），提供 `file(OutputFile)` 和 `file(InputFile)` 方法，将 Iceberg 的 IO 抽象转换为 Parquet 的 IO 抽象。

### `build.gradle` (+9 lines)

**修改目的**：为 iceberg-data 模块添加 ORC 和 Parquet 测试依赖。

**工作逻辑**：

在 `iceberg-data` 项目中添加：
```groovy
testImplementation project(path: ':iceberg-orc', configuration: 'testArtifacts')
testImplementation(testFixtures(project(':iceberg-parquet')))
```

同时在 `iceberg-parquet` 项目中新增 `testFixturesApi` 依赖配置，使 Parquet 的测试工具能被其他模块复用。

### `flink/v1.20/build.gradle`、`flink/v2.0/build.gradle`、`flink/v2.1/build.gradle` (各 +2 lines)

**修改目的**：为各 Flink 版本模块添加 ORC 和 Parquet 测试依赖。

### `spark/v3.4/build.gradle` (+2 lines)、`spark/v3.5/build.gradle` (+1 line)、`spark/v4.0/build.gradle` (+1 line)、`spark/v4.1/build.gradle` (+1 line)

**修改目的**：为各 Spark 版本模块添加 ORC 和 Parquet 测试依赖。v3.4 添加了两项依赖（ORC 和 Parquet），其他版本仅补充缺失的一项。

## 总结

这是一个重要的测试基础设施提交，为 Iceberg 的核心 Schema Evolution 能力建立了全面的 TCK 测试覆盖。通过在 `BaseFormatModelTests` 基类中添加覆盖列增删、重命名、重排序、类型提升、约束放宽、空投影、NameMapping 等场景的参数化测试，确保所有文件格式（Parquet/Avro/ORC）和所有引擎实现（Spark/Flink/Core）都能正确处理 schema 变更。这种统一的 TCK 测试对于保证 Iceberg 多引擎生态的兼容性至关重要，同时新增的辅助工具类也提升了测试代码的可复用性。
