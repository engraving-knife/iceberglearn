# 提交 2830：Kafka Connect: Don't check that consumer group is stable for coordinator leader election (#14395)

## 提交信息

- **序号**：2830 / 4088
- **哈希**：c235305c985e2f4427fa735a1a7460252da8005d
- **短哈希**：c235305c9
- **日期**：2025-11-04 11:27:52 -0800
- **作者**：Fenil Doshi
- **提交说明**：Kafka Connect: Don't check that consumer group is stable for coordinator leader election (#14395)
- **PR/Issue**：#14395

## 总体目的

在 Iceberg Kafka Connect 中，当多个 worker（SinkTask 实例）协同写入 Iceberg 表时，需要一个协调者（coordinator）来负责提交（commit）已写入的数据快照。协调者通过"持有第一个分区（partition 0）的消费者"选举产生，被称为 leader。

原实现要求消费组状态必须是 `ConsumerGroupState.STABLE` 才会进行 leader 选举。但消费组状态在 rebalance 期间会处于 `PreparingRebalance` 或 `CompletingRebalance` 等非稳定状态，这会导致协调者选举被推迟甚至无法进行。特别是当消费组处于 `CompletingRebalance` 状态时，分区分配实际上已经确定，但状态还不是 `STABLE`，此时完全可以判断 leader。

该提交移除了对 `STABLE` 状态的检查，让 leader 选举能在更早的时机进行，从而提升 Kafka Connect Sink 在 rebalance 过渡阶段的可用性和响应速度。

## 如何达成设计目的

主要修改集中在 `CommitterImpl.hasLeaderPartition` 方法：

1. 删除 `ConsumerGroupState` 的导入以及 `if (groupDesc.state() == ConsumerGroupState.STABLE)` 的状态判断分支。
2. 直接从 `ConsumerGroupDescription` 取 `members()` 列表，并调用 `containsFirstPartition` 来判断当前 worker 是否持有第一个分区。
3. 将 `hasLeaderPartition` 方法从 `private` 改为包级可见并标注 `@VisibleForTesting`，以便单元测试可以直接验证该方法（绕过原本对 Kafka 真实 admin 客户端的依赖）。
4. 在测试中通过反射注入 `config` 与 `clientFactory`，并使用 Mockito 的 `mockStatic` 来 mock `KafkaUtils.consumerGroupDescription`，从而测试非 STABLE 状态下也能正常选举 leader。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CommitterImpl.java` (+10/-8 lines)

**修改目的**：移除对消费组 STABLE 状态的强依赖，让 leader 选举在消费组处于 rebalance 中间状态时也能进行。

**工作逻辑**：
- `hasLeaderPartition(Collection<TopicPartition> currentAssignedPartitions)` 方法用于判断当前 worker 是否应充当协调者 leader。原逻辑先调用 `KafkaUtils.consumerGroupDescription` 获取消费组描述，再判断状态是否为 STABLE，只有稳定才进入成员分配检查。
- 修改后直接获取 `members()`，调用 `containsFirstPartition(members, currentAssignedPartitions)` 检查当前 worker 是否持有任意 topic 的 partition 0。如果命中则保存成员列表到 `membersWhenWorkerIsCoordinator` 并返回 true。
- 同时将方法可见性放宽并加 `@VisibleForTesting`，便于测试。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCommitterImpl.java` (+60/-5 lines)

**修改目的**：增加对 `hasLeaderPartition` 方法的单元测试，验证即便消费组状态非 STABLE，leader 选举逻辑依然正确工作。

**工作逻辑**：
- 将原 `testIsLeader` 测试拆为两个：
  - `testIsLeader`：直接测试 `containsFirstPartition`，验证持有 partition 0 返回 true，否则返回 false。
  - `testHasLeaderPartition`：通过反射注入 mock 的 `IcebergSinkConfig`（返回 `"test-group"`）和 `KafkaClientFactory`（返回 mock Admin），用 `mockStatic(KafkaUtils.class)` 让 `consumerGroupDescription` 返回 mock 的 `ConsumerGroupDescription`。mock 的描述未对 `state()` 进行 stub（默认返回 null），模拟"非 STABLE"状态，但仍能正确返回 members，从而验证 leader 选举不再依赖 STABLE 状态。

## 总结

该提交移除了 Kafka Connect 协调者 leader 选举对消费组 STABLE 状态的依赖，避免了 rebalance 期间 leader 选举被不必要地推迟。同时通过将方法可见性放宽并增加 mock 测试，提升了可测试性。这是一个对 Kafka Connect Iceberg Sink 在 rebalance 场景下的健壮性改进。
