# 提交 1150：Flink: Port #10484 to v1.19 (#11010) (#11117)

## 提交信息

- **序号**：1150
- **哈希**：ab0594bf71a6884ee0e196470bfe4b4d3baa58b9
- **短哈希**：ab0594bf7
- **日期**：2024-09-12（Thu Sep 12 16:56:17 2024 +0300）
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Flink: Port #10484 to v1.19 (#11010) (#11117)
- **PR/Issue**：#11117（基于 #11010 / #10484）

## 总体目的

前一个提交 1148（#11010）为 `flink/v1.20` 引入了 Flink Table Maintenance 模块的 `LockRemover` 算子及相关基础设施（`TaskResult`、`TableMaintenanceMetrics` 新常量、`TriggerManager` 重命名、`ManualSource` watermark 修复、`JdbcLockFactory` Javadoc、`TestLockRemover` 测试）。但 Iceberg 的 Flink 集成同时维护 v1.18、v1.19、v1.20 三个版本，每个版本的代码相互独立（同一文件在三处目录各有一份拷贝）。

本提交将 #11010 的全部改动**移植（port）到 `flink/v1.19`**，让 v1.19 与 v1.20 的 maintenance operator 子包保持一致。这样使用 Flink 1.19 的用户也能使用 LockRemover 算子。

同时，移植过程中发现 v1.19 的 Flink API 不支持 v1.20 测试中使用的 `CheckpointingOptions.CHECKPOINTING_INTERVAL` 配置项（这是 Flink 较新版本才引入的配置 key），因此把 v1.20 测试中对应的 checkpoint 间隔设置方式改为更通用的 `env.enableCheckpointing(10)`，并同步回 v1.20 的 TestLockRemover，让两个版本的测试代码保持一致。这就是为什么本提交同时修改了 v1.19（新增 7 文件 + 改 1 文件）和 v1.20（改 1 文件 TestLockRemover）。

注：提交说明中的 `#10484` 是最初的 PR 编号，`#11010` 是 v1.20 上的实现 PR，`#11117` 是本次移植到 v1.19 的 PR。

## 如何达成设计目的

1. **v1.19 完整移植**：将 v1.20 在 #11010 中新增/修改的 7 个文件逐一拷贝到 `flink/v1.19/flink/src/.../maintenance/operator/`：
   - 新增 `LockRemover.java`（与 v1.20 完全一致）；
   - 新增 `TaskResult.java`（与 v1.20 完全一致）；
   - 新增 `TestLockRemover.java`（与 v1.20 几乎一致，下面会说明差异）；
   - 修改 `TableMaintenanceMetrics.java`（新增三个度量常量）；
   - 修改 `TriggerManager.java`（`taskNames` → `maintenanceTaskNames` 重命名）；
   - 修改 `ManualSource.java`（watermark 发送逻辑修复）；
   - 修改 `JdbcLockFactory.java`（补充 Javadoc）。
2. **测试 checkpoint 设置统一**：v1.20 原 `TestLockRemover.testInSink` 使用：
   ```java
   config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofMillis(10));
   ```
   该配置 key 在 Flink 1.19 中不存在。改为更通用的：
   ```java
   env.enableCheckpointing(10);
   ```
   并同步回 v1.20 的 TestLockRemover（删除 `import java.time.Duration;`），让 v1.19 与 v1.20 测试代码一致。
3. **保持其余文件完全一致**：v1.19 的 `LockRemover.java`、`TaskResult.java`、`TableMaintenanceMetrics.java`、`TriggerManager.java`、`ManualSource.java`、`JdbcLockFactory.java` 内容与 v1.20 完全相同（哈希一致），便于后续跨版本同步。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/JdbcLockFactory.java`

**修改目的**：补充 Javadoc（与 v1.20 一致）。

**工作逻辑**：在类声明前新增：
```java
/**
 * JDBC table backed implementation of the {@link
 * org.apache.iceberg.flink.maintenance.operator.TriggerLockFactory}.
 */
```

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/LockRemover.java`（新增 144 行）

**修改目的**：移植 v1.20 的 LockRemover 算子到 v1.19。

**工作逻辑**：与 v1.20 完全一致（文件 index `3c3761ef2` 与 v1.20 相同）。继承 `AbstractStreamOperator<Void>` 实现 `OneInputStreamOperator<TaskResult, Void>`，在 `processElement` 中释放任务锁并更新度量，在 `processWatermark` 中根据 watermark 是否超过 `lastProcessedTaskStartEpoch` 决定是否同时释放 lock 与 recoveryLock。详细逻辑见提交 1148 的分析。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResult.java`（新增 65 行）

**修改目的**：移植 v1.20 的 TaskResult 数据类到 v1.19。

**工作逻辑**：与 v1.20 完全一致。不可变 POJO，含 `taskIndex`、`startEpoch`、`success`、`exceptions` 四字段。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java`

