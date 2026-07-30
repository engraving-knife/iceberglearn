# 提交 2247：Flink: Dynamic Iceberg Sink: Add sink / core processing logic / benchmarking

## 提交信息

- **序号**：2247 / 4088
- **哈希**：68651a34987ebd66422fe79ceaff9f987f32d6d6
- **短哈希**：68651a349
- **日期**：2025-06-17 17:20:15 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Iceberg Sink: Add sink / core processing logic / benchmarking
- **PR/Issue**：#13304

## 总体目的

本提交是动态 Iceberg Sink 功能的核心实现提交，在之前提交（#13303 引入的 HashKeyGenerator、RowDataEvolver、DynamicTableUpdateOperator 基础组件）之上，添加了完整的 Sink 入口和核心处理逻辑。动态 Iceberg Sink 允许一个 Flink Sink 同时写入多个 Iceberg 表，打破了传统 IcebergSink 1:1 的 sink-to-table 关系。它支持根据用户提供的路由规则动态创建和更新表、自动适配 schema 和分区规格的变更。此外，本提交还包含性能基准测试代码，用于评估 DynamicRecord 序列化/反序列化的性能开销。

## 如何达成设计目的

- 新增 `DynamicIcebergSink`：实现 Flink SinkV2 接口（SupportsPreWriteTopology、SupportsCommitter、SupportsPreCommitTopology、SupportsPostCommitTopology），作为动态 Sink 的入口，构建完整的写入拓扑。
- 新增 `DynamicRecordProcessor`：核心处理算子，将用户输入转换为 DynamicRecord，处理表元数据缓存、schema/spec 更新检测、哈希键生成和数据路由。
- 新增 `DynamicRecordGenerator`：用户可实现的接口，将输入记录转换为 DynamicRecord。
- 重构 `SinkUtil`：将 `FlinkSink` 中的 `writeProperties` 方法提取到 `SinkUtil` 中并改为 public，供动态 Sink 复用。
- 为 `FlinkWriteConf` 和 `FlinkConfParser` 添加不依赖 Table 对象的构造函数，支持动态 Sink 在没有预定义表的情况下创建配置。
- 新增性能基准测试和集成测试。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (新增, +406 lines)

**修改目的**：动态 Iceberg Sink 的主入口类，构建完整的 Flink 写入拓扑。

**工作逻辑**：实现 Flink SinkV2 的多个拓扑接口：
- `addPreWriteTopology`：构建 pre-write 拓扑，使用 `DynamicRecordProcessor` 将输入转换为 DynamicRecordInternal，并通过 `DynamicTableUpdateOperator` 处理表元数据更新。使用 `HashKeyGenerator` 进行 keyBy 路由。
- `createWriter`：创建 `DynamicWriter`，负责实际的数据文件写入。
- `createCommitter`：创建 `DynamicCommitter`，负责提交快照。
- `addPreCommitTopology`：构建 pre-commit 拓扑，使用 `DynamicWriteResultAggregator` 聚合写入结果。
- `addPostCommitTopology`：构建 post-commit 拓扑，处理提交后操作。
- 每个 sink 实例生成唯一 UUID 作为 sinkId，用于区分不同 sink 写入同一表时产生的文件。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (新增, +171 lines)

**修改目的**：核心处理算子，将用户输入转换为 DynamicRecord 并处理表元数据。

**工作逻辑**：继承 ProcessFunction，在 open 时初始化 TableMetadataCache、HashKeyGenerator 和 TableUpdater。processElement 方法调用 DynamicRecordGenerator 将输入转为 DynamicRecord，然后通过 TableUpdater 检查和更新表 schema/spec。支持 immediateUpdate 模式，可选择立即更新或通过侧输出流异步更新。实现 Collector 接口收集生成的 DynamicRecord。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordGenerator.java` (新增, +34 lines)

**修改目的**：定义用户可实现的接口，将输入记录转换为 DynamicRecord。

**工作逻辑**：接口定义 `convert(T inputRecord, Collector<DynamicRecord> out)` 方法，用户实现该方法决定输入记录路由到哪个表、使用什么 schema 和分区规格。一条输入可以生成零到多条 DynamicRecord。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/SinkUtil.java` (修改, +50/-9 lines)

**修改目的**：将 writeProperties 方法从 FlinkSink 提取到 SinkUtil 并改为 public，供动态 Sink 复用。

**工作逻辑**：新增 `writeProperties(FileFormat format, FlinkWriteConf conf, Table table)` 静态方法，根据文件格式（PARQUET/AVRO/ORC）从 FlinkWriteConf 中读取压缩配置，合并表属性生成写入属性。类从 package-private 改为 `@Internal public`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java` (修改, +3/-46 lines)

**修改目的**：移除 writeProperties 方法，改为调用 SinkUtil.writeProperties。

**工作逻辑**：删除 FlinkSink 中的私有 writeProperties 方法和相关 import，将调用点改为 `SinkUtil.writeProperties(format, flinkWriteConf, initTable)`。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java` (修改, +4/-0 lines)

**修改目的**：新增不依赖 Table 的构造函数。

**工作逻辑**：新增 `FlinkWriteConf(Map<String, String> writeOptions, ReadableConfig readableConfig)` 构造函数，内部调用 `FlinkConfParser` 的新构造函数，使动态 Sink 在没有预定义 Table 对象的情况下也能创建写配置。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/FlinkConfParser.java` (修改, +7/-0 lines)

**修改目的**：新增不依赖 Table 的构造函数。

**工作逻辑**：新增 `FlinkConfParser(Map<String, String> options, ReadableConfig readableConfig)` 构造函数，tableProperties 设为空 ImmutableMap。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (修改, +11/-5 lines)

**修改目的**：小幅调整 HashKeyGenerator 以适配新的处理流程。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (修改, +2/-1 lines)

**修改目的**：小幅调整 DynamicCommitter。

### 测试文件

新增 `TestDynamicIcebergSink.java`（831 行）进行端到端集成测试，`TestDynamicIcebergSinkPerf.java`（245 行）进行性能测试，以及 `DynamicRecordSerializerDeserializerBenchmark.java`（138 行）进行序列化基准测试。修改 `TestFlinkIcebergSinkBase.java`、`SimpleDataUtil.java` 等测试辅助类。

## 总结

本提交是动态 Iceberg Sink 功能的核心实现，新增了 DynamicIcebergSink（Sink 入口）、DynamicRecordProcessor（核心处理算子）和 DynamicRecordGenerator（用户接口）三个关键组件，构建了完整的 Flink SinkV2 写入拓扑。同时重构了 SinkUtil 以复用写配置逻辑，并扩展了 FlinkWriteConf 以支持无表场景。这是 Iceberg Flink 集成支持多表动态写入的里程碑式提交。
