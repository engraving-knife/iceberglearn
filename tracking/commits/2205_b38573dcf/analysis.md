# 提交 2205：Core: Add basic classes for writing table format-version 4 (#13123)

## 提交信息

- **序号**：2205 / 4088
- **哈希**：b38573dcfb4a5467a70c28573a2d39874718a2a1
- **短哈希**：b38573dcf
- **日期**：2025-06-04 10:49:18 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add basic classes for writing table format-version 4 (#13123)
- **PR/Issue**：#13123

## 总体目的

这个提交为 Iceberg 表格式版本 4（format-version 4）添加基础的写入类支持。Iceberg 表格式经历了 v1、v2、v3 的演进，每个版本引入了新的元数据特性。版本 4 在版本 3 的基础上继续发展（涉及行级谱系 row lineage 等特性）。为了支持创建和写入 v4 表，需要在核心模块中添加对应的 manifest writer、manifest list writer 以及 v4 元数据 schema 定义。本提交新增了 `V4Metadata` 类，定义 v4 的 manifest list schema 和 manifest entry schema，并新增 `ManifestWriter.V4Writer`、`ManifestWriter.V4DeleteWriter`、`ManifestListWriter.V4Writer` 等写入器实现，同时更新 `ManifestFiles`、`ManifestLists` 工厂方法以支持 formatVersion=4 的分支，并在 `MergingSnapshotProducer` 中集成 v4 相关逻辑。此外，更新了大量测试以覆盖 v4 场景。

## 如何达成设计目的

- 新增 `V4Metadata` 类：定义 v4 的 manifest list schema（`MANIFEST_LIST_SCHEMA`）、manifest entry schema（`entrySchema`）、`ManifestFileWrapper`（用于以 v4 schema 写出 ManifestFile）、`ManifestEntryWrapper`（用于以 v4 schema 写出 ManifestEntry）。
- 新增 `ManifestListWriter.V4Writer`：以 v4 schema 写出 manifest list，处理 first-row-id 的分配。
- 新增 `ManifestWriter.V4Writer` 和 `V4DeleteWriter`：分别以 v4 schema 写出数据 manifest 和删除 manifest。
- 更新 `ManifestFiles.write`/`writeDeleteManifest` 工厂方法：在 formatVersion switch 中新增 case 4 分支。
- 更新 `ManifestLists.write` 工厂方法：新增 case 4 分支。
- 更新 `MergingSnapshotProducer`：集成 v4 相关逻辑（如 first-row-id 处理）。
- 更新 `TableMetadata`：调整版本相关常量。
- 更新大量测试类（TestBase、TestRowDelta、TestMetrics 等）：支持 v4 格式版本的参数化测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/V4Metadata.java` (新增, +526/-0 lines)

**修改目的**：定义 v4 格式的元数据 schema 和 wrapper 类。

**工作逻辑**：
- 定义 `MANIFEST_LIST_SCHEMA`：v4 manifest list 的 Avro schema，包含 path、length、spec_id、manifest_content、sequence_number、min_sequence_number、snapshot_id、各文件计数、行计数、partition_summaries、key_metadata、first_row_id 等字段。
- `ManifestFileWrapper`：实现 ManifestFile 和 StructLike 接口，用于将任意 ManifestFile 实现按 v4 schema 写出。通过 `wrap(ManifestFile, Long firstRowId)` 绑定，`get(pos)` 按 schema 列序返回对应字段值，处理 sequence number 的赋值校验。
- `ManifestEntryWrapper<F>`：类似地，用于将 ManifestEntry 按 v4 schema 写出。
- `entrySchema(Types.StructType)`：根据分区类型构造 v4 manifest entry 的 schema。

### `core/src/main/java/org/apache/iceberg/ManifestListWriter.java` (修改, +58/-0 lines)

**修改目的**：新增 v4 manifest list writer。

**工作逻辑**：新增 `V4Writer` 内部类，继承 `ManifestListWriter`。构造时设置 format-version=4 及 first-row-id 元数据。`prepare(ManifestFile)` 方法：对数据 manifest 且无 firstRowId 的，分配 nextRowId 并推进（留出 existing + added 行的空间）；其他情况直接包装。`newAppender` 使用 `V4Metadata.MANIFEST_LIST_SCHEMA`。`nextRowId()` 返回当前 nextRowId。

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java` (修改, +75/-0 lines)

**修改目的**：新增 v4 manifest writer（数据与删除）。

**工作逻辑**：
- `V4Writer`：数据 manifest writer，使用 `V4Metadata.entrySchema` 和 `V4Metadata.ManifestEntryWrapper`，meta 设置 format-version=4、content=data。
- `V4DeleteWriter`：删除 manifest writer，类似但 content=deletes，无 firstRowId。

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java` (修改, +4/-0 lines)

**修改目的**：工厂方法支持 v4。

**工作逻辑**：在 `write` 和 `writeDeleteManifest` 的 formatVersion switch 中新增 `case 4` 分支，分别返回 `V4Writer` 和 `V4DeleteWriter`。

### `core/src/main/java/org/apache/iceberg/ManifestLists.java` (修改, +3/-0 lines)

**修改目的**：工厂方法支持 v4。

**工作逻辑**：在 `write` 的 formatVersion switch 中新增 `case 4` 分支，返回 `ManifestListWriter.V4Writer`。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (修改, +5/-2 lines)

**修改目的**：集成 v4 的 first-row-id 逻辑。

**工作逻辑**：在提交相关逻辑中处理 v4 的 first-row-id 分配。

### 其他文件（修改）

- `TableMetadata.java`（+1/-1）：版本常量调整。
- 多个测试类（TestBase、TestRowDelta、TestMetrics、TestFormatVersions、TestRowLineageMetadata 等，约 20 个测试文件）：更新以支持 v4 参数化测试，调整断言和测试数据。
- `TableMetadataUnsupportedVersion.json`（+1/-1）：测试资源更新。

## 总结

该提交为 Iceberg 表格式版本 4 添加了基础的写入基础设施，包括 v4 元数据 schema 定义、manifest writer 和 manifest list writer 实现、工厂方法支持以及测试覆盖。这是 v4 格式支持的核心基础工作，使 Iceberg 能够创建和写入 format-version=4 的表，为后续 v4 高级特性（如行级谱系）奠定基础。
