# 提交 2195：Core: Catch IAE when decoding JWT (#13192)

## 提交信息

- **序号**：2195 / 4088
- **哈希**：73758ed9a7acb8b47b63ad68a01cfe9dbe50ff79
- **短哈希**：73758ed9a
- **日期**：2025-06-02 22:47:34 -0700
- **作者**：Ning Kang
- **提交说明**：Core: Catch IAE when decoding JWT (#13192)
- **PR/Issue**：#13192

## 总体目的

这个提交修复了 JWT（JSON Web Token）解码时的一个异常处理缺陷。在 Iceberg 的 REST 认证流程中，`OAuth2Util.expiresAtMillis` 方法用于从 JWT token 的 payload 部分提取过期时间。该方法先对 token 的第二段（payload）进行 Base64 URL 解码，再解析为 JSON。此前的代码只捕获了 `IOException`（JSON 解析异常），但没有捕获 `IllegalArgumentException`。当传入的 token 格式不合法（例如 payload 部分不是有效的 Base64 URL 编码字符串，如 "a.b.c" 这样的三段式但第二段无效的字符串），`Base64.getUrlDecoder().decode()` 会抛出 `IllegalArgumentException`，导致未捕获异常向上传播，可能中断认证流程。本提交将 `IllegalArgumentException` 加入捕获范围，使方法对无效 token 返回 null（表示无法解析过期时间），与已有的 "not a token" 等无效输入的处理行为保持一致。

## 如何达成设计目的

- 在 `OAuth2Util` 的 JWT 解码 try-catch 块中，将 catch 的异常类型从单一的 `IOException` 扩展为 `IOException | IllegalArgumentException`，使 Base64 解码失败时也能被优雅处理，返回 null。
- 在测试中增加一个测试用例，传入 `"a.b.c"` 这种格式上像 JWT 但 payload 无效的字符串，验证方法返回 null 而非抛出异常。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java` (修改, +1/-1 lines)

**修改目的**：修复 JWT 解码时未捕获 IllegalArgumentException 的问题。

**工作逻辑**：在 `expiresAtMillis` 方法中，JWT payload 解析的 try 块内执行 `JsonUtil.mapper().readTree(Base64.getUrlDecoder().decode(parts.get(1)))`。将 catch 子句从 `catch (IOException e)` 改为 `catch (IOException | IllegalArgumentException e)`，两种异常均返回 null。`IllegalArgumentException` 主要来自 `Base64.getUrlDecoder().decode()`，当输入不是合法的 Base64 URL 编码时抛出。

### `core/src/test/java/org/apache/iceberg/rest/auth/TestOAuth2Util.java` (修改, +1/-0 lines)

**修改目的**：增加对无效 JWT payload 的测试覆盖。

**工作逻辑**：在 `testExpiresAt` 测试方法中，新增断言 `assertThat(OAuth2Util.expiresAtMillis("a.b.c")).isNull();`。"a.b.c" 是一个三段式字符串（以 . 分隔），会被当作 JWT 处理，但其第二段 "b" 虽然是合法 Base64，第三段等结构可能不构成有效 token；更重要的是该测试验证了对于此类非真实 JWT 的输入，方法能安全返回 null 而不抛出异常。

## 总结

该提交修复了一个 JWT 解码的异常处理边界问题，将 `IllegalArgumentException`（Base64 解码失败）纳入捕获范围，避免无效 token 导致未捕获异常中断 REST 认证流程。修复方式简洁，并附带了测试用例验证，提升了认证模块的健壮性。
