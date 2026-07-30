# 提交 3569：Core: Use Stream overload for reading response in HTTPClient (#15648)

## 提交信息

- **序号**：3569 / 4088
- **哈希**：5dae5fc856e228cd8e1b69ba14909d0067006650
- **短哈希**：5dae5fc85
- **日期**：2026-04-21 12:59:33 -0700
- **作者**：alpbeysir
- **提交说明**：Core: Use Stream overload for reading response in HTTPClient (#15648)
- **PR/Issue**：#15648

## 总体目的

该提交优化了 Iceberg REST 客户端 `HTTPClient` 中响应体的读取方式。之前，HTTP 响应体先通过 `extractResponseBodyAsString(response)` 提取为字符串，然后再用 Jackson 的 `ObjectReader.readValue(String)` 从字符串反序列化为目标类型。这种方式需要先将整个响应体读入内存中的字符串，再解析，存在双重内存开销。

该提交改为使用 Jackson 的 `ObjectReader.readValue(InputStream)` 重载方法，直接从 `response.getEntity().getContent()` 获取的输入流中读取并反序列化，避免了中间字符串的创建。这不仅减少了内存使用，还可能提升解析性能。此外，该提交还移除了对 `JsonProcessingException` 的单独捕获，将其统一归入 `IOException` 处理。

## 如何达成设计目的

将响应体读取逻辑从"先提取字符串再解析"改为"直接从输入流解析"。仅在错误处理路径（非成功响应）中才将响应体提取为字符串（因为错误处理可能需要完整的响应文本）。成功响应直接从实体输入流读取。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (+6/-16 lines)

**修改目的**：使用流式读取替代字符串读取，优化内存使用。

**工作逻辑**：
- 移除 `JsonProcessingException` 导入（`IOException` 已涵盖）。
- 成功响应路径不再调用 `extractResponseBodyAsString`，改为直接检查 `response.getEntity() == null`。
- 反序列化改为 `reader.readValue(response.getEntity().getContent())`，直接从输入流读取。
- 错误处理路径中，`extractResponseBodyAsString` 调用移至 `!isSuccessful(response)` 分支内，仅在错误时提取字符串。
- 移除了之前对 `JsonProcessingException` 的单独 try-catch 块（该异常现在由外层 `IOException` catch 统一处理）。

```java
// 之前：先提取字符串再解析
String responseBody = extractResponseBodyAsString(response);
// ...
return reader.readValue(responseBody);

// 之后：直接从流解析
return reader.readValue(response.getEntity().getContent());
```

## 总结

该提交通过使用流式读取优化了 HTTPClient 的响应处理，减少了内存开销（避免中间字符串创建）。这是一个性能优化，特别对大响应体有积极影响。同时简化了异常处理逻辑，将 `JsonProcessingException` 统一归入 `IOException` 处理。
