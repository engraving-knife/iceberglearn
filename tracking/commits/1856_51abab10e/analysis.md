# 提交 1856：Kafka Connect: Handle no coordinator and data loss in ICR mode (#12372)

## 提交信息

- **序号**：1856 / 4088
- **哈希**：51abab10e22569250ade2b3726844ab7fa9bc0b6
- **短哈希**：51abab10e
- **日期**：2025-03-14 10:10:36 -0700
- **作者**：kumarpritam863
- **提交说明**：Kafka Connect: Handle no coordinator and data loss in ICR mode (#12372)
- **PR/Issue**：#12372

## 总体目的

Iceberg Kafka Connect sink 在 ICR（Iceberg Connect Router / 协调器模式）下，会从消费者组成员中选举一个"leader"任务运行 `Coordinator`（提交协调器线程），其余任务运行 `Worker`（数据写入器）。选举规则：持有"第一个 topic 的 partition 0"的任务当 leader。

此前的实现存在两个严重问题：

1. **coordinator 不会随分区变更而启停**：`CommitterImpl.start(...)` 在 `IcebergSinkTask.open(partitions)` 时一次性判断是否是 leader，如果是就启动 coordinator 线程；后续若发生 rebalance 导致该任务丢失了 partition 0，`stop()` 只是简单停止所有资源，不会感知"我不再是 leader"这一事实。结果 rebalance 后可能没有任务运行 coordinator（数据无人提交），或者多个任务同时运行 coordinator（重复提交）。

2. **数据丢失**：`IcebergSinkTask` 此前在 `open()` 时才创建 catalog 和 committer，在 `close()` 时停止 committer。但 Kafka Connect 的 `close(partitions)` 表示分区被撤销，如果此时直接停掉 worker，已拉取但未提交的记录会丢失——下次 `open` 时 consumer 会从上次 committed offset 开始读，中间未提交的数据被跳过。

本提交重写 `CommitterImpl` 的生命周期：把 `start/stop` 拆分为 `open(addedPartitions)/close(closedPartitions)`，按"是否持有 leader 分区"动态启停 coordinator；并在 `close` 时把 consumer seek 回最后 committed offset，避免数据丢失。

## 如何达成设计目的

1. **扩展 `Committer` 接口**：新增 `open(catalog, config, context, addedPartitions)` 与 `close(closedPartitions)` 两个 default 方法（默认委托给旧的 `start`/`stop`），旧方法标记 `@Deprecated`（2.0.0 移除）。
2. **`IcebergSinkTask` 调整生命周期**：把 catalog/committer 的创建从 `open` 提前到 `start`（任务启动时一次性创建），`open(partitions)` 只调用 `committer.open(..., partitions)`，`close(partitions)` 只调用 `committer.close(partitions)`；私有的 `close()`（任务停止时）才彻底停止 committer。
3. **`CommitterImpl` 重构为按 leader 分区动态启停**：
   - `initialize(...)` 用 `AtomicBoolean` 保证只初始化一次（catalog/config/context/clientFactory）。
   - `hasLeaderPartition(partitions)`：查询 consumer group 状态，判断当前分区集合是否含"第一个 topic 的 partition 0"，并缓存成员列表。
   - `open(...)`：初始化后，若 `hasLeaderPartition(addedPartitions)` 为真则 `startCoordinator()`。
   - `close(closedPartitions)`：若 `hasLeaderPartition(closedPartitions)` 为真则 `stopCoordinator()`；总是 `stopWorker()`；然后 `KafkaUtils.seekToLastCommittedOffsets(context)` 把 consumer seek 回最后 committed offset，防止数据丢失。
   - `save(...)`：改为按需 `startWorker()`（懒启动 worker）。
4. **`KafkaUtils.seekToLastCommittedOffsets`**：新增工具方法，读取 consumer 当前 assignment 的 committed offsets，逐个 `consumer.seek(tp, offset)`；若 rebalance 已导致分区丢失（`IllegalStateException`）则 warn 并跳过。
5. **重命名**：`isLeader` → `containsFirstPartition`（语义更准确——判断是否含第一个分区，而非"是否是 leader"，因为 leader 选举还涉及 group 状态）。
6. **测试**：新增 `MockIcebergSinkTask` 模拟 task 的 open/close 行为；`ChannelTestBase` 增加 `sourceConsumer` 与 `ConsumerRebalanceListener` 模拟 rebalance 触发 open/close；`CoordinatorTest` 新增 `testCoordinatorRunning` 验证 rebalance 时 coordinator 的启停。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/Committer.java` (修改, +25 lines)

**修改目的**：扩展 Committer 接口以支持分区级生命周期。

**工作逻辑**：新增 `default void open(Catalog, IcebergSinkConfig, SinkTaskContext, Collection<TopicPartition> addedPartitions)`（默认调 `start`）和 `default void close(Collection<TopicPartition> closedPartitions)`（默认调 `stop`）。旧 `start`/`stop` 标记 `@Deprecated`（2.0.0 移除）。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkTask.java` (修改, +5/-9 lines)

**修改目的**：把 catalog/committer 创建提前到 `start`，`open`/`close` 改为委托给 committer 的分区级方法。

**工作逻辑**：`start(props)` 中 `catalog = CatalogUtils.loadCatalog(config); committer = CommitterFactory.createCommitter(config);`。`open(partitions)` 调 `committer.open(catalog, config, context, partitions)`。`close(partitions)` 调 `committer.close(partitions)`。私有 `close()`（任务停止）调 `committer.close(context.assignment())` 后置 null。移除 `Preconditions.checkArgument(catalog == null, ...)` 等校验（不再需要，因为 open 可被多次调用）。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CommitterImpl.java` (修改, +118/-29 lines)

**修改目的**：重写为按 leader 分区动态启停 coordinator/worker。

**工作逻辑**：
- 新增字段：`catalog`/`config`/`context`/`clientFactory`/`membersWhenWorkerIsCoordinator`/`isInitialized`（AtomicBoolean）。
- `initialize(...)`：CAS 保证只初始化一次。
- `hasLeaderPartition(currentAssignedPartitions)`：查 consumer group 状态，若 STABLE 且 `containsFirstPartition(members, partitions)` 则缓存 members 并返回 true。
- `containsFirstPartition(members, partitions)`（原 `isLeader`）：找成员中字典序最小的 topic，取其 partition 0，判断 `partitions.contains(firstTopicPartition)`。
- `open(...)`：`initialize` 后若 `hasLeaderPartition` 则 `startCoordinator()`。
- `close(closedPartitions)`：若 `hasLeaderPartition` 则 `stopCoordinator()`；`stopWorker()`；`KafkaUtils.seekToLastCommittedOffsets(context)`。
- `save(...)`：`startWorker()`（懒启动）后 `worker.save(...)`。
- `startCoordinator/startWorker/stopCoordinator/stopWorker`：四个私有方法，分别用 null 检查保证幂等。
- 旧 `start`/`stop` 抛 `UnsupportedOperationException` 提示用新方法。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/KafkaUtils.java` (修改, +34 lines)

**修改目的**：新增 `seekToLastCommittedOffsets` 防止 rebalance 后数据丢失。

**工作逻辑**：`seekToLastCommittedOffsets(context)`：通过反射拿到 `WorkerSinkTaskContext` 内的 `Consumer`，`consumer.committed(consumer.assignment())` 拿到已提交 offset，逐个 `consumer.seek(tp, offset)`；若分区已因 rebalance 丢失（`IllegalStateException`）则 warn 跳过。新增 `Logger`、`Map`/`OffsetAndMetadata`/`TopicPartition` 等 import。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/MockIcebergSinkTask.java` (新增, 61 lines)

**修改目的**：测试用的 mock SinkTask，跟踪 coordinator 状态。

**工作逻辑**：`open(partitions)` 时若含 partition 0 则 `isCoordinator = true`；`close(partitions)` 时若含 partition 0 则 `isCoordinator = false`。`isCoordinatorRunning()` 暴露状态供测试断言。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/ChannelTestBase.java` (修改, +23/-4 lines)

**修改目的**：测试基类增加 sourceConsumer 与 rebalance listener 模拟。

**工作逻辑**：新增 `sourceConsumer`（MockConsumer）和 `mockIcebergSinkTask`；`sourceConsumer.subscribe(Collections.singleton(SRC_TOPIC_NAME), new Listener())`，`Listener` 在 `onPartitionsAssigned/Revoked` 时调 `mockIcebergSinkTask.open/close`，模拟 Kafka Connect 的 rebalance 回调。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/CommitterImplTest.java` (修改, +2/-2 lines)

**修改目的**：跟随 `isLeader` → `containsFirstPartition` 重命名。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/CoordinatorTest.java` (修改, +23 lines)

**修改目的**：新增 `testCoordinatorRunning` 验证 rebalance 时 coordinator 启停。

**工作逻辑**：分配 tp0/tp1/tp2（含 partition 0，应被选为 leader 启动 coordinator），断言 `mockIcebergSinkTask.isCoordinatorRunning()` 为 true；revoke tp0（丢失 leader 分区），断言为 false；再 assign tp0，断言恢复 true。

## 小结

- **成效**：修复了 ICR 模式下 rebalance 导致的"无 coordinator"和"数据丢失"两个严重问题。coordinator 现在会随 leader 分区的获得/丢失而动态启停；rebalance 后 consumer 会 seek 回最后 committed offset，避免已拉取未提交的记录被跳过。
- **影响范围**：kafka-connect 模块，4 个主代码文件（+176/-40）、4 个测试文件（+107/-12）。属于正确性修复，影响所有 ICR 模式部署。
- **回迁到 1.4.x 的注意事项**：建议回迁，前提是 1.4.x 已包含 kafka-connect 模块且 `Committer`/`CommitterImpl`/`IcebergSinkTask` 结构与 main 接近。本提交改动较大（重构 CommitterImpl 生命周期），回迁时需整体移植 `CommitterImpl` 与 `IcebergSinkTask` 的对应方法，并同步引入 `seekToLastCommittedOffsets`。注意 `Committer` 接口新增的 default 方法是向后兼容的（旧实现仍可用 start/stop）。回迁后需充分测试 rebalance 场景。
