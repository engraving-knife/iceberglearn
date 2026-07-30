# 提交 2710：BigQuery: Add iceberg-bigquery dependency to spark and flink build scripts

## 提交信息

- **序号**：2710 / 4088
- **哈希**：2b9a7e3fd42c8ab82d053760bced09cdbf82a1cf
- **短哈希**：2b9a7e3fd
- **日期**：2025-10-01 08:34:43 +0200
- **作者**：Владислав Самороков
- **提交说明**：BigQuery: Add iceberg-bigquery dependency to spark and flink build scripts
- **PR/Issue**：#14221

## 总体目的

Iceberg 社区正在推进 BigQuery 目录（iceberg-bigquery）模块的集成工作。随着 BigQuery 作为 Iceberg 表目录的支持日益完善，需要确保 BigQuery 模块被正确打包到 Spark 和 Flink 的运行时（runtime）JAR 包中，以便用户在使用 Spark 或 Flink 引擎时能够直接访问 BigQuery 目录。

在此之前，Spark 和 Flink 的 runtime 构建脚本中已经包含了 `iceberg-gcp`（Google Cloud Platform）依赖，但缺少了 `iceberg-bigquery` 依赖。这意味着如果用户在 Spark/Flink 中配置了 BigQuery 目录，运行时会因为缺少 BigQuery 相关类而失败。

此提交的目的就是将 `iceberg-bigquery` 模块作为依赖项添加到所有受影响的构建脚本中，使得 BigQuery 目录支持能够随 Spark 和 Flink 的 runtime 包一起分发。

## 如何达成设计目的

该提交通过在多个 `build.gradle` 文件中添加一行 `implementation project(':iceberg-bigquery')`（或在 open-api 模块中为 `testFixturesImplementation`）来实现目标。修改覆盖了所有当前维护的 Spark 版本（v3.4、v3.5、v4.0）和 Flink 版本（v1.20、v2.0、v2.1）的 runtime 构建，以及 open-api 模块的测试 fixtures。

## 修改详情

### `build.gradle` (+1/-0 lines)

**修改目的**：在 `iceberg-open-api` 项目的测试 fixtures 依赖中添加 `iceberg-bigquery`。

**工作逻辑**：open-api 模块的测试需要使用 BigQuery 模块来验证 OpenAPI 规范与各目录实现的兼容性，因此将 `iceberg-bigquery` 添加为 `testFixturesImplementation` 依赖。

### `flink/v1.20/build.gradle` (+1/-0 lines)

**修改目的**：在 Flink 1.20 的 runtime 模块依赖中添加 `iceberg-bigquery`。

**工作逻辑**：在 `iceberg-flink-runtime` 项目的依赖列表中，紧随 `iceberg-gcp` 之后添加 `implementation project(':iceberg-bigquery')`，确保 BigQuery 目录类被打包进 Flink runtime JAR。

### `flink/v2.0/build.gradle` (+1/-0 lines)

**修改目的**：在 Flink 2.0 的 runtime 模块依赖中添加 `iceberg-bigquery`。逻辑同上。

### `flink/v2.1/build.gradle` (+1/-0 lines)

**修改目的**：在 Flink 2.1 的 runtime 模块依赖中添加 `iceberg-bigquery`。逻辑同上。

### `spark/v3.4/build.gradle` (+1/-0 lines)

**修改目的**：在 Spark 3.4 的 runtime 模块依赖中添加 `iceberg-bigquery`。

**工作逻辑**：在 `iceberg-spark-runtime` 项目的依赖列表中，紧随 `iceberg-gcp` 之后添加 `implementation project(':iceberg-bigquery')`，确保 BigQuery 目录类被打包进 Spark runtime JAR。

### `spark/v3.5/build.gradle` (+1/-0 lines)

**修改目的**：在 Spark 3.5 的 runtime 模块依赖中添加 `iceberg-bigquery`。逻辑同上。

### `spark/v4.0/build.gradle` (+1/-0 lines)

**修改目的**：在 Spark 4.0 的 runtime 模块依赖中添加 `iceberg-bigquery`。逻辑同上。

## 总结

该提交是 BigQuery 目录集成工作的一部分，通过在所有受支持的 Spark 和 Flink 版本的 runtime 构建脚本中添加 `iceberg-bigquery` 依赖，确保用户能够在 Spark/Flink 运行时环境中直接使用 BigQuery 目录，而无需手动添加额外的 JAR 包。这是一个简单的构建配置变更，但对 BigQuery 目录的可用性有重要意义。
