# 提交 3206：Flink: Dynamic Sink: Resolve effective write config at runtime (#15237)

## 提交信息

- **序号**：3206 / 4088
- **哈希**：ccf4d91e1c0d3db1b723cb2cebb51253b0e05790
- **短哈希**：ccf4d91e1
- **日期**：2026-02-05
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Resolve effective write config at runtime (#15237)
- **PR/Issue**：#15237

## 总体目的

Iceberg 的 Flink Dynamic Sink（动态写入器）是一种特殊 Sink，它并不预先绑定到某一张表，而是在运行时根据每条 `DynamicRecordInternal` 中携带的目标表名，把数据动态路由到不同表中。正因为要写多张表，原实现存在一个根本缺陷：它在 Sink 构建阶段（builder 阶段）就把写配置一次性解析出来，包括 `dataFileFormat`、`targetDataFileSize`、`overwriteMode`、`workerPoolSize` 等，并把这些已解析的常量固化到 `DynamicIcebergSink` 与 `DynamicWriter` 的字段里。

这样做的问题在于：构建阶段根本没有具体的 `Table` 对象，于是 `FlinkWriteConf` 无法感知任何单表属性。例如某张表通过 `write.parquet.compression-codec` 自定义了压缩编码，或通过表属性指定了文件格式，这些设置都会被丢弃——动态 Sink 会用同一份全局配置去写所有表。此外 `SinkUtil.writeProperties(...)` 旧调用以 `null` 作为 table 参数，意味着即便 table 属性存在也无法被并入写属性。

本提交将写配置的解析时机从"构建期一次性解析"推迟到"运行时按表解析"：`DynamicIcebergSink` 不再持有 `FlinkWriteConf` 及派生常量，转而持有原始的 `Configuration flinkConfig` 与写选项（`writeOptions`/`writeProperties`）；当真正需要对某张表写数据时，才用加载到的 `Table` 对象连同这些原始输入构造 `FlinkWriteConf`，从而正确地按表解析出有效配置。

这一改动也理顺了配置优先级链：写入选项（write options）> Flink 配置（`Configuration`/`FlinkWriteOptions`）> 表属性。新增的测试用例明确验证了该优先级，并覆盖了 Flink 配置覆盖表属性、写选项覆盖 Flink 配置、Flink 配置决定文件格式与目标文件大小等场景。

## 如何达成设计目的

整体思路是"延迟解析 + 携带原始输入"。涉及四个文件：核心改动在 `DynamicIcebergSink.java` 与 `DynamicWriter.java`，分别把构造期解析改为运行期解析；`TestDynamicIcebergSink.java` 与 `TestDynamicWriter.java` 则调整测试桩并新增覆盖优先级与各配置项的测试。关键设计是把已解析的 `FlinkWriteConf` 替换为更底层的 `Configuration flinkConfig`（Flink 原生配置对象），它和写选项一起作为"原料"在各需要处即时生成 `FlinkWriteConf`，使每张目标表都能带上自身属性参与解析。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+18/-24)

**修改目的**：将写配置的解析推迟到运行时，按需重建 `FlinkWriteConf`。

**工作逻辑**：
字段层面删除了 `flinkWriteConf`、`dataFileFormat`、`targetDataFileSize`、`overwriteMode`、`workerPoolSize` 五个已解析常量，改为只保存 `Configuration flinkConfig`。构造方法签名相应从接收 `FlinkWriteConf` 改为接收 `Configuration flinkConfig`。

在 `createWriter(...)` 中不再传递 `dataFileFormat`/`targetDataFileSize`，而是把 `writeProperties`（写选项）与 `flinkConfig` 透传给 `DynamicWriter`，让其在写数据时按表解析。在 `createCommitter(...)` 中改为就地 `new FlinkWriteConf(writeProperties, flinkConfig)` 后再取 `overwriteMode()` 与 `workerPoolSize()`——因为 committer 不绑定单表，这里仍用全局配置。

