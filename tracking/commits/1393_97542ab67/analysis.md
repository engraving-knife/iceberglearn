# 提交 1393：API, Arrow, Core, Data, Spark: Replace usage of deprecated ContentFile#path API with location API (#11563)

## 提交信息

- **序号**：1393 / 4088
- **哈希**：97542ab677cc1cc03f9cab7461266f01888ef1f9
- **短哈希**：97542ab67
- **日期**：2024-11-18（Mon Nov 18 08:26:58 2024 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：API, Arrow, Core, Data, Spark: Replace usage of deprecated ContentFile#path API with location API (#11563)
- **PR/Issue**：#11563

## 总体目的

Iceberg 的 `ContentFile` 接口（`DataFile`、`DeleteFile` 的父接口）历史上提供 `path()` 方法返回 `CharSequence`（文件路径字符串）。该 API 在后续版本中被标记为 `@Deprecated`，社区推荐改用 `location()` 方法：`location()` 返回值同样是文件位置的字符串表示，但语义更清晰（"location" 表示文件存储位置，而 "path" 容易与文件系统路径混淆），并且 `path()` 计划在后续主版本中移除。

本提交是一次大规模的"去 deprecated"清理：把 API、Arrow、Core、Data、Spark 等多个模块中所有生产代码与测试代码里对 `file.path()` / `file.path().toString()` 的调用，统一替换为 `file.location()` 或 `file.location().toString()`。这样可以：

1. 在 `path()` 被移除前完成迁移，避免后续版本升级时编译失败；
2. 让代码风格统一到新的推荐 API；
3. 减少 deprecated 警告噪音，便于将来进一步清理 deprecated API。

## 如何达成设计目的

逐文件执行机械替换，但需根据上下文判断具体替换形式：

- 当原代码用 `file.path()` 作为 `CharSequence` 使用（例如作为 `Pair<CharSequence, Long>` 的元素、或作为日志参数）时，直接替换为 `file.location()`，因为 `location()` 同样返回 `CharSequence`。
- 当原代码用 `file.path().toString()` 作为 `String` 使用（例如作为 Map 的 key）时，替换为 `file.location()`，因为新版 `location()` 在多数上下文中可直接当 `String` 用；个别位置保留 `.toString()` 以满足等值比较的类型匹配。
- 测试中用 `assertThat(...).isEqualTo(FILE_A.path())` 的，替换为 `isEqualTo(FILE_A.location())`，保持断言语义一致。

