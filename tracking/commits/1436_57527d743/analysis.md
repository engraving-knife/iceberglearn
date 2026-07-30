# 提交 1436：Flink: Backport #11244 to Flink 1.19 (Add table.exec.iceberg.use-v2-sink option) (#11665)

## 提交信息

- **序号**：1436 / 4088
- **哈希**：57527d743b0c1863475aca17fe73ca26f28f6f8d
- **短哈希**：57527d743
- **日期**：2024-11-27（Wed Nov 27 13:43:46 2024 +0100）
- **作者**：Arek Burdach <arek.burdach@gmail.com>
- **提交说明**：Flink: Backport #11244 to Flink 1.19 (Add table.exec.iceberg.use-v2-sink option) (#11665)
- **PR/Issue**：#11665（回迁 #11244 至 Flink 1.19）
- **作用模块**：Flink v1.19

## 总体目的

Iceberg 的 Flink 集成提供了两种 sink 实现：

1. **`FlinkSink`**：基于「自定义算子链 + `DiscardingSink` 终止」的传统实现（使用旧版 `addSink` API 与 `org.apache.flink.streaming.api.functions.sink.DiscardingSink`）；
2. **`IcebergSink`**：基于 Flink SinkV2 API（`org.apache.flink.api.connector.sink2.Sink`）的现代实现，是推荐方向。

在 #11244（main 上对应的提交）中，Iceberg 引入了一个新的 Flink 配置项 `table.exec.iceberg.use-v2-sink`，让用户可以在表执行层面切换是否使用 SinkV2 实现的 `IcebergSink`，默认为 `false`（保持原有 `FlinkSink` 行为）。但该功能只在 main 的 Flink v1.20 路径下提供，未回迁到 Flink 1.19。

本提交把该配置项与对应的 sink 选择逻辑回迁到 Flink v1.19 路径，使 Flink 1.19 用户也能通过 SQL 配置（或 Table API）启用 SinkV2 实现，便于在生产中渐进式验证并切换到新 sink，加速 SinkV2 在生产环境的落地与对老 `FlinkSink` 的最终淘汰。

## 如何达成设计目的

1. **新增配置项**：在 `FlinkConfigOptions` 中新增 `TABLE_EXEC_ICEBERG_USE_V2_SINK`（key `table.exec.iceberg.use-v2-sink`，boolean，默认 `false`，描述为「Use the SinkV2 API based Iceberg sink implementation」）。
2. **在 `IcebergTableSink` 中按配置选择 sink**：在 `consumeDataStream` 中读取该配置，若为 `true` 则走 `IcebergSink.forRowData(...)` 路径，否则保留原 `FlinkSink.forRowData(...)` 路径；两条路径的 builder 调用形式对称（同样的 `tableLoader`、`tableSchema`、`equalityFieldColumns`、`overwrite`、`setAll(writeProps)`、`flinkConf(readableConfig)`、`append()`）。
3. **将 `FlinkSink` 内部 sink 改用 SinkV2 形式**：把 `DiscardingSink` 的导入从 `org.apache.flink.streaming.api.functions.sink.DiscardingSink` 改为 `...sink.v2.DiscardingSink`；将 `appendDummySink` 中的 `addSink(new DiscardingSink())` 改为 `sinkTo(new DiscardingSink<>())`，并把泛型与方法返回类型从 `<T>` 调整为 `<Void>`，移除 `@SuppressWarnings("unchecked")`。这是为了让 `FlinkSink` 的「终止算子」也使用 SinkV2 兼容的 `DiscardingSink`，与切换到 `IcebergSink` 后保持环境一致。
4. **测试**：在 `TestFlinkTableSink` 中新增参数 `useV2Sink`（index 4），扩展参数矩阵：原有用例固定 `useV2Sink=false`，再额外添加一组使用 `testhadoop_basenamespace` catalog + 三种 format（ORC/AVRO/PARQUET）+ 流/批二维的用例且 `useV2Sink=true`；并在 `getTableEnv` 中根据参数设置 `TABLE_EXEC_ICEBERG_USE_V2_SINK`。在 `TestFlinkTableSinkExtended` 中也加入 `useV2Sink` 参数，新增一个 `testUsedFlinkSinkInterface` 用例，通过解析 INSERT 语句得到的 `Transformation` 断言：开启 v2 时是 `SinkTransformation` 且 sink 是 `IcebergSink`，关闭时则是 `DiscardingSink`（旧链路终止算子）；并对 `testWriteParallelism` 做条件分支以适配两种 sink 的算子树形态。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/FlinkConfigOptions.java`

**修改目的**：新增 `table.exec.iceberg.use-v2-sink` 配置项。

**工作逻辑**：紧随 `TABLE_EXEC_ICEBERG_USE_V2_SOURCE` 之后追加：
```java
public static final ConfigOption<Boolean> TABLE_EXEC_ICEBERG_USE_V2_SINK =
    ConfigOptions.key("table.exec.iceberg.use-v2-sink")
        .booleanType()
        .defaultValue(false)
        .withDescription("Use the SinkV2 API based Iceberg sink implementation.");
