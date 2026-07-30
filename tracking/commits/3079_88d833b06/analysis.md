# 提交 3079：Core: Handle NotFound exception for missing metadata file (#13143)

## 提交信息

- **序号**：3079 / 4088
- **哈希**：88d833b06bdb07101e458b8fdb7e60fe9e1a9c9a
- **短哈希**：88d833b06
- **日期**：2026-01-07
- **作者**：Ashok
- **提交说明**：Core: Handle NotFound exception for missing metadata file (#13143)
- **PR/Issue**：#13143

## 总体目的

当 Iceberg 表的元数据文件（metadata JSON 文件）因某种原因丢失或不可访问时（例如底层存储故障、文件被误删、并发操作导致文件被清理等），加载表（`loadTable`）会抛出异常。然而此前的错误处理链路中，REST Catalog 的服务端错误响应在映射回客户端异常时，对这种"文件未找到"的情况处理不够精确：HTTP 404 响应只会被映射为 `NoSuchNamespaceException` 或 `NoSuchTableException`，而实际上表是存在的（catalog 层面表元数据存在），只是底层的 metadata 文件不可读。

这会导致用户看到误导性的"表不存在"错误，难以区分到底是表真的不存在还是元数据文件丢失。更严重的是，在某些 REST Catalog 实现中，`NotFoundException`（表示底层文件/资源未找到的通用异常）没有对应的错误处理分支，可能导致异常被错误地包装或丢失上下文。

本提交的核心目的是：在 REST Catalog 的错误处理体系中为 `NotFoundException` 增加专门的处理分支，使其在 HTTP 404 响应中被正确识别和抛出，从而让调用方能准确区分"表不存在"（`NoSuchTableException`）和"元数据文件丢失"（`NotFoundException`），并提供更清晰的错误信息用于排障。

## 如何达成设计目的

整体思路是在 REST 错误处理链路的两端补齐 `NotFoundException` 的支持：服务端适配器（`RESTCatalogAdapter`）将 `NotFoundException` 映射为 HTTP 404 状态码；客户端错误处理器（`ErrorHandlers`）在收到 404 时根据错误类型字段（`error.type()`）区分 `NotFoundException` 并抛出对应异常。同时在 `CatalogTests` 基类中新增通用的 `testLoadTableWithMissingMetadataFile` 测试，并在 `TestInMemoryCatalog` 和 `TestRESTCatalog` 中实现/覆盖该测试，验证元数据文件缺失时抛出 `NotFoundException` 而非 `NoSuchTableException`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ErrorHandlers.java` (+3/-0 lines)

**修改目的**：在 REST 客户端的 404 错误处理中新增 `NotFoundException` 分支。

**工作逻辑**：
在 `defaultErrorHandler()` 的 `case 404` 分支中，原有逻辑为：若 `error.type()` 等于 `NoSuchNamespaceException` 的类名则抛 `NoSuchNamespaceException`，否则（else）抛 `NoSuchTableException`。新增一个 `else if` 分支：若 `error.type()` 等于 `NotFoundException` 的类名，则抛出 `NotFoundException("%s", error.message())`。这保证了当服务端报告的错误类型是 `NotFoundException` 时，客户端能准确还原该异常类型，而不是被默认归为"表不存在"。分支顺序为：先判 namespace，再判 NotFound，最后兜底为 table 不存在。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java` (+38/-0 lines)

**修改目的**：在 catalog 测试基类中新增 `testLoadTableWithMissingMetadataFile` 通用测试方法。

**工作逻辑**：
新增导入 `java.nio.file` 相关类（`Files`、`Path`、`Paths`、`StandardCopyOption`、`URI`）和 `@TempDir`。测试方法逻辑：创建表并确认表存在；加载表获取当前 metadata 文件位置（通过 `((HasTableOperations) table).operations().current().metadataFileLocation()`）；将 metadata 文件移动（重命名）到临时目录使其"丢失"；此时调用 `catalog.loadTable(TBL)` 应抛出 `NotFoundException`，且消息包含 `"Failed to open input stream for file: "` + 原文件位置；最后在 `finally` 块中将文件移回原位恢复状态。该测试用文件移动模拟元数据文件丢失场景，是所有 catalog 实现都应通过的通用契约测试。

### `core/src/test/java/org/apache/iceberg/inmemory/TestInMemoryCatalog.java` (+30/-0 lines)

**修改目的**：为 `InMemoryCatalog` 实现 `testLoadTableWithMissingMetadataFile` 测试覆盖。

**工作逻辑**：
新增必要的导入（`assertThat`、`assertThatThrownBy`、`HasTableOperations`、`Table`、`NotFoundException`、`@Test`、`@TempDir` 等）。覆盖基类测试方法：创建表后，直接通过 `table.io().deleteFile(metadataFileLocation)` 删除内存中的元数据文件，然后断言 `catalog.loadTable(TBL)` 抛出 `NotFoundException`，消息为 `"No in-memory file found for location: " + metadataFileLocation`。最后通过 `table.io().newOutputFile(metadataFileLocation).create()` 重新创建空文件以恢复状态。与基类用文件移动不同，这里利用 InMemoryCatalog 的 `deleteFile` 直接删除，更贴合内存 catalog 的特性。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+2/-0 lines)

**修改目的**：在 REST 服务端适配器的异常到 HTTP 状态码映射中新增 `NotFoundException`。

**工作逻辑**：
在异常类到状态码的映射 Map 中新增 `.put(NotFoundException.class, 404)`。这样当服务端代码抛出 `NotFoundException` 时，`RESTCatalogAdapter` 会将其序列化为 HTTP 404 响应，且错误响应的 `type` 字段为 `NotFoundException` 的简单类名，客户端 `ErrorHandlers` 据此还原为 `NotFoundException`。此前 `NotFoundException` 没有在这个映射中，可能被当作未知异常处理。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+22/-0 lines)

**修改目的**：为 REST Catalog 覆盖 `testLoadTableWithMissingMetadataFile` 测试。

**工作逻辑**：
新增导入 `HasTableOperations`。覆盖基类测试方法：创建表后，通过 `table.io().deleteFile(metadataFileLocation)` 删除元数据文件，断言 `restCatalog.loadTable(TBL)` 抛出 `NotFoundException`，消息包含 `"No in-memory file found for location: "`（因为 RESTCatalogAdapter 底层使用 InMemoryCatalog 测试）。与 `TestInMemoryCatalog` 类似但走 REST 协议链路，验证了错误经 REST 序列化/反序列化后仍保持正确类型。

## 总结

本提交完善了 Iceberg REST Catalog 对元数据文件丢失场景的异常处理，通过在 `ErrorHandlers` 和 `RESTCatalogAdapter` 两端补齐 `NotFoundException` 的映射，使"表存在但元数据文件不可读"这一场景能抛出准确的 `NotFoundException` 而非误导性的 `NoSuchTableException`。配套的通用测试和 InMemory/REST 两个实现的具体测试验证了该行为契约。这提升了错误诊断的准确性和系统的可观测性，对生产环境排障有实际价值。
