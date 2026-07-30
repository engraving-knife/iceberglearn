# 提交 2989：Core: REST Scan Planning Task Implementation (#13400)

## 提交信息

- **序号**：2989 / 4088
- **哈希**：0cc337ace306033dff1db2775a8046c1a58214f5
- **短哈希**：0cc337ace
- **日期**：2025-12-10
- **作者**：Prashant Singh
- **提交说明**：Core: REST Scan Planning Task Implementation (#13400)
- **PR/Issue**：#13400

## 总体目的

Iceberg 的 REST Catalog 规范允许将表扫描的 plan 阶段下推到 REST 服务端执行。规范为此定义了一组端点：提交扫描计划（`POST .../plan`）、轮询计划结果（`GET .../plan/{plan-id}`）、取消计划（`DELETE .../plan/{plan-id}`）以及按 plan-task 拉取扫描任务（`POST .../tasks`）。在此之前，Iceberg 客户端侧只实现了同步的 `planFiles`，即客户端自行基于 metadata 文件做扫描规划；服务端 planning 能力虽然有端点定义（`Endpoint.V1_SUBMIT_TABLE_SCAN_PLAN` 等）但客户端并不会真正调用它们。

本提交在客户端实现完整的服务端扫描规划任务（Scan Planning Task）流程，使 Iceberg 在 REST Catalog 模式下能够把代价高昂的文件扫描规划交给服务端异步执行，并通过 plan-task 分片拉取 `FileScanTask`，适用于超大表或希望将计算下沉到服务端的场景。

具体动机包括：1）大表本地 plan 开销大、占用客户端资源；2）服务端可基于元数据/索引做更优规划并分批返回；3）异步规划支持轮询与取消，提升健壮性。整个特性通过开关 `rest-scan-planning-enabled`（默认 false）控制，仅当服务端声明支持相关端点且客户端显式开启时才生效，保证向后兼容。

## 如何达成设计目的

设计上引入三层：`RESTSessionCatalog` 在 `loadTable`/`registerTable`/`loadTable(SessionCatalog)` 路径上判断是否启用服务端规划，若启用则返回新的 `RESTTable`（继承 `BaseTable`，覆盖 `newScan()`）；`RESTTable.newScan()` 返回 `RESTTableScan`（继承 `DataTableScan`），在 `planFiles()` 中走"提交计划→轮询→拉取任务"的异步流程，并使用 Failsafe 做指数退避轮询与超时；`ScanTaskIterable` 作为返回的 `CloseableIterable<FileScanTask>`，用线程池并发拉取 plan-task 并通过阻塞队列向迭代器喂入任务，迭代器关闭时自动取消未完成计划。配套新增 `ResourcePaths` 的 plan/tasks 路径、`ErrorHandlers` 的 plan/planTask 错误处理器，以及 `TableScanContext` 改为 public 以便 REST 层使用。

## 修改详情

### `build.gradle` (+1/-0 lines)

**修改目的**：为 iceberg-core 引入 Failsafe 重试库。

**工作逻辑**：在 `:iceberg-core` 的 dependencies 中新增 `implementation libs.failsafe`。Failsafe 用于 `RESTTableScan.fetchPlanningResult` 中构建指数退避 + 抖动 + 最大次数/时长限制的 `RetryPolicy`，驱动对计划结果的轮询。

### `core/src/main/java/org/apache/iceberg/TableScanContext.java` (+1/-1 lines)

**修改目的**：放开 `TableScanContext` 的可见性。

**工作逻辑**：将 `abstract class TableScanContext` 改为 `public abstract class TableScanContext`，因为新建的 `RESTTable`（位于 `org.apache.iceberg.rest` 包，不在 core 同包）需要引用 `ImmutableTableScanContext` 来构造扫描上下文。

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (+50/-0 lines)

**修改目的**：为 plan 与 plan-task 请求新增专用错误处理器。

**工作逻辑**：新增 `planErrorHandler()` 与 `planTaskHandler()` 两个静态方法，分别返回 `PlanErrorHandler`/`PlanTaskErrorHandler` 单例。两者均继承 `DefaultErrorHandler`，在 404 错误时按 `error.type()` 区分：若是 `NoSuchNamespaceException`/`NoSuchTableException` 则抛对应异常，否则分别抛 `NoSuchPlanIdException`（plan 路径）或 `NoSuchPlanTaskException`（plan-task 路径）；其余错误走默认处理。这使客户端能根据服务端返回精确区分"命名空间/表不存在"与"计划 ID/计划任务不存在"。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+4/-0 lines)

**修改目的**：新增服务端规划开关属性。

