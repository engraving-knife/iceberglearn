# 提交 1579 c98d0d0d4 分析

## 提交信息
- 哈希：c98d0d0d4e1a542923f1239c38430506eeb564bc
- 日期：2025-01-14（Tue Jan 14 17:44:21 2025 +0800）
- 作者：dongwang <mingwbd@gmail.com>
- 消息：Core: Move namespace/table/view validation into try-catch block (#11960)

## 总体目的

本提交修复 `RESTSessionCatalog` 中 `tableExists`、`namespaceExists`、`viewExists` 三个方法的异常处理边界问题：将命名空间/表/视图标识符的合法性校验（`checkIdentifierIsValid` / `checkNamespaceIsValid` / `checkViewIdentifierIsValid`）从 `try` 块外部移入 `try` 块内部。

原实现在调用 `Endpoint.check(...)` 之后、进入 `try` 块之前先执行标识符校验。这意味着如果标识符非法（例如包含非法字符、为 null 等），校验抛出的 `IllegalArgumentException` 不会被 `try-catch` 捕获，而是直接向上抛出，导致调用方收到一个未经过 REST 错误处理器（`ErrorHandlers.tableErrorHandler` 等）规整的原始异常。

这三个 `*Exists` 方法的 `try-catch` 结构本身是为了把 REST 调用可能抛出的异常（如 `NoSuchTableException`）转换为 `false` 返回值（"不存在"），并让其它异常经过对应的 `ErrorHandlers` 处理后抛出。把标识符校验移入 `try` 块后，校验抛出的 `IllegalArgumentException` 也会经过 `ErrorHandlers` 处理，与其它非法调用异常的行为保持一致——即统一通过错误处理器转换为合适的 REST 错误响应或运行时异常。

这是一个一致性修复，让 `*Exists` 系列方法对所有失败路径（无论是标识符非法还是 REST 调用失败）都走统一的错误处理流程，避免调用方需要分别处理校验异常和 REST 异常。

## 如何达成设计目的

通过调整三个方法中两行代码的顺序，把 `check*IsValid(...)` 调用从 `try` 块上方移到 `try` 块内的第一行。无需新增逻辑，仅改变异常抛出点所在的代码块。

### 修改详情

#### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：统一 `tableExists`、`namespaceExists`、`viewExists` 的异常处理路径。

**工作逻辑**（以 `tableExists` 为例，其余两个方法对称）：
```java
   @Override
   public boolean tableExists(SessionContext context, TableIdentifier identifier) {
     Endpoint.check(endpoints, Endpoint.V1_TABLE_EXISTS);
-    checkIdentifierIsValid(identifier);

     try {
+      checkIdentifierIsValid(identifier);
       client.head(paths.table(identifier), headers(context), ErrorHandlers.tableErrorHandler());
       return true;
     } catch (NoSuchTableException e) {
```

- 修改前：`Endpoint.check` 后立即 `checkIdentifierIsValid`，若标识符非法抛出 `IllegalArgumentException`，该异常在 `try` 块外，不会被下方的 `catch (NoSuchTableException e)` 捕获，也不会经过 `ErrorHandlers.tableErrorHandler()` 规整，直接抛给调用方。
- 修改后：`checkIdentifierIsValid` 移入 `try` 块内。虽然 `catch (NoSuchTableException e)` 仍只捕获 `NoSuchTableException`，但 `client.head` 调用上挂的 `ErrorHandlers.tableErrorHandler()` 是在 `client.head` 内部处理 HTTP 错误响应的；标识符非法抛出的 `IllegalArgumentException` 实际上仍会向上抛出，但此时它处于 `try` 块内，未来如果需要在 `catch` 中补充对 `IllegalArgumentException` 的处理（例如转换为 `false` 或特定 REST 错误），代码结构已经就位。更关键的是，这一调整让"校验"与"调用"在同一个异常处理域内，语义上更内聚：`*Exists` 方法的契约是"返回 true/false 或抛出经错误处理器规整的异常"，校验失败也应纳入该契约域。

`namespaceExists` 和 `viewExists` 做了完全相同的调整，分别针对 `checkNamespaceIsValid(namespace)` 和 `checkViewIdentifierIsValid(identifier)`。

## 小结

- **成效**：`RESTSessionCatalog` 的 `tableExists`/`namespaceExists`/`viewExists` 三个方法将标识符校验移入 `try` 块，使校验失败异常与 REST 调用异常处于同一异常处理域，提升异常处理的一致性与内聚性，为未来统一错误转换奠定结构基础。
- **影响范围**：仅 `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` 一个文件，3 处各两行代码的位置调整，无新增逻辑，无测试变更。
- **回迁到 1.4.x 的注意事项**：这是一个低风险的异常处理结构修复，改善了 `*Exists` 方法的错误处理一致性。如果 1.4.x 的 `RESTSessionCatalog` 存在相同代码结构，**可以回迁**以保持与 main 的一致性，但功能影响较小（主要是异常抛出点的代码块位置变化）。回迁时需确认 1.4.x 的 `ErrorHandlers` 行为与该调整兼容。
