# 提交 3736：Kafka Connect: Surface commit failures instead of silently swallowing them (#16237)

## 提交信息

- **序号**：3736 / 4088
- **哈希**：2fe32aa8f77ebaee5359cabd4592300e1df69e72
- **短哈希**：2fe32aa8f
- **日期**：2026-05-18 22:44:30 +0200
- **作者**：Anupam Yadav
- **提交说明**：Kafka Connect: Surface commit failures instead of silently swallowing them (#16237)
- **PR/Issue**：#16237（修复 #15878）

## 总体目的

本提交修复了 Iceberg Kafka Connect 中 commit 失败被静默吞掉的问题（issue #15878）。

在 Kafka Connect 的 `Coordinator` 中，`commit(boolean partialCommit)` 方法负责将收集到的数据文件提交到 Iceberg 表。原实现对 `doCommit()` 抛出的任何 `Exception` 都只是记录 WARN 日志并吞掉，然后在下一个周期重试。这种处理方式存在严重问题：

1. **全量提交（full commit）失败被吞掉**：当全量提交因 `CommitFailedException`（如并发更新冲突）、`ValidationException`（如 stale offsets）等原因失败时，错误被静默吞掉，操作者无法通过 Connect 框架感知到任务出了问题，可能导致数据丢失或长时间不一致。
2. **与 Connect 框架的失败语义脱节**：Kafka Connect 框架依赖任务线程抛出未捕获异常来将任务状态转换为 `FAILED`，从而触发告警和重启。吞掉异常使得任务始终保持 `RUNNING` 状态，掩盖了真实故障。

本提交区分了全量提交失败和部分提交失败：全量提交失败时重新抛出异常，使 coordinator 线程终止并将 Connect 任务转为 `FAILED`；部分提交失败（由提交超时触发）仍以 WARN 记录并吞掉，因为 coordinator 会在下一周期自动重试。

## 如何达成设计目的

修改 `Coordinator.commit()` 方法的异常处理逻辑：
1. 将 catch 的异常类型从 `Exception` 收窄为 `RuntimeException`（更精确地匹配实际可能抛出的运行时异常）。
2. 在 catch 块中根据 `partialCommit` 标志分支处理：
   - `partialCommit == true`：记录 WARN 日志并吞掉异常（保持原有重试语义，因为部分提交超时是预期可重试的场景）。
   - `partialCommit == false`（全量提交）：记录 ERROR 日志并重新抛出异常，使 coordinator 线程终止。
3. `finally` 块中的 `commitState.endCurrentCommit()` 保持不变，确保提交状态始终被清理。

同时更新测试用例，验证全量提交失败时异常正确传播。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+5/-2 lines, 实为 +8/-3)

**修改目的**：区分全量提交与部分提交的失败处理，让全量提交失败向外抛出。

**工作逻辑**：
原实现：
```java
private void commit(boolean partialCommit) {
  try {
    doCommit(partialCommit);
  } catch (Exception e) {
    LOG.warn(
        "Coordinator {} failed to commit for commit {}, will try again next cycle",
        taskId,
        commitState.currentCommitId(),
        e);
  } finally {
    commitState.endCurrentCommit();
  }
}
```
新实现：
```java
private void commit(boolean partialCommit) {
  try {
    doCommit(partialCommit);
  } catch (RuntimeException e) {
    if (partialCommit) {
      LOG.warn(
          "Partial commit {} failed for task {}, will retry",
          commitState.currentCommitId(),
          taskId,
          e);
    } else {
      LOG.error("Commit {} failed for task {}", commitState.currentCommitId(), taskId, e);
      throw e;
    }
  } finally {
    commitState.endCurrentCommit();
  }
}
```
关键变化：
- catch 类型从 `Exception` 收窄为 `RuntimeException`。
- `partialCommit` 为 true 时：WARN 日志 + 吞掉（可重试场景）。
- `partialCommit` 为 false 时：ERROR 日志 + 重新抛出（让 coordinator 线程终止，任务转 FAILED）。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/channel/TestCoordinator.java` (+27/-6 lines)

**修改目的**：更新现有测试以反映异常传播的新行为，并新增 `CommitFailedException` 传播测试。

**工作逻辑**：
- 引入 `assertThatThrownBy`、`doThrow`、`spy` 等 Mockito/assertJ 工具，以及 `CommitFailedException`、`ValidationException` 异常类。
- `testBadDataFile`（已有测试）：原先期望 commit 静默失败、不抛异常；现在改为 `assertThatThrownBy(...).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cannot find partition spec")`，验证错误数据文件导致的异常会向外抛出。
- 新增 `testCommitFailedExceptionPropagates`：使用 Mockito `spy` 包装 table 和 `AppendFiles`，让 `spiedAppend.commit()` 抛出 `CommitFailedException("Glue detected concurrent update")`，验证 `coordinatorTest` 会抛出该异常并包含相应消息。这模拟了 Glue 等目录服务检测到并发更新时的场景。
- `testStaleOffsets`（已有测试，名称从上下文推断）：原先期望 commit 静默失败且表未更新；现在改为 `assertThatThrownBy(...).isInstanceOf(ValidationException.class).hasMessageContaining("stale offsets")`，验证 stale offsets 异常向外抛出。同时调整断言：表仍有 2 个 snapshot，当前 snapshot 的 offsets 属性仍为 `{"0":7}`（即失败的提交未生效）。

## 总结

本提交修复了 Kafka Connect Coordinator 静默吞掉 commit 失败异常的问题，通过区分全量提交与部分提交的失败处理，让全量提交失败时重新抛出异常，使 coordinator 线程终止并将 Connect 任务状态转为 `FAILED`，从而让操作者能够及时感知并处理故障。部分提交失败（超时触发）仍保持可重试的语义。配套测试验证了 `CommitFailedException`、`ValidationException`、`IllegalArgumentException` 等场景下异常的正确传播。这是一个面向生产可靠性的重要修复。
