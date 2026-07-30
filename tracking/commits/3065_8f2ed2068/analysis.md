# 提交 3065：Flink: Backport: DynamicSink: Report writer records/bytes send metrics (#14971)

## 提交信息

- **序号**：3065 / 4088
- **哈希**：8f2ed2068967e9cadb7ae4e263cfef490c844b8e
- **短哈希**：8f2ed2068
- **日期**：2026-01-06
- **作者**：aiborodin
- **提交说明**：Flink: Backport: DynamicSink: Report writer records/bytes send metrics (#14971)
- **PR/Issue**：#14971（回移自 #14878）

## 总体目的

本提交是提交 3062（PR #14878）的回移（backport），将"Flink 动态 Sink 上报 numRecordsSend/numBytesSend 标准指标"的改动从 `flink/v2.1` 同步到 `flink/v1.20` 与 `flink/v2.0`。Iceberg 同时维护多个 Flink 版本适配模块，新功能/修复先在最新 `v2.1` 落地，再按相同逻辑回移到仍受支持的旧版本，保证各版本行为一致。

被回移的源改动（详见 3062 分析）解决的问题是：动态 Sink 此前未向 Flink 的 `SinkWriterMetricGroup` 上报标准的 sink 写出指标，导致 `numRecordsSend`（已发送记录数）与 `numBytesSend`（已发送字节数）两个计数器在动态 Sink 场景下始终为零，用户无法从 Flink 指标体系观察动态 Sink 的写出吞吐，监控出现盲区。回移后，`flink/v1.20` 与 `flink/v2.0` 下的 `DynamicWriter` 会在每条记录写入后递增 `numRecordsSend`，`DynamicWriterMetrics` 会在 flush 完成后把数据文件与删除文件大小累加到 `numBytesSend`。

之所以需要回移，是因为使用 Flink 1.20 与 2.0 的生产作业同样依赖 Flink 指标体系做监控与告警。若不回移，旧版本用户在动态 Sink 场景下无法获取写出量指标，与 v2.1 行为不一致，增加运维盲区。

## 如何达成设计目的

把 3062 对 `flink/v2.1` 的全部改动原样应用到 `flink/v1.20` 与 `flink/v2.0`：包括 `DynamicWriter` 的记录数递增、`DynamicWriterMetrics` 的字节数累加与类型收窄、`TestDynamicWriter` 的 metric group 类型适配。两套改动文件清单与内容完全对称，共 6 个文件。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+1/-0 lines)

**修改目的**：在每条记录写入后递增 `numRecordsSend` 计数器。

**工作逻辑**：
与 3062 中 `flink/v2.1` 同名文件改动一致：在 `write(element)` 的 `taskWriter.write(element.rowData())` 之后新增 `metrics.mainMetricsGroup().getNumRecordsSendCounter().inc();`，按记录数递增标准 sink 计数器。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriterMetrics.java` (+27/-3 lines)

**修改目的**：在 flush 完成后累加 `numBytesSend`，并将 metric group 类型收窄为 `SinkWriterMetricGroup`。

**工作逻辑**：
与 3062 一致：字段 `mainMetricsGroup` 类型由 `MetricGroup` 改为 `SinkWriterMetricGroup`，构造器入参同步收窄，新增 `mainMetricsGroup()` 访问器。`updateFlushResult()` 中新增 `long bytesOutTotal = sum(result.dataFiles()) + sum(result.deleteFiles());` 与 `this.mainMetricsGroup.getNumBytesSendCounter().inc(bytesOutTotal);`。`sum(DataFile[])` 用 `DataFile::fileSizeInBytes`，`sum(DeleteFile[])` 用 `ScanTaskUtil::contentSizeInBytes`，通用 `sum(T[], ToLongFunction<T>)` 用 `Arrays.stream(files).mapToLong(sizeExtractor).sum()`。import 同步新增 `Arrays`、`ToLongFunction`、`SinkWriterMetricGroup`、`ContentFile`、`DataFile`、`DeleteFile`、`ScanTaskUtil`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+1/-1 lines)

**修改目的**：适配 `DynamicWriterMetrics` 对 `SinkWriterMetricGroup` 的类型要求。

**工作逻辑**：
将 `new DynamicWriterMetrics(new UnregisteredMetricsGroup())` 改为 `new DynamicWriterMetrics(UnregisteredMetricsGroup.createSinkWriterMetricGroup())`，与 v2.1 一致。

### `flink/v2.0/` 下 3 个文件 (+30/-4 lines)

**修改目的**：对 Flink 2.0 做完全相同的回移。

**工作逻辑**：
`flink/v2.0` 下的 `DynamicWriter.java`（+1/-0，记录数递增）、`DynamicWriterMetrics.java`（+27/-3，字节数累加与类型收窄）、`TestDynamicWriter.java`（+1/-1，metric group 适配）三个文件，改动内容与上述 `flink/v1.20` 完全一致，此处不再逐文件赘述。

## 总结

本提交将 3062 的"Flink 动态 Sink 上报 numRecordsSend/numBytesSend 标准指标"改动从 `flink/v2.1` 回移到 `flink/v1.20` 与 `flink/v2.0`，使三个 Flink 版本在记录数计数、字节数累加、`SinkWriterMetricGroup` 类型收窄与测试适配上完全对齐，保证旧版本用户同样能通过 Flink 标准指标体系监控动态 Sink 的写出吞吐，消除监控盲区。
