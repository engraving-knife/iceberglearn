# 提交 2459：Close resources only if initialised to avoid rare case NPE (#13670)

## 提交信息

- **序号**：2459 / 4088
- **哈希**：357020aa69333795598c7740dd0e303ad0b87b56
- **短哈希**：357020aa6
- **日期**：2025-08-05 11:32:29 -0700
- **作者**：kumarpritam863
- **提交说明**：Close resources only if initialised to avoid rare case NPE (#13670)
- **PR/Issue**：#13670

## 总体目的

该提交修复了 Kafka Connect Iceberg Sink 中资源关闭时的空指针异常（NPE）问题。在罕见的情况下，`CommitterImpl.close()` 方法可能在资源尚未初始化时被调用，导致 NPE。此外，该提交还对 Coordinator 的线程池管理、终止逻辑和日志进行了全面改进。

提交的核心目标是确保在资源未初始化时安全地跳过关闭操作，同时改进了 Coordinator 的生命周期管理，包括线程池配置的可定制化和终止流程的规范化。

## 如何达成设计目的

整体设计包含以下几个关键改动：

1. **CommitterImpl 防御性关闭**：在 `close()` 方法开头检查 `isInitialized` 标志，如果未初始化则记录警告日志并直接返回，避免对未初始化的资源调用方法导致 NPE。

2. **Coordinator 线程池自定义**：将 Coordinator 中的线程池从 `ThreadPools.newFixedThreadPool()` 替换为直接构造 `ThreadPoolExecutor`，支持通过配置自定义 keep-alive 超时时间，并设置守护线程和有意义的线程名称格式。

3. **Coordinator 终止逻辑重构**：移除了 `terminated` volatile 标志（改为依赖 `exec.shutdownNow()`），新增 `stop()` 方法覆盖来调用 `terminate()`，确保 Coordinator 在 Channel 停止时被正确终止。

4. **CoordinatorThread 简化**：`terminate()` 方法不再直接调用 `coordinator.terminate()`，因为终止逻辑现在通过 `stop()` 方法链处理。

5. **Task ID 支持**：新增 `task.id` 配置属性，在 connector 创建 task 配置时为每个 task 分配唯一 ID，便于日志中区分不同 task 的行为。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConfig.java` (+16/-0 lines)

**修改目的**：新增 task ID 配置和 coordinator executor keep-alive 超时配置。

**工作逻辑**：
- 新增 `TASK_ID` 常量和 `taskId()` 方法，用于获取当前 task 的 ID
- 新增 `COORDINATOR_EXECUTOR_KEEP_ALIVE_TIMEOUT_MS` 配置项，默认值 120000ms（2分钟），控制 coordinator executor 的 keep-alive 时间
- 新增 `keepAliveTimeoutInMs()` 方法获取该配置值

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkConnector.java` (+1/-0 lines)

**修改目的**：在创建 task 配置时注入 task ID。

**工作逻辑**：在为每个 task 生成配置 Map 时，添加 `map.put("task.id", String.valueOf(i))`，为每个 task 分配从 0 开始的序号 ID。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CommitterImpl.java` (+12/-2 lines)

**修改目的**：在 close() 方法中添加初始化检查，避免 NPE；改进日志包含 task 标识。

**工作逻辑**：
```java
@Override
public void close(Collection<TopicPartition> closedPartitions) {
    if (!isInitialized.get()) {
        LOG.warn("Unexpected close() call without resource initialization");
        return;
    }
    // ... 后续关闭逻辑
}
```
日志中也加入了 `config.connectorName()` 和 `config.taskId()` 以便区分不同 task。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java` (+18/-9 lines)

**修改目的**：自定义线程池配置，重构终止逻辑。

**工作逻辑**：
1. 线程池创建改为直接构造 `ThreadPoolExecutor`，支持自定义 keep-alive 超时、守护线程和线程名称格式
2. 移除 `terminated` volatile 标志和相关的 commit 中断检查
3. 新增 `stop()` 方法覆盖，调用 `terminate()` 后再调用 `super.stop()`

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CoordinatorThread.java` (+0/-1 lines)

**修改目的**：简化 CoordinatorThread 的 terminate 方法。

**工作逻辑**：移除了 `coordinator.terminate()` 调用，因为终止逻辑现在通过 `stop()` 方法链处理，避免双重终止。

## 总结

该提交主要修复了 Kafka Connect Iceberg Sink 中 `CommitterImpl.close()` 在资源未初始化时可能导致的 NPE 问题，通过添加初始化检查来防御性处理。同时，该提交还包含了对 Coordinator 线程池管理的全面改进：支持可配置的 keep-alive 超时、使用守护线程、规范化终止流程，以及新增 task ID 用于日志区分。这些改动提升了 Kafka Connect connector 的健壮性和可观测性。
