# 提交 3074：Spark: Backport: Add Spark app name to env context for Spark v3.4, 3.5, 4.0 (#14981)

## 提交信息

- **序号**：3074 / 4088
- **哈希**：b07c1e570d5db28a937b3fc632ef2f2788932818
- **短哈希**：b07c1e570
- **日期**：2026-01-07
- **作者**：Varun Lakhyani
- **提交说明**：Spark: Backport: Add Spark app name to env context for Spark v3.4, 3.5, 4.0 (#14981)
- **PR/Issue**：#14981（回移自 #14976）

## 总体目的

本提交是提交 3072（PR #14976）的回移（backport），将"在 SparkCatalog 初始化时将 Spark 应用名称写入 `EnvironmentContext`"的改动从 `spark/v4.1` 同步到 `spark/v3.4`、`spark/v3.5`、`spark/v4.0`。Iceberg 的 Spark 适配模块同时维护多个 Spark 版本（v3.4、v3.5、v4.0、v4.1），新功能先在最新 v4.1 落地，再回移到仍受支持的旧版本，保证各版本行为一致。

被回移的源改动（详见 3072 分析）解决的问题是：`SparkCatalog` 初始化环境上下文时已写入 `ENGINE_NAME`、`ENGINE_VERSION`、`APP_ID`，但缺少 `APP_NAME`（Spark 应用名称）。`APP_ID` 是机器可读标识（如 `application_123456_0001`），而 `APP_NAME` 是用户设置的可读名称，写入快照摘要后便于审计和运维追踪快照来源。`CatalogProperties.APP_NAME` 常量已在 3072 中定义于 core 模块，各 Spark 版本共享，故回移只需在各版本的 `SparkCatalog` 和测试中新增一行。

回移的原因是使用 Spark 3.4/3.5/4.0 的生产作业同样需要快照摘要中包含可读的应用名称。若不回移，旧版本用户无法享受该追溯能力，与 v4.1 行为不一致。

## 如何达成设计目的

将 3072 对 `spark/v4.1` 的改动原样应用到 `spark/v3.4`、`spark/v3.5`、`spark/v4.0`：每个版本在 `SparkCatalog.initialize()` 中新增一行 `EnvironmentContext.put(CatalogProperties.APP_NAME, sparkSession.sparkContext().appName());`，并在两个重写过程测试中新增对 `APP_NAME` 键的断言。三个版本的改动完全对称，共 9 个文件（每版本 3 个）。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+1/-0 lines)

**修改目的**：在 v3.4 的 SparkCatalog 初始化时写入 Spark 应用名称。

**工作逻辑**：
在 `initialize()` 方法中 `EnvironmentContext.put(CatalogProperties.APP_ID, ...)` 之后新增 `EnvironmentContext.put(CatalogProperties.APP_NAME, sparkSession.sparkContext().appName());`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+1/-0 lines)

**修改目的**：验证 v3.4 数据文件重写快照摘要包含 `APP_NAME`。

**工作逻辑**：
在快照摘要断言链中 `.containsKey(CatalogProperties.APP_ID)` 之后新增 `.containsKey(CatalogProperties.APP_NAME)`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesProcedure.java` (+1/-0 lines)

**修改目的**：验证 v3.4 位置删除文件重写快照摘要包含 `APP_NAME`。

**工作逻辑**：与数据文件重写测试一致，新增对 `APP_NAME` 键的断言。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+1/-0 lines)

**修改目的**：对 v3.5 做相同的 SparkCatalog 改动。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+1/-0 lines)

**修改目的**：对 v3.5 做相同的数据文件重写测试改动。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesProcedure.java` (+1/-0 lines)

**修改目的**：对 v3.5 做相同的位置删除文件重写测试改动。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+1/-0 lines)

**修改目的**：对 v4.0 做相同的 SparkCatalog 改动。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+1/-0 lines)

**修改目的**：对 v4.0 做相同的数据文件重写测试改动。

**工作逻辑**：与 v3.4 改动完全一致。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesProcedure.java` (+1/-0 lines)

**修改目的**：对 v4.0 做相同的位置删除文件重写测试改动。

**工作逻辑**：与 v3.4 改动完全一致。

## 总结

本提交将 3072 的 Spark 应用名称环境上下文改动回移到 Spark v3.4、v3.5、v4.0 三个版本，确保四个受支持的 Spark 版本的快照摘要中均包含 `app-name` 字段，保持版本间行为一致，提升快照来源的可追溯性。
