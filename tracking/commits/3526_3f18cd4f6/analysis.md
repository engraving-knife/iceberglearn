# 提交 3526：Kafka Connect: Fix source offset tracking when SMTs modify the record topic (#15880)

## 提交信息

- **序号**：3526 / 4088
- **哈希**：3f18cd4f69a2110327161972646da226fbbecc13
- **短哈希**：3f18cd4f6
- **日期**：2026-04-13 14:02:10 -0700
- **作者**：kumarpritam863
- **提交说明**：Kafka Connect: Fix source offset tracking when SMTs modify the record topic (#15880)
- **PR/Issue**：#15880

## 总体目的

Kafka Connect 支持通过 Single Message Transforms（SMT）在记录到达 sink connector 之前对记录做转换，例如 `RegexRouter` 可以重写记录的 topic 名。Iceberg Kafka Connect 的 `SinkWriter` 此前用 `record.topic()` 和 `record.kafkaPartition()` 作为 key 来追踪 source offset，以便后续向 Kafka Connect 框架提交消费位移。

问题在于：当用户配置了像 `RegexRouter` 这样的 SMT 后，`record.topic()` 已经被改写成新的 topic 名，但 Kafka Connect 框架的 `context.assignment()` 和消费者位移管理仍然基于「原始」topic/partition。这导致 `SinkWriter` 提交的 offset 用了改写后的 topic 作为 key，与框架期望的原始 topic 不匹配，offset 提交实际上不生效，可能导致重启后重复消费或无法正确提交位移。

本提交将 offset 追踪改为使用 `record.originalTopic()`、`record.originalKafkaPartition()`、`record.originalKafkaOffset()` 这些「原始」属性，确保与 Kafka Connect 框架的位移管理对齐。

## 如何达成设计目的

`SinkRecord` 提供了 `originalTopic()`、`originalKafkaPartition()`、`originalKafkaOffset()` 等方法，它们在 SMT 修改记录后仍保留原始的 topic/partition/offset 信息。`SinkWriter.save()` 中将 offset 的 key 从 `new TopicPartition(record.topic(), record.kafkaPartition())` 改为 `new TopicPartition(record.originalTopic(), record.originalKafkaPartition())`，offset 值也从 `record.kafkaOffset()+1` 改为 `record.originalKafkaOffset()+1`。

这样无论 SMT 如何修改 topic，offset 始终以原始 topic/partition 为 key 提交，与 Kafka Connect 框架一致。同时新增单元测试和集成测试验证修复。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/SinkWriter.java` (+6/-1 lines)

**修改目的**：用原始 topic/partition/offset 追踪 source offset。

**工作逻辑**：
```java
// use the original topic and partition to track offsets, as SMTs may have changed
// record.topic() and record.kafkaPartition() (e.g. RegexRouter). The framework's
// context.assignment() and consumer offset management use the original values.
sourceOffsets.put(
    new TopicPartition(record.originalTopic(), record.originalKafkaPartition()),
    new Offset(record.originalKafkaOffset() + 1, timestamp));
```
关键点：key 用 `originalTopic`/`originalKafkaPartition`，value 的 offset 用 `originalKafkaOffset+1`（+1 表示下次应消费的位置）。timestamp 仍用 `record.timestamp()`（记录时间戳）。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/TestSinkWriter.java` (+68/-0 lines)

**修改目的**：单元测试验证 offset 用原始 topic 追踪。

**工作逻辑**：
`testOffsetTrackedByOriginalTopicPartition` 测试模拟 RegexRouter 场景：
1. 构造一个原始 `SinkRecord`（topic=`orders`, partition=0, offset=42）
2. 通过 `original.newRecord(transformedTopic, ...)` 模拟 SMT 把 topic 改为 `tmp.dynamic_orders`
3. 调用 `sinkWriter.save(...)` 和 `completeWrite()`
4. 断言 `result.sourceOffsets()` 中以 `orders`+partition=0 为 key 的 offset 存在，值为 43（42+1），timestamp 匹配
5. 断言以改写后 topic `tmp.dynamic_orders` 为 key 的 offset 不存在

这精确验证了修复后的行为：offset 以原始 topic 为 key。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/data/TestIntegrationDynamicTable.java` (+53/-0 lines)

**修改目的**：集成测试验证带 topic 重写 SMT 的动态路由端到端工作。

**工作逻辑**：
`testDynamicRouteWithTopicRewritingSMT` 测试场景：
1. 创建目标表 `smttbl`
2. 配置 connector：启用动态表 (`iceberg.tables.dynamic-enabled=true`)，路由字段为 `srcTopic`
3. 配置两个 SMT：
   - `RegexRouter`：把 topic 正则替换为 `test.smttbl`
   - `InsertField$Value`：把改写后的 `record.topic()`（即 `test.smttbl`）插入到字段 `srcTopic`
4. 动态路由根据 `srcTopic` 字段值选择目标表
5. 发送 2 条测试记录，flush
6. 用 Awaitility 等待快照生成，验证目标表有 1-2 个数据文件，总记录数=2

这个测试覆盖了「SMT 改写 topic + 动态路由 + offset 正确提交」的完整链路。新增了 `Duration`、`Awaitility`、`Test` 等 import。

## 总结

本提交修复了 Iceberg Kafka Connect 在使用 topic 重写类 SMT（如 RegexRouter）时 source offset 提交 key 不匹配的问题，导致 offset 提交失效。通过改用 `SinkRecord` 的 `original*` 系列方法获取原始 topic/partition/offset，确保与 Kafka Connect 框架的位移管理一致。修复配有单元测试和端到端集成测试，覆盖了 SMT 改写 topic + 动态路由的复杂场景。
