# 提交 2652：AWS, Core, Data, Spark: Remove deprecations for 1.11.0 (#14059)

## 提交信息

- **序号**：2652 / 4088
- **哈希**：345140e9f7d428fcc3b4fcc639a690084a698b76
- **短哈希**：345140e9f
- **日期**：2025-09-18 16:35:17 -0600
- **作者**：Doğukan Çağatay
- **提交说明**：AWS, Core, Data, Spark: Remove deprecations for 1.11.0 (#14059)
- **PR/Issue**：#14059
- **共同作者**：Eduard Tudenhoefner

## 总体目的

本提交是 1.11.0 版本发布前的例行清理工作，移除了在 1.10.0 中标记为 `@Deprecated`（计划在 1.11.0 移除）的所有代码。Apache Iceberg 遵循版本化的弃用策略：当一个 API 被弃用时，会标注弃用版本和计划移除版本，到计划移除版本时统一清理。

本次移除涉及多个模块的弃用代码，包括：
- AWS 模块：S3FileIO、S3InputFile、S3OutputFile 中接收 `S3FileIOProperties` 参数的旧构造器与工厂方法
- GCP 模块：GCSFileIO 中接收 `GCPProperties` 参数的旧构造器
- Core 模块：`PartitionStatsUtil` 整个类、`PartitionStatsHandler` 中旧版 `schema(StructType)` 方法、`RewriteTablePathUtil` 中旧版方法、`TableMetadataParser.read(FileIO, InputFile)`、`EncryptionUtil.createEncryptionManager(...)`、`OAuth2Util` 中多个旧版 token 方法
- Data 模块：`org.apache.iceberg.data.PartitionStatsHandler` 整个类

这些弃用代码大多有推荐的替代方案（如使用 `initialize(Map)` 代替构造器传属性、使用 core 模块的 `PartitionStatsHandler` 代替 data 模块的同名类）。

## 如何达成设计目的

整体思路是批量删除所有标记为 `@Deprecated since 1.10.0, will be removed in 1.11.0` 的代码，并同步更新：

1. **删除弃用代码**：移除弃用的类、方法和构造器
2. **更新调用方**：将测试和基准测试中对已删除方法的调用改为使用推荐的替代方法
3. **更新 API 兼容性配置**：在 `.palantir/revapi.yml` 中记录这些破坏性变更，标注理由为"Removing deprecated code for 1.11.0"，使 Revapi（API 兼容性检查工具）不再报告这些为错误

## 修改详情

### `.palantir/revapi.yml` (+57/-0 lines)

**修改目的**：记录 1.10.0 版本下因移除弃用代码而产生的 API 破坏性变更，使 CI 兼容性检查通过。

**工作逻辑**：在 `acceptedBreaks` 下新增 `"1.10.0"` 版本块，列出 iceberg-core 和 iceberg-data 模块中所有被移除的类和方法（如 `PartitionStatsUtil` 类、`RewriteTablePathUtil.stagingPath` 旧方法、`OAuth2Util` 的多个 token 方法等），每条都标注 `justification: "Removing deprecated code for 1.11.0"`。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (+5/-34 lines)

**修改目的**：移除接收 `S3FileIOProperties` 参数的弃用构造器。

**工作逻辑**：删除了 `S3FileIO(SerializableSupplier<S3Client>, S3FileIOProperties)` 和 `S3FileIO(SerializableSupplier<S3Client>, SerializableSupplier<S3AsyncClient>, S3FileIOProperties)` 两个弃用构造器。保留的构造器内部改为直接用 `SerializableMap.copyOf(Maps.newHashMap())` 初始化属性（而非通过 S3FileIOProperties），统一依赖 `initialize(Map)` 设置属性。同时简化了 `S3FileIO(SerializableSupplier<S3Client>)` 构造器的调用链。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputFile.java` (+0/-88 lines)

**修改目的**：移除 S3InputFile 中接收 `S3Client`/`S3AsyncClient` + `S3FileIOProperties` 参数的弃用 `fromLocation` 工厂方法。

**工作逻辑**：删除了 4 个标记为弃用的 `fromLocation` 静态方法重载，它们接收 `S3Client`、`S3AsyncClient`、`S3FileIOProperties` 等参数。保留了使用 `PrefixedS3Client` 的非弃用版本。

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3OutputFile.java` (+0/-41 lines)

**修改目的**：移除 S3OutputFile 中接收 `S3Client`/`S3AsyncClient` + `S3FileIOProperties` 参数的弃用 `fromLocation` 工厂方法。

**工作逻辑**：删除了 2 个标记为弃用的 `fromLocation` 静态方法重载。保留了使用 `PrefixedS3Client` 的非弃用版本。

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+0/-19 lines)

**修改目的**：移除旧版 `schema(StructType)` 方法。

