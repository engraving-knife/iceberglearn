# 提交 3785：Core: Replace deprecated CloseableHttpClient.execute (#16149)

## 提交信息

- **序号**：3785 / 4088
- **哈希**：d3cb9e354485bd5a27bfade31c8a241193b78cfa
- **短哈希**：d3cb9e354
- **日期**：2026-05-25 07:45:59 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Core: Replace deprecated CloseableHttpClient.execute (#16149)
- **PR/Issue**：#16149

## 总体目的

这个提交将 Apache HttpClient 5 中已废弃的 `CloseableHttpClient.execute(HttpUriRequest, HttpContext)` 方法替换为新的 `execute(HttpUriRequest, HttpContext, HttpClientResponseHandler)` 方法。

在 Apache HttpClient 5 中，旧的 `execute(request, context)` 方法返回 `CloseableHttpResponse`，需要调用者手动管理响应的关闭（try-with-resources）。这种方式已被标记为 `@Deprecated`，新版本推荐使用接受 `HttpClientResponseHandler` 回调的 `execute` 重载，这种方式会自动管理响应的生命周期，确保资源正确释放。

## 如何达成设计目的

1. 将 `execute` 方法的响应处理逻辑从 try-with-resources 模式改为通过 `HttpClientResponseHandler` 回调处理。
2. 将响应类型从 `CloseableHttpResponse` 改为 `ClassicHttpResponse`（新 API 使用的接口类型）。
3. 将原来的内联处理逻辑抽取为独立的 `handleResponse` 方法作为回调。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (+58/-43 lines)

**修改目的**：替换废弃的 `execute` 方法调用，改用新的回调式 API。

**工作逻辑**：

1. **导入变更**：
   - 移除 `import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;`
   - 新增 `import org.apache.hc.core5.http.ClassicHttpResponse;`

2. **方法签名变更**：将 `extractResponseBodyAsString`、`isSuccessful`、`buildDefaultErrorResponse`、`throwFailure`、`emptyBody` 等辅助方法的参数类型从 `CloseableHttpResponse` 改为 `ClassicHttpResponse`。`ClassicHttpResponse` 是 `CloseableHttpResponse` 的父接口，更通用。

3. **`execute` 方法重构**：
   移除 `@SuppressWarnings("deprecation")` 注解，将原来的 try-with-resources 模式：
   ```java
   try (CloseableHttpResponse response = httpClient.execute(request, context)) {
     // 处理响应...
   } catch (IOException e) {
     throw new RESTException(e, ...);
   }
   ```
   改为回调式：
   ```java
   try {
     return httpClient.execute(
         request,
         context,
         response -> handleResponse(req, response, responseType, errorHandler, responseHeaders, parserContext));
   } catch (IOException e) {
     throw new RESTException(e, ...);
   }
   ```

4. **新增 `handleResponse` 方法**：将原来内联在 try 块中的响应处理逻辑抽取为独立方法，作为 `HttpClientResponseHandler` 的实现。该方法处理响应头、空响应体判断、错误响应处理、响应体解析等逻辑。新方法声明 `throws IOException`，因为回调接口要求。

这种方式的优势是 HttpClient 框架会自动管理 `ClassicHttpResponse` 的关闭，无需调用者手动处理，减少了资源泄漏的风险。

## 总结

这个提交通过将 REST HTTP 客户端从废弃的 `CloseableHttpClient.execute` 方法迁移到新的回调式 API，消除了编译警告并确保了更好的资源管理。新的 `HttpClientResponseHandler` 回调模式自动管理响应的生命周期，降低了资源泄漏的风险。这是一个技术债务清理提交，保持代码与最新版本的 Apache HttpClient 5 API 一致。
