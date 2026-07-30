# 提交 2496：Spark 4.0: Add configuration to disable executor cache for delete files (#12893)

## 提交信息

- **序号**：2496 / 4088
- **哈希**：efbfb7ef9addeb33e72208c927936e50b92d3357
- **短哈希**：efbfb7ef9
- **日期**：2025-08-13 13:09:28 -0500
- **作者**：Anurag Mantripragada
- **提交说明**：Spark 4.0: Add configuration to disable executor cache for delete files (#12893)
- **PR/Issue**：#12893

## 总体目的

本提交为 Spark 4.0 引入了针对删除文件（delete files）的 executor 缓存开关配置。Iceberg 在 Spark executor 上读取数据时，会通过 `SparkExecutorCache` 缓存输入文件，包括数据文件和删除文件。删除文件的缓存是在执行 DELETE、UPDATE、MERGE 等行级操作时用于加速等值删除和位置删除的读取。

然而，在某些场景下，缓存删除文件可能带来问题：例如删除文件数量很大时会导致 executor 内存压力，或者在并发任务场景下缓存可能不命中反而增加开销。本提交新增了配置项 `spark.sql.iceberg.executor-cache.delete-files.enabled`（默认为 `true` 保持向后兼容），允许用户在需要时单独禁用删除文件的 executor 缓存，而不影响数据文件的缓存。

该配置仅在 `executor-cache.enabled` 为 `true` 时生效，即只有在全局缓存开启的情况下，删除文件缓存开关才有意义。后续提交 2499 将此功能 backport 到 Spark 3.5 和 3.4，提交 2505 将其在 `RewriteDataFilesSparkAction` 中应用。

## 如何达成设计目的

关键设计点：

1. **新增配置项**：在 `SparkSQLProperties` 中定义 `EXECUTOR_CACHE_DELETE_FILES_ENABLED` 及其默认值 `true`。
2. **读取配置**：在 `SparkReadConf` 中新增 `cacheDeleteFilesOnExecutors()` 方法，组合全局缓存开关和删除文件缓存开关。
3. **传递配置**：通过 `SparkBatch` -> `SparkInputPartition` -> 各 Reader 构造函数链，将配置传递到 executor 端。
4. **条件化 DeleteLoader**：在 `BaseReader` 中，根据配置决定使用 `CachingDeleteLoader`（缓存）还是 `BaseDeleteLoader`（不缓存）。
5. **全面测试**：新增多个测试验证配置行为和禁用缓存后的正确性。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java` (+5/-0 lines)

**修改目的**：定义新的配置项键名和默认值。

**工作逻辑**：新增 `EXECUTOR_CACHE_DELETE_FILES_ENABLED = "spark.sql.iceberg.executor-cache.delete-files.enabled"` 和 `EXECUTOR_CACHE_DELETE_FILES_ENABLED_DEFAULT = true`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+12/-0 lines)

**修改目的**：提供读取删除文件缓存配置的方法。

**工作逻辑**：`cacheDeleteFilesOnExecutors()` 方法返回 `executorCacheEnabled() && cacheDeleteFilesOnExecutorsInternal()`，即全局缓存和删除文件缓存都开启时才为 true。`cacheDeleteFilesOnExecutorsInternal()` 通过 `confParser` 解析 session 级配置。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/BaseReader.java` (+10/-4 lines)

**修改目的**：根据配置决定使用缓存型还是非缓存型的 DeleteLoader。

**工作逻辑**：
- 构造函数新增 `cacheDeleteFilesOnExecutors` 参数并保存为字段。
- 新增 `cacheDeleteFilesOnExecutors()` 受保护方法。
- 在 `newDeleteLoader()` 中，当 `cacheDeleteFilesOnExecutors` 为 true 时返回 `CachingDeleteLoader`，否则返回 `BaseDeleteLoader`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatch.java` (+5/-1 lines)

**修改目的**：从 `SparkReadConf` 读取配置并传递给 `SparkInputPartition`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkInputPartition.java` (+9/-1 lines)

**修改目的**：存储配置并通过 `cacheDeleteFilesOnExecutors()` 方法暴露给 Reader 构造。

### 其他 Reader 文件 (BatchDataReader, BaseBatchReader, BaseRowReader, ChangelogRowReader, EqualityDeleteRowReader, PositionDeletesRowReader, RowDataReader, SparkMicroBatchStream)

**修改目的**：在各 Reader 的构造函数链中透传 `cacheDeleteFilesOnExecutors` 参数，确保最终传递到 `BaseReader`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestSparkExecutorCache.java` (+167/-0 lines)

**修改目的**：测试新配置的行为。

**工作逻辑**：
- `testDeleteFilesCacheDisabledConfig`：验证三种配置组合下 `cacheDeleteFilesOnExecutors()` 的返回值。
- `checkDeleteWithDeleteFilesCacheDisabled`：验证禁用缓存后 DELETE 操作仍然正确，且删除文件的流打开次数大于缓存场景。
- `checkUpdateWithDeleteFilesCacheDisabled`：验证 UPDATE 操作。
- `checkMergeWithDeleteFilesCacheDisabled`：验证 MERGE 操作。
- 通过 `streamCount` 断言删除文件被多次打开（证明缓存被禁用），同时验证结果数据正确性。

### 测试辅助文件修改 (TestBaseReader, TestChangelogReader, TestPositionDeletesReader, TestSparkReaderDeletes)

**修改目的**：适配 Reader 构造函数签名变更，传递新增参数。

## 总结

本提交为 Spark 4.0 的 executor 缓存机制增加了细粒度控制，允许用户单独禁用删除文件缓存。这对于处理大删除文件场景或调试缓存相关问题非常有用。改动涉及完整的配置传递链路和充分的测试覆盖，是一个设计完善的特性增强。后续提交将其 backport 到更早的 Spark 版本并应用于数据重写操作。
