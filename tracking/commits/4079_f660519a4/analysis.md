# 提交 4079：Kafka Connect: Set engine metadata in EnvironmentContext

## 提交信息

- **序号**：4079 / 4088
- **哈希**：f660519a4e027a30a74c08b9259ce8ce0ae87755
- **短哈希**：f660519a4
- **日期**：2026-07-21 10:28:56 -0600
- **作者**：ajreid21
- **提交说明**：Kafka Connect: Set engine metadata in EnvironmentContext (#17195)
- **PR/Issue**：#17195

## 总体目的

Iceberg 的 `EnvironmentContext` 是一个全局的运行时上下文，用于在 commit 时记录"是哪个引擎、哪个版本"提交了这次数据变更。这些信息会写入 Iceberg 表的 commit metadata（如 properties 中的 `engine-name`、`engine-version`），便于审计、追踪和调试数据来源。

其他引擎（如 Spark、Flink、Trino 等）在初始化时都会把自己的引擎名和版本写入 `EnvironmentContext`。但 Kafka Connect sink 此前没有做这件事，导致由 Kafka Connect 写入的 Iceberg 表 commit metadata 中引擎信息缺失（或保留为默认值），无法识别数据来源是 Kafka Connect。

本提交在 `IcebergSinkTask.start(...)` 时将 `ENGINE_NAME` 设为 `"kafka-connect"`、`ENGINE_VERSION` 设为 Kafka Connect 运行时的版本（通过 `AppInfoParser.getVersion()` 获取，这是 Kafka Connect 框架自带 API，返回当前 Connect 框架版本号），使后续所有 commit 都自动带上 Kafka Connect 的运行时上下文。

## 如何达成设计目的

实现非常简洁：在 `IcebergSinkTask.start` 方法最开头（在加载 config、catalog、committer 之前）调用 `EnvironmentContext.put(...)` 写入两个键值。`EnvironmentContext` 是一个进程级的静态 Map，写入后整个 JVM 内的所有 Iceberg commit 操作都会读取这两个值并附加到 commit metadata。

版本号通过 Kafka 自带的 `org.apache.kafka.common.utils.AppInfoParser.getVersion()` 获取，确保与实际运行的 Kafka Connect 框架版本一致。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/IcebergSinkTask.java` (+4/-0 lines)

**修改目的**：在任务启动时设置引擎元数据。

**工作逻辑**：

新增两行导入：
```java
import org.apache.iceberg.EnvironmentContext;
import org.apache.kafka.common.utils.AppInfoParser;
```

在 `start(Map<String, String> props)` 方法体最开头加入：
```java
@Override
public void start(Map<String, String> props) {
  EnvironmentContext.put(EnvironmentContext.ENGINE_NAME, "kafka-connect");
  EnvironmentContext.put(EnvironmentContext.ENGINE_VERSION, AppInfoParser.getVersion());
  this.config = new IcebergSinkConfig(props);
  ...
}
```

放在方法开头确保即使后续初始化失败，引擎上下文也已设置（虽然此场景下意义有限，但保持设置时机最早）。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/TestIcebergSinkTask.java` (+67/-0 lines, new file)

**修改目的**：新增测试类验证 `start` 方法正确设置了 `EnvironmentContext`。

**工作逻辑**：

新增 `TestIcebergSinkTask` 测试类，包含一个测试 `startSetsEnvironmentContext`：

1. 先 `EnvironmentContext.remove(...)` 清除并保存之前的 `ENGINE_NAME` 和 `ENGINE_VERSION` 值，避免受其他测试污染；
2. 创建 `IcebergSinkTask` 实例并调用 `start(taskConfig())`；
3. 断言 `EnvironmentContext.get()` 中：
   - `ENGINE_NAME` 等于 `"kafka-connect"`
   - `ENGINE_VERSION` 等于 `AppInfoParser.getVersion()`（运行时实际版本）
4. 在 finally 块中 `task.stop()` 并恢复原始 `EnvironmentContext` 状态，保证测试隔离。

辅助方法：
- `taskConfig()`：使用 `ImmutableMap` 构造最小可用配置，使用 `InMemoryCatalog` 作为 catalog 实现避免依赖外部存储；
- `restoreEnvironmentContext(key, value)`：恢复或清除指定 key，保证测试不污染全局状态。

## 总结

这是一个小而重要的可观测性改进：让 Kafka Connect sink 写入的 Iceberg commit 自动包含 `engine-name=kafka-connect` 和 `engine-version=<kafka version>` 元数据，与其他引擎行为对齐，便于审计数据来源。实现仅 2 行核心代码，配套测试妥善处理了全局 `EnvironmentContext` 的保存与恢复，避免测试间相互污染。
