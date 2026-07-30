# 提交 1920：Core: child HTTPClient should not close shared resources (#12566)

## 提交信息

- **序号**：1920 / 4088
- **哈希**：62632986372cec0ef5233173d31526baf858ca71
- **短哈希**：626329863
- **日期**：2025-03-25 11:37:53 -0700
- **作者**：Alexandre Dutra
- **提交说明**：Core: child HTTPClient should not close shared resources (#12566)
- **PR/Issue**：#12566

## 总体目的

`HTTPClient`（Iceberg REST 客户端实现）支持通过 `withAuthSession(AuthSession)` 派生出一个"子客户端"：子客户端与父客户端共享底层 Apache HTTP 客户端（`httpClient`）、`mapper`、`baseHeaders` 等资源，仅替换 `authSession`。这种设计用于在保持连接池复用的前提下切换鉴权会话。

问题在于：当子客户端被 `close()` 时，原实现会关闭 `authSession`（如果非空），并 **无条件** 关闭共享的 `httpClient`。这导致父客户端共享的底层 HTTP 客户端被一起关掉，父客户端实际上也变得不可用——后续任何请求都会失败。在 REST Catalog 鉴权流程中会创建短生命周期的子客户端做 token 刷新等，关闭这些子客户端就把主连接池也带垮了。

本提交修复该问题：让子客户端（非 root）的 `close()` 既不关闭共享的 `httpClient`，也不关闭 `authSession`（AuthSession 的生命周期由调用方管理），只有 root 客户端才在 close 时关闭底层 HTTP 客户端。

## 如何达成设计目的

1. 在 `HTTPClient` 中新增 `private final boolean isRootClient` 字段：根构造器（公开构造路径）设为 `true`，子构造器（`withAuthSession` 派生路径）设为 `false`。
2. 重写 `close()`：移除对 `authSession.close()` 的调用（注释说明 AuthSession 由 owner 管理），仅在 `isRootClient` 为 `true` 时才 `httpClient.close(CloseMode.GRACEFUL)`。
3. 在 `RESTClient` 接口的 `withAuthSession` 默认方法上补充 Javadoc，明确两条实现契约：关闭返回的子客户端不应影响父客户端共享资源；关闭子客户端不应关闭传入的 AuthSession。
4. 测试 `testCloseChild`：用 mock 的 AuthSession 派生子客户端并在 try-with-resources 中关闭，断言 `authSession.close()` 从未被调用，且父客户端在子客户端关闭后仍能成功执行 HTTP 方法。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (修改, +6/-5 lines)

**修改目的**：区分 root/child 客户端，修正 close 行为。

**工作逻辑**：

新增字段 `private final boolean isRootClient;`。根构造器（接收 builder 参数的那个）中 `this.isRootClient = true;`；子构造器（从 parent 派生）中 `this.isRootClient = false;`。

`close()` 改为：

```java
@Override
public void close() throws IOException {
  // Do not close the AuthSession as it's managed by the owner of this HTTPClient.
  // Only close the underlying Apache HTTP client if this is a root HTTPClient.
  if (isRootClient) {
    httpClient.close(CloseMode.GRACEFUL);
  }
}
```

移除了原来的 `try/finally` 与 `authSession.close()` 调用，避免子客户端关闭共享 HTTP 客户端与别人的 AuthSession。

### `core/src/main/java/org/apache/iceberg/rest/RESTClient.java` (修改, +13/-1 lines)

**修改目的**：在接口契约上明确子客户端的关闭语义。

**工作逻辑**：给 `withAuthSession(AuthSession session)` 补充 Javadoc，列出两条实现要求：

- 关闭返回的客户端不应影响父客户端；共享资源应等父客户端关闭时才关闭。
- 关闭返回的客户端不应关闭传入的 AuthSession，那是调用方的责任。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPClient.java` (修改, +14 lines)

**修改目的**：验证子客户端关闭不影响父客户端与 AuthSession。

**工作逻辑**：

```java
@Test
public void testCloseChild() throws IOException {
  AuthSession authSession = mock(AuthSession.class);
  try (RESTClient child = restClient.withAuthSession(authSession)) {
    assertThat(child).isNotNull().isNotSameAs(restClient);
  }
  verify(authSession, never().description("RESTClient should not close the AuthSession")).close();
  assertThatCode(() -> testHttpMethodOnSuccess(HttpMethod.POST))
      .as("Parent RESTClient should still be operational after child is closed")
      .doesNotThrowAnyException();
}
```

断言子客户端 close 后：mock 的 AuthSession 从未被 close，且父客户端仍能成功执行 POST 请求。

## 总结

本提交修复 `HTTPClient` 子客户端（由 `withAuthSession` 派生）被关闭时连带关闭父客户端共享底层 HTTP 客户端与 AuthSession 的缺陷。通过新增 `isRootClient` 标记，使只有根客户端在 close 时关闭底层 HTTP 客户端，子客户端 close 不影响共享资源与 AuthSession；并在 `RESTClient` 接口 Javadoc 中明确该关闭契约，补充测试验证父客户端在子客户端关闭后仍可用。
