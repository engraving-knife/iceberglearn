# 提交 1664：Spark 3.4: Support Comet Parquet readers (#9841)

## 提交信息

- **序号**：1664 / 4088
- **哈希**：40334f5f72d1d22ec3bb14fd029d90a5258d75f2
- **短哈希**：40334f5f7
- **日期**：2025-01-31（Fri Jan 31 15:31:56 2025 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.4: Support Comet Parquet readers (#9841)
- **PR/Issue**：#9841

## 总体目的

Apache DataFusion Comet 是一个基于 Apache DataFusion（Rust 实现的查询引擎）的 Spark 加速器，通过 native 执行提升 Spark 的向量化读取与计算性能。Comet 的 Parquet reader 在 JVM 中完成 I/O 与解压，但解码放到 native 侧，从而获得比纯 JVM 向量化读取更高的吞吐；此外 Comet 还能把 Spark 的物理计划转为 native 物理计划执行。

在此之前，Iceberg 的 Spark 3.4 集成只支持内置的 Iceberg 向量化 Parquet reader（基于 Arrow）。本提交为 Spark 3.4 引入对 Comet Parquet reader 的可选支持：用户通过新的 SQL 配置项 `spark.sql.iceberg.parquet.reader-type` 选择 `iceberg`（默认，行为不变）或 `comet`（启用 Comet native reader）。选择 `comet` 时，Iceberg 会构建一组 Comet 风格的向量化 reader，把 Parquet 列读取委托给 Comet 的 native `AbstractColumnReader`，同时保留 Iceberg 自身的删除过滤（equality delete / position delete）、元数据列（`_pos`、`_deleted`）、常量列等能力。

## 如何达成设计目的

整体设计分为三层：

1. **配置层**：新增 `ParquetReaderType` 枚举（`ICEBERG`/`COMET`）与 SQL 属性 `spark.sql.iceberg.parquet.reader-type`（默认 `ICEBERG`），通过 `SparkReadConf.parquetReaderType()` 暴露。为把"batch size + reader type"一起传递到 reader 工厂，新增不可变配置对象 `ParquetBatchReadConf`（含 `batchSize` 与 `readerType`）和 `OrcBatchReadConf`（含 `batchSize`），用 Immutables 生成。
2. **调度层**：重构 `SparkBatch`、`SparkColumnarReaderFactory`、`BaseBatchReader`、`BatchDataReader`，把原先传递的 `int batchSize` 替换为 `ParquetBatchReadConf`/`OrcBatchReadConf`。`SparkBatch.createReaderFactory` 新增 `useCometBatchReads()` 判定（向量化开启 + reader 类型为 COMET + 投影列全是除 UUID 外的原语类型 + 任务全是 Parquet 的 FileScanTask），命中时构造带 `COMET` 类型的 conf。
3. **读取层**：新增一整套 Comet 风格的向量化 reader：
   - `CometVectorizedReaderBuilder`：仿照现有 `VectorizedReaderBuilder`，按 Iceberg schema 遍历 Parquet schema，为每个字段构造对应的 `CometColumnReader`，并处理常量列、`_pos`、`_deleted`、缺失字段默认值等。
   - `CometColumnReader`：包装 Comet native 的 `AbstractColumnReader`，负责 native reader 的初始化、reset、page reader 设置、close。
   - `CometColumnarBatchReader`：组装多个 `CometColumnReader` 为 Spark `ColumnarBatch`，委托 Comet 的 `BatchReader.nextBatch` 批量读取，并集成 Iceberg 的删除过滤（rowIdMapping / isDeleted）。
   - `CometConstantColumnReader`/`CometPositionColumnReader`/`CometDeleteColumnReader`：分别处理常量列、`_pos` 元数据列、`_deleted` 元数据列，通过 Comet native API（`Native.setPosition`/`Native.setIsDeleted`）把值设到 native 侧。
   - `VectorizedSparkParquetReaders.buildCometReader`：入口方法，用 `CometVectorizedReaderBuilder` 构建并返回 `CometColumnarBatchReader`。

同时为了让删除过滤的 `ColumnVectorWithFilter` 能包装 Comet 的 `ColumnVector`（而非只能包装 `IcebergArrowColumnVector`），把它从继承 `IcebergArrowColumnVector` 改为直接继承 Spark 的 `ColumnVector`，持有任意 `ColumnVector delegate`，并补全 `getByte/getShort/getMap/getChild/hasNull/numNulls` 等方法。相应地移除 `IcebergArrowColumnVector.vector()` 访问器，并调整 `ColumnarBatchReader` 中使用 `ColumnVectorWithFilter` 的方式。

构建方面，`spark/v3.4/build.gradle` 新增 Comet 依赖 `org.apache.datafusion:comet-spark-spark3.4_2.12:0.3.0`（compileOnly + testImplementation）与 Immutables annotation processor；checkstyle 对 `CometColumnReader` 放开 `IllegalImport`（因其需 import `org.apache.comet.*`）。

## 修改详情

### `.baseline/checkstyle/checkstyle-suppressions.xml`（修改，3 行）

**修改目的**：放开 `CometColumnReader` 的 `IllegalImport` 检查。

**工作逻辑**：新增一条 suppress 规则，匹配类 `org.apache.iceberg.spark.data.vectorized.CometColumnReader`，豁免 `IllegalImport` check。因为该类必须 import `org.apache.comet.parquet.*` 等 Comet 包，而 baseline 默认禁止部分 import 模式。

### `spark/v3.4/build.gradle`（修改，5 行）

**修改目的**：引入 Comet 依赖与 Immutables 注解处理器。

**工作逻辑**：
- 在 spark 主项目 dependencies 中新增 `annotationProcessor libs.immutables.value` 与 `compileOnly libs.immutables.value`（用于生成 `ImmutableParquetBatchReadConf`/`ImmutableOrcBatchReadConf`）；
- 新增 `compileOnly "org.apache.datafusion:comet-spark-spark${sparkMajorVersion}_${scalaVersion}:0.3.0"`（Comet 作为编译期依赖，运行期由用户自行引入）；
- 在 extensions 测试依赖中新增 `testImplementation "...comet-spark-spark...:0.3.0"`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/ParquetReaderType.java`（新增，47 行）

**修改目的**：枚举 Parquet reader 类型。

**工作逻辑**：定义枚举 `ICEBERG`（内置 Arrow reader）与 `COMET`（DataFusion Comet native reader）。`fromString` 把字符串（不区分大小写）转为枚举，非法值抛 `IllegalArgumentException`。`COMET` 的 Javadoc 说明：I/O 与解压在 JVM，解码在 native；并留 TODO 表示后续要在 `SparkScan` 实现 `SupportsComet` 以把物理计划转 native。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java`（修改，4 行）

**修改目的**：定义 reader-type SQL 属性。

**工作逻辑**：新增 `PARQUET_READER_TYPE = "spark.sql.iceberg.parquet.reader-type"` 与默认值 `PARQUET_READER_TYPE_DEFAULT = ParquetReaderType.ICEBERG`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`（修改，8 行）

**修改目的**：暴露 reader-type 配置读取。

**工作逻辑**：新增 `parquetReaderType()` 方法，用 `confParser.enumConf(ParquetReaderType::fromString)` 解析，取 session 级配置 `SparkSQLProperties.PARQUET_READER_TYPE`，默认 `ICEBERG`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/ParquetBatchReadConf.java`（新增，29 行）

**修改目的**：不可变配置对象，承载 parquet 批读所需的 batch size 与 reader type。

**工作逻辑**：`@Value.Immutable` 接口，`int batchSize()` + `ParquetReaderType readerType()`，`Serializable`。Immutables 生成 `ImmutableParquetBatchReadConf`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/OrcBatchReadConf.java`（新增，27 行）

**修改目的**：不可变配置对象，承载 ORC 批读的 batch size。

**工作逻辑**：`@Value.Immutable` 接口，仅 `int batchSize()`，`Serializable`。引入它是为了与 parquet conf 对齐接口风格，让 reader 工厂统一接收 conf 对象。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java`（修改，39 行）

**修改目的**：根据 reader 类型选择 reader 工厂，并构造 conf 对象。

**工作逻辑**：
- `createReaderFactory` 改为三分支：`useCometBatchReads()` → 构造 `COMET` 类型的 `ParquetBatchReadConf` 传给工厂；`useParquetBatchReads()` → 构造 `ICEBERG` 类型的 conf；`useOrcBatchReads()` → 构造 `OrcBatchReadConf`；否则用行读工厂。
- 新增 `parquetBatchReadConf(readerType)` 与 `orcBatchReadConf()` 私有方法构造 conf。
- 新增 `useCometBatchReads()`：要求 parquet 向量化开启、reader 类型为 COMET、投影列全是原语类型且非 UUID、任务全是 Parquet FileScanTask（复用 `supportsParquetBatchReads`）。
- 新增 `supportsCometBatchReads(field)`：`field.type().isPrimitiveType() && typeId != UUID`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkColumnarReaderFactory.java`（修改，19 行）

**修改目的**：用 conf 对象替代 int batchSize。

**工作逻辑**：字段从 `int batchSize` 改为 `ParquetBatchReadConf parquetConf` + `OrcBatchReadConf orcConf`；新增两个构造器分别接收 parquet conf 或 orc conf；`createReader` 把两个 conf 传给 `BatchDataReader`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java`（修改，28 行）

**修改目的**：用 conf 对象替代 batchSize，并在 parquet 路径按 reader 类型分发。

**工作逻辑**：字段从 `int batchSize` 改为 `ParquetBatchReadConf parquetConf` + `OrcBatchReadConf orcConf`；构造器相应调整。`newBatchIterable` 的 parquet 分支中，`createBatchedReaderFunc` 改为 lambda：若 `parquetConf.readerType() == COMET` 调 `VectorizedSparkParquetReaders.buildCometReader`，否则调原 `buildReader`；`recordsPerBatch` 用 `parquetConf.batchSize()`。ORC 分支用 `orcConf.batchSize()`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/BatchDataReader.java`（修改，15 行）

**修改目的**：透传 conf 对象到父类。

**工作逻辑**：两个构造器的 `int size` 参数替换为 `ParquetBatchReadConf parquetConf` + `OrcBatchReadConf orcConf`，转调父类。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java`（修改，17 行）

**修改目的**：新增 Comet reader 入口方法。

**工作逻辑**：新增静态方法 `buildCometReader(expectedSchema, fileSchema, idToConstant, deleteFilter)`，用 `TypeWithSchemaVisitor.visit` + `CometVectorizedReaderBuilder` 构建，`readerFactory` 为 `readers -> new CometColumnarBatchReader(readers, expectedSchema)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometVectorizedReaderBuilder.java`（新增，147 行）

**修改目的**：按 Iceberg schema 遍历 Parquet schema，为每列构造 Comet reader。

**工作逻辑**：继承 `TypeWithSchemaVisitor<VectorizedReader<?>>`。
- `message(...)`：顶层入口。按 Parquet field id 建立 `readersById`，再按 Iceberg 字段顺序重排：常量列→`CometConstantColumnReader`；`_pos`→`CometPositionColumnReader`；`_deleted`→`CometDeleteColumnReader`；有 reader→直接用；有 initialDefault→常量 reader；optional→null 常量 reader；否则抛缺失字段异常。最后用 `readerFactory` 组装 `CometColumnarBatchReader`，若有 deleteFilter 则 `setDeleteFilter`。
- `struct(...)`：嵌套 struct 暂不支持向量化，expected 非 null 时抛异常。
- `primitive(...)`：取 parquet field id 与 `ColumnDescriptor`，重复级别 >0（嵌套）返回 null，否则返回 `new CometColumnReader(sparkType, desc)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometColumnReader.java`（新增，150 行）

**修改目的**：包装 Comet native `AbstractColumnReader` 的列 reader 基类。

**工作逻辑**：实现 `VectorizedReader<ColumnVector>`。持有 `ColumnDescriptor descriptor`、`DataType sparkType`、可变的 `AbstractColumnReader delegate`、`CometSchemaImporter importer`、`batchSize`（默认 8192）。
- 两个构造器：`(sparkType, descriptor)` 与 `(field)`（用 `TypeUtil.convertToParquet` 从 Spark StructField 推导 descriptor）。
- `reset()`：关闭旧 importer/delegate，新建 `CometSchemaImporter(new RootAllocator())`，用 `Utils.getColumnReader` 创建新 delegate，标记 initialized。每个 row group 调用一次以重置字典编码。
- `setPageReader(pageReader)`：要求先 reset，再委托 `((ColumnReader)delegate).setPageReader`。
- `read(...)`/`setRowGroupInfo(...)`：抛 `UnsupportedOperationException`（实际读取由 `CometColumnarBatchReader` 通过 `BatchReader.nextBatch` 驱动）。
- `close()`：关闭 importer 与 delegate。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometColumnarBatchReader.java`（新增，203 行）

**修改目的**：组装多列为 Spark `ColumnarBatch`，驱动批量读取并集成删除过滤。

**工作逻辑**：实现 `VectorizedReader<ColumnarBatch>`。持有 `CometColumnReader[] readers`、`boolean hasIsDeletedColumn`、Comet `BatchReader delegate`（内含 `AbstractColumnReader[]`）、`DeleteFilter deletes`、`long rowStartPosInBatch`。
- 构造：把 readers cast 为 `CometColumnReader[]`，检测是否含 `CometDeleteColumnReader`；创建 `BatchReader` 并 `setSparkSchema`。
- `setRowGroupInfo`：对非常量/非 position/非 delete 的 reader 调 `reset()` + `setPageReader`；把各 reader 的 delegate 填入 `delegate.getColumnReaders()`；记录 `rowStartPosInBatch`（取 `PageReadStore.getRowIndexOffset`，用于 position delete 行号对齐）。
- `read(reuse, numRowsToRead)`：用内部类 `ColumnBatchLoader` 加载数据。
- `ColumnBatchLoader.loadDataToColumnBatch`：`readDataToColumnVectors`（`delegate.nextBatch(batchSize)` 后取各 reader 的 `delegate().currentBatch()`）；若含 `_deleted` 列，先 `buildIsDeleted` 再 `readDeletedColumn`（因为 isDeleted 值此时才可用，需显式调 `readBatch`）；否则 `buildRowIdMapping`，命中时用 `ColumnVectorWithFilter` 包装；最后处理 equality delete 的额外列移除，设行数返回。
- `setBatchSize`/`close`：转发给所有 reader。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometConstantColumnReader.java`（新增，65 行）

**修改目的**：处理常量列（分区常量、默认值、optional 的 null）。

**工作逻辑**：继承 `CometColumnReader`。构造时用 Comet `ConstantColumnReader` 作 delegate，调用 `convertToSparkValue` 把 Iceberg 值转为 Spark 内部类型（String→UTF8String、BigDecimal→Decimal、ByteBuffer→byte[]）。`setBatchSize` 转发并标记 initialized。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometDeleteColumnReader.java`（新增，74 行）

**修改目的**：处理 `_deleted` 元数据列。

**工作逻辑**：继承 `CometColumnReader`。内部 `DeleteColumnReader extends MetadataColumnReader`，持 `boolean[] isDeleted`。`readBatch(total)` 先 `Native.resetBatch`，再 `Native.setIsDeleted(nativeHandle, isDeleted)` 把删除标记传到 native 侧，再 `super.readBatch`。提供两个构造：从 field 构造（占位）与从 `isDeleted` 数组构造（实际使用）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometPositionColumnReader.java`（新增，58 行）

**修改目的**：处理 `_pos`（行位置）元数据列。

**工作逻辑**：继承 `CometColumnReader`。内部 `PositionColumnReader extends MetadataColumnReader`，持 `long position`。`readBatch(total)` 先 `Native.resetBatch`，再 `Native.setPosition(nativeHandle, position, total)`，`position += total`，再 `super.readBatch`。每批行号递增。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnVectorWithFilter.java`（修改，105 行变化）

**修改目的**：让它能包装任意 `ColumnVector`（包括 Comet 的），而非只能包装 `IcebergArrowColumnVector`。

**工作逻辑**：
- 父类从 `IcebergArrowColumnVector` 改为直接 `extends ColumnVector`；
- 字段从 `VectorHolder holder` + 继承的 accessor 改为 `ColumnVector delegate` + `int[] rowIdMapping`；
- 构造器 `ColumnVectorWithFilter(ColumnVector delegate, int[] rowIdMapping)`，调 `super(delegate.dataType())`；
- 所有取值方法（`isNullAt/getBoolean/getByte/getShort/getInt/getLong/getFloat/getDouble/getArray/getMap/getDecimal/getUTF8String/getBinary`）改为 `delegate.xxx(rowIdMapping[rowId])`，去掉原先的 null 检查（委托给 delegate 处理）；
- 新增 `hasNull`/`numNulls`（numNulls 故意返回 delegate 的值，注释说明精确计算代价高，高估可接受）；
- 新增 `getChild(ordinal)`：对 StructType 懒初始化子 vector 数组（递归包装 `delegate.getChild` + 同一 rowIdMapping），其他类型抛异常；
- `close` 转发给 delegate。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java`（修改，39 行变化）

**修改目的**：适配新的 `ColumnVectorWithFilter` 构造方式，并抽取辅助方法。

**工作逻辑**：
- `loadDataToColumnBatch` 中，使用 `ColumnVectorWithFilter(vectors[i], rowIdMapping)` 直接包装任意 vector（原先要先 cast 回 `IcebergArrowColumnVector` 再取 `.vector()`）；
- 把 `buildIsDeleted` 与 `buildRowIdMapping` 抽为 `ColumnBatchLoader` 的私有方法；
- 变量重命名 `arrowColumnVectors` → `vectors`，循环用 for-each。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/IcebergArrowColumnVector.java`（修改，删除 6 行）

**修改目的**：移除不再需要的 `vector()` 访问器。

**工作逻辑**：删除 `private final VectorHolder holder` 字段与 `public VectorHolder vector()` 方法。因为 `ColumnarBatchReader` 不再需要取 holder 来构造 `ColumnVectorWithFilter`。

## 小结

- **成效**：为 Iceberg Spark 3.4 集成引入可选的 Comet Parquet native reader，用户通过 `spark.sql.iceberg.parquet.reader-type=comet` 启用，可在不改变 Iceberg 删除过滤、元数据列语义的前提下，借助 Comet 的 native 解码提升 Parquet 读取吞吐。默认值 `iceberg` 保证现有行为完全不变。
- **影响范围**：仅 `spark/v3.4` 模块。新增 Comet reader 类、配置项、conf 对象；重构 `BaseBatchReader`/`BatchDataReader`/`SparkColumnarReaderFactory`/`SparkBatch` 的 batchSize 传递链路为 conf 对象；重构 `ColumnVectorWithFilter` 使其通用化。Comet 依赖为 compileOnly，运行期需用户自行引入 comet-spark jar。当前 Comet reader 不支持 UUID、嵌套 struct；position/eq delete 通过 `ColumnVectorWithFilter`/`isDeleted` 集成。
- **回迁到 1.4.x 的注意事项**：此为新特性，回迁需谨慎。1.4.x 分支若要回迁需确认：① 1.4.x 的 Spark 3.4 模块结构与 main 一致；② Immutables 依赖在 1.4.x build.gradle 中可用；③ Comet 0.3.0 制品在 1.4.x 构建环境可解析（注意 1666 提交立即把 Comet 升到 0.5.0 并修复 API 差异，回迁时应直接用 0.5.0 及 1666 的修复，避免使用已过时的 0.3.0）；④ `ColumnVectorWithFilter` 的重构会影响 1.4.x 既有 `ColumnarBatchReader` 行为，需一并回迁保持一致。由于该特性依赖 native 库，1.4.x 发布制品若不打包 Comet，则该功能仅对显式引入 Comet 的用户生效，不影响其他用户。
