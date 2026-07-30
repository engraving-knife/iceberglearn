# 提交 3615：Spark 3.5: Backport Async Micro Batch Planner to 3.5 (#15992)

## 提交信息

- **序号**：3615 / 4088
- **哈希**：f0ba022fc34ef60bbb9199ef27d29c5f34543bf2
- **短哈希**：f0ba022fc
- **日期**：2026-04-29 07:43:50 -0700
- **作者**：Ruijing Li
- **提交说明**：Spark 3.5: Backport Async Micro Batch Planner to 3.5 (#15992)
- **PR/Issue**：#15992

## 总体目的

这个提交将异步 Micro Batch Planner 功能反向移植到 Spark 3.5 版本。

Iceberg 的 Spark 结构化流式读取通过 Micro Batch 机制工作，之前的实现中，micro batch 的规划（planning）是同步的，即在每次 micro batch 触发时，需要同步扫描表快照、规划文件任务，这会成为流式处理的性能瓶颈，特别是对于大表或有大量文件的表。

异步 Micro Batch Planner 将规划逻辑放到后台线程中执行，可以提前规划下一个 micro batch，从而减少流式处理的延迟。同时还引入了可配置的轮询间隔和队列预加载限制，使流式读取的性能可调。

## 如何达成设计目的

1. 将原有的 `SparkMicroBatchStream` 中的规划逻辑提取到独立的 Planner 类层次中：
   - `BaseSparkMicroBatchPlanner`：基础规划器，包含共享的规划逻辑。
   - `SyncSparkMicroBatchPlanner`：同步规划器，保持原有行为。
   - `AsyncSparkMicroBatchPlanner`：异步规划器，在后台线程中提前规划。
   - `SparkMicroBatchPlanner`：规划器接口。
   - `MicroBatchUtils`：工具类。

2. 在 `SparkMicroBatchStream` 中根据配置选择使用同步或异步规划器。

3. 新增配置选项控制异步规划行为：
   - `async-micro-batch-planning-enabled`：是否启用异步规划（默认 false）。
   - `streaming-snapshot-polling-interval-ms`：快照轮询间隔（默认 30000ms）。
   - `async-queue-preload-file-limit`：队列预加载文件限制（默认 100）。
   - `async-queue-preload-row-limit`：队列预加载行数限制（默认 100000）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+33/-0 lines)

**修改目的**：新增异步 micro batch 规划的配置读取方法。

**工作逻辑**：
新增 4 个配置读取方法：
- `asyncMicroBatchPlanningEnabled()`：读取是否启用异步规划。
- `streamingSnapshotPollingIntervalMs()`：读取快照轮询间隔。
- `asyncQueuePreloadFileLimit()`：读取队列预加载文件限制。
- `asyncQueuePreloadRowLimit()`：读取队列预加载行数限制。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadOptions.java` (+15/-0 lines)

**修改目的**：定义异步规划相关的读取选项常量。

**工作逻辑**：
定义了 4 个配置选项的键名和默认值，如 `ASYNC_MICRO_BATCH_PLANNING_ENABLED`、`STREAMING_SNAPSHOT_POLLING_INTERVAL_MS` 等。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+5/-0 lines)

**修改目的**：定义 session 级别的 SQL 属性。

**工作逻辑**：
新增 `ASYNC_MICRO_BATCH_PLANNING_ENABLED` 属性及其默认值（false）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/AsyncSparkMicroBatchPlanner.java` (+543 lines, new file)

**修改目的**：实现异步 micro batch 规划器。

**工作逻辑**：
在后台线程中异步规划 micro batch，使用队列预加载机制提前规划后续 batch。包含快照轮询、文件扫描、任务拆分等逻辑，通过配置参数控制预加载行为。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkMicroBatchPlanner.java` (+151 lines, new file)

**修改目的**：提取共享的 micro batch 规划逻辑。

**工作逻辑**：
包含同步和异步规划器共享的基础逻辑，如快扫描、micro batch 生成等。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SyncSparkMicroBatchPlanner.java` (+249 lines, new file)

**修改目的**：实现同步 micro batch 规划器。

**工作逻辑**：
保持原有的同步规划行为，每次 micro batch 触发时同步规划。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchPlanner.java` (+47 lines, new file)

**修改目的**：定义规划器接口。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/MicroBatchUtils.java` (+69 lines, new file)

**修改目的**：micro batch 工具类。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+25/-303 lines)

**修改目的**：重构为使用新的规划器架构。

**工作逻辑**：
将原有的规划逻辑从 `SparkMicroBatchStream` 中提取到独立的 Planner 类中。`SparkMicroBatchStream` 现在根据配置选择使用 `SyncSparkMicroBatchPlanner` 或 `AsyncSparkMicroBatchPlanner`。大量规划相关的代码（约 303 行）被移除，由新的 Planner 类替代。

### 测试文件

- `TestAsyncSparkMicroBatchPlanner.java` (+61 lines, new)：异步规划器测试。
- `TestMicroBatchPlanningUtils.java` (+100 lines, new)：工具类测试。
- `TestStructuredStreamingRead3.java` (+283/-0 lines)：扩展流式读取测试。

## 总结

这个提交将异步 Micro Batch Planner 功能反向移植到 Spark 3.5，通过将规划逻辑放到后台线程执行，可以显著减少流式处理的延迟。重构将规划逻辑从 Stream 类中提取到独立的 Planner 类层次中，代码结构更清晰。新增的配置选项使流式读取性能可调。默认不启用异步规划，保持向后兼容。
