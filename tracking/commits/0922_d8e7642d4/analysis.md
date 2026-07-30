# 提交 0922：Kafka Connect: Commit coordination (#10351)

## 提交信息

- **序号**：0922 / 4088
- **哈希**：d8e7642d4e61b6d442c38b2cab1f631864c4e368
- **短哈希**：d8e7642d4
- **日期**：2024-07-11（Thu Jul 11 08:22:20 2024 -0700）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: Commit coordination
- **PR/Issue**：#10351

## 总体目的

本提交为 Iceberg 的 Kafka Connect Sink 连接器引入"提交协调"（commit coordination）机制。在此之前，`IcebergSinkConnector` 的 `taskClass()` 还返回 `null`，并且带着 `FIXME: update this when the connector channel is added` 注释，说明连接器尚未真正具备运行 Sink 任务的能力。本提交补齐这一关键缺口，使连接器能够接收 Kafka 数据、写入 Iceberg 表，并通过协调器在多个 Sink 任务之间统一管理提交。

引入协调机制的动机是：Kafka Connect 的并行 Sink 任务各自只看到部分分区数据，但 Iceberg 表的提交通常需要将这批数据文件原子地附加到目标表快照。如果每个任务各自提交，会出现快照碎片化、重复提交、offset 与 Iceberg 快照不一致等问题。因此需要一个 leader 任务扮演"协调器"，收集所有 worker 的写入结果，再统一对每张表做一次 `AppendFiles`/`RowDelta` 提交，并把 Kafka 的消费 offset 作为快照属性保存，从而保证"已提交的 Iceberg 快照对应的 Kafka offset"是确定的、可恢复的。

整体来看，本提交是 Kafka Connect 模块从"骨架阶段"走向"可运行阶段"的核心改动，定义了控制通道、事件协议、leader 选举、worker 写入、协调器提交、超时与恢复等完整链路。

## 如何达成设计目的

设计上引入了一个独立的 `channel` 包，承载基于 Kafka control topic 的事件通信；以及一个 `Committer` 抽象（接口 + `CommitterImpl`），由 `IcebergSinkTask` 持有。每个 Sink 任务启动时都会创建一个 `Worker`，负责消费 Kafka 数据并通过 `SinkWriter`/`IcebergWriter` 写出 Iceberg 数据文件；同时其中一个被选为 leader 的任务额外启动一个 `Coordinator`（在独立线程 `CoordinatorThread` 中运行），负责发起提交周期、收集 worker 回报的 `DATA_WRITTEN`/`DATA_COMPLETE` 事件、按表聚合文件后调用 Iceberg 的 `AppendFiles`/`RowDelta` 完成提交，并把控制 topic 的消费 offset 写入快照 summary 以便恢复时去重。leader 选举基于"持有最小 TopicPartition 的任务"，简单且确定。

通信使用 Kafka 事务型 producer + `read_committed` 隔离级别的 consumer，保证事件要么全看到要么看不到；事件按 producer ID 分区以保持顺序。整体形成"worker 写数据→coordinator 发起提交→worker 回报数据完成→coordinator 收齐后提交 Iceberg 表→广播 CommitComplete"的闭环。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConnector.java`

**修改目的**：让 SinkConnector 真正返回 SinkTask 实现类，使连接器可以运行。

**工作逻辑**：删除 `taskClass()` 中 `return null;` 及 FIXME 注释，改为 `return IcebergSinkTask.class;`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkTask.java`（新增）

**修改目的**：实现 Kafka Connect `SinkTask` 接口，作为每个 Sink 任务入口，持有 catalog 与 committer。

**工作逻辑**：`start()` 读取配置；`open()` 通过 `CatalogUtils.loadCatalog` 加载 catalog，并通过 `CommitterFactory.createCommitter` 创建 `CommitterImpl` 并启动；`put()` 调用 `committer.save(sinkRecords)`；`flush()` 调用 `committer.save(null)`；`preCommit()` 返回空 map（offset 由 worker 通过事务提交，不依赖 Kafka Connect 框架的 offset 机制）；`close()`/`stop()` 关闭 committer 与 catalog（若 catalog 实现了 `AutoCloseable`）。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/Committer.java`（新增）

**修改目的**：定义提交器接口。

**工作逻辑**：接口含 `start(Catalog, IcebergSinkConfig, SinkTaskContext)`、`save(Collection<SinkRecord>)`、`stop()` 三个方法。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/CommitterFactory.java`（新增）

