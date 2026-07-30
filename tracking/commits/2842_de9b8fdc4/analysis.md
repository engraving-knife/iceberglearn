# 提交 2842：Kafka Connect: add task ID snapshot property (#14493)

## 提交信息

- **序号**：2842 / 4088
- **哈希**：de9b8fdc4909ae5a550bab401fe24bfdbc9719df
- **短哈希**：de9b8fdc4
- **日期**：2025-11-06 16:11:34 -0800
- **作者**：Bryan Keller
- **提交说明**：Kafka Connect: add task ID snapshot property (#14493)
- **PR/Issue**：#14493

## 总体目的

Iceberg Kafka Connect 的 Coordinator 在提交快照时，已经会把 `kafka.connect.commit-id`（当前 commit ID）和 `kafka.connect.valid-through-ts`（有效时间戳）写入快照 summary 属性，用于追溯哪个 Kafka Connect commit 对应哪个 Iceberg 快照。

但在多 task 并发场景下，仅凭 commit-id 还不足以定位是哪个 task 实例产生的提交。该提交新增 `kafka.connect.task-id` 快照属性，值为 `<connectorName>-<taskId>`（例如 `my-sink-connector-0`），让每个快照都能直接关联到产生它的具体 task，便于排查问题、审计与监控。

## 如何达成设计目的

1. 在 `Coordinator` 中新增常量 `TASK_ID_SNAPSHOT_PROP = "kafka.connect.task-id"`。
2. 在 commit 逻辑中（append 与 rowDelta 两个分支），先构造 `taskId = String.format("%s-%s", config.connectorName(), config.taskId())`。
3. 在 `AppendFiles` 与 `RowDelta` 操作上调用 `set(TASK_ID_SNAPSHOT_PROP, taskId)`，与已有的 `COMMIT_ID_SNAPSHOT_PROP`、`VALID_THROUGH_TS_SNAPSHOT_PROP` 并列写入快照 summary。
4. 在集成测试 `IntegrationTestBase` 中增加断言 `assertThat(props).containsKey("kafka.connect.task-id")`，确保提交后快照属性包含 task-id。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+4/-0 lines)

**修改目的**：在快照提交时写入 task-id 属性。

**工作逻辑**：
- 新增 `TASK_ID_SNAPSHOT_PROP` 常量。
- 在 `commit` 方法中，当有数据/删除文件需要提交时，先计算 `taskId = connectorName + "-" + taskId`。
- 在 append 分支：`appendOp.set(COMMIT_ID_SNAPSHOT_PROP, ...)` 之后追加 `appendOp.set(TASK_ID_SNAPSHOT_PROP, taskId)`。
- 在 rowDelta 分支：`deltaOp.set(COMMIT_ID_SNAPSHOT_PROP, ...)` 之后追加 `deltaOp.set(TASK_ID_SNAPSHOT_PROP, taskId)`。
- 跳过提交（无数据）的分支不写 task-id，因为不产生新快照。

### `kafka-connect/kafka-connect-runtime/src/integration/java/org/apache/iceberg/connect/IntegrationTestBase.java` (+1/-0 lines)

**修改目的**：验证快照属性中包含 task-id。

**工作逻辑**：在已有的 `assertThat(props).containsKey("kafka.connect.commit-id")` 之后追加 `assertThat(props).containsKey("kafka.connect.task-id")`，确保提交后能从快照 summary 读到 task-id。

## 总结

该提交为 Kafka Connect 的 Iceberg 快照新增 `kafka.connect.task-id` summary 属性，值为 `connectorName-taskId`，使每个快照都能直接关联到产生它的具体 task 实例，便于多 task 场景下的排查与审计。修改轻量，append 与 rowDelta 两条提交路径都覆盖，并加了集成测试断言。
