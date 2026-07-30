# 提交 0204：Flink: Backport #8553 to v1.15, v1.16 (#9145)

## 提交信息

- **序号**：0204 / 4088
- **哈希**：5e059c1bfb664cc1880883425bfd68b2a8df3190
- **短哈希**：5e059c1bf
- **日期**：2023-11-28 22:59:44 +0100
- **作者**：pvary
- **提交说明**：Flink: Backport #8553 to v1.15, v1.16 (#9145)
- **PR/Issue**：#9145（backport #8553）

## 总体目的

本提交是把此前已合入主线（针对 Flink 1.17+）的 PR #8553——"基于 Iceberg 列统计（column statistics）的事件时间水印与 split 排序"——向后移植到 Iceberg 维护的 Flink 1.15 和 Flink 1.16 两个集成分支。功能本身是为 `IcebergSource`（Flink 新版 source API，`Source<T, IcebergSourceSplit, IcebergEnumeratorState>`）增加一种"按某列事件时间生成 watermark 并据此排序 split"的能力，使流式读取 Iceberg 表时既能产出有意义的 Flink watermark，又能尽量按事件时间顺序处理 split，便于下游做事件时间对齐、窗口计算和水印对齐（watermark alignment）。

背景：Iceberg 的每个数据文件在 manifest 中都带有每列的统计信息（lowerBounds / upperBounds / nullValueCounts 等），这些统计是文件级元数据，不需要扫数据即可获得。流式 Flink source 通常需要为下游提供 watermark 来推进事件时间，但 Iceberg source 此前并没有内建的水印策略——用户只能依赖下游算子或外部机制。#8553 的思路是：让用户在 `IcebergSource.Builder` 上指定一个"事件时间列"（`TIMESTAMP`、`TIMESTAMP_TZ` 或 `LONG` 类型），source 在构造 split 时把该列的列统计一并取回，然后：(1) 用每个 split 内所有文件 `lowerBounds` 的最小值作为该 split 的 watermark；(2) 按这个 watermark 给 split 排序（小水印的 split 先读）；(3) 在 record emitter 里，每开始读一个新 split 时把该 split 的 watermark 发给 `SourceOutput`，从而推进下游事件时间。这种做法代价极低（只读 manifest 统计，不读数据），且与 Iceberg 的文件级元数据天然契合。

由于 Iceberg 同时维护 Flink 1.15、1.16、1.17 三个版本分支，主线的 #8553 需要同步到 1.15/1.16 才能让这两个版本的用户用上新能力。作者 pvary（Peter Vary，Iceberg Flink 维护者）以单独 PR #9145 的形式完成 backport，并在 v1.15、v1.16 两个目录下镜像了完全相同的源码与测试改动。

## 如何达成设计目的

整体设计分四块，全部在 `flink/v1.1x/flink/src/main/java/org/apache/iceberg/flink/source/` 下：

1. 抽象"split 水印提取"为接口 [`SplitWatermarkExtractor`](flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/SplitWatermarkExtractor.java)，并提供唯一实现 [`ColumnStatsWatermarkExtractor`](flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/ColumnStatsWatermarkExtractor.java)，用列下界统计算 split 水印。
2. 把"记录发射"抽象为可序列化的 [`SerializableRecordEmitter`](flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/SerializableRecordEmitter.java)（函数式接口），提供 `defaultEmitter()` 和 `emitterWithWatermark(extractor)` 两个工厂；后者由 [`WatermarkExtractorRecordEmitter`](flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/WatermarkExtractorRecordEmitter.java) 实现，在每次切换 split 时发射一次 watermark。
3. 在 [`SplitComparators`](flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/split/SplitComparators.java) 上新增 `watermark(extractor)` 工厂，用提取出的 watermark 给 split 排序（小水印优先，相同水印按 splitId 兜底）。
4. 把 [`IcebergSourceReader`](flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/IcebergSourceReader.java) 改为接收外部传入的 `SerializableRecordEmitter`（而不是内部硬编码 `IcebergSourceRecordEmitter`），并在 [`IcebergSource`](flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java) 的 `Builder` 上新增 `watermarkColumn(String)` / `watermarkTimeUnit(TimeUnit)` 两个方法，在 `build()` 时若设了水印列就自动组装"含列统计的 scan + watermark emitter + 按 watermark 排序的 OrderedSplitAssignerFactory"。

改动共 32 个文件、2460 行新增、86 行删除，其中 v1.15 与 v1.16 各占一半（源码与测试均成对镜像），主体逻辑集中在 v1.15 一侧。

## 修改详情

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/SplitWatermarkExtractor.java`（新增）

**修改目的**：定义"从 split 提取 watermark"的抽象接口，使水印策略可插拔。

**工作逻辑**：`interface SplitWatermarkExtractor extends Serializable`，唯一方法 `long extractWatermark(IcebergSourceSplit split)`。返回的 watermark 单位是 epoch 毫秒（与 Flink `Watermark` 一致）。`Serializable` 是因为需要随 source 序列化分发到 task manager。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/ColumnStatsWatermarkExtractor.java`（新增）

