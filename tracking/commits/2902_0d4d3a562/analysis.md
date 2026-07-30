# 提交 2902：Core: Fix Async Planning handling in RESTCatalogAdapter for Remote Scan Planning (#14629)

## 提交信息

- **序号**：2902 / 4088
- **哈希**：0d4d3a562ffd339faae7e1db41c2068dc77920e8
- **短哈希**：0d4d3a562
- **日期**：2025-11-20 10:47:12 -0800
- **作者**：Prashant Singh
- **提交说明**：Core: Fix Async Planning handling in RESTCatalogAdapter for Remote Scan Planning (#14629)
- **PR/Issue**：#14629

## 总体目的

Iceberg 的 REST Catalog 协议支持远程扫描规划（remote scan planning），即客户端提交 `PlanTableScan` 请求后，服务端可以同步完成规划，也可以返回一个 `planId` 让客户端稍后通过 `FetchPlanningResult` 拉取结果（异步规划）。`CatalogHandlers` 与 `InMemoryPlanningState` 共同承担这套机制的服务端实现：前者负责执行规划并把结果存进内存，后者负责维护 planId 到规划任务的映射。

本提交修复这套异步规划链路中的若干缺陷。首先，原先异步规划任务被丢进 `ASYNC_PLANNING_POOL.execute(...)` 后没有任何状态追踪，调用方无法区分某个 async planId 是"仍在规划中"、"已完成"还是"已失败"；`FetchPlanningResult` 一律假设任务已经完成并直接尝试取结果，遇到尚未完成或已失败的任务会给出错误的响应或异常。其次，同步规划分支构造的 `PlanTableScanResponse` 没有把 `planId` 回填到响应里，导致客户端拿到一个 `PlanStatus.COMPLETED` 但拿不到用于后续分页拉取的 `planId`。再次，`clearPlanningState()` 里调用了 `ASYNC_PLANNING_POOL.shutdown()`，这会把整个共享异步线程池关闭，导致后续任何异步规划都无法再执行，是一个明显的资源管理 bug。最后，`cancelPlanTableScan` 调用的 `removePlan` 只清理了分页映射，对异步状态没有任何处理，取消一个仍在运行的异步规划时其状态会残留为"运行中"。

提交作者通过引入显式的异步规划状态机（`SUBMITTED → COMPLETED/FAILED/CANCELLED`）并改造 `CatalogHandlers` 对异步任务的生命周期管理，系统性地修复了上述问题。

## 如何达成设计目的

整体思路是在 `InMemoryPlanningState` 中新增一个 `asyncPlanningStates` 并发映射，用 `PlanStatus` 枚举显式记录每个异步 planId 的生命周期；在 `CatalogHandlers` 中把 `asyncPlanFiles` 从"提交即忘"的 `execute(...)` 改为基于 `CompletableFuture.runAsync(...).whenComplete(...)` 的写法，在回调里根据成功/失败把状态置为 `COMPLETED` 或 `FAILED`。`FetchPlanningResult` 入口先查异步状态，若不是 `COMPLETED` 就直接返回当前状态而不去读结果。同步与异步 planId 分别加 `sync-` / `async-` 前缀以便区分来源；同步响应补回 `withPlanId(planId)`。`cancelPlanTableScan` 改为调用新命名的 `cancelPlan`，该函数在原有清理基础上把处于 `SUBMITTED` 的异步规划置为 `CANCELLED`。最后移除 `clearPlanningState()` 中错误的 `ASYNC_PLANNING_POOL.shutdown()` 调用，避免关闭共享线程池。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+24/-6 lines)

**修改目的**：修正异步规划的状态追踪、同步响应缺失 planId、`FetchPlanningResult` 对未完成异步规划的误判，以及 `clearPlanningState` 误关闭线程池等问题。

