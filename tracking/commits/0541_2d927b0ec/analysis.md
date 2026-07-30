# 提交 0541：Flink 1.16/1.17 修复 iceberg source plan parallelism 配置不生效

## 提交信息

- **序号**：0541 / 4088
- **哈希**：2d927b0ec7ded6a09f57314ebd05686c86b97f11
- **短哈希**：2d927b0ec
- **日期**：2024-02-26（AuthorDate 2024-02-26 20:58:52 +0800，CommitDate 2024-02-26 13:58:52 +0100）
- **作者**：Reo <leinuowen@gmail.com>
- **提交说明**：Flink 1.16, 1.17: Fix iceberg source plan parallelism not effective (#9811)
- **PR/Issue**：#9811（关联此前 v1.18 的同源修复 PR #9761，commit `1b6826251`）

## 总体目的

本提交要修复的 Bug 是：Flink 集成模块在 v1.16 与 v1.17 两个版本中，用户为 Iceberg source 设置的 **规划并行度（plan parallelism）** 配置不生效。

在 Iceberg-Flink 集成中，`plan parallelism` 实际指 manifest 规划/扫描时所使用的工作线程池大小（worker pool size），由配置项 `table.exec.iceberg.worker-pool-size`（即 `FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE`）控制。该值通过 `FlinkReadConf.workerPoolSize()` 解析后写入 `ScanContext.planParallelism`，随后被四处使用：

- `FlinkInputFormat.createInputSplits()` 创建 `iceberg-plan-worker-pool`
- `IcebergSource.planSplitsForBatch()` 创建批量规划线程池
- `StreamingMonitorFunction.open()` 创建流式监控线程池
- `ContinuousSplitPlannerImpl` 构造函数创建持续规划线程池

因此 worker pool size 直接决定 Iceberg source 在规划阶段的并行度。

**问题表现**：当用户通过以下两种方式之一显式指定 plan parallelism 时，配置值会被丢弃、最终仍使用默认值 `ThreadPools.WORKER_THREAD_POOL_SIZE`：

1. SQL Hint：`SELECT * FROM t /*+ OPTIONS('table.exec.iceberg.worker-pool-size'='10') */`
2. Java API：`IcebergSource.Builder.planParallelism(10)`

其根因是 `FlinkReadConf.workerPoolSize()` 在解析配置时漏注册了 SQL Hint / readOptions 这条优先级最高的来源。

## 如何达成设计目的

Iceberg Flink 模块使用自研的 `FlinkConfParser` 链式 API 统一从多个来源解析配置，配置优先级（从高到低）为：

1. **SQL Hint 选项（options/readOptions map）** —— 通过 `.option(name)` 注册的键名
2. **Flink 全局配置（ReadableConfig）** —— 通过 `.flinkConfig(ConfigOption)` 注册
3. **Iceberg 表属性（table properties）** —— 通过 `.tableProperty(name)` 注册
4. **默认值** —— 通过 `.defaultValue(value)` 注册

核心解析逻辑位于 `FlinkConfParser.ConfParser.parse()` 方法（v1.17 路径 `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/FlinkConfParser.java` 第 302-327 行）：依次遍历 `optionNames` → `configOption` → `tablePropertyName` → `defaultValue`，找到第一个非空值即返回。

**关键事实**：`IcebergSource.Builder.planParallelism(int)` 的实现是把值写入 `readOptions` map（键为 `table.exec.iceberg.worker-pool-size`），而 SQL Hint `/*+ OPTIONS(...) */` 同样进入 readOptions map。也就是说，"用户层指定的 plan parallelism"必须通过 `.option()` 路径才能被解析器读到。

修复前的 `workerPoolSize()` 只调用了 `.flinkConfig(...)` 与 `.defaultValue(...)`，没有调用 `.option(...)`，导致 readOptions map 里的 `table.exec.iceberg.worker-pool-size` 永远不会被检查——解析器跳过第 1 优先级直接到第 2 优先级（Flink 全局配置），如果全局也没设，就回落到默认值。用户通过 SQL Hint 或 Java API 显式指定的 plan parallelism 因此被静默丢弃。

修复方式极为精简：在链式调用中补一行 `.option(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key())`，让解析器在最高优先级层先去 readOptions 中查 `table.exec.iceberg.worker-pool-size`，命中即返回；未命中再回退到 Flink 全局配置和默认值。这与 v1.18 在 PR #9761 中的修复完全一致，是对 v1.16/v1.17 的回port。

## 修改详情

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/FlinkReadConf.java`

**修改目的**：让 `workerPoolSize()` 能从 SQL Hint / readOptions map 中读取用户显式指定的 plan parallelism。

**工作逻辑**：在 `workerPoolSize()` 方法的解析链中补一行 `.option(...)`：

```java
public int workerPoolSize() {
  return confParser
      .intConf()
      .option(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key())  // 新增
      .flinkConfig(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE)
      .defaultValue(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.defaultValue())
      .parse();
}
```

补上后，`ConfParser.parse()` 会先在 `options` map（即 readOptions）中按键名 `table.exec.iceberg.worker-pool-size` 查找：
- 若用户通过 SQL Hint 或 `IcebergSource.Builder.planParallelism(int)` 设置过，命中并以 `Integer::parseInt` 转换后返回该值；
- 否则继续向下查 Flink 全局配置 `ReadableConfig`，再查表属性，最终回落到默认值。

修复后的解析优先级与本类中其它读取配置项（如 `limit()`、`caseSensitive()`、`includeColumnStats()`、`maxAllowedPlanningFailures()`）保持一致——这些方法都同时注册了 `.option(...)` 与 `.flinkConfig(...)`，唯独 `workerPoolSize()` 此前漏掉了 `.option(...)`。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/FlinkReadConf.java`

**修改目的**：与 v1.16 完全相同。

**工作逻辑**：v1.16 与 v1.17 的 `FlinkReadConf.java` 文件内容一致（diff 中两个文件的 index 哈希同为 `d53ea73f9`→`804a956ec`），改动也完全相同——同样在 `workerPoolSize()` 方法中补一行 `.option(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key())`。

## 小结

**成效**：本提交以最小代价（每个版本 1 行、共 2 行新增）修复了 v1.16 与 v1.17 上 plan parallelism 配置不生效的 Bug。修复后用户通过 SQL Hint 或 Java API `IcebergSource.Builder.planParallelism(int)` 设置的工作线程池大小能被正确解析并应用到 manifest 规划/扫描阶段，进而真正控制 source 的规划并行度。

**影响范围**：仅影响 Flink 集成模块的 v1.16、v1.17 两个子模块的 `FlinkReadConf.workerPoolSize()` 解析行为；不涉及任何序列化格式、API 兼容性变更，无破坏性影响。修复方式与同源 PR #9761（v1.18）一致，三个 Flink 版本语义对齐。

**回迁到 1.4.x 的注意事项**：

1. 本提交本身就是从 main 分支回port 到 1.4.x 维护分支的修复，1.4.x 上的 v1.16/v1.17 已经包含此修复（即本提交内容）。
2. **需注意 v1.15 同样存在此 Bug 但本 PR 未覆盖**。在 1.4.x 分支中 `flink/v1.15/flink/src/main/java/org/apache/iceberg/flink/FlinkReadConf.java` 的 `workerPoolSize()`（第 208 行附近）同样只有 `.flinkConfig(...)` 而没有 `.option(...)`，且 `flink/v1.15/.../source/ScanContext.java` 中 `resolveConfig` 同样调用 `flinkReadConf.workerPoolSize()` 作为 `planParallelism`，`flink/v1.15/.../source/IcebergSource.java` 的 `Builder.planParallelism(int)` 同样把值写入 readOptions。因此 v1.15 用户若通过 SQL Hint 或 Java API 设置 plan parallelism 也会遇到配置不生效的问题。如果 1.4.x 仍维护 v1.15，建议把同样的修复（补一行 `.option(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key())`）一并应用到 v1.15 的 `FlinkReadConf.java`。
3. 回迁时无需改动测试，但建议新增覆盖用例：通过 `/*+ OPTIONS('table.exec.iceberg.worker-pool-size'='N') */` 设置后断言 `FlinkReadConf.workerPoolSize()` 返回 N，以及 `IcebergSource.Builder.planParallelism(N)` 后断言 `ScanContext.planParallelism()` 为 N。
