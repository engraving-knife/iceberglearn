# 提交 2536：Spark 3.5, 3.4: Disable executor cache for delete files in RewriteDataFilesSparkAction (#13868)

## 提交信息

- **序号**：2536 / 4088
- **哈希**：2012f661a19e613abec2ef0024d7b340c9620f14
- **短哈希**：2012f661a
- **日期**：2025-08-20 15:25:23 -0700
- **作者**：Anurag Mantripragada
- **提交说明**：Spark 3.5, 3.4: Disable executor cache for delete files in RewriteDataFilesSparkAction (#13868)
- **PR/Issue**：#13868

## 总体目的

`RewriteDataFilesSparkAction` 在执行数据文件重写时，每个分区通常单独被一个 task 处理。Spark 4.0 之前已经显式关闭了"executor 上 delete files 缓存"以避免无意义缓存与潜在连接池问题，但 Spark 3.4 与 3.5 的对应实现没有同步这一行为，导致这两个版本上重写时仍可能启用 executor 端 delete file 缓存。

虽然重写过程中每个分区的 delete files 被独立读取，缓存复用收益有限，但当用户配置 `target-spec` 让多分区数据被合并到一起重写时，缓存可能在多个 task 间共享连接池资源，反而引发连接池耗尽/超时问题。为统一行为并规避风险，本提交把 Spark 3.4 与 3.5 的 `RewriteDataFilesSparkAction` 构造函数中也加上 `spark().conf().set(SparkSQLProperties.EXECUTOR_CACHE_DELETE_FILES_ENABLED, "false")`，与 4.0 对齐。同时把 4.0 的注释更新得更准确，说明即便合并多分区也仍然关闭缓存以规避连接池问题。

测试侧为 3.4/3.5 各新增一个 `testExecutorCacheForDeleteFilesDisabled`，验证构造 action 后通过 `SparkReadConf` 读到的 `cacheDeleteFilesOnExecutors()` 为 false。

## 如何达成设计目的

- 在 Spark 3.4 与 3.5 的 `RewriteDataFilesSparkAction` 构造函数中紧跟关闭 AQE 之后，设置 `EXECUTOR_CACHE_DELETE_FILES_ENABLED=false`，并加注释说明动机（每个分区独立重写、即便跨分区合并也关闭以避免连接池问题）。
- 更新 Spark 4.0 同位置注释，使其与新加注释一致，强调"avoid connection pool issues"。
- 测试通过 `SparkReadConf.cacheDeleteFilesOnExecutors()` 反查配置生效。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+5)

**修改目的**：在 3.4 关闭 executor delete file 缓存。

**工作逻辑**：构造函数中 `spark().conf().set(SparkSQLProperties.EXECUTOR_CACHE_DELETE_FILES_ENABLED, "false")`，新增 `SparkSQLProperties` import。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+13)

**修改目的**：3.4 测试验证配置生效。

**工作逻辑**：构造 action 后用 `SparkReadConf` 读取并断言 `cacheDeleteFilesOnExecutors()` 为 false。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+5)

**修改目的**：在 3.5 关闭 executor delete file 缓存。

**工作逻辑**：与 3.4 完全相同。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java` (+13)

**修改目的**：3.5 测试验证配置生效。

**工作逻辑**：同 3.4 测试。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+3/-2)

**修改目的**：更新 4.0 注释。

**工作逻辑**：把原注释改为与 3.4/3.5 一致的措辞，强调跨分区合并仍关闭缓存以避免连接池问题。代码逻辑不变（4.0 之前已设置 false）。

## 总结

将 Spark 4.0 已有的"关闭 executor delete file 缓存"行为同步到 Spark 3.4 与 3.5 的 `RewriteDataFilesSparkAction`，统一三个版本的行为，避免重写时因缓存引发连接池问题。配套测试验证配置确实被关闭，并更新 4.0 注释使其更准确。
