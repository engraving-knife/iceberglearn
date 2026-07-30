# 提交 3072：Spark: Add Spark app name to env context (#14976)

## 提交信息

- **序号**：3072 / 4088
- **哈希**：46c871ccbaa6a4494433778fc38af889afefa735
- **短哈希**：46c871ccb
- **日期**：2026-01-07
- **作者**：Stas Pak
- **提交说明**：Spark: Add Spark app name to env context (#14976)
- **PR/Issue**：#14976

## 总体目的

本提交在 Spark 的 `SparkCatalog` 初始化环境上下文时，新增将 Spark 应用名称（app name）写入 `EnvironmentContext`，使其随快照摘要（snapshot summary）一并持久化。Iceberg 的 `EnvironmentContext` 是一个线程局部的键值存储，用于在提交快照时记录操作来源的引擎信息，如引擎名称（`engine-name`）、引擎版本（`engine-version`）、应用 ID（`app-id`）等。这些信息写入快照的 summary 中，便于后续审计、追踪和排查"是哪个 Spark 作业产生了这个快照"。

此前 `SparkCatalog` 的 `initialize` 方法已经写入了 `ENGINE_NAME`（"spark"）、`ENGINE_VERSION`（Spark 版本号）和 `APP_ID`（Spark 应用 ID，即 `applicationId`），但缺少应用名称。`APP_ID` 通常是类似 `local-1234567890` 或 `application_123456_0001` 的机器可读标识，对人类不够友好。新增 `APP_NAME`（来自 `sparkSession.sparkContext().appName()`）后，快照摘要中将包含用户在 Spark 作业中通过 `SparkConf.set("spark.app.name", ...)` 或 `--name` 参数设置的可读应用名，大幅提升快照来源的可追溯性。

本次改动先在 Spark v4.1 落地，后续由 3074 回移到 v3.4、v3.5、v4.0。

## 如何达成设计目的

在 `CatalogProperties` 中新增 `APP_NAME` 常量定义；在 `SparkCatalog.initialize()` 中新增一行将 Spark app name 写入 `EnvironmentContext`；在两个重写文件相关的测试中新增对 `APP_NAME` 键的断言。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogProperties.java` (+1/-0 lines)

**修改目的**：定义 `APP_NAME` 属性键常量。

**工作逻辑**：
在已有 `APP_ID` 常量后新增 `public static final String APP_NAME = "app-name";`，作为环境上下文和快照摘要中应用名称的标准化键名。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+1/-0 lines)

**修改目的**：在 SparkCatalog 初始化时将 Spark 应用名称写入环境上下文。

**工作逻辑**：
在 `initialize` 方法中，紧跟已有的 `EnvironmentContext.put(CatalogProperties.APP_ID, sparkSession.sparkContext().applicationId());` 之后，新增 `EnvironmentContext.put(CatalogProperties.APP_NAME, sparkSession.sparkContext().appName());`。这样每次通过该 catalog 提交快照时，summary 中会自动包含 `app-name` 字段。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+1/-0 lines)

**修改目的**：验证数据文件重写过程的快照摘要包含 `APP_NAME`。

**工作逻辑**：
在 `snapshotSummary()` 的断言链中，在 `.containsKey(CatalogProperties.APP_ID)` 之后新增 `.containsKey(CatalogProperties.APP_NAME)`，确保重写数据文件产生的快照摘要中包含应用名称。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesProcedure.java` (+1/-0 lines)

**修改目的**：验证位置删除文件重写过程的快照摘要包含 `APP_NAME`。

**工作逻辑**：
与上述数据文件重写测试一致，在快照摘要断言链中新增对 `APP_NAME` 键的检查。

## 总结

本提交通过在 `SparkCatalog` 初始化时将 Spark 应用名称写入 `EnvironmentContext`，使快照摘要中新增 `app-name` 字段，补全了快照来源追溯信息。相比已有的机器可读 `app-id`，`app-name` 提供了人类可读的应用标识，便于审计和运维定位。