**修改目的**：工厂类用于解耦创建 `CommitterImpl`。

**工作逻辑**：`createCommitter(config)` 直接 `return new CommitterImpl();`，便于后续扩展或注入测试实现。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/CatalogUtils.java`（新增）

**修改目的**：将原 `Utilities` 中加载 catalog 与 Hadoop 配置的逻辑提取到独立工具类。

**工作逻辑**：`loadCatalog(config)` 调用 `CatalogUtil.buildIcebergCatalog`，并传入 `loadHadoopConfig(config)`。`loadHadoopConfig` 通过反射（`DynClasses`/`DynConstructors`/`DynMethods`）尝试加载 `HdfsConfiguration` 或 `Configuration`，从 `hadoopConfDir` 读取 `core-site.xml`/`hdfs-site.xml`/`hive-site.xml`，再注入 sink 配置中的 hadoop 属性。这样避免把 Hadoop 作为强依赖。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Channel.java`（新增）

**修改目的**：抽象出 coordinator 与 worker 共享的控制通道基类，封装 Kafka producer/consumer/admin 与事务化收发事件的能力。

**工作逻辑**：构造时根据 `name + transactionalSuffix` 创建事务 producer，订阅 control topic 创建 consumer（使用 `read_committed` 隔离级别、关闭自动提交），并创建 admin client。`send()` 在事务内发送一批事件，并可选把 source offsets 通过 `sendOffsetsToTransaction` 与 consumer group metadata 一起原子提交，失败时 `abortTransaction()`。`consumeAvailable()` 循环 poll，把每条记录封装为 `Envelope` 调用子类 `receive()`，并记录"下一条要消费的 offset"到 `controlTopicOffsets`。`commitConsumerOffsets()` 用 `consumer.commitSync` 提交控制 topic 的低水位 offset（coordinator 用）。子类需实现 `receive(Envelope)`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/KafkaClientFactory.java`（新增）

**修改目的**：集中创建 Kafka 客户端。

**工作逻辑**：`createProducer(transactionalId)` 设置 `TRANSACTIONAL_ID_CONFIG` 并 `initTransactions()`；`createConsumer(groupId)` 设 `AUTO_OFFSET_RESET=latest`、关闭 `ENABLE_AUTO_COMMIT`、`ISOLATION_LEVEL=read_committed`、设 group id；`createAdmin()` 创建 admin。均使用 String/ByteArray 序列化。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/KafkaUtils.java`（新增）

**修改目的**：提供 consumer group 描述与 consumer group metadata 的工具方法。

**工作逻辑**：`consumerGroupDescription(groupId, admin)` 调用 `admin.describeConsumerGroups` 同步获取；`consumerGroupMetadata(context)` 通过反射从 `WorkerSinkTaskContext` 的 `consumer` 字段拿到底层 `Consumer`，再 `groupMetadata()`，用于事务化提交 offset。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/NotRunningException.java`（新增）

**修改目的**：定义协调器意外终止时抛出的运行时异常，使 Sink 任务感知到协调器线程已退出。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Envelope.java`（新增）

**修改目的**：包装收到的 Event 并携带其在 control topic 上的 partition/offset，便于 coordinator 去重与排序。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CommitterImpl.java`（新增）

**修改目的**：`Committer` 接口实现，负责 leader 选举、启动 coordinator 线程与 worker，并在每次 `save()` 时把记录交给 worker、并驱动控制事件处理。

**工作逻辑**：`start()` 通过 admin 拿到 `ConsumerGroupDescription`，若状态为 `STABLE`，调用 `isLeader(members, partitions)`：找出所有成员分配到的最小 `TopicPartition`（按 topic 字典序、partition 升序），持有该 partition 的任务为 leader。leader 创建 `Coordinator` 并包成 `CoordinatorThread` 启动；所有任务都创建 `Worker` 并 `start()`。`save(records)` 把记录交给 worker，再调用 `processControlEvents()`（若 coordinator 线程已终止则抛 `NotRunningException`，让 Sink 任务感知）。`stop()` 关闭 worker 与 coordinator 线程。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CoordinatorThread.java`（新增）