涉及 48 个文件、共 237 处新增、227 处删除，规模较大但语义零变化。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ArrowReader.java`

**修改目的**：Arrow 向量化读取器在收集加密文件元数据和按文件查找 InputFile 时使用新 API。

**工作逻辑**：
- `keyMetadata.put(file.path().toString(), file.keyMetadata())` → `keyMetadata.put(file.location(), file.keyMetadata())`：把文件加密元数据按文件位置索引，`location()` 返回的 `CharSequence` 可直接作为 Map key。
- `inputFiles.get(task.file().path().toString())` → `inputFiles.get(task.file().location())`：根据 FileScanTask 的文件位置从预加载的 InputFile 映射中取回输入文件。

### `core/src/main/java/org/apache/iceberg/encryption/InputFilesDecryptor.java`

**修改目的**：解密器在为每个扫描任务收集加密 key 时改用 `location()`。

**工作逻辑**：`keyMetadata.put(file.path().toString(), file.keyMetadata())` → `keyMetadata.put(file.location(), file.keyMetadata())`，与 ArrowReader 中的修改一致。

### `api/src/test/java/org/apache/iceberg/TestHelpers.java`

**修改目的**：测试辅助类中根据文件名推断文件格式时改用 `location()`。

**工作逻辑**：`FileFormat.fromFileName(path())` → `FileFormat.fromFileName(location())`，从文件位置字符串解析扩展名以确定 FileFormat。

### `core/src/test/java/org/apache/iceberg/ScanPlanningAndReportingTestBase.java`

**修改目的**：扫描计划测试中断言文件位置使用 `location()`。

**工作逻辑**：`assertThat(task.file().path()).isEqualTo(FILE_D.path())` → `assertThat(task.file().location()).isEqualTo(FILE_D.location())`。

### `core/src/test/java/org/apache/iceberg/TestSnapshot.java`

**修改目的**：快照测试中对 removed/added data file 与 delete file 的位置断言改用 `location()`。

**工作逻辑**：4 处 `removedDataFile.path()` / `addedDataFile.path()` / `removedDeleteFile.path()` / `addedDeleteFile.path()` 全部替换为对应的 `location()` 调用，并同步把期望值的 `FILE_A.path()` 等改为 `FILE_A.location()`。

### `core/src/test/java/org/apache/iceberg/TestTransaction.java`

**修改目的**：事务测试中收集扫描任务的文件路径集合改用 `location()`。

**工作逻辑**：把 `task -> task.file().path().toString()` 简化为 `task -> task.file().location()`，并把期望集合 `FILE_A.path().toString()` 等简化为 `FILE_A.location()`。由于 `location()` 返回 `CharSequence`，`Sets.newHashSet` 可直接接受。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：Catalog 抽象测试基类中的文件路径断言改用 `location()`。共 5 处替换。

### `data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java`

**修改目的**：删除文件加载器在缓存 eq/pos 删除记录和打印日志时使用 `location()`。

**工作逻辑**：
- `String cacheKey = deleteFile.path().toString()` → `String cacheKey = deleteFile.location()`：用文件位置作为缓存 key。
- `LOG.trace("Opening delete file {}", deleteFile.path())` → `LOG.trace("Opening delete file {}", deleteFile.location())`：日志输出文件位置。

### `data/src/main/java/org/apache/iceberg/data/DeleteFilter.java`

**修改目的**：删除过滤器的日志和 InputFile 加载改用 `location()`。

**工作逻辑**：
- `LOG.debug("Adding position delete file {} to filter", delete.path())` → `delete.location()`，equality delete 同理。
- `getInputFile(deleteFile.path().toString())` → `getInputFile(deleteFile.location())`：根据删除文件位置加载 InputFile。

### `data/src/main/java/org/apache/iceberg/data/GenericDeleteFilter.java`

**修改目的**：泛型删除过滤器构造时传递的文件路径改用 `location()`。

**工作逻辑**：`super(task.file().path().toString(), ...)` → `super(task.file().location(), ...)`。

### `data/src/main/java/org/apache/iceberg/data/GenericReader.java`

**修改目的**：泛型读取器在打开文件和异常信息中使用 `location()`。

**工作逻辑**：
- `io.newInputFile(task.file().path().toString())` → `io.newInputFile(task.file().location())`。
- 异常消息 `"Cannot read %s file: %s", ..., task.file().path()` → `task.file().location()`。

### `data/src/test/java/org/apache/iceberg/data/DeleteReadTests.java`

**修改目的**：删除读取测试中构造位置删除的 `Pair<CharSequence, Long>` 时使用 `location()`。

**工作逻辑**：把所有 `Pair.of(dataFile.path(), 0L)` 等替换为 `Pair.of(dataFile.location(), 0L)`，保持位置删除记录指向数据文件的位置。

### `data/src/test/java/org/apache/iceberg/data/TestDataFileIndexStatsFilters.java`

**修改目的**：数据文件索引统计过滤测试改用 `location()`，并修正测试方法名。

**工作逻辑**：
- 测试方法 `testPositionDeletePlanningPath` 重命名为 `testPositionDeletePlanninglocation`（注意：方法名采用小写驼峰，新名仅把 `Path` 改为 `location`，与字段命名风格一致）。
- 所有 `dataFile.path()` → `dataFile.location()`，包括 `Pair.of(dataFile.path(), 0L)`、`dataFileWithEvenRecords.path()`、`task.file().path().toString().equals(file.path().toString())` → `task.file().location().toString().equals(file.location().toString())`、`actualDeletePaths.contains(expectedDeleteFile.path())` → `.location()`。

### `core/src/test/java/org/apache/iceberg/io/TestAppenderFactory.java`、`TestFileWriterFactory.java`、`TestGenericSortedPosDeleteWriter.java`、`TestPartitioningWriters.java`、`TestPositionDeltaWriters.java`

**修改目的**：IO 写入器测试中文件路径断言统一改用 `location()`。

**工作逻辑**：这些测试类在断言写入文件的位置、构建位置删除 Pair 等场景下，把 `file.path()` / `file.path().toString()` 替换为 `file.location()`。例如 `TestPartitioningWriters` 有 58 处变更，是改动最多的文件，主要因为按分区写入测试中大量比较和收集文件路径。

### `delta/src/test/java/org/apache/iceberg/delta/TestSnapshotDeltaLakeTable.java`、`BaseSnapshotDeltaLakeTableAction.java`

**修改目的**：Delta Lake 表快照测试与基础 Action 中文件路径访问改用 `location()`。

### `spark/v3.4/spark-extensions/.../TestExpireSnapshotsProcedure.java`、`TestRemoveOrphanFilesProcedure.java`、`TestRewritePositionDeleteFiles.java`、`TestUpdate.java`

**修改目的**：Spark 3.4 扩展测试中断言文件路径改用 `location()`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java`、`RemoveDanglingDeletesSparkAction.java`

