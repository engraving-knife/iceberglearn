# 提交 3001：Core: disable flaky test for batchScan RemoteScanPlanning (#14826)

## 提交信息

- **序号**：3001 / 4088
- **哈希**：7418f4986de820efe04570b28392a0db2f640ec9
- **短哈希**：7418f4986
- **日期**：2025-12-11 15:12:14 -0800
- **作者**：Prashant Singh
- **提交说明**：Core: disable flaky test for batchScan RemoteScanPlanning (#14826)
- **PR/Issue**：#14826（关联 issue #14823）

## 总体目的

在提交 2998（PR #14776）中，作者为 `RESTTable` 实现了 `newBatchScan()`，并新增了一个参数化测试 `scanPlanningWithBatchScan`（位于 `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java`），用 `@ParameterizedTest @EnumSource(PlanningMode.class)` 覆盖不同 REST 扫描计划模式下的 batch scan 行为。

但该测试合入后被发现存在 flaky（不稳定）现象——在某些运行条件下会失败。这种 flaky 测试的危害是显而易见的：CI 会在没有真实代码回归的情况下时红时绿，干扰提交者判断，浪费排查时间，并可能让团队对 CI 红色信号产生麻木。在根本原因尚未定位、修复方案还在追踪中（issue #14823）的当下，社区标准的应急做法是先用 `@Disabled` 把这个 flaky 测试暂时停用，避免它继续污染 CI，同时通过注释把追踪 issue 显式记录下来，待 #14823 修复后再恢复。

本提交的目的就是：暂时禁用 `scanPlanningWithBatchScan` 这个 flaky 测试，并在 `@Disabled` 注解中标注追踪 issue，让 CI 恢复稳定，同时保留测试代码本身以便后续修复时直接启用。

## 如何达成设计目的

整体思路是"用 JUnit 5 的 `@Disabled` 注解标记测试，附上原因与追踪 issue"。`TestRESTScanPlanning.java` 此前已经导入了 `org.junit.jupiter.api.Disabled`（同文件第 463 行的 `metadataTablesWithRemotePlanning` 变体也用过 `@Disabled("Pruning files based on columns is not yet supported in REST scan planning")`），所以新增一处 `@Disabled` 不需要调整 import。改动只在 `scanPlanningWithBatchScan` 方法上方加一行注解。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+1/-0 lines)

**修改目的**：暂时禁用 flaky 的 `scanPlanningWithBatchScan` 参数化测试。

**工作逻辑**：

在 `void scanPlanningWithBatchScan(...)` 方法上方、`@ParameterizedTest` 之前新增一行：

```java
@Disabled("Temporarily disabled: Fix tracked via issue-14823")
```

JUnit 5 的 `@Disabled` 注解作用于测试方法时，会让该方法（包括其所有参数化变体）在测试执行阶段被跳过，并在测试报告中显示禁用原因。这里把追踪 issue 编号 14823 写进原因字符串，便于：

1. CI 报告中直接看到禁用原因和追踪入口；
2. 后续修复 #14823 时，可以通过 `grep issue-14823` 直接定位到被禁用的测试方法，恢复启用。

`@EnumSource(PlanningMode.class)` 提供的所有 `PlanningMode` 变体（同步/异步等）都会被一并跳过，确保 CI 不再因这个 flaky 测试变红。注解顺序上 `@Disabled` 放在 `@ParameterizedTest` 之前，符合 JUnit 5 注解生效约定。

## 总结

该提交通过一行 `@Disabled("Temporarily disabled: Fix tracked via issue-14823")` 暂时禁用了提交 2998 引入的 flaky 参数化测试 `scanPlanningWithBatchScan`，避免它继续污染 CI。禁用原因显式标注了追踪 issue #14823，便于后续修复时定位并恢复测试。这是社区处理 flaky 测试的标准应急手段——保留测试代码、暂停执行、追踪根因。