**工作逻辑**：新增常量 `REST_SCAN_PLANNING_ENABLED = "rest-scan-planning-enabled"` 与默认值 `REST_SCAN_PLANNING_ENABLED_DEFAULT = false`，默认关闭以保持现有行为不变。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+41/-0 lines)

**修改目的**：在表加载路径上按开关返回 `RESTTable`。

**工作逻辑**：新增字段 `restScanPlanningEnabled`，在 `initialize` 中从合并属性解析该开关。抽取私有方法 `restTableForScanPlanning(ops, identifier, restClient)`：当 `endpoints.contains(Endpoint.V1_SUBMIT_TABLE_SCAN_PLAN)` 且 `restScanPlanningEnabled` 为 true 时，构造并返回 `RESTTable`；否则返回 null。在三个加载入口（`loadTable`、`registerTable`/`loadTable(SessionCatalog)` 的 isExisting 分支、以及 `loadTable` 的 session 路径）均调用该方法，若返回非 null 则用 `RESTTable` 替代 `BaseTable`。注释明确说明元数据表（metadata tables）不返回 `RESTTable`，因为客户端元数据表依赖本地 metadata 文件而非 catalog。

### `core/src/main/java/org/apache/iceberg/rest/RESTTable.java` (+70/-0 lines, 新增)

**修改目的**：定义支持服务端规划的 `Table` 实现。

**工作逻辑**：`RESTTable extends BaseTable`，额外持有 `RESTClient client`、`Supplier<Map<String,String>> headers`、`MetricsReporter reporter`、`ResourcePaths resourcePaths`、`TableIdentifier tableIdentifier`、`Set<Endpoint> supportedEndpoints`。核心是覆盖 `newScan()`，返回一个 `RESTTableScan`，并传入 `ImmutableTableScanContext.builder().metricsReporter(reporter).build()` 作为初始上下文。其余行为（写入、提交等）继承自 `BaseTable`，仍走原有 ops 链路。

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+282/-0 lines, 新增)

**修改目的**：实现异步服务端扫描规划主流程。

**工作逻辑**：

`RESTTableScan extends DataTableScan`，持有 client、headers、operations、resourcePaths、tableIdentifier、supportedEndpoints 与 `ParserContext`（用于反序列化 `FileScanTask`），并维护 `currentPlanId`。

`planFiles()` 从上下文取出 `startSnapshotId`/`endSnapshotId`/`snapshotId`、`selectedColumns`（来自 schema 列名）、`statsFields`（来自 `columnsToKeepStats()`，转为列名），构造 `PlanTableScanRequest`（含 select、filter、caseSensitive、statsFields、快照范围等），调用 `planTableScan(request)`。

`planTableScan(request)` 向 `resourcePaths.planTableScan(tableIdentifier)` 发 POST，得到 `PlanTableScanResponse`。按 `planStatus` 分支：`COMPLETED` 直接用返回的 planTasks/fileScanTasks 构造可迭代对象；`SUBMITTED` 检查端点 `V1_FETCH_TABLE_SCAN_PLAN` 后调用 `fetchPlanningResult(planId)` 轮询；`FAILED`/`CANCELLED` 抛 `IllegalStateException`。

`fetchPlanningResult(planId)` 用 Failsafe 构建重试策略：当响应状态仍为 `SUBMITTED` 时重试，退避从 1s 起步、上限 60s、指数因子 2.0、抖动 10%、最大 10 次、总时长上限 5 分钟；失败时调用 `cancelPlan()` 清理；成功后校验状态为 `COMPLETED` 并返回任务可迭代对象。轮询走 `GET resourcePaths.plan(tableIdentifier, planId)`，错误用 `ErrorHandlers.planErrorHandler()`。

`scanTasksIterable(planTasks, fileScanTasks)` 在存在 planTasks 时检查端点 `V1_FETCH_TABLE_SCAN_PLAN_TASKS`，返回 `ScanTaskIterable` 并通过 `CloseableIterable.whenComplete(..., this::cancelPlan)` 在迭代结束时自动取消未完成计划（仅当支持 `V1_CANCEL_TABLE_SCAN_PLAN` 时）。

`cancelPlan()` 用 `DELETE resourcePaths.plan(tableIdentifier, planId)` 取消计划，失败时静默返回 false（计划可能已完成/失败）。

### `core/src/main/java/org/apache/iceberg/rest/ScanTaskIterable.java` (+249/-0 lines, 新增)

**修改目的**：并发拉取 plan-task 并以阻塞队列向迭代器供数。

**工作逻辑**：

