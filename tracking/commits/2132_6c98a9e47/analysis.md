# 提交 2132：Spark: initial support for Spark 4.0

## 提交信息

- **序号**：2132 / 4088
- **哈希**：6c98a9e470d44f77e1939a1a333bf197a57949a6
- **短哈希**：6c98a9e47
- **日期**：2025-05-14 14:22:57 -0700
- **作者**：huaxingao
- **提交说明**：Spark: initial support for Spark 4.0
- **PR/Issue**：无 PR 号（直接提交到 main）

## 总体目的

本提交是 Iceberg 对 Spark 4.0 的初始支持实现。在前两个提交完成模块创建（v3.5 重命名为 v4.0，再复制回 v3.5）后，本提交对 spark/v4.0 模块进行实际的 Spark 4.0 适配修改。Spark 4.0 相比 3.5 有若干 API 变更，包括 Row Lineage 相关 API 的移除、Call 语句重命名、分析异常处理调整等。本提交涉及 127 个文件的修改（+507/-1369 行），同时更新构建配置（settings.gradle、build.gradle、libs.versions.toml）、CI 工作流和 Spark 基础设施代码。这是一个大型功能提交，标志着 Iceberg 正式开始支持 Spark 4.0。

## 如何达成设计目的

1. 在 `settings.gradle` 中注册 spark/v4.0 模块
2. 在 `gradle/libs.versions.toml` 中添加 Spark 4.0 版本依赖
3. 更新 `spark/v4.0/build.gradle` 配置 Spark 4.0 依赖
4. 移除 Spark 4.0 中不再支持的 Row Lineage 相关代码（RewriteMergeIntoTableForRowLineage、RewriteOperationForRowLineage、RewriteUpdateTableForRowLineage、RemoveRowLineageOutputFromOriginalTable 等）
5. 重命名 `Call.scala` 为 `IcebergCall.scala` 以避免 Spark 4.0 中的命名冲突
6. 适配 Spark 4.0 的 API 变更（SparkCatalog、SparkSessionCatalog、SparkTable、SparkWriteBuilder 等多个类）
7. 更新测试代码适配 Spark 4.0 API
8. 更新 CI 工作流支持 Spark 4.0 构建

## 修改详情

### 构建配置文件

#### `settings.gradle` (修改, +12 lines)

**修改目的**：注册 Spark 4.0 模块。

**工作逻辑**：添加 `includeProject('iceberg-spark:iceberg-spark-4.0', 'spark/v4.0/spark')` 等模块声明。

#### `gradle/libs.versions.toml` (修改, +4 lines)

**修改目的**：添加 Spark 4.0 版本依赖声明。

#### `build.gradle` / `spark/build.gradle` / `spark/v4.0/build.gradle` (修改)

**修改目的**：配置 Spark 4.0 模块的构建。

#### `gradle.properties` / `jmh.gradle` (修改)

**修改目的**：更新项目属性和 JMH 基准测试配置。

### CI 工作流

#### `.github/workflows/java-ci.yml` / `spark-ci.yml` / `publish-snapshot.yml` (修改)

**修改目的**：更新 CI 配置支持 Spark 4.0。

### Spark 4.0 核心代码适配

#### Row Lineage 相关代码移除 (多个 .scala 文件删除, -269 lines)

**修改目的**：移除 Spark 4.0 不再支持的 Row Lineage 功能。

**工作逻辑**：删除 `RewriteMergeIntoTableForRowLineage.scala`、`RewriteOperationForRowLineage.scala`、`RewriteUpdateTableForRowLineage.scala`、`RemoveRowLineageOutputFromOriginalTable.scala` 等 Row Lineage 相关分析规则。

#### `Call.scala` -> `IcebergCall.scala` (重命名, +4/-4 lines)

**修改目的**：避免与 Spark 4.0 中的 Call 类命名冲突。

#### `SparkCatalog.java` / `SparkSessionCatalog.java` / `SparkTable.java` 等 (修改)

**修改目的**：适配 Spark 4.0 API 变更。

**工作逻辑**：更新 Catalog 和 Table 实现以匹配 Spark 4.0 的接口变更，包括方法签名调整、参数变化等。

#### `SparkWriteBuilder.java` / `SparkCopyOnWriteOperation.java` / `SparkPositionDeltaOperation.java` 等 (修改)

**修改目的**：适配 Spark 4.0 写入 API。

**工作逻辑**：移除 `SparkCopyOnWriteScan.java`（-40 lines），调整写入相关操作的实现以匹配 Spark 4.0 的 DataSource V2 API 变更。

### 测试代码适配

#### 多个测试文件 (修改/删除)

**修改目的**：适配 Spark 4.0 测试。

**工作逻辑**：
- 删除 `TestRowLevelOperationsWithLineage.java`（-492 lines）和 `TestMergeOnReadWithLineage.java`（-35 lines）等 Row Lineage 相关测试
- 更新多个 Procedure 测试（TestCherrypickSnapshotProcedure、TestExpireSnapshotsProcedure 等）适配 Spark 4.0 API
- 更新基准测试文件的导入和 API 调用

## 总结

本提交是 Iceberg 对 Spark 4.0 的初始支持实现，在前两个提交创建 v4.0 模块的基础上，进行了实际的 Spark 4.0 适配。主要工作包括移除不再支持的 Row Lineage 功能、解决命名冲突、适配 Spark 4.0 API 变更、更新构建和 CI 配置。这标志着 Iceberg 正式开始支持 Spark 4.0，后续将有更多提交完善 Spark 4.0 的功能支持。这是一个重要的大型功能提交，整体减少了 862 行代码（主要是移除了 Row Lineage 相关代码和测试）。
