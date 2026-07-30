# 提交 3511：Core : Make REST scan planning poll timeout configurable (#15863)

## 提交信息

- **序号**：3511 / 4088
- **哈希**：e1a2713d18ae62ae91512ebbde052d137c942861
- **短哈希**：e1a2713d18
- **日期**：2026-04-07 08:46:45 -0700
- **作者**：Rahul Shivu Mahadev
- **提交说明**：Core : Make REST scan planning poll timeout configurable (#15863)
- **PR/Issue**：#15863

## 总体目的

使 REST scan planning 的轮询超时时间可配置。原实现中 `MAX_WAIT_TIME_MS` 是硬编码的 5 分钟常量，无法调整。对于大型表或慢速服务端，5 分钟可能不够；对于快速失败场景，5 分钟又太长。新增 `rest-scan-planning.poll-timeout-ms` catalog 属性允许用户自定义超时时间，默认仍为 5 分钟。

同时新增 `RemotePlanTimeoutException` 异常类，在超时时抛出更明确的异常信息（包含 planId、timeout 值和最大重试次数），而非原始的 `NotCompleteException`。

## 如何达成设计目的

1. 在 `RESTCatalogProperties` 中新增 `REST_SCAN_PLANNING_POLL_TIMEOUT_MS` 属性，默认 5 分钟。
2. 在 `RESTTableScan.fetchPlanningResult` 中从 catalogProperties 读取超时值，替代硬编码常量。
3. 添加参数校验确保超时值为正数。
4. 捕获 `NotCompleteException`，抛出包含更多上下文信息的 `RemotePlanTimeoutException`。
5. 新增 `RemotePlanTimeoutException` 异常类。
6. 添加测试覆盖超时、成功和无效值场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+5 lines)

**修改目的**：新增可配置超时属性。

**工作逻辑**：
```java
public static final String REST_SCAN_PLANNING_POLL_TIMEOUT_MS =
    "rest-scan-planning.poll-timeout-ms";
public static final long REST_SCAN_PLANNING_POLL_TIMEOUT_MS_DEFAULT =
    TimeUnit.MINUTES.toMillis(5);
```

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+54/-30 lines)

**修改目的**：从 catalog 属性读取超时值，新增超时异常处理。

**工作逻辑**：
- 移除硬编码 `MAX_WAIT_TIME_MS` 常量。
- `fetchPlanningResult` 中使用 `PropertyUtil.propertyAsLong` 从 catalogProperties 读取超时值，默认使用 `REST_SCAN_PLANNING_POLL_TIMEOUT_MS_DEFAULT`。
- 添加 `Preconditions.checkArgument(maxWaitTimeMs > 0, ...)` 校验。
- 将 Tasks 调用包裹在 try-catch 中，捕获 `NotCompleteException` 时抛出 `RemotePlanTimeoutException`，消息包含 planId、timeout 和 maxRetries。

### `core/src/main/java/org/apache/iceberg/rest/RemotePlanTimeoutException.java` (+26 lines, 新文件)

**修改目的**：新增超时异常类。

**工作逻辑**：继承 RuntimeException，构造函数接受 message 和 cause。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+131 lines)

**修改目的**：测试三种场景。

**工作逻辑**：
- `asyncPlanningRespectsConfigurablePollTimeout`：设置 1ms 超时和永不完成的服务端，验证抛出 `RemotePlanTimeoutException`。
- `asyncPlanningSucceedsWithCustomTimeout`：设置 30000ms 超时，验证正常完成。
- `asyncPlanningRejectsInvalidTimeout`：设置 -1 超时，验证抛出 `IllegalArgumentException`。

## 总结

功能增强提交，将 REST scan planning 的轮询超时从硬编码 5 分钟改为可通过 `rest-scan-planning.poll-timeout-ms` 属性配置。新增 `RemotePlanTimeoutException` 提供更明确的超时错误信息。测试覆盖超时失败、正常成功和无效参数三种场景。
