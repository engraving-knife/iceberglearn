# 提交 2869：Core: Add server-side implementation of remote scan planning to RESTCatalogAdapter (#14480)

## 提交信息

- **序号**：2869 / 4088
- **哈希**：966003c47b92a2a6f735a72f36f405f69463f469
- **短哈希**：966003c47
- **日期**：2025-11-12 04:27:16 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Add server-side implementation of remote scan planning to RESTCatalogAdapter (#14480)
- **PR/Issue**：#14480

## 总体目的

这个提交为 RESTCatalogAdapter 添加了服务端远程扫描计划（remote scan planning）的实现。这是 Iceberg REST Catalog 规范中定义的扫描计划 API 的服务端实现，使 REST Catalog 能够支持服务端扫描计划流程。

远程扫描计划是 Iceberg REST Catalog 的一个重要功能，它将扫描计划（scan planning）的逻辑从客户端移到服务端。服务端负责根据客户端的请求（指定快照、筛选条件、列选择等）执行扫描计划，生成文件扫描任务（FileScanTask），并将结果分页返回给客户端。这减少了客户端需要处理的数据量和计算量，特别适用于大规模数据集和瘦客户端场景。

该实现支持同步和异步两种计划模式：
- **同步模式**：服务端立即执行扫描计划并返回第一批结果。
- **异步模式**：服务端提交异步计划任务，返回 planId，客户端后续轮询获取结果。

结果通过 plan task 机制分页返回，每个 plan task 包含一组 FileScanTask。

## 如何达成设计目的

整体设计包含四个核心组件：

1. **CatalogHandlers**：新增四个静态方法处理扫描计划的核心逻辑——`planTableScan`（计划扫描）、`fetchPlanningResult`（获取异步计划结果）、`fetchScanTasks`（获取扫描任务分页）、`cancelPlanTableScan`（取消计划）。

2. **InMemoryPlanningState**：新建单例类，管理扫描计划的状态，包括 plan task 到 FileScanTask 的映射和 plan task 之间的分页链接关系。

3. **RESTCatalogAdapter**：新增四个路由处理分支（PLAN_TABLE_SCAN、FETCH_PLANNING_RESULT、FETCH_SCAN_TASKS、CANCEL_PLAN_TABLE_SCAN），以及 PlanningBehavior 接口用于控制同步/异步行为和分页大小。

4. **Route**：新增四个路由枚举值，对应 REST API 端点。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+160/-0 lines)

**修改目的**：实现扫描计划的核心服务端逻辑。

**工作逻辑**：

1. **静态字段**：
   - `IN_MEMORY_PLANNING_STATE`：InMemoryPlanningState 单例实例，管理计划状态。
   - `ASYNC_PLANNING_POOL`：单线程 ExecutorService，用于异步执行扫描计划。

2. **`planTableScan()` 方法**：核心入口方法。加载表并创建 TableScan，根据请求设置快照、列选择、筛选条件、统计字段和大小写敏感性。通过 `shouldPlanAsync` 谓词判断是否异步执行：
   - 异步：生成 planId，提交到线程池执行，返回 SUBMITTED 状态和 planId。
   - 同步：生成 planId，调用 `planFilesFor()` 执行计划，从 InMemoryPlanningState 获取初始结果，返回 COMPLETED 状态和第一批 FileScanTask。

3. **`fetchPlanningResult()` 方法**：获取异步计划的结果。根据 planId 从 InMemoryPlanningState 获取初始扫描任务，返回 COMPLETED 状态的结果。

4. **`fetchScanTasks()` 方法**：获取指定 plan task 的扫描任务。根据 planTask key 从 InMemoryPlanningState 获取对应的 FileScanTask 列表，同时返回下一个 plan task 用于分页。

5. **`cancelPlanTableScan()` 方法**：取消计划，从 InMemoryPlanningState 中移除所有相关状态。

6. **`planFilesFor()` 方法**：执行实际的扫描计划。使用 `tableScan.planFiles()` 获取所有 FileScanTask，通过 `Iterables.partition()` 按 `tasksPerPlanTask` 大小分组。每个分组分配一个 plan task key（格式为 `planId-tableUuid-sequence`），存入 InMemoryPlanningState，并建立前后 plan task 的链接关系。

7. **`asyncPlanFiles()` 方法**：将 `planFilesFor()` 提交到异步线程池执行。