**修改目的**：提供基于 Iceberg 列下界统计的水印提取实现，这是该特性的核心。

**工作逻辑**：`@Internal public class ColumnStatsWatermarkExtractor implements SplitWatermarkExtractor, Serializable`。构造器 `ColumnStatsWatermarkExtractor(Schema schema, String eventTimeFieldName, TimeUnit timeUnit)`：
- 用 `schema.findField(eventTimeFieldName)` 找到字段，校验类型必须是 `LONG` 或 `TIMESTAMP`（含 withZone/withoutZone），否则抛 `IllegalArgumentException`；
- 记下 `eventTimeFieldId` 和 `eventTimeFieldName`；
- `timeUnit` 仅对 `LONG` 列生效（用于把 long 值转 epoch 毫秒）；对 `TIMESTAMP` 列强制用 `MICROSECONDS`（Iceberg timestamp 内部存为 micros）。

核心方法 `extractWatermark(IcebergSourceSplit split)`：
```java
return split.task().files().stream()
    .map(scanTask -> {
      Preconditions.checkArgument(
          scanTask.file().lowerBounds() != null
              && scanTask.file().lowerBounds().get(eventTimeFieldId) != null,
          "Missing statistics for column name = %s in file = %s",
          ...);
      return timeUnit.toMillis(
          Conversions.fromByteBuffer(
              Types.LongType.get(), scanTask.file().lowerBounds().get(eventTimeFieldId)));
    })
    .min(Comparator.comparingLong(l -> l))
    .get();
```
即：遍历 split 中每个 `FileScanTask`，取该列在文件 `lowerBounds` 里的值（文件内该列的最小值），用 `Conversions.fromByteBuffer` 解成 `Long`，按 `timeUnit` 转 epoch 毫秒，最后取所有文件中的最小值作为整个 split 的 watermark。前提是 scan 必须带上该列的列统计（由 `IcebergSource.Builder` 通过 `contextBuilder.includeColumnStats(Sets.newHashSet(watermarkColumn))` 保证）。若任一文件缺统计，直接抛异常（fail-fast，避免悄悄发错水印）。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/SerializableRecordEmitter.java`（新增）

**修改目的**：把"记录发射"抽象出来，让水印版与默认版可通过工厂切换。

**工作逻辑**：`@Internal @FunctionalInterface interface SerializableRecordEmitter<T> extends RecordEmitter<RecordAndPosition<T>, T, IcebergSourceSplit>, Serializable`。两个静态工厂：
- `defaultEmitter()`：纯 lambda，`output.collect(element.record())` + `split.updatePosition(...)`，与重构前 `IcebergSourceRecordEmitter` 行为一致；
- `emitterWithWatermark(SplitWatermarkExtractor extractor)`：返回 `new WatermarkExtractorRecordEmitter<>(extractor)`。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/WatermarkExtractorRecordEmitter.java`（新增）

**修改目的**：在发射记录的同时，按 split 边界发射 Flink watermark。

**工作逻辑**：`class WatermarkExtractorRecordEmitter<T> implements SerializableRecordEmitter<T>`，持有 `SplitWatermarkExtractor timeExtractor`、`String lastSplitId`（初始 null）、`long watermark`。`emitRecord(element, output, split)`：
- 若 `split.splitId()` 与 `lastSplitId` 不同（即开始读一个新 split），调 `timeExtractor.extractWatermark(split)` 得到 `newWatermark`；
- 若 `newWatermark < watermark`（出现回退，比如 split 排序后被重新分配或并发读取），仅 `LOG.info` 记录，不发射回退的 watermark（Flink 语义要求 watermark 单调非减）；
- 否则更新 `watermark = newWatermark` 并 `output.emitWatermark(new Watermark(watermark))`；
- 更新 `lastSplitId = split.splitId()`；
- 最后照常 `output.collect(element.record())` + `split.updatePosition(...)`。

关键设计是"每个 split 只发一次 watermark"——通过 `lastSplitId` 去重，避免每条记录都发。这与 Flink 的 split 级 watermark 语义一致：split 内部假设事件时间都 ≥ 该 split 的下界水印。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/reader/IcebergSourceReader.java`（修改）

**修改目的**：把原来硬编码的 `IcebergSourceRecordEmitter` 改为外部注入，使水印版 emitter 能接入。

**工作逻辑**：构造器首位新增参数 `SerializableRecordEmitter<T> emitter`，原本 `new IcebergSourceRecordEmitter<>()` 改为传入的 `emitter`。这样 source 端可以按是否启用水印来传不同实现。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/split/SplitComparators.java`（修改）

**修改目的**：新增按 watermark 排序 split 的比较器；顺带修一个 typo。

