# 提交 3631：Core: Surface failed scan planning even when server omits error payload (#16197)

## 提交信息

- **序号**：3631 / 4088
- **哈希**：6d7ab339aad435d3050c05ed089b17aa508d7268
- **短哈希**：6d7ab339a
- **日期**：2026-05-02 20:49:19 -0700
- **作者**：Prashant Singh
- **提交说明**：Core: Surface failed scan planning even when server omits error payload (#16197)
- **PR/Issue**：#16197

## 总体目的

这个提交是提交 3630 的后续修复，处理当服务器在扫描规划失败时省略错误载荷（error payload）的情况。

提交 3630 在 `failureMessage()` 方法中添加了前置条件检查 `Preconditions.checkArgument(error != null, ...)`，要求 FAILED 状态必须包含 ErrorResponse。然而，REST Catalog 规范虽然要求服务器在 FAILED 状态时返回 ErrorResponse，但如果服务器违反了规范，没有包含错误载荷，客户端会在原有失败之上再抛出一个 `IllegalArgumentException`，使问题更加混乱。

这个提交将严格的前置条件检查替换为优雅降级处理：当 ErrorResponse 为 null 时，使用 "unknown" 和 code 0 作为默认值，确保客户端始终能给出有意义的失败消息，而不是在已经失败的响应上再抛出异常。

## 如何达成设计目的

修改 `failureMessage()` 方法，将 `Preconditions.checkArgument` 检查替换为每字段的回退值（null 检查），当 error 为 null 时使用 "unknown"/0 作为默认值。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+8/-4 lines)

**修改目的**：使 failureMessage 方法在 error 为 null 时优雅降级。

**工作逻辑**：
```java
private static String failureMessage(String planId, ErrorResponse error) {
  // If a FAILED response lacks the expected error payload, still return a useful error
  // message instead of throwing.
  String type = error != null ? error.type() : "unknown";
  int code = error != null ? error.code() : 0;
  String message = error != null ? error.message() : "unknown";
  return String.format(
      Locale.ROOT,
      "Remote scan planning failed for planId: %s: %s (code=%d): %s",
      planId, type, code, message);
}
```
当 error 为 null 时，type 为 "unknown"，code 为 0，message 为 "unknown"，仍然返回一个有意义的错误消息。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+21/-0 lines)

**修改目的**：添加测试验证服务器省略错误载荷时的行为。

**工作逻辑**：
```java
@ParameterizedTest
@EnumSource(PlanningMode.class)
public void planningFailsWithoutServerErrorIsStillSurfaced(...) {
  // 服务器返回 FAILED 状态但不包含 ErrorResponse
  CatalogWithAdapter catalogWithAdapter =
      catalogThatFailsPlanning(null, behavior, "test-planning-failed-no-error");
  // 验证仍然抛出 IllegalStateException，而不是 IllegalArgumentException
  assertThatThrownBy(scan::planFiles)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Remote scan planning failed")
      .hasMessageContaining("unknown")
      .hasMessageContaining("code=0");
}
```

## 总结

这个提交改进了提交 3630 的错误处理健壮性，当服务器违反规范省略错误载荷时，客户端不再抛出额外的 `IllegalArgumentException`，而是使用默认值生成有意义的失败消息。这是防御性编程的良好实践，确保客户端在面对不规范的服务器响应时仍然能给出有用的错误信息。
