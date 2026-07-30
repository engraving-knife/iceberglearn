# 提交 2341：Core: Make metrics reporting asynchronous (#13507)

## 提交信息

- **序号**：2341 / 4088
- **哈希**：20b2179bff881a3f2c3ebb3fbc850fdf87cce80f
- **短哈希**：20b2179bf
- **日期**：2025-07-11 21:29:35 +0200
- **作者**：Anoop Johnson
- **提交说明**：Core: Make metrics reporting asynchronous (#13507)
- **PR/Issue**：#13507

## 总体目的

本提交将 `RESTMetricsReporter` 的指标上报操作从同步改为异步，避免指标上报阻塞主流程。

`RESTMetricsReporter` 是 Iceberg REST Catalog 中负责将操作指标（如扫描的文件数、读取的记录数等）通过 HTTP POST 上报到 REST 服务端的组件。此前，`report()` 方法在调用 `client.post()` 上报指标时是同步执行的，这意味着调用方（如查询执行流程）需要等待 HTTP 请求完成才能继续。

指标上报通常是"尽力而为"的操作，其失败不应影响数据操作本身，也不应因为网络延迟而拖慢主流程。将上报改为异步后，主流程可以立即继续执行，不受 REST 服务端响应速度的影响。

## 如何达成设计目的

设计思路是使用一个专用的线程池异步执行 HTTP POST 请求，主流程提交任务后立即返回。

关键设计点：
1. 使用 `ThreadPools.newExitingWorkerPool` 创建一个单线程的、JVM 退出时自动关闭的线程池。
2. 使用 Iceberg 的 `Tasks` 工具类将上报操作封装为异步任务，通过 `executeWith` 指定线程池。
3. 使用 `suppressFailureWhenFinished` 确保上报失败不会影响主流程。
4. 在 `onFailure` 回调中记录警告日志，便于问题排查。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTMetricsReporter.java` (+22/-10 lines)

**修改目的**：将同步的 HTTP POST 上报改为异步执行。

**工作逻辑**：

1. **新增导入**：引入 `ExecutorService`、`Tasks`、`ThreadPools`。

2. **新增线程池**：创建静态常量 `METRICS_EXECUTOR`，使用 `ThreadPools.newExitingWorkerPool("rest-metrics-reporter", 1)` 创建单线程、JVM 退出时自动关闭的线程池。静态常量意味着所有 `RESTMetricsReporter` 实例共享同一个线程池。

3. **`report()` 方法改写**：原同步的 `try { client.post(...) } catch { LOG.warn(...) }` 逻辑替换为使用 `Tasks.range(1).executeWith(METRICS_EXECUTOR).suppressFailureWhenFinished().onFailure(...).run(...)` 的异步执行模式。`Tasks.range(1)` 创建一个只有一个元素的虚拟范围作为任务载体，`run` 方法中执行实际的 HTTP POST 请求，`onFailure` 中记录警告日志，`suppressFailureWhenFinished` 确保任务失败不会抛出异常影响调用方。

## 总结

本提交将 `RESTMetricsReporter` 的指标上报从同步改为异步，通过专用线程池执行 HTTP POST 请求，避免指标上报的网络延迟阻塞主数据操作流程。这提升了系统的整体性能和可靠性，特别是在 REST 服务端响应较慢或网络不稳定时。
