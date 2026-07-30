# 提交 1594：Spark 3.4: Backport support for default values (#11987)

## 提交信息

- **序号**：1594 / 4088
- **哈希**：d97ac3eff5a2eebd505ad5e9f2acc83982fe1e7c
- **短哈希**：d97ac3eff
- **日期**：2025-01-16（Thu Jan 16 21:27:05 2025 -0800）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Spark 3.4: Backport support for default values (#11987)
- **PR/Issue**：#11987

## 总体目的

Iceberg 在 main 分支上引入了 v3 格式规范中的字段默认值（initial default / write default）支持，允许在 schema 中为字段声明 `initialDefault`（用于旧文件中缺失字段时填充）和 `writeDefault`（写入时未提供该字段时使用）。main 分支的相关 reader / writer / 测试基础设施已经更新到支持默认值，但 Spark 3.4 维护分支尚未跟上。本提交将 main 分支上"支持默认值"的功能完整回迁到 `spark/v3.4/` 模块，使 Spark 3.4 用户也能在表 schema 演进中安全地新增带默认值的必填字段，而无需重写历史数据。

回迁需要解决三个层面的问题：

1. **常量值转换复用**：原 `BaseReader.convertConstant` 把 Iceberg 内部对象模型转成 Spark `InternalRow` 期望的对象（`Decimal`、`UTF8String`、`byte[]`、`GenericInternalRow` 等），但它是 `BaseReader` 的 protected 静态方法，无法被 Parquet / Avro / ORC reader 模块跨包复用。回迁把该方法上移到 `SparkUtil.internalToSpark`，使其成为公共工具方法，供所有 reader 在处理"字段缺失但 schema 声明了默认值"时统一调用。
2. **Parquet / Avro reader 支持"字段缺失"路径**：当文件中缺少某字段但 schema 中声明了 `initialDefault` 时，reader 需要返回默认值而不是 null。Parquet reader 的字段重排逻辑需要新增"默认值分支"；Avro reader 原本基于 `SparkAvroReader`，它使用旧的"按 expected schema 直接构造 reader"方式，难以注入默认值常量，因此回迁引入了 main 分支已存在的 `SparkPlannedAvroReader`——它使用 `AvroWithPartnerVisitor` 构建读取计划并支持 `idToConstant`，是默认值能流入 Avro 读取路径的关键。
3. **测试基础设施重构**：原 `TestDataFrameWrites`（421 行）与 `TestAvroScan` / `TestParquetScan` 之间存在大量重复逻辑，回迁顺带把共用部分抽到新的 `ScanTestBase` 与 `DataFrameWriteTestBase`，使每个文件格式只需实现 `writeRecords` 钩子。同时 `AvroDataTest` 新增了一系列默认值回归测试（`testDefaultValues`、`testNullDefaultValue`、`testNestedDefaultValue`、`testMapNestedDefaultValue`、`testMissingRequiredWithoutDefault`），由 `supportsDefaultValues()` 开关控制是否运行——旧 reader 实现可选择不开启。

## 如何达成设计目的

### 核心架构变更：将 `convertConstant` 上移为 `SparkUtil.internalToSpark`

原 `BaseReader.convertConstant` 仅服务于 partition constants（分区字段在文件中不存在，需要从 task 上下文注入），逻辑就是按 Iceberg `Type` 分支把内部对象转成 Spark 对象。回迁发现默认值也需要同样的转换逻辑（把 `field.initialDefault()` 转成 Spark 内部对象），故把该方法原样搬到 `SparkUtil`，并新增对 `UUID` 类型的处理（与 main 分支保持一致）。`BaseReader` 中的 `constantsMap` 改为方法引用 `SparkUtil::internalToSpark`，删除 `BaseReader.convertConstant`。

### Parquet reader 的默认值分支

`SparkParquetReaders` 原本在字段重排时只判断三种情况：常量（来自 partition）、`_deleted` 元数据列、其它走 `readersById` 或 `nulls()`。回迁后在"reader 不存在"分支前插入 `else if (field.initialDefault() != null)` 分支，调用 `ParquetValueReaders.constant(SparkUtil.internalToSpark(field.type(), field.initialDefault()), maxDefinitionLevelsById.getOrDefault(id, defaultMaxDefinitionLevel))`，让缺失字段返回默认值常量；并把原本兜底的 `else` 拆成"可选字段返回 nulls"和"必填字段抛 IllegalArgumentException"两条路径，避免必填字段缺失时被静默填 null。同时修了一个泛型原始类型警告（`UnboxedReader` -> `UnboxedReader<>`）。

### Avro reader 重写为 `SparkPlannedAvroReader`

新增 `SparkPlannedAvroReader`（192 行）替代 `SparkAvroReader` 在 `BaseRowReader` 中的使用。它通过 `AvroWithPartnerVisitor.visit` 基于 expected schema 与文件 schema 构建 `ValueReader` 读取计划，并传入 `idToConstant`（包含默认值常量）；在 record 节点调用 `ValueReaders.buildReadPlan(expected, record, fieldReaders, idToConstant, SparkUtil::internalToSpark)`，由 `PlannedStructReader` 按计划只读取文件中存在的字段、缺失字段从常量取值。`SparkValueReaders` 新增 `PlannedStructReader`（基于 `ValueReaders.PlannedStructReader`）和 `struct(readPlan, numFields)` 工厂方法以支撑这一路径。`SparkAvroReader` 被标记为 `@Deprecated`（将在 1.8.0 移除），但保留以兼容现有调用方。

### 向量化 Parquet reader

`VectorizedSparkParquetReaders` 的内部 `ReaderBuilder` 构造改为向父类 `VectorizedReaderBuilder` 多传一个 `SparkUtil::internalToSpark` 函数引用，让向量化路径也能用统一的常量转换逻辑（默认值常量经此函数转成 Spark 内部表示）。

### 测试基础设施重构

- 新增 `ScanTestBase`（126 行）：从原 `TestAvroScan` / `TestParquetScan` 抽出公共部分，继承 `AvroDataTest` 并实现 `writeAndValidate(Schema, Schema)`——支持"写入 schema"与"读取 schema"不一致的场景，这正是默认值测试需要的形态。它通过 `TypeUtil.reassignOrRefreshIds` + `TableMetadata.buildFrom` 在测试中模拟 schema 演进（直接走 `TableOperations` 而非表 API，以便测试不兼容更新）。`supportsDefaultValues()` 默认返回 true。
- 新增 `DataFrameWriteTestBase`（140 行）：从原 `TestDataFrameWrites` 抽出，覆盖 `testAlternateLocation` 等 write-path 测试，但 `supportsDefaultValues()` 返回 false（因为写路径不参与默认值填充）。
- 新增 `TestAvroDataFrameWrite` / `TestORCDataFrameWrite` / `TestParquetDataFrameWrite` 三个薄壳类，每个仅实现 `configureTable` 设置默认文件格式。
- 新增 `TestParquetVectorizedScan`（26 行）：向量化读取的薄壳，覆盖向量化路径下的默认值测试。
- `TestAvroScan` / `TestParquetScan` 大幅瘦身（分别删除 65 / 137 行重复代码），改为继承 `ScanTestBase` 并实现 `writeRecords`。
- 删除 `TestDataFrameWrites`（-421 行），其用例被 `DataFrameWriteTestBase` 取代。

### `AvroDataTest` 测试增强

- 迁移到 JUnit 5（`@TempDir`、`@Test` from `junit.jupiter`、`ParameterizedTest` / `MethodSource`）。
- 新增 `writeAndValidate(Schema, Schema)` 重载、`supportsDefaultValues()` 与 `supportsNestedTypes()` 钩子。
- `SUPPORTED_PRIMITIVES` 新增 `ts_without_zone` 字段（109）。
- 新增默认值相关测试：`testMissingRequiredWithoutDefault`（必填字段无默认值且文件缺失时应报错）、`testDefaultValues`、`testNullDefaultValue`、`testNestedDefaultValue`、`testMapNestedDefaultValue`，均通过 `Assumptions.assumeThat(supportsDefaultValues())` 控制是否运行。
- 多个 `testNested*` 测试加上 `Assumptions.assumeThat(supportsNestedTypes())`。

### `TestHelpers` 增强

新增 / 调整用于默认值比较的辅助方法（约 +110 / -变更行），使 `assertEqualsSafe` / `assertEqualsUnsafe` 能正确处理默认值场景下的类型与 null 比较。

### 其它测试调整

- `TestSparkAvroEnums` / `TestSparkAvroReader` / `TestSparkOrcReader` / `TestSparkParquetReader` / `TestSparkRecordOrcReaderWriter` 等改为适配新的 JUnit 5 + `SparkPlannedAvroReader` 路径，部分断言从 JUnit `Assert` 切换到 AssertJ。
- 向量化测试（`TestParquetDictionaryEncodedVectorizedReads`、`TestParquetDictionaryFallbackToPlainEncodingVectorizedReads`、`TestParquetVectorizedReads`）做小幅适配，主要为配合 `AvroDataTest` 的新签名与默认值测试开关。

## 修改详情

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkUtil.java`（+67）

新增公共静态方法 `internalToSpark(Type, Object)`，按 Iceberg `Type` 把内部对象转为 Spark `InternalRow` 兼容对象（`Decimal`、`UTF8String`、`byte[]`、`GenericInternalRow` 等），新增对 `UUID` 类型的处理。该方法是默认值常量在 Spark 路径下的统一转换入口。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java`（-62 行，约 +5）

删除原 `convertConstant`，`constantsMap` 改为引用 `SparkUtil::internalToSpark`。同时清理相关 import。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/BaseRowReader.java`（+2 / -2）

Avro 读取路径从 `new SparkAvroReader(projection, readSchema, idToConstant)` 切换到 `SparkPlannedAvroReader.create(schema, idToConstant)`，使用 `createResolvingReader` API。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkAvroReader.java`（+12）

整个类与构造方法标记 `@Deprecated`，注明将在 1.8.0 移除，请改用 `SparkPlannedAvroReader`。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkPlannedAvroReader.java`（新文件，+192）

基于 `AvroWithPartnerVisitor` 的 Avro reader，支持 `idToConstant`（默认值 / 分区常量）。内部 `ReadBuilder` 实现各 Avro 类型到 Spark `ValueReader` 的映射，并通过 `ValueReaders.buildReadPlan` + `SparkValueReaders.struct` 构造支持缺失字段的 `PlannedStructReader`。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkValueReaders.java`（+38）

新增 `struct(readPlan, numFields)` 工厂与 `PlannedStructReader` 内部类：基于 readPlan 仅读取文件中存在的字段，缺失字段保持 null（默认值常量在 buildReadPlan 阶段已注入为 `ConstantReader`）。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java`（+26 / -8）

字段重排逻辑新增 `initialDefault` 分支与"必填字段缺失抛异常"分支，原 `else` 收窄为"可选字段返回 nulls"。修泛型原始类型警告。

#### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java`（+7 / -2）

向 `VectorizedReaderBuilder` 父类构造传递 `SparkUtil::internalToSpark`，使向量化路径复用统一常量转换。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/AvroDataTest.java`（+368 / 变更）

迁移 JUnit 5，新增默认值相关测试方法、`supportsDefaultValues()` / `supportsNestedTypes()` 钩子、`writeAndValidate(Schema, Schema)` 重载，`SUPPORTED_PRIMITIVES` 增加 `ts_without_zone`。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`（+110 / 变更）

补充默认值比较所需的辅助方法。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java`（新文件，+126）

从原 scan 测试抽出公共基类，支持写入 schema 与读取 schema 不一致的测试场景，默认开启默认值测试。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/DataFrameWriteTestBase.java`（新文件，+140）

从原 `TestDataFrameWrites` 抽出 write-path 公共逻辑，关闭默认值测试。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestAvroDataFrameWrite.java`、`TestORCDataFrameWrite.java`、`TestParquetDataFrameWrite.java`（新文件，各 +33）

三种文件格式的 DataFrame 写测试薄壳，仅设置默认文件格式。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestParquetVectorizedScan.java`（新文件，+26）

向量化读取的薄壳测试，覆盖向量化路径下的默认值场景。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestAvroScan.java`（-65 / +少量）

继承 `ScanTestBase`，仅实现 `writeRecords`。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestParquetScan.java`（-137 / +少量）

同上，瘦身后继承 `ScanTestBase`。

#### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWrites.java`（删除，-421）

被 `DataFrameWriteTestBase` 取代。

#### 其余测试文件小幅调整

`TestSparkAvroEnums`、`TestSparkAvroReader`、`TestSparkOrcReader`、`TestSparkParquetReader`、`TestSparkRecordOrcReaderWriter`、`TestParquetDictionaryEncodedVectorizedReads`、`TestParquetDictionaryFallbackToPlainEncodingVectorizedReads`、`TestParquetVectorizedReads` 适配新 API 与 JUnit 5。

## 小结

- **成效**：Spark 3.4 模块完整获得默认值（initial-default）读取支持：Parquet（向量化 + 非向量化）、Avro（新 `SparkPlannedAvroReader`）、ORC 路径统一通过 `SparkUtil.internalToSpark` 注入默认值常量；测试基础设施重构后消除大量重复代码，并新增针对默认值的回归覆盖。同时弃用 `SparkAvroReader`，统一到基于读取计划的 Avro reader。
- **影响范围**：仅作用于 `spark/v3.4/` 模块。主代码变更约 +449 / -67 行（其余为测试），不涉及 Iceberg 核心 API / 表格式规范。
- **回迁到 1.4.x 的注意事项**：本提交本身就是"从 main 回迁到 Spark 3.4 模块"的回迁 PR。1.4.x 若仍发布 Spark 3.4 artifact，则该支持已包含在 1.4.x 的对应 Spark 3.4 jar 中。1.4.x 不需要再次回迁此提交，但需关注与之相关的 reader 行为变化（如 `SparkAvroReader` 已弃用、Avro reader 切换为 `SparkPlannedAvroReader`），若有下游依赖原 reader 行为需评估兼容性。
