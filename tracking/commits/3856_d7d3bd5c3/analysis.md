# 提交 3856：Core: Fix RESTMetricsReporter.report() blocking the calling thread (#16695)

## 提交信息

- **序号**：3856 / 4088
- **哈希**：d7d3bd5c3788b882f52fa79c44cf09a11459deab
- **短哈希**：d7d3bd5c3
- **日期**：2026-06-11 08:47:50 +0200
- **作者**：Fei Wang
- **提交说明**：Core: Fix RESTMetricsReporter.report() blocking the calling thread (#16695)
- **PR/Issue**：#16695

## 总体目的

本提交修复了 `RESTMetricsReporter.report()` 方法阻塞调用线程的问题。`RESTMetricsReporter` 用于将 Iceberg 的指标（metrics）通过 HTTP POST 上报到 REST catalog 端点。

问题根源在于 `report()` 方法使用了 `Tasks.range(1).executeWith(METRICS_EXECUTOR).run()` 模式提交 HTTP 调用到单线程执行器，但随后通过 `Tasks.waitFor()`（一个 `sleep(10ms)` 轮询循环）等待提交的 future 完成。这使得 `report()` 从调用者角度看是同步的——尽管表面上使用了执行器。

这会导致严重问题：如果调用者将 `report()` 包装在自己的执行器池中以实现异步行为（fire-and-forget 指标发布），其所有池线程都会永久阻塞等待 `METRICS_EXECUTOR`，导致线程堆积（thread pile-up），速率与指标产生速率成正比。

本提交通过直接使用 `executor.execute(lambda)` 替代 `Tasks` 模式，使 `report()` 真正异步——入队 HTTP 调用后立即返回。使用守护线程和无界 `LinkedBlockingQueue`（指标是 best-effort，端点临时慢时不应丢弃报告）。

## 如何达成设计目的

整体设计变更：

1. **移除静态执行器**：将 `RESTMetricsReporter` 中的静态 `METRICS_EXECUTOR` 移除，改为通过构造器注入 `ExecutorService`，使执行器生命周期由 `RESTSessionCatalog` 管理。

2. **直接 execute 替代 Tasks 模式**：`report()` 方法直接调用 `executor.execute(lambda)` 提交异步任务，任务内部 try-catch 处理 HTTP 异常。外层捕获 `RejectedExecutionException`（执行器已关闭时）。

3. **执行器生命周期管理**：`RESTSessionCatalog` 在初始化时创建固定线程池（1 线程），注册到 `closeables` 以便关闭时清理。

4. **测试适配异步行为**：测试中使用 `Mockito.timeout(5000)` 验证异步上报，并添加等待逻辑避免 Mockito stubbing 与后台线程竞争。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTMetricsReporter.java` (+37/-19 lines)

**修改目的**：修复异步行为。

**工作逻辑**：

1. 移除静态 `METRICS_EXECUTOR` 和 `Tasks`/`ThreadPools` 导入，新增 `RejectedExecutionException` 导入。

2. 构造器新增 `ExecutorService executor` 参数：
```java
RESTMetricsReporter(
    RESTClient client,
    String metricsEndpoint,
    Supplier<Map<String, String>> headers,
    ExecutorService executor) {
```

3. `report()` 方法改为直接 `executor.execute()`：
```java
try {
  executor.execute(() -> {
    try {
      client.post(metricsEndpoint, ReportMetricsRequest.of(report), null, headers,
          ErrorHandlers.defaultErrorHandler());
    } catch (Exception e) {
      LOG.warn("Failed to report metrics to REST endpoint {}", metricsEndpoint, e);
    }
  });
} catch (RejectedExecutionException e) {
  LOG.warn("Failed to report metrics to REST endpoint {}: metrics executor has been shut down",
      metricsEndpoint, e);
}
```

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+11/-1 lines)

**修改目的**：管理指标执行器生命周期。

**工作逻辑**：

1. 新增 `metricsExecutor` 字段。

2. 初始化时创建执行器并注册关闭钩子：
```java
if (reportingViaRestEnabled) {
  this.metricsExecutor = ThreadPools.newFixedThreadPool("rest-metrics-reporter", 1);
  this.closeables.addCloseable(metricsExecutor::shutdown);
}
```

3. 创建 `RESTMetricsReporter` 时传入执行器：
```java
new RESTMetricsReporter(restClient, metricsEndpoint, Map::of, metricsExecutor);
```

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+12/-2 lines)

**修改目的**：适配异步指标上报。

**工作逻辑**：

1. 指标上报验证改为异步等待：
```java
Mockito.verify(adapter, timeout(5000)).execute(matches(HTTPMethod.POST, ...));
```

2. 在冲突提交测试中，添加等待第一次指标上报完成的逻辑，避免 Mockito stubbing 与后台线程竞争导致 `UnfinishedStubbingException`。

## 总结

本提交修复了一个重要的性能问题：`RESTMetricsReporter.report()` 虽然使用了执行器但实际上是同步阻塞的，导致调用者线程堆积。修复将 `Tasks` 轮询等待模式替换为真正的异步 `executor.execute()`，并将执行器生命周期交给 `RESTSessionCatalog` 管理。这对于高频指标上报场景的稳定性至关重要，避免了线程泄漏和潜在的死锁。
