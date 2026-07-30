# 提交 3247：Spark 4.1: Refactor SparkMicroBatchStream to SyncPlanner (#15298)

## 提交信息

- **序号**：3247 / 4088
- **哈希**：b6de7acdb23704ef5165fb674d6814060f60e754
- **短哈希**：b6de7acdb
- **日期**：2026-02-14
- **作者**：Ruijing Li
- **提交说明**：Spark 4.1: Refactor SparkMicroBatchStream to SyncPlanner (#15298)
- **PR/Issue**：#15298

## 总体目的

在 Spark 4.1 模块中，`SparkMicroBatchStream` 是 Iceberg 结构化流式读取的核心类，实现了 Spark 的 `MicroBatchStream` 和 `SupportsTriggerAvailableNow` 接口。重构前，该类是一个"上帝类"，既承担流式协调职责（管理初始偏移量、提交、生命周期），又包含全部微批规划逻辑——包括 `planFiles`（根据起止偏移量生成文件扫描任务）、`latestOffset`（在 ReadLimit 约束下计算最新可推进偏移量）、`shouldProcess`（判断快照操作是否可处理）、`determineStartingOffset`（根据时间戳确定起始偏移量）、`nextValidSnapshot`（跳过 rewrite/delete 快照）、`addedFilesCount`（统计快照新增文件数）以及 `getMaxFiles`/`getMaxRows`（解析 ReadLimit）等大量方法。这些规划逻辑与流式协调逻辑耦合在一起，导致类体量庞大（约 560 行）、难以维护和测试。

本次重构的核心动机是将微批规划逻辑从 `SparkMicroBatchStream` 中抽离为独立的 Planner 组件体系，引入 `SparkMicroBatchPlanner` 接口和 `SyncSparkMicroBatchPlanner` 实现类。从命名中的 "Sync"（同步）可以推断，这是为未来引入异步规划器（Async Planner）做准备——Spark 4.1 的流式 API 演进可能需要支持异步的 `latestOffset` 计算，使得规划器可以与流式协调器解耦演进。同时，将公共工具方法提取到 `MicroBatchUtils` 和 `BaseSparkMicroBatchPlanner` 中，使它们可被复用且能独立单元测试。

## 如何达成设计目的

整体采用"提取接口 + 抽象基类 + 具体实现"的经典重构模式，同时辅以工具类抽取。新建四个文件：`SparkMicroBatchPlanner`（接口）定义三个核心方法 `planFiles`、`latestOffset`、`stop`；`BaseSparkMicroBatchPlanner`（抽象基类）承载所有规划器的公共逻辑；`SyncSparkMicroBatchPlanner`（具体实现）包含同步规划的全部细节；`MicroBatchUtils`（工具类）封装无状态的偏移量和文件计数工具方法。`SparkMicroBatchStream` 改为持有 `SparkMicroBatchPlanner` 引用并委托调用，同时新增 `TestMicroBatchPlanningUtils` 测试类覆盖提取出的工具方法。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchPlanner.java` (+47 lines，新增文件)

**修改目的**：定义微批规划器的统一接口，为同步/异步实现提供抽象契约。

**工作逻辑**：
该接口声明了三个方法：`planFiles(StreamingOffset startOffset, StreamingOffset endOffset)` 返回两个偏移量之间的 `List<FileScanTask>`；`latestOffset(StreamingOffset startOffset, ReadLimit limit)` 在给定读取限制下返回流可推进到的最新偏移量（无新数据时返回 null）；`stop()` 释放资源。接口使 `SparkMicroBatchStream` 不再依赖具体实现，为后续替换不同规划策略（如异步规划）提供扩展点。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkMicroBatchPlanner.java` (+137 lines，新增文件)

**修改目的**：为所有微批规划器提供公共基类，封装快照过滤和 ReadLimit 解析等共享逻辑。

**工作逻辑**：
该抽象类实现 `SparkMicroBatchPlanner` 接口，持有 `Table` 和 `SparkReadConf` 引用。核心方法 `shouldProcess(Snapshot)` 根据快照操作类型（`append`/`replace`/`delete`/`overwrite`）决定是否处理：append 返回 true，replace 返回 false，delete 和 overwrite 分别检查 `streamingSkipDeleteSnapshots`/`streamingSkipOverwriteSnapshots` 配置，未启用时抛出 `Preconditions.checkState` 异常并提示用户设置对应参数。`nextValidSnapshot(Snapshot)` 通过 `SnapshotUtil.snapshotAfter` 逐步前进，跳过不应处理的快照，到达当前快照时返回 null。

内部类 `UnpackedLimits` 是本次重构对原有 `getMaxFiles`/`getMaxRows` 的改进合并：构造时接收 `ReadLimit`，支持 `CompositeReadLimit`（组合限制）、`ReadMaxRows`、`ReadMaxFiles` 三种类型，对组合限制中的多个同类约束取最小值（`Math.min`），最终暴露 `getMaxRows()` 和 `getMaxFiles()`。相比原先两个独立的 `getMaxFiles`/`getMaxRows` 方法每次都遍历 ReadLimit，`UnpackedLimits` 在构造时一次性解析，避免了 `latestOffset` 内循环中的重复解析开销。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/MicroBatchUtils.java` (+67 lines，新增文件)

**修改目的**：提取无状态的偏移量计算和文件计数工具方法，便于复用和测试。

**工作逻辑**：
`determineStartingOffset(Table table, long fromTimestamp)` 根据起始时间戳确定流的起始偏移量：表无快照时返回 `START_OFFSET`；`fromTimestamp == Long.MIN_VALUE`（默认值）时从最古老祖先快照开始，避免循环查找；当前快照时间戳早于 fromTimestamp 时返回 `START_OFFSET`；否则通过 `SnapshotUtil.oldestAncestorAfter` 查找时间戳之后的最早祖先，查找失败时回退到最古老祖先。相比原实现将 `fromTimestamp` 设为 `Long` 类型并判 null，这里改为 `long` 基本类型并用 `Long.MIN_VALUE` 哨兵值，语义更清晰。

`addedFilesCount(Table table, Snapshot snapshot)` 优先从快照摘要 `SnapshotSummary.ADDED_FILES_PROP` 读取新增文件数，摘要缺失时回退到遍历 `snapshot.addedDataFiles(table.io())` 计数。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SyncSparkMicroBatchPlanner.java` (+240 lines，新增文件)

**修改目的**：提供同步微批规划器的完整实现，承接从 `SparkMicroBatchStream` 抽离的全部规划逻辑。

**工作逻辑**：
继承 `BaseSparkMicroBatchPlanner`，构造时接收 `Table`、`SparkReadConf` 和 `lastOffsetForTriggerAvailableNow`（用于 `Trigger.AvailableNow` 场景预计算的偏移上限）。

`planFiles` 方法在 `[startOffset, endOffset)` 区间内逐快照生成 `FileScanTask`：若起始偏移为 `START_OFFSET` 则通过 `MicroBatchUtils.determineStartingOffset` 确定；循环中通过 `SnapshotUtil.snapshotAfter` 推进当前偏移，对每个快照调用 `shouldProcess` 过滤，再通过 `MicroBatches.from(...).generate(...)` 生成微批任务，直到到达 endOffset。

`latestOffset` 方法在 ReadLimit 约束下计算可推进的最新偏移量：先通过 `UnpackedLimits` 一次性解析限制，然后从起始偏移开始遍历快照，使用 `MicroBatches.skippedManifestIndexesFromSnapshot` 生成清单索引，逐文件累加 `curFilesAdded` 和 `curRecordCount`，超过 `maxFiles` 或 `maxRows` 时停止。该方法标注了 `@SuppressWarnings("checkstyle:CyclomaticComplexity")`，保留了原有复杂度。当 `Trigger.AvailableNow` 启用时使用预计算的 `lastOffsetForTriggerAvailableNow.snapshotId()` 作为上限。最终若新偏移量与起始偏移相同则返回 null（表示无新数据）。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+347/-319 lines 净变化)

