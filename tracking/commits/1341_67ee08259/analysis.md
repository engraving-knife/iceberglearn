# 提交 1341：Core, Data, Flink, Spark: Improve tableDir initialization for tests (#11460)

## 提交信息

- **序号**：1341 / 4088
- **哈希**：67ee0825959c42b405c2cc0e2e5b916bd2cfc493
- **短哈希**：67ee08259
- **日期**：2024-11-05（Tue Nov 5 12:56:02 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core, Data, Flink, Spark: Improve tableDir initialization for tests (#11460)
- **PR/Issue**：#11460

## 总体目的

Iceberg 测试套件中大量测试类需要"表目录"用于通过 `TestTables.create(...)`、`HadoopTables.create(...)` 等创建临时表。这些测试类的目录管理方式不统一、且大多走"在 `@TempDir Path temp` 之下再用 `Files.createTempDirectory(temp, "junit").toFile()` 建一个子目录，然后 `tableDir.delete()` 把它删掉（因为表的创建要求目录不存在或要由表自己创建）"的复杂模式。这种模式有三个问题：

1. **冗余**：先建目录再删目录，多余的两步操作。
2. **隐患**：`tableDir.delete()` 只在目录为空时才返回 true；如果目录创建后有任何残留（如上次测试异常退出留下的文件），删除会失败、`assertThat(tableDir.delete()).isTrue()` 会抛错。
3. **flaky**：JUnit 5 的 `@TempDir` 本身就提供"每个测试方法独立、测试结束自动清理"的目录生命周期，再叠一层手动管理反而引入竞态与清理负担（与 1340 同源问题）。

本提交统一改为：直接用 `@TempDir File tableDir` 作为表目录，删除所有"先 `Files.createTempDirectory` 再 `delete()`"的样板代码，让 JUnit 5 接管目录生命周期。这是 1340 思路的批量推广。

涉及 Core、Data、Flink v1.18/v1.19/v1.20、Spark v3.5 共 51 个测试文件，是大规模的"机械式重构"。

## 如何达成设计目的

按统一模式重构 51 个测试类：

1. 在测试类中声明 `@TempDir private File tableDir;`（或既有 `@TempDir Path temp` 改为 `@TempDir File tableDir`，或新增并删除 `temp`）。
2. 删除 `@BeforeEach` 中的 `this.tableDir = Files.createTempDirectory(temp, "junit").toFile();` 与 `assertThat(tableDir.delete()).isTrue();` 样板代码。
3. 测试方法体内原本 `File tableDir = Files.createTempDirectory(temp, "junit").toFile(); assertThat(tableDir.delete()).isTrue();` 的局部变量声明删除，统一使用类字段 `tableDir`。
4. 删除不再使用的 `import java.nio.file.Files;`、`import java.nio.file.Path;`、`import java.io.File;`（视具体类而定）以及不再使用的 `@BeforeEach`、`setupTableDir()` 方法。
5. 对原本依赖 `temp` 子路径的测试（如 `temp.resolve("junit").toFile()`）改为直接 `tableDir`。

由于改动机械且数量大，下文按文件分组总结，重点说明每组的共同模式与少数差异。

## 修改详情

### Core 模块测试类（19 个）

文件清单：

- `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java`
- `core/src/test/java/org/apache/iceberg/FilterFilesTestBase.java`
- `core/src/test/java/org/apache/iceberg/ScanTestBase.java`
- `core/src/test/java/org/apache/iceberg/TestCreateTransaction.java`
- `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java`
- `core/src/test/java/org/apache/iceberg/TestMetadataTableScansWithPartitionEvolution.java`
- `core/src/test/java/org/apache/iceberg/TestMetrics.java`
- `core/src/test/java/org/apache/iceberg/TestMetricsModes.java`
- `core/src/test/java/org/apache/iceberg/TestOverwrite.java`
- `core/src/test/java/org/apache/iceberg/TestOverwriteWithValidation.java`
- `core/src/test/java/org/apache/iceberg/TestPartitionSpecBuilderCaseSensitivity.java`
- `core/src/test/java/org/apache/iceberg/TestPartitionSpecInfo.java`
- `core/src/test/java/org/apache/iceberg/TestPartitioning.java`
- `core/src/test/java/org/apache/iceberg/TestReplacePartitions.java`
- `core/src/test/java/org/apache/iceberg/TestReplaceTransaction.java`
- `core/src/test/java/org/apache/iceberg/TestSortOrder.java`
- `core/src/test/java/org/apache/iceberg/TestSplitPlanning.java`
- `core/src/test/java/org/apache/iceberg/TestTimestampPartitions.java`
- `core/src/test/java/org/apache/iceberg/util/TestSnapshotUtil.java`

**共同模式**：

- 把 `@TempDir private Path temp; private File tableDir;` 改为 `@TempDir private File tableDir;`（如 `FilterFilesTestBase`、`TestPartitioning`、`TestPartitionSpecInfo`、`TestSortOrder`）。
- 删除 `@BeforeEach setupTableDir()` 中的 `Files.createTempDirectory(temp, "junit").toFile();`。
- 删除测试方法体内的 `File tableDir = Files.createTempDirectory(temp, "junit").toFile(); assertThat(tableDir.delete()).isTrue();`（如 `TestCreateTransaction` 的 8 处、`TestReplacePartitions` 的 3 处、`TestReplaceTransaction` 的 2 处、`TestMetrics` 的 2 处、`TestMetricsModes` 的 3 处、`TestOverwrite`/`TestOverwriteWithValidation` 的 `@BeforeEach` 各 1 处）。
- 删除 `TestMetadataTableScans` 中 `this.tableDir = Files.createTempDirectory(temp, "junit").toFile();`。
- 删除 `TestMetadataTableScansWithPartitionEvolution` 中 `this.tableDir = Files.createTempDirectory(temp, "junit").toFile(); tableDir.delete();`。
- 删除 `TestPartitionSpecBuilderCaseSensitivity` 中整个 `@TempDir`/`@BeforeEach`（该类不再需要 tableDir，直接用静态 schema 测 spec builder case sensitivity）。
- `TestSplitPlanning`、`TestTimestampPartitions`、`TestOverwrite`、`TestOverwriteWithValidation` 中 `@BeforeEach` 改为只保留表创建逻辑，不再管理目录。
- `TestSnapshotUtil` 的 `@BeforeEach` 删除 `tableDir.delete(); // created by table create` 一行。
- `DeleteFileIndexTestBase#testUnpartitionedTableScan` 把局部 `File location = Files.createTempDirectory(temp, "junit").toFile(); assertThat(location.delete()).isTrue();` 改为直接复用类字段 `tableDir`。
- `ScanTestBase` 两个测试方法中删除 `File dir = Files.createTempDirectory(temp, "junit").toFile(); dir.delete();`，改用 `tableDir`。
- `TestMetrics` 把 `@TempDir public Path temp` 改为 `@TempDir protected Path temp; @TempDir private File tableDir;`（temp 保留是因为该类其他地方仍用 `temp`，但 tableDir 抽到字段）。

### Data 模块测试类（10 个）

文件清单：

- `data/src/test/java/org/apache/iceberg/TestSplitScan.java`
- `data/src/test/java/org/apache/iceberg/data/TestGenericReaderDeletes.java`
- `data/src/test/java/org/apache/iceberg/io/TestAppenderFactory.java`
- `data/src/test/java/org/apache/iceberg/io/TestBaseTaskWriter.java`
- `data/src/test/java/org/apache/iceberg/io/TestFileWriterFactory.java`
- `data/src/test/java/org/apache/iceberg/io/TestPartitioningWriters.java`
- `data/src/test/java/org/apache/iceberg/io/TestPositionDeltaWriters.java`
- `data/src/test/java/org/apache/iceberg/io/TestRollingFileWriters.java`
- `data/src/test/java/org/apache/iceberg/io/TestTaskEqualityDeltaWriter.java`
- `data/src/test/java/org/apache/iceberg/io/TestWriterMetrics.java`

**共同模式**：

- `TestSplitScan`：`@TempDir private File tempDir; private File tableLocation;` 合并为 `@TempDir private File tableLocation;`，`@BeforeEach` 中删除 `tableLocation = Files.createTempDirectory(tempDir.toPath(), "table").toFile();`；`writeToFile` 中的 `File.createTempFile("junit", null, tempDir)` 改为 `tableLocation`。
- `TestGenericReaderDeletes`：新增 `@TempDir private File tableDir;`，`createTable` 内删除 `File tableDir = Files.createTempDirectory(temp, "junit").toFile(); assertThat(tableDir.delete()).isTrue();`。
- `TestAppenderFactory`、`TestBaseTaskWriter`、`TestFileWriterFactory`、`TestPartitioningWriters`、`TestPositionDeltaWriters`、`TestRollingFileWriters`、`TestTaskEqualityDeltaWriter`：在 `@BeforeEach setupTable()` 中删除 `this.tableDir = Files.createTempDirectory(temp, "junit").toFile(); assertThat(tableDir.delete()).isTrue();`；这些类的父类已提供 `@TempDir File tableDir`。
- `TestWriterMetrics`：`@TempDir private File tempDir;` 改为 `@TempDir private File tableDir;`；`setupTable()` 与两个 `testMaxColumns` 测试方法删除 `File tableDir = Files.createTempDirectory(tempDir.toPath(), "table").toFile(); assertThat(tableDir.delete()).isTrue();`。

### Flink 模块测试类（v1.18、v1.19、v1.20 各 4 个，共 12 个）

文件清单（每个版本 4 个，三版本结构相同）：

- `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestDeltaTaskWriter.java`
- `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergFilesCommitter.java`
- `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingMonitorFunction.java`
- `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestStreamingReaderOperator.java`
- （v1.19、v1.20 同上 4 个文件，结构一致）

**共同模式**：在 `@BeforeEach setupTable()` 中删除 `this.tableDir = Files.createTempDirectory(temp, "junit").toFile();` 与 `assertThat(tableDir.delete()).isTrue();`，父类提供 `@TempDir File tableDir`。`TestIcebergFilesCommitter` 保留 `flinkManifestFolder = Files.createTempDirectory(temp, "flink").toFile();`（这是 Flink manifest 专用目录，与 tableDir 不同用途，未删除）。

### Spark v3.5 模块测试类（10 个）

文件清单：

- `spark/v3.5/spark/src/test/java/org/apache/iceberg/TestScanTaskSerialization.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestCreateActions.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestDeleteReachableFilesAction.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestExpireSnapshotsAction.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveDanglingDeleteAction.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveOrphanFilesAction.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestIcebergSourceHadoopTables.java`
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataFile.java`

**共同模式**：

- `TestCreateActions`：`private File tableDir;` 改为 `@TempDir private File tableDir;`，删除 `@BeforeEach before()` 中的 try/catch 包裹的 `Files.createTempDirectory(temp, "junit").toFile();`。
- `TestDeleteReachableFilesAction`、`TestRemoveDanglingDeleteAction`、`TestRemoveOrphanFilesAction`、`TestExpireSnapshotsAction`、`TestRewriteDataFilesAction`、`TestRewriteManifestsAction`、`TestSparkDataFile`：`@TempDir private Path temp;` 改为 `@TempDir private File tableDir;`（或新增并删除 `temp`），`@BeforeEach setupTableLocation()` 中删除 `File tableDir = temp.resolve("junit").toFile();`。`TestRewriteManifestsAction` 同时保留 `@TempDir private Path temp;`（其他用）并新增 `@TempDir private File tableDir;`。
- `TestIcebergSourceHadoopTables`：`File tableDir = null;` 改为 `@TempDir private File tableDir;`，`@BeforeEach setupTable()` 删除 `this.tableDir = temp.toFile(); tableDir.delete();`。
- `TestScanTaskSerialization`：新增 `@TempDir private File tableDir;`，`@BeforeEach setupTableLocation()` 删除 `File tableDir = Files.createTempDirectory(temp, "junit").toFile();`。

### 总体统计

- 51 个测试文件改动，共 +32/-270 行（净减少 238 行）。所有改动均为删除样板代码与新增/调整 `@TempDir File tableDir;` 字段，无产品代码改动，无测试逻辑变更。

## 小结

- **成效**：统一了 Core、Data、Flink、Spark 51 个测试类的表目录初始化模式，删除"先 `Files.createTempDirectory` 再 `delete()`"的样板代码，交由 JUnit 5 `@TempDir` 管理目录生命周期；既消除潜在的 flaky（与 1340 同源），也提升代码可读性与一致性。净减少 238 行代码。
- **影响范围**：仅测试代码，覆盖 4 个模块 51 个测试文件；无产品代码、无 API、无 manifest 格式变更。
- **回迁到 1.4.x 的注意事项**：
  1. 这是纯测试基础设施改进，**对 1.4.x 运行时无任何影响**。是否回迁取决于 1.4.x 是否需要这些测试稳定性与可读性收益。
  2. 改动量大（51 个文件）但机械，回迁无功能风险；但需注意 1.4.x 上的测试类结构与 main 可能已分叉（如 1.4.x 可能尚未引入某些测试方法），cherry-pick 时会有冲突，需要逐文件解决。
  3. 如果 1.4.x 的 CI 已稳定运行无 flaky，可暂不回迁以减少维护负担；若 1.4.x 上确有类似 flaky，可挑选受影响的测试类单独回迁。
  4. 回迁需注意 JUnit 5 版本——`@TempDir File` 自 JUnit 5.0 起即支持，1.4.x 上的 JUnit 5 版本应满足。但 1.4.x 上若仍用 JUnit 4（部分老测试可能如此），则该模式不适用，应保持原模式。
