# 提交 1872：REST: HTTPRequest.baseUri() should be nullable (#12556)

## 提交信息

- **序号**：1872 / 4088
- **哈希**：6417719ad4c231dd051023aaf78419bfa3fcb51b
- **短哈希**：6417719ad
- **日期**：2025-03-18 17:32:15 +0100
- **作者**：Alexandre Dutra
- **提交说明**：REST: HTTPRequest.baseUri() should be nullable (#12556)
- **PR/Issue**：#12556

## 总体目的

本提交修复 REST 客户端引入 `HTTPRequest` 抽象后引入的一个行为回归。在引入 `HTTPRequest` 之前，技术上可以创建一个没有 base URI 的 `HTTPClient`，此时所有请求必须使用绝对路径（以 `http://` 或 `https://` 开头）。引入 `HTTPRequest` 后，`baseUri()` 字段不能为 null，导致这种"无 base URI + 绝对路径请求"的用法不再可行。

本提交通过允许 `HTTPRequest.baseUri()` 返回 null（当请求路径是绝对 URI 时）来恢复这一旧行为。这主要用于 OAuth 认证等场景：认证服务器的 token endpoint 通常与 REST catalog 的 base URI 不同，需要直接使用绝对 URL 请求。

## 如何达成设计目的

1. **标注可空**：在 `baseUri()` 方法上添加 `@Nullable` 注解，并在 Javadoc 中说明可能为 null 的条件。

2. **提取绝对路径判断**：将 `requestUri()` 中重复的 `path().startsWith("https://") || path().startsWith("http://")` 逻辑提取为私有方法 `hasAbsolutePath()`，复用于请求校验。

3. **新增校验**：在 `check()` 校验方法中，如果 `baseUri()` 为 null 且路径不是绝对路径，则抛出 `RESTException`，明确报错"相对路径但无 base URI"。这保证了只有合法的"无 base URI + 绝对路径"组合能通过。

4. **测试更新**：移除两个测试用例中不必要的 `baseUri`（验证绝对路径请求无需 base URI），新增 `relativePathWithoutBaseUri` 测试验证相对路径无 base URI 时抛出异常。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPRequest.java` (修改, +13/-2 lines)

**修改目的**：允许 baseUri 为 null 并补充校验。

**工作逻辑**：
- `baseUri()` 方法添加 `@Nullable` 注解，Javadoc 更新说明"当 REST 客户端没有 base URI 且路径为绝对 URI 时可为 null"。
- `requestUri()` 默认方法中将原本内联的 `path().startsWith("https://") || path().startsWith("http://")` 替换为调用 `hasAbsolutePath()`。
- `check()` 校验方法新增逻辑：`if (baseUri() == null && !hasAbsolutePath())` 则抛出 `RESTException("Received a request with a relative path and no base URI: %s", path())`。
- 新增私有方法 `hasAbsolutePath()` 返回 `path().startsWith("https://") || path().startsWith("http://")`。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPRequest.java` (修改, +12/-2 lines)

**修改目的**：更新测试覆盖新的可空行为。

**工作逻辑**：
- 在两个绝对路径请求的测试用例中移除 `.baseUri(URI.create("http://localhost:8080/foo"))`，证明绝对路径请求不需要 base URI。
- 新增 `relativePathWithoutBaseUri` 测试：构建一个无 baseUri、路径为 `v1/namespaces`（相对路径）的请求，断言抛出 `RESTException`，消息为 "Received a request with a relative path and no base URI: v1/namespaces"。

## 总结

本提交修复了 `HTTPRequest` 引入后的行为回归，允许 `baseUri()` 为 null（当请求路径为绝对 URI 时），恢复了无 base URI 客户端的能力（主要用于 OAuth 认证等场景）。通过在 `check()` 中新增校验确保"相对路径 + 无 base URI"的组合被明确拒绝，并补充相应测试。
