# 提交 4053：Kafka Connect: Add bounded retry for transient commit exceptions (#16434)

## 提交信息

- **序号**：4053 / 4088
- **哈希**：be43e31747cc9ef07571a5df58d93bf45d812ca6
- **短哈希**：be43e3174
- **日期**：2026-07-16 23:26:39 +0200
- **作者**：Anupam Yadav
- **提交说明**：Kafka Connect: Add bounded retry for transient commit exceptions (#16434)
- **PR/Issue**：#16434（Fixes #16393）

## 总体目的

这个提交为 Iceberg Kafka Connect 的协调器（Coordinator）添加了有界重试机制，用于处理瞬态提交异常（transient commit exceptions），解决 issue #16393。

在此之前的实现中，当 Coordinator 执行 Iceberg 表提交（commit）遇到 `CommitFailedException`（如 Glue 检测到并发更新、乐观锁冲突等）时，会立即抛出异常。对于 Kafka Connect sink 而言，这类瞬态失败如果直接传播，会导致整个 connector 任务失败重启，而重启后可能再次遇到同样的并发冲突，形成「失败-重启-再失败」的循环，无法自动恢复。

本提交引入了一个可配置的「连续提交失败上限」（`commitMaxConsecutiveFailures`，默认值 1，即保持向后兼容的「一次失败即终止」行为）。当设置为大于 1 时，Coordinator 会在遇到 `CommitFailedException` 后不立即抛出，而是记录失败次数并在下一次提交周期重试；只有当连续失败次数达到上限时才终止任务。成功提交会重置失败计数器。对于非 `CommitFailedException` 的异常（如 `CommitStateUnknownException`、`ValidationException`、`ForbiddenException`、NPE 等），由于不可重试，仍然立即终止。

## 如何达成设计目的

设计上分三层：

1. **配置层**：在 `IcebergSinkConfig` 中新增 `iceberg.control.commit.max-consecutive-failures` 配置项，类型为 int，默认值 1，范围校验 `atLeast(1)`，重要性 MEDIUM。提供 `commitMaxConsecutiveFailures()` 访问方法。

2. **协调器逻辑层**：在 `Coordinator.commit()` 方法中重构异常处理：
   - 成功的非 partial commit 将 `consecutiveCommitFailures` 计数器归零。
   - partial commit 失败仍走原有路径（记录 partialCommitFailures 计数后 return，不参与重试）。
   - 非 `CommitFailedException` 的异常立即抛出（不可重试）。
   - `CommitFailedException` 时递增计数器，若达到上限则 LOG.error 并抛出终止；否则 LOG.warn 并在下个周期重试。

3. **测试层**：新增多个测试用例覆盖重试、计数器重置、多线程场景。

## 修改详情

### `docs/docs/kafka-connect.md` (+1/-0 lines)

**修改目的**：文档化新增配置项。

**工作逻辑**：在 Kafka Connect 配置表格中新增一行：
```
| iceberg.control.commit.max-consecutive-failures | Maximum number of consecutive commit failures before the coordinator terminates, default is `1` |
```

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java` (+15/-0 lines)

**修改目的**：定义新配置项和访问方法。

**工作逻辑**：
- 新增配置属性常量和默认值：
  ```java
  private static final String COMMIT_MAX_CONSECUTIVE_FAILURES_PROP =
      "iceberg.control.commit.max-consecutive-failures";
  private static final int COMMIT_MAX_CONSECUTIVE_FAILURES_DEFAULT = 1;
  ```
- 在 `CONFIG_DEF` 中注册，带 `ConfigDef.Range.atLeast(1)` 范围校验，确保用户不能设置为 0 或负数。
- 新增访问方法 `public int commitMaxConsecutiveFailures()`。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+30/-2 lines)

**修改目的**：实现有界重试逻辑。

**工作逻辑**：
- 新增字段 `private int consecutiveCommitFailures;` 跟踪连续失败次数。
- 重构 `commit()` 方法的异常处理：
  ```java
  private void commit(boolean partialCommit) {
    try {
      doCommit(partialCommit);
      if (!partialCommit) {
        consecutiveCommitFailures = 0;  // 成功则重置
      }
    } catch (RuntimeException e) {
      if (partialCommit) {
        partialCommitFailures.incrementAndGet();
        // ... log and return
        return;
      }
      if (!(e instanceof CommitFailedException)) {
        // 不可重试异常，立即终止
        throw e;
      }
      consecutiveCommitFailures++;
      if (consecutiveCommitFailures >= config.commitMaxConsecutiveFailures()) {
        LOG.error("... ({} consecutive failures, terminating)", ...);
        throw e;
      }
      LOG.warn("... ({} consecutive failures, will retry)", ...);
    } finally {
      commitState.endCurrentCommit();
    }
  }
  ```
  关键设计：只有 `CommitFailedException`（乐观锁冲突等瞬态错误）才重试；其他异常立即抛出。partial commit 失败不参与重试计数。成功提交重置计数器。
- 新增 import `org.apache.iceberg.exceptions.CommitFailedException`。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/ChannelTestBase.java` (+1/-0 lines)

**修改目的**：在测试基类 mock 中配置新配置项默认值。

**工作逻辑**：`when(config.commitMaxConsecutiveFailures()).thenReturn(1);` 使所有继承该基类的测试默认使用「一次失败即终止」的旧行为，保持向后兼容。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCoordinator.java` (+148/-0 lines)

**修改目的**：覆盖重试逻辑的各种场景。

**工作逻辑**：
- `testCommitBoundedRetry`：配置上限为 3，前两次 `CommitFailedException` 不抛出（重试），第三次达到上限抛出终止。
- `testCommitCounterResetsOnSuccess`：验证成功提交重置计数器。序列为「失败、失败、成功（no-op）、失败、失败、失败」——第三次失败后才终止，证明成功提交后计数器归零。
- `testCommitBoundedRetryWithMultipleThreads`：配置 2 个提交线程、上限 2，验证多线程下的重试行为。
- 已有测试 `testCommitError`、`testCommitFailedExceptionPropagates`、`testCoordinatorCommittedOffsetValidation` 中显式设置 `commitMaxConsecutiveFailures(1)` 以保持原行为预期。
- 新增辅助方法 `triggerCommitCycle`：模拟一个完整的提交周期（process → 发送 StartCommit → 接收 DataWritten + DataComplete → process），驱动 Coordinator 的提交流程。

## 总结

这个提交为 Kafka Connect Coordinator 增加了有界的瞬态提交失败重试能力，使 connector 能在遇到乐观锁冲突等瞬态错误时自动重试若干次而非立即失败重启，提升了在并发冲突场景下的鲁棒性。设计上区分了可重试异常（`CommitFailedException`）和不可重试异常，默认值 1 保持向后兼容，用户可通过配置调大重试上限。测试覆盖了重试、计数器重置、多线程等关键路径，质量较高。
