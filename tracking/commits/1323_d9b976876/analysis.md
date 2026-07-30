# 提交 1323：API, Core: Add data file reference to DeleteFile (#11443)

## 提交信息

- **序号**：1323 / 4088
- **哈希**：d9b9768766b359adf696f5dc9e321507bd0213d2
- **短哈希**：d9b976876
- **日期**：2024-11-02（Sat Nov 2 17:23:28 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：API, Core: Add data file reference to DeleteFile (#11443)
- **PR/Issue**：#11443

## 总体目的

承接提交 1322 在规格层引入的"删除向量"（DV）能力，本提交在 Java API 与 Core 实现层落地 DV 元数据中最关键的一环：为 `DeleteFile` 接口新增 `referencedDataFile()` 方法，并在 manifest 读写、JSON 序列化、扫描列投影等链路中贯穿这一新字段。

`referenced_data_file`（字段 ID 143）在规格中定义为"所有删除所引用的数据文件全限定 URI"：对 DV 是必需，对 v2/v3 中只针对单个数据文件的 position delete 文件是可选，对 equality delete 文件始终为 null。本提交将该字段加入 `DataFile` 字段常量、`BaseFile` 内部状态、`FileMetadata.Builder`、`GenericDeleteFile`/`GenericDataFile` 构造器、`V2Metadata`/`V3Metadata` 索引访问器、`ContentFileParser` JSON 读写、`SnapshotProducer` 中的 `DeleteFile` 包装器，以及 `BaseScan` 的扫描统计列投影，从而让 Iceberg 内核能完整地"读、写、序列化、投影"这一字段，为后续 DV 的 Puffin 存储与读取扫清结构障碍。

## 如何达成设计目的

1. **API 层声明字段**：在 `DataFile` 接口上定义 `REFERENCED_DATA_FILE` 字段常量（ID 143、optional、String），并将其加入 `getType` 返回的 `StructType` 字段列表，使 manifest schema 包含该字段；同时在 `DeleteFile` 接口新增 `default referencedDataFile()` 返回 null。
2. **Core 层实现字段**：在 `BaseFile` 中新增 `referencedDataFile` 私有字段、构造器参数、拷贝构造、`case 17/18` 的字段访问 ordinal（因为新增了一个字段，原有 `fileOrdinal` 的 ordinal 从 17 顺延为 18）、`referencedDataFile()` getter 以及 `toString` 输出。`GenericDataFile` 在构造时显式传 `null`（数据文件不引用其他数据文件），`GenericDeleteFile` 新增 `referencedDataFile` 构造参数透传给父类。
3. **构建器与元数据索引**：`FileMetadata.Builder` 新增 `withReferencedDataFile` 方法与对应私有字段，`build()` 时把字段写入 `GenericDeleteFile`；`V2Metadata`/`V3Metadata` 的 `IndexedManifestEntry` 在 `case 16` 返回 `referencedDataFile`，但二者策略不同——V2 对任何 `DeleteFile` 都返回，V3 仅对 `FileContent.POSITION_DELETES` 返回（equality delete 在 v3 始终为 null）。
4. **扫描与序列化**：`BaseScan` 的 `DELETE_SCAN_WITH_STATS_COLUMNS` 列表追加 `referenced_data_file`，确保扫描读取 manifest 时该列被读取；`ContentFileParser` 在 JSON 写入时仅对 `DeleteFile` 且值非 null 时写出 `referenced-data-file` 字段，读取时统一 `JsonUtil.getStringOrNull` 解析并传入 `GenericDeleteFile` 构造器；`SnapshotProducer` 中内部 `DeleteFile` 包装器新增 `referencedDataFile()` 委托给被包装的 `deleteFile`；`ContentFileUtil` 在计算删除文件路径时优先使用 `referencedDataFile`，若非空直接返回。
5. **测试**：在 `TestBase` 新增辅助方法 `newDeleteFileWithRef(DataFile)` 构造带引用的 position delete 文件；`TestManifestReader` 新增 `testDeleteFilesWithReferences` 验证写入后读回的 `referencedDataFile` 与原始 `dataFile.location()` 一致；`TestContentFileParser` 新增带 `referenced-data-file` 的 JSON 用例并修正既有用例的构造器参数顺序；`TestManifestEncryption`、`TestManifestWriterVersions` 同步为 `GenericDeleteFile` 构造器补 `null` 参数以适配新签名。

## 修改详情

### `api/src/main/java/org/apache/iceberg/DataFile.java`

**修改目的**：在 API 层声明 `referenced_data_file` 字段。

**工作逻辑**：
- 新增字段常量 `REFERENCED_DATA_FILE = optional(143, "referenced_data_file", StringType.get(), "Fully qualified location (URI with FS scheme) of a data file that all deletes reference")`。
- `getType` 返回的 `StructType` 字段列表在 `SORT_ORDER_ID` 之后追加 `REFERENCED_DATA_FILE`。
- 注释 `NEXT ID TO ASSIGN` 由 142 更新为 144。

### `api/src/main/java/org/apache/iceberg/DeleteFile.java`

**修改目的**：在 `DeleteFile` 接口暴露引用数据文件。

**工作逻辑**：新增 `default String referencedDataFile()` 返回 `null`，Javadoc 说明该字段对 DV 必需、对仅引用一个数据文件的 position delete 文件可选、对 equality delete 文件始终返回 null。

### `core/src/main/java/org/apache/iceberg/BaseFile.java`

**修改目的**：在 `BaseFile` 内部状态与字段访问机制中承载新字段。

**工作逻辑**：
- 新增私有字段 `private String referencedDataFile = null;`。
- `PROJECTED_FIELDS` 静态数组在 `SORT_ORDER_ID` 与 `ROW_POSITION` 之间插入 `DataFile.REFERENCED_DATA_FILE`，使基础字段总数从 17 增至 18。
- 主构造器新增 `String referencedDataFile` 参数并赋值；拷贝构造器复制该字段。
- 字段访问的 ordinal 因插入新字段而整体后移一位：
  - `set` 方法 `case 17` 改为设置 `referencedDataFile`（`value != null ? value.toString() : null`），原 `fileOrdinal` 移至 `case 18`。
  - `get` 方法 `case 17` 改为返回 `referencedDataFile`，原 `fileOrdinal` 移至 `case 18`。
- 新增 `public String referencedDataFile()` getter。
- `toString` 中追加 `"referenced_data_file"` 键值。

### `core/src/main/java/org/apache/iceberg/V2Metadata.java`

**修改目的**：在 v2 manifest 索引访问中暴露新字段。

**工作逻辑**：
- `V2_INDEXED_FIELDS` 列表在 `SORT_ORDER_ID` 后追加 `DataFile.REFERENCED_DATA_FILE`。
- `IndexedManifestEntry.get` 新增 `case 16`：当被包装对象 `instanceof DeleteFile` 时返回 `((DeleteFile) wrapped).referencedDataFile()`，否则返回 `null`。

### `core/src/main/java/org/apache/iceberg/V3Metadata.java`

**修改目的**：在 v3 manifest 索引访问中暴露新字段，但仅对 position delete 有效。

**工作逻辑**：
- 同 V2，`V3_INDEXED_FIELDS` 列表追加 `DataFile.REFERENCED_DATA_FILE`。
- `IndexedManifestEntry.get` 新增 `case 16`：当 `wrapped.content() == FileContent.POSITION_DELETES` 时返回 `referencedDataFile()`，否则返回 `null`。这与规格中"equality delete 始终为 null"一致。

### `core/src/main/java/org/apache/iceberg/FileMetadata.java`

**修改目的**：让删除文件构建器能设置引用数据文件。

**工作逻辑**：
- `Builder` 新增私有字段 `referencedDataFile`。
- 新增方法 `withReferencedDataFile(CharSequence newReferencedDataFile)`：非 null 时 `toString()` 后赋值，null 时置 null，返回 `this`。
- `build()` 构造 `GenericDeleteFile` 时把 `referencedDataFile` 作为最后一个参数传入。

### `core/src/main/java/org/apache/iceberg/GenericDataFile.java`

**修改目的**：适配 `BaseFile` 构造器新签名。

**工作逻辑**：调用父类构造器时在 `keyMetadata` 之后补 `null /* no referenced data file */`，因为数据文件不引用其他数据文件。

### `core/src/main/java/org/apache/iceberg/GenericDeleteFile.java`

**修改目的**：让删除文件能持有引用数据文件。

**工作逻辑**：构造器新增 `String referencedDataFile` 参数，透传给 `BaseFile` 父类构造器。

### `core/src/main/java/org/apache/iceberg/BaseScan.java`

**修改目的**：在删除文件扫描的统计列投影中包含新字段。

**工作逻辑**：`DELETE_SCAN_WITH_STATS_COLUMNS` 列表在 `"split_offsets"` 之后、`"equality_ids"` 之前插入 `"referenced_data_file"`。

### `core/src/main/java/org/apache/iceberg/ContentFileParser.java`

**修改目的**：在 REST/JSON 序列化中支持新字段。

**工作逻辑**：
- 新增常量 `REFERENCED_DATA_FILE = "referenced-data-file"`。
- 写入时：在 `sort-order-id` 之后，若 `contentFile instanceof DeleteFile` 且 `referencedDataFile() != null`，写出该字符串字段。
- 读取时：`JsonUtil.getStringOrNull(REFERENCED_DATA_FILE, jsonNode)` 解析后传入 `GenericDeleteFile` 构造器。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`

**修改目的**：让 `SnapshotProducer` 内部的 `DeleteFile` 包装器透传新字段。

**工作逻辑**：内部包装类新增 `@Override public String referencedDataFile() { return deleteFile.referencedDataFile(); }`，保证写快照过程中包装的删除文件不丢失该信息。

### `core/src/main/java/org/apache/iceberg/util/ContentFileUtil.java`

**修改目的**：在解析删除文件路径时优先使用引用数据文件。

**工作逻辑**：在原有 `deleteFile` 路径解析逻辑之前插入判断——若 `deleteFile.referencedDataFile() != null`，直接返回该值。这意味着当删除文件显式声明了引用的数据文件时，工具不再走 `DELETE_FILE_PATH` 元数据列解析路径。

### 测试文件

- `TestBase.java`：新增 `protected DeleteFile newDeleteFileWithRef(DataFile dataFile)`，使用 `FileMetadata.deleteFileBuilder` 构造一个 position delete 文件，`withReferencedDataFile(dataFile.location())` 设置引用，便于其他测试复用。
- `TestManifestReader.java`：原 `((BaseFile) file).get(17)` 改为 `get(18)`（因 ordinal 后移）；新增 `testDeleteFilesWithReferences`，对 `FILE_A`/`FILE_B` 各构造一个带引用的 delete 文件，写入 manifest 后读回并断言 `referencedDataFile()` 与原始数据文件 location 一致。
- `TestContentFileParser.java`：新增 `deleteFileWithDataRef` 构造带 `"/path/to/data/file.parquet"` 引用的 `GenericDeleteFile`，以及对应 JSON 字符串 `deleteFileWithDataRefJson`，作为参数化测试用例；既有 `deleteFileWithAllOptional`、`deleteFileWithRequiredOnly` 的构造器调用补 `null` 参数以适配新签名。
- `TestManifestEncryption.java`、`TestManifestWriterVersions.java`：为 `GenericDeleteFile` 构造器调用补 `null` 参数以适配新签名。

## 小结

- **成效**：`referenced_data_file` 字段在 API/Core 层全链路落地，包括接口声明、`BaseFile` 状态、构建器、v2/v3 manifest 索引、扫描列投影、JSON 序列化、`SnapshotProducer` 包装器与 `ContentFileUtil` 路径解析；测试覆盖了 manifest 往返与 JSON 解析。本提交是 DV 实现的"字段骨架"，后续 DV 的 Puffin 读写可在此基础上扩展 `content_offset`/`content_size_in_bytes`。
- **影响范围**：触及 17 个文件（11 个主代码 + 6 个测试），共约 +157 / -15 行。属于 API 兼容性增量改动（新字段为 optional，`DeleteFile.referencedDataFile()` 默认返回 null），但 `BaseFile` 内部字段 ordinal 与 `GenericDeleteFile` 构造器签名发生变化，对继承或直接构造这些内部类的下游代码不兼容（需重新编译）。
- **回迁到 1.4.x 的注意事项**：**不建议回迁**。理由有三：其一，`referenced_data_file` 是为 v3 删除向量服务的字段，1.4.x 主打 v2，没有 DV 用例，回迁后无实际消费方；其二，`BaseFile` 字段 ordinal 与 `GenericDeleteFile` 构造器签名的变更属破坏性内部改动，1.4.x 已有的 position/equality delete 读写测试与下游集成（Flink/Spark 等）需要一并回归，风险高收益低；其三，规格字段 ID 143 在 1.4.x 对应的规格版本中未定义，回迁会引入规格与实现的不一致。若 1.4.x 强烈需要 DV 支持，应整体升级到包含完整 DV 实现的版本线，而非单独回迁本提交。
