# 提交 1429：Flink: Add table.exec.iceberg.use-v2-sink option (#11244)

## 提交信息

- **序号**：1429 / 4088
- **哈希**：fa47f3141f8c163c4ddcbf1a02e9af85f3f5df04
- **短哈希**：fa47f3141
- **日期**：2024-11-25（Mon Nov 25 13:23:10 2024 +0100）
- **作者**：Arek Burdach <arek.burdach@gmail.com>
- **提交说明**：Flink: Add table.exec.iceberg.use-v2-sink option (#11244)
- **PR/Issue**：#11244

## 总体目的

Iceberg 的 Flink 集成当前默认使用 `FlinkSink`——一个基于自定义 `StreamOperator` 链 + `DiscardingSink` 的实现。这个实现是在 Flink Sink API 还不成熟时写的。Flink 1.15 引入了 SinkV2 接口（FLIP-191），Iceberg 据此实现了新的 `IcebergSink`（位于 `iceberg-flink` 模块），作为后续 table maintenance 等功能的基础。新的 `IcebergSink` 当前仍是实验性特性。

为了让用户能通过 SQL 方便地切换到 SinkV2 实现（而不仅限于 DataStream API 手动构造 `IcebergSink`），本提交新增 Flink 表配置项 `table.exec.iceberg.use-v2-sink`，默认 `false`（保持原有 `FlinkSink` 行为）。设为 `true` 时，`IcebergTableSink` 在 SQL 写入路径上改用 `IcebergSink`。

同时，为了让 `FlinkSink` 也能在新版 Flink 上正常工作，本提交把 `FlinkSink` 内部使用的 `DiscardingSink` 从旧的 `org.apache.flink.streaming.api.functions.sink.DiscardingSink`（Sink V1 API）切换到新的 `org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink`（Sink V2 API），并用 `sinkTo` 替代 `addSink`，同时把 `appendDummySink` 的泛型签名简化为 `DataStreamSink<Void>`。

此外补充了文档 `docs/docs/flink-writes.md` 说明 SinkV2 实现的用法与已知差异，并扩展了 `TestFlinkTableSink` / `TestFlinkTableSinkExtended` 的参数化测试，覆盖 v1/v2 两种 sink。

## 如何达成设计目的

1. **新增配置项**：在 `FlinkConfigOptions` 注册 `table.exec.iceberg.use-v2-sink`（boolean，默认 false）。
2. **SQL 写入路径分支**：在 `IcebergTableSink.consumeDataStream` 中根据该配置选择 `IcebergSink.forRowData(...)` 或 `FlinkSink.forRowData(...)`。
3. **FlinkSink 内部现代化**：把 `DiscardingSink` 切换到 v2 版本，`addSink` → `sinkTo`，泛型签名清理。
4. **文档**：在 `flink-writes.md` 新增"Flink Writes (SinkV2 based implementation)"章节，说明 SQL 开关与 DataStream 用法，并列出 `RANGE` distribution mode、`uidSuffix` vs `uidPrefix` 等差异。
5. **测试**：
   - `TestFlinkTableSink` 增加参数 `useV2Sink`，在 `useV2Sink=true` 时用 `testhadoop_basenamespace` catalog 跑一遍 v2 sink 全格式（ORC/AVRO/PARQUET）× 流批矩阵。
   - `TestFlinkTableSinkExtended` 增加 `useV2Sink` 参数（含 `null` 用例表示不设置开关），新增 `testUsedFlinkSinkInterface` 断言实际生成的 sink 类型，并改造 `testWriteParallelism` 适配 v1/v2 两种 operator 拓扑。

## 修改详情

### `docs/docs/flink-writes.md`

**修改目的**：文档化 SinkV2 实现及开关。

**工作逻辑**：新增章节"Flink Writes (SinkV2 based implementation)"，说明：
- 当前默认 `FlinkSink` 是基于自定义 `StreamOperator` 链 + `DiscardingSink` 的实现。
- Flink 1.15 引入 SinkV2（FLIP-191），新 `IcebergSink` 基于此，是 table maintenance 等后续功能的基础，当前为实验性。
- SQL 开启方式：`SET table.exec.iceberg.use-v2-sink = true;`
- DataStream 方式：直接用 `IcebergSink` 替代 `FlinkSink`，并提示差异：`RANGE` distribution mode 暂不支持 `IcebergSink`；`IcebergSink` 用 `uidSuffix` 而非 `uidPrefix`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkConfigOptions.java`

**修改目的**：注册新配置项。

**工作逻辑**：
```java
public static final ConfigOption<Boolean> TABLE_EXEC_ICEBERG_USE_V2_SINK =
    ConfigOptions.key("table.exec.iceberg.use-v2-sink")
        .booleanType()
        .defaultValue(false)
        .withDescription("Use the SinkV2 API based Iceberg sink implementation.");
```
默认 false，保证向后兼容。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/IcebergTableSink.java`

**修改目的**：SQL 写入路径根据开关选择 sink 实现。

**工作逻辑**：`consumeDataStream` 中：
- `readableConfig.get(TABLE_EXEC_ICEBERG_USE_V2_SINK)` 为 true 时调用 `IcebergSink.forRowData(...).tableLoader(...).tableSchema(...).equalityFieldColumns(...).overwrite(...).setAll(writeProps).flinkConf(readableConfig).append()`。
- 否则保持原 `FlinkSink.forRowData(...)` 链路。
- 引入 `org.apache.iceberg.flink.sink.IcebergSink` import。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`

**修改目的**：`FlinkSink` 内部切换到 Sink V2 API 的 `DiscardingSink`。

**工作逻辑**：
- import 由 `org.apache.flink.streaming.api.functions.sink.DiscardingSink` 改为 `org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink`。
- `chainIcebergOperators` 返回类型由 `<T> DataStreamSink<T>` 改为 `DataStreamSink<Void>`（消除伪泛型）。
- `appendDummySink` 重写：去掉 `@SuppressWarnings("unchecked")` 和 `<T>` 泛型，返回类型改为 `DataStreamSink<Void>`；内部 `committerStream.addSink(new DiscardingSink())` 改为 `committerStream.sinkTo(new DiscardingSink<>())`。`sinkTo` 是 Flink 1.15+ 用于 SinkV2 的接入方法。

这样 `FlinkSink` 与 `IcebergSink` 都基于 SinkV2 体系，避免在同一作业中混用 V1/V2 sink API 导致拓扑不兼容。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSink.java`

**修改目的**：参数化测试覆盖 v2 sink。

**工作逻辑**：
- 删除未使用的 `SOURCE_TABLE` 常量。
- 新增 `@Parameter(index = 4) boolean useV2Sink`，参数名模板加 `useV2Sink={4}`。
- `parameters()` 方法在原有 v1 矩阵（catalog×format×流批）之外，额外追加 v2 矩阵：固定 `testhadoop_basenamespace` catalog + `Namespace.of("l0","l1")`，跑 ORC/AVRO/PARQUET × 流批，`useV2Sink=true`。
- `getTableEnv` 中 `tEnv.getConfig().getConfiguration().set(TABLE_EXEC_ICEBERG_USE_V2_SINK, useV2Sink)`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSinkExtended.java`

**修改目的**：扩展测试覆盖 v2 sink 并新增 sink 类型断言。

**工作逻辑**：
- 新增 import：`DiscardingSink`（v2）、`SinkTransformation`、`IcebergSink`。
- 字段改为 `@Parameter(index=0) boolean isStreamingJob`、`@Parameter(index=1) Boolean useV2Sink`（用 `Boolean` 以支持 `null`）。
- `parameters()` 改为 5 组：`(true,false)`、`(false,false)`、`(true,true)`、`(false,true)`、`(true,null)`（null 表示不设置开关，验证默认行为）。
- `getTableEnv` 中 `useV2Sink != null` 时设置开关。
- 新增 `testUsedFlinkSinkInterface`：
  - 注册空数据源，解析 `INSERT INTO ... SELECT * FROM ...`，translate 出 `Transformation`。
  - 断言 transformation 是 `SinkTransformation`（说明用了 SinkV2 API）。
  - `useV2Sink` 为 true 时断言 sink 是 `IcebergSink`；否则断言是 `DiscardingSink`（即 `FlinkSink` 的 dummy sink 拓扑）。
- 改造 `testWriteParallelism`：v2 sink 拓扑与 v1 不同——v1 是 `dummySink → committer → writer`，v2 是 `sink → writerInput`。根据 `useV2Sink` 分别断言 parallelism。

## 小结

- **成效**：用户可通过 `SET table.exec.iceberg.use-v2-sink = true;` 在 SQL 中启用基于 SinkV2 的 `IcebergSink`，为后续 table maintenance 等功能铺路。`FlinkSink` 内部也统一到 SinkV2 API，避免 V1/V2 混用。默认行为不变（仍用 `FlinkSink`），向后兼容。
- **影响范围**：Flink 1.20 模块的 `FlinkConfigOptions`、`IcebergTableSink`、`FlinkSink`、两个测试类，以及文档。属于功能增强 + 内部 API 现代化，无表格式或元数据变更。
- **回迁到 1.4.x 的注意事项**：**需谨慎评估**。
  - 本提交依赖 `IcebergSink` 类已存在于 1.4.x 的 `flink` 模块。如果 1.4.x 尚未引入 `IcebergSink`（SinkV2 实现），则无法直接回迁——需先回迁 `IcebergSink` 本身，工作量较大，不适合维护分支。
  - `FlinkSink` 内部从 V1 `DiscardingSink` 切到 V2 `DiscardingSink` + `sinkTo`，要求 Flink 版本 ≥ 1.15。1.4.x 若仍支持 Flink 1.15 之前的版本（如 1.13/1.14），则不能回迁这部分改动。
  - 建议：1.4.x **不回迁**本提交，除非 1.4.x 已明确支持 Flink 1.20 且已有 `IcebergSink`。如果仅需要 SQL 开关而 `IcebergSink` 已存在，可仅回迁 `FlinkConfigOptions` + `IcebergTableSink` 两处改动，但需确认 `IcebergSink.forRowData(...).append()` 链路在 1.4.x 上完整可用。
  - 文档改动可独立回迁，无风险。
