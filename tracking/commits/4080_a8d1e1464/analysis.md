# 提交 4080：Flink: Add plannedGroups counter metric and log for DataFileRewritePlanner

## 提交信息

- **序号**：4080 / 4088
- **哈希**：a8d1e1464b55a41526bafa0683964fee1b466ec6
- **短哈希**：a8d1e1464
- **日期**：2026-07-22 14:39:37 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Add plannedGroups counter metric and log for DataFileRewritePlanner (#17316)
- **PR/Issue**：#17316

## 总体目的

Flink 的 Iceberg maintenance 模块中，`DataFileRewritePlanner` 负责为 `RewriteDataFiles` 操作规划重写分组（rewrite file groups）。每次规划会产出一组 `RewriteFileGroup`，这些 group 的数量是衡量 rewrite 工作量、并发度以及调优 rewrite 策略的关键指标。

此前 `DataFileRewritePlanner` 已经记录了 errorCounter（错误次数）、addedDataFileNum/Size、removedDataFileNum/Size 等指标，但没有暴露"本次规划产生了多少个 rewrite group"这一信息。运维人员只能从日志里间接观察，无法通过 Flink metrics 系统进行聚合监控和告警。

本提交新增 `plannedGroups` counter 指标，每次规划完成后通过 `plannedGroupsCounter.inc(groups.size())` 累加本次规划出的 group 数量。同时改进了日志格式，把 group 数量也打印出来，便于排查时快速定位规划规模。

## 如何达成设计目的

设计上沿用 `TableMaintenanceMetrics` 中既有的 metrics 注册模式：

1. 在 `TableMaintenanceMetrics` 中新增常量 `PLANNED_GROUPS_COUNTER = "plannedGroups"`，与既有 `ERROR_COUNTER` 等并列；
2. 在 `DataFileRewritePlanner` 的 `open(Configuration)` 方法（Flink 算子初始化钩子）中，复用 `TableMaintenanceMetrics.groupFor(...)` 获取 metric group 并注册 counter，与 `errorCounter` 同样的方式；
3. 在规划出 `groups` 后调用 `plannedGroupsCounter.inc(groups.size())`；
4. 同步改进日志模板，把 `groups.size()` 也加入日志参数。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+8/-1 lines)

**修改目的**：注册并使用 `plannedGroups` counter，改进日志输出。

**工作逻辑**：

1. 新增字段：
```java
private transient Counter plannedGroupsCounter;
```

2. 在 `open` 方法中初始化（与 `errorCounter` 并列）：
```java
this.plannedGroupsCounter =
    TableMaintenanceMetrics.groupFor(getRuntimeContext(), tableName, taskName, taskIndex)
        .counter(TableMaintenanceMetrics.PLANNED_GROUPS_COUNTER);
```

3. 在规划完成后累加 counter 并改进日志：
```java
LOG.info(
    DataFileRewritePlanner.MESSAGE_PREFIX + "Rewrite plan created, {} groups: {}",
    tableName,
    taskName,
    taskIndex,
    ctx.timestamp(),
    groups.size(),
    groups);
plannedGroupsCounter.inc(groups.size());
```

日志模板从 `"Rewrite plan created {}"` 改为 `"Rewrite plan created, {} groups: {}"`，新增 `groups.size()` 参数，让一行日志就能直观看到本次规划规模。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java` (+3/-0 lines)

**修改目的**：定义 `plannedGroups` 指标名常量。

**工作逻辑**：

```java
// DataFileRewritePlanner metrics
public static final String PLANNED_GROUPS_COUNTER = "plannedGroups";
```

放在已有的 `REMOVED_DATA_FILE_*` 常量之后，按算子分组添加注释。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+25/-0 lines)

**修改目的**：在 3 个已有的 rewrite 测试场景中补充对 `plannedGroups` 指标的断言。

**工作逻辑**：

在测试期望的 metrics map 中，3 处都新增了对 `plannedGroups` 的期望值断言（每个场景下 planner 各运行一次，故期望值为 1L）：

```java
.put(
    ImmutableList.of(
        PLANNER_TASK_NAME + "[0]",
        DUMMY_TABLE_NAME,
        DUMMY_TASK_NAME,
        "0",
        PLANNED_GROUPS_COUNTER),
    1L)
```

metric 路径结构与 `ERROR_COUNTER` 等其他指标保持一致：`<taskName>[<index>], <tableName>, <taskName>, "0", <metricName>`。三处测试覆盖了不同 rewrite 场景（成功场景、空输入场景等），都期望 planner 被触发一次产生 1 个 group 计数。同时新增了对常量 `PLANNED_GROUPS_COUNTER` 的静态导入。

## 总结

这是一个运维可观测性增强提交：为 Flink Iceberg maintenance 的 `DataFileRewritePlanner` 新增 `plannedGroups` counter 指标，让运维人员能够通过 Flink metrics 系统监控每次 rewrite 规划产生的 group 数量，便于评估 rewrite 工作量、调优并发配置和排查异常。同时附带改进了日志格式，使规划规模在一行日志中即可读出。实现遵循了已有的 metrics 注册模式，改动小而集中，测试同步更新了 3 个场景的期望值。
