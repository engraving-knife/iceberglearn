# 提交 3197：Core: Do not cleanup when CREATE transactions fail with 503 (#15051)

## 提交信息

- **序号**：3197 / 4088
- **哈希**：63d4084722824054b3460b8600f5cee9ab9a7248
- **短哈希**：63d408472
- **日期**：2026-02-02
- **作者**：Alessandro Nori
- **提交说明**：Core: Do not cleanup when CREATE transactions fail with 503 (#15051)
- **PR/Issue**：#15051

## 总体目的

该提交修复了 REST Catalog 在创建表（CREATE）事务提交失败时错误清理数据文件的问题。背景如下：当通过 `RESTTableOperations` 执行 CREATE 类型的提交时，原代码使用的是通用的 `ErrorHandlers.tableErrorHandler()`。该 handler 对 503（Service Unavailable）等表示"服务不可用"的错误处理方式，没有把异常归入 `CommitStateUnknownException` 体系，导致上层认为提交"明确失败"，进而触发已写入文件（如 manifest list）的清理逻辑。

但 503 的语义是"服务暂时不可用"，提交可能已经在服务端成功落盘、只是响应未返回，也可能确实未提交——即提交状态未知（commit state unknown）。在状态未知时清理文件是危险的：如果提交其实已成功，删除 manifest list 等文件会导致表元数据指向不存在的文件，造成表损坏。Iceberg 对 `CommitStateUnknownException` 有特殊处理——遇到该异常时不清理可清理的文件，因为无法确定提交是否真的失败。

因此本提交为 CREATE 路径引入专门的 `CreateTableErrorHandler`，让 503 正确映射为 `CommitStateUnknownException`（包装 `ServiceFailureException`），从而跳过文件清理，保证数据安全。

## 如何达成设计目的

设计上新增一个继承自 `CommitErrorHandler` 的 `CreateTableErrorHandler`，针对建表场景定制错误码映射（404→`NoSuchNamespaceException`，409→`AlreadyExistsException`），其余错误（含 503）交给父类 `CommitErrorHandler` 处理，后者会将 503 映射为 `ServiceFailureException`，最终被包装为 `CommitStateUnknownException`，触发"不清理"语义。然后在 `RESTTableOperations` 的 CREATE 分支把 handler 从 `tableErrorHandler()` 替换为 `createTableErrorHandler()`。测试侧新增 `testNoCleanupOnCreate503`，模拟 503 并断言异常类型与文件保留。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (+21/-0 lines)

**修改目的**：新增建表专用的错误处理器，使 503 走"提交状态未知"路径。

**工作逻辑**：
新增公开静态方法 `createTableErrorHandler()` 返回 `CreateTableErrorHandler.INSTANCE`。`CreateTableErrorHandler` 继承 `CommitErrorHandler`，重写 `accept`：

```java
switch (error.code()) {
  case 404:
    throw new NoSuchNamespaceException("%s", error.message());
  case 409:
    throw new AlreadyExistsException("%s", error.message());
}
super.accept(error);
```

404 在建表场景表示命名空间不存在，409 表示表已存在；其余码（包括 422、500、503 等）交给父类 `CommitErrorHandler`。`CommitErrorHandler` 对 503 抛 `ServiceFailureException`，由 `RESTTableOperations` 的提交包装逻辑转换为 `CommitStateUnknownException`，从而跳过清理。相比之下，原先用的 `tableErrorHandler()` 不会产出该包装异常，导致清理被错误触发。

### `core/src/main/java/org/apache/iceberg/rest/RESTTableOperations.java` (+1/-1 lines)

**修改目的**：在 CREATE 分支切换到新的建表错误处理器。

**工作逻辑**：
在 `case CREATE:` 分支中，将 `errorHandler = ErrorHandlers.tableErrorHandler();`（带注释 `// throws NoSuchTableException`）改为 `errorHandler = ErrorHandlers.createTableErrorHandler();`。这一行替换是整个修复的核心落点，确保建表提交使用正确的异常映射。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+71/-0 lines)

**修改目的**：验证 503 时异常映射正确且文件不被清理。

**工作逻辑**：
新增 `testNoCleanupOnCreate503`。通过 `Mockito.spy` 包装 `RESTCatalogAdapter`，重写 `execute`：当请求为 POST 且 path 包含表名时，构造 503 `ErrorResponse` 并交给 errorHandler。然后创建一个 `newCreateTableTransaction`，先 `newAppend().appendFile(FILE_A).commit()`（在事务内追加文件），再调用 `commitTransaction()`。

断言一：`commitTransaction()` 抛 `CommitStateUnknownException`，其 cause 为 `ServiceFailureException`，消息含 `"Service failed: 503"`。

断言二：通过 `allRequests(adapter)` 找到提交请求体 `UpdateTableRequest`，从中提取 `AddSnapshot` 的 `manifestListLocation`，再通过 `catalog.loadTable(TABLE).io().newInputFile(manifestListLocation).exists()` 确认文件仍然存在（`isTrue()`），证明未执行清理。这正是修复所要保证的行为。

## 总结

该修复为 REST Catalog 的建表事务引入专用错误处理器，使 503 等服务不可用错误正确归类为"提交状态未知"，避免在状态不明时删除已写文件而造成表元数据损坏。改动聚焦于错误处理链路，并通过端到端测试同时验证了异常类型与文件保留行为，对生产环境的数据安全性有重要意义。
