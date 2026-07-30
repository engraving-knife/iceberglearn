# 提交 1480：Core, Flink, Spark, KafkaConnect: Remove usage of deprecated path API (#11744)

## 提交信息

- **序号**：1480 / 4088
- **哈希**：da53495bc1bb52db37cdd1ced5c2377001c9d482
- **短哈希**：da53495bc
- **日期**：2024-12-11（Wed Dec 11 10:04:59 2024 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Core, Flink, Spark, KafkaConnect: Remove usage of deprecated path API (#11744)
- **PR/Issue**：#11744

## 总体目的

Iceberg 的 `ContentFile` 接口（`DataFile`、`DeleteFile` 等的父接口）历史上用 `CharSequence path()` 返回文件路径。在 1.7.0 中，该方法被标记为 `@Deprecated since 1.7.0, will be removed in 2.0.0; use #location() instead.`，并新增了 default 方法 `String location()`，默认实现 `return path().toString()`。也就是说：

- `path()` 返回 `CharSequence`（通常是 `String`，但接口允许其他实现），并在 2.0.0 会被移除；
- `location()` 返回 `String`，是新的推荐入口。

仓库自身代码中仍大量使用 `file.path()` 或 `file.path().toString()`，这些用法在 IDE 中会显示删除线警告，且会在未来 2.0.0 移除 `path()` 时编译失败。本提交的目的就是把仓库内所有内部代码中的 `.path()` / `.path().toString()` 调用替换为 `.location()`，让代码库自身先告别 deprecated API，给外部用户做示范，并降低未来 2.0.0 移除 `path()` 时的工作量。

涉及模块：Core、Flink（v1.18/v1.19/v1.20）、Spark（v3.3/v3.4/v3.5）、Kafka Connect、MR、Hive、ORC 测试、Parquet Benchmark、Spark extensions 测试等多个子模块，共 126 个文件。

## 如何达成设计目的

通过遍历仓库内所有调用 `.path()` 或 `.path().toString()` 的位置，逐一替换为 `.location()`。这是一次大规模机械式重构（mechanical refactor），替换规则有几种典型形态：

1. `file.path().toString()` → `file.location()`（直接调用，因为 `location()` 已返回 `String`，不再需要 `toString()`）；
2. `file.path()`（用作 `CharSequence` 上下文）→ `file.location()`（隐式 String→CharSequence 自动转换）；
3. `dataFile -> dataFile.path().toString()`（lambda）→ `ContentFile::location`（方法引用，更简洁）；
4. 错误日志/异常消息中的 `file.path()` → `file.location()`。

替换不修改 `ContentFile` 接口本身（`path()` 仍在），只修改调用方。这是"先消除内部使用，再在 2.0.0 移除 API"的标准两步走的第一步。

## 修改详情

由于改动文件众多（126 个），下面按模块归纳典型改动模式，并对几处代表性文件做详细说明。

### Core 模块

1. **`core/src/main/java/org/apache/iceberg/SnapshotProducer.java`**：内部类 `DeleteFileImpl`（写 delete file 时使用的实现）的 `path()` 方法原本 `return deleteFile.path();`，改为 `return deleteFile.location();`。注意这里实现的是 `ContentFile.path()` 接口方法（仍要保留以维持接口契约），但内部委托从 `path()` 改为 `location()`——这是合理的，因为 `deleteFile` 的 `location()` 默认实现就是 `path().toString()`，但显式调用 `location()` 能确保即使 `deleteFile` 的 `path()` 被进一步重写也走 `location()` 路径。

2. **`core/src/main/java/org/apache/iceberg/V1Metadata.java` / `V2Metadata.java` / `V3Metadata.java`**：每个文件中有一个 `GenericDataFile` / `GenericDeleteFile` 风格的 wrapper 类，其 `path()` 方法原本 `return wrapped.path();`，改为 `return wrapped.location();`。理由同上。

3. **`core/src/test/java/org/apache/iceberg/TestRewriteManifests.java`**、**`core/src/test/java/org/apache/iceberg/io/TestTaskEqualityDeltaWriter.java`** 等测试：把测试中的 `file.path()` 改为 `file.location()`。

### Flink 模块（v1.18 / v1.19 / v1.20 各一份）

每个 Flink 版本下都做了相同的修改：

1. **`flink/vX.YZ/flink/src/main/java/org/apache/iceberg/flink/source/RowDataFileScanTaskReader.java`**：
   ```java
-  super(task.file().path().toString(), task.deletes(), tableSchema, requestedSchema);
+  super(task.file().location(), task.deletes(), tableSchema, requestedSchema);
   ```
   父类构造函数接受 `String`，所以 `path().toString()` 完全可以替换为 `location()`。

2. **`flink/vX.YZ/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplit.java`**：在 `toString()` 中：
   ```java
-  .add("file", fileScanTask.file().path().toString())
+  .add("file", fileScanTask.file().location())
   ```
   `MoreObjects.toStringHelper.add(String, Object)` 接受 Object，String 直接传入即可。

3. **测试文件**（`TestBucketPartitionerFlinkIcebergSink.java`、`TestFlinkIcebergSinkV2.java`、`TestTaskWriters.java`、`TestContinuousSplitPlannerImpl.java`、`TestFlinkCatalogTable.java`、`TestHelpers.java`、`TestRewriteDataFilesAction.java`、`TestIcebergSourceSplitSerializer.java` 等）：批量替换 `.path().toString()` → `.location()` 或 `.path()` → `.location()`。

### Spark 模块（v3.3 / v3.4 / v3.5）

1. **`spark/vX.Y/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`**：多处把 `f.path().toString()` 的 lambda 改为方法引用 `ContentFile::location`：
   ```java
-  .createDataset(Lists.transform(files, f -> f.path().toString()), Encoders.STRING())
+  .createDataset(Lists.transform(files, ContentFile::location), Encoders.STRING())
   ```
   并新增 import `org.apache.iceberg.ContentFile`。这种"lambda → 方法引用"的简化是这次重构的副产品，让代码更紧凑。

2. **`BaseReader.java`、`BatchDataReader.java`、`ChangelogRowReader.java`、`EqualityDeleteRowReader.java`、`PositionDeletesRowReader.java`、`RowDataReader.java`、`SparkCleanupUtil.java`、`SparkCopyOnWriteScan.java`**（v3.3/v3.4 部分）：reader 类内部把 `file.path()` 改为 `file.location()`。

3. **`BaseSparkAction.java`、`RemoveDanglingDeletesSparkAction.java`**（v3.4）：action 类内部替换。

4. **测试文件**（`TaskCheckHelper.java`、`ValidationHelpers.java`、`SparkTestBase.java`、`TestExpireSnapshotsAction.java`、`TestRemoveDanglingDeleteAction.java`、`TestRewriteDataFilesAction.java`、`TestRewritePositionDeleteFilesAction.java`、`TestBaseReader.java`、`TestDataFrameWrites.java`、`TestDataSourceOptions.java`、`TestIcebergSourceTablesBase.java`、`TestPositionDeletesTable.java`、`TestRuntimeFiltering.java`、`TestSparkDataFile.java`、`TestSparkReaderDeletes.java`、`TestCompressionSettings.java`、`TestSparkExecutorCache.java`、`TestDeleteReachableFilesAction.java` 等）：批量替换调用。某些测试涉及 spark extensions 的 procedure 测试（`TestExpireSnapshotsProcedure.java`、`TestRemoveOrphanFilesProcedure.java`、`TestRewritePositionDeleteFiles.java`、`TestUpdate.java`）也同步修改。

5. **JMH benchmark 文件**（`IcebergSourceParquetMultiDeleteFileBenchmark.java`、`IcebergSourceParquetPosDeleteBenchmark.java`、`IcebergSourceParquetWithUnrelatedDeleteBenchmark.java`）：benchmark 中也清理 deprecated 调用。

### Kafka Connect 模块

**`kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java`**：在收集待提交的 data/delete files 时：
```java
-  .filter(distinctByKey(dataFile -> dataFile.path().toString()))
+  .filter(distinctByKey(ContentFile::location))
```
这里 `distinctByKey` 接受 `Function<DataFile, ?>` 作为 key 提取器，原来用 lambda 提取 path 字符串，改为方法引用 `ContentFile::location`（DataFile 是 ContentFile 子接口，方法引用兼容）。需要新增 import `org.apache.iceberg.ContentFile`。

### MR 模块

1. **`mr/src/main/java/org/apache/iceberg/mr/hive/HiveIcebergOutputCommitter.java`**：abort 时删除文件：
   ```java
-  .run(file -> table.io().deleteFile(file.path().toString()));
+  .run(file -> table.io().deleteFile(file.location()));
   ```
   `io.deleteFile(String)` 接受 String，原来要 `.path().toString()`，现在 `.location()` 直接返回 String。

   同时日志从 `file.path()` 改为 `file.location()`。

2. **`mr/src/main/java/org/apache/iceberg/mr/hive/HiveIcebergRecordWriter.java`**：类似替换 `dataFile.path().toString()` → `dataFile.location()`。

3. **`mr/src/main/java/org/apache/iceberg/mr/mapreduce/IcebergInputFormat.java`**：
   ```java
-  EncryptedFiles.encryptedInput(io.newInputFile(file.path().toString()), file.keyMetadata())
+  EncryptedFiles.encryptedInput(io.newInputFile(file.location()), file.keyMetadata())
   ```
   以及异常消息中的 `file.path()` → `file.location()`。

### Hive 测试

**`hive/src/test/java/org/apache/iceberg/hive/HiveTableTest.java`**：测试中的 path 调用替换。

### 数据/其他测试

**`data/src/test/java/org/apache/iceberg/io/TestTaskEqualityDeltaWriter.java`**、**`data/src/test/java/org/apache/iceberg/data/TestDataFileIndexStatsFilters.java`**、**`mr/src/test/java/org/apache/iceberg/orc/TestOrcDataWriter.java`**：相关测试同步替换。

## 小结

- **成效**：仓库内部代码全面弃用 `ContentFile.path()` / `path().toString()`，统一改用 `location()`，126 个文件、535 处新增、533 处删除（基本是一一对应的替换）。代码库自身的 deprecated 警告大幅减少，并为未来 2.0.0 真正移除 `path()` 方法铺平道路。多处 lambda 顺带简化为 `ContentFile::location` 方法引用，代码更紧凑。
- **影响范围**：仅替换调用方，**不修改 `ContentFile` 接口本身**——`path()` 仍存在并仍可用，只是不再被仓库内部使用。对外部用户透明，无 API 破坏。
- **回迁到 1.4.x 的注意事项**：**视情况可选回迁，但通常不需要**。1.4.x 分支的 `ContentFile` 接口可能尚未引入 `location()` default 方法（`location()` 是 1.7.0 才加的）。如果 1.4.x 没有 `location()`，则本提交无法直接 cherry-pick——会把代码改成调用一个不存在的方法，导致编译失败。1.4.x 用户若想清理 deprecated 调用，应先确认 `location()` 是否存在；若不存在，应保持使用 `path()`，等升级到 1.7.0+ 后再考虑做类似清理。**结论：1.4.x 不要回迁**。
