# 提交序号 1510 短哈希 7e1a4c9fe 分析

## 提交信息
- 哈希：7e1a4c9fedeb679be85a1921ade7995d5ec2cbec
- 日期：2024-12-18
- 作者：Ryan Blue <blue@apache.org>
- 消息：Spark 3.5: Support default values in Parquet reader (#11803)

## 总体目的

本提交为 Spark 3.5 的 Parquet 读取器添加对字段默认值（default values）的支持。这是 Iceberg schema 演进能力的重要一环：当用户在表 schema 中新增一个带有默认值的列后，读取那些在该列添加之前写入的旧数据文件时，该列在文件中不存在。修改前，读取器对于文件中不存在的列一律返回 null（对于可选列）或交给上层处理；修改后，读取器会检查该字段是否定义了 `initialDefault()`，若定义则用默认值填充，从而保证读取结果与新 schema 一致。

这种能力对 schema 演进的体验至关重要。例如，表中新增一个 `status` 列并设默认值 `"active"`，那么读取旧文件时该列应显示为 `"active"` 而非 null，这样下游分析逻辑才能基于一致的 schema 工作。对于新增的必填列（required column）带默认值的情况，这一支持更是必须的——否则旧文件中缺失该必填列会导致读取失败或返回 null（违反必填语义）。

此外，本提交还将原先位于 `BaseReader` 中的 `convertConstant` 方法上移到 `SparkUtil` 工具类并改为 public，使其能在 Parquet 读取器中被复用，用于将 Iceberg 内部对象模型的默认值转换为 Spark 内部表示。

## 如何达成设计目的

本提交通过三个主代码文件的修改和两个测试文件的修改来达成目的。核心思路是：在 `SparkParquetReaders` 构建读取器时，对于文件中不存在的字段，依次检查是否有常量映射、是否有默认值、是否可选，据此决定填充常量、默认值还是 null；若必填字段既无数据又无默认值则报错。同时将 `convertConstant` 工具方法提取到 `SparkUtil` 供复用。

### 修改详情

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkUtil.java

新增 `convertConstant(Type type, Object value)` 公共静态方法，该方法从 `BaseReader` 中搬迁而来，用于将 Iceberg 内部对象模型的值转换为 Spark 内部表示。转换逻辑按类型分派：
- DECIMAL：`BigDecimal` → Spark `Decimal`
- STRING：处理 Avro `Utf8`（直接从字节构造 `UTF8String`，避免拷贝）和普通字符串
- FIXED：处理 `byte[]`、Avro `GenericData.Fixed`、`ByteBuffer` 三种形态
- BINARY：`ByteBuffer` → `byte[]`
- STRUCT：递归转换，构造 `GenericInternalRow`；空 struct 返回空 `GenericInternalRow`
- 其它类型：原值返回

新增多个 import 支持上述转换。将该方法设为 public 使得 Parquet 读取器在填充默认值时能复用同一套转换逻辑，保证一致性。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java

这是本次修改的核心——修改 `buildReader` 中字段重排序（reorder）的逻辑。修改前的逻辑较为简单：对于 expectedFields 中的每个字段，若有常量映射则用常量读取器，若有 IS_DELETED 则用 false 常量，否则尝试从 readersById 获取实际读取器，若都没有则用 null 读取器。

修改后的逻辑增加了对默认值和必填字段的处理，优先级如下：
1. 若字段在 `idToConstant` 中（分区列等已有常量映射），使用该常量。
2. 若是 `MetadataColumns.IS_DELETED`，使用 false 常量。
3. 若 `readersById` 中存在该字段的读取器（说明文件中有该列），使用实际读取器。
4. 若字段定义了 `field.initialDefault()`（非 null），使用 `SparkUtil.convertConstant` 转换默认值后构造常量读取器，并传入该字段的最大定义级别（从 `maxDefinitionLevelsById` 获取或用默认值）。这一步是本次提交的核心新增能力。
5. 若字段是可选的（`field.isOptional()`），使用 null 读取器。
6. 否则（必填字段且文件中无数据且无默认值），抛出 `IllegalArgumentException("Missing required field: ...")`。

修改前第6种情况会被当作可选字段返回 null，掩盖了必填字段缺失的问题；修改后明确报错，更符合语义。此外还有一个小的泛型修正：`UnboxedReader(desc)` 改为 `UnboxedReader<>(desc)`。

#### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java

将原先的 `convertConstant` 方法删除（已搬迁到 `SparkUtil`），并将 `constantsMap` 方法中对 `BaseReader::convertConstant` 的引用改为 `SparkUtil::convertConstant`。同时清理了因此不再需要的 import（`BigDecimal`、`ByteBuffer`、`GenericData`、`Utf8`、`Type`、`NestedField`、`GenericInternalRow`、`Decimal`、`UTF8String`）。这是重构性的改动，不改变运行时行为。

#### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java

修改 `assertEqualsUnsafe` 方法以支持"写入 schema 与读取 schema 不一致"的测试场景。修改前，该方法假设写入记录（Record）与读取行（InternalRow）的字段位置一一对应。修改后，对于读取 schema 中的每个字段，先通过字段名在写入记录的 Avro schema 中查找对应字段（`rec.getSchema().getField(field.name())`）：若找到则按写入位置取值，若找不到（说明是读取时新增的带默认值列）则取 `field.initialDefault()` 作为期望值。这使得测试能够验证"旧文件按新 schema（含默认值列）读取"的场景。

另外修正了一个泛型警告：`(Collection) expected` 改为 `(Collection<?>) expected`。

#### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReader.java

`writeAndValidate` 方法重构为接受两个 schema 参数：`writeSchema`（写入文件时用的旧 schema）和 `expectedSchema`（读取时投影的新 schema），原有单参数方法委托给双参数方法。这样测试可以模拟"用旧 schema 写文件、用新 schema（含新增默认值列）读文件"的场景。

新增多个测试用例：
- `testMissingRequiredWithoutDefault()`：新增一个必填列且无默认值，验证读取时抛 `IllegalArgumentException("Missing required field: missing_str")`。
- `testDefaultValues()`：新增带默认值的必填列（`missing_str` 默认 "orange"）和可选列（`missing_int` 默认 34），验证读取旧文件时这些列填入默认值。同时验证已有列的 `initialDefault`（设为 "wrong!"）不会被使用（因为文件中已有该列数据）。
- `testNullDefaultValue()`：新增一个无默认值的可选日期列，验证读取时该列为 null。
- `testNestedDefaultValue()`：在嵌套 struct 中新增带默认值的字段（`missing_inner_float` 默认 -0.0F），验证嵌套场景下默认值正确填充。
- `testMapNestedDefaultValue()`：在 map 的值 struct 中新增带默认值字段，验证更深层嵌套场景。

## 小结

本提交为 Spark 3.5 Parquet 读取器添加了字段默认值支持，使 schema 演进中新增的带默认值列在读取旧文件时能正确填充默认值，而非一律返回 null。核心改动是在 `SparkParquetReaders` 的字段重排序逻辑中新增了"字段有 initialDefault 则用常量读取器填充转换后的默认值"这一分支，并对必填字段缺失且无默认值的情况明确报错。同时将 `convertConstant` 工具方法从 `BaseReader` 提取到 `SparkUtil` 以供复用，配套测试覆盖了默认值填充、null 默认、嵌套默认、map 嵌套默认以及必填字段缺失报错等多种场景。这一改进显著增强了 Iceberg 在 Spark 上的 schema 演进能力，是后续"修复 Parquet 和 Avro 默认值日期/时间表示"（提交 1511）的前置基础。