**修改目的**：Spark Action 在记录或处理文件时改用 `location()`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/` 下多个 Reader 类

**修改目的**：Spark 数据源读取器（`BaseReader`、`BatchDataReader`、`ChangelogRowReader`、`EqualityDeleteRowReader`、`PositionDeletesRowReader`、`RowDataReader`、`SparkCleanupUtil`、`SparkCopyOnWriteScan`）在加载 InputFile、日志、清理逻辑中使用 `location()`。

**工作逻辑**：例如 `BaseReader` 中加载删除文件 InputFile：`io.newInputFile(deleteFile.path().toString())` → `io.newInputFile(deleteFile.location())`；`SparkCleanupUtil` 在记录待清理文件时用 `location()` 替代 `path()`。

### `spark/v3.4/spark/src/test/.../TaskCheckHelper.java`、`ValidationHelpers.java`、`TestBase.java`、`TestSparkExecutorCache.java`

**修改目的**：Spark 测试辅助类与基类中的文件路径比较改用 `location()`。

### `spark/v3.4/spark/src/test/.../actions/TestDeleteReachableFilesAction.java`、`TestExpireSnapshotsAction.java`

**修改目的**：Spark Action 测试中收集可达文件、过期快照文件时使用 `location()`。`TestExpireSnapshotsAction` 有 32 处变更，涉及大量文件路径集合比较。

### `spark/v3.4/spark/src/test/.../source/` 下多个测试类

**修改目的**：`TestBaseReader`、`TestCompressionSettings`、`TestDataFrameWrites`、`TestDataSourceOptions`、`TestPositionDeletesTable`（73 处变更，最多）、`TestRuntimeFiltering`、`TestSparkDataFile`、`TestSparkReaderDeletes` 等测试在断言文件位置、构建删除记录、验证读取结果时统一改用 `location()`。

**工作逻辑（以 `TestPositionDeletesTable` 为例）**：该测试覆盖位置删除表的多种读写场景，大量使用 `dataFile.path()` 构建位置删除 Pair 和断言，本次全部替换为 `dataFile.location()`，因此变更行数最多（73 处）。

## 小结

- **成效**：完成 API、Arrow、Core、Data、Spark 多模块对 `ContentFile#path()` deprecated API 的清理，统一改用 `location()`；为后续主版本移除 `path()` 铺路，同时消除大量 deprecated 警告。
- **影响范围**：48 个文件、237 处新增、227 处删除；纯机械替换，无语义变化；既有行为完全保留，仅 API 命名层面的迁移。
- **回迁到 1.4.x 的注意事项**：
  - 该改动是 API 迁移，不引入新功能或 bug 修复，回迁价值在于让 1.4.x 代码与 main 保持一致、便于后续从 1.4.x 升级到 1.5+ 时不再需要做这次大规模替换。
  - 1.4.x 中 `path()` 是否已被标记 deprecated 取决于 1.4.x 当时的 API 状态。若 1.4.x 中 `path()` 仍非 deprecated，则回迁是无害的同义替换；若已 deprecated，回迁可直接消除警告。
  - 由于改动量大（48 文件）、纯机械替换，回迁时冲突风险低，但需整体回迁而非部分，避免代码风格不一致。建议作为低优先级清理批量 cherry-pick。
  - 注意 `TestDataFileIndexStatsFilters` 中测试方法 `testPositionDeletePlanningPath` 重命名为 `testPositionDeletePlanninglocation`，回迁时需同步重命名，否则该测试无法被构建工具按方法名识别（虽然 JUnit 不依赖方法名匹配，但重命名保持一致性更佳）。
