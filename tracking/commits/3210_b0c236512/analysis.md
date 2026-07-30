# 提交 3210：Flink: Backport: Dynamic Sink: Resolve effective write config at runtime (#15247)

## 提交信息

- **序号**：3210 / 4088
- **哈希**：b0c236512ae38b7668fa2a332b100db0f5b64d30
- **短哈希**：b0c236512
- **日期**：2026-02-06
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Dynamic Sink: Resolve effective write config at runtime (#15247)
- **PR/Issue**：#15247（回移 #15237）

## 总体目的

本提交是序号 3206（PR #15237，提交 `ccf4d91e1`）的回移（backport）。原 PR 修复了 Flink Dynamic Sink 在构建期一次性解析写配置、导致多表写入时忽略各表自身属性的问题，并将该改动应用于 `flink/v2.1` 模块。Iceberg 为不同 Flink 版本维护彼此独立的集成模块，除最新的 `flink/v2.1` 外，还有 `flink/v1.20` 与 `flink/v2.0` 两个仍在维护的版本线，它们各自存在完全相同的 Dynamic Sink 代码与缺陷。为使这两个版本线的用户同样受益，作者将 #15237 的修改干净地回移到 `flink/v1.20` 与 `flink/v2.0` 两个模块。

回移的核心动机与原 PR 一致：Dynamic Sink 在运行时按每条记录携带的目标表名动态路由数据到不同表，但旧实现在 Sink 构建阶段就把 `FlinkWriteConf` 及派生常量（`dataFileFormat`、`targetDataFileSize`、`overwriteMode`、`workerPoolSize`）固化下来，构建时尚无具体 `Table` 对象，导致无法感知任何单表属性；且 `SinkUtil.writeProperties(...)` 以 `null` 作为 table 参数，表级属性根本无法并入写属性。结果是所有目标表共用同一份全局配置，自定义的压缩编码、文件格式等被丢弃。回移把同样的"延迟解析"修复带给 v1.20 与 v2.0。

## 如何达成设计目的

整体设计与原 PR 完全相同：把已解析的 `FlinkWriteConf` 替换为更底层的 `Configuration flinkConfig`，连同写选项作为"原料"在各需要处即时生成 `FlinkWriteConf`，使每张目标表带上自身属性参与解析，并理顺"写选项 > Flink 配置 > 表属性"的优先级链。本提交一次性把该修改应用到两个 Flink 版本模块，每个模块涉及四个文件（核心两处 + 测试两处），共八个文件。经逐文件比对，回移的 diff 与原 PR 对应文件的改动逐字一致，仅文件路径前缀由 `flink/v2.1/` 改为 `flink/v1.20/` 与 `flink/v2.0/`。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+18/-24)

**修改目的**：将 v1.20 模块的 Dynamic Sink 写配置解析推迟到运行时。

**工作逻辑**：
删除 `flinkWriteConf`、`dataFileFormat`、`targetDataFileSize`、`overwriteMode`、`workerPoolSize` 五个已解析常量，改为只保存 `Configuration flinkConfig`；构造方法签名由接收 `FlinkWriteConf` 改为接收 `Configuration`。`createWriter(...)` 不再传 `dataFileFormat`/`targetDataFileSize`，改为透传 `writeProperties` 与 `flinkConfig`；`createCommitter(...)` 就地 `new FlinkWriteConf(writeProperties, flinkConfig)` 取 `overwriteMode()`/`workerPoolSize()`。builder `append()` 处把 `readableConfig` 规整为 `Configuration` 后传给 `instantiateSink`，末尾设置并行度时重新构造 `FlinkWriteConf` 取 `writeParallelism()`。逻辑与原 PR 一致。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+15/-16)

**修改目的**：在创建每张表的 writer 工厂时按该表属性解析有效写配置。

**工作逻辑**：
字段由 `FileFormat dataFileFormat`、`long targetDataFileSize` 改为 `Configuration flinkConfig`。创建 `RowDataTaskWriterFactory` 时改为 `FlinkWriteConf flinkWriteConf = new FlinkWriteConf(table, commonWriteProperties, flinkConfig);`（首参为 `table`，使表属性参与解析），再 `SinkUtil.writeProperties(flinkWriteConf.dataFileFormat(), flinkWriteConf, table)`（带 `table`），从而按表合并属性；`targetDataFileSize` 与 `dataFileFormat` 改从按表解析的 `flinkWriteConf` 取值。与原 PR 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+5/-5)

**修改目的**：使 v1.20 测试桩适配新构造签名。

**工作逻辑**：
`CommitHookDynamicIcebergSink` 的 `instantiateSink(...)` 参数类型由 `FlinkWriteConf` 改为 `Configuration`；`overwriteMode` 改为 `new FlinkWriteConf(writeProperties, flinkConfig).overwriteMode()` 即时解析。与原 PR 一致。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+96/-5)

**修改目的**：验证 v1.20 的按表配置解析与优先级链。

**工作逻辑**：
原 `testDynamicWriterPropertiesPriority` 重命名为 `testFlinkConfigOverridesTableProperties`；新增 `testWritePropertiesOverrideFlinkConfig`、`testFlinkConfigFileFormat`、`testFlinkConfigTargetFileSize` 三个测试，覆盖写选项 > Flink 配置 > 表属性的优先级及文件格式/目标文件大小按 Flink 配置生效。各测试改为以 `Map` 写选项 + `Configuration` 构造 `DynamicWriter`。与原 PR 一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+18/-24)

**修改目的**：将 v2.0 模块的 Dynamic Sink 写配置解析推迟到运行时。

**工作逻辑**：
与上述 v1.20 同名文件的改动逐字一致，仅路径前缀为 `flink/v2.0/`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicWriter.java` (+15/-16)

**修改目的**：在创建每张表的 writer 工厂时按该表属性解析有效写配置。

**工作逻辑**：
与 v1.20 同名文件改动一致。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+5/-5)

**修改目的**：使 v2.0 测试桩适配新构造签名。

**工作逻辑**：
与 v1.20 同名文件改动一致。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicWriter.java` (+96/-5)

**修改目的**：验证 v2.0 的按表配置解析与优先级链。

**工作逻辑**：
与 v1.20 同名文件改动一致。

## 总结

本提交将 PR #15237 对 Flink Dynamic Sink 的"运行时按表解析有效写配置"修复干净回移到仍在维护的 `flink/v1.20` 与 `flink/v2.0` 两个版本模块，使这两个版本线的用户同样获得多表写入时正确感知各表属性、并理顺配置优先级链的能力，与 `flink/v2.1` 保持行为一致。
