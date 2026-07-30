# 提交 1102：Flink: infer source parallelism for FLIP-27 source in batch execution mode (#10832)

## 提交信息

- **序号**：1102 / 4088
- **哈希**：2ed61a12bf6fd1582a868a02326e4ad745dfb177
- **短哈希**：2ed61a12b
- **日期**：2024-08-26（Mon Aug 26 08:01:13 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: infer source parallelism for FLIP-27 source in batch execution mode (#10832)
- **PR/Issue**：#10832
- **影响模块**：flink v1.20 source

## 总体目的

Iceberg 的 Flink 集成在 1.20 版本提供了基于 FLIP-27 的现代 `IcebergSource` 实现，但该 source 在批执行模式下并不会根据实际扫描得到的 split 数量自动推断并行度——用户必须显式设置并行度，否则会沿用 Flink 的默认全局并行度。这在小表场景下容易出现"并行度过高、空跑任务多"的浪费，而在大表场景下又可能因为默认并行度太低而无法充分利用集群资源。

本提交为 FLIP-27 版本的 `IcebergSource` 引入"按 split 数量推断并行度"的能力：

1. 在 `IcebergSource.Builder` 中新增 `buildStream(StreamExecutionEnvironment)` 方法，构建 source 的同时把它注册到 `StreamExecutionEnvironment`，并在批模式下调用 `inferParallelism` 设置并行度。
2. 复用已有的 `SourceUtil.inferParallelism` 工具方法，根据 `TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM`（开关）与 `TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM_MAX`（上限）做推断。
3. 在 `IcebergTableSource`（SQL 入口）中改用新的 `buildStream`，让 SQL 作业也能享受推断并行度。
4. 为推断逻辑引入 split 缓存，避免"推断并行度"和"枚举器创建"两条路径重复扫描表，造成双倍 plan 开销。

## 如何达成设计目的

整体思路是：把"构建 Source + 注册到 env + 设置并行度"这三步聚合成一个新的 Builder API `buildStream(env)`，内部按以下流程工作：

1. 调用 `build()` 构造 `IcebergSource` 实例。
2. 推断输出 `TypeInformation`（RowData 路径用 schema 转换；带 `RowDataConverter` 路径用 `converter.getProducedType()`）。
3. 调用 `env.fromSource(source, WatermarkStrategy.noWatermarks(), source.name(), outputTypeInfo)` 把 source 加入流图。
4. 如果是批模式（`!scanContext.isStreaming()`），调用 `inferParallelism(flinkConfig, env)`，调用 `SourceUtil.inferParallelism`：
   - 读取默认并行度 `TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM`；
   - 若开关打开，调用 `splitCountProvider.getAsInt()`，触发一次 split 规划，得到 split 数；
   - 与 `maxInferParallelism` 取小；
   - 若有 `limit`，再与 limit 取小；
   - 最后与 `env.getMaxParallelism()` 取小（不能超过作业最大并行度），并保证至少为 1。

为了避免"推断并行度"和后续 `createEnumerator` 中再次 `planSplitsForBatch` 重复扫描表，引入 `volatile List<IcebergSourceSplit> batchSplits` 字段缓存第一次的规划结果，第二次直接复用；并在 `createEnumerator` 末尾清空缓存，避免持有大对象。

线程安全方面，作者特意用 `volatile` 保护 `batchSplits`，因为这两次调用来自不同线程：构建流图在 main 线程，而 enumerator 创建在 Flink 作业提交时的另一线程。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：为 FLIP-27 source 增加批模式下推断并行度的能力，并提供 `buildStream` 入口。

**工作逻辑**：

- 新增 import：`WatermarkStrategy`、`TypeInformation`、`DataStream`、`DataStreamSource`、`StreamExecutionEnvironment`、`FlinkCompatibilityUtil`。
- 新增字段 `private volatile List<IcebergSourceSplit> batchSplits;` 用于跨线程缓存批模式 split 规划结果，注释明确说明两次调用来自两个不同线程，所以需要 `volatile`。
- 改造 `planSplitsForBatch(String threadName)`：进入时先检查 `batchSplits != null`，若已有缓存直接返回，避免重复扫描；扫描结果赋给 `this.batchSplits` 再返回。
- 在 `createEnumerator` 中：调用 `planSplitsForBatch` 后通过 `this.batchSplits = null` 主动清空缓存，因为枚举器创建后不再需要，避免长期持有大对象。
- 新增 `shouldInferParallelism()`：返回 `!scanContext.isStreaming()`，即只在批模式推断，流式不推断（流式无界无法预估）。
- 新增 `inferParallelism(ReadableConfig flinkConf, StreamExecutionEnvironment env)`：调用 `SourceUtil.inferParallelism`，传入 `flinkConf`、`scanContext.limit()` 和一个 lambda（内部再次调用 `planSplitsForBatch` 取 split 数）；并与 `env.getMaxParallelism()` 取小。
- 在 Builder 中新增 `public DataStream<T> buildStream(StreamExecutionEnvironment env)`：
  - 校验 `readerFunction == null`，因为 `buildStream` 只支持 RowData 或 Converter 路径，不支持自定义 reader function；
  - 构造 `IcebergSource<T> source = build()`；
  - 通过 `outputTypeInfo(converter, table.schema(), source.scanContext.project())` 推断输出类型：converter 不为空用 `converter.getProducedType()`，否则用 `FlinkCompatibilityUtil.toTypeInfo(FlinkSchemaUtil.convert(readSchema))`；
  - 调 `env.fromSource(source, WatermarkStrategy.noWatermarks(), source.name(), outputTypeInfo)`；
  - 批模式下 `stream.setParallelism(source.inferParallelism(flinkConfig, env))`。
- 新增私有静态方法 `outputTypeInfo(...)` 用于上面的类型推断。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/IcebergTableSource.java`

**修改目的**：让 SQL 入口也享受推断并行度。

**工作逻辑**：

- 删除原本在 `createFLIP27Stream` 中直接 `IcebergSource.forRowData()....build()` + `env.fromSource(...)` 的样板代码。
- 改为链式调用 `.buildStream(env)`，让 `IcebergSource.Builder` 内部统一处理 fromSource + 推断并行度。
- 方法返回类型由 `DataStreamSource<RowData>` 改为 `DataStream<RowData>`（因为 `buildStream` 返回 `DataStream<T>`）。
- 同步移除不再用到的 import：`WatermarkStrategy`、`TypeInformation`、`DataStreamSource`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceInferParallelism.java`（新增）

**修改目的**：新增专门针对推断并行度行为的端到端测试。

**工作逻辑**：

- 用 `MiniClusterExtension` 起 2 个 TM × 2 slot = 4 并行度的小集群；`MAX_INFERRED_PARALLELISM = 3`。
- 测试用例：
  - `testEmptyTable`：空表也要至少 1 个并行度，期望 1。
  - `testTableWithFilesLessThanMaxInferredParallelism`：写 2 个文件（`splitSize(1L)` 强制一文件一片），期望并行度 2。
  - `testTableWithFilesMoreThanMaxInferredParallelism`：写 `MAX_INFERRED_PARALLELISM + 1 = 4` 个文件，期望并行度被 cap 到 3。
- 验证手段：用 `DataStream.Collector` 收集结果校验记录数；通过反射拿 `MiniClusterExtension` 内部的 `MiniCluster`，再 `getExecutionGraph(jobID)` 拿到 source 顶点的实际并行度做断言（注释说明这是借鉴 Flink `FileSourceTextLinesITCase` 的方法，因为公开 API 没有）。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBounded.java`

**修改目的**：把原本手写 `env.fromSource(...)` 改为 `sourceBuilder.buildStream(env)`，统一入口。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceBoundedSql.java`

**修改目的**：把 `tableConf.setBoolean(key, true)` 改为 `tableConf.set(ConfigOption, true)`，用类型安全的 setter。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceSql.java`

**修改目的**：除改用类型安全 setter 外，**显式关闭推断并行度**（`TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM = false`）。原因是这些测试用 watermark 列校验 split 分配顺序，依赖默认并行度 1 的单 reader task 来比对读取顺序；若开启推断并行度把并行度抬上去，顺序就乱了。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSpeculativeExecutionSupport.java`

**修改目的**：保证推测执行测试在新并行度推断下仍稳定。

**工作逻辑**：

- 加 `@Timeout(value = 60)` 注释说明：测试里有 `Thread.sleep(Integer.MAX_VALUE)`，万一异常会一直挂着，加 60 秒超时兜底。
- `TestingMap.map` 改为只让 **subtask 0 + attempt 0** 永久睡眠触发推测执行，原来是所有 attempt 0 都睡眠。这样配合默认并行度推断/推测执行配置更稳定。
- 显式关闭 `TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM`，避免并行度变化影响推测执行逻辑。

## 小结

- **成效**：FLIP-27 `IcebergSource` 现在在批模式下可以按 split 数量自动推断并行度，且与 Flink 全局 `maxParallelism` / Iceberg 的 `inferSourceParallelismMax` / `limit` 共同约束；SQL 和 DataStream API 入口都生效。通过 `volatile` 缓存避免了 plan 两次的开销。
- **影响范围**：仅 flink v1.20 模块的 source 子模块；不改 Iceberg core，也不影响流式 source 行为。默认开关沿用 `TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM`，未开启时行为与原来一致。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 维护分支的 Flink 集成版本通常较低（1.4.x 对应的 Flink 1.17/1.18），不一定有 FLIP-27 `IcebergSource` 类。本提交依赖的 `IcebergSource`、`SourceUtil.inferParallelism`、`FlinkConfigOptions.TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM` 都需要先确认在 1.4.x 是否存在。
  - 即使存在，回迁时还需同步把对应版本的 `IcebergTableSource`、`TestIcebergSourceSql`、`TestIcebergSpeculativeExecutionSupport` 等改动一并 cherry-pick，确保测试稳定。
  - 若 1.4.x 不包含 FLIP-27 source 实现，**不建议回迁**；若包含，可按本 PR 路径回迁，但需要确认 1.4.x 的 `FlinkConfigOptions` 已有 `TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM` 与 `MAX` 两个选项。
  - 注意 `buildStream` 是 `Experimental` API，回迁会引入新的公共 API，需要在 1.4.x 的 release notes 中声明。
