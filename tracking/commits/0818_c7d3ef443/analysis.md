# 提交 0818：Flink: Maintenance - MonitorSource

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 0818 |
| 完整哈希 | c7d3ef44367c46875f0367d312372fb2f246ae73 |
| 短哈希 | c7d3ef443 |
| 提交日期 | 2024-06-06 09:51:25 +0200 |
| 作者 | pvary <peter.vary.apache@gmail.com> |
| 提交说明 | Flink: Maintenance - MonitorSource (#10308) |
| PR/Issue | #10308 |

## 总体目的

该提交为 Iceberg 的 Flink 集成引入了 maintenance（维护）能力的第一个核心组件 —— `MonitorSource`。它是一个基于 Flink Source V2 API 的流式 Source，用于持续监控一张 Iceberg 表的变更，并把每次"自上次读取以来新增的提交"汇聚成一个 `TableChange` 事件向下游发射。这个 `TableChange` 描述了新增的数据文件数、删除文件数、数据/删除文件大小、以及提交次数，下游可据此触发诸如 manifest 重写、数据压缩、删除过期快照等维护动作。

这是 Iceberg Flink maintenance 机制的基础设施之一，旨在让用户用 Flink 流作业自动监听表变化并按需触发维护任务，而无需外部调度。本提交只引入 MonitorSource 本身及其测试工具链，下游的 Trigger/Action 等维护算子不在本提交范围内。

## 如何达成设计目的

整体设计基于 Flink 的 Source V2 API，并通过一个抽象基类 `SingleThreadedIteratorSource` 把"单线程、单 split、有状态的迭代器式 Source"这套通用骨架抽取出来，`MonitorSource` 继承它并只实现"如何产生迭代器"和"如何序列化迭代器状态"两个抽象方法。这种分层让后续其他维护类 Source 可以复用同一骨架。

**核心工作逻辑（`MonitorSource.TableChangeIterator`）**：
1. 初始化时记录 `lastSnapshotId`（恢复时从 checkpoint 反序列化得到，首次启动为 null），并加载 Iceberg `Table`。
2. `hasNext()` 永远返回 true（无界流）。
3. `next()`：先 `table.refresh()` 拉取最新元数据，获取当前快照 id `current`。然后从 `current` 沿 `parentId` 链向前回溯，直到遇到 `lastSnapshotId` 或回溯数超过 `maxReadBack`。回溯过程中：
   - 跳过 `DataOperations.REPLACE` 类型的快照（如 rewrite manifests / 重写文件，这些不应触发下游维护动作，否则会循环）。
   - 对其他快照，构造 `TableChange(snapshot, io)` 并 `merge` 累加到本次事件中。
   - 若历史链中某个快照已被 expire（`table.snapshot(checking)` 返回 null），则停止回溯。
4. 把 `lastSnapshotId` 更新为 `current`，返回累加后的 `TableChange`。异常时返回空事件而不是抛出，保证流不中断。

**限流逻辑**：`createReader` 中用 `rateLimiterStrategy.createRateLimiter(1)` 包裹 `RateLimitedSourceReader`，控制对表的轮询频率，避免高频轮询压垮元数据存储。并行度强制为 1（单线程读取，避免并发拉取同一表造成重复事件）。

**状态/容错**：`TableChangeIteratorSerializer` 序列化时只保存 `lastSnapshotId`（8 字节 long，-1 表示 null）。这样 savepoint/restart 后能从上次读到的快照继续，不会重复发射也不会漏读。

**`TableChange` 数据结构**：一个不可变值对象，包含 `dataFileNum/deleteFileNum/dataFileSize/deleteFileSize/commitNum` 五个聚合指标。`merge` 方法用于把多个相邻快照的变更累加成一次事件。实现了 `equals/hashCode/toString`，方便测试断言。

**测试基础设施**：本提交同时落地的测试工具链非常完整，包括：
- `OperatorTestBase`：用 `MiniClusterExtension` 启动 Flink mini cluster，`FlinkSqlExtension` 提供表环境，禁用 classloader 检查。
- `FlinkSqlExtension`：JUnit5 扩展，每个测试方法前重建 TableEnvironment、创建 catalog/database、提供 `exec()` 执行 SQL、`tableLoader()` 获取 TableLoader，测试后清理 warehouse。
- `CollectingSink<T>`：测试用 Sink，用 BlockingQueue 收集输出，支持 `poll(timeout)` 等待元素。
- `ManualSource<T>`：测试用手动注入数据的 Source，可发 record/watermark/finish 信号。
- `FlinkStreamingTestUtils`：`closeJobClient` 工具方法，支持 stop with savepoint 并等待作业终止。

`TestMonitorSource` 覆盖：迭代器直接读测试（带/不带 delete）、完整 Source 流式作业测试、状态恢复（savepoint/无 savepoint 对比）、非 1 并行度抛错、maxReadBack 限制、跳过 REPLACE 快照。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/MonitorSource.java`（新文件，206 行）

- **修改目的**：实现监控 Iceberg 表变更的 Flink Source。
- **工作逻辑**：
  - 继承 `SingleThreadedIteratorSource<TableChange>`，实现 `createIterator()` 返回 `TableChangeIterator`、`iteratorSerializer()` 返回 `TableChangeIteratorSerializer`。
  - `getBoundedness()` 返回 `CONTINUOUS_UNBOUNDED`，`getProducedType()` 返回 `TableChange.class` 的 TypeInformation。
  - `createReader` 用 `RateLimitedSourceReader` 包装父类 reader，按 `rateLimiterStrategy` 限流。
  - `TableChangeIterator`：核心回溯逻辑见上文。`hasNext()` 恒为 true，`next()` 回溯快照链并跳过 REPLACE 快照后返回聚合 `TableChange`，异常返回空事件。
  - `TableChangeIteratorSerializer`：序列化 `lastSnapshotId`（long，-1 代表 null），版本 1。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/SingleThreadedIteratorSource.java`（新文件，197 行）

- **修改目的**：抽象"单线程迭代器式 Source"通用骨架，供 MonitorSource 及未来其他维护 Source 复用。
- **工作逻辑**：
  - 实现 `Source<T, GlobalSplit<T>, Collection<GlobalSplit<T>>>` 和 `ResultTypeQueryable<T>`。
  - 强制并行度为 1（`createEnumerator`/`restoreEnumerator`/`createReader` 都校验 `currentParallelism()==1` 和 `indexOfSubtask()==0`，否则抛 `IllegalArgumentException("Parallelism should be set to 1")`）。
  - `GlobalSplit<T>` 实现 `IteratorSourceSplit`，固定 splitId="1"。
  - `SplitSerializer` 和 `EnumeratorSerializer` 委托子类提供的 `iteratorSerializer()` 序列化迭代器。`EnumeratorSerializer` 用首字节 0/1 标记空/非空 checkpoint。
  - 抽象方法 `createIterator()` 和 `iteratorSerializer()` 由子类实现。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableChange.java`（新文件，133 行）

- **修改目的**：定义表变更事件的数据结构。
- **工作逻辑**：
  - 字段：`dataFileNum/deleteFileNum/dataFileSize/deleteFileSize/commitNum`。
  - 构造器 `(Snapshot, FileIO)`：遍历 `snapshot.addedDataFiles(io)` 和 `addedDeleteFiles(io)` 累加文件数和大小，`commitNum=1`。
  - `empty()` 工厂返回全 0 事件。`merge(other)` 把另一事件的指标加到自己身上。`copy()`、`equals`、`hashCode`、`toString` 齐全。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/CollectingSink.java`（新文件，115 行）

- **修改目的**：测试用 Sink，收集 Source 输出供断言。
- **工作逻辑**：静态 `queues` 列表 + `numSinks` 计数器，每个实例分配一个 index 和对应 BlockingQueue。`poll(timeout)` 阻塞等待元素，超时抛 TimeoutException；`remainingOutput()`/`isEmpty()` 辅助查询。`CollectingWriter` 把元素 add 到对应队列。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/FlinkSqlExtension.java`（新文件，132 行）

- **修改目的**：JUnit5 扩展，封装 Flink SQL 表环境的搭建与清理。
- **工作逻辑**：`BeforeEachCallback`/`AfterEachCallback`。beforeEach 重建 TableEnvironment、创建 catalog/database 并切换；afterEach drop 所有表、切回 default catalog、drop catalog、删除 warehouse 临时目录。`exec()` 执行 SQL 并 collect 结果为 `List<Row>`；`tableLoader()` 返回指定表的 TableLoader。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/FlinkStreamingTestUtils.java`（新文件，73 行）

- **修改目的**：提供关闭 JobClient 的工具方法。
- **工作逻辑**：`closeJobClient(jobClient, savepointDir)` 支持两种模式 —— 有 savepointDir 时 `stopWithSavepoint` 并等待 savepoint 目录生成、把路径写入 Configuration 供恢复；无 savepointDir 时 `cancel()`。两种都 `Awaitility` 等待作业进入 terminal state。重载 `closeJobClient(jobClient)` 默认无 savepoint。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/ManualSource.java`（新文件，316 行）

- **修改目的**：测试用手动注入数据的 Source，用于后续需要可控输入流的算子测试。
- **工作逻辑**：静态 `queues`（ArrayDeque<Tuple2<?,Long>>）和 `availabilities`（CompletableFuture）列表按 index 分配。`sendRecord/sendWatermark/markFinished` 往队列塞元素并 complete availability future。`createReader` 返回匿名 SourceReader，`pollNext` 从队列取元素，空时返回 NOTHING_AVAILABLE 并注册新的 availability future。`DummySplit`/`DummyCheckpoint` 及其 NoOp 序列化器为占位实现，因为 split/checkpoint 实际不传递状态。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java`（新文件，51 行）

- **修改目的**：维护算子测试的基类，提供 MiniCluster 和 FlinkSqlExtension。
- **工作逻辑**：`@RegisterExtension` 注册 `MiniClusterExtension`（1 个 TaskManager、8 slots、禁用 classloader 检查）和 `FlinkSqlExtension`（hadoop 类型 catalog、数据库名 "db"、表名常量 `TABLE_NAME="test_table"`）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestMonitorSource.java`（新文件，362 行）

- **修改目的**：覆盖 MonitorSource 与迭代器的功能、容错、边界行为。
- **工作逻辑**：
  - `testChangeReaderIterator(withDelete)`：直接测迭代器，验证空表返回空事件、单次提交返回对应事件、连续调用不重复返回、多次提交一次拉取能合并。
  - `testSource`：完整流作业，插入数据后用 CollectingSink 收到正确 `TableChange(1, 0, size, 0L, 1)`。
  - `testStateRestore`：先跑作业生成 savepoint，再从 savepoint 恢复（验证不重复读历史），再不带 savepoint 重跑（验证能读到历史）。这是对 `lastSnapshotId` 序列化的核心验证。
  - `testNotOneParallelismThrows`：并行度设为 2 时作业抛 `IllegalArgumentException("Parallelism should be set to 1")`。
  - `testMaxReadBack`：maxReadBack=1 只读 1 个快照、=2 读 2 个、=MAX_VALUE 读全部 3 个。
  - `testSkipReplace`：构造 `RewriteFiles`（用文件替换自身产生 REPLACE 快照），验证迭代器返回空事件，确认 REPLACE 被跳过。

## 小结

- **成效**：为 Flink maintenance 机制奠定了第一块基石 —— 表变更监控 Source。设计上通过 `SingleThreadedIteratorSource` 抽象骨架 + `TableChange` 事件模型 + `lastSnapshotId` 状态序列化，实现了无界流、有状态、可容错、限流、单线程并行的表变更监听能力。配套测试工具链（MiniCluster、FlinkSqlExtension、CollectingSink、ManualSource）一次性落地，为后续维护算子提供了可复用的测试底座。
- **影响范围**：仅在 `flink/v1.19/` 模块下新增 9 个文件（3 个 main、6 个 test），共 1585 行新增，无对现有代码的修改。是纯增量特性，不破坏现有功能。属于 Flink maintenance 这一更大特性的第一步。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个独立的新特性模块，不依赖对 1.4.x 现有代码的修改，回迁相对独立。
  - 但需注意 1.4.x 对应的 Flink 版本：本提交针对 flink/v1.19，若 1.4.x 同时维护 v1.18/v1.20 等其他 Flink 版本目录，需要决定是否同步落到这些目录（本提交只落了 v1.19）。
  - 依赖 Flink Source V2 API、`RateLimiterStrategy`、`RateLimitedSourceReader`、`IteratorSourceEnumerator/IteratorSourceReader/IteratorSourceSplit` 等类，需确认 1.4.x 支持的 Flink 版本中这些 API 已存在且签名一致；若 1.4.x 的最低 Flink 版本较老，部分 API 可能不可用。
  - 该特性是后续一系列 maintenance PR（Trigger、Task、Action 等）的前置依赖，单独回迁 MonitorSource 而不回迁后续 PR 价值有限；建议作为 maintenance 特性整体的一部分评估回迁。
  - 测试中使用了 `MiniClusterExtension`（JUnit5）和 `awaitility`，需确认 1.4.x 的 flink v1.19 测试依赖中已包含相应库。
  - `ManualSource`/`CollectingSink` 等测试工具是本提交新引入的，后续 PR 可能复用，回迁时需保证这些工具类一并落地。
