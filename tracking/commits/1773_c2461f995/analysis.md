# 提交 1773：Core: Don't remove trailing slash from absolute paths (#12389)

## 提交信息

- **序号**：1773 / 4088
- **哈希**：c2461f995f0e1fec889d09e56924a9c5afaa80c9
- **短哈希**：c2461f995
- **日期**：2025-02-24 09:03:59 +0100
- **作者**：Alexandre Dutra
- **提交说明**：Core: Don't remove trailing slash from absolute paths (#12389)
- **PR/Issue**：#12389（修复 Issue #12373）

## 总体目的

这个提交修复了 REST Catalog 中 HTTP 请求 URI 构建时错误移除绝对路径尾部斜杠的问题。在修改前的代码中，`HTTPRequest.requestUri()` 方法会对所有路径（包括绝对 URI 路径）统一调用 `RESTUtil.stripTrailingSlash()` 移除尾部斜杠。

问题在于，当路径是一个绝对 URI（以 `http://` 或 `https://` 开头，例如用于 OAuth 认证服务器的 token 端点）时，尾部斜杠可能具有语义意义。某些 HTTP 服务器对带尾部斜杠和不带尾部斜杠的 URL 会做不同处理（例如重定向），错误地移除尾部斜杠可能导致请求发送到错误的端点，引发 404 或重定向循环等问题（Issue #12373）。

该提交的解决方案是：仅对相对路径拼接而成的完整路径移除尾部斜杠（因为这是 Iceberg 内部路径拼接产生的冗余斜杠），而对于用户提供的绝对 URI 路径，保持原样不动。

## 如何达成设计目的

提交通过重构 `HTTPRequest.requestUri()` 方法的路径处理逻辑来达成目标。核心设计思路是将绝对路径和相对路径的处理分支分离：

1. **绝对路径分支**：当路径以 `http://` 或 `https://` 开头时，直接使用原始路径，不做任何尾部斜杠处理。
2. **相对路径分支**：当路径是相对路径时，先将 baseUri 的尾部斜杠移除，再与路径拼接，然后移除拼接结果的尾部斜杠，确保内部拼接产生的冗余斜杠被清理。

此外，新增了两个测试用例分别验证：baseUri 带尾部斜杠时的正确处理，以及绝对路径带尾部斜杠时尾部斜杠被保留。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPRequest.java`（修改, +10/-6 lines）

**修改目的**：重构 requestUri() 方法，避免移除绝对路径的尾部斜杠。

**工作逻辑**：
- 原实现使用三元运算符区分绝对路径和相对路径，然后统一对 fullPath 调用 `RESTUtil.stripTrailingSlash()`，这会无差别地移除所有路径的尾部斜杠。
- 新实现改用 if-else 结构：
  - 绝对路径分支：直接使用 `path()` 作为 fullPath，不做任何处理。
  - 相对路径分支：先对 `baseUri().toString()` 调用 `RESTUtil.stripTrailingSlash()` 移除 baseUri 尾部斜杠，再用 `String.format("%s/%s", baseUri, path())` 拼接，然后对拼接结果调用 `RESTUtil.stripTrailingSlash()` 移除冗余尾部斜杠。
- 最终 URIBuilder 直接使用处理后的 fullPath（已提前移除了需要移除的尾部斜杠），不再在 URIBuilder 构造时再次调用 stripTrailingSlash。

### `core/src/test/java/org/apache/iceberg/rest/TestHTTPRequest.java`（修改, +21/-1 lines）

**修改目的**：新增测试用例验证尾部斜杠处理行为。

**工作逻辑**：
- 新增测试用例1：baseUri 为 `http://localhost:8080/foo/`（带尾部斜杠）、path 为 `v1/namespaces/ns/tables/`（带尾部斜杠），验证最终 URI 正确移除了由拼接产生的尾部斜杠，结果为 `http://localhost:8080/foo/v1/namespaces/ns/tables?pageToken=1234&pageSize=10`。
- 新增测试用例2：path 为绝对路径 `http://authserver.com/token/`（带尾部斜杠），验证尾部斜杠被保留，结果为 `http://authserver.com/token/`。这直接验证了修复的核心行为。

## 小结

- **成效**：成功修复了绝对路径尾部斜杠被错误移除的问题。对于用户提供的绝对 URI（如 OAuth token 端点），尾部斜杠现在会被保留；对于内部拼接的相对路径，冗余的尾部斜杠仍会被正确移除。
- **影响范围**：涉及 REST Catalog 的 HTTP 请求构建逻辑（`HTTPRequest`），影响所有使用 REST Catalog 发送 HTTP 请求的场景，特别是使用绝对路径（如 OAuth 认证）时的 URL 构建行为。
- **回迁到 1.4.x 的注意事项**：建议回迁。此提交是对 URL 构建逻辑的 bug 修复，变更清晰且有测试覆盖。回迁时需确认 1.4.x 分支中 `HTTPRequest` 和 `RESTUtil` 的代码结构与该提交一致。无前置依赖，可独立回迁。