**修改目的**：将规划逻辑委托给独立的 Planner 组件，使自身聚焦于流式协调职责。

**工作逻辑**：
该文件删除了约 330 行规划相关代码（`planFiles`、`shouldProcess`、`determineStartingOffset`、`nextValidSnapshot`、`addedFilesCount`、`getMaxFiles`、`getMaxRows`、`validateCurrentSnapshotExists` 及 `latestOffset` 的全部实现体），新增 `SparkMicroBatchPlanner planner` 字段和 `readConf` 字段。`planInputPartitions` 方法改为懒初始化规划器后委托 `planner.planFiles(startOffset, endOffset)`。`latestOffset` 方法同样懒初始化规划器后委托 `planner.latestOffset(...)`。`stop()` 方法改为调用 `planner.stop()`。`prepareCommit`/`Trigger.AvailableNow` 的 `commit` 回调中，设置 `lastOffsetForTriggerAvailableNow` 后会调用 `planner.stop()` 并置 null，确保下次调用时以新的偏移上限重建规划器。`initialOffset` 的计算改为调用 `MicroBatchUtils.determineStartingOffset`。原先的 `skipDelete`/`skipOverwrite` 字段被移除，因为 `shouldProcess` 逻辑已移至 `BaseSparkMicroBatchPlanner`，后者通过 `readConf()` 直接读取这些配置。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestMicroBatchPlanningUtils.java` (+100 lines，新增文件)

**修改目的**：为提取出的工具方法提供独立单元测试覆盖。

**工作逻辑**：
继承 `CatalogTestBase`，使用参数化测试扩展。包含三个测试：`testUnpackedLimitsCompositeChoosesMinimum` 验证 `UnpackedLimits` 对组合限制中的多个 `maxRows` 和 `maxFiles` 取最小值；`testDetermineStartingOffsetWithTimestampBetweenSnapshots` 验证当时间戳落在两个快照之间时，`determineStartingOffset` 正确返回后一个快照；`testAddedFilesCountUsesSummaryWhenPresent` 验证 `addedFilesCount` 优先使用快照摘要中的文件计数。这些测试此前无法独立编写，因为逻辑内嵌在 `SparkMicroBatchStream` 中。

## 总结

本次重构将 Spark 4.1 流式读取中 `SparkMicroBatchStream` 的微批规划逻辑系统性抽离为 `SparkMicroBatchPlanner` 接口体系（`BaseSparkMicroBatchPlanner` + `SyncSparkMicroBatchPlanner`）和 `MicroBatchUtils` 工具类，使流式协调与规划职责分离。这不仅降低了单类复杂度、提升了可测试性（新增针对性单元测试），更通过 "Sync" 命名为未来异步规划器的引入预留了扩展点，是 Iceberg 流式引擎架构演进的重要一步。