```
默认 `false`，保持向后兼容。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/IcebergTableSink.java`

**修改目的**：根据配置选择 `IcebergSink` 或 `FlinkSink`。

**工作逻辑**：在 `consumeDataStream` 内把原本直接调用 `FlinkSink.forRowData(...)` 的代码改为分支：
```java
if (readableConfig.get(FlinkConfigOptions.TABLE_EXEC_ICEBERG_USE_V2_SINK)) {
  return IcebergSink.forRowData(dataStream)
      .tableLoader(tableLoader)
      .tableSchema(tableSchema)
      .equalityFieldColumns(equalityColumns)
      .overwrite(overwrite)
      .setAll(writeProps)
      .flinkConf(readableConfig)
      .append();
} else {
  return FlinkSink.forRowData(dataStream)
      .tableLoader(tableLoader)
      .tableSchema(tableSchema)
      .equalityFieldColumns(equalityColumns)
      .overwrite(overwrite)
      .setAll(writeProps)
      .flinkConf(readableConfig)
      .append();
}
```
两条路径参数对称，仅 builder 类型不同。同时新增 `import org.apache.iceberg.flink.sink.IcebergSink;`。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`

**修改目的**：把 `FlinkSink` 内部的「终止算子」从旧 `DiscardingSink`（`addSink`）切换到 SinkV2 的 `DiscardingSink`（`sinkTo`）。

**工作逻辑**：
- 将 `import org.apache.flink.streaming.api.functions.sink.DiscardingSink;` 改为 `import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink;`；
- `chainIcebergOperators()` 的返回类型由 `<T> DataStreamSink<T>` 改为 `DataStreamSink<Void>`；
- `appendDummySink(...)` 由 `<T> DataStreamSink<T> appendDummySink(SingleOutputStreamOperator<Void> committerStream)` 改为 `DataStreamSink<Void> appendDummySink(SingleOutputStreamOperator<Void> committerStream)`，内部 `addSink(new DiscardingSink())` 改为 `sinkTo(new DiscardingSink<>())`，并移除 `@SuppressWarnings("unchecked")` 与对应的强制转换。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSink.java`

**修改目的**：扩展参数矩阵覆盖 `useV2Sink=true`。

