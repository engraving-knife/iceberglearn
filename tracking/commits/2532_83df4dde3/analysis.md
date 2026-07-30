# 提交 2532：Core: Deprecate TableMetadataParser#read with unused file io parameter (#13871)

## 提交信息

- **序号**：2532 / 4088
- **哈希**：83df4dde388087fc5e2a74cf2ee72a5cce8036f7
- **短哈希**：83df4dde3
- **日期**：2025-08-20 12:41:34 +0200
- **作者**：Yujiang Zhong
- **提交说明**：Core: Deprecate TableMetadataParser#read with unused file io parameter (#13871)
- **PR/Issue**：#13871

## 总体目的

`TableMetadataParser` 之前存在两个 `read` 重载：
- `read(FileIO io, String path)`：用 `io` 构造 `InputFile` 后再读。
- `read(FileIO io, InputFile file)`：传入 `io` 但实际未使用，只用到 `file`。

第二个重载的 `FileIO io` 参数完全没用，是历史遗留的死参数。它误导调用者以为 `io` 会参与读取（例如解压、远程访问），实际上读取只依赖 `InputFile` 自身的 `newStream()`。这造成 API 表面积冗余且容易让人误用。

本次提交将 `read(FileIO, InputFile)` 标记为 `@Deprecated`（since 1.10.0，1.11.0 移除），新增一个干净的 `read(InputFile)` 重载，并在内部把 `read(FileIO, String)` 改为通过 `read(InputFile)` 实现，避免重复代码。生产代码与测试中所有调用点都被迁移到新签名，从而完成 deprecation 的第一步迁移工作。

## 如何达成设计目的

- 新增 `read(InputFile file)` 作为唯一真正实现，包含原有读取逻辑（按文件名推断 `Codec`，必要时用 `GZIPInputStream` 包装）。
- 旧 `read(FileIO io, InputFile file)` 保留并标 `@Deprecated`，方法体直接转调 `read(file)`，保持二进制兼容。
- `read(FileIO io, String path)` 内部改为 `read(io.newInputFile(path))`，依旧保留 `FileIO` 参数因为需要用它构造 `InputFile`。
- 修改生产代码 `BaseMetastoreCatalog.registerTable` 中调用点：原来传入 `ops.io()` 与 `metadataFile`，现在直接传 `metadataFile`。
- 修改测试调用点，把 `read(null, file)` 与 `read(testIo, file)` 改为 `read(file)`，并清理随之失效的 `TestTables` import。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetastoreCatalog.java` (+1/-1)

**修改目的**：迁移生产代码到新签名。

**工作逻辑**：`registerTable` 中读取 metadata 文件改为 `TableMetadataParser.read(metadataFile)`，不再传多余的 `ops.io()`。

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java` (+10/-1)

**修改目的**：废弃旧重载，新增干净重载。

**工作逻辑**：
- `read(FileIO, String)` 内部调用 `read(io.newInputFile(path))`。
- `read(FileIO, InputFile)` 标 `@Deprecated`，转调 `read(file)`。
- 新增 `read(InputFile)`，迁移原有读取实现。

### `core/src/test/java/org/apache/iceberg/TestTableMetadataParser.java` (+1/-2)

**修改目的**：测试改用新签名。

**工作逻辑**：原 `TableMetadataParser.read(null, Files.localInput(...))` 改为 `TableMetadataParser.read(Files.localInput(...))`，去掉无意义的 `null` io。

### `core/src/test/java/org/apache/iceberg/hadoop/HadoopTableTestBase.java` (+1/-3)

**修改目的**：测试改用新签名并清理 import。

**工作逻辑**：`readMetadataVersion` 不再构造 `TestTableOperations` 取 io，直接 `TableMetadataParser.read(localInput(version(version)))`；移除 `TestTables` 导入。

## 总结

通过废弃 `TableMetadataParser.read(FileIO, InputFile)` 中未使用的 `FileIO` 参数，引入干净的 `read(InputFile)` 重载，并迁移所有调用点，简化了 API 表面积，避免误导。旧方法保留转调以保证二进制兼容，计划在 1.11.0 移除。
