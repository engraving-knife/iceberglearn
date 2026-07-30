# 提交 0240：Spark 3.5: Rework DeleteFileIndexBenchmark (#9165)

## 提交信息

- **序号**：0240 / 4088
- **哈希**：feeaa8c73034f60f1ab55d0db763fe13fa3c229f
- **短哈希**：feeaa8c73
- **日期**：2023-12-08 01:22:36 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Rework DeleteFileIndexBenchmark (#9165)
- **PR/Issue**：#9165

## 总体目的

本提交重写 Spark 3.5 下的 `DeleteFileIndexBenchmark` JMH 基准测试，目标是让基准更真实、更轻量、更可复用。原基准为了构造 50 个分区、每分区 5 万个数据文件 + 100 个删除文件的规模，采用了一条"借真实 Spark 写入再复制"的迂回路径：先用 `RandomData.generateSpark` 生成少量真实数据文件并 append 进表，再用 `loadAddedDataFile()`/`loadAddedDeleteFile()` 取出一份真实 `DataFile`/`DeleteFile`，然后在内存里通过 `DataFiles.builder(spec).copy(dataFile).withPath(...)` 制造 5 万份"副本"（仅 path 不同、其余元数据完全相同）来撑规模。这种做法有几个问题：(1) 副本文件物理上不存在，依赖 Iceberg 不校验文件存在性；(2) 副本 metrics 完全相同，不能反映真实分布，可能让 `DeleteFileIndex` 的某些优化路径（依赖 metrics 上下界）行为失真；(3) 必须启动完整 Spark SQL 写入路径，构建数据慢且重；(4) 逻辑混在 benchmark 类内部，无法被其它测试复用。

本提交引入一个新的测试工具类 [`FileGenerationUtil`](core/src/test/java/org/apache/iceberg/FileGenerationUtil.java)，用纯内存方式按 schema 随机生成 `DataFile` 与 `DeleteFile`（含合理随机 metrics），让 benchmark 直接 `table.newRowDelta().addRows(...).addDeletes(...).commit()` 提交这些"合成"文件，去掉所有 Spark DataFrame 写入与 `copy` 复制逻辑。同时把 `FileGenerationUtil` 放在 `core/src/test/java/org/apache/iceberg/` 下并通过 `testArtifacts` 配置暴露给 spark 模块，使其成为跨模块可复用的测试资产。这是为后续 position delete / equality delete 性能基准打基础的基础设施改动。

## 如何达成设计目的

整体设计是"抽取工具类 + 简化 benchmark"两步：(1) 新建 `FileGenerationUtil`，提供 `generateDataFile`、`generatePositionDeleteFile`（两个重载：按 partition、按 dataFile）、`generateFileName`、`generateRandomMetrics`、`generatePositionDeleteMetrics`（两个重载）等静态方法，使用 `ThreadLocalRandom` 生成随机但合规的文件元数据；(2) 重写 `DeleteFileIndexBenchmark.initDataAndDeletes`，去掉 `RandomData`/`JavaRDD`/`Dataset<Row>`/`loadAddedDataFile`/`copy` 一整套，改为对每个分区直接循环调用 `FileGenerationUtil.generateDataFile` 与 `generatePositionDeleteFile` 累积进一个 `RowDelta` 后一次 commit；(3) 在 `spark/v3.5/build.gradle` 的 spark-extensions 项目中加一行 `testImplementation project(path: ':iceberg-core', configuration: 'testArtifacts')`，让 benchmark 能引用 `FileGenerationUtil`。

## 修改详情

### `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java`（新增，191 行）

**修改目的**：提供跨模块可复用的合成 `DataFile`/`DeleteFile` 生成器，供 benchmark 与测试使用，避免每次都走真实 Spark 写入或手工 `copy` 复制。

**工作逻辑**：工具类为 final + private 构造，全部静态方法，使用 `ThreadLocalRandom.current()` 保证线程安全。核心方法：

- `generateDataFile(Table table, StructLike partition)`：通过 `table.locationProvider().newDataLocation(spec, partition, generateFileName())` 生成合规路径，调用 `generateRandomMetrics(schema)` 按 schema 各列生成 metrics，最后 `DataFiles.builder(spec).withPath(...).withPartition(partition).withFileSizeInBytes(generateFileSize()).withFormat(PARQUET).withMetrics(metrics).build()`。
- `generatePositionDeleteFile(Table table, StructLike partition)`：用 `FileMetadata.deleteFileBuilder(spec).ofPositionDeletes()` 构建，metrics 走 `generatePositionDeleteMetrics()`（无 dataFile 参照版本，仅 column sizes，无 bounds）。
- `generatePositionDeleteFile(Table table, DataFile dataFile)`：关键变体，调用 `generatePositionDeleteMetrics(dataFile)`，使生成的 position delete 文件的 `file_path` 列（`MetadataColumns.DELETE_FILE_PATH`）的 lower/upper bound 都精确等于 `dataFile.path()`。这模拟了"删除文件实际指向某个数据文件"的真实场景，让 `DeleteFileIndex` 的按 path 匹配逻辑能在 benchmark 中真实生效，比纯随机 bounds 更有代表性。
- `generateFileName()`：模仿 `OutputFileFactory` 的命名格式 `%d-%d-%s-%d.parquet`（partitionId-taskId-operationId-fileCount），让生成的文件名在分布上接近真实写入。
- `generateRandomMetrics(Schema schema)`：对 schema 每列填 columnSizes/valueCounts/nullValueCounts/nanValueCounts（0-5 随机）/lowerBounds/upperBounds（16 字节随机），行数 100000-101000 随机。
- `generatePositionDeleteMetrics(DataFile dataFile)`：仅遍历 `DeleteSchemaUtil.pathPosSchema()` 的列（即 position delete 的 `file_path` + `pos`），对 `file_path` 字段用 `Conversions.toByteBuffer(StringType, dataFile.path())` 设置 bound，其余列只填 columnSizes。
- 私有 helper `generateRowCount`/`generateColumnSize`/`generateValueCount`/`generateFileSize` 提供各字段的随机区间。

### `spark/v3.5/build.gradle`

**修改目的**：让 spark-extensions 子项目能引用 `core` 模块的测试代码（即 `FileGenerationUtil`）。

**工作逻辑**：在 `project(":iceberg-spark:iceberg-spark-extensions-3.5_...")` 的依赖块中新增一行 `testImplementation project(path: ':iceberg-core', configuration: 'testArtifacts')`。`testArtifacts` 是 Iceberg 各模块约定暴露测试类与测试资源的 Gradle configuration，此行使 `FileGenerationUtil` 从 core 测试源码可见于 spark-extensions 的 JMH 源集与测试源集。

### `spark/v3.5/spark-extensions/src/jmh/java/org/apache/iceberg/DeleteFileIndexBenchmark.java`

**修改目的**：用 `FileGenerationUtil` 重写数据/删除文件构造逻辑，去掉 Spark DataFrame 写入与 `copy` 复制，让基准更轻量、更真实。

**工作逻辑**：
- 常量精简：删除 `NUM_REAL_DATA_FILES_PER_PARTITION`（25）、`NUM_REPLICA_DATA_FILES_PER_PARTITION`（50_000）、`NUM_ROWS_PER_DATA_FILE`（500），合并为单个 `NUM_DATA_FILES_PER_PARTITION = 50_000`。原方案是"写 25 个真实文件 + 复制 5 万份副本"，新方案是"直接生成 5 万个合成数据文件"，规模对齐但语义更干净。
- `initDataAndDeletes` 重写：去掉了原 `randomDataDF`/`appendAsFile`/`loadAddedDataFile`/`loadAddedDeleteFile` 以及 `append` 提交副本的整套逻辑。新实现为：

  ```java
  for (int partitionOrdinal = 0; partitionOrdinal < NUM_PARTITIONS; partitionOrdinal++) {
    StructLike partition = TestHelpers.Row.of(partitionOrdinal);
    RowDelta rowDelta = table.newRowDelta();
    for (int fileOrdinal = 0; fileOrdinal < NUM_DATA_FILES_PER_PARTITION; fileOrdinal++) {
      DataFile dataFile = FileGenerationUtil.generateDataFile(table, partition);
      rowDelta.addRows(dataFile);
    }
    for (int fileOrdinal = 0; fileOrdinal < NUM_DELETE_FILES_PER_PARTITION; fileOrdinal++) {
      DeleteFile deleteFile = FileGenerationUtil.generatePositionDeleteFile(table, partition);
      rowDelta.addDeletes(deleteFile);
    }
    rowDelta.commit();
  }
  ```

  即每分区一个 `RowDelta`，内含 5 万数据文件 + 100 删除文件，一次 commit。注意 benchmark 这里用的是无 dataFile 参照的 `generatePositionDeleteFile(table, partition)`，bounds 为空，属于"均匀随机"压力模型。
- 删除大量不再使用的 import：`lit`、`LocationProvider`、`Iterables`、`SparkSchemaUtil`、`RandomData`、`JavaRDD`、`JavaSparkContext`、`Dataset`、`Row`、`InternalRow`、`StructType`，以及私有方法 `loadAddedDataFile`、`loadAddedDeleteFile`、`appendAsFile`、`randomDataDF`。`initDataAndDeletes` 的 `throws NoSuchTableException` 也被去掉（不再调用 Spark 写入 API）。`Setup`/`TearDown`/`@Benchmark` 方法本身未改动，仍是构建 `DeleteFileIndex` 并对每个数据文件做 `forDataFile`/`hasPosDeletes` 之类的查询。

整体上 benchmark 类从 ~170 行降到 ~90 行，专注度回到"测 `DeleteFileIndex` 构建与查询性能"本身，数据准备细节外包给 `FileGenerationUtil`。

## 小结

本提交通过新增跨模块可复用的测试工具类 `FileGenerationUtil` 并用它重写 `DeleteFileIndexBenchmark` 的数据准备逻辑，去除了原 benchmark 对 Spark DataFrame 写入与"复制真实文件"的依赖，使基准更轻量、更真实，并为后续删除相关基准与测试提供了通用的合成文件生成基础设施。
