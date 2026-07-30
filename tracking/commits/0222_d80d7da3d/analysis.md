# 提交 0222：Core: Handle IAE in default error handler (#9225)

## 提交信息

- **序号**：0222 / 4088
- **哈希**：d80d7da3d0a956e1b6aeadbe26e0cb3f3ef2fb25
- **短哈希**：d80d7da3d
- **日期**：2023-12-05 19:04:47 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Handle IAE in default error handler (#9225)
- **PR/Issue**：#9225

## 总体目的

这是一个针对 Iceberg REST 客户端默认错误处理器的修复，解决了错误类型在客户端丢失的问题。

Iceberg 的 REST 协议定义了 `ErrorResponse`，其中包含 `type` 字段用于描述服务端异常的类型（通常是异常类的简单名）。当服务端因参数校验失败抛出 `IllegalArgumentException`（IAE）并返回 HTTP 400 时，错误响应的 `type` 字段会携带 `"IllegalArgumentException"`。然而，在此提交之前，客户端的 `DefaultErrorHandler` 对所有 400 响应一律包装为 `BadRequestException`，丢失了原始异常类型信息。

这导致调用方无法用 `catch (IllegalArgumentException)` 这样的精确类型来捕获参数校验异常，只能笼统地捕获 `BadRequestException`。在视图（View）相关场景中尤其明显：当用户为同一方言添加多条 SQL 查询时，服务端会抛出 `IllegalArgumentException("Invalid view version: Cannot add multiple queries for dialect ...")`，但客户端测试只能断言捕获的是泛化的 `Exception`，无法验证异常类型语义。

本提交在 400 分支中增加了对 `IllegalArgumentException` 类型的特判：当 `error.type()` 等于 `IllegalArgumentException` 的简单名时，重新抛出原始的 `IllegalArgumentException` 而非 `BadRequestException`，从而在客户端还原了服务端的异常类型语义。

## 如何达成设计目的

设计思路是在不破坏现有 400→`BadRequestException` 默认行为的前提下，对携带特定 `type` 的 400 响应做"短路"——优先抛出与该类型匹配的原始异常。改动集中在 `ErrorHandlers.DefaultErrorHandler.accept()` 方法的 `case 400` 分支，在抛出 `BadRequestException` 之前插入一个类型匹配判断。同时修改对应测试，将断言从 `Exception.class` 收紧为 `IllegalArgumentException.class`，验证新的行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java`

**修改目的**：在默认错误处理器的 400 分支中识别 `IllegalArgumentException` 类型并还原为原始异常类型，避免被统一包装为 `BadRequestException`。

**工作逻辑**：在 `DefaultErrorHandler.accept(ErrorResponse error)` 方法的 `case 400:` 分支中，于原有 `throw new BadRequestException(...)` 之前插入一段判断：

```java
case 400:
  if (IllegalArgumentException.class.getSimpleName().equals(error.type())) {
    throw new IllegalArgumentException(error.message());
  }
  throw new BadRequestException("Malformed request: %s", error.message());
```

判断逻辑：用 `IllegalArgumentException.class.getSimpleName()` 取得字符串 `"IllegalArgumentException"`，与 `error.type()`（来自服务端响应）做相等比较。若匹配，则用 `error.message()` 作为消息直接抛出 `IllegalArgumentException`，绕过 `BadRequestException` 的包装。注意这里使用 `.getSimpleName()` 与 `error.type()` 比较，与该类其它分支（如 500 用 `error.type()` 作为消息参数）一致，假定服务端在 `type` 字段中填充的是异常类的简单名而非全限定名。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java`

**修改目的**：收紧测试断言以反映修复后的行为——创建视图时为同一方言重复添加 SQL 查询应抛出 `IllegalArgumentException`，而非泛化的 `Exception`。

**工作逻辑**：在 `createView()` 测试中，断言从 `.isInstanceOf(Exception.class)` 改为 `.isInstanceOf(IllegalArgumentException.class)`，消息仍断言包含 `"Invalid view version: Cannot add multiple queries for dialect trino"`。该测试覆盖的场景是：通过 `buildView` 为 trino 方言连续两次 `withQuery(trino.dialect(), trino.sql())`，触发服务端/校验端的 `IllegalArgumentException`。这间接验证了错误处理器修复在端到端路径上的正确性。

## 小结

通过在默认 REST 错误处理器的 400 分支中识别并还原 `IllegalArgumentException`，本提交让客户端能够按原始异常类型精确捕获参数校验错误，恢复了服务端到客户端的异常类型语义，并配套收紧了视图重复方言查询的测试断言。
