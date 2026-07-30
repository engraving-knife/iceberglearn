# 提交 3204：Core: Add code/type in RestException (#14927)

## 提交信息

- **序号**：3204 / 4088
- **哈希**：5970ddd9278a2baa060183a18f895de3608eab1f
- **短哈希**：5970ddd92
- **日期**：2026-02-04
- **作者**：Miguel A. Sotomayor
- **提交说明**：Core: Add code/type in RestException (#14927)
- **PR/Issue**：#14927

## 总体目的

该提交改进了 REST Catalog 错误处理链路中 `RESTException` 的异常消息内容，使其包含服务端返回的错误码（code）与错误类型（type）。原先在 `ErrorHandlers` 中，多处对未明确处理的错误码会抛 `RESTException("Unable to process: %s", error.message())`，仅包含 `message` 字段。但 `ErrorResponse` 实际携带 `code`（HTTP 状态码）、`type`（错误类型字符串，如 `ValidationException`）与 `message` 三个字段，仅输出 message 会丢失关键的诊断信息。

实际排查 REST 问题时，错误码与类型往往是最先需要的线索：code 能区分是客户端错误（4xx）还是服务端错误（5xx），type 能指明服务端异常类型。把这些信息纳入异常消息，可显著降低用户与开发者在日志中定位问题的成本，尤其当错误被层层包装后，顶层异常消息里仍能看到原始 code/type。

该改动新增一个统一的 `createRESTException(ErrorResponse)` 工厂方法，构造格式为 `"Unable to process (code: <code>, type: <type>): <message>"` 的 `RESTException`，并把 `ErrorHandlers` 中三处直接构造 `RESTException` 的调用替换为该工厂方法。同时新增 `TestErrorHandlers` 测试类覆盖不同字段组合下的消息格式。

## 如何达成设计目的

在 `ErrorHandlers` 中新增私有静态方法 `createRESTException`，统一构造含 code/type/message 的异常消息；将 `CommitErrorHandler`（422 分支）、`DefaultErrorHandler`（兜底分支）、`OAuthErrorHandler`（兜底分支）三处 `throw new RESTException("Unable to process: %s", error.message())` 替换为 `throw createRESTException(error)`。新增测试类 `TestErrorHandlers`，针对 defaultErrorHandler 验证四种字段组合下的消息格式。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (+20/-3 lines)

**修改目的**：统一在 RESTException 消息中携带 code 与 type。

**工作逻辑**：
新增私有静态方法：

```java
private static RESTException createRESTException(ErrorResponse error) {
  return new RESTException(
      "Unable to process (code: %s, type: %s): %s", error.code(), error.type(), error.message());
}
```

该方法从 `ErrorResponse` 取 `code()`、`type()`、`message()` 拼成标准化消息。随后替换三处调用：

1. `CommitErrorHandler.accept` 的 `case 422:` 分支：原 `throw new RESTException("Unable to process: %s", error.message())` 改为 `throw createRESTException(error)`。422 表示请求体语义错误（Unprocessable Entity），type 通常是服务端的校验异常类型，纳入后便于区分校验失败原因。

2. `DefaultErrorHandler.accept` 的末尾兜底 `throw`：同样改为 `createRESTException(error)`。这是未被 switch 命中的所有错误码的最终出口，覆盖面最广。

3. `OAuthErrorHandler.accept` 的末尾兜底 `throw`：改为 `createRESTException(error)`。OAuth 鉴权流程中的未分类错误也会带上 code/type。

注意：这三处之前各自格式相同（`"Unable to process: %s"`），改动后消息前缀变为 `"Unable to process (code: %s, type: %s): %s"`，属于消息格式增强，异常类型仍为 `RESTException`，不影响 catch 逻辑。

### `core/src/test/java/org/apache/iceberg/rest/TestErrorHandlers.java` (+71/-0 lines)

**修改目的**：验证 RESTException 消息中 code/type 的格式与缺失字段处理。

**工作逻辑**：
新建测试类 `TestErrorHandlers`，使用 AssertJ 的 `assertThatThrownBy` 对 `ErrorHandlers.defaultErrorHandler()` 验证四种场景：

1. `errorHandlerIncludesCodeAndType`：code=422、type=`ValidationException`、message=`Invalid input`，断言消息为 `"Unable to process (code: 422, type: ValidationException): Invalid input"`；
2. `errorHandlerWithCodeOnly`：仅 code=422（type/message 为 null），断言消息为 `"Unable to process (code: 422, type: null): null"`，确认 null 字段被原样输出而非抛错；
3. `errorHandlerWithCodeAndMessageOnly`：code=422 + message，type 为 null，断言 `"Unable to process (code: 422, type: null): Invalid input"`；
4. `errorHandlerWithCodeAndTypeOnly`：code=422 + type，message 为 null，断言 `"Unable to process (code: 422, type: ValidationException): null"`。

这组测试覆盖了字段完整与部分缺失的情况，确保 `%s` 格式化对 null 值安全，并锁定了消息格式契约。

## 总结

该提交通过统一的 `createRESTException` 工厂方法，将 REST 错误处理三处兜底异常的消息从仅含 message 增强为含 code、type、message 三段，显著提升了 REST Catalog 错误的可诊断性。改动小而聚焦，并配套完整的单元测试锁定消息格式，对线上问题排查有实际价值。
