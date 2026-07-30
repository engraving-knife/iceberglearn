# 提交 3341：Core: Track & close FileIO used for remote scan planning (#15439)

## 提交信息

- **序号**：3341 / 4088
- **哈希**：39ed7e445361b2ff83887795e7e8d9f92ed45abc
- **短哈希**：39ed7e445
- **日期**：2026-03-03 09:50:17 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Track & close FileIO used for remote scan planning (#15439)
- **PR/Issue**：#15439

## 总体目的

本提交修复了 REST Catalog 在远程扫描规划（remote scan planning）场景下，为每个 plan 创建的专用 `FileIO` 实例不被关闭而导致的资源泄漏问题。

背景是：Iceberg REST Catalog 支持把扫描规划下推到服务端——客户端发送 `PlanTableScanRequest`，服务端异步计算并返回 `planId` 与该 plan 专属的存储凭证（`storageCredentials`）。客户端在 `fileIOForPlanId()` 中用这些专属凭证加载一个独立的 `FileIO` 实例（存入字段 `fileIOForPlanId`），并通过重写的 `io()` 方法（`return null != fileIOForPlanId ? fileIOForPlanId : tableIo`）让扫描在读取数据文件时使用这个带专属凭证的 FileIO，而非表级默认的 `tableIo`。这个 plan 级 FileIO 持有连接、凭证提供者等资源，使用完毕后必须关闭。

问题在于：此前 `fileIOForPlanId()` 只是创建并返回 FileIO，没有任何地方负责关闭它。在失败路径上（轮询超时、不可重试异常），代码只调用了 `cancelPlan()`（通知服务端取消该 plan），却未关闭本地 FileIO；在成功路径上，返回的 `CloseableIterable` 在关闭时也只回调 `cancelPlan()`，同样不关闭 FileIO。结果是每次远程扫描规划都会泄漏一个 FileIO 及其持有的底层资源（如 HTTP 连接池、凭证句柄），在长时间运行或高频扫描的服务中会逐步耗尽资源。

## 如何达成设计目的

设计采用"显式清理 + GC 兜底"的双保险策略：

1. **GC 兜底追踪器**：引入一个静态的 Caffeine 缓存 `FILEIO_TRACKER`，以 `RESTTableScan` 实例为弱键（`weakKeys()`）、plan 级 FileIO 为值，注册 `RemovalListener`：当条目被移除时调用 `io.close()`。弱键意味着缓存不阻止 scan 对象被垃圾回收；当 scan 失去强引用被 GC 时，缓存条目变为可驱逐，监听器自动关闭对应 FileIO。这为任何未显式清理的路径提供了安全网。

2. **显式清理方法**：新增 `cleanupPlanResources()`，先调用 `cancelPlan()`（服务端取消），再若 `fileIOForPlanId != null` 则调用 `FILEIO_TRACKER.invalidate(this)`（触发移除监听器关闭 FileIO）并将字段置空。用于失败路径的即时清理。

3. **失败路径接入**：将 `fetchPlanningResult()` 中两处原调用 `cancelPlan()` 的位置（Failsafe `onFailure` 重试耗尽钩子、catch 不可重试异常块）改为调用 `cleanupPlanResources()`，使失败时立即释放 FileIO。

成功路径仍使用 `CloseableIterable.whenComplete(..., this::cancelPlan)`，不显式关闭 FileIO——因为消费方在迭代读取 FileScanTask 期间仍需该 FileIO（`io()` 返回它读取数据文件），过早关闭会中断读取；该路径的 FileIO 关闭依赖弱键缓存在 scan 被 GC 后自动完成。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+38/-12 lines)

**修改目的**：追踪并关闭远程扫描规划使用的 plan 级 FileIO，消除资源泄漏。

**工作逻辑**：

- **新增静态缓存 `FILEIO_TRACKER`**：类型为 `Cache<RESTTableScan, FileIO>`，通过 `Caffeine.newBuilder().weakKeys().removalListener(...)` 构建。`weakKeys()` 使 scan 实例作为弱引用键——缓存不持有 scan 的强引用，scan 可被正常 GC；当 scan 被 GC 后缓存条目可被驱逐。`RemovalListener` 在条目移除时检查 value 非空则调用 `io.close()`，确保 FileIO 被关闭。该缓存是静态的（类级），作为所有 scan 实例的 FileIO 追踪中心与 GC 安全网。新增了 `com.github.benmanes.caffeine.cache` 的 `Cache`、`Caffeine`、`RemovalListener` 三个 import。

- **`fileIOForPlanId(List<Credential>)` 方法**：原逻辑是直接 `return CatalogUtil.loadFileIO(...)`。修改后先把加载结果存入局部变量 `ioForScan`，然后调用 `FILEIO_TRACKER.put(this, ioForScan)` 将其注册到追踪缓存，再返回。这样每次创建 plan 级 FileIO 都被纳入追踪，即使后续未显式清理也能在 scan GC 时被关闭。

- **新增 `cleanupPlanResources()` 方法**：私有方法，先 `cancelPlan()`（通知服务端取消 plan），再判断 `fileIOForPlanId != null` 时调用 `FILEIO_TRACKER.invalidate(this)`（使缓存移除该条目，从而触发 `RemovalListener` 执行 `io.close()`），最后将 `this.fileIOForPlanId` 置空避免重复关闭。Javadoc 说明"取消服务端 plan（若支持）并关闭 plan 级 FileIO"。

- **`fetchPlanningResult()` 失败路径改用 `cleanupPlanResources()`**：两处改动——(1) Failsafe `RetryPolicy` 的 `.onFailure(e -> ...)` 钩子中，原 `cancelPlan()` 改为 `cleanupPlanResources()`，在轮询重试耗尽/超时后既取消 plan 又关闭 FileIO；(2) catch 不可重试 `Exception` 的块中，原 `cancelPlan()` 改为 `cleanupPlanResources()`，在 I/O 错误、鉴权错误等异常时立即释放资源（仍用 try-catch 包裹忽略清理本身的失败并作为 suppressed 异常附加）。

  注意成功路径（`scanTasksIterable` 返回的 `CloseableIterable.whenComplete(..., this::cancelPlan)`）未改为 `cleanupPlanResources`，因为消费方读取 FileScanTask 期间仍需通过 `io()` 使用该 FileIO，关闭需延迟到 scan 不再被使用时由弱键缓存自动完成。

## 总结

本提交通过引入以 `RESTTableScan` 为弱键的 Caffeine 缓存追踪远程扫描规划创建的 plan 级 FileIO，并在移除时自动关闭，同时新增 `cleanupPlanResources()` 在失败路径显式做服务端取消与 FileIO 关闭，修复了 REST Catalog 远程扫描规划场景下 plan 级 FileIO 从不关闭的资源泄漏问题。成功路径依赖弱键 GC 兜底关闭（因读取期间仍需该 FileIO），失败路径则即时清理，兼顾了正确性与资源安全。