**工作逻辑**：
- planId 加前缀：`String asyncPlanId = "async-" + UUID.randomUUID();`、`String planId = "sync-" + UUID.randomUUID();`，便于在日志与状态机中区分同步与异步规划来源。
- 同步响应补回 planId：在同步分支的 `PlanTableScanResponse.builder()` 链中新增 `.withPlanId(planId)`，使客户端拿到 `PlanStatus.COMPLETED` 时同时拥有可用于后续 `FetchPlanningResult` / 分页的 planId。
- `fetchPlanningResult` 前置状态检查：方法入口先调用 `IN_MEMORY_PLANNING_STATE.asyncPlanStatus(planId)`，若状态不是 `COMPLETED`（即仍为 `SUBMITTED`、`FAILED` 或 `CANCELLED`），直接 `return FetchPlanningResultResponse.builder().withPlanStatus(status).build();`，避免在未完成或失败的异步规划上尝试读取结果导致错误。注意这里 `asyncPlanStatus` 在 planId 不存在时会抛 `NoSuchPlanIdException`，保留了原行为。
- `cancelPlanTableScan` 改用 `cancelPlan`：`IN_MEMORY_PLANNING_STATE.removePlan(planId)` → `IN_MEMORY_PLANNING_STATE.cancelPlan(planId)`，与新方法名对应。
- `clearPlanningState()` 不再 `shutdown()` 线程池：删除了 `ASYNC_PLANNING_POOL.shutdown();` 一行，避免在清理内存状态时把共享异步线程池也关掉，导致后续异步规划无法再提交。
- `asyncPlanFiles` 改为基于 `CompletableFuture` 的可观测写法：
  ```java
  IN_MEMORY_PLANNING_STATE.addAsyncPlan(asyncPlanId);
  CompletableFuture.runAsync(
          () -> { planFilesFor(tableScan, asyncPlanId, tasksPerPlanTask); },
          ASYNC_PLANNING_POOL)
      .whenComplete((result, exception) -> {
        if (exception != null) {
          IN_MEMORY_PLANNING_STATE.markAsyncPlanFailed(asyncPlanId);
        } else {
          IN_MEMORY_PLANNING_STATE.markAsyncPlanAsComplete(asyncPlanId);
        }
      });
  ```
  提交前先 `addAsyncPlan` 把状态置为 `SUBMITTED`（同时校验该 planId 不已存在）；任务在 `ASYNC_PLANNING_POOL` 上执行，完成后回调根据是否抛异常分别置为 `COMPLETED` 或 `FAILED`。`@SuppressWarnings("FutureReturnValueIgnored")` 表明返回的 `CompletableFuture` 不需要在外部等待。这取代了原先"提交即忘"的 `ASYNC_PLANNING_POOL.execute(...)`，让异步规划的状态可被外部查询。

### `core/src/main/java/org/apache/iceberg/rest/InMemoryPlanningState.java` (+57/-1 lines)

**修改目的**：为异步规划引入显式状态机，提供状态查询、完成/失败/取消标记能力，并让 `clear` / `cancelPlan` 一并清理异步状态。

**工作逻辑**：
新增 import `Preconditions`。新增字段 `private final Map<String, PlanStatus> asyncPlanningStates;`，在私有构造器中初始化为 `Maps.newConcurrentMap()`，与已有的两个分页映射一致使用并发 Map。

新增四个方法：
- `addAsyncPlan(String plan)`：用 `Preconditions.checkArgument` 校验该 plan 不已存在（存在则报错"Plan %s already exists with status %s"），然后 `put(plan, PlanStatus.SUBMITTED)`。这是异步规划生命周期的起点。
- `asyncPlanStatus(String plan)`：取出状态；若不存在则抛 `NoSuchPlanIdException("Cannot find plan with id %s", plan)`，复用 REST 协议中已有的"planId 不存在"语义。
- `markAsyncPlanAsComplete(String plan)`：校验存在且当前状态为 `SUBMITTED`，再置为 `COMPLETED`。
- `markAsyncPlanFailed(String plan)`：校验存在且当前状态为 `SUBMITTED`，再置为 `FAILED`（注意其错误信息文案复用了"completed"字样，属轻微文案瑕疵但行为正确）。

这两个 `mark*` 方法的 `SUBMITTED` 前置校验保证状态机不会出现非法跃迁（例如对已完成/已失败的 plan 再次标记完成）。

`removePlan` 重命名为 `cancelPlan`：保留原先按 `planId` 子串匹配清理 `planTaskToNext` 与 `planTaskToFileScanTasks` 的逻辑；新增对异步状态的处理——若 `asyncPlanningStates` 含该 planId 且状态非空，则仅当状态为 `SUBMITTED` 时置为 `CANCELLED`，对已终止（`COMPLETED`/`FAILED`/`CANCELLED`）的 plan 不再改写状态。注释说明"找不到 plan 不应让取消失败"、"已终止的 plan 也无需再标记"。

`clear()` 新增 `asyncPlanningStates.clear();`，与另外两个映射一并清空，保证测试间状态隔离。

## 总结

本提交系统性修复了 `RESTCatalogAdapter` 远程扫描规划中异步链路的多个缺陷：通过在 `InMemoryPlanningState` 中引入 `PlanStatus` 状态机（`SUBMITTED/COMPLETED/FAILED/CANCELLED`）并改用 `CompletableFuture` 回调追踪异步任务结果，使 `FetchPlanningResult` 能正确区分未完成/失败/取消的异步规划；同时补回同步响应缺失的 `planId`、移除 `clearPlanningState` 中误关闭共享线程池的代码、并让取消逻辑同步更新异步状态。整体提升了远程扫描规划在并发与异常场景下的正确性和可观测性。
