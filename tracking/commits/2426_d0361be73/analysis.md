# 提交 2426：Core, Data, Parquet: Cleanup unit tests by delegating file cleanup to JUnit (#13590)

## 提交信息

- **序号**：2426 / 4088
- **哈希**：d0361be73688d23f0e03ee11bb1a378f2383a80a
- **短哈希**：d0361be73
- **日期**：2025-07-28 12:32:07 +0200
- **作者**：Anoop Johnson
- **提交说明**：Core, Data, Parquet: Cleanup unit tests by delegating file cleanup to JUnit (#13590)
- **PR/Issue**：#13590

## 总体目的

本提交对 Iceberg 项目的单元测试进行了系统性的清理，将临时文件的创建方式从 `File.createTempFile()` 改为使用 JUnit 管理的临时目录（`@TempDir`）的 `Path.resolve()` 方法，从而简化测试代码并让 JUnit 自动负责文件清理。

此前，许多测试使用 `File.createTempFile("prefix", null, temp.toFile())` 来创建临时文件。这种方式有两个问题：
1. `File.createTempFile` 会在磁盘上立即创建一个空文件，使用前通常需要先删除它（如 `assertThat(manifestFile.delete()).isTrue()`）
2. 需要处理 `IOException`，增加了代码的样板逻辑（如 try-catch 或声明 throws）

改为 `temp.resolve("prefix" + System.nanoTime()).toFile()` 后：
1. 不会立即在磁盘上创建文件，只是构造一个路径，避免了先创建再删除的冗余操作
2. 不需要处理 `IOException`
3. 临时目录由 JUnit 的 `@TempDir` 注解管理，测试结束后会自动清理整个目录

## 如何达成设计目的

在 34 个测试文件中，系统性地将 `File.createTempFile(prefix, suffix, temp.toFile())` 替换为 `temp.resolve(prefix + System.nanoTime() + suffix).toFile()`。使用 `System.nanoTime()` 保证文件名唯一性，替代 `File.createTempFile` 内置的随机数机制。

同时移除了不再需要的：
- `import java.io.File`（部分文件中不再直接使用 File 类的静态方法）
- `import java.io.IOException`（部分文件中不再需要声明或捕获 IOException）
- `import java.io.UncheckedIOException` / `import org.apache.iceberg.exceptions.RuntimeIOException`（不再需要将 IOException 包装为运行时异常）
- 显式的 `assertThat(file.delete()).isTrue()` 调用

## 修改详情

### 34 个测试文件 (+78/-142 lines 总计)

以下列出关键文件的修改模式：

**`core/src/test/java/org/apache/iceberg/LocalTableOperations.java` (+1/-9 lines)**

**修改目的**：简化元数据文件路径创建逻辑。

**工作逻辑**：将 `metadataFileLocation` 方法中的 `File.createTempFile("junit", null, temp.toFile()).getAbsolutePath()` 替换为 `temp.resolve("junit" + System.nanoTime()).toFile().getAbsolutePath()`。移除了 try-catch 块和 IOException/RuntimeIOException 的导入。

**`core/src/test/java/org/apache/iceberg/TestManifestWriter.java` (+3/-11 lines)**

**修改目的**：简化 manifest 文件创建。

**工作逻辑**：
- `testWriteManifestWithSequenceNumber`：将 `File.createTempFile("manifest", ".avro", temp.toFile())` + `assertThat(manifestFile.delete()).isTrue()` 替换为 `temp.resolve("manifest" + System.nanoTime() + ".avro").toFile()`，移除了先创建空文件再删除的冗余步骤
- `newManifestFile`：将包含 try-catch 的 `File.createTempFile(...)` 替换为简洁的 `temp.resolve(...)`，移除了 `UncheckedIOException` 导入

**`data/src/test/java/org/apache/iceberg/data/DeleteReadTests.java` (+25/-24 lines)**

**修改目的**：简化数据文件和删除文件的创建。

**工作逻辑**：将所有 `File.createTempFile("junit", null, temp.toFile())` 调用替换为 `temp.resolve("junit" + System.nanoTime()).toFile()`，涉及大量测试方法中的数据文件和删除文件创建。

**`core/src/test/java/org/apache/iceberg/encryption/TestGcmStreams.java` (+6/-6 lines)**

**修改目的**：简化加密测试中的临时文件创建。

**工作逻辑**：将 6 处 `File.createTempFile("test", null, temp.toFile())` 替换为 `temp.resolve("test" + System.nanoTime()).toFile()`。

### 其他文件

同样的模式被应用到以下文件（每个文件修改量较小）：
- `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java`
- `core/src/test/java/org/apache/iceberg/TestBase.java`
- `core/src/test/java/org/apache/iceberg/TestManifestCaching.java`
- `core/src/test/java/org/apache/iceberg/TestManifestListVersions.java`
- `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`
- `core/src/test/java/org/apache/iceberg/TestScanDataFileColumns.java`
- `core/src/test/java/org/apache/iceberg/TestSnapshotJson.java`
- `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`
- `core/src/test/java/org/apache/iceberg/avro/TestReadDefaultValues.java`
- `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopFileIO.java`
- `core/src/test/java/org/apache/iceberg/TestSplitScan.java`
- `data/src/test/java/org/apache/iceberg/data/GenericAppenderHelper.java`
- `data/src/test/java/org/apache/iceberg/data/TestDataFileIndexStatsFilters.java`
- `data/src/test/java/org/apache/iceberg/data/TestLocalScan.java`
- `data/src/test/java/org/apache/iceberg/data/TestMetricsRowGroupFilter.java`
- `data/src/test/java/org/apache/iceberg/data/TestTableMigrationUtil.java`
- `data/src/test/java/org/apache/iceberg/data/avro/TestGenericData.java`
- `data/src/test/java/org/apache/iceberg/data/avro/TestGenericReadProjection.java`
- `data/src/test/java/org/apache/iceberg/data/orc/TestGenericData.java`
- `data/src/test/java/org/apache/iceberg/data/orc/TestGenericReadProjection.java`
- `data/src/test/java/org/apache/iceberg/data/orc/TestOrcDataWriter.java`
- `data/src/test/java/org/apache/iceberg/data/orc/TestOrcRowIterator.java`
- `data/src/test/java/org/apache/iceberg/data/parquet/TestGenericData.java`
- `data/src/test/java/org/apache/iceberg/data/parquet/TestGenericReadProjection.java`
- `data/src/test/java/org/apache/iceberg/data/parquet/TestParquetEncryptionWithWriteSupport.java`
- `data/src/test/java/org/apache/iceberg/parquet/TestGenericMergingMetrics.java`
- `core/src/test/java/org/apache/iceberg/rest/RESTCatalogServer.java`
- `parquet/src/test/java/org/apache/iceberg/parquet/TestBloomRowGroupFilter.java`
- `parquet/src/test/java/org/apache/iceberg/parquet/TestDictionaryRowGroupFilter.java`
- `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java`

## 总结

本提交是一个测试代码质量改进，通过将 `File.createTempFile()` 替换为 `@TempDir` 管理的 `Path.resolve()` 方式，简化了 34 个测试文件中的临时文件创建逻辑。改动减少了代码样板（移除了 IOException 处理、文件删除操作），避免了不必要的磁盘 I/O（不再先创建空文件再删除），并让 JUnit 统一负责临时文件的清理。净减少 64 行代码（+78/-142）。
