# 提交 2343：Core: Implement close() method in CompositeMetricsReporter (#13535)

## 提交信息

- **序号**：2343 / 4088
- **哈希**：ae672a270dceea92fc56fc2ca51a1a9d03715122
- **短哈希**：ae672a270
- **日期**：2025-07-11 17:04:08 -0600
- **作者**：Anoop Johnson
- **提交说明**：Core: Implement close() method in CompositeMetricsReporter (#13535)
- **PR/Issue**：#13535

## 总体目的

本提交为 `CompositeMetricsReporter` 的内部实现类实现了 `close()` 方法，确保组合报告器关闭时能正确关闭其包含的所有子报告器。

`CompositeMetricsReporter` 是 Iceberg 中的组合模式指标报告器，它将多个 `MetricsReporter` 实例组合在一起，使得一次指标上报可以同时发送到多个目标（如 REST 端点、日志、自定义报告器等）。`MetricsReporter` 接口继承了 `AutoCloseable`，因此每个报告器都可能持有需要清理的资源（如线程池、HTTP 连接等）。

此前，`CompositeMetricsReporter` 的内部实现类 `CompositeMetricsReporter`（在 `MetricsReporters.java` 中）没有重写 `close()` 方法。这意味着当组合报告器被关闭时，其子报告器的 `close()` 方法不会被调用，导致子报告器持有的资源（如提交 2341 中 `RESTMetricsReporter` 的线程池）无法被正确清理，造成资源泄漏。

本提交通过遍历所有子报告器并逐一关闭它们来修复这个问题。每个子报告器的关闭失败不会影响其他报告器的关闭，失败仅记录警告日志。

## 如何达成设计目的

在 `CompositeMetricsReporter` 内部类中重写 `close()` 方法，遍历所有子报告器并调用各自的 `close()`，异常隔离处理。

关键设计点：
1. 遍历所有注册的 `reporters`，逐一调用 `close()`。
2. 每个子报告器的关闭异常被捕获并记录为警告日志，不影响其他报告器的关闭。
3. 使用已有的 `LOG` 记录器输出失败信息，包含报告器的类名便于排查。

## 修改详情

### `core/src/main/java/org/apache/iceberg/metrics/MetricsReporters.java` (+11/-0 lines)

**修改目的**：为组合报告器实现 `close()` 方法。

**工作逻辑**：在 `CompositeMetricsReporter` 内部类中新增 `close()` 方法重写。方法遍历 `reporters` 集合中的每个 `MetricsReporter`，在 try-catch 中调用 `reporter.close()`。如果关闭抛出异常，使用 `LOG.warn` 记录警告日志（包含报告器类名和异常），然后继续关闭下一个报告器。这确保了一个报告器的关闭失败不会阻止其他报告器的资源清理。

## 总结

本提交为 `CompositeMetricsReporter` 实现了 `close()` 方法，修复了组合报告器关闭时子报告器资源不被清理的问题。这与提交 2341（使 `RESTMetricsReporter` 使用异步线程池）密切相关——异步报告器持有线程池资源，需要通过 `close()` 正确释放。异常隔离的设计确保了一个报告器的关闭失败不会影响其他报告器。
