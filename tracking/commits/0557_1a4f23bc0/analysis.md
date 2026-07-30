# 提交 0557：Core: Fix REST catalog handling when the service has no view support

## 提交信息

- **序号**：0557 / 4088
- **哈希**：1a4f23bc0e6cda520ca815f2a245f5f21bfbc24f
- **短哈希**：1a4f23bc0
- **日期**：2024-03-02 14:05:42 -0800
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Core: Fix REST catalog handling when the service has no view support (#9853)
- **PR/Issue**：#9853

## 总体目的

修复 `RESTSessionCatalog.loadView` 在底层 REST Catalog 服务不支持视图（view）能力时抛出错误异常类型的问题。按 Iceberg API 契约，`loadView` 在视图不存在时应抛出 `NoSuchViewException`；但当 REST 服务端未实现视图端点时，可能返回 `501 Not Implemented`、`401 Unauthorized`、`403 Forbidden` 等状态码，这些会被 `ErrorHandlers.viewErrorHandler()`/`DefaultErrorHandler` 转换为 `UnsupportedOperationException`、`NotAuthorizedException`、`ForbiddenException` 等异常。这些异常不是 `NoSuchViewException`，会被上层引擎（如 Spark、Trino、Flink）当作真正的运行时错误而非"视图不存在"处理，导致引擎在仅支持表、不支持视图的 REST Catalog 上出现错误的失败行为。

本提交通过捕获这两类异常并重新包装为 `NoSuchViewException`，让没有视图支持的 REST 服务在调用 `loadView` 时行为与"视图不存在"等价，从而保证引擎侧的兼容性。

## 如何达成设计目的

设计思路是：在 `RESTSessionCatalog.loadView` 调用 REST 客户端发起 `GET /v1/views/{view}` 请求的位置加一层 try/catch，捕获 `UnsupportedOperationException` 与 `RESTException`，统一重抛 `NoSuchViewException`。

之所以选择捕获这两个异常类型，是因为它们恰好覆盖了"REST 服务端不支持视图端点"时所有可能从 `ErrorHandlers` 抛出的异常：

- `UnsupportedOperationException`：`DefaultErrorHandler` 在 HTTP `501 Not Implemented` 时抛出，是服务端未实现端点的最典型表现。
- `RESTException`：是 Iceberg REST 客户端所有服务端异常的基类。`BadRequestException`（400）、`NotAuthorizedException`（401）、`ForbiddenException`（403）、`ServiceFailureException`（500）、`ServiceUnavailableException`（503）以及默认的 `RESTException` 都继承自它。捕获 `RESTException` 即可一次性覆盖 401/403 等场景。

这样设计避免了逐一捕获每个子类异常，使代码简洁。同时，重抛时通过 `new NoSuchViewException(e, "Unable to load view %s.%s: %s", name(), identifier, e.getMessage())` 把原异常作为 cause 保留，并把原异常消息拼接进新消息。注释中特别说明：通常拷贝异常消息不是好做法，但引擎在视图不存在时可能只显示顶层异常的消息而抑制 cause，因此把原消息（如 "Not authorized"、"Forbidden"）拼接进来能保证这些关键信息不被丢失。

为什么不直接修改 `ViewErrorHandler` 让 401/403/501 都抛 `NoSuchViewException`？因为 `ViewErrorHandler` 是通用的视图级错误处理器，会被多个视图操作（如 `dropView`、`renameView`、`viewExists` 等）共用。在那里把 401/403 改为 `NoSuchViewException` 会对其他操作产生副作用——例如 `dropView` 时 403 应该让用户知道权限不足，而非误以为视图不存在。所以修复点放在 `loadView` 的调用处更精准，只针对该方法的契约进行兜底。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：让 `loadView` 方法在 REST 服务端不支持视图端点（返回 501/401/403 等）时，仍然按 API 契约抛出 `NoSuchViewException`，使上层引擎把这种场景识别为"视图不存在"而非运行时错误。

**工作逻辑**：

原代码直接调用 `client.get(...)` 并赋值：

```java
LoadViewResponse response =
    client.get(
        paths.view(identifier),
        LoadViewResponse.class,
        headers(context),
        ErrorHandlers.viewErrorHandler());
```

修改后把 `client.get(...)` 调用包入 try/catch：

```java
LoadViewResponse response;
try {
  response =
      client.get(
          paths.view(identifier),
          LoadViewResponse.class,
          headers(context),
          ErrorHandlers.viewErrorHandler());
} catch (UnsupportedOperationException | RESTException e) {
  // Normally, copying an exception message is a bad practice but engines may show just the
  // message and suppress the exception cause when the view does not exist. Since 401 and 403
  // responses can trigger this case, including the message increases the chances that the "Not
  // authorized" or "Forbidden" message is preserved and shown.
  throw new NoSuchViewException(
      e, "Unable to load view %s.%s: %s", name(), identifier, e.getMessage());
}
```

关键逻辑点：

1. `LoadViewResponse response;` 改为先声明后赋值，便于在 try 块内赋值。
2. `catch` 同时捕获 `UnsupportedOperationException` 与 `RESTException`，对应 501 与 4xx/5xx 的 REST 异常族。
3. 重抛的 `NoSuchViewException` 把原异常 `e` 作为 cause，并通过 `%s` 把 `e.getMessage()` 拼到新异常消息中，确保 401 的 "Not authorized" 或 403 的 "Forbidden" 这类关键信息在引擎只显示顶层消息时也能被看到。
4. 注释明确解释了"为什么拷贝消息"——这是个反模式，但在引擎可能抑制 cause 的现实约束下是合理的折中。

下游代码（`AuthSession session = ...`、`ViewMetadata metadata = ...`、`RESTViewOperations ops = ...`、`return new BaseView(...)`）保持不变，仍只在 `client.get` 成功返回后执行视图对象构建。

## 小结

- 本提交是 REST Catalog 视图能力兼容性修复，单文件 16 增 6 删，集中在 `loadView` 方法。
- 影响范围：仅当 REST Catalog 服务端不支持视图端点（返回 501/401/403 等）时行为发生变化，原本会抛 `UnsupportedOperationException`/`NotAuthorizedException`/`ForbiddenException` 等导致引擎误报错误，现在统一抛 `NoSuchViewException`，让引擎把"无视图能力"识别为"视图不存在"。对支持视图的服务端无任何影响。
- 设计上的取舍：修复点放在 `loadView` 调用处而非通用 `ViewErrorHandler`，避免对 `dropView`/`renameView` 等其他视图操作产生副作用。
- 回迁到 1.4.x 的注意事项：1.4.x 若已有 `RESTSessionCatalog.loadView` 实现应可直接套用此 patch；需确认 `NoSuchViewException` 的构造函数签名 `(Throwable cause, String message, Object... args)` 在 1.4.x 中已存在。该修复不改变成功路径行为，回迁风险低，但建议在 1.4.x 的集成测试中加入"REST 服务端返回 501 时 loadView 抛 NoSuchViewException"的用例以验证效果。
