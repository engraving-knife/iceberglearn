# 提交 2499：Spark 3.5, 3.4: Add configuration to disable executor cache for delete files (#13817)

## 提交信息

- **序号**：2499 / 4088
- **哈希**：ed7330b0675f605a0256f4c13eb6e4319bfe713a
- **短哈希**：ed7330b06
- **日期**：2025-08-14 12:51:14 -0700
- **作者**：Anurag Mantripragada
- **提交说明**：Spark 3.5, 3.4: Add configuration to disable executor cache for delete files (#13817)
- **PR/Issue**：#13817

## 总体目的

本提交是提交 2496 的 backport，将"禁用删除文件 executor 缓存"的功能从 Spark 4.0 移植到 Spark 3.5 和 3.4 版本。

在 Spark 4.0 中首次引入了 `spark.sql.iceberg.executor-cache.delete-files.enabled` 配置项，允许用户单独控制删除文件的缓存行为。为了保证三个 Spark 版本（3.4、3.5、4.0）在功能上的一致性，本提交将相同的功能和代码结构同步到 Spark 3.5 和 3.4 分支。

## 如何达成设计目的

设计思路与提交 2496 完全一致，关键设计点包括：

1. **新增配置项**：在两个版本的 `SparkSQLProperties` 中定义 `EXECUTOR_CACHE_DELETE_FILES_ENABLED` 及默认值 `true`。
2. **读取配置**：在 `SparkReadConf` 中新增 `cacheDeleteFilesOnExecutors()` 方法。
3. **传递配置**：通过 `SparkBatch` -> `SparkInputPartition` -> 各 Reader 构造函数链传递。
4. **条件化 DeleteLoader**：在 `BaseReader` 中根据配置选择 `CachingDeleteLoader` 或 `BaseDeleteLoader`。
5. **测试覆盖**：在各版本的 `TestSparkExecutorCache` 中新增配置验证和功能验证测试。

## 修改详情

### Spark 3.5 分支文件

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+5/-0 lines)
**修改目的**：定义配置项。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+12/-0 lines)
**修改目的**：新增 `cacheDeleteFilesOnExecutors()` 读取方法。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java` (+10/-4 lines)
**修改目的**：新增 `cacheDeleteFilesOnExecutors` 字段和条件化的 `newDeleteLoader()` 逻辑。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java` (+4/-2 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseRowReader.java` (+4/-2 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BatchDataReader.java` (+11/-5 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/ChangelogRowReader.java` (+6/-3 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/EqualityDeleteRowReader.java` (+3/-2 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/PositionDeletesRowReader.java` (+6/-3 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/RowDataReader.java` (+6/-3 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (+4/-1 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkInputPartition.java` (+7/-2 lines)
#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkMicroBatchStream.java` (+3/-2 lines)
**修改目的**：在构造函数链中透传 `cacheDeleteFilesOnExecutors` 参数。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkExecutorCache.java` (+134/-0 lines)
**修改目的**：新增配置验证和 DELETE/UPDATE/MERGE 操作下禁用缓存的测试。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestBaseReader.java` (+1/-1 lines)
#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestChangelogReader.java` (+5/-3 lines)
#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesReader.java` (+6/-3 lines)
#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java` (+2/-1 lines)
**修改目的**：适配 Reader 构造函数签名变更。

### Spark 3.4 分支文件

与 Spark 3.5 完全相同的修改，涉及相同的一组文件（37 个文件，+534/-66 lines）。Spark 3.4 的测试文件比 3.5 少一些增量（+33 行 vs +134 行），因为部分测试场景在 3.4 中已有覆盖。

## 总结

本提交是 Spark 4.0 功能的 backport，确保了三个 Spark 版本在 executor 缓存控制功能上的一致性。这使得使用 Spark 3.4 和 3.5 的用户也能享受细粒度的删除文件缓存开关，便于在大删除文件场景下优化内存使用。代码结构和测试与 4.0 版本保持一致。