构建器 `append()`（builder 收尾）处也做了重构：原先 `FlinkWriteConf flinkWriteConf = new FlinkWriteConf(writeOptions, readableConfig);` 并用 `SinkUtil.writeProperties(...)` 预算写属性，现改为先把 `readableConfig` 规整成 `Configuration`（若已是 `Configuration` 直接强转，否则 `Configuration.fromMap(...)`），再把 `writeOptions` 与 `flinkConfig` 传给 `instantiateSink(...)`。同时 `append()` 末尾设置并行度时，重新 `new FlinkWriteConf(writeOptions, readableConfig)` 取 `writeParallelism()`，因为并行度属全局策略、与单表无关。`instantiateSink` 的可测桩签名也由 `FlinkWriteConf` 改为 `Configuration`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+15/-16)

**修改目的**：在创建每张表的 writer 工厂时，按该表属性解析有效写配置。

**工作逻辑**：
字段由 `FileFormat dataFileFormat`、`long targetDataFileSize` 改为 `Configuration flinkConfig`，构造方法相应调整。关键改动在创建 `RowDataTaskWriterFactory` 的逻辑里：原先直接把 `table.properties()` 与 `commonWriteProperties` 做一次简单 `putAll` 合并；现在改为 `FlinkWriteConf flinkWriteConf = new FlinkWriteConf(table, commonWriteProperties, flinkConfig);` ——注意该构造把 `table` 作为首参传入，使表属性参与解析；随后 `SinkUtil.writeProperties(flinkWriteConf.dataFileFormat(), flinkWriteConf, table)` 也带上 `table`，从而能正确合并表级属性。最终传给 `RowDataTaskWriterFactory` 的 `targetDataFileSize` 与 `dataFileFormat` 改为从按表解析出的 `flinkWriteConf.targetDataFileSize()` / `flinkWriteConf.dataFileFormat()` 取值。

`toString()` 中移除了 `dataFileFormat` 与 `targetDataFileSize` 的输出。新增了对 `FlinkWriteConf`、`SinkUtil` 的 import，移除了 `FileFormat` 的 import。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+5/-5)

**修改目的**：使测试桩适配新的构造签名。

**工作逻辑**：
`CommitHookDynamicIcebergSink` 的 `instantiateSink(...)` 参数类型由 `FlinkWriteConf` 改为 `Configuration`；其内部原本直接 `this.overwriteMode = flinkWriteConf.overwriteMode();` 改为 `this.overwriteMode = new FlinkWriteConf(writeProperties, flinkConfig).overwriteMode();`，即时解析。父类构造调用也由传 `flinkWriteConf` 改为传 `flinkConfig`。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+96/-5)

**修改目的**：验证新的按表配置解析与优先级链。

**工作逻辑**：
原 `testDynamicWriterPropertiesPriority` 被重命名为 `testFlinkConfigOverridesTableProperties`，验证表属性 `write.parquet.compression-codec=zstd` 会被 Flink 配置 `COMPRESSION_CODEC=snappy` 覆盖（结果为 `snappy`）。

新增 `testWritePropertiesOverrideFlinkConfig`：写入选项 `compression-codec=gzip` 覆盖 Flink 配置 `COMPRESSION_CODEC=snappy`（结果为 `gzip`），从而确认优先级：写选项 > Flink 配置。

新增 `testFlinkConfigFileFormat`：Flink 配置 `WRITE_FORMAT=orc` 使输出文件以 `.orc` 落盘，断言 data 目录下存在且仅有一个 `.orc` 文件。

新增 `testFlinkConfigTargetFileSize`：Flink 配置 `TARGET_FILE_SIZE_BYTES=2048L` 被正确解析，写入一条记录后产生一个数据文件。

各测试均改为直接以 `Map` 写选项 + `Configuration` 构造 `DynamicWriter`，旧的 `FileFormat`/`targetDataFileSize` 入参被移除。

## 总结

本提交修正了 Flink Dynamic Sink 一个本质缺陷：写配置在构建期就被一次性固化，导致多表写入时忽略了各表自身的属性。通过改为运行期按表解析 `FlinkWriteConf`，并理清"写选项 > Flink 配置 > 表属性"的优先级链，动态 Sink 终于能为每张目标表生成正确的文件格式、压缩编码、目标文件大小等配置，显著提升了动态多表写入的正确性与可配置性。