**工作逻辑**：新增 `import SplitWatermarkExtractor`，新增静态方法 `watermark(SplitWatermarkExtractor watermarkExtractor)` 返回一个 `SerializableComparator<IcebergSourceSplit>`：对两个 split 各调 `extractWatermark`，`Long.compare` 比较；若相等用 `o1.splitId().compareTo(o2.splitId())` 兜底，保证全序。同时把原 `sequenceNumber` 比较器里一处 `"IInvalid file sequence number..."` 拼写错误修为 `"Invalid file sequence number..."`。

### `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`（修改）

**修改目的**：在 source 顶层接入新能力——构造器接收 emitter、Builder 暴露 `watermarkColumn` / `watermarkTimeUnit`、build 时组装整套链路。

**工作逻辑**：
- 新增 `import java.util.concurrent.TimeUnit`、`ColumnStatsWatermarkExtractor`、`SerializableRecordEmitter`、`SplitWatermarkExtractor`、`SplitComparators`、`Sets`；
- 主类新增字段 `private final SerializableRecordEmitter<T> emitter;`，构造器新增对应参数；`createReader` 把 emitter 传给 `new IcebergSourceReader<>(emitter, ...)`；
- Builder 新增 `private String watermarkColumn;` 和 `private TimeUnit watermarkTimeUnit = TimeUnit.MICROSECONDS;`（默认微秒，对应 Iceberg timestamp 内部表示）；
- 新增 `watermarkColumn(String columnName)`：校验 `splitAssignerFactory == null`（水印排序与自定义 assigner 互斥），存字段；
- 新增 `watermarkTimeUnit(TimeUnit)`：仅对 long 列生效；
- 在 `assignerFactory(SplitAssignerFactory)` 里也加反向校验 `watermarkColumn == null`，双向互斥；
- `build()` 里组装：默认 `emitter = SerializableRecordEmitter.defaultEmitter()`；若 `watermarkColumn != null`：`contextBuilder.includeColumnStats(Sets.newHashSet(watermarkColumn))`，构造 `ColumnStatsWatermarkExtractor`，`emitter = SerializableRecordEmitter.emitterWithWatermark(watermarkExtractor)`，并 `splitAssignerFactory = new OrderedSplitAssignerFactory(SplitComparators.watermark(watermarkExtractor))`（用 watermark 比较器建一个有序 assigner）。最后把 emitter 透传给 `new IcebergSource<>(..., emitter)`。

`watermarkColumn` 的 Javadoc 明确建议：用于 watermark alignment 时考虑调 `read.split.open-file-cost` 防止小文件被合并进同一个 split（否则一个 split 内文件跨度大、lowerBound 最小值偏小，水印会过于保守）。

### v1.16 镜像（同名列文件）

**修改目的**：在 Flink 1.16 分支镜像 v1.15 的全部源码改动。

**工作逻辑**：`flink/v1.16/flink/src/main/java/...` 下同名文件的 diff 与 v1.15 完全一致（IcebergSource、ColumnStatsWatermarkExtractor、IcebergSourceReader、SerializableRecordEmitter、SplitWatermarkExtractor、WatermarkExtractorRecordEmitter、SplitComparators）。这是 backport 的标准做法，确保两个维护分支行为对齐。

### 测试文件（新增）

**修改目的**：覆盖水印提取、source 端到端、failover、split assigner 四个层面。

**工作逻辑**：
- `TestColumnStatsWatermarkExtractor`（178 行，参数化测试）：用 `HadoopTableResource` 建表，写入随机 `Record`，对 timestamp/timestamptz/long 三种列分别验证 `extractWatermark` 等于"split 内所有文件 lowerBounds 的最小值"；对缺统计的文件验证抛异常；对 string 列验证构造期类型校验失败。
- `TestIcebergSourceWithWatermarkExtractor`（v1.15 481 行 / v1.16 451 行）：端到端验证设了 `watermarkColumn` 后 source 的 split 排序与 watermark 发射行为。
- `TestIcebergSourceFailoverWithWatermarkExtractor`（112 行）：验证 failover 场景下水印 emitter 的状态恢复正确性。
- `TestWatermarkBasedSplitAssigner`（146 行）：直接测 `SplitComparators.watermark(...)` 配合 `OrderedSplitAssignerFactory` 的排序行为。
- `SplitAssignerTestBase`、`TestFileSequenceNumberBasedSplitAssigner`、`ReaderUtil`、`TestIcebergSourceReader`、`TestIcebergSourceFailover`：因 emitter 签名变化做的同步小调整（传 emitter 参数、补 mock）。

## 小结

这次 backport 把主线 #8553 引入的"基于 Iceberg 列统计的事件时间水印 + split 排序"能力同步到 Flink 1.15/1.16 两个维护分支，让这两个版本的用户也能在 `IcebergSource` 上以极低代价（仅读 manifest 列统计）获得有意义的 Flink watermark 和按事件时间排序的 split 读取顺序。