8. **`clearPlanningState()` 方法**：清理计划状态和关闭线程池，在 RESTCatalogAdapter 关闭时调用。

### `core/src/main/java/org/apache/iceberg/rest/InMemoryPlanningState.java` (+132/-0 lines, new file)

**修改目的**：管理扫描计划状态的内存存储。

**工作逻辑**：使用单例模式（双重检查锁定），维护两个并发 Map：
- `planTaskToFileScanTasks`：plan task key 到 FileScanTask 列表的映射。
- `planTaskToNext`：plan task key 到下一个 plan task key 的映射（分页链表）。

关键方法：
- `addPlanTask()`：添加 plan task 和对应的扫描任务。
- `addNextPlanTask()`：建立 plan task 之间的分页链接。
- `fileScanTasksForPlanTask()`：根据 plan task key 获取扫描任务，找不到时抛出 `NoSuchPlanTaskException`。
- `nextPlanTask()`：获取下一个 plan task key，用于分页。
- `initialScanTasksFor()`：根据 planId 找到初始 plan task（序列号为 "0" 的任务），找不到时抛出 `NoSuchPlanIdException`。通过解析 plan task key（以 "-" 分隔，最后一段为序列号）来识别初始任务。
- `removePlan()`：移除指定 planId 的所有相关状态。
- `clear()`：清空所有状态。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+69/-0 lines)

**修改目的**：在 RESTCatalogAdapter 中添加扫描计划路由处理和 PlanningBehavior 接口。

**工作逻辑**：

1. **异常映射**：在异常到 HTTP 状态码的映射中添加 `NoSuchPlanIdException` 和 `NoSuchPlanTaskException` 都映射到 404。

2. **PlanningBehavior 接口**：新增接口，定义两个默认方法：
   - `numberFileScanTasksPerPlanTask()`：每个 plan task 包含的 FileScanTask 数量，默认 100。
   - `shouldPlanTableScanAsync(TableScan)`：是否异步执行扫描计划，默认 false（同步）。
   - `planningBehavior()` 方法返回默认实现，子类可覆盖以自定义行为。

3. **路由处理**：在 `handleRequest()` 方法中新增四个 case 分支：
   - `PLAN_TABLE_SCAN`：调用 `CatalogHandlers.planTableScan()`，传入 PlanningBehavior 的异步判断和分页大小。
   - `FETCH_PLANNING_RESULT`：调用 `CatalogHandlers.fetchPlanningResult()`。
   - `FETCH_SCAN_TASKS`：调用 `CatalogHandlers.fetchScanTasks()`。
   - `CANCEL_PLAN_TABLE_SCAN`：调用 `CatalogHandlers.cancelPlanTableScan()`。

4. **关闭清理**：在 `close()` 方法中调用 `CatalogHandlers.clearPlanningState()` 清理计划状态。

### `core/src/test/java/org/apache/iceberg/rest/Route.java` (+24/-1 lines)

**修改目的**：添加四个新的 REST API 路由定义。

**工作逻辑**：新增四个路由枚举值：
- `PLAN_TABLE_SCAN`：POST 方法，路径 `V1_TABLE_SCAN_PLAN_SUBMIT`，请求体 PlanTableScanRequest，响应 PlanTableScanResponse。
- `FETCH_PLANNING_RESULT`：GET 方法，路径 `V1_TABLE_SCAN_PLAN`，无请求体，响应 FetchPlanningResultResponse。
- `FETCH_SCAN_TASKS`：POST 方法，路径 `V1_TABLE_SCAN_PLAN_TASKS`，请求体 FetchScanTasksRequest，响应 FetchScanTasksResponse。
- `CANCEL_PLAN_TABLE_SCAN`：DELETE 方法，路径 `V1_TABLE_SCAN_PLAN`，无请求体和响应。

## 总结

这个提交为 RESTCatalogAdapter 添加了完整的服务端远程扫描计划实现，使 REST Catalog 能够支持服务端扫描计划流程。实现包含四个核心 API 端点（计划扫描、获取计划结果、获取扫描任务分页、取消计划），支持同步和异步两种模式，通过 InMemoryPlanningState 单例管理计划状态和分页。PlanningBehavior 接口提供了可扩展的配置点，允许子类自定义异步行为和分页大小。这是 Iceberg REST Catalog 远程扫描计划功能的重要基础设施，提交共修改 4 个文件，新增 384 行代码。
