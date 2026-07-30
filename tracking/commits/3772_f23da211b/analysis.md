# 提交 3772：Core, Parquet: Allow for Writing Parquet/Avro Manifests in V4 (#15634)

## 提交信息

- **序号**：3772 / 4088
- **哈希**：f23da211bbe108a060df30c1f65684858aea0e8d
- **短哈希**：f23da211b
- **日期**：2026-05-22 15:52:38 -0500
- **作者**：Russell Spitzer
- **提交说明**：Core, Parquet: Allow for Writing Parquet/Avro Manifests in V4 (#15634)
- **PR/Issue**：#15634

## 总体目的

这个提交使 Iceberg V4 格式的清单文件（manifest files）支持使用 Parquet 格式写入，而不仅限于 Avro。同时处理了 V4 Parquet 清单在非分区表中的几个技术挑战：

1. **Parquet 无法表示空结构体**：非分区表的分区类型是空结构体 `StructType.of()`，但 Parquet 格式不支持空组（empty group）。因此需要在 V4 schema 中完全省略分区字段。
2. **原生加密支持**：Parquet 支持原生加密（native encryption），不需要 Avro 那样的整文件加密方式，需要区分处理。
3. **Parquet 容器复用问题**：Parquet 读取器会复用 ByteBuffer 容器，需要深拷贝以避免数据被覆盖。
4. **V2/V3 仍限制为 Avro**：较旧的格式版本仍然只支持 Avro，需要添加校验。

## 如何达成设计目的

1. **`ManifestWriter` 修改**：新增 `format` 字段，根据文件名确定格式。V4 的 `newAppender` 方法使用 `format()` 而非硬编码 `FileFormat.AVRO`。对 V2/V3 添加 Avro 格式校验。处理原生加密文件的输出路径选择。
2. **`V4Metadata` 修改**：`fileType()` 方法在分区类型为空时省略分区字段。`DataFileWrapper` 和 `ManifestEntryWrapper` 接受 `partitionType` 参数，处理分区字段缺失时的位置偏移。
3. **`ManifestReader` 修改**：读取元数据时校验 Avro 格式（Parquet 不支持元数据读取）。非分区表的分区字段标记为 optional。
4. **`BaseFile` 修改**：处理 Parquet 容器复用导致的 ByteBuffer 数据覆盖问题，添加深拷贝逻辑。
5. **基准测试重组**：将 Manifest 基准测试拆分为独立的读和写基准测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestWriter.java` (+50/-12 lines)

**修改目的**：支持 V4 清单使用 Parquet 格式写入，V2/V3 限制为 Avro。

**工作逻辑**：
1. 新增 `format` 字段，在构造函数中通过 `FileFormat.fromFileName()` 确定。
2. 新增 `outputFile()` 方法：当格式为 Parquet 且文件是 `NativeEncryptionOutputFile` 时，使用原生加密文件而非整文件加密。
3. 新增 `keyMetadataBuffer()` 方法：区分 Avro（需要文件长度嵌入 GCM 截断保护）和 Parquet（原生加密自行处理）的密钥元数据处理。
4. V4 的 `newAppender` 方法使用 `format()` 替代硬编码 `FileFormat.AVRO`。
5. V4 的 `ManifestEntryWrapper` 构造函数新增 `spec.partitionType()` 参数。
6. V2/V3 的各 writer 构造函数添加 `Preconditions.checkArgument(format() == FileFormat.AVRO, ...)` 校验。

### `core/src/main/java/org/apache/iceberg/V4Metadata.java` (+38/-36 lines)

**修改目的**：处理非分区表中分区字段的省略。

**工作逻辑**：
1. `fileType()` 方法：当 `partitionType.fields().isEmpty()` 时，跳过分区字段（`PARTITION_ID`），生成的 schema 有 18 个字段而非 19 个。这是因为 Parquet 无法表示空结构体。
2. `ManifestEntryWrapper` 构造函数新增 `partitionType` 参数，传递给 `DataFileWrapper` 和 `entrySchema()`。
3. `DataFileWrapper` 新增 `hasPartition` 标志和 `PARTITION_POSITION` 常量。`get()` 方法中，当没有分区字段时，位置 >= 3 的字段需要偏移 +1 以跳过分区字段的位置。

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (+23/-1 lines)

**修改目的**：处理 Parquet 清单的读取和非分区表分区字段。

**工作逻辑**：
1. `readMetadata()` 方法新增 Avro 格式校验，因为只有 Avro 清单支持元数据读取。
2. 新增 `UNPARTITIONED_PARTITION_FIELD` 常量（optional 的空结构体分区字段）。
3. 在 schema 投影中，非分区表的分区字段使用 optional 版本，使读取器在字段缺失时返回 null 而非抛出异常。

### `core/src/main/java/org/apache/iceberg/BaseFile.java` (+35/-2 lines)

**修改目的**：处理 Parquet 容器复用导致的 ByteBuffer 数据覆盖。

**工作逻辑**：
1. `set()` 方法 case 4（partitionData）：当值为 null 时保留构造函数初始化的默认值（V4 Parquet 非分区清单中分区字段缺失）。
2. `copyByteBufferMap()` 方法：新增 null 检查和 `deepCopyByteBufferMap()` 方法。深拷贝 ByteBuffer 是因为 Parquet 读取器会复用 ByteBuffer 容器，不拷贝的话后续读取会覆盖之前的数据。
3. 添加详细的注释说明 Parquet 容器复用和 SerializableByteBufferMap 的处理逻辑。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+4/-3 lines)

**修改目的**：适配 ManifestWriter 的变化。

**工作逻辑**：调整与清单写入相关的逻辑。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (+1/-0 lines)

**修改目的**：小幅调整。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java` (+12/-6 lines)

**修改目的**：处理 Parquet 读取器中的空结构体问题。

**工作逻辑**：调整 struct reader 以正确处理空结构体场景。

### 测试文件变更

- `TestManifestWriterVersions.java` (+114/-3)：新增 V4 Parquet 清单写入测试。
- `TestManifestReader.java` (+3/-1)：适配读取器变化。
- `TestManifestWriter.java` (+8/-3)：适配写入器变化。
- `TestFastAppend.java`、`TestMergeAppend.java`、`TestRewriteManifests.java` 等：适配测试。
- `TestBase.java` (+15/-8)：适配基础测试设施。

### 基准测试重组

- 删除 `ManifestBenchmark.java`（部分保留）和 `ManifestReadBenchmark.java`、`ManifestWriteBenchmark.java`。
- 新增 `ManifestBenchmarkUtil.java` 和 `ManifestCompressionBenchmark.java`。
- 重组基准测试结构，支持不同格式的清单写入对比。

## 总结

这个提交是 V4 格式的重要扩展，使清单文件支持 Parquet 格式写入，带来了更好的压缩率和原生加密支持。核心技术挑战包括处理 Parquet 无法表示空结构体的问题（省略非分区表的分区字段并调整位置偏移）、Parquet 容器复用导致的 ByteBuffer 深拷贝需求，以及区分 Avro 整文件加密和 Parquet 原生加密的密钥处理。V2/V3 格式仍限制为 Avro 以保证向后兼容。这是一个涉及核心模块多个文件的大型功能提交。
