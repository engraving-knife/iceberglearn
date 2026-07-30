# 提交 2505：Spark 4.0: Disable executor cache for delete files in RewriteDataFilesSparkAction (#13820)

## 提交信息

- **序号**：2505 / 4088
- **哈希**：24447bfbec9c1ab4acb64b2b98e3e58d15d16006
- **短哈希**：24447bfbe
- **日期**：2025-08-15 14:49:54 -0500
- **作者**：Anurag Mantripragada
- **提交说明**：Spark 4.0: Disable executor cache for delete files in RewriteDataFilesSparkAction (#13820)
- **PR/Issue**：#13820

## 总体目的

本提交是提交 2496（Add configuration to disable executor cache for delete files）的实际应用。在 `RewriteDataFilesSparkAction` 的构造函数中，自动将 `spark.sql.iceberg.executor-cache.delete-files.enabled` 设置为 `false`。

数据文件重写操作（RewriteDataFiles）会逐个分区地重写数据文件。由于每个分区的重写是独立进行的，删除文件在不同分区间的缓存复用并不提供任何收益。相反，缓存删除文件只会占用 executor 内存，可能导致内存压力。因此，在数据重写场景下禁用删除文件缓存是更优的选择。

本提交利用了提交 2496 引入的配置项，在 `RewriteDataFilesSparkAction` 构造时自动设置该配置，无需用户手动配置。这与同一构造函数中已禁用 Adaptive Query Execution（AQE）的做法一致，都是为重写操作设置最优的执行环境。

## 如何达成设计目的

在 `RewriteDataFilesSparkAction` 构造函数中，紧接禁用 AQE 的语句之后，添加一行设置 `SparkSQLProperties.EXECUTOR_CACHE_DELETE_FILES_ENABLED` 为 `"false"`。同时新增测试验证配置被正确设置。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+4/-0 lines)

**修改目的**：在数据重写操作中自动禁用删除文件缓存。

**工作逻辑**：
- 新增 `SparkSQLProperties` 的 import。
- 在构造函数中，紧接 `spark().conf().set(SQLConf.ADAPTIVE_EXECUTION_ENABLED().key, false)` 之后，添加 `spark().conf().set(SparkSQLProperties.EXECUTOR_CACHE_DELETE_FILES_ENABLED, "false")`。
- 注释说明原因：每个分区被单独重写，跨分区缓存删除文件不提供收益。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+13/-0 lines)

**修改目的**：验证重写操作中删除文件缓存被禁用。

**工作逻辑**：
- 新增 `testExecutorCacheForDeleteFilesDisabled` 测试方法。
- 创建分区表，获取 `RewriteDataFilesSparkAction` 实例。
- 通过 `SparkReadConf` 读取配置，断言 `cacheDeleteFilesOnExecutors()` 返回 `false`。

## 总结

本提交是 executor 缓存控制功能的实际应用场景。通过在数据重写操作中自动禁用删除文件缓存，避免了不必要的内存占用，优化了重写操作的资源使用。这体现了提交 2496 引入配置项的实际价值，也为后续类似场景的优化提供了模式参考。
