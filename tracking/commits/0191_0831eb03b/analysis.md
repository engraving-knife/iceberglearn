# 提交 0191：Flink: Emit watermarks from the IcebergSource (#8553)

## 提交信息

- **序号**：0191 / 4088
- **哈希**：0831eb03b09cce2d09dd62da829323004b41f423
- **短哈希**：0831eb03b
- **日期**：2023-11-23 10:40:33 +0100
- **作者**：pvary
- **提交说明**：Flink: Emit watermarks from the IcebergSource (#8553)
- **PR/Issue**：#8553

## 总体目的

这个提交为 Flink 的 `IcebergSource` 引入了基于文件列统计信息（column statistics）生成 watermark 的能力。在 Flink 流式处理中，watermark 是事件时间（event-time）处理的核心机制，用于触发窗口计算、水位对齐（watermark alignment）等。在此之前，Iceberg 的 Flink Source 不会发出 watermark，意味着下游算子无法基于事件时间进行调度，也无法使用 Flink 的 watermark alignment 机制来协调多个 source 的消费进度。

具体场景是：Iceberg 表中的数据文件通常带有列级统计（lower/upper bounds），尤其是时间戳列。本提交利用这些统计信息，在 reader 读取一个 split 之前，根据该 split 中所有文件 lower bound 的最小值生成一个 watermark 并发送给下游。同时，为了避免乱序 watermark，source 侧还会按 watermark 对 split 进行排序，使得 reader 倾向于先读取 watermark 较小（即事件时间更早）的 split。

这一改动对 Iceberg 在 Flink 流式场景下的演进意义重大：它让 Iceberg 既能作为有界批源，也能更好地作为无界/流式源参与事件时间语义的流处理，特别是与 Flink 的 watermark alignment 配合使用，可以避免某些 source 过快消费导致的数据延迟问题。

## 如何达成设计目的

整体设计思路是：引入一个可序列化的 `SplitWatermarkExtractor` 接口，由具体实现（如 `ColumnStatsWatermarkExtractor`）从一个 split 中提取 watermark；再引入一个 `SerializableRecordEmitter` 接口替代原先硬编码的 `IcebergSourceRecordEmitter`，默认实现保持原行为，而 `WatermarkExtractorRecordEmitter` 装饰器在每次切换 split 时通过 extractor 提取并发出 watermark。同时，在 `IcebergSource.Builder` 上新增 `watermarkColumn`/`watermarkTimeUnit` 配置，启用后会自动请求列统计、构造 watermark extractor、配置基于 watermark 的 split 排序比较器，并使用 `OrderedSplitAssignerFactory` 来保证 split 的下发顺序。改动整体结构清晰：核心逻辑放在新的 reader 包类中，Builder 做组装，测试覆盖了 extractor 单元测试、assigner 排序测试以及端到端 failover 测试。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：在 Source 顶层接入 watermark 生成机制，并提供 Builder 配置入口。

**工作逻辑**：
- `IcebergSource` 构造函数新增 `SerializableRecordEmitter<T> emitter` 字段，`createReader` 时把 emitter 传给 `IcebergSourceReader`，取代之前在 reader 内部硬编码 `IcebergSourceRecordEmitter` 的做法。这让 emitter 行为可插拔。
- Builder 新增 `watermarkColumn(String)` 与 `watermarkTimeUnit(TimeUnit)` 两个配置项，默认 `watermarkTimeUnit = TimeUnit.MICROSECONDS`。注释说明：watermark 基于 split 内文件列统计的最小值生成，支持 `TIMESTAMP`/`TIMESTAMPTZ`/`LONG` 类型列；建议同时调小 `read.split.open-file-cost` 防止小文件被合并到同一 split，从而破坏 watermark 对齐。
- `assignerFactory(...)` 与 `watermarkColumn(...)` 互斥校验：`Preconditions.checkArgument` 确保用户不会同时手动指定 SplitAssigner 和 watermark 列（因为 watermark 模式会自动用 `OrderedSplitAssignerFactory`）。
- 在 `build()` 中：若设置了 `watermarkColumn`，则调用 `contextBuilder.includeColumnStats(Sets.newHashSet(watermarkColumn))` 请求该列的统计；构造 `ColumnStatsWatermarkExtractor`；用 `SerializableRecordEmitter.emitterWithWatermark(watermarkExtractor)` 生成带 watermark 的 emitter；并把 `splitAssignerFactory` 设为 `new OrderedSplitAssignerFactory(SplitComparators.watermark(watermarkExtractor))`，使 split 按 watermark 升序下发。最后把 emitter 透传给 `IcebergSource` 构造函数。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/SplitWatermarkExtractor.java`（新增）

**修改目的**：定义从 split 提取 watermark 的可序列化接口。

**工作逻辑**：仅声明 `long extractWatermark(IcebergSourceSplit split)`，继承 `Serializable`，供 `ColumnStatsWatermarkExtractor` 实现以及 `SplitComparators.watermark(...)` 排序使用。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/ColumnStatsWatermarkExtractor.java`（新增）

**修改目的**：基于 Iceberg 文件列统计（lower bounds）实现 watermark 提取。

**工作逻辑**：
- 构造函数接收 `Schema`、`eventTimeFieldName`、`TimeUnit`。通过 schema 找到字段，校验类型必须是 `LONG` 或 `TIMESTAMP`，否则抛 `IllegalArgumentException`。对 `LONG` 列使用传入的 `timeUnit`，对 `TIMESTAMP` 列固定用 `MICROSECONDS`（因为 Iceberg timestamp 内部以微秒存储）。
- `extractWatermark(split)`：遍历 split 中所有 `CombinedScanTask` 的文件，从 `scanTask.file().lowerBounds()` 取出该字段 id 对应的字节，用 `Conversions.fromByteBuffer(Types.LongType.get(), ...)` 转成 long，再用 `timeUnit.toMillis(...)` 转毫秒，最后取所有文件中的最小值返回。
- 若任一文件缺少 lower bounds 统计，则抛 `IllegalArgumentException`（提示 "Missing statistics for column ..."），保证数据完整性约束。
- 还提供一个 `@VisibleForTesting` 的包级构造函数，直接传 fieldId/fieldName，用于测试缺失统计的场景。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/SerializableRecordEmitter.java`（新增）

**修改目的**：定义可序列化、可插拔的 record emitter 接口，替代原先硬编码的 emitter。

**工作逻辑**：`@FunctionalInterface` 接口，继承 Flink 的 `RecordEmitter<RecordAndPosition<T>, T, IcebergSourceSplit>` 与 `Serializable`。提供两个静态工厂：
- `defaultEmitter()`：原默认行为——`output.collect(element.record())` 并 `split.updatePosition(...)`。
- `emitterWithWatermark(extractor)`：返回 `WatermarkExtractorRecordEmitter` 实例。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/WatermarkExtractorRecordEmitter.java`（新增）

**修改目的**：在 emit 记录的同时，于 split 切换时发出 watermark。

**工作逻辑**：
- 持有 `SplitWatermarkExtractor timeExtractor`、`String lastSplitId`、`long watermark` 状态。
- `emitRecord(...)` 中：若当前 `split.splitId()` 不等于 `lastSplitId`（即进入新 split），则调用 `timeExtractor.extractWatermark(split)` 得到 `newWatermark`。若 `newWatermark < watermark`（出现回退），仅记录 INFO 日志而不发出（避免 watermark 倒退破坏 Flink 语义）；否则更新 `watermark` 并通过 `output.emitWatermark(new Watermark(watermark))` 发出。然后更新 `lastSplitId`，最后照常 `output.collect(element.record())` 与 `split.updatePosition(...)`。
- 这种"每个 split 起始发一次 watermark"的策略，结合 split 排序，使整个 source 输出的 watermark 单调不减。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/IcebergSourceReader.java`

**修改目的**：接收外部传入的 emitter，而非内部硬编码。

**工作逻辑**：构造函数新增 `SerializableRecordEmitter<T> emitter` 参数，传给父类 `SourceReaderBase` 替换原来的 `new IcebergSourceRecordEmitter<>()`。这是把 emitter 行为外部化的关键点。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/split/SplitComparators.java`

**修改目的**：新增基于 watermark 的 split 比较器，供 OrderedSplitAssigner 排序使用。

**工作逻辑**：新增 `watermark(SplitWatermarkExtractor)` 静态方法，返回 `SerializableComparator<IcebergSourceSplit>`，先按 `extractWatermark` 升序比较，watermark 相同时回退到 `splitId()` 字典序，保证排序稳定。同时顺手修正了一处原有注释的笔误（"IInvalid" → "Invalid"）。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestColumnStatsWatermarkExtractor.java`（新增）

**修改目的**：对 `ColumnStatsWatermarkExtractor` 做参数化单元测试。

**工作逻辑**：用 `@RunWith(Parameterized.class)` 对 `timestamp_column`、`timestamptz_column`、`long_column` 三种列类型分别测试。覆盖：单文件提取（`testSingle`）、`TimeUnit` 转换（`testTimeUnit`，仅 long 列）、多文件取最小值（`testMultipleFiles`）、错误列类型校验（`testWrongColumn`，对 string 列应抛异常）、缺失统计校验（`testEmptyStatistics`）。使用 `GenericAppenderHelper` 写入 Parquet 文件以生成真实列统计。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/assigner/TestWatermarkBasedSplitAssigner.java`（新增）

**修改目的**：测试基于 watermark 排序的 `OrderedSplitAssigner` 行为。

**工作逻辑**：继承 `SplitAssignerTestBase`，`splitAssigner()` 返回以 `SplitComparators.watermark(new ColumnStatsWatermarkExtractor(SCHEMA, "timestamp_column", null))` 构造的 assigner。测试：多文件 split 场景（`testMultipleFilesInAnIcebergSplit`）、乱序下发后按 watermark 升序消费（`testSplitSort`）、比较器可序列化（`testSerializable`）。重写 `createSplits` 生成带时间戳记录的真实 split。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailoverWithWatermarkExtractor.java`（新增）

**修改目的**：验证启用 watermark extractor 后，在 failover 场景下 source 仍能正确产出记录。

**工作逻辑**：继承 `TestIcebergSourceFailover`，重写 `sourceBuilder()` 设置 `.watermarkColumn("ts")`，重写 `generateRecords` 生成带递增 `ts`（批次间 +15 分钟，批内 +1 秒）的记录以模拟真实事件时间分布。重写 `assertRecords` 处理 `LocalDateTime` 与 `Comparators` 内部 Long 表示不一致的问题（归一化为 epochMilli 再比较）。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：将 `assertRecords` 提取为 protected 钩子，便于子类覆盖。

**工作逻辑**：新增 `protected void assertRecords(Table table, List<Record> expectedRecords, Duration timeout)`，原两处 `SimpleDataUtil.assertTableRecords(...)` 调用改为调用该 protected 方法，使带 watermark 的子类可以重写断言逻辑。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/assigner/SplitAssignerTestBase.java`

**修改目的**：抽出 `createSplits(...)` 模板方法，支持子类生成带特定数据的 split。

**工作逻辑**：原直接调用 `SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, fileCount, filesPerSplit)` 的地方改为调用新的 `protected List<IcebergSourceSplit> createSplits(int fileCount, int filesPerSplit, String version)`，默认实现仍走 Hadoop 表，子类（如 `TestWatermarkBasedSplitAssigner`）可重写以生成带时间戳数据的 split。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/assigner/TestFileSequenceNumberBasedSplitAssigner.java`

**修改目的**：适配基类抽出 `createSplits` 后的调用方式，并将一个 `assertGetNext` 由 protected 改为 private（避免与基类同名方法签名冲突）。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/reader/ReaderUtil.java`

**修改目的**：修复测试工具方法，使其生成的 `DataFile` 携带 metrics（含 lower bounds），供 watermark extractor 测试使用。

**工作逻辑**：原 `createCombinedScanTask` 用 try-with-resources 关闭 appender 后无法取 metrics；改为先创建 appender、try 块内 `addAll`、finally 块关闭，然后 `DataFile` 构建时 `.withMetrics(appender.metrics())` 写入统计信息。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestIcebergSourceReader.java`

**修改目的**：适配 `IcebergSourceReader` 构造函数新增 emitter 参数。

**工作逻辑**：构造 reader 时传入 `SerializableRecordEmitter.defaultEmitter()`，保持原有行为。

### `data/src/test/java/org/apache/iceberg/data/GenericAppenderHelper.java`

**修改目的**：让测试 appender 支持透传 Parquet 相关配置。

**工作逻辑**：新增 `PARQUET_CONFIG_PATTERN = ".*parquet.*"`，当 format 为 `PARQUET` 且有 conf 时，`appenderFactory.setAll(conf.getValByRegex(PARQUET_CONFIG_PATTERN))`，使测试能调整 Parquet 写入参数（如统计相关配置）。

## 小结

这个提交为 Iceberg 的 Flink Source 引入了基于文件列统计的 watermark 生成与 split 排序机制，使 Iceberg 能够真正参与 Flink 事件时间流处理与 watermark alignment，是 Iceberg-Flink 流式集成的一项重要能力补齐。
