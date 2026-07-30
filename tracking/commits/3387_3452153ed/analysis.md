# 提交 3387：Core: Replace Failsafe with Tasks utility in RESTTableScan (#15613)

## 提交信息

- **序号**：3387 / 4088
- **哈希**：3452153ed2e8e9ee897c1a9a76a3264df9b2855b
- **短哈希**：3452153ed
- **日期**：2026-03-13 12:24:23 -0700
- **作者**：Prashant Singh
- **提交说明**：Core: Replace Failsafe with Tasks utility in RESTTableScan (#15613)
- **PR/Issue**：#15613

## 总体目的

将 `RESTTableScan.fetchPlanningResult()` 中使用的第三方 Failsafe 重试库替换为 Iceberg 自带的 `Tasks` 工具类，统一代码库中的重试/退避实现风格，并从 `iceberg-core` 模块中移除对 failsafe 库的依赖。

背景：REST Catalog 的表扫描采用异步规划（plan）模式——客户端提交 plan 请求后，服务端返回 `PlanStatus.SUBMITTED`，客户端需要轮询直到状态变为 `COMPLETED`。原实现使用 `dev.failsafe` 库的 `RetryPolicy` 来做带退避和抖动的轮询重试。但 Iceberg 代码库中其它地方普遍使用内置的 `org.apache.iceberg.util.Tasks` 工具做重试，引入 failsafe 仅为此一处使用，增加了不必要的依赖。

## 如何达成设计目的

1. **使用 `Tasks.foreach(planId)` 链式 API**：配合 `.exponentialBackoff(...)`、`.retry(MAX_ATTEMPTS)`、`.onlyRetryOn(NotCompleteException.class)`、`.onFailure(...)`、`.throwFailureWhenFinished()`、`.run(...)` 构建重试逻辑，复用已有的常量 `MIN_SLEEP_MS/MAX_SLEEP_MS/MAX_WAIT_TIME_MS/SCALE_FACTOR/MAX_ATTEMPTS`。
2. **通过自定义 `NotCompleteException` 触发重试**：在 `run` 的 lambda 中，当 `response.planStatus() == PlanStatus.SUBMITTED` 时抛出 `NotCompleteException`，由 `onlyRetryOn` 捕获并重试；当状态既非 SUBMITTED 也非 COMPLETED 时抛 `IllegalStateException` 立即失败；COMPLETED 时将响应存入 `AtomicReference`。
3. **结果通过 `AtomicReference` 传出**：因为 `Tasks.run` 不返回值，使用 `AtomicReference<FetchPlanningResultResponse>` 在 lambda 内部设置最终结果，循环结束后取回。
4. **失败清理**：`.onFailure(...)` 中调用 `cleanupPlanResources()` 并记录日志，与原 Failsafe 的 `onFailure` 钩子行为对齐。
5. **移除依赖**：从 `build.gradle` 的 `iceberg-core` 依赖列表中删除 `implementation libs.failsafe`。

## 修改详情

### `build.gradle` (+0/-1 lines)

**修改目的**：从 iceberg-core 模块依赖中移除 failsafe 库。

**工作逻辑**：
- 删除 `implementation libs.failsafe` 一行。由于这是 core 模块中 failsafe 的唯一使用点，移除后该依赖不再被需要，减少了核心模块的外部依赖数量。

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+40/-67 lines)

**修改目的**：将 `fetchPlanningResult()` 中的 Failsafe 重试逻辑替换为 Iceberg 内置 `Tasks` 工具实现。

**工作逻辑**：
- **导入替换**：移除 `dev.failsafe.*` 和 `java.time.Duration` 的导入，新增 `java.util.concurrent.atomic.AtomicReference` 和 `org.apache.iceberg.util.Tasks`。
- **重试构建**：原代码构建一个 `RetryPolicy<FetchPlanningResultResponse>`，配置 `handleResultIf(response -> response.planStatus() == PlanStatus.SUBMITTED)`、`withBackoff`、`withJitter(0.1)`、`withMaxAttempts`、`withMaxDuration`，再通过 `Failsafe.with(retryPolicy).get(...)` 执行。新代码改用 `Tasks.foreach(planId).exponentialBackoff(...).retry(MAX_ATTEMPTS).onlyRetryOn(NotCompleteException.class).onFailure(...).throwFailureWhenFinished().run(id -> {...})`。
- **状态判断移入 lambda**：原本通过 `handleResultIf` 判断结果是否需重试；新代码在 `run` 的 lambda 内主动判断 `planStatus`：SUBMITTED 抛 `NotCompleteException`（触发重试），非 COMPLETED 抛 `IllegalStateException`（立即失败），COMPLETED 存入 `AtomicReference`。
- **异常处理简化**：原代码需捕获 `FailsafeException` 包装为 `IllegalStateException`，并在通用 `Exception` 分支中手动调用 `cleanupPlanResources()`；新代码通过 `Tasks` 的 `.throwFailureWhenFinished()` 和 `.onFailure(...)` 钩子处理，`onFailure` 中调用 `cleanupPlanResources()`，逻辑更集中。
- **新增内部类**：`private static class NotCompleteException extends RuntimeException {}` 作为重试信号。
- **后续逻辑不变**：拿到 `response` 后，根据 `credentials()` 是否为空决定 `scanFileIO`，并返回 `scanTasksIterable(...)`。

## 总结

本提交通过将 REST 表扫描的轮询重试从第三方 Failsafe 库迁移到 Iceberg 内置的 `Tasks` 工具，统一了代码库的重试实现风格，并从核心模块中移除了 failsafe 依赖，降低了依赖维护成本。改动后行为等价（指数退避、最大尝试次数、超时上限、失败清理均保留），但代码更符合项目惯例。注意：`Tasks` 实现没有显式的 jitter（抖动）配置，这是与原 Failsafe 实现的一个细微差异，但 `Tasks.exponentialBackoff` 内部已包含退避逻辑，对大多数场景影响可忽略。后续提交（#15627）会将 `MAX_ATTEMPTS` 重命名为 `MAX_RETRIES` 以更准确反映语义。