**修改目的**：在独立线程中运行 `Coordinator`，避免阻塞 Sink 任务线程。

**工作逻辑**：`run()` 先 `coordinator.start()`，然后循环 `coordinator.process()`，捕获异常则置 `terminated=true` 并退出循环，最后 `coordinator.stop()`。提供 `terminate()` 与 `isTerminated()` 用于外部控制与查询。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java`（新增）

**修改目的**：核心协调器，负责发起提交周期、收集 worker 事件、按表聚合后做 Iceberg 提交并广播完成事件。

**工作逻辑**：
- 构造时根据所有成员分配到的 partition 总数算 `totalPartitionCount`，构建提交线程池 `exec`（`config.commitThreads()`）与 `CommitState`。consumer group id 使用 `connectGroupId + "-coord"`（用于提交控制 topic 的低水位 offset）。
- `process()`：若达到提交间隔（`commitState.isCommitIntervalReached()`），生成新 commitId 并广播 `StartCommit` 事件；`consumeAvailable(1s)` 拉取事件；若提交超时（`isCommitTimedOut()`）则强制部分提交。
- `receive(envelope)`：`DATA_WRITTEN` 加入响应缓冲；`DATA_COMPLETE` 加入就绪缓冲，当就绪 partition 数达到 `totalPartitionCount` 时触发完整提交。
- `commit(partial)`：调用 `doCommit`，异常仅告警，最后 `endCurrentCommit()`。
- `doCommit`：按 `TableReference` 分组，使用 `Tasks.foreach(...).executeWith(exec).stopOnFailure()` 并行提交各表，全部成功后提交 control topic consumer offset、清空响应、广播 `CommitComplete`。
- `commitToTable`：loadTable（找不到则告警跳过），从历史快照 summary 中按 `snapshotOffsetsProp` 取出该表上次提交的 partition offset map，过滤掉已提交过的 envelope；对 data/delete 文件按 path 去重（`distinctByKey`）；无文件则跳过；仅有数据文件用 `newAppend`，含 delete 文件用 `newRowDelta`，均把 `kafka.connect.offsets.<controlTopic>.<groupId>`（offsetsJson）、`kafka.connect.commit-id`、`kafka.connect.valid-through-ts` 写入快照属性，可指定 branch；提交后发 `CommitToTable` 事件。
- `lastCommittedOffsetsForTable`：从最新快照沿 parent 链查找第一个含 `snapshotOffsetsProp` 的快照，反序列化 offset map，用于跳过已提交数据，保证恢复时不重复提交。
- `stop()`：`exec.shutdownNow()` 并 `awaitTermination(1min)`，超时抛异常以让 worker 失败，再调用 `super.stop()`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CommitState.java`（新增）

**修改目的**：维护单个协调器实例的提交周期状态。

**工作逻辑**：保存 `commitBuffer`（`DATA_WRITTEN` envelope 列表）、`readyBuffer`（`DataComplete` 列表）、`startTime`、`currentCommitId`。提供 `isCommitIntervalReached`（按 `commitIntervalMs` 判断）、`startNewCommit`（生成 UUID）、`isCommitTimedOut`（按 `commitTimeoutMs`）、`isCommitReady(expected)`（统计 `DataComplete.assignments` 总数是否达到预期 partition 数）、`tableCommitMap()`（按 `TableReference` 分组）、`validThroughTs(partial)`（非部分提交且所有 assignment 都有 timestamp 时，取所有 timestamp 的最小值作为"有效截止时间"）。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Worker.java`（新增）

**修改目的**：每个任务侧的 worker，消费数据写入 Iceberg，并响应 coordinator 的 `StartCommit`。

**工作逻辑**：继承 `Channel`，consumer group id 使用 `DEFAULT_CONTROL_GROUP_PREFIX + UUID`（瞬态、不提交 offset）。`save(records)` 转交 `SinkWriter.save`。`process()` 调 `consumeAvailable(Duration.ZERO)` 处理控制事件。`receive(envelope)` 只处理 `START_COMMIT`：调用 `sinkWriter.completeWrite()` 拿到 `SinkWriterResult`，把当前任务所有分配的 partition（即使无数据也包含，使用 `Offset.NULL_OFFSET`）封装为 `TopicPartitionOffset` 列表，把每个 writer 结果转成 `DataWritten` 事件，再加一个 `DataComplete` 事件，连同 source offsets 一起通过 `send(events, sourceOffsets)` 事务化发送（offset 与事件原子提交）。`stop()` 先 `super.stop()` 再关闭 sinkWriter。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SinkWriter.java`（新增）

