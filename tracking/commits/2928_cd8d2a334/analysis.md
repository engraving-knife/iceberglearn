# 提交 2928：Core: Fix server side planning on empty tables in CatalogHandlers (#14660)

## 提交信息

- **序号**：2928 / 4088
- **哈希**：cd8d2a3345cb387f1d735763ff3914ac4e2617e2
- **短哈希**：cd8d2a334
- **日期**：2025-11-26 09:01:29 -0700
- **作者**：Drew Gallardo
- **提交说明**：Core: Fix server side planning on empty tables in CatalogHandlers (#14660)
- **PR/Issue**：#14660

## 总体目的

Iceberg 的 REST Catalog 协议支持服务端规划（server-side planning）：客户端发起 `PlanTableScan` 请求，由服务端的 `CatalogHandlers` 执行文件规划（`scan.planFiles()`），把文件扫描任务分组成多个 plan task，存入内存规划状态（`IN_MEMORY_PLANNING_STATE`），并通过 `PlanTableScanResponse` 返回第一批任务以及后续可拉取的 plan task 标识。客户端随后可异步调用 `FetchScanTasks` 拉取剩余任务。

此次修复针对的是**空表（没有任何数据文件的表）**的场景。在改动前，`planFilesFor` 方法将 `scan.planFiles()` 的结果按 `tasksPerPlanTask` 分组后遍历写入 `IN_MEMORY_PLANNING_STATE`。但当表为空时，`scan.planFiles()` 返回的迭代器没有任何元素，分组后的 for 循环体一次也不会执行，导致 `IN_MEMORY_PLANNING_STATE` 中没有为该 `planId` 注册任何 plan task。随后调用方通过 `IN_MEMORY_PLANNING_STATE.initialScanTasksFor(planId)` 获取初始任务时会因找不到对应状态而失败（返回 null 或抛异常），进而使得对空表的同步规划请求出错。此外，旧代码无论任务多少都会无条件调用 `withPlanTasks(nextPlanTask(...))`，在只有一批任务时也会向响应里塞入一个空的 planTasks 列表。

本提交让 `planFilesFor` 显式处理空表情况：当没有文件任务时，向规划状态注册一个空 plan task（标记规划已完成），并返回空任务列表；同时让调用方在后续 plan task 列表为空时不再设置 `withPlanTasks`，使空表规划能正常返回一个 COMPLETED 的、不含后续任务的响应。

## 如何达成设计目的

改动集中在 `CatalogHandlers.java` 的两处方法。核心思路是把 `planFilesFor` 从 `void` 改为返回 `Pair<List<FileScanTask>, String>`（初始文件任务列表 + 首个 plan task 的 key），这样调用方无需再单独通过 `initialScanTasksFor` 反查，避免空表时反查落空。`planFilesFor` 内部新增对空迭代器的判断：为空时注册一个 key 为 `planTaskPrefix + "0"` 的空任务组并返回；非空时在遍历分组的过程中记录首个 plan task key 与首批任务。调用方据此构建响应，并仅在存在后续 plan task 时才填充 `planTasks` 字段。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+48/-22 lines)

**修改目的**：修复空表场景下服务端同步规划失败的问题，并使响应构建更严谨。

**工作逻辑**：

1. **同步规划入口方法（约 685 行起）**：

旧代码先调用 `void planFilesFor(...)`，再通过 `IN_MEMORY_PLANNING_STATE.initialScanTasksFor(planId)` 反查初始任务，并无条件构建带 `withPlanTasks(...)` 的响应。新代码改为：

