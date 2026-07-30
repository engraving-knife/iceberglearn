# 提交 2098：AWS, Core, Flink, Parquet: Remove deprecations for 1.10.0 (#12909)

## 提交信息

- **序号**：2098 / 4088
- **哈希**：8cf996188a61288964b0e840a705eeb20214bb33
- **短哈希**：8cf996188
- **日期**：2025-05-07 12:53:39 -0600
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：AWS, Core, Flink, Parquet: Remove deprecations for 1.10.0 (#12909)
- **PR/Issue**：#12909

## 总体目的

Iceberg 的版本策略约定：标注 `@Deprecated` 且注释"will be removed in 1.10.0"的 API，在 1.10.0 开发周期开始时统一删除，以保持代码整洁并强制下游迁移到新 API。本提交执行这一清理，跨 AWS、Core、Flink、Parquet 四个模块，删除在 1.9.x 之前就已弃用的类、方法、字段，并同步更新调用方与测试，最后在 `.palantir/revapi.yml` 中登记这些相对 1.9.0 的"已接受破坏"，使 RevAPI 不再报错。

被移除的弃用项可归为几类：
1. **Row lineage 旧入口**：`MetadataUpdate.EnableRowLineage`、`TableMetadata.rowLineageEnabled()`、`TableMetadata.Builder.enableRowLineage()`、`TableProperties.ROW_LINEAGE`——row lineage 在 v3 规范中以 `next_row_id` 属性表达（见 #12982/#12986），不再用这些旧 API。
2. **旧 `FileRewriter` 体系**：`actions.FileRewriter` 接口、`SizeBasedFileRewriter`/`SizeBasedDataRewriter`/`SizeBasedPositionDeletesRewriter`、`RewriteFileGroup`/`RewritePositionDeletesGroup` 上的旧构造器与别名方法（`fileScans()`、`numFiles()`、`sizeInBytes()`、`tasks()`、`numRewrittenDeleteFiles()`、`rewrittenBytes()`）——已被新的 Planner/Runner API（见 #12980）取代。
3. **`MetadataUpdate.RemoveSnapshot`**（单数）——已被 `RemoveSnapshots`（复数）取代。
4. **`GenericManifestFile` 旧构造器**、`PartitionStats.totalRecordCount()`、`SnapshotProducer.PendingDeleteFile` 内部类。
5. **AWS `RESTSigV4Signer`**——整个类被删除（已弃用的 REST SigV4 签名实现）。
6. **Parquet 读写器旧工厂方法**：`GenericParquetReaders.createStructReader`、`GenericParquetWriter.buildWriter/createStructWriter`、`InternalReader.createStructReader`、`InternalWriter.create/createStructWriter`、`ParquetValueWriters.recordWriter(List)`（无 schema 版本）——统一到带 `Schema`/`Types.StructType` 参数的新方法。

## 如何达成设计目的

1. **删除弃用代码**：直接移除上述类、方法、字段及其 `@Deprecated` 注解与 javadoc。
2. **更新调用方**：`MetadataUpdateParser` 不再处理 `RemoveSnapshot`（单数）分支，统一用 `RemoveSnapshots`；`TableMetadata` 移除 `rowLineageEnabled`/`enableRowLineage` 后，相关内部逻辑调整。
3. **更新测试**：`TestMetadataUpdateParser`、`TestUpdateRequirements`、Flink `TestIcebergCommitter`/`TestIcebergFilesCommitter`（v1.18/1.19/1.20 三份）、`TestCommitTransactionRequestParser` 改为调用新 API；删除 `TestSizeBasedRewriter`。
4. **登记 RevAPI 接受的破坏**：在 `.palantir/revapi.yml` 的 `acceptedBreaks` 下新增 `"1.9.0"` 段，列出所有被移除的类/方法/字段，统一 justification 为 "Removing deprecations for 1.10.0"。

## 修改详情

### `.palantir/revapi.yml` (修改, +97/-0 lines)

**修改目的**：登记相对 1.9.0 基准的已接受破坏，避免 RevAPI 报错。

**工作逻辑**：在 `acceptedBreaks` 下新增 `"1.9.0"` 键，分 `iceberg-core` 与 `iceberg-parquet` 两个模块列出所有 `java.class.removed`/`java.method.removed`/`java.field.removedWithConstant` 条目，对应本次删除的弃用 API。

### `aws/src/main/java/org/apache/iceberg/aws/RESTSigV4Signer.java` (删除, -160 lines)

**修改目的**：移除已弃用的 REST SigV4 签名实现。

**工作逻辑**：整个类删除。该类实现 `HttpRequestInterceptor`，用 `Aws4Signer` 对 REST 请求做 SigV4 签名，已被更现代的认证机制取代。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java` (修改, +0/-39 lines)

**修改目的**：移除 `RemoveSnapshot`（单数）与 `EnableRowLineage` 两个弃用内部类。

**工作逻辑**：删除 `@Deprecated class RemoveSnapshot` 与 `@Deprecated class EnableRowLineage`，保留 `RemoveSnapshots`（复数）。

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java` (修改, +3/-16 lines)

**修改目的**：移除对 `RemoveSnapshot`（单数）的兼容处理。

**工作逻辑**：序列化/反序列化时不再区分单数/复数，统一用 `RemoveSnapshots`；删除 `ImmutableSet`/`Iterables` 导入与单元素分支。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (修改, +1/-31 lines)

**修改目的**：移除 `rowLineageEnabled()` 与 `Builder.enableRowLineage()`。

**工作逻辑**：删除这两个 `@Deprecated` 方法及其关联字段/逻辑，row lineage 改由 `next_row_id` 属性驱动。

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (修改, +0/-7 lines)

**修改目的**：移除 `ROW_LINEAGE = "row-lineage"` 常量。

### `core/src/main/java/org/apache/iceberg/GenericManifestFile.java` (修改, +0/-41 lines)

**修改目的**：移除旧的多参数构造器。

### `core/src/main/java/org/apache/iceberg/PartitionStats.java` (修改, +0/-8 lines)

**修改目的**：移除 `totalRecordCount()` 别名方法。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (修改, +0/-33 lines)

**修改目的**：移除 `PendingDeleteFile` 内部类。

### `core/src/main/java/org/apache/iceberg/actions/FileRewriter.java` (删除, -80 lines)

**修改目的**：移除旧的 `FileRewriter<T extends ContentScanTask<F>, F>` 接口，已被 `FileRewritePlanner` + `FileRewriteRunner` 取代。

### `core/src/main/java/org/apache/iceberg/actions/SizeBasedFileRewriter.java` (删除, -348 lines)
### `core/src/main/java/org/apache/iceberg/actions/SizeBasedDataRewriter.java` (删除, -173 lines)
### `core/src/main/java/org/apache/iceberg/actions/SizeBasedPositionDeletesRewriter.java` (删除, -63 lines)

**修改目的**：移除旧的 size-based rewriter 体系，已被新的 `BinPackRewriteFilePlanner` 等 Planner/Runner 取代。

### `core/src/main/java/org/apache/iceberg/actions/RewriteFileGroup.java` (修改, +0/-32 lines)

**修改目的**：移除旧构造器 `RewriteFileGroup(FileGroupInfo, List<FileScanTask>)` 与别名方法 `fileScans()`、`sizeInBytes()`、`numFiles()`。

### `core/src/main/java/org/apache/iceberg/actions/RewritePositionDeletesGroup.java` (修改, +0/-32 lines)

**修改目的**：移除旧构造器与 `tasks()`、`rewrittenBytes()`、`numRewrittenDeleteFiles()` 别名方法。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetReaders.java` (修改, +0/-13 lines)

**修改目的**：移除旧 `createStructReader(List, List, StructType)` 工厂方法，统一用带 `Schema` 的版本。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetWriter.java` (修改, +0/-21 lines)

**修改目的**：移除 `buildWriter(MessageType)` 与旧 `createStructWriter(List)`，统一用 `create(Schema, MessageType)` / `createWriter(Types.StructType, MessageType)`。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/InternalReader.java` (修改, +0/-14 lines)

**修改目的**：移除旧 `createStructReader(List, List, StructType)`，统一用带 `Types.StructType` 的新方法。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/InternalWriter.java` (修改, +0/-21 lines)

**修改目的**：移除 `create(MessageType)` 与旧 `createStructWriter(List)`，统一用 `create(Schema, MessageType)` / `createWriter(Types.StructType, MessageType)`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueWriters.java` (修改, +0/-12 lines)

**修改目的**：移除无 schema 的 `recordWriter(List)` 重载，统一用 `recordWriter(Types.StructType, List)`。

### 测试文件

- `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`（修改, +6/-17）：把 `RemoveSnapshot` 用例改为 `RemoveSnapshots`，删除单数分支断言。
- `core/src/test/java/org/apache/iceberg/TestUpdateRequirements.java`（修改, +1/-1）：`RemoveSnapshot` → `RemoveSnapshots`。
- `core/src/test/java/org/apache/iceberg/actions/TestSizeBasedRewriter.java`（删除, -95）：旧 rewriter 测试整体删除。
- `flink/v1.18|v1.19|v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergCommitter.java` 与 `TestIcebergFilesCommitter.java`（各修改若干行）：适配被移除的 API（如改用新方法名）。
- `core/src/test/java/org/apache/iceberg/rest/requests/TestCommitTransactionRequestParser.java`（修改, +1/-1）：`RemoveSnapshot` → `RemoveSnapshots`。

## 总结

本次提交是 1.10.0 开发周期的标准弃用清理：删除跨 AWS/Core/Flink/Parquet 四模块、在 1.9.x 之前就已标注"will be removed in 1.10.0"的类、方法、字段，涵盖 row lineage 旧入口、旧 `FileRewriter`/`SizeBased*Rewriter` 体系、`RemoveSnapshot` 单数、Parquet 读写器旧工厂方法、`RESTSigV4Signer` 等。同时更新调用方与测试，并在 `revapi.yml` 登记相对 1.9.0 的已接受破坏。净减约 1361 行代码，是配合新 Planner/Runner API 与 v3 row lineage 规范演进的必要清理。