**修改目的**：把原 `IcebergSinkTask`/`Utilities` 中的路由与写入逻辑收敛到 SinkWriter，配合 worker 完成写入并产出结果。

**工作逻辑**：维护 `writers`（按表名到 `RecordWriter`）与 `sourceOffsets`（TopicPartition→Offset）。`save(record)` 记录 offset（kafkaOffset+1，因为 consumer 下一条要消费的 offset），把 timestamp 转 `OffsetDateTime`，按 `dynamicTablesEnabled` 走动态或静态路由：静态无 routeField 时写入所有配置表；有 routeField 时按正则匹配写入；动态路由用 routeValue 转小写作为表名。`completeWrite()` 调用所有 writer 的 `complete()` 收集 `IcebergWriterResult`，连同 sourceOffsets 一起返回 `SinkWriterResult`，然后清空状态。`writerForTable` 通过 `IcebergWriterFactory.createWriter` 懒创建。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SinkWriterResult.java`（新增）

**修改目的**：封装一次 completeWrite 的结果，包含 writer 结果列表与 source offsets map。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/Offset.java`（新增）

**修改目的**：携带 Kafka 记录的 offset 与 timestamp，供 worker 上报与 coordinator 计算 `valid-through-ts`。

**工作逻辑**：定义 `NULL_OFFSET`（offset/timestamp 均为 null）；实现 `Comparable`，null offset 视为最小，便于排序取最小 timestamp。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordUtils.java`（新增，替换原 `Utilities`）

**修改目的**：把原 `Utilities` 中记录工具方法（`extractFromRecordValue`、`createTableWriter`）提取到 `RecordUtils`，原 `Utilities` 被删除（catalog 加载移到 `CatalogUtils`）。

**工作逻辑**：`extractFromRecordValue` 支持从 `Struct` 或 `Map` 按点分路径取值；`createTableWriter` 根据表属性与配置创建 `TaskWriter`（无分区用 `UnpartitionedWriter`，有分区用 `PartitionedAppendWriter`），可由 `idColumns` 配置覆盖 identifier 字段集，用 `GenericAppenderFactory`，`OutputFileFactory` 使用随机 `operationId` 保证文件名唯一。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriter.java`

**修改目的**：调整为包级可见、与新的 `IcebergWriterResult` 与 `RecordUtils` 对接。

**工作逻辑**：类与方法可见性由 `public` 改为包级（`class`/`IcebergWriter(...)`/`complete()` 等）；`List<WriterResult>` 改为 `List<IcebergWriterResult>`；`Utilities.createTableWriter` 改为 `RecordUtils.createTableWriter`；`new WriterResult(...)` 改为 `new IcebergWriterResult(...)`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriterFactory.java`

**修改目的**：可见性收紧为包级，与模块封装一致。

**工作逻辑**：`public class`/`public IcebergWriterFactory`/`public RecordWriter createWriter` 均去掉 `public`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordWriter.java`

**修改目的**：接口收紧为包级，返回类型改为 `IcebergWriterResult`。

**工作逻辑**：`public interface` → `interface`，`List<WriterResult> complete()` → `List<IcebergWriterResult> complete()`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/IcebergWriterResult.java`（新增，替换原 `WriterResult`）

**修改目的**：把原 `WriterResult` 重命名为 `IcebergWriterResult`，避免与 `SinkWriterResult` 混淆，更明确语义。

**工作逻辑**：字段为 `tableIdentifier`、`dataFiles`、`deleteFiles`、`partitionStruct`，提供访问器。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/NoOpWriter.java`

