# 提交 1003：Core: Adds Basic Classes for Iceberg Table Version 3 (#10760)

## 提交信息

- **序号**：1003 / 4088
- **哈希**：eb9d3951eeefc51824b87d36ca3824f7a968e81e
- **短哈希**：eb9d3951e
- **日期**：2024-08-01 10:23:49 -0500
- **作者**：Russell Spitzer
- **提交说明**：Core: Adds Basic Classes for Iceberg Table Version 3 (#10760)
- **PR/Issue**：#10760

## 总体目的

本提交是 Iceberg 表格式版本 3（format-version=3）落地工作的奠基性第一步。在 Iceberg 现有规范中，v1 主要面向基础的快照与分区模型，v2 引入了序列号（sequence number）、删除文件（delete file）等能力。随着社区对 v3 规范的讨论推进（例如新的字段类型、行级变更等扩展），需要在 Java 参考实现中先铺设好支撑 v3 表格式读写的基础类骨架，使得后续真正引入 v3 特性的 PR（例如新字段、新元数据）可以在这一骨架上叠加，而无需每次都从零开始搭建版本化的元数据/manifest 读写路径。

具体来说，本提交新增了 `V3Metadata` 类，提供 v3 表 manifest 列表与 manifest entry 的 Avro schema、以及对应的 Indexed 包装器（IndexedManifestFile、IndexedManifestEntry、IndexedDataFile），并在 `ManifestFiles`、`ManifestLists`、`ManifestWriter`、`ManifestListWriter` 中加入 v3 对应的工厂分支。同时把 `TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION` 从 2 提升到 3，使得 Iceberg 内核正式承认 v3 是一个"合法但行为暂时与 v2 等价"的格式版本。

这一步的策略是"复制 v2 行为"——v3 的 schema 暂时与 v2 保持一致，主要是把版本化基础设施打通，让测试可以在 formatVersion=3 下跑起来，为后续真正引入 v3 特有 schema 字段铺路。

## 如何达成设计目的

整体设计思路是"以 V2Metadata 为蓝本，复制一份为 V3Metadata，让 v3 走独立但等价的代码路径"，这样未来 v3 与 v2 出现差异时只改 V3Metadata 即可，不会影响 v2 的稳定性。具体步骤：

1. 新建 `V3Metadata.java`，内部包含 `MANIFEST_LIST_SCHEMA`、`entrySchema/partitionType/fileType` 等静态 schema 定义，以及 `IndexedManifestFile`/`IndexedManifestEntry`/`IndexedDataFile` 三个 Indexed 包装器类。这些类目前与 V2Metadata 中的对应物几乎完全一致，目的是在 Avro 写出 manifest 时按 v3 的 schema 序列化字段。
2. 在 `ManifestWriter` 中新增 `V3Writer`（用于数据文件）和 `V3DeleteWriter`（用于删除文件），通过 `V3Metadata.IndexedManifestEntry` 包装 entry 后写出，并在 Avro 元数据中标记 `format-version=3`。
3. 在 `ManifestListWriter` 中新增 `V3Writer`，使用 `V3Metadata.MANIFEST_LIST_SCHEMA` 与 `IndexedManifestFile` 写出 manifest list，元数据中标记 `format-version=3`。
4. 在 `ManifestFiles.createWriter`/`createDeleteWriter` 与 `ManifestLists.write` 的 switch 中加入 `case 3` 分支，路由到 V3 writer。
5. 将 `TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION` 从 2 提升到 3，使得 `upgradeToFormatVersion(3)` 成为合法操作；同时把 `BaseUpdatePartitionSpec` 中"formatVersion == 2"的判断改为 `>= 2`，使 v3 也能复用 v2 引入的 partition field 复用逻辑。
6. 大量测试文件将 `parameters()` 中的 `Arrays.asList(1, 2)` 扩展为 `Arrays.asList(1, 2, 3)`，让现有测试在 v3 下也跑一遍；并把若干 `formatVersion == 2` 的断言分支改为 `>= 2`，把"仅 v2 支持 position deletes"的 `assumeThat(...).isEqualTo(2)` 放宽为 `isNotEqualTo(1)`，因为 v3 也支持。
7. 资源文件 `TableMetadataUnsupportedVersion.json` 中把"不支持的版本"由 3 改为 4，`TestFormatVersions` 中期望异常信息由 "v3 (supported: v2)" 改为 "v4 (supported: v3)"，反映 v3 现已被支持。

## 修改详情

### `core/src/main/java/org/apache/iceberg/V3Metadata.java`（新增）

**修改目的**：提供 v3 表格式 manifest list 与 manifest entry 的 Avro schema 定义及 IndexedRecord 包装器，作为 v3 元数据读写的核心基础类。

**工作逻辑**：
- `MANIFEST_LIST_SCHEMA`：定义 manifest list 文件中每条 manifest_file 记录的字段（path、length、spec_id、manifest_content、sequence_number、min_sequence_number、snapshot_id、各 added/existing/deleted 文件与行数计数、partition_summaries、key_metadata），所有原本可选的字段在 v3 中均改为 required。
- `entrySchema(partitionType)` / `wrapFileSchema` / `fileType`：定义 manifest entry 与 data_file 的 schema，包含 status、snapshot_id、sequence_number、file_sequence_number 以及嵌套的 data_file 结构（content、file_path、file_format、partition、record_count、file_size、各种 metrics、key_metadata、split_offsets、equality_ids、sort_order_id）。
- `IndexedManifestFile`：实现 `ManifestFile` 与 `IndexedRecord`，作为 wrapper 在写出 manifest list 时按 v3 schema 序列化。`get(int pos)` 按字段顺序返回包装的 ManifestFile 各属性，并处理 sequence number 未分配时的继承逻辑（用 commitSnapshotId 校验后用当前提交的 sequenceNumber 替换）。
- `IndexedManifestEntry<F>`：包装 `ManifestEntry<F>`，在 `get(int i)` 中处理 data_sequence_number 为 null 时的继承（仅 ADDED 状态允许，且 snapshotId 须匹配当前提交）。
- `IndexedDataFile<F>`：包装 `ContentFile<F>`，按 v3 data_file schema 顺序返回各字段，包含一个 `IndexedStructLike` 分区包装器。

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java`

**修改目的**：新增 v3 manifest writer，使数据文件与删除文件能以 v3 schema 写入 manifest 文件。

**工作逻辑**：
- `V3Writer extends ManifestWriter<DataFile>`：构造时创建 `V3Metadata.IndexedManifestEntry<DataFile>`，`prepare` 时用它包装 entry，`newAppender` 使用 `V3Metadata.entrySchema` 写出，元数据中标记 `format-version=3`、`content=data`。
- `V3DeleteWriter extends ManifestWriter<DeleteFile>`：与 V3Writer 结构相同，但 `content()` 返回 `ManifestContent.DELETES`，元数据 `content=deletes`。

### `core/src/main/java/org/apache/iceberg/ManifestListWriter.java`

**修改目的**：新增 v3 manifest list writer，使 manifest 列表文件能以 v3 schema 写入。

**工作逻辑**：`V3Writer extends ManifestListWriter`，构造时创建 `V3Metadata.IndexedManifestFile`，`prepare` 时包装 manifest，`newAppender` 使用 `V3Metadata.MANIFEST_LIST_SCHEMA`，Avro 元数据中除 snapshot-id/parent-snapshot-id/sequence-number 外加上 `format-version=3`。

### `core/src/main/java/org/apache/iceberg/ManifestFiles.java`

**修改目的**：在 manifest writer 工厂方法中为 formatVersion=3 路由到 V3Writer/V3DeleteWriter。

**工作逻辑**：在 `createWriter` 与 `createDeleteWriter` 的 switch 中分别新增 `case 3: return new ManifestWriter.V3Writer(...)` 与 `case 3: return new ManifestWriter.V3DeleteWriter(...)`。

### `core/src/main/java/org/apache/iceberg/ManifestLists.java`

**修改目的**：在 manifest list writer 工厂中为 formatVersion=3 路由到 V3Writer。

**工作逻辑**：在 `write` 方法的 switch 中新增 `case 3: return new ManifestListWriter.V3Writer(...)`。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：将内核支持的最大表格式版本从 2 提升到 3，使 `upgradeToFormatVersion(3)` 合法。

**工作逻辑**：将 `SUPPORTED_TABLE_FORMAT_VERSION` 常量由 2 改为 3。`DEFAULT_TABLE_FORMAT_VERSION` 仍保持为 2（新建表默认仍是 v2）。

### `core/src/main/java/org/apache/iceberg/BaseUpdatePartitionSpec.java`

**修改目的**：让 v3 表也能复用 v2 引入的 partition field 复用逻辑（避免在 spec 更新时为相同 source+transform 创建重复的 partition field）。

**工作逻辑**：将 `recycleOrCreatePartitionField` 中的判断 `formatVersion == 2 && base != null` 改为 `formatVersion >= 2 && base != null`，使 v3 也走该分支。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java`

**修改目的**：让元数据表扫描测试适配 v3，将原本"v2 才有"的删除文件相关测试假设放宽到"v1 不支持"。

**工作逻辑**：
- 大量 `if (formatVersion == 2)` 改为 `if (formatVersion >= 2)`，因为 v3 行为与 v2 一致。
- 多处 `assumeThat(formatVersion).isEqualTo(2)`（针对 position deletes 测试）改为 `isNotEqualTo(1)`，因为 v3 同样支持 position deletes。注释信息也由 "Position deletes supported only for v2 tables" 改为 "Position deletes are not supported by V1 Tables"。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScansWithPartitionEvolution.java`

**修改目的**：将 position deletes 测试的版本假设从"仅 v2"放宽到"非 v1"。

**工作逻辑**：`assumeThat(formatVersion).isEqualTo(2)` 改为 `isNotEqualTo(1)`，注释同步修改。

### `core/src/test/java/org/apache/iceberg/TestFormatVersions.java`

**修改目的**：更新"不支持版本"的期望消息以反映 v3 已被支持。

**工作逻辑**：将期望异常消息从 `"Cannot upgrade table to unsupported format version: v3 (supported: v2)"` 改为 `"Cannot upgrade table to unsupported format version: v4 (supported: v3)"`（测试尝试升级到 `SUPPORTED_TABLE_FORMAT_VERSION + 1`，即现在的 v4）。

### `core/src/test/resources/TableMetadataUnsupportedVersion.json`

**修改目的**：将"不支持的版本"测试资源从 v3 改为 v4。

**工作逻辑**：`"format-version": 3` 改为 `"format-version": 4`，并补上文件末尾换行。

### 大量 `core/src/test/java/.../Test*.java` 测试基类与子类（约 50 个文件）

**修改目的**：让现有测试套件在 formatVersion=3 下也执行，并适配 v3 与 v2 行为等价的断言。

**工作逻辑**：分两类小改动：
1. 多个 `TestBase` 子类（如 `TestFastAppend`、`TestManifestWriter`、`TestSnapshot`、`TestTransaction` 等）的 `parameters()` 由 `Arrays.asList(1, 2)` 改为 `Arrays.asList(1, 2, 3)`，使参数化测试自动多跑一份 v3 用例。
2. 部分 `TestBase` 基类（如 `DeleteFileIndexTestBase`、`MetadataTableScanTestBase`、`ScanPlanningAndReportingTestBase` 等）以及多个子类中的 `formatVersion == 2` 断言改为 `formatVersion >= 2`，确保 v3 走与 v2 相同的期望路径。

### `core/src/test/java/org/apache/iceberg/actions/TestSizeBasedRewriter.java`、`core/src/test/java/org/apache/iceberg/io/TestOutputFileFactory.java`、`core/src/test/java/org/apache/iceberg/mapping/TestMappingUpdates.java`

**修改目的**：同上，让这些测试基类/子类在 v3 下运行并使用 `>= 2` 判断。

**工作逻辑**：`parameters()` 加入 3，或 `== 2` 改为 `>= 2`。

## 小结

- **成效**：为 Iceberg 表格式 v3 在 Java 内核中铺设了基础读写骨架。新增 `V3Metadata` 类与 V3 系列的 manifest/manifest list writer，将 `SUPPORTED_TABLE_FORMAT_VERSION` 提升到 3，使 v3 表可以被创建、写入和读取（行为暂与 v2 等价），并让约 50 个测试套件自动覆盖 v3。这是后续真正引入 v3 特有字段与语义的前置 PR。
- **影响范围**：核心模块 `core` 下的生产代码（V3Metadata 新增、ManifestWriter/ManifestListWriter/ManifestFiles/ManifestLists/TableMetadata/BaseUpdatePartitionSpec 修改）以及大量测试文件，共 55 个文件、+762/-75 行。
- **回迁到 1.4.x 的注意事项**：**不建议回迁**。本提交是 v3 格式的奠基性改动，引入了新的表格式版本支持，属于规范层面的能力扩展，而非 bug 修复。1.4.x 作为维护分支应保持 v1/v2 的稳定行为，回迁会改变 `SUPPORTED_TABLE_FORMAT_VERSION`、引入 v3 writer 代码路径与大量测试参数变化，风险高且无必要。如果 1.4.x 仅需 bug 修复，应保持原 v2 上限。若确需在 1.4.x 支持 v3，应整体评估 v3 规范成熟度与兼容性，而非单独 cherry-pick 此提交。
