# 提交 3630：Core: Propagate server error message in failed remote scan planning responses (#16024)

## 提交信息

- **序号**：3630 / 4088
- **哈希**：76283899d2c671082b402cd2bb476a29c77ba009
- **短哈希**：76283899d
- **日期**：2026-05-02 16:34:51 -0600
- **作者**：Prashant Singh
- **提交说明**：Core: Propagate server error message in failed remote scan planning responses (#16024)
- **PR/Issue**：#16024

## 总体目的

这个提交改进了 REST Catalog 远程扫描规划（remote scan planning）失败时的错误处理，将服务器返回的错误信息传播到客户端异常中。

Iceberg REST Catalog 支持异步的远程扫描规划，服务器端可以返回不同的计划状态（SUBMITTED、COMPLETED、FAILED、CANCELLED）。当状态为 FAILED 时，服务器可能会在响应中包含一个 `ErrorResponse`，提供失败的具体原因（错误类型、错误代码、错误消息）。

之前，当扫描规划失败时，客户端只是抛出一个简单的 `IllegalStateException`，只包含 planId 和状态信息，不包含服务器返回的具体错误原因。这使调试远程扫描规划失败变得困难，因为用户无法看到服务器端的错误详情。

这个提交在 `PlanTableScanResponse` 和 `FetchPlanningResultResponse` 中添加了 `errorResponse` 字段，并在失败时将错误信息包含在异常消息中。

## 如何达成设计目的

1. 在 `PlanTableScanResponse` 和 `FetchPlanningResultResponse` 中添加 `ErrorResponse` 字段和对应的 Builder 方法。
2. 在响应的 parser 中添加 `errorResponse` 字段的序列化/反序列化支持。
3. 在 `RESTTableScan` 中，当计划状态为 FAILED 时，使用 `failureMessage()` 方法从 `ErrorResponse` 构建详细的错误消息。
4. 重构 `fetchPlanningResult()` 方法中的状态处理逻辑，使用 switch 语句替代 if-else，更清晰地处理每种状态。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponse.java` (+18/-0 lines)

**修改目的**：添加 ErrorResponse 字段。

**工作逻辑**：
1. 新增 `errorResponse` 字段和 `errorResponse()` 方法。
2. 新增 Builder 的 `withErrorResponse()` 方法。
3. 在 `validate()` 中添加校验：error 只能在 FAILED 状态时返回。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchPlanningResultResponse.java` (+20/-2 lines)

**修改目的**：添加 ErrorResponse 字段。

**工作逻辑**：
与 `PlanTableScanResponse` 类似的修改，添加 `errorResponse` 字段、方法和校验。

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+30/-12 lines)

**修改目的**：在失败时传播服务器错误信息。

**工作逻辑**：

1. **新增 `failureMessage()` 方法**：
```java
private static String failureMessage(String planId, ErrorResponse error) {
  Preconditions.checkArgument(error != null, "Error must be present for failed status");
  return String.format(
      Locale.ROOT,
      "Remote scan planning failed for planId: %s: %s (code=%d): %s",
      planId, error.type(), error.code(), error.message());
}
```
构建包含错误类型、代码和消息的详细错误信息。

2. **重构状态处理**：在 `plan()` 方法的 FAILED/CANCELLED 分支使用 `failureMessage()`，在 `fetchPlanningResult()` 方法中使用 switch 语句替代 if-else：
```java
switch (response.planStatus()) {
  case COMPLETED:
    result.set(response);
    break;
  case SUBMITTED:
    throw new NotCompleteException();
  case FAILED:
    throw new IllegalStateException(failureMessage(id, response.errorResponse()));
  case CANCELLED:
    throw new IllegalStateException(
        String.format(Locale.ROOT, "Remote scan planning cancelled for planId: %s", id));
  default:
    throw new IllegalStateException(...);
}
```

### Parser 文件

- `PlanTableScanResponseParser.java` (+11/-0 lines)：添加 errorResponse 的序列化/反序列化。
- `FetchPlanningResultResponseParser.java` (+12/-0 lines)：添加 errorResponse 的序列化/反序列化。
- `ErrorResponseParser.java` (+5/-2 lines)：小幅调整。

### 测试文件

- `TestRESTScanPlanning.java` (+97/-0 lines)：测试失败场景下错误信息的传播。
- `TestPlanTableScanResponseParser.java` (+68/-0 lines)：测试 parser 对 errorResponse 的处理。
- `TestFetchPlanningResultResponseParser.java` (+66/-0 lines)：测试 parser 对 errorResponse 的处理。

## 总结

这个提交改进了 REST Catalog 远程扫描规划失败时的错误处理，将服务器返回的错误信息（类型、代码、消息）传播到客户端异常中。这使得调试远程扫描规划失败变得更加容易，用户可以直接从异常消息中看到服务器端的失败原因。同时在响应类中添加了 `ErrorResponse` 字段，并在 parser 中添加了序列化支持。
