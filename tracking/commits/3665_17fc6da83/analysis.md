# 提交 3665：API, Core: Handle 404 from /v1/config for missing warehouses (#16059)

## 提交信息

- **序号**：3665 / 4088
- **哈希**：17fc6da837442443421cfbac01ff2941a820ba20
- **短哈希**：17fc6da83
- **日期**：2026-05-07 13:32:30 -0700
- **作者**：Oguzhan Unlu
- **提交说明**：API, Core: Handle 404 from /v1/config for missing warehouses (#16059)
- **PR/Issue**：#16059

## 总体目的

这个提交为 REST Catalog 的 `/v1/config` 端点新增了对 404 响应的专门处理，使其在 warehouse 不存在时抛出明确的 `NoSuchWarehouseException`，而非笼统的 `RESTException`。

REST Catalog 在初始化时会调用 `/v1/config` 端点获取配置。当 warehouse 不存在时，REST 服务器会返回 404 响应。此前，`RESTSessionCatalog` 使用 `defaultErrorHandler()` 处理 config 端点的错误，而 `defaultErrorHandler` 对 404 响应（除非有特定的 error type 匹配如 `NoSuchTableException` 等）会抛出笼统的 `RESTException`。这使得用户无法区分"warehouse 不存在"和"URI 配置错误"等不同原因，难以诊断问题。

本提交新增 `NoSuchWarehouseException` 异常类和 `configErrorHandler()`，当 config 端点返回 404 且包含有效的 error type 时，抛出 `NoSuchWarehouseException`，提供更清晰的错误信息。

## 如何达成设计目的

1. 在 API 模块新增 `NoSuchWarehouseException`（继承 `RuntimeException`）。
2. 在 `ErrorHandlers` 新增 `configErrorHandler()`，其 `ConfigErrorHandler` 在 404 且 `error.type()` 不为 null 时抛出 `NoSuchWarehouseException`，其他情况委托给父类 `DefaultErrorHandler`。
3. 在 `RESTSessionCatalog` 的 config 调用处，将 `defaultErrorHandler()` 替换为 `configErrorHandler()`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/exceptions/NoSuchWarehouseException.java` (+34 lines, new)

**修改目的**：新增 warehouse 不存在异常。

**工作逻辑**：
```java
public class NoSuchWarehouseException extends RuntimeException {
  @FormatMethod
  public NoSuchWarehouseException(String message, Object... args) {
    super(String.format(message, args));
  }

  @FormatMethod
  public NoSuchWarehouseException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }
}
```
使用 `@FormatMethod` 注解支持格式化消息。

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (+18/-1 lines)

**修改目的**：新增 config 端点专用错误处理器。

**工作逻辑**：
```java
public static Consumer<ErrorResponse> configErrorHandler() {
  return ConfigErrorHandler.INSTANCE;
}

private static class ConfigErrorHandler extends DefaultErrorHandler {
  private static final ErrorHandler INSTANCE = new ConfigErrorHandler();

  @Override
  public void accept(ErrorResponse error) {
    if (error.code() == 404 && error.type() != null) {
      throw new NoSuchWarehouseException("%s", error.message());
    }
    super.accept(error);
  }
}
```
`ConfigErrorHandler` 继承 `DefaultErrorHandler`，仅在 404 且 error type 非 null 时抛出 `NoSuchWarehouseException`，否则委托父类处理。`error.type() != null` 条件用于区分"服务器识别为 warehouse 不存在的 404"和"URI 配置错误导致的 404"（后者通常没有 error type）。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+1/-1 line)

**修改目的**：config 端点调用使用新的错误处理器。

**工作逻辑**：
```java
// 旧
ErrorHandlers.defaultErrorHandler()
// 新
ErrorHandlers.configErrorHandler()
```

### `core/src/test/java/org/apache/iceberg/rest/TestErrorHandlers.java` (+36 lines)

**修改目的**：新增 configErrorHandler 的单元测试。

**工作逻辑**：验证 404 带有效 error type 时抛出 `NoSuchWarehouseException`，404 不带 error type 时走默认处理，其他状态码行为不变。

## 总结

这个提交为 REST Catalog 的 `/v1/config` 端点新增了专门的 404 错误处理，当 warehouse 不存在时抛出明确的 `NoSuchWarehouseException`，使错误信息更具可诊断性。通过 `error.type() != null` 条件区分"warehouse 不存在"和"URI 配置错误"两种 404 场景。这是一个改进用户体验的改动，帮助用户更快定位 warehouse 配置问题。
