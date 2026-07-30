# 提交 4084：Flink: Backport Add plannedGroups counter metric and log for DataFileRewritePlanner to 2.0 and 1.20

## 提交信息

- **序号**：4084 / 4088
- **哈希**：cd708ae33175fad24e0b4dc2f3395167662c1121
- **短哈希**：cd708ae33
- **日期**：2026-07-23 17:38:44 +0200
- **作者**：GuoYu
- **提交说明**：Flink: Backport Add plannedGroups counter metric and log for DataFileRewritePlanner to 2.0 and 1.20 (#17335)
- **PR/Issue**：#17335（backport of #17316）

## 总体目的

这是 PR #17316（即本批次第 4080 号提交）的回溯（backport）。原 PR 在 `flink/v2.1` 目录下为 `DataFileRewritePlanner` 新增了 `plannedGroups` counter 指标并改进了日志。但 Iceberg 同时维护多个 Flink 版本的对接模块（v1.20、v2.0、v2.1），需要在所有受支持的版本上保持功能一致。

本提交把同样的改动同步到 `flink/v1.20` 和 `flink/v2.0` 两个目录，让使用 Flink 1.20 和 2.0 的用户也能获得 `plannedGroups` 指标和改进后的日志，避免不同 Flink 版本之间运维可观测性能力出现差异。

回溯的动机和原 PR 一致：让运维人员能够通过 Flink metrics 系统监控每次 rewrite 规划产生的 group 数量，便于评估 rewrite 工作量、调优并发配置和排查异常。

## 如何达成设计目的

直接把 v2.1 中的三处改动（`DataFileRewritePlanner` 新增 counter 字段与使用、`TableMaintenanceMetrics` 新增常量、`TestRewriteDataFiles` 新增断言）逐字复制到 v1.20 和 v2.0 对应文件。两个 Flink 版本的 maintenance 模块代码结构与 v2.1 完全一致，因此回溯不涉及任何适配性修改。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+8/-1 lines)

**修改目的**：在 v1.20 版本注册并使用 `plannedGroups` counter，改进日志。

**工作逻辑**：与 v2.1 完全一致：
1. 新增字段 `private transient Counter plannedGroupsCounter;`；
2. 在 `open` 方法中通过 `TableMaintenanceMetrics.groupFor(...).counter(TableMaintenanceMetrics.PLANNED_GROUPS_COUNTER)` 初始化；
3. 在规划完成后 `plannedGroupsCounter.inc(groups.size())`；
4. 日志模板从 `"Rewrite plan created {}"` 改为 `"Rewrite plan created, {} groups: {}"`，新增 `groups.size()` 参数。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java` (+3/-0 lines)

**修改目的**：在 v1.20 版本定义 `plannedGroups` 指标名常量。

**工作逻辑**：新增：
```java
// DataFileRewritePlanner metrics
public static final String PLANNED_GROUPS_COUNTER = "plannedGroups";
```

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+25/-0 lines)

**修改目的**：在 v1.20 版本的 3 个 rewrite 测试场景中补充对 `plannedGroups` 指标的断言。

**工作逻辑**：在每个测试期望的 metrics map 中新增对 `plannedGroups` 期望值为 1L 的断言，路径结构与 v2.1 一致：`<PLANNER_TASK_NAME>[0], <DUMMY_TABLE_NAME>, <DUMMY_TASK_NAME>, "0", PLANNED_GROUPS_COUNTER`。同时新增 `PLANNED_GROUPS_COUNTER` 的静态导入。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/DataFileRewritePlanner.java` (+8/-1 lines)

**修改目的**：v2.0 版本同 v1.20 的改动。

**工作逻辑**：与上述 v1.20 改动完全一致。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TableMaintenanceMetrics.java` (+3/-0 lines)

**修改目的**：v2.0 版本新增 `PLANNED_GROUPS_COUNTER` 常量。

**工作逻辑**：与 v1.20 改动一致。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestRewriteDataFiles.java` (+25/-0 lines)

**修改目的**：v2.0 版本测试断言补充。

**工作逻辑**：与 v1.20 测试改动一致。

## 总结

这是 #17316 的回溯提交，把 `plannedGroups` counter 指标和日志改进同步到 Flink v1.20 和 v2.0 两个版本的 maintenance 模块，确保三个受支持的 Flink 版本（1.20/2.0/2.1）在 rewrite planner 可观测性上行为一致。改动内容与原 PR 完全相同，只是按目录复制，无适配性调整。这对在多 Flink 版本环境下统一运维监控体系有实际价值。
