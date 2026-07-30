# 提交 1515 70336679e 分析

## 提交信息
- 哈希：70336679e12d243e3ff0a1962bb21337166e77ff
- 日期：2024-12-19（Thu Dec 19 11:36:49 2024 -0800）
- 作者：Ryan Blue <blue@apache.org>
- 消息：Spark 3.5: Support default values in vectorized reads (#11815)

## 总体目的

Iceberg 表 schema 支持给字段定义默认值（`initialDefault`/`writeDefault`），用于：当数据文件中缺少某字段（例如 schema 演进后新增的列，旧数据文件没有该列）时，读取时应该用默认值填充而非 null。这保证了 schema 演进的向前兼容——用户给老表加一个带默认值的列后，查询老数据文件能自动得到默认值而非 null。

在非向量化（行式）读取路径中，默认值填充早已支持。但 Spark 3.5 的向量化（列式）Parquet 读取路径此前未支持默认值：当 `VectorizedReaderBuilder` 在 expectedSchema 与 parquetSchema 之间找不到匹配 reader 时，旧逻辑直接走 `VectorizedArrowReader.nulls()`（即所有行都返回 null）。这意味着即使字段定义了默认值，向量化读取也会返回 null，行为与行式读取不一致，且违反 Iceberg 默认值语义。

本提交在向量化读取路径中补齐默认值支持。核心改动：在 `VectorizedReaderBuilder` 中，当某字段在数据文件中缺失但有 `initialDefault` 时，使用 `ConstantVectorReader` 返回该默认值常量；并且为了避免默认值的 Iceberg 内部表示与 Spark 内部表示不一致（例如日期、时间戳的表示方式不同），引入一个可注入的 `convert` 函数（`BiFunction<Type, Object, Object>`），由调用方决定如何把 Iceberg 内部默认值转换为对应引擎的内部表示。Spark 实现通过 `SparkUtil::internalToSpark` 注入此转换。

同时提交顺带修复了测试基础设施：让 `AvroDataTest` 引入 `supportsNestedTypes()` 钩子，把"嵌套类型测试在向量化路径上不适用"的硬编码 `@Disabled` 改为基于假设的跳过；把 `TimestampType.withoutZone()` 测试合并到 `SUPPORTED_PRIMITIVES` 中，并修复 `TestHelpers` 在比较 Spark `TimestampNTZType` 时的兼容性问题（`ColumnarRow.get` 不支持 TimestampNTZType，借由它与 TimestampType 表示一致来绕过）。`TestParquetVectorizedReads` 也扩展为支持分离的 write schema 与 expected schema，使默认值测试可以在"写入不含字段、读取含默认值字段"的场景下进行。

## 如何达成设计目的

### 修改详情

#### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedReaderBuilder.java`

这是核心改动，包含三部分：

1. **新增 `convert` 字段与构造函数重载**
   - 新增字段：`private final BiFunction<org.apache.iceberg.types.Type, Object, Object> convert;`
   - 原公共构造函数保留兼容性，委托给新的 protected 构造函数，传入 `(type, value) -> value`（恒等转换，即默认不转换）。
   - 新增 protected 构造函数接受额外 `convert` 参数。
   - **目的**：让子类（如 Spark 实现）可以注入引擎特定的类型转换器，把 Iceberg 内部默认值（如 `LocalDate`、`Instant`）转换为引擎内部表示（如 Spark 的 `int` epoch days、`long` micros）。

2. **重构缺失字段的处理逻辑**（在 `vectorizedReader` 方法中）
   - 原逻辑：字段在 `idToConstant` 中 → ConstantVectorReader；否则是 ROW_POSITION/DELETE → 特殊 reader；否则 reader 不为空 → reader；**否则 → nulls reader**（不论字段是 optional 还是 required，也不论是否有 default）。
   - 新逻辑：在 `idToConstant` 命中时改为调用新的 `constantReader(field, idToConstant.get(id))`（包装方法）；当 reader 为空时，先判断 `field.initialDefault() != null` → 用 `constantReader(field, convert.apply(field.type(), field.initialDefault()))` 返回转换后的默认值常量；否则若 `field.isOptional()` → nulls reader；**否则抛 `IllegalArgumentException("Missing required field: ...")`**。
   - **工作逻辑**：`initialDefault()` 非 null 意味着 schema 演进时为该字段定义了默认值，此时即使数据文件缺失该列也用默认值填充。`convert.apply` 把 Iceberg 默认值转换为引擎内部表示后再交给 `ConstantVectorReader`。对于 required 字段，如果既无 reader 也无默认值，则属于数据不一致（必填字段在数据文件中缺失），抛异常比静默返回 null 更安全。

3. **新增 `constantReader` 包装方法**
   - `private <T> ConstantVectorReader<T> constantReader(Types.NestedField field, T constant)`，简单 `return new ConstantVectorReader<>(field, constant);`
   - **目的**：让 `idToConstant` 路径和 `initialDefault` 路径都走统一入口，便于维护与类型推断。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java`

- Builder 子类的构造函数中，调用父类（`VectorizedReaderBuilder`）新构造函数，传入 `SparkUtil::internalToSpark` 作为 `convert` 参数。
- 新增 import：`org.apache.iceberg.spark.SparkUtil`。
- **目的**：Spark 向量化读取器使用 `SparkUtil.internalToSpark` 把 Iceberg 内部默认值转换为 Spark 内部表示。例如 Iceberg 用 `LocalDate` 表示日期，Spark 用 `int` 表示 epoch days；`internalToSpark` 完成此类转换，确保 `ConstantVectorReader` 返回的常量能被 Spark 直接消费。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/AvroDataTest.java`

测试基类的重构，主要为了支持默认值与嵌套类型测试的灵活化：

1. **新增 `supportsNestedTypes()` 钩子**，默认返回 `true`。
2. **在 `SUPPORTED_PRIMITIVES` 中新增 `ts_without_zone` 字段**（field id 109，`TimestampType.withoutZone()`）。
   - 把原来独立的 `testTimestampWithoutZone` 测试合并到嵌套 struct 测试中，覆盖 NTZ 类型。
3. **删除独立的 `testTimestampWithoutZone` 测试方法**。
4. **为所有嵌套类型相关测试加上 `Assumptions.assumeThat(supportsNestedTypes()).isTrue()`**：`testNestedStruct`、`testArray`、`testArrayOfStructs`、`testMap`、`testNumericMapKey`、`testComplexMapKey`、`testMapOfStructs`、`testMixedTypes`、`testNestedDefaultValue`、`testMapNestedDefaultValue`、`testListNestedDefaultValue`。
   - **目的**：用 JUnit Assumptions 而不是 `@Disabled` 跳过测试。这样新增 `supportsNestedTypes()` 钩子后，向量化测试子类可以声明 `supportsNestedTypes() == false`，让这些嵌套类型测试自动跳过（绿色 skipped），而不是显示为 disabled（灰色，容易被误以为长期未跑）。同时也让默认值的嵌套测试只在支持嵌套类型的实现中执行。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java`

1. **`assertEqualsBatch` 方法重构**：支持 expected schema 与实际读取 schema 不一致的场景（默认值测试的核心）。
   - 原逻辑：按位置一一对应 `rec.get(i)` 与 `row.get(i, convert(fieldType))`。
   - 新逻辑：遍历读 schema 的字段（`readPos`），通过字段名在写 schema 中查找对应字段（`writeField = rec.getSchema().getField(field.name())`）：
     - 若 `writeField != null`：`expectedValue = rec.get(writePos)`（按写入位置的值）。
     - **若 `writeField == null`（写入时不存在该字段）**：`expectedValue = field.initialDefault()`（用默认值作为期望值）。
   - **目的**：使测试断言能正确验证默认值填充——写入 schema 没有的字段，读取时应该等于该字段的 `initialDefault`，而不是简单按位置取期望记录的对应列。

2. **`assertEquals` 方法新增 `TimestampNTZType` 兼容**：
   - 当类型是 `TimestampNTZType` 时，转换为 `TimestampType$.MODULE$` 再比较/读取。
   - 注释说明：`ColumnarRow.get` 不支持 `TimestampNTZType`，但其表示与 `TimestampType` 相同，故借用后者来验证。
   - **目的**：修复 NTZ 字段在向量化读取测试中的断言失败——Spark 的 `ColumnarRow.get` API 暂不支持 NTZ，绕过此限制让测试能跑通。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetVectorizedReads.java`

1. **新增 `writeAndValidate(Schema writeSchema, Schema expectedSchema)` 重载**，分离写入与读取的 schema——这是默认值测试的关键：写入用旧 schema（不含新字段），读取用新 schema（含默认值字段），验证读出的默认值正确。
2. **`writeAndValidate(Schema)` 改为委托给 `writeAndValidate(schema, schema)`**。
3. **私有 `writeAndValidate` 方法签名扩展为接受 `writeSchema` 和 `expectedSchema` 两个参数**：写入数据用 `writeSchema`，读取验证用 `expectedSchema`。把 seed 默认值从 `0L` 改为 `29714278L`（更新测试随机种子）。
4. **新增 `supportsDefaultValues()` override 返回 `true`**（启用默认值测试）。
5. **新增 `supportsNestedTypes()` override 返回 `false`**（向量化路径不支持嵌套类型测试）。
6. **删除所有原 `@Disabled` 的嵌套类型测试 override**（`testArray`、`testArrayOfStructs`、`testMap`、`testNumericMapKey`、`testComplexMapKey`、`testMapOfStructs`、`testMixedTypes`），因为现在由 `AvroDataTest` 中的 `Assumptions.assumeThat(supportsNestedTypes())` 自动跳过。
   - **目的**：清理重复的 disabled 样板代码，统一用 `supportsNestedTypes()` 钩子控制跳过。
7. **`testVectorizedReadsWithReallocatedArrowBuffers`**：把 schema 提取为局部变量，然后调用新的 `writeAndValidate(schema, schema, ...)` 重载。

## 小结

- **成效**：在 Spark 3.5 的向量化 Parquet 读取路径中补齐了 Iceberg 默认值支持，使 schema 演进新增的带默认值字段在向量化读取时能正确返回默认值（与行式读取一致），修复了行为不一致问题。同时通过可注入的 `convert` 函数保持了 `VectorizedReaderBuilder` 的引擎无关性，Spark 通过 `SparkUtil::internalToSpark` 注入类型转换。测试基础设施也做了清理和扩展，让默认值与嵌套类型测试更灵活、更易读。
- **影响范围**：核心向量化读取构建器（`VectorizedReaderBuilder`，arrow 模块）+ Spark 3.5 向量化读取器（`VectorizedSparkParquetReaders`）+ 3 个测试文件。共 5 个文件，+125/-64 行。属于功能补齐 + 测试增强，不改变现有 API（构造函数保持向后兼容）。
- **设计亮点**：用 `BiFunction` 注入类型转换器，避免在 arrow 通用模块中耦合 Spark 类型系统；同时保持原构造函数（默认恒等转换）使其他引擎（Flink 等）不受影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 通常不引入新 feature，但若 1.4.x 的 Spark 3.5 向量化读取存在同样的默认值缺失问题且影响用户查询结果（默认值变 null），则可作为 bugfix **回迁**。回迁需同时带回 `VectorizedReaderBuilder` 的构造函数扩展、`VectorizedSparkParquetReaders` 的 convert 注入以及相关测试。需评估 1.4.x 的 `VectorizedReaderBuilder` 是否与 main 一致——若已分叉，回迁需谨慎调整。

__tr_native_ec=$?; pwd -P >| '/var/folders/j4/8_ygb9zx7ll_gb4jlr9jd_vw0000gn/T/trae-agent-toolhost-501/jobs/job-6c2d1bc1592d44cdb86ebe7fecf9f73b/cwd.txt'; exit "$__tr_native_ec"