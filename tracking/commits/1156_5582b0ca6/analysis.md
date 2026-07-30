# 提交 1156：Spark 3.4: Action to compute table stats (#11106)

## 提交信息

- **序号**：1156 / 4088
- **哈希**：5582b0ca67894400b988d39ed1bda77e938f763a
- **短哈希**：5582b0ca6
- **日期**：2024-09-13（Fri Sep 13 11:27:18 2024 -0700）
- **作者**：Karuppayya <karuppayya1990@gmail.com>
- **提交说明**：Spark 3.4: Action to compute table stats (#11106)
- **PR/Issue**：#11106

## 总体目的

Iceberg 支持通过 Puffin 文件存储表级别统计信息（statistics file），其中一类重要统计是 NDV（Number of Distinct Values，列的去重值数）。此前 Spark 3.5 已经有了 `ComputeTableStatsSparkAction`，但 Spark 3.4 模块还没有这个能力。本提交把该能力下沉到 Spark 3.4 模块，让 Spark 3.4 用户也能通过 `SparkActions.get().computeTableStats(table)` 触发一个分布式 action，为指定列（或全部基本类型列）生成 Apache DataSketches Theta Sketch，并把 sketch 写入 Puffin 文件、注册到表的 statistics 中，供后续查询优化器（如 Spark 基于 NDV 的成本估算）使用。

Theta Sketch 是一种概率数据结构，能在合并多个 partial sketch 后给出近似的去重值数，比精确 distinct 便宜得多，适合在大表上离线计算 NDV。

## 如何达成设计目的

1. 新增 `ComputeTableStatsSparkAction` 实现 `ComputeTableStats` 接口：参数化 `columns` 与 `snapshot`，`execute()` 在 Spark job group 中跑 `doExecute()`，由 `NDVSketchUtil.generateBlobs` 计算每个列的 Theta Sketch，再用 `PuffinWriter` 把 sketch 写成 Puffin 文件，最后通过 `table.updateStatistics().setStatistics(snapshotId, statisticsFile).commit()` 把统计文件元数据提交到表 metadata。
2. 新增 `NDVSketchUtil` 工具类：用 Spark DataFrame 读取指定 snapshot 的数据，对每个目标列调用一个自定义聚合函数 `ThetaSketchAgg`，把每个列的 sketch 作为一行返回；再包装成 Iceberg `Blob`（类型 `APACHE_DATASKETCHES_THETA_V1`，压缩 `ZSTD`，附带 `ndv` 属性）。
3. 新增 Scala 自定义聚合 `ThetaSketchAgg`：继承 Spark Catalyst 的 `TypedImperativeAggregate[Sketch]`，在每个 partition 上构建 `UpdateSketch`（Alpha family，默认 seed），用 Iceberg 的 `Conversions.toByteBuffer` 把列值序列化为字节喂给 sketch；merge 阶段用 `SetOperationBuilder().buildUnion` 合并 partial sketches；最终 `compact()` 后输出 `BinaryType` 字节数组。
4. 在 `SparkActions` 上注册 `computeTableStats(table)` 工厂方法。
5. 在 `spark/v3.4/build.gradle` 中加入 `datasketches-java` 依赖；并在 runtime shadow jar 配置中把 `org.apache.datasketches` relocate 到 `org.apache.iceberg.shaded.org.apache.datasketches`，避免与用户 classpath 上的 datasketches 版本冲突。
6. 新增测试 `TestComputeTableStatsAction`（约 415 行，19 个测试方法），覆盖各种数据类型、列校验、空 snapshot、null 值、不同 schema 的 snapshot、嵌套 schema、不可计算列等场景。

## 修改详情

### `spark/v3.4/build.gradle`

**修改目的**：引入 datasketches 依赖并配置 shading。

**工作逻辑**：
- 在 `iceberg-spark-3.4_2.12` 子项目的 `dependencies` 中新增 `implementation("org.apache.datasketches:datasketches-java:${libs.versions.datasketches.get()}")`。
- 在 runtime shadow jar 的 `relocate` 列表中新增 `relocate 'org.apache.datasketches', 'org.apache.iceberg.shaded.org.apache.datasketches'`，保证发布包内 datasketches 类被重定位，避免与 Spark 用户应用中的 datasketches 版本冲突。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/ComputeTableStatsSparkAction.java`（新增）

**修改目的**：Spark 3.4 实现的"计算表统计"action。

**工作逻辑**：
- 继承 `BaseSparkAction<ComputeTableStatsSparkAction>` 并实现 `ComputeTableStats` 接口。
- 字段：`Table table`、`List<String> columns`（可空，为空时默认取 snapshot schema 中所有 primitive 列）、`Snapshot snapshot`（默认 `table.currentSnapshot()`）。
- `columns(String...)` / `snapshot(long)` 提供链式配置入口；前者去重后存为不可变 list，后者校验 snapshot 存在。
- `execute()`：若 snapshot 为 null（空表）直接返回 `EMPTY_RESULT`；否则 `validateColumns()` 后在 `JobGroupInfo` 中跑 `doExecute()`。
- `doExecute()`：调 `generateNDVBlobs()` 算出每个列的 `Blob` 列表，调 `writeStatsFile(blobs)` 把 blob 写入 Puffin 文件得到 `StatisticsFile`，再 `table.updateStatistics().setStatistics(snapshotId(), statisticsFile).commit()` 把统计文件元数据提交到表 metadata；返回 `ImmutableComputeTableStats.Result` 含 `statisticsFile`。
- `writeStatsFile(blobs)`：用 `table.io().newOutputFile(outputPath())` 创建输出文件，`Puffin.write(outputFile).createdBy(appIdentifier()).build()` 创建 `PuffinWriter`，`blobs.forEach(writer::add)`、`writer.finish()`，构造 `GenericStatisticsFile(snapshotId, location, fileSize, footerSize, blobMetadata)` 返回。
- `outputPath()`：通过 `((HasTableOperations) table).operations().metadataFileLocation("{snapshotId}-{UUID}.stats")` 决定输出路径，与 metadata 文件放在一起。
- `columns()`：lazy 初始化，若未指定则从 `table.schemas().get(snapshot.schemaId())` 中取所有 primitive 列名。
- `validateColumns()`：校验列存在且为 primitive 类型（嵌套结构不能算 NDV）。
- `appIdentifier()`：返回 `"Iceberg {icebergVersion} Spark {sparkVersion}"`，写入 Puffin 文件的 `created-by` 字段便于追踪。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/NDVSketchUtil.java`（新增）

**修改目的**：封装"用 Spark 算 NDV sketch 并包装成 Iceberg Blob"的逻辑。

**工作逻辑**：
- 常量 `APACHE_DATASKETCHES_THETA_V1_NDV_PROPERTY = "ndv"`：作为 blob 的 properties key，value 是估算的 NDV 字符串。
- `generateBlobs(spark, table, snapshot, columns)`：调 `computeNDVSketches` 取一行 `Row`，每个 column 对应一个 byte[] sketch；对每个 column 找到 `schema.findField(col)`，`CompactSketch.wrap(Memory.wrap(bytes))` 还原 sketch，调 `toBlob(field, sketch, snapshot)` 包装。
- `toBlob(field, sketch, snapshot)`：构造 `Blob`：
  - type = `StandardBlobTypes.APACHE_DATASKETCHES_THETA_V1`
  - inputFields = `[field.fieldId()]`
  - snapshotId = `snapshot.snapshotId()`
  - sequenceNumber = `snapshot.sequenceNumber()`
  - content = `ByteBuffer.wrap(sketch.toByteArray())`
  - compression = `PuffinCompressionCodec.ZSTD`
  - properties = `{ndv: String.valueOf((long) sketch.getEstimate())}`（把估算值取整后写入，便于不解析 sketch 就能拿到 NDV）
- `computeNDVSketches(spark, table, snapshot, colNames)`：用 `spark.read.format("iceberg").option(SparkReadOptions.SNAPSHOT_ID, ...).load(table.name()).select(toAggColumns(colNames)).first()` 触发一次 Spark action，返回单行结果。
- `toAggColumn(colName)`：构造 `new Column(new ThetaSketchAgg(colName).toAggregateExpression())`，把自定义聚合函数挂到 Spark 列上。

### `spark/v3.4/spark/src/main/scala/org/apache/spark/sql/stats/ThetaSketchAgg.scala`（新增）

**修改目的**：实现 Spark Catalyst 自定义聚合函数，在分布式环境下计算 Theta Sketch。

**工作逻辑**：
- `case class ThetaSketchAgg(child, mutableAggBufferOffset, inputAggBufferOffset)` 继承 `TypedImperativeAggregate[Sketch]` 与 `UnaryLike[Expression]`。提供 `(colName: String)` 构造方便 Java 侧调用。
- `dataType = BinaryType`、`nullable = false`：聚合结果以 byte[] 形式返回。
- `createAggregationBuffer()`：返回 `UpdateSketch.builder.setFamily(Family.ALPHA).build()`，即 Alpha family sketch（比默认 Theta 快），用默认 seed。
- `update(buffer, input)`：取 child 表达式在输入行上的值，若非 null 则用 `toIcebergValue` 转换（UTF8String→String、Decimal→BigDecimal、byte[]→ByteBuffer），再用 `Conversions.toByteBuffer(icebergType, value)` 把 Iceberg 类型值序列化为字节喂给 `UpdateSketch.update`。
- `merge(buffer, input)`：用 `new SetOperationBuilder().buildUnion.union(buffer, input)` 合并两个 partial sketch（partial 聚合阶段在 reducer 上调用）。
- `eval(buffer)` / `serialize(buffer)`：调 `toBytes` 把 sketch `compact()` 后输出为字节数组（Compact Theta sketch 格式，便于存储与跨进程传输）。
- `deserialize(storageFormat)`：用 `CompactSketch.wrap(Memory.wrap(bytes))` 还原。
- `withNewMutableAggBufferOffset` / `withNewInputAggBufferOffset` / `withNewChildInternal`：Catalyst 树改写所需的标准方法，用 `copy(...)` 返回新实例。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java`

**修改目的**：暴露 `computeTableStats` 工厂方法。

**工作逻辑**：新增 `@Override public ComputeTableStats computeTableStats(Table table) { return new ComputeTableStatsSparkAction(spark, table); }`，实现 `ActionsProvider` 接口的方法。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputeTableStatsAction.java`（新增）

**修改目的**：覆盖 action 的端到端正确性与边界。

**工作逻辑**：继承 `SparkCatalogTestBase`，19 个测试方法覆盖：
- `testComputeTableStatsAction`：基本流程，构造含重复值的 5 行数据，断言生成 1 个 statistics file、2 个 blob（id 和 data 列），且 id 列的 NDV 属性为 4（去重后 1,2,3,4）。同时设置 `read.split.target-size=100` 和 `write.parquet.row-group-size-bytes=100` 触发多个 split，验证多 partition 合并的正确性。
- `testComputeTableStatsActionWithoutExplicitColumns`：不指定 columns 时默认取所有 primitive 列。
- `testComputeTableStatsForInvalidColumns`：传入不存在的列名或嵌套列时抛 `IllegalArgumentException`。
- `testComputeTableStatsWithNoSnapshots`：空表（无 snapshot）时返回空结果。
- `testComputeTableStatsWithNullValues`：列含 null 值时 sketch 应正确忽略 null。
- `testComputeTableStatsWithSnapshotHavingDifferentSchemas`：指定历史 snapshot 时按该 snapshot 的 schema 算统计。
- `testComputeTableStatsWhenSnapshotIdNotSpecified`：默认使用 current snapshot。
- `testComputeTableStatsWithNestedSchema`：嵌套结构列不能算 NDV。
- `testComputeTableStatsWithNoComputableColumns`：表只有嵌套列时抛异常。
- `testComputeTableStatsOnByte/Short/Int/Long/Timestamp/TimestampNtz/Date/Decimal/BinaryColumn`：逐一覆盖各种基本类型的 sketch 计算正确性。

## 小结

- **成效**：Spark 3.4 用户现在可以调用 `SparkActions.get().computeTableStats(table).columns("c1", "c2").execute()` 为表生成 NDV 统计，结果以 Puffin 文件形式持久化在表 metadata 下，并自动注册到 `table.statisticsFiles()`，供查询优化器（如 Spark 的 cost-based optimizer）使用。同时与 Spark 3.5 实现保持一致。
- **影响范围**：仅 `spark/v3.4` 模块。新增 3 个生产文件（`ComputeTableStatsSparkAction` / `NDVSketchUtil` / `ThetaSketchAgg`）、1 个测试文件，修改 `SparkActions` 与 `build.gradle`。无 core 模块变更，无表元数据格式变更（statistics file 与 puffin spec 已存在）。
- **回迁到 1.4.x 的注意事项**：
  1. 该改动完全是 Spark 3.4 模块的增量功能。1.4.x 如果还维护 Spark 3.4 模块则可回迁；若 1.4.x 已不再支持 Spark 3.4（只保留 3.3 / 3.5），则可跳过。
  2. 依赖 `core` 模块的 `ComputeTableStats` action 接口、`ImmutableComputeTableStats.Result`、`Puffin` / `PuffinWriter`、`StandardBlobTypes.APACHE_DATASKETCHES_THETA_V1`、`GenericStatisticsFile`、`table.updateStatistics()` API 等。1.4.x 必须已有这些基础能力（这些在 1.4.0 已合入）。
  3. 必须同时回迁 `build.gradle` 的 datasketches 依赖与 relocate 配置，否则编译失败或发布的 jar 与用户环境冲突。
  4. Theta Sketch 用 Alpha family + 默认 seed，与 Spark 3.5 实现保持一致；若 1.4.x 的 Spark 3.5 模块已有该实现，则两份代码应保持一致以便于维护。
  5. Puffin 文件使用的 `created-by` 字段、blob 的 `type` / `properties` 等都是已有 spec，无需协议升级；与 1.4.x 已有 statistics 文件完全兼容。
  6. 测试需要 Spark 集成测试环境（`SparkCatalogTestBase`），1.4.x 若已有该测试基础设施可直接复用。
