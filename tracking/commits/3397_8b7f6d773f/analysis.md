# 提交 3397：Spark 4.1: New Async Spark Micro Batch Planner (#15299)

## 提交信息

- **序号**：3397 / 4088
- **哈希**：8b7f6d773f99d4b581f151efbd871c8965f2b3cb
- **短哈希**：8b7f6d773f
- **日期**：2026-03-16 07:54:58 -0700
- **作者**：Ruijing Li
- **提交说明**：Spark 4.1: New Async Spark Micro Batch Planner (#15299)
- **PR/Issue**：#15299

## 总体目的

为 Spark 4.1 的结构化流式读取引入异步微批处理规划器（Async Micro Batch Planner）。在原有的同步规划器中，每次微批处理的文件扫描任务规划都是在 Spark 驱动计划阶段同步完成的，导致微批之间存在空闲等待时间。新的异步规划器在后台线程预先获取文件扫描任务并填充队列，使当前微批处理与下一批的规划可以并行进行，从而减少规划延迟、提升流式查询吞吐量。

## 如何达成设计目的

1. 新建 `AsyncSparkMicroBatchPlanner` 类，继承 `BaseSparkMicroBatchPlanner`，通过后台 `ScheduledExecutorService` 定期刷新表元数据和填充任务队列
2. 使用 `LinkedBlockingQueue` 缓存预取的 `FileScanTask` + `StreamingOffset` 对，实现生产者-消费者模式
3. 在 `SparkMicroBatchStream` 中根据配置选择使用同步或异步规划器
4. 新增多个配置项控制异步规划行为：开关、轮询间隔、预加载限制
5. 增强 `BaseSparkMicroBatchPlanner.nextValidSnapshot` 以支持 null 输入（异步场景需要从起始偏移量查找）
6. 在 `SyncSparkMicroBatchPlanner` 中增加对单文件超过行数限制的警告日志
7. 参数化测试以同时覆盖同步和异步两种模式

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/AsyncSparkMicroBatchPlanner.java` (+527 lines, 新文件)

**修改目的**：实现异步微批处理规划器核心逻辑。

**工作逻辑**：
- 构造时同步填充初始队列以满足最小约束，然后启动两个后台定时任务：一个定期刷新表元数据检测新快照，另一个定期填充队列
- 使用 Caffeine 缓存 `planFiles` 结果，处理 Spark 可能多次调用同一偏移量范围的情况
- `planFiles` 方法从队列中消费任务直到到达 endOffset，使用轮询+超时机制避免忙等
- `latestOffset` 方法查看队列内容计算下一个微批的结束偏移量，支持 `ReadAllAvailable`（Trigger.AvailableNow）和有限制两种模式
- 行限制为软限制：先包含文件再检查，保证前向进度；单文件超过限制时会发出警告
- 后台任务异常被捕获并存储在 volatile 字段中，在下次 `latestOffset` 调用时抛出

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkMicroBatchPlanner.java` (+20/-7 lines)

**修改目的**：增强 `nextValidSnapshot` 方法以支持 null 输入，适配异步规划器场景。

**工作逻辑**：
- 当 `curSnapshot` 为 null 时，通过 `MicroBatchUtils.determineStartingOffset` 查找起始快照
- 当当前快照已等于表最新快照时返回 null（表示无更多数据）
- 原有逻辑仅支持非 null 输入并直接调用 `SnapshotUtil.snapshotAfter`

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+11/-2 lines)

**修改目的**：根据配置在同步和异步规划器之间切换。

**工作逻辑**：
- `initializePlanner` 方法检查 `asyncMicroBatchPlanningEnabled()` 配置
- 若启用异步，创建 `AsyncSparkMicroBatchPlanner` 并传入 startOffset、endOffset 等参数
- 否则使用原有的 `SyncSparkMicroBatchPlanner`

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SyncSparkMicroBatchPlanner.java` (+9 lines)

**修改目的**：在同步规划器中增加对单文件超过行数限制的警告日志，与异步规划器保持一致。

**工作逻辑**：
- 当 `curFilesAdded == 1` 且 `curRecordCount > maxRows` 时，记录警告日志说明该文件超过 `maxRecordsPerMicroBatch` 限制，将被完整处理以保证前向进度

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+33 lines)

**修改目的**：新增四个配置读取方法。

**工作逻辑**：
- `asyncMicroBatchPlanningEnabled()`：读取异步规划开关，默认 false
- `streamingSnapshotPollingIntervalMs()`：读取快照轮询间隔，默认 30000ms
- `asyncQueuePreloadFileLimit()`：读取初始预加载文件数限制，默认 100
- `asyncQueuePreloadRowLimit()`：读取初始预加载行数限制，默认 100000

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadOptions.java` (+13 lines)

**修改目的**：定义新的读选项常量和默认值。

**工作逻辑**：
- `ASYNC_MICRO_BATCH_PLANNING_ENABLED` = "async-micro-batch-planning-enabled"
- `STREAMING_SNAPSHOT_POLLING_INTERVAL_MS` 及默认值 30000L
- `ASYNC_QUEUE_PRELOAD_FILE_LIMIT` 及默认值 100L
- `ASYNC_QUEUE_PRELOAD_ROW_LIMIT` 及默认值 100000L

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+5 lines)

**修改目的**：定义会话级 SQL 属性常量。

**工作逻辑**：
- `ASYNC_MICRO_BATCH_PLANNING_ENABLED` = "spark.sql.iceberg.async-micro-batch-planning-enabled"
- 默认值为 false

### `docs/docs/spark-configuration.md` (+5 lines)

**修改目的**：文档化新增的配置项。

**工作逻辑**：
- 在会话配置表中添加异步规划开关说明
- 在读选项表中添加四个新选项及其默认值和说明

### `docs/docs/spark-structured-streaming.md` (+7 lines)

**修改目的**：添加异步微批处理规划的使用说明。

**工作逻辑**：
- 说明启用方式及其带来的吞吐量提升和权衡（更高内存使用和快照检测延迟）
- 指引用户参考 spark-configuration 文档了解额外选项

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (+126/-6 lines)

**修改目的**：参数化测试以同时覆盖同步和异步两种模式，并新增异步专有测试。

**工作逻辑**：
- 扩展 `@Parameters` 矩阵，为每种 catalog（HIVE、HADOOP、REST、SPARK_SESSION）增加 async=true/false 两种变体
- 新增 `@Parameter(index = 3)` 注入 async 标志
- `startStream` 方法根据 async 标志自动设置 `ASYNC_MICRO_BATCH_PLANNING_ENABLED` 和轮询间隔
- 新增 `testReadStreamWithLowAsyncQueuePreload` 测试低预加载限制下后台线程能正确加载剩余数据
- 在 `testReadStreamFromTimestamp` 中添加 `Thread.sleep(50)` 等待异步后台线程刷新

## 总结

本提交为 Spark 4.1 结构化流式读取引入了异步微批处理规划器，通过后台线程预取文件扫描任务到队列，实现当前微批处理与下一批规划的并行化，减少微批间空闲时间。新增了4个配置项控制异步行为，默认关闭以保证向后兼容。测试矩阵扩展为同步/异步双模式覆盖，确保两种路径的正确性。
