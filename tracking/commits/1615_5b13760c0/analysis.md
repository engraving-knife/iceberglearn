# 提交 1615 5b13760c0 分析

## 提交信息
- 哈希：5b13760c01bdbb2ab15be072c582adb2a8792f23
- 日期：2025-01-21 14:46:20 -0800
- 作者：Ryan Blue
- 消息：Spark 3.3: Backport support for default values (#11988)

## 总体目的

本提交把 Iceberg 表格式 v3 引入的"字段默认值"（initial-default / write-default）读取支持从 Spark 3.4/3.5 模块回迁到 Spark 3.3 模块。Iceberg v3 schema 允许为字段声明 `initial-default`（表中不存在该列历史文件时用于填充的默认值）和 `write-default`（写入时未提供值时使用的默认值）。Spark 3.4/3.5 集成已经支持在读取时为缺失字段填充默认值，本提交让 Spark 3.3 集成也具备这一能力，从而保持各 Spark 版本在 v3 表上的功能一致。

具体来说，本提交实现了：当读取 v3 表时，若数据文件中不存在某些字段（例如 schema 演进后新增的字段），但该字段在 schema 中声明了 `initial-default`，则读取器会用默认值填充；若字段既不存在于文件也没有默认值，则按 nullable 填充 null；若字段是 required 但既不存在于文件也没有默认值，则直接抛 `IllegalArgumentException("Missing required field: ...")`，避免静默返回错误数据。

此外，本提交还顺带重构了 Avro 读取路径：新增 `SparkPlannedAvroReader` 取代原有的 `SparkAvroReader`，前者基于 `ValueReaders.buildReadPlan` 构建"按需读取计划"，能更高效地跳过文件中存在但 schema 不需要的字段，并为默认值/常量列留出位置。旧 `SparkAvroReader` 被标记为 `@Deprecated`（计划在 1.8.0 移除），但保留以兼容外部调用。

## 如何达成设计目的

设计思路分三层：

1. **统一类型转换工具**：把原本散落在 `BaseReader.convertConstant` 中的"Iceberg 内部值 → Spark InternalRow 值"的转换逻辑抽到 `SparkUtil.internalToSpark`，使其可被多个读取器（Parquet reader、Vectorized reader、Avro reader、分区常量填充）复用。这样默认值也能通过同一个转换路径从 Iceberg 类型（BigDecimal、Utf8、ByteBuffer、StructLike 等）转成 Spark 类型（Decimal、UTF8String、byte[]、GenericInternalRow 等）。

2. **Parquet 读取侧填充默认值**：在 `SparkParquetReaders.buildStructReader` 的字段重排逻辑中，新增 `field.initialDefault() != null` 分支：当字段在文件中没有对应 reader、但声明了 initial-default 时，插入一个 `ParquetValueReaders.constant(SparkUtil.internalToSpark(field.type(), field.initialDefault()), maxDefinitionLevel)` 常量 reader，让读取结果中该字段始终为默认值。同时把"既无 reader 也无默认值的 required 字段"改为抛 `IllegalArgumentException`，而 optional 字段仍走 `ParquetValueReaders.nulls()`。

3. **Avro 读取侧改用 Planned reader**：新增 `SparkPlannedAvroReader`，它通过 `AvroWithPartnerVisitor.visit` + 自定义 `ReadBuilder` 构建 `ValueReader`，在 record 节点调用 `ValueReaders.buildReadPlan` 生成"字段 ID → reader"的有序读计划，并把默认值/常量通过 `SparkUtil::internalToSpark` 转换后注入计划。配套新增 `SparkValueReaders.PlannedStructReader`，按 readPlan 只读取文件中实际存在的字段、其余位置由常量/默认值/null 填充。`BaseRowReader` 的 Avro 读取路径从 `SparkAvroReader` 切换到 `SparkPlannedAvroReader.create`。

测试侧则把原来散落在 `TestAvroScan`、`TestParquetScan`、`TestDataFrameWrites` 中的样板代码抽到新的 `ScanTestBase` 和 `DataFrameWriteTestBase`，并让 `AvroDataTest` 增加大量针对默认值场景的测试（`testDefaultValues`、`testMissingRequiredWithoutDefault` 等），通过 `supportsDefaultValues()` 钩子让子类决定是否启用这些测试。

### 修改详情

#### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/SparkUtil.java

新增公共静态方法 `internalToSpark(Type type, Object value)`，实现 Iceberg 内部对象模型到 Spark InternalRow 值的转换：
- `DECIMAL` → `Decimal.apply(BigDecimal)`；
- `UUID`/`STRING` → `UTF8String`（兼容 Avro `Utf8`、`String` 等）；
- `FIXED` → `byte[]`（兼容 `byte[]`、`GenericData.Fixed`、`ByteBuffer`）；
- `BINARY` → `byte[]`；
- `STRUCT` → `GenericInternalRow`（递归转换每个字段，空 struct 返回 `new GenericInternalRow()`）；
- 其他类型原样返回。

该方法取代了 `BaseReader.convertConstant`，成为所有 Spark 读取器统一的"Iceberg 值 → Spark 值"出口。

#### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java

删除本地的 `convertConstant` 方法，`constantsMap` 改为 `PartitionUtil.constantsMap(task, partitionType, SparkUtil::internalToSpark)` 和 `PartitionUtil.constantsMap(task, SparkUtil::internalToSpark)`。这样分区常量、元数据列常量的填充路径与默认值填充路径共用同一个转换函数。

#### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java

`buildStructReader` 中字段重排逻辑调整（按优先级顺序）：
1. `idToConstant` 中有该字段 → 用常量 reader（用于分区值、`_file`、`_pos` 等元数据列）；
2. `IS_DELETED` 元数据列 → 常量 `false`；
3. 文件中有对应 reader → 用该 reader；
4. **新增**：`field.initialDefault() != null` → 用常量 reader，值通过 `SparkUtil.internalToSpark(field.type(), field.initialDefault())` 转换；
5. `field.isOptional()` → 用 `ParquetValueReaders.nulls()`；
6. **新增**：required 字段既无 reader 也无默认值 → 抛 `IllegalArgumentException("Missing required field: " + field.name())`。

这是默认值支持的核心：让 v3 表中"新增了带默认值字段"的 schema 演进场景在读取旧文件时能正确填充默认值。同时把 `UnboxedReader` 改为 `UnboxedReader<>`（菱形语法）。

#### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java

`VectorizedReaderBuilder` 构造函数调用新增一个 `SparkUtil::internalToSpark` 参数（父类 `VectorizedReaderBuilder` 已支持自定义转换函数）。这样向量化读取路径在填充常量/默认值时也走统一的转换逻辑。

#### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/SparkPlannedAvroReader.java（新文件）

新的 Avro 读取器，192 行。核心结构：
- `create(Schema)` / `create(Schema, Map<Integer, ?>)` 静态工厂；
- `setSchema(Schema fileSchema)` 时通过 `AvroWithPartnerVisitor.visit(expectedType, fileSchema, new ReadBuilder(idToConstant), FieldIDAccessors.get())` 构建 `ValueReader<InternalRow>`；
- `ReadBuilder.record` 中调用 `ValueReaders.buildReadPlan(expected, record, fieldReaders, idToConstant, SparkUtil::internalToSpark)` 生成有序读计划，再用 `SparkValueReaders.struct(readPlan, expected.fields().size())` 包装成 `PlannedStructReader`；
- `ReadBuilder.primitive` 按Avro logical type / primitive type 返回对应 `ValueReader`（date、timestamp-millis/micros、decimal、uuid、int→long 提升等）。

相比旧 `SparkAvroReader`，它通过 readPlan 只读文件中实际存在的字段，跳过不需要的列，性能更好且天然支持默认值/常量填充。

#### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/SparkValueReaders.java

新增 `PlannedStructReader` 内部类，继承 `ValueReaders.PlannedStructReader<InternalRow>`：
- `reuseOrCreate` 复用传入的 `GenericInternalRow`（当字段数匹配时）或新建；
- `set` 在 value 非 null 时 `struct.update(pos, value)`，null 时 `struct.setNullAt(pos)`；
- `get` 始终返回 null（因为 readPlan 中已处理）。

并提供 `struct(List<Pair<Integer, ValueReader<?>>>, int)` 工厂方法。

#### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/SparkAvroReader.java

类与构造函数全部加上 `@Deprecated` 注解和 javadoc 说明"will be removed in 1.8.0; use SparkPlannedAvroReader instead"。保留实现以兼容外部调用，但 Iceberg 内部读取路径已切换到新 reader。

#### spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/BaseRowReader.java

Avro 读取路径从 `.createReaderFunc(readSchema -> new SparkAvroReader(projection, readSchema, idToConstant))` 改为 `.createResolvingReader(schema -> SparkPlannedAvroReader.create(schema, idToConstant))`。`createResolvingReader` 是 Iceberg Avro API 提供的"按需解析"读取器入口，它会在 setSchema 时根据文件 schema 与预期 schema 构建读计划，正好匹配 `SparkPlannedAvroReader` 的设计。

#### spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/data/AvroDataTest.java

大幅扩展测试基类：
- 引入 JUnit 5（`@TempDir`、`@ParameterizedTest`、`Assumptions`）；
- 新增 `supportsDefaultValues()`（默认 false）、`supportsNestedTypes()`（默认 true）钩子，让子类按能力开启/关闭相关测试；
- 新增 `testDefaultValues`、`testMissingRequiredWithoutDefault`、`testDefaultValuesWithDefaults`、`testOptionalFieldWithInitialDefault` 等大量默认值场景测试，覆盖"读旧文件遇到新字段（带/不带默认值）"、"required 字段缺失默认值应抛异常"等核心场景；
- 原 `writeAndValidate(Schema)` 之外新增 `writeAndValidate(Schema writeSchema, Schema expectedSchema)` 重载，支持"用 writeSchema 写、用 expectedSchema 读"的 schema 演进测试。

#### spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java（新文件）

把 `TestAvroScan`/`TestParquetScan` 等的公共骨架抽到基类：管理 SparkSession 生命周期、`writeAndValidate` 通用流程（建表、写数据、必要时通过 `TableOperations` 直接升级 format-version 到 3 并切换 schema、读回校验）。子类只需实现 `writeRecords(Table, List<Record>)`。`supportsDefaultValues()` 返回 true，让所有 scan 子类都跑默认值测试。

#### spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/DataFrameWriteTestBase.java（新文件）

继承 `ScanTestBase`，但 `supportsDefaultValues()` 返回 false（因为这是写路径测试，默认值由读取侧填充，写侧不测）。`writeRecords` 通过 `SparkPlannedAvroReader` 把 GenericData.Record 转成 InternalRow RDD，再用 `spark.internalCreateDataFrame` 构造 Dataset 后通过 `df.write().format("iceberg")` 写入。额外测试 `testAlternateLocation` 验证 `WRITE_DATA_LOCATION` 属性。

#### spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestAvroScan.java、TestParquetScan.java、TestParquetVectorizedScan.java

改为继承 `ScanTestBase`，删除重复的 SparkSession 管理、`writeAndValidate` 实现等样板代码，只保留各自的 `writeRecords` 实现（按对应文件格式写数据）。`TestParquetVectorizedScan` 新增用于向量化路径的扫描测试。

#### spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestAvroDataFrameWrite.java、TestORCDataFrameWrite.java、TestParquetDataFrameWrite.java（新文件）

三个轻量子类，分别继承 `DataFrameWriteTestBase`，仅在 `configureTable` 中设置 `DEFAULT_FILE_FORMAT` 为对应格式，让同一套写测试在三种格式下各跑一遍。

#### spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWrites.java（删除）

原 421 行的大文件，内容被拆分到 `DataFrameWriteTestBase` + 三个格式子类中，消除了 parquet/avro/orc 三套近乎重复的写测试代码。

#### 其他测试文件

`TestHelpers.java`、`TestSparkAvroEnums.java`、`TestSparkAvroReader.java`、`TestSparkOrcReader.java`、`TestSparkParquetReader.java`、`TestSparkRecordOrcReaderWriter.java`、`TestParquetDictionaryEncodedVectorizedReads.java`、`TestParquetDictionaryFallbackToPlainEncodingVectorizedReads.java`、`TestParquetVectorizedReads.java` 等做了配套调整：迁移到 JUnit 5、适配新的 `AvroDataTest` 钩子签名、改用 `SparkPlannedAvroReader`、补默认值断言等。

## 小结

本次回迁效果是把 Spark 3.4/3.5 已成熟的"v3 字段默认值读取支持"完整搬到 Spark 3.3，并顺带把 Avro 读取路径升级到基于 readPlan 的 `SparkPlannedAvroReader`，让旧 reader 进入弃用周期。测试侧通过抽公共基类消除了大量重复代码，并新增了针对默认值/schema 演进的系统化测试。

影响范围：仅限 Spark 3.3 模块（`spark/v3.3/spark`）。对 v2 表行为完全不变（v2 schema 不允许声明默认值，相关分支不会触发）。对 v3 表则带来了新能力——读取带默认值的新增字段时能正确填充，required 字段缺失默认值时显式报错而非静默错误。

回迁到 1.4.x 分支的注意事项：1.4.x 通常较老，可能根本没有 `spark/v3.3` 模块，或 1.4.x 时期的 spark/v3.3 还不支持 v3 表/默认值。本提交依赖 core 侧的 `ValueReaders.buildReadPlan`、`ValueReaders.PlannedStructReader`、`AvroWithPartnerVisitor`、`ParquetValueReaders.constant(Object, int)`、`VectorizedReaderBuilder` 的转换函数参数、`Types.NestedField.initialDefault()` 等 API。回迁前必须先确认 1.4.x 的 core 模块是否已经具备这些基础设施；若 core 侧尚不具备，则不能单独回迁本 Spark 3.3 提交。此外，弃用 `SparkAvroReader` 可能影响 1.4.x 时期仍依赖旧 reader 的外部代码，回迁时需评估 API 兼容性。最后，测试从 JUnit 4 迁移到 JUnit 5 的部分需要 1.4.x 的构建配置支持 JUnit 5。