**工作逻辑**：删除了仅适用于 format version 1 和 2 的旧版 `schema(StructType)` 方法（标注 `@Deprecated since 1.10.0`），保留支持指定 format version 的 `schema(StructType, int)` 方法。

### `core/src/main/java/org/apache/iceberg/PartitionStatsUtil.java` (+0/-144 lines)

**修改目的**：删除整个 `PartitionStatsUtil` 弃用类。

**工作逻辑**：整个文件被删除。该类自 1.10.0 起被弃用，推荐直接使用 `PartitionStatsHandler`。类中包含 `computeStats`、`sortStats`、`collectStats`、`mergeStats` 等方法。

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+0/-91 lines)

**修改目的**：移除弃用的 `stagingPath`、`rewriteDataManifest`、`rewriteDeleteManifest` 方法。

**工作逻辑**：删除了旧版 `stagingPath(String, String)` 方法（新版需要 `sourcePrefix` 参数），以及旧版 `rewriteDataManifest` 和 `rewriteDeleteManifest` 方法。

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java` (+0/-8 lines)

**修改目的**：移除弃用的 `read(FileIO, InputFile)` 方法。

**工作逻辑**：删除了接收 `FileIO` 和 `InputFile` 参数的旧版 `read` 方法。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (+0/-12 lines)

**修改目的**：移除弃用的 `createEncryptionManager` 方法。

**工作逻辑**：删除了接收 `Map<String, String>` 和 `KeyManagementClient` 参数的旧版 `createEncryptionManager` 方法。

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java` (+2/-89 lines)

**修改目的**：移除多个弃用的 token 交换/获取方法。

**工作逻辑**：删除了旧版 `exchangeToken`（两个重载）和 `fetchToken`（两个重载）方法，共 4 个弃用方法。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (+2/-2 lines)

**修改目的**：更新测试中对已删除 `schema(StructType)` 方法的调用。

**工作逻辑**：将 `PartitionStatsHandler.schema(partitionType)` 改为 `PartitionStatsHandler.schema(partitionType, 2)`，使用带 format version 参数的新方法。

### `core/src/test/java/org/apache/iceberg/TestPartitionStatsUtil.java` (+0/-588 lines)

**修改目的**：删除针对已移除的 `PartitionStatsUtil` 类的整个测试文件。

**工作逻辑**：整个测试文件被删除，因为被测试的类已被移除。

### `core/src/test/java/org/apache/iceberg/TestRewriteTablePathUtil.java` (+3/-8 lines)

**修改目的**：更新测试以使用新版 `stagingPath` 方法。

**工作逻辑**：移除了对旧版 `stagingPath(originalPath, stagingDir)` 的调用，统一使用新版 `stagingPath(originalPath, sourcePrefix, stagingDir)`。

### `data/src/jmh/java/org/apache/iceberg/PartitionStatsHandlerBenchmark.java` (+1/-1 lines)

**修改目的**：更新基准测试中对 `schema` 方法的调用。

**工作逻辑**：将 `PartitionStatsHandler.schema(Partitioning.partitionType(table))` 改为 `PartitionStatsHandler.schema(Partitioning.partitionType(table), 2)`。

### `data/src/main/java/org/apache/iceberg/data/PartitionStatsHandler.java` (+0/-282 lines)

**修改目的**：删除 data 模块中整个弃用的 `PartitionStatsHandler` 类。

**工作逻辑**：整个文件被删除。该类自 1.10.0 起被弃用，推荐使用 core 模块的 `org.apache.iceberg.PartitionStatsHandler`。类中包含分区统计的 schema 生成、计算写入、读取等功能。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java` (+0/-18 lines)

**修改目的**：移除接收 `GCPProperties` 参数的弃用构造器。

**工作逻辑**：删除了 `GCSFileIO(SerializableSupplier<Storage>, GCPProperties)` 构造器，同时移除了对 `GCPProperties` 的 import。

### Spark 模块测试文件（多个，各 +1/-1 或 +2/-2 lines）

**修改目的**：更新 Spark 3.4/3.5/4.0 测试中对 `schema` 方法的调用。

**工作逻辑**：在 `TestComputePartitionStatsAction`、`TestRewriteDataFilesProcedure`、`TestRewriteManifestsProcedure` 等测试中，将 `PartitionStatsHandler.schema(partitionType)` 统一改为 `PartitionStatsHandler.schema(partitionType, 2)`。

## 总结

本提交是 1.11.0 版本发布前的大规模弃用代码清理，涉及 AWS、Core、Data、GCP、Spark 等多个模块，共删除约 1433 行代码。移除的内容包括弃用的 FileIO 构造器与工厂方法、PartitionStatsUtil 类、data 模块的 PartitionStatsHandler 类、以及多个工具类的旧版方法。所有调用方已更新为使用推荐的新 API，并通过 revapi.yml 记录了 API 破坏性变更。这是保持代码库整洁、减少技术债务的重要维护工作。