```java
Pair<List<FileScanTask>, String> initial =
    planFilesFor(configuredScan, planId, table.uuid().toString(),
        tasksPerPlanTask.applyAsInt(configuredScan));
List<String> nextPlanTasks =
    initial.second() == null
        ? Collections.emptyList()
        : IN_MEMORY_PLANNING_STATE.nextPlanTask(initial.second());
PlanTableScanResponse.Builder builder =
    PlanTableScanResponse.builder()
        .withPlanStatus(PlanStatus.COMPLETED)
        .withPlanId(planId)
        .withFileScanTasks(initial.first())
        .withDeleteFiles(
            initial.first().stream()
                .flatMap(task -> task.deletes().stream())
                .distinct()
                .collect(Collectors.toList()))
        .withSpecsById(table.specs());

if (!nextPlanTasks.isEmpty()) {
  builder.withPlanTasks(nextPlanTasks);
}

return builder.build();
```

初始任务直接来自 `planFilesFor` 的返回值（`initial.first()`），首个 plan task key 来自 `initial.second()`。若 key 为 null（防御性处理，理论上非空路径不会出现），后续任务列表置空；否则通过 `nextPlanTask` 取后续 key 列表。只有当后续任务非空时才调用 `withPlanTasks`，避免向响应写入空列表。

2. **`planFilesFor` 方法（约 816 行起）**：签名由 `void` 改为 `Pair<List<FileScanTask>, String>`，并新增空表处理与首任务记录逻辑：

```java
Iterable<FileScanTask> planTasks = scan.planFiles();
String planTaskPrefix = planId + "-" + tableId + "-";

// Handle empty table scans
if (!planTasks.iterator().hasNext()) {
  String planTaskKey = planTaskPrefix + "0";
  // Add empty scan to planning state so async calls know the scan completed
  IN_MEMORY_PLANNING_STATE.addPlanTask(planTaskKey, Collections.emptyList());
  return Pair.of(Collections.emptyList(), planTaskKey);
}

Iterable<List<FileScanTask>> taskGroupings = Iterables.partition(planTasks, tasksPerPlanTask);
int planTaskSequence = 0;
String previousPlanTask = null;
String firstPlanTaskKey = null;
List<FileScanTask> initialFileScanTasks = null;
for (List<FileScanTask> taskGrouping : taskGroupings) {
  String planTaskKey = planTaskPrefix + planTaskSequence++;
  IN_MEMORY_PLANNING_STATE.addPlanTask(planTaskKey, taskGrouping);
  if (previousPlanTask != null) {
    IN_MEMORY_PLANNING_STATE.addNextPlanTask(previousPlanTask, planTaskKey);
  } else {
    firstPlanTaskKey = planTaskKey;
    initialFileScanTasks = taskGrouping;
  }
  previousPlanTask = planTaskKey;
}
return Pair.of(initialFileScanTasks, firstPlanTaskKey);
```

关键点：
- **空表分支**：当 `planFiles()` 迭代器没有元素时，注册一个 key 为 `"{planId}-{tableId}-0"` 的空任务组到 `IN_MEMORY_PLANNING_STATE`。注释说明这是为了让异步调用（`FetchScanTasks`）知道规划已完成；返回空任务列表和该 key。
- **非空分支**：把原先 `String.format("%s-%s-%s", ...)` 改为用预计算的 `planTaskPrefix + planTaskSequence++` 拼接（语义等价但更高效），并在首次迭代时记录 `firstPlanTaskKey` 与 `initialFileScanTasks`，循环结束后返回。这样调用方无需再调用 `initialScanTasksFor` 反查。
- **Javadoc 更新**：为 `planFilesFor` 补充 `@return the initial file scan tasks and the first plan task key` 说明。

## 总结

该提交修复了 REST Catalog 服务端同步规划在空表上的缺陷：原先空表因无文件任务导致 `IN_MEMORY_PLANNING_STATE` 缺少对应 planId 的状态而失败。通过让 `planFilesFor` 显式为空表注册一个空 plan task 并直接返回初始任务/首任务 key，同时让响应构建在无后续任务时省略 `planTasks` 字段，使空表规划能正确返回 COMPLETED 响应，提升了服务端规划的健壮性。
