# 提交 2615：Spark 3.5: Support Trigger AvailableNow in Structured Streaming (#13824)

## 提交信息

- **序号**：2615 / 4088
- **哈希**：d731489b436179b38fc322e89bb42a24b31851ac
- **短哈希**：d731489b4
- **日期**：2025-09-08 13:59:23 -0700
- **作者**：Alex Prosak
- **提交说明**：Spark 3.5: Support Trigger AvailableNow in Structured Streaming
- **PR/Issue**：#13824

## 总体目的

Spark Structured Streaming 提供了 `Trigger.AvailableNow()` 触发器模式：流式查询启动时只处理"当前可用"的数据，处理完即自行终止，且在运行期间新到达的数据不会被本次查询处理（留待下次启动）。这对批流一体的场景非常有用——可以像批处理一样定期消费增量数据，同时获得流式的 checkpoint 容错能力。

此前 Iceberg 的 `SparkMicroBatchStream`（Spark 3.5）实现了 `SupportsAdmissionControl`，但未实现 `SupportsTriggerAvailableNow`。这意味着当用户使用 `Trigger.AvailableNow()` 时，Iceberg 流源没有在启动时"锁定"一个上界快照，而是在每个 micro-batch 循环中通过 `table.currentSnapshot()` 动态获取最新快照作为终止条件。这会导致：如果在查询运行期间有新数据写入，`currentSnapshot()` 会变化，使得本应在"本次可用数据"处理完即终止的查询继续处理新数据，违背了 `AvailableNow` 的语义。

本提交通过实现 `SupportsTriggerAvailableNow` 接口，在查询启动前预先计算并缓存一个"最后偏移量"，作为本次查询处理快照的上界，确保运行期间新写入的数据不会被处理，正确支持 `Trigger.AvailableNow()`。

## 如何达成设计目的

核心思路是"预计算上界 + 用预计算值替代动态查询"：

1. **接口替换**：将 `SparkMicroBatchStream` 实现的接口从 `SupportsAdmissionControl` 改为 `SupportsTriggerAvailableNow`（后者继承前者，是 Spark 3.3+ 为 AvailableNow 引入的扩展接口）。Spark 引擎在检测到 `Trigger.AvailableNow()` 时会调用 `prepareForTriggerAvailableNow()`。

2. **预计算最后偏移量**：实现 `prepareForTriggerAvailableNow()`，调用 `latestOffset(initialOffset, ReadLimit.allAvailable())` 计算从初始偏移量开始、无限制下的最新偏移量，缓存到 `lastOffsetForTriggerAvailableNow` 字段，并记录日志。

3. **用预计算值作为循环上界**：在 `latestOffset`/micro-batch 处理循环中，原本用 `table.currentSnapshot().snapshotId()` 作为终止快照判断；改为优先使用 `lastOffsetForTriggerAvailableNow.snapshotId()`（若已设置），否则回退到 `table.currentSnapshot()`。这样即使运行期间有新快照产生，循环也只处理到预计算的上界即停止。

4. **测试覆盖**：为现有各 ReadLimit 测试增加 `Trigger.AvailableNow()` 变体（验证批大小分布一致）；新增两个专门测试：验证 AvailableNow 查询会自行终止、重启不重复处理；验证运行期间新写入的数据不被本次查询处理。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+25/-4 lines)

**修改目的**：实现 `SupportsTriggerAvailableNow` 接口，支持 `Trigger.AvailableNow()`。

**工作逻辑**：
- 将 `implements MicroBatchStream, SupportsAdmissionControl` 改为 `implements MicroBatchStream, SupportsTriggerAvailableNow`（导入也相应替换）。`SupportsTriggerAvailableNow` 继承自 `SupportsAdmissionControl`，因此原有的 admission control 方法（如 `getDefaultReadLimit`、`latestOffset`）依然有效。
- 新增字段 `private StreamingOffset lastOffsetForTriggerAvailableNow;` 用于缓存预计算的上界偏移量。
- 新增 `@Override public void prepareForTriggerAvailableNow()`：记录日志，调用 `latestOffset(initialOffset, ReadLimit.allAvailable())` 计算"当前所有可用数据"对应的最后偏移量并缓存，再记录其 json 日志。Spark 引擎在查询启动前（构造 micro-batch 之前）调用此方法。
- 在 micro-batch 循环的终止判断处：原先 `if (curSnapshot.snapshotId() == table.currentSnapshot().snapshotId()) { break; }` 改为先计算 `latestSnapshotId`（若 `lastOffsetForTriggerAvailableNow != null` 则用其 snapshotId，否则用 `table.currentSnapshot().snapshotId()`），然后用 `curSnapshot.snapshotId() == latestSnapshotId` 作为终止条件。这保证 AvailableNow 模式下循环只到预计算快照即停止，不受运行期间新快照影响。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestStructuredStreamingRead3.java` (+148 lines)

**修改目的**：为 Trigger.AvailableNow 增加测试覆盖。

**工作逻辑**：
- 导入 `Trigger`。
- 为现有的多个 ReadLimit 测试（maxFiles=1、maxFiles=2、maxRows=1、maxRows=2、maxRows=4、maxFiles+maxRows 组合）各增加一个 `Trigger.AvailableNow()` 变体调用，验证在 AvailableNow 触发器下各 micro-batch 的记录数分布与 ProcessingTime 一致。
- 重载 `assertMicroBatchRecordSizes` 方法，新增带 `Trigger` 参数的版本；原方法委托给 `Trigger.ProcessingTime(0L)` 版本，保持向后兼容。在 `writeStream().trigger(trigger)` 处应用触发器。
- 新增 `testAvailableNowStreamReadShouldNotHangOrReprocessData`：多轮写入数据后用 AvailableNow 启动查询，验证：(a) 查询自行终止（`awaitTermination` 返回 true）；(b) 输出数据正确；(c) 立即重启查询不会重复处理数据（`recentProgress` 长度为 1，且 startOffset 等于 endOffset）。
- 新增 `testTriggerAvailableNowDoesNotProcessNewDataWhileRunning`：先写入初始数据并记录当前 snapshotId，启动 AvailableNow 查询（maxFiles=1 以拉长执行时间），然后在查询运行期间追加两批新数据；验证查询终止后：(a) endOffset 的 snapshotId 等于启动时记录的 snapshotId（未处理运行期间新数据）；(b) 结果记录数等于初始数据量，内容与初始数据一致。

## 总结

本提交为 Iceberg Spark 3.5 流式读取正确支持了 `Trigger.AvailableNow()` 触发器。通过实现 `SupportsTriggerAvailableNow` 接口并在查询启动前预计算快照上界，确保 AvailableNow 查询只处理启动时已存在的数据，运行期间新数据留待下次，且重启不会重复处理。这对批流一体的增量消费场景（如定期跑批）非常重要。测试覆盖充分，包含一致性验证与两个专门的边界场景测试。后续提交 2616 会将此功能 backport 到 Spark 4.0 和 3.4。
