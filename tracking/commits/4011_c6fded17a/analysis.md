# 提交 4011：Core: Fix OAuthTokenResponse.addScope error to show the invalid scope (#17126)

## 提交信息

- **序号**：4011 / 4088
- **哈希**：c6fded17a633103c2364fc6c65ca969ce6de1058
- **短哈希**：c6fded17a
- **日期**：2026-07-11 12:45:14 -0700
- **作者**：Anas Khan
- **提交说明**：Core: Fix OAuthTokenResponse.addScope error to show the invalid scope (#17126)
- **PR/Issue**：#17126

## 总体目的

本提交修复 `OAuthTokenResponse.Builder.addScope(String)` 方法中错误信息格式化的 bug。原代码使用 `Preconditions.checkArgument(OAuth2Util.isValidScopeToken(scope), "Invalid scope: %s")`，消息中含 `%s` 占位符但没有传入 `scope` 参数。Guava 的 `Preconditions` 在占位符无对应参数时保留字面 `%s`，导致抛出的 `IllegalArgumentException` 消息是字面的 "Invalid scope: %s"，而不是实际被拒绝的 scope 值，使排查 OAuth 配置问题非常困难。

本提交将 `scope` 作为格式参数传入，使错误消息正确显示被拒绝的 scope 值，并新增回归测试。

## 如何达成设计目的

在 `addScope` 的 `Preconditions.checkArgument` 调用中补上 `scope` 参数：
```java
Preconditions.checkArgument(OAuth2Util.isValidScopeToken(scope), "Invalid scope: %s", scope);
```
并新增测试 `invalidScopeReportedInErrorMsg`，传入 "bad scope"（含空格，非法）并断言异常消息为 "Invalid scope: bad scope"。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/OAuthTokenResponse.java` (+1/-1 lines)

**修改目的**：修复错误消息格式化。

**工作逻辑**：
```java
// 修改前
Preconditions.checkArgument(OAuth2Util.isValidScopeToken(scope), "Invalid scope: %s");
// 修改后
Preconditions.checkArgument(OAuth2Util.isValidScopeToken(scope), "Invalid scope: %s", scope);
```
Guava `checkArgument(boolean, String, Object...)` 会用第三个参数填充 `%s`，现在错误消息会显示实际被拒绝的 scope。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestOAuthTokenResponse.java` (+7/-0 lines)

**修改目的**：新增回归测试。

**工作逻辑**：
```java
@Test
void invalidScopeReportedInErrorMsg() {
  assertThatThrownBy(() -> OAuthTokenResponse.builder().addScope("bad scope"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid scope: bad scope");
}
```
"bad scope" 因含空格而非合法 scope token，触发校验失败，断言消息包含实际 scope 值。

## 总结

这是一次小的 bug 修复提交，修正了 `OAuthTokenResponse.addScope` 错误消息中 `%s` 占位符未填充的问题，使非法 scope 的错误消息能正确显示被拒绝的值，便于 OAuth 配置排错。改动仅一行核心代码加一个回归测试，体现了对错误消息可观测性的关注。
