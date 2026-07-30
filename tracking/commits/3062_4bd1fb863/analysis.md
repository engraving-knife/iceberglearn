# 提交 3062：Flink: DynamicSink: Report writer records/bytes send metrics (#14878)

## 提交信息

- **序号**：3062 / 4088
- **哈希**：4bd1fb8632ecde781fdf7e444137f0981c0e86bb
- **短哈希**：4bd1fb863
- **日期**：2026-01-05
- **作者**：aiborodin
- **提交说明**：Flink: DynamicSink: Report writer records/bytes send metrics (#14878)
- **PR/Issue**：#14878

## 总体目的

本提交为 Flink Iceberg 动态 Sink（DynamicSink）补齐了标准的 Sink 写出指标上报，使动态 Sink 与 Flink 生态的指标体系对齐。Flink 的 `SinkWriterMetricGroup` 定义了一组 sink 专用的标准计数器，其中 `numRecordsSend`（已发送记录数）与 `numBytesSend`（已发送字节数）是衡量 sink 吞吐与背压的关键指标，被 Flink Web UI、Metrics Reporter 与监控系统广泛消费。然而动态 Sink 此前并未上报这两个计数器：`DynamicWriter` 在每条记录写入后没有递增 `numRecordsSend`，`DynamicWriterMetrics` 在 flush 完成拿到 `WriteResult`（包含已写出的数据文件与删除文件）后也没有累加 `numBytesSend`。这导致用户在动态 Sink 场景下无法从 Flink 指标体系观察写出量，监控出现盲区。

本提交分两处补齐：一是在 `DynamicWriter.write()` 中，每写入一条 `element.rowData()` 后调用 `metrics.mainMetricsGroup().getNumRecordsSendCounter().inc()`，按记录数递增；二是在 `DynamicWriterMetrics.updateFlushResult()` 中，把本次 flush 产出的数据文件与删除文件的大小求和，累加到 `mainMetricsGroup.getNumBytesSendCounter()`。这样动态 Sink 就能在每条记录与每次 flush 两个粒度上，向 Flink 的标准 sink 指标组上报记录数与字节数。

为支持上述改动，`DynamicWriterMetrics` 的 `mainMetricsGroup` 类型从宽泛的 `MetricGroup` 收窄为 `SinkWriterMetricGroup`，因为 `getNumRecordsSendCounter()`/`getNumBytesSendCounter()` 是 `SinkWriterMetricGroup` 才有的方法。这要求调用方传入的 metric group 也必须是 `SinkWriterMetricGroup`，测试中的 `new DynamicWriterMetrics(new UnregisteredMetricsGroup())` 相应改为 `new DynamicWriterMetrics(UnregisteredMetricsGroup.createSinkWriterMetricGroup())`。此外新增 `mainMetricsGroup()` 访问器，供 `DynamicWriter` 获取该 group 调用计数器。

## 如何达成设计目的

思路是复用 Flink `SinkWriterMetricGroup` 已有的 `numRecordsSend`/`numBytesSend` 计数器，在动态 Sink 的写入与 flush 两个时机分别上报。涉及 `flink/v2.1` 下三个文件：`DynamicWriter`（记录数计数）、`DynamicWriterMetrics`（字节数计数与类型收窄、访问器）、`TestDynamicWriter`（测试 metric group 类型适配）。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+1/-0 lines)

**修改目的**：在每条记录写入后递增 `numRecordsSend` 计数器。

**工作逻辑**：
在 `write(element)` 方法中，`taskWriter.write(element.rowData())` 之后新增一行 `metrics.mainMetricsGroup().getNumRecordsSendCounter().inc();`。`mainMetricsGroup()` 是本次新增的访问器，返回 `SinkWriterMetricGroup`，`getNumRecordsSendCounter()` 是 Flink 提供的标准 sink 计数器，`inc()` 默认递增 1。这样每写一条记录，Flink 指标体系中的"已发送记录数"就加 1，与 Flink 原生 sink 的语义一致。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriterMetrics.java` (+27/-3 lines)

**修改目的**：在 flush 完成后累加 `numBytesSend`，并将 metric group 类型收窄为 `SinkWriterMetricGroup`。

**工作逻辑**：
字段 `mainMetricsGroup` 类型由 `MetricGroup` 改为 `SinkWriterMetricGroup`，构造器入参同步收窄。新增 `SinkWriterMetricGroup mainMetricsGroup()` 访问器供 `DynamicWriter` 使用。在 `updateFlushResult(fullTableName, result)` 中，先调用原有的 `writerMetrics(fullTableName).updateFlushResult(result)` 更新每表 Iceberg 指标，再新增字节数累加：`long bytesOutTotal = sum(result.dataFiles()) + sum(result.deleteFiles());` 然后 `this.mainMetricsGroup.getNumBytesSendCounter().inc(bytesOutTotal);`。`sum(DataFile[])` 用 `DataFile::fileSizeInBytes` 求和数据文件大小；`sum(DeleteFile[])` 用 `ScanTaskUtil::contentSizeInBytes` 求和删除文件内容大小（删除文件的"内容大小"语义与数据文件略有不同，故走 `ScanTaskUtil`）；通用的 `sum(T[] files, ToLongFunction<T> sizeExtractor)` 通过 `Arrays.stream(files).mapToLong(sizeExtractor).sum()` 实现泛型求和。import 同步新增 `Arrays`、`ToLongFunction`、`SinkWriterMetricGroup`、`ContentFile`、`DataFile`、`DeleteFile`、`ScanTaskUtil`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+1/-1 lines)

**修改目的**：适配 `DynamicWriterMetrics` 构造器对 `SinkWriterMetricGroup` 的类型要求。

**工作逻辑**：
将构造 `DynamicWriterMetrics` 处的 `new DynamicWriterMetrics(new UnregisteredMetricsGroup())` 改为 `new DynamicWriterMetrics(UnregisteredMetricsGroup.createSinkWriterMetricGroup())`。因为 `DynamicWriterMetrics` 现在要求 `SinkWriterMetricGroup`，而 `UnregisteredMetricsGroup` 本身是 `MetricGroup`，需通过 `createSinkWriterMetricGroup()` 创建一个未注册的 sink writer metric group 实例，使测试代码编译通过并能正确暴露 `numRecordsSend`/`numBytesSend` 计数器。

## 总结

本提交通过在 `DynamicWriter.write()` 递增 `numRecordsSend`、在 `DynamicWriterMetrics.updateFlushResult()` 累加 `numBytesSend`（数据文件按 `fileSizeInBytes`、删除文件按 `ScanTaskUtil.contentSizeInBytes` 求和），为 Flink 动态 Sink 补齐了标准 sink 写出指标上报，并收窄 metric group 类型为 `SinkWriterMetricGroup` 以访问这些计数器，使动态 Sink 与 Flink 原生 sink 在监控语义上对齐，消除了动态 Sink 场景下的指标盲区。