**修改目的**：新增 LockRemover 度量指标常量。

**工作逻辑**：与 v1.20 一致，追加：
```java
// LockRemover metrics
public static final String SUCCEEDED_TASK_COUNTER = "succeededTasks";
public static final String FAILED_TASK_COUNTER = "failedTasks";
public static final String LAST_RUN_DURATION_MS = "lastRunDurationMs";
```

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TriggerManager.java`

**修改目的**：将 `taskNames` 重命名为 `maintenanceTaskNames`。

**工作逻辑**：与 v1.20 一致——字段、构造参数、本地变量、错误消息、`open()` 中 stream 来源全部同步改名。无行为变更。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/ManualSource.java`

**修改目的**：修复并扩展测试用 Source 的 watermark 发送逻辑。

**工作逻辑**：与 v1.20 一致——`pollNext` 重构为三分支，支持通过 `(null, timestamp)` 发送 watermark、通过 `(null, null)` 表示输入结束；新增 `import org.apache.flink.api.common.eventtime.Watermark`；SourceReader 改用菱形语法 `new SourceReader<>()`；加 `@SuppressWarnings("unchecked")`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemover.java`（新增 378 行）

**修改目的**：为 v1.19 的 LockRemover 提供测试覆盖。

**工作逻辑**：与 v1.20 版本基本一致，包含 `testProcess`、`testInSink`、`testMetrics`、`testRecovery` 四个测试用例，以及 `TestingLockFactory`、`TestingLock`、`SinkTest` 辅助类。**唯一差异**在 `testInSink` 中设置 checkpoint 间隔的方式：

- v1.20 原版（提交 1148）：`config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofMillis(10));`
- v1.19 版本（本提交）：`env.enableCheckpointing(10);`

理由是 `CheckpointingOptions.CHECKPOINTING_INTERVAL` 是 Flink 较新版本（1.20+）才有的配置 key，v1.19 不支持。`env.enableCheckpointing(long interval)` 是更通用的 API，在 v1.18/v1.19/v1.20 都可用。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestLockRemover.java`（修改）

**修改目的**：把 v1.20 测试中的 checkpoint 设置方式改为与 v1.19 一致，保持两版本测试代码同步。

**工作逻辑**：
- 删除 `import java.time.Duration;`（不再使用）；
- 将 `config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofMillis(10));` 改为 `env.enableCheckpointing(10);`；
- 调整顺序：原本先 set config 再创建 env，现在先创建 env 再调用 `env.enableCheckpointing(10)`。

这样 v1.19 与 v1.20 的 TestLockRemover 内容完全一致（除包路径不同），便于后续跨版本同步。

## 小结

- **成效**：将 #11010 引入的 LockRemover 算子及相关基础设施完整移植到 `flink/v1.19`，让 Flink 1.19 用户也能使用 Flink Table Maintenance 模块的锁释放与度量收集能力；同时把 v1.20 测试中依赖 Flink 1.20+ API 的 checkpoint 配置改为通用 API `env.enableCheckpointing(10)`，并同步回 v1.20，让两版本测试代码保持一致。
- **影响范围**：仅 `flink/v1.19`（新增 4 文件 + 修改 3 文件，+614 行）与 `flink/v1.20`（修改 1 文件 TestLockRemover，+1/-2 行）。所有 v1.19 的非测试 Java 文件与 v1.20 完全一致，便于后续维护。
- **回迁到 1.4.x 的注意事项**：
  - 这是把 v1.20 的功能同步到 v1.19 的移植提交。1.4.x 是否需要回迁取决于其维护的 Flink 版本范围：
    - 若 1.4.x 同时维护 v1.19 与 v1.20，且已回迁了提交 1148（v1.20 的 LockRemover），则**应一并回迁本提交**（v1.19 移植 + v1.20 测试调整），保持两版本一致；
    - 若 1.4.x 只维护 v1.20 不维护 v1.19，则本提交中 v1.19 部分不适用，但 v1.20 TestLockRemover 的 checkpoint 设置方式调整（改用 `env.enableCheckpointing`）可单独回迁，让 1.4.x 的 v1.20 测试更健壮；
    - 若 1.4.x 维护 v1.18，则需要类似的本移植工作——但本提交未涉及 v1.18，需额外人工 port。
  - 移植时需注意 Flink API 版本差异：`env.enableCheckpointing(long)` 是兼容性更好的 API，1.4.x 移植时建议采用此方式，避免依赖具体 Flink 版本的配置 key。
  - 该模块仍处于 `@Experimental` 阶段，1.4.x 用户使用前应评估稳定性。
