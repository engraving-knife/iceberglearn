# 提交 2250：Flink: Backport dynamic Iceberg Sink: Add sink / core processing logic / benchmarking to Flink 1.19 / 1.20 (#13341)

## 提交信息

- **序号**：2250 / 4088
- **哈希**：81b74fc0b9c7cc9247588438f0740797d7c26e76
- **短哈希**：81b74fc0b
- **日期**：2025-06-18 16:06:19 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport dynamic Iceberg Sink: Add sink / core processing logic / benchmarking to Flink 1.19 / 1.20
- **PR/Issue**：#13341 (backports #13304)

## 总体目的

本提交将动态 Iceberg Sink 的核心处理逻辑和基准测试从 Flink 2.0 回移植（backport）到 Flink 1.19 和 1.20 版本。动态 Iceberg Sink 是一个实验性功能，它突破了传统 Iceberg Sink 的 1:1 sink-table 映射限制，支持向任意数量的表写入数据，并根据用户提供的路由规则动态创建和更新表，以及根据用户提供的规范更新表的 schema 和分区规范。

这一功能对于需要将流数据动态路由到多个 Iceberg 表的场景非常重要。例如，在多租户数据管道中，数据可能需要根据内容路由到不同的表，且表的 schema 可能随时间演进。动态 Sink 允许在不重启作业的情况下处理这些变化。通过回移植到 Flink 1.19/1.20，使得使用这些较旧 Flink 版本的用户也能使用这一强大的功能。

## 如何达成设计目的

- 新增 `DynamicIcebergSink` 类作为动态 Sink 的入口，实现 Flink 的 `Sink` 接口及其多个拓扑扩展接口（`SupportsPreWriteTopology`、`SupportsCommitter`、`SupportsPreCommitTopology`、`SupportsPostCommitTopology`），构建完整的写入-预提交-提交流水线。
- 新增 `DynamicRecordGenerator` 接口，允许用户自定义输入数据到 `DynamicRecord` 的转换逻辑。
- 新增 `DynamicRecordProcessor` 作为 ProcessFunction，负责处理输入记录、查询表元数据缓存、决定是直接写入还是触发表更新。
- 将 `FlinkSink` 和 `IcebergSink` 中的 `writeProperties` 方法提取到 `SinkUtil` 工具类中，实现代码复用。
- 新增完整的测试类 `TestDynamicIcebergSink` 和性能测试类 `TestDynamicIcebergSinkPerf`，以及序列化/反序列化基准测试。
- 所有改动同时应用于 flink/v1.19 和 flink/v1.20 两个模块。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (新增, +406/0 lines)

**修改目的**：实现动态 Iceberg Sink 的核心类，支持多表写入和动态表管理。

**工作逻辑**：`DynamicIcebergSink` 实现了 Flink Sink V2 接口的多重拓扑扩展。`createWriter()` 创建 `DynamicWriter` 负责实际数据写入；`createCommitter()` 创建 `DynamicCommitter` 负责提交事务。`addPreWriteTopology()` 在写入前对数据流进行分发（按表名 keyBy）。`addPreCommitTopology()` 将写入结果按表名分组并聚合，生成可提交的 `DynamicCommittable`。Builder 模式允许用户配置 CatalogLoader、写入属性、快照属性等。每次创建 Sink 时生成随机 UUID 作为 sinkId，用于区分不同 Sink 写入同一表时生成的文件。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordGenerator.java` (新增, +34/0 lines)

**修改目的**：定义用户自定义输入数据到 DynamicRecord 转换的接口。

**工作逻辑**：`DynamicRecordGenerator<T>` 是一个可序列化接口，包含 `open()` 生命周期方法和 `convert()` 方法（注：后续在 2251 提交中改名为 `generate()`）。用户实现此接口将输入类型 T 转换为零个、一个或多个 `DynamicRecord`，通过 `Collector` 输出。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (新增, +171/0 lines)

**修改目的**：处理输入记录并决定写入路径或表更新操作。

**工作逻辑**：继承 Flink 的 `ProcessFunction`，在 `open()` 中初始化 `TableMetadataCache`、`HashKeyGenerator` 和 `TableUpdater`（或旁路输出标签）。`processElement()` 调用 generator 转换输入并收集结果。在 `collect()` 方法中，查询表元数据缓存判断表是否存在、schema 和分区规范是否匹配，然后决定是将记录写入现有表、触发表创建/更新（立即或通过旁路输出），还是丢弃记录。支持两种模式：immediateUpdate（立即更新表结构）和延迟更新（通过旁路输出流发送更新请求）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/SinkUtil.java` (修改, +59/-XX lines)

**修改目的**：将 writeProperties 逻辑提取为公共工具方法，供 FlinkSink、IcebergSink 和 DynamicIcebergSink 共享。

**工作逻辑**：从 FlinkSink 和 IcebergSink 中提取 `writeProperties` 方法到 SinkUtil，根据 FileFormat（PARQUET/AVRO/ORC）覆盖表级别的压缩属性配置，返回合并后的写入属性 Map。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java` (修改, +49/-XX lines)

**修改目的**：移除内联的 writeProperties 方法，改为调用 SinkUtil.writeProperties，同时移除不再需要的压缩相关 import。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +49/-XX lines)

**修改目的**：同 FlinkSink，移除内联 writeProperties 方法，改为调用 SinkUtil 共享方法。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicCommitter.java` (修改, +3/-1 lines)

**修改目的**：适配 DynamicCommitter 以支持动态 Sink 的提交流程。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (修改, +16/-XX lines)

**修改目的**：为动态 Sink 提供哈希键生成逻辑，用于数据分发。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkConfParser.java` (修改, +7/0 lines)

**修改目的**：新增配置项解析支持动态 Sink 相关配置。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/FlinkWriteConf.java` (修改, +4/0 lines)

**修改目的**：新增 FlinkWriteConf 中动态 Sink 相关的写入配置项。

### 测试文件 (新增/修改)

- `TestDynamicIcebergSink.java` (+831 lines)：完整的动态 Sink 功能测试
- `TestDynamicIcebergSinkPerf.java` (+245 lines)：动态 Sink 性能测试
- `DynamicRecordSerializerDeserializerBenchmark.java` (+138 lines)：序列化/反序列化基准测试
- `SimpleDataUtil.java`、`TestHelpers.java`、`TestFlinkIcebergSinkBase.java`、`TestFlinkFilters.java`：测试辅助工具的适配修改

注：以上所有改动同时在 `flink/v1.19/` 对应路径下重复一份。

## 总结

本提交是动态 Iceberg Sink 功能回移植到 Flink 1.19/1.20 的核心提交，涉及近 3900 行新增代码。它引入了完整的动态 Sink 处理流水线，包括多表写入、动态表创建/更新、schema 演进支持等能力，并通过代码重构（提取 SinkUtil）提升了代码复用性。这是一个大型功能回移植，使得旧版 Flink 用户也能使用动态多表写入能力。