**修改目的**：适配 `IcebergWriterResult` 与 `complete()` 在无操作时返回空列表而非 null。

**工作逻辑**：`complete()` 返回类型改为 `List<IcebergWriterResult>`，返回 `ImmutableList.of()` 而非 `null`，避免下游 NPE。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/Utilities.java`（删除）

**修改目的**：拆分为 `CatalogUtils`（catalog/Hadoop 配置）与 `RecordUtils`（记录/写入器工具），原文件删除以改善职责划分。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java`

**修改目的**：移除不再需要的 `iceberg.control.group-id` 配置项与 `controlGroupId()`，并简化 `version()`。

**工作逻辑**：删除 `CONTROL_GROUP_ID_PROP` 常量、对应 `configDef.define(...)`、`controlGroupId()` 方法（协调器与 worker 现在分别用 `connectGroupId + "-coord"` 与随机 UUID 作为 group id）；`version()` 不再拼接 `-kc-<kcVersion>`，直接返回 `IcebergBuild.version()`。

### `kafka-connect/kafka-connect-events/src/main/java/org/apache/iceberg/connect/events/TableReference.java`

**修改目的**：为 `TableReference` 增加 `equals`/`hashCode`，使其可作为 Map key 用于按表分组提交。

**工作逻辑**：基于 `catalog`、`namespace`、`name` 三字段实现 `equals` 与 `hashCode`（用 `Objects.equals`/`Objects.hash`）。

### `kafka-connect/kafka-connect-events/src/test/java/org/apache/iceberg/connect/events/EventTestUtil.java`

**修改目的**：测试工具中 `SPEC` 不再强制 `withSpecId(1)`，使用默认 spec id，简化测试 fixtures。

### 测试文件（新增/重命名）

新增 `channel/ChannelTestBase`、`CommitStateTest`、`CommitterImplTest`、`CoordinatorTest`、`CoordinatorThreadTest`、`EventTestUtil`、`WorkerTest`，`data/RecordUtilsTest`、`SchemaUpdateTest`、`SinkWriterTest`，以及把原 `UtilitiesTest` 重命名为 `CatalogUtilsTest`。这些测试覆盖协调器选举、提交就绪/超时、按表聚合、去重、valid-through-ts、worker 路由等核心逻辑。

## 小结

- **成效**：让 Iceberg Kafka Connect Sink 从"无 task 实现"变为可运行：连接器返回 `IcebergSinkTask`，每个任务运行 worker 写数据，leader 任务额外运行 coordinator，通过 control topic 协调统一提交 Iceberg 快照，并将 Kafka offset 写入快照 summary 实现可恢复的去重提交。
- **影响范围**：主要影响 `kafka-connect/kafka-connect` 模块（新增 `channel` 包、`Committer`/`CatalogUtils`、`SinkWriter` 等，调整 `data` 包若干类的可见性与命名），并轻微修改 `kafka-connect-events` 的 `TableReference`（加 equals/hashCode）与测试 util。共 39 个文件，+2869/-190 行。
- **回迁到 1.4.x 的注意事项**：本提交是 Kafka Connect 模块"从骨架到可用"的关键功能补齐，涉及大量新文件与新增的 leader 选举/控制 topic 协议，属于新功能而非 bug 修复。1.4.x 通常不引入新功能，且若 1.4.x 的 kafka-connect 模块尚处于更早骨架阶段，cherry-pick 会与现有代码（如 `Utilities`、`IcebergSinkConnector.taskClass()=null`）冲突，需要连同前序基础设施一起评估。建议谨慎：仅在 1.4.x 已经决定补齐 Kafka Connect 可用性时才考虑，否则不应回迁；同时需检查 `kafka-connect-events` 模块（`TableReference` 的 equals/hashCode、`EventTestUtil` 的 SPEC 调整）与 1.4.x 是否兼容，以及 `IcebergSinkConfig` 删除 `control.group-id` 配置项对现有用户的影响。
