# 提交 1335：API, Core: Add content offset and size to DeleteFile (#11446)

## 提交信息

- **序号**：1335 / 4088
- **哈希**：ec269ee3ec0de4184eb536a6ef4f3523dc91332a
- **短哈希**：ec269ee3e
- **日期**：2024-11-04（Mon Nov 4 19:35:08 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：API, Core: Add content offset and size to DeleteFile (#11446)
- **PR/Issue**：#11446

## 总体目的

本提交为 Iceberg 引入**删除向量（Deletion Vectors, DV）**支撑能力迈出关键一步。DV 是一种紧凑的位置删除表示，存储在 Puffin 文件中的二进制 blob（通常是 Roaring bitmap），用于高效表达"被删除的行号集合"。相比传统位置删除文件（每行一条记录），DV 在文件内仅占一个连续 blob，**通过偏移和长度即可在 Puffin 文件内定位该 blob**，无需读取整个文件。

为支持这种存储模型，需要让 `DeleteFile` 元数据能够描述"对应的 DV blob 在 Puffin 文件内的偏移与长度"。本提交新增 `contentOffset()` 和 `contentSizeInBytes()` 两个属性，贯穿 API、Core、Flink 多模块，让 manifest 文件、JSON 解析、Spark 行包装、扫描统计等所有路径都能正确携带与读取这两个字段。

设计要点：
1. **字段语义**：`contentOffset` 与 `contentSizeInBytes` 仅对 DV（Puffin 格式）有意义；对 equality/position delete 文件始终为 `null`。
2. **统计一致性**：所有"计算删除文件大小"的地方（如 `ScanTaskUtil.contentSizeInBytes`、`SnapshotSummary`、`ScanMetricsUtil` 等）改为按"是否 DV"选择 `contentSizeInBytes` 或 `fileSizeInBytes`，避免用整个 Puffin 文件大小去代表 DV blob 大小造成统计偏高。
3. **去重键一致性**：`DeleteFileSet` 的等价/哈希逻辑纳入 `location + contentOffset + contentSize`，使同一 Puffin 文件内不同 DV 也能区分。

## 如何达成设计目的

分多个层次落地：

- **API 层**：在 `DataFile` 接口定义两个新的 `Types.NestedField`（ID 144、145），分别表示 `content_offset` 和 `content_size_in_bytes`；在 `DeleteFile` 接口新增 `contentOffset()`、`contentSizeInBytes()` 两个 `default` 方法（默认返回 `null`，向后兼容）。新增工具类 `ScanTaskUtil`，对外提供按内容类型计算"内容大小"的统一方法。
- **Core 层**：`BaseFile` 增加 `contentOffset`/`contentSizeInBytes` 字段及对应访问器；`FileMetadata.Builder` 增加 `withContentOffset`/`withContentSizeInBytes`，并对格式做强制校验（Puffin 必填、非 Puffin 必为 null）；`ContentFileParser` 增加 JSON 序列化/反序列化支持；`V3Metadata` 索引访问新增 case 17、18；`SnapshotProducer` 的 `DeferredDeleteFile` 代理新方法；`SnapshotSummary`、`ScanSummary`、`ScanMetricsUtil`、`TableScanUtil`、`BaseFileScanTask`、Flink 的 `CommitSummary`、`IcebergStreamWriterMetrics` 等统计/指标点统一改用 `ScanTaskUtil.contentSizeInBytes`。
- **Spark 层**：仅本提交未直接改 Spark，但其依赖的 API 已就绪；后续 1337、1339 才补上 `SparkContentFile` 包装与 manifest rewrite 测试。
- **测试层**：`FileGenerationUtil` 新增 `generateDV` 工具方法；`TestScanTaskUtil`、`TestTableScanUtil`、`TestContentFileParser`、`TestManifestReader`、`TestManifestEncryption`、`TestManifestWriterVersions`、`TestBase` 均补齐 DV 用例或更新构造参数。

## 修改详情

### `api/src/main/java/org/apache/iceberg/DataFile.java`

在 manifest entry 的 schema 中新增两个 optional 字段：

```java
Types.NestedField CONTENT_OFFSET =
    optional(144, "content_offset", LongType.get(), "The offset in the file where the content starts");
Types.NestedField CONTENT_SIZE =
    optional(145, "content_size_in_bytes", LongType.get(), "The length of referenced content stored in the file");
```

并把 `NEXT ID TO ASSIGN` 从 144 改为 146。`getType(StructType)` 把这两个字段加入 manifest 文件 schema。这两个字段同时挂在 `DataFile` 上是因为 manifest entry schema 是 DataFile/DeleteFile 共享的，DV 作为 POSITION_DELETES 的一种存储形式走 DeleteFile 路径。

### `api/src/main/java/org/apache/iceberg/DeleteFile.java`

新增两个 default 方法，文档明确说明 DV（Puffin blob）才需要、equality/position delete 文件恒为 null：

```java
default Long contentOffset() { return null; }
default Long contentSizeInBytes() { return null; }
```

### `api/src/main/java/org/apache/iceberg/FileFormat.java`

在枚举中新增 `PUFFIN("puffin", false)`。第二个参数是 `splitable`，Puffin 不支持 split，所以为 `false`。

### `api/src/main/java/org/apache/iceberg/util/ScanTaskUtil.java`（新文件）

集中处理"内容大小"语义。对于 DATA 文件直接返回 `fileSizeInBytes`；对于 DELETE 文件，若是 DV（Puffin 格式）则返回 `contentSizeInBytes`，否则返回 `fileSizeInBytes`。提供单文件与可迭代重载。`isDV` 通过 `format == PUFFIN` 判定。

```java
public static long contentSizeInBytes(ContentFile<?> file) {
    if (file.content() == FileContent.DATA) {
        return file.fileSizeInBytes();
    } else {
        DeleteFile deleteFile = (DeleteFile) file;
        return isDV(deleteFile) ? deleteFile.contentSizeInBytes() : deleteFile.fileSizeInBytes();
    }
}
```

### `api/src/main/java/org/apache/iceberg/util/DeleteFileSet.java`

`DeleteFileWrapper` 的 `equals`/`hashCode` 改为同时纳入 `location`、`contentOffset`、`contentSizeInBytes`，并删除注释中"this needs to be updated once deletion vector support is added"的 TODO。这样同一 Puffin 文件内不同偏移的 DV 不会被判为相等。

### 各 ScanTask 接口（`AddedRowsScanTask`、`DeletedDataFileScanTask`、`DeletedRowsScanTask`、`FileScanTask`）

`sizeBytes()` 计算改为 `length() + ScanTaskUtil.contentSizeInBytes(deletes())` 形式，确保 DV 用 blob 大小统计、传统 delete 用文件大小统计。

### `core/src/main/java/org/apache/iceberg/BaseFile.java`

新增字段 `contentOffset`、`contentSizeInBytes`；在 `BASE_TYPE` 字段列表里追加这两个字段（位置 18、19），原有的"行序号"挪到 20；构造方法、拷贝构造、`get(pos)`/`set(pos, value)` 的 case 表均新增对应分支；新增 `contentOffset()`、`contentSizeInBytes()` 访问器；`toString` 中也加入这两个字段。

### `core/src/main/java/org/apache/iceberg/FileMetadata.java`

Builder 增加 `withContentOffset(long)`、`withContentSizeInBytes(long)`；`build()` 增加校验：Puffin 格式必填这两个字段、非 Puffin 格式禁止填，并对 `GenericDeleteFile` 构造多透传两个参数。

```java
if (format == FileFormat.PUFFIN) {
    Preconditions.checkArgument(contentOffset != null, "Content offset is required for DV");
    Preconditions.checkArgument(contentSizeInBytes != null, "Content size is required for DV");
} else {
    Preconditions.checkArgument(contentOffset == null, "Content offset can only be set for DV");
    Preconditions.checkArgument(contentSizeInBytes == null, "Content size can only be set for DV");
}
```

### `core/src/main/java/org/apache/iceberg/V3Metadata.java`

`V3_INDEXED_COLUMNS` 追加 `CONTENT_OFFSET`、`CONTENT_SIZE`；`IndexedManifestEntry.get(pos)` 新增 case 17/18，仅当文件内容为 `POSITION_DELETES` 时返回对应值，否则返回 null（DV 走 POSITION_DELETES 内容类型 + Puffin 格式）。

### `core/src/main/java/org/apache/iceberg/ContentFileParser.java`

新增 JSON 字段 `content-offset`、`content-size-in-bytes`；写入时仅当非 null 才输出；读取时解析为 `Long`，并在构造 `GenericDeleteFile` 时传入。

### `core/src/main/java/org/apache/iceberg/GenericDataFile.java`、`GenericDeleteFile.java`

构造方法签名新增 `contentOffset`、`contentSizeInBytes` 两个参数；`GenericDataFile` 直接传 `null`（数据文件不携带这两项）。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

内部 `DeferredDeleteFile`（用于延迟加载的 DeleteFile 代理）新增 `contentOffset()`、`contentSizeInBytes()` 的代理实现。

### `core/src/main/java/org/apache/iceberg/SnapshotSummary.java`、`ScanSummary.java`、`metrics/ScanMetricsUtil.java`、`util/TableScanUtil.java`、`BaseFileScanTask.java`、`BaseScan.java`

将原先 `file.fileSizeInBytes()`/`deleteFile.fileSizeInBytes()` 等调用替换为 `ScanTaskUtil.contentSizeInBytes(file)`，让 DV 的统计用 blob 大小、传统删除用文件大小。`BaseScan.SCAN_COLUMNS`/`DELETE_SCAN_WITH_STATS_COLUMNS` 中追加 `content_offset`、`content_size_in_bytes` 列。

### Flink v1.19/v1.20/v1.21 的 `CommitSummary.java`、`IcebergStreamWriterMetrics.java`

`deleteFilesByteCount` 累加、`deleteFilesSizeHistogram` 更新均改用 `ScanTaskUtil.contentSizeInBytes(deleteFile)`，三个 Flink 版本同步改动，逻辑完全一致。

### 测试

- `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java`：新增 `generateDV(Table, DataFile)`，调用 `withReferencedDataFile`、`withContentOffset`、`withContentSizeInBytes`、path 以 `.puffin` 结尾；新增 `generateContentOffset()`、`generateContentLength()` 两个随机数生成。
- `core/src/test/java/org/apache/iceberg/TestBase.java`：新增 `newDV(DataFile)` 工具方法，便于子类测试生成 DV。
- `api/src/test/java/org/apache/iceberg/util/TestScanTaskUtil.java`（新文件）：用 mock DV 验证 `ScanTaskUtil.contentSizeInBytes` 在空列表、单 DV、多 DV 场景下的累加正确性。
- `core/src/test/java/org/apache/iceberg/util/TestTableScanUtil.java`：新增 `testFileScanTaskSizeEstimation`，验证 `MockFileScanTask` 在 dataFile=100 + DV=20 时 `sizeBytes()` 返回 120（用 `contentSizeInBytes` 而非 `fileSizeInBytes`）。
- `core/src/test/java/org/apache/iceberg/TestContentFileParser.java`：增加 DV 用例 `dv(spec)` 与对应 JSON `dvJson()`，验证序列化包含 `content-offset`、`content-size-in-bytes`；其他静态构造的 `DeleteFile` 多传两个 `null` 适配新签名。
- `core/src/test/java/org/apache/iceberg/TestManifestReader.java`：原访问 `((BaseFile) file).get(18)` 改为 `get(20)`（因新增两个字段后行序号位置由 18 挪到 20）；新增 `testDVs` 用例验证读出 DV 的 `referencedDataFile`、`contentOffset`、`contentSizeInBytes` 与写入一致。
- `core/src/test/java/org/apache/iceberg/TestManifestEncryption.java`、`TestManifestWriterVersions.java`：静态 `DeleteFile` 构造多传两个 `null` 适配新签名。

## 小结

- **成效**：为 Iceberg 引入 DV（Puffin blob 形式的位置删除）所需的元数据字段（`content_offset`、`content_size_in_bytes`）贯通 API/Core/Flink；统计、去重、序列化、Spark 包装等各路径均能正确处理 DV，使后续 DV 读写落地成为可能。
- **影响范围**：跨 API、Core、Flink（v1.19/v1.20/v1.21）三个模块共 36 个文件、约 +398/-40 行，是一次较重的"加字段"型重构。新增字段为 optional，向后兼容旧 manifest；新增方法为 default，向后兼容外部实现。
- **回迁到 1.4.x 的注意事项**：**不建议**作为独立提交回迁到 1.4.x。理由：
  1. 本提交是 DV 整体特性的"地基"，后续 1336（revert parquet，与 DV 间接相关）、1337、1338（DV 序列化）、1339 均依赖此提交的字段与方法；单独回迁本提交而缺失后续提交，会留下未使用的代码与未对齐的测试，价值有限。
  2. 新增 manifest schema 字段会改变 V3 manifest 的 entry 类型；1.4.x 是稳定维护分支，对 manifest 格式变更应保持高度谨慎。若 1.4.x 决定完整引入 DV 特性，应作为一组连续特性回迁，并配合完整的 manifest 兼容性测试。
  3. 改动了 `BaseFile` 的字段 ordinal（行序号由 18 挪到 20），1.4.x 上如有依赖该 ordinal 的下游测试或自定义代码会受影响，回迁需仔细排查。
  4. 如果 1.4.x 不打算引入 DV，则本提交与 DV 直接相关的字段（content_offset/content_size）即便回迁也无实际写入路径，属于"死字段"，应跳过。