**工作逻辑**：
- 删除未使用的 `SOURCE_TABLE` 常量；
- 新增 `@Parameter(index = 4) private boolean useV2Sink;`，并把 `@Parameters` 名称模板更新为 `catalogName={0}, baseNamespace={1}, format={2}, isStreaming={3}, useV2Sink={4}`；
- 原有参数生成（遍历 format × catalog × isStreaming）固定追加 `false`（don't use v2 sink）；
- 新增一组参数生成：遍历 ORC/AVRO/PARQUET × 流/批，固定使用 `testhadoop_basenamespace` catalog 与 `Namespace.of("l0","l1")`，追加 `true`（use v2 sink）；
- 在 `getTableEnv` 末尾根据 `useV2Sink` 设置 `TABLE_EXEC_ICEBERG_USE_V2_SINK`，使后续测试用例实际走 v2 路径。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkTableSinkExtended.java`

**修改目的**：扩展 `useV2Sink` 参数并验证两种 sink 的算子树。

**工作逻辑**：
- 引入 `DiscardingSink`（v2）与 `SinkTransformation`；
- 把原 `@Parameter protected boolean isStreamingJob;` 改为 `@Parameter(index = 0) protected boolean isStreamingJob;`，新增 `@Parameter(index = 1) protected Boolean useV2Sink;`；
- `@Parameters` 由「true/false」二选一扩展为五组参数：`(true,false)`、`(false,false)`、`(true,true)`、`(false,true)`、`(true,null)`——其中最后一组 `useV2Sink=null` 用于不显式设置配置的回归场景；
- `getTableEnv` 中 `useV2Sink != null` 时才设置 `TABLE_EXEC_ICEBERG_USE_V2_SINK`，否则保留默认（`false`）；
- 新增 `testUsedFlinkSinkInterface` 测试：注册空数据 source、构造 INSERT、通过 `PlannerBase` 解析得到 `Transformation`，断言：
  - 总是 `SinkTransformation`；
  - `useV2Sink==true` 时 sink 是 `IcebergSink`；
  - 否则 sink 是 `DiscardingSink`（旧链路）；
- 改造 `testWriteParallelism`：根据 `useV2Sink` 区分算子树结构。开启 v2 时 sink 自身并行度为 1，其输入（writer）并行度按流/批为 2/4；关闭 v2 时维持原结构（sink→committer→writer，writer 并行度 1，writer 的输入为 2/4）。

## 小结

- **成效**：把 `table.exec.iceberg.use-v2-sink` 配置项及对应的 sink 选择逻辑回迁到 Flink v1.19，让用户可在 SQL 层面切换 `IcebergSink`（SinkV2）与 `FlinkSink`（旧链路），默认 `false` 保持向后兼容；同时把 `FlinkSink` 内部终止算子升级到 SinkV2 形式的 `DiscardingSink`。测试通过参数矩阵覆盖 v2 与非 v2 两种路径，并新增 `testUsedFlinkSinkInterface` 直接断言生成的 `Transformation` 类型与 sink 实现类，确保切换行为正确。共 5 个文件、约 123 新增/33 删除。
- **影响范围**：Flink v1.19 模块的主代码 3 个文件（`FlinkConfigOptions`、`IcebergTableSink`、`FlinkSink`）与测试 2 个文件（`TestFlinkTableSink`、`TestFlinkTableSinkExtended`）。属于功能回迁，向后兼容（默认关闭，新行为需显式开启）。
- **回迁到 1.4.x 的注意事项**：本提交本身就是一次回迁（main → v1.19），若 1.4.x 维护分支对应 Flink 版本包含 v1.19，可进一步回迁到 1.4.x。需注意：
  - 1.4.x 的 `FlinkSink` 是否已使用 SinkV2 形式的 `DiscardingSink`（`sink.v2.DiscardingSink` + `sinkTo`）；若仍是旧 `addSink` 形式，需配套调整以避免编译/运行期 API 不一致；
  - 1.4.x 是否已存在 `IcebergSink`（SinkV2 实现）；若不存在，则本回迁不完整（开启 v2 时找不到实现），需先把 `IcebergSink` 整体回迁，再回迁本配置开关；
  - 1.4.x 的 `FlinkConfigOptions` 与 Table API 集成若与 main 的 v1.19 有差异（如 `getTableEnv` 中配置设置方式），需按 1.4.x 既有写法适配测试。
  - 若 1.4.x Flink 版本仅为 v1.17/v1.18 而无 v1.19，则本提交不直接适用，但同套思路可平行回迁到对应版本。