`ScanTaskIterable implements CloseableIterable<FileScanTask>`，内部维护容量 1000 的 `LinkedBlockingQueue<FileScanTask> taskQueue`、初始 fileScanTasks 与 planTasks 两个 `ConcurrentLinkedQueue`、`activeWorkers` 计数、`shutdown` 标志，以及一个外部传入的 `ExecutorService`（worker 池大小取 `WORKER_THREAD_POOL_SIZE/4`，至少 1）。构造时若有初始 planTasks 或初始 fileScanTasks 则提交固定数量的 `PlanTaskWorker`。

`PlanTaskWorker.run()`：循环从 `planTasks` 队列取任务，取到则调用 `processPlanTask`：向 `resourcePaths.fetchScanTasks(tableIdentifier)` 发 POST（body 为 `FetchScanTasksRequest(planTask)`，错误用 `planTaskHandler`），将返回的新 planTasks 立即加入队列供其他 worker 拾取，并把 fileScanTasks 与剩余初始任务塞入 `taskQueue`（`put` 阻塞直到有空间）。取不到 planTask 时把剩余初始任务灌入队列后退出。`finally` 中递减 activeWorkers，并在仍有待处理且未 shutdown 时补一个 worker。

`ScanTasksIterator`：`hasNext()` 通过 `isDone()`（队列/planTasks/初始/activeWorkers 全空）判断结束，否则以 100ms 超时轮询 `taskQueue`；`next()` 返回并清空缓存；`close()` 置 shutdown、清空队列与 planTasks。

这套设计实现了"生产者-消费者"式拉取：多个 worker 并发向后端拉取 plan-task，消费侧迭代器按需取用，背压由阻塞队列容量控制。

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java` (+34/-0 lines)

**修改目的**：新增 plan/tasks 路径构造方法。

**工作逻辑**：新增 `planTableScan(ident)` 生成 `.../tables/{table}/plan`，`plan(ident, planId)` 生成 `.../tables/{table}/plan/{plan-id}`（planId 经 `RESTUtil.encodeString` 编码，处理空格等特殊字符），`fetchScanTasks(ident)` 生成 `.../tables/{table}/tasks`。

### `core/src/test/java/org/apache/iceberg/TestBase.java` (+27/-3 lines)

**修改目的**：为扫描规划测试补充 equality delete 文件与公开部分常量。

**工作逻辑**：新增 `FILE_A_EQUALITY_DELETES`、`FILE_B_EQUALITY_DELETES`、`FILE_C_EQUALITY_DELETES` 三个 equality delete 文件（分别与 FILE_A/B/C 同分区，作用于 id 列），并将 `FILE_WITH_STATS` 由包级改为 `public`，供 `TestRESTScanPlanning` 引用以构造带删除文件的扫描场景。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+12/-3 lines)

**修改目的**：支持测试中动态注入 `PlanningBehavior`。

**工作逻辑**：将 `planningBehavior` 字段由 `final` 改为可变，`planningBehavior()` 在为 null 时返回默认空实现，否则返回已设置值；新增 `setPlanningBehavior(behavior)` 供测试自定义异步规划行为（如 `shouldPlanTableScanAsync` 与 `numberFileScanTasksPerPlanTask`）。原调用点改用 `planningBehavior()::...` 方法引用以读取最新值。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+1003/-0 lines, 新增)

**修改目的**：端到端测试服务端扫描规划流程。

**工作逻辑**：使用内嵌 Jetty + `RESTCatalogAdapter`（基于 `InMemoryCatalog`）搭建 REST 服务，通过 `setPlanningBehavior` 控制同步/异步规划与 plan-task 分片大小。覆盖：同步完成、异步轮询完成、plan-task 分片拉取、取消计划、equality/position delete 任务、列投影、过滤下推、stats fields、不同快照范围、错误场景（NoSuchPlanId/NoSuchPlanTask）等。1003 行的体量体现了对完整状态机的覆盖。

### `core/src/test/java/org/apache/iceberg/rest/TestResourcePaths.java` (+63/-0 lines)

**修改目的**：测试新增的 plan/tasks 路径构造。

**工作逻辑**：新增 `planEndpointPath`、`fetchScanTasksPath`、`cancelPlanEndpointPath` 三个测试，验证带/不带 prefix、单层/多层 namespace、含空格 planId 的编码是否正确。

## 总结

本提交是 REST Catalog 服务端扫描规划能力在客户端的完整落地，引入了 `RESTTable`/`RESTTableScan`/`ScanTaskIterable` 三层核心实现，支持异步提交-轮询-分片拉取-取消的完整生命周期，配合 Failsafe 退避重试与多 worker 并发拉取保证大表场景下的吞吐与健壮性。通过开关与端点探测保证向后兼容，配套的千行级端到端测试与单元测试覆盖了状态机的各分支，是一次架构完整度较高的特性交付。
