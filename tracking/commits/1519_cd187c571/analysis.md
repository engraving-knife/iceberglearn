# 提交 1519 cd187c571 分析

## 提交信息
- 哈希：cd187c5718ba1eb0c8853d595eed82fa7230bc3d
- 日期：2024-12-20（Fri Dec 20 16:31:48 2024 -0800）
- 作者：Ryan Blue <blue@apache.org>
- 消息：Spark: Test reading default values in Spark (#11832)

## 总体目的

本提交是 Iceberg 默认值（default values）端到端支持的关键一环，紧随 #11815（向量化读取支持默认值）。前一个提交解决了"读"路径中默认值填充的问题，但暴露出更多基础缺陷，本提交集中修复：

1. **Schema 演进时默认值丢失**：`ReassignIds` 在重分配字段 ID 时只保留 id/name/type/doc，丢掉了 `initialDefault`/`writeDefault`。这意味着表 schema 演进（如 `reassignOrRefreshIds`）后默认值会消失，破坏默认值语义。
2. **SchemaParser 不序列化默认值**：Iceberg 的 schema JSON 表示此前不包含 `initial-default`/`write-default` 字段，导致 schema 持久化到 metadata.json 或在 REST catalog 传输时默认值丢失。
3. **`Types.NestedField.equals` 不考虑默认值**：两个字段即使默认值不同也被判等，导致 schema 比较与演进判断错误。
4. **Spark 测试基础设施不支持默认值场景**：原 `TestHelpers.assertEqualsSafe` 假设写 schema 与读 schema 相同（按位置取期望值），无法验证"写入无该字段、读取用默认值填充"的场景；且 AvroDataTest 的 `withSQLConf` 等样板代码重复散落。

本提交通过以下手段达成目的：用 builder API 重写 ReassignIds 保留所有字段属性；扩展 SchemaParser 序列化/反序列化默认值；修正 NestedField.equals；重构 Spark 测试为 `ScanTestBase`/`DataFrameWriteTestBase` 两个基类 + 格式子类，支持分离 write/read schema 的默认值测试。

## 如何达成设计目的

### 修改详情

#### `api/src/main/java/org/apache/iceberg/types/ReassignIds.java`（修改）
- 原代码按 `field.isRequired()` 分支调用 `required`/`optional` 工厂，只传 id/name/type/doc，**丢失 initialDefault/writeDefault**。
- 改为：`newFields.add(Types.NestedField.from(field).withId(fieldId).ofType(types.get(i)).build())`——用 `from(field)` builder 复制原字段所有属性（包括默认值、doc），再覆盖 id 和 type。
- **目的**：schema 演进/ID 重分配时保留默认值。这是默认值能跨 schema 演进存活的前提。

#### `api/src/main/java/org/apache/iceberg/types/Types.java`（修改）
- `NestedField.equals` 在原有 id/name/isOptional/doc/type 比较基础上，新增 `initialDefault` 和 `writeDefault` 的比较。
- **目的**：两个字段只有默认值不同时应判不等。修复前，schema 演进添加默认值后 `equals` 仍返回 true，导致 `Schema.sameSchema` 等判断错误，可能跳过必要的 schema 更新。

#### `api/src/test/java/org/apache/iceberg/types/TestTypeUtil.java`（修改）
- `reassignOrRefreshIds` 测试用例中，目标 schema 的字段 `c` 改为带 `initialDefault(23)`/`writeDefault(34)` 的 builder 构造，期望 schema 同样带默认值。
- **目的**：验证 ReassignIds 修复后默认值能被保留。

#### `core/src/main/java/org/apache/iceberg/SchemaParser.java`（修改）
- 新增常量 `INITIAL_DEFAULT = "initial-default"`、`WRITE_DEFAULT = "write-default"`。
- **写入路径**（`structToJson`）：若 `field.initialDefault()` 非空，用 `SingleValueParser.toJson(type, value, generator)` 写入；writeDefault 同理。按类型序列化默认值（如 date 写整数 epoch days、string 写字符串）。
- **读取路径**：新增 `defaultFromJson(defaultField, type, json)` 用 `SingleValueParser.fromJson` 解析；`structFromJson` 用新 `fieldBuilder(isRequired, name)` builder 构造字段，链式 `withId/withDoc/ofType/withInitialDefault/withWriteDefault/build()`。
- **目的**：让 schema JSON 完整往返默认值。这是 REST catalog、metadata.json 持久化的基础——没有这个，默认值只在内存有效，落盘即丢失。

#### `core/src/test/java/org/apache/iceberg/TestSchemaParser.java`（新增，126 行）
- 继承 `AvroDataTest`，`writeAndValidate` 改为 schema JSON 往返测试（`SchemaParser.fromJson(SchemaParser.toJson(schema))` 后 `asStruct` 相等）——复用 AvroDataTest 的所有 schema 测试用例做往返覆盖。
- `testSchemaId`、`testIdentifierColumns`、`testDocStrings`：验证 schema id、标识字段、doc 往返。
- **`testPrimitiveTypeDefaultValues`**（参数化）：覆盖 14 种原语类型（Boolean/Integer/Long/Float/Double/Date/Timestamptz/Timestampntz/String/UUID/Fixed/Binary/Decimal）的默认值往返——构造带默认值的 schema，序列化再反序列化，断言 `initialDefault`/`writeDefault` 相等。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseRowReader.java`（修改）
- `createReaderFunc(readSchema -> ...)` 改为 `createResolvingReader(schema -> ...)`。
- **目的**：适配 Avro 读取 API 的方法重命名（`createReaderFunc` → `createResolvingReader`），属于同步更名。新名更准确地表达"创建解析型 reader"。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/AvroDataTest.java`（修改）
- 删除 `withSQLConf` 方法和 `Action` 函数接口（共 ~40 行）——这两个在 1515 提交后已无使用，且 `withSQLConf` 在 Spark 3 中 `SQLConf.get` 在某些测试环境不可用，移除避免误用。
- `testMissingRequiredWithoutDefault` 的断言改为：用 AssertJ `Condition` 检查 throwable **或其 cause** 是 `IllegalArgumentException`，且 `hasMessageContaining`（非精确匹配）。因为不同读取路径抛异常的层级不同（有的直接抛，有的包装在 cause 里），放宽匹配使测试在各路径下都稳定。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/ParameterizedAvroDataTest.java`（删除，284 行）
- 删除整个文件。它曾是 `AvroDataTest` 的 `@TestTemplate` 版本副本（供参数化测试类继承用），与 `AvroDataTest` 大量重复。
- **目的**：消除重复。新测试基类（ScanTestBase）直接继承 AvroDataTest，不再需要 TestTemplate 版本。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`（修改）
- **`assertEqualsSafe` 重构**（与 1515 的 `assertEqualsBatch` 同思路）：按读 schema 字段名在写 schema 中查找 `writeField`：
  - `writeField != null`：`expectedValue = rec.get(writeField.pos())`（按写位置取值）。
  - `writeField == null`（写时无此字段）：`expectedValue = field.initialDefault()`（用默认值作为期望）。
- **FIXED 类型比较修复**：原代码假设 expected 一定是 `GenericData.Fixed`，但默认值来自 Iceberg 内部表示是 `ByteBuffer`。改为同时支持 `ByteBuffer` 和 `GenericData.Fixed`，统一转 `byte[]` 后比较。
- **目的**：让测试断言能验证默认值填充场景，并兼容默认值与生成数据的不同 Java 表示。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java`（新增，126 行）
- 抽象基类，`extends AvroDataTest`。管理 SparkSession 生命周期（`@BeforeAll`/`@AfterAll`）、`@TempDir`、表创建。
- **核心方法 `writeAndValidate(Schema writeSchema, Schema expectedSchema)`**：
  1. 用 `writeSchema` 创建表。
  2. 用表 schema（ID 已重分配）生成 100 条随机数据。
  3. 调用抽象 `writeRecords(table, expected)` 写入（由子类按格式实现）。
  4. 若 `expectedSchema` 与表 schema 不同，用 `TableOperations` 直接 commit 更新 schema 到 expectedSchema（绕过表 API，因为某些测试覆盖不兼容更新）。先 `reassignOrRefreshIds` 对齐 ID，`upgradeFormatVersion(3)`（默认值需 v3），`setCurrentSchema`。
  5. `spark.read().format("iceberg").load()` 读取，断言 100 行，逐行 `assertEqualsSafe(table.schema().asStruct(), expected, row)`。
- `writeAndValidate(Schema)` 委托给 `writeAndValidate(schema, schema)`。
- `supportsDefaultValues()` 返回 true——启用 AvroDataTest 中的默认值测试用例。
- **目的**：统一的 Spark 扫描测试基类，支持"写旧 schema、读新 schema（带默认值）"的默认值端到端验证。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/DataFrameWriteTestBase.java`（新增，140 行）
- `extends ScanTestBase`。测试 Spark DataFrame 写入路径。
- `supportsDefaultValues()` 返回 **false**——因为这是写路径测试，默认值是读时填充，写路径不涉及。
- `writeRecords`：用 `SparkPlannedAvroReader` 把 GenericData.Record 转 InternalRow，构 DataFrame，`df.write().format("iceberg").mode("append")` 写入。
- `testAlternateLocation`：验证 `WRITE_DATA_LOCATION` 属性覆盖数据文件存放位置。
- **目的**：统一 DataFrame 写测试基类，与 ScanTestBase 共享读取验证逻辑但关闭默认值测试。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestAvroScan.java`（修改，大幅瘦身）
- 从 `extends AvroDataTest`（自带 Spark 管理 + scan 逻辑 ~90 行）改为 `extends ScanTestBase`，只实现 `writeRecords`（用 Avro writer 写文件 + appendFile）。
- **目的**：消除与 ScanTestBase 的重复，专注 Avro 格式特定的写入逻辑。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestParquetScan.java`（修改，大幅瘦身）
- 从 `@ExtendWith(ParameterizedTestExtension)` 参数化（vectorized=true/false）改为 `extends ScanTestBase`，`vectorized()` 方法返回 false（默认非向量化）。
- `configureTable` 设置 `PARQUET_VECTORIZATION_ENABLED`。
- `writeRecords` 用 Parquet writer 写文件。
- 重写 `writeAndValidate(writeSchema, expectedSchema)`：保留原"非 string map key 跳过"的 assumeThat，然后调 `super.writeAndValidate`。
- **目的**：消除参数化样板，向量化与非向量化拆为两个类。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestParquetVectorizedScan.java`（新增，26 行）
- `extends TestParquetScan`，`vectorized()` 返回 true。
- **目的**：替代原参数化的 vectorized=true 分支。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestAvroDataFrameWrite.java`、`TestORCDataFrameWrite.java`、`TestParquetDataFrameWrite.java`（新增，各 33 行）
- 均 `extends DataFrameWriteTestBase`，`configureTable` 设置 `DEFAULT_FILE_FORMAT` 为对应格式。
- **目的**：替代原 `TestDataFrameWrites` 的参数化 format 维度，每种格式独立类，复用 DataFrameWriteTestBase 的写入+验证逻辑。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWrites.java`（删除，412 行）
- 删除。原文件用 `@ExtendWith(ParameterizedTestExtension)` 参数化 format=parquet/avro/orc，包含大量自定义测试逻辑（testFaultToleranceOnWrite、testNullableWithSparkSqlOption 等）。
- **目的**：被 DataFrameWriteTestBase + 三个格式子类替代。原文件中一些非通用的测试（如容错、nullability）未在新结构中保留——这些测试场景与默认值无关，可能已在其他地方覆盖或被有意移除。

## 小结

- **成效**：完成了默认值端到端支持的最后一块拼图——修复了 ReassignIds/SchemaParser/NestedField.equals 三处默认值丢失/误判缺陷，使默认值能在 schema 演进、JSON 持久化、schema 比较中正确保留。同时重构 Spark 测试基础设施为 ScanTestBase/DataFrameWriteTestBase 两层基类 + 格式子类，支持"写旧 schema 读新 schema"的默认值端到端验证，消除了 ParameterizedAvroDataTest 和 TestDataFrameWrites 两个重复文件（-938 行）。
- **影响范围**：api 模块（ReassignIds、Types、TestTypeUtil）、core 模块（SchemaParser、TestSchemaParser）、spark 3.5 模块（BaseRowReader + 9 个测试文件）。共 18 个文件，+644/-938 行，净减 294 行。
- **设计亮点**：（1）ScanTestBase 的 `writeAndValidate(writeSchema, expectedSchema)` 设计精巧——先写后改 schema，模拟真实的"老数据 + 新 schema 带默认值"场景；（2）用 `TableOperations` 直接 commit 绕过表 API 的兼容性检查，使测试能覆盖不兼容更新；（3）`upgradeFormatVersion(3)` 确保默认值所需的 format v3。
- **回迁到 1.4.x 的注意事项**：本提交含三处 bug 修复（ReassignIds 丢默认值、SchemaParser 不序列化默认值、NestedField.equals 不考虑默认值），若 1.4.x 已支持默认值（format v3）则这些修复**应回迁**，否则 1.4.x 的默认值在 schema 演进或持久化后会丢失。Spark 测试基础设施重构属于测试代码，回迁收益低、风险也低，可不回迁。回迁时需注意 `createResolvingReader` 重命名是否与 1.4.x 的 Avro API 一致。

__tr_native_ec=$?; pwd -P >| '/var/folders/j4/8_ygb9zx7ll_gb4jlr9jd_vw0000gn/T/trae-agent-toolhost-501/jobs/job-fbab989f0c6c48be9147b2066cb701be/cwd.txt'; exit "$__tr_native_ec"