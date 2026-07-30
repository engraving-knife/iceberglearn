# 提交 2581：Build: add spark 4.0 to stage-binaries.sh (#13948)

## 提交信息

- **序号**：2581 / 4088
- **哈希**：256a3a06e77f446f7a8c060a8f4500a95102bb59
- **短哈希**：256a3a06e
- **日期**：2025-08-30 17:02:25 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Build: add spark 4.0 to stage-binaries.sh (#13948)
- **PR/Issue**：#13948

## 总体目的

此次提交将 Spark 4.0 加入到 Iceberg 发布构建的二进制暂存脚本 `stage-binaries.sh` 中。Iceberg 的发布流程使用该脚本调用 Gradle 的 `publishApachePublicationToMavenRepository` 任务，将各引擎模块（Spark/Flink/Kafka）的二进制产物发布到 Maven 暂存仓库。脚本中通过 `SPARK_VERSIONS` 变量指定要发布的 Spark 版本列表。

此前 `SPARK_VERSIONS=3.4,3.5`，未包含 Spark 4.0。随着 Iceberg Spark 4.0 模块的成熟（参见提交 2572 添加 Spark 4 UnknownType 支持等），发布构建需要将 Spark 4.0 产物纳入暂存范围，以便用户能从 Maven 仓库获取 Spark 4.0 的 Iceberg 依赖。

由于 Spark 4.0 仅支持 Scala 2.13（不像 Spark 3.4/3.5 同时支持 2.12 和 2.13），脚本无需为 Spark 4.0 单独添加 Scala 2.13 的发布命令，注释中对此做了说明。

## 如何达成设计目的

- 将 `SPARK_VERSIONS` 从 `3.4,3.5` 改为 `3.4,3.5,4.0`，使第一个 Gradle publish 命令（基于 `SCALA_VERSION=2.12`）包含 Spark 4.0。
- 在脚本末尾新增注释说明"Spark 4.0 only supports Scala 2.13. no need to specify scalaVersion"，解释为何不需要像 3.4/3.5 那样单独追加 Scala 2.13 的 publish 命令。

## 修改详情

### `dev/stage-binaries.sh` (+2/-1)

**修改目的**：将 Spark 4.0 纳入发布二进制暂存范围。

**工作逻辑**：
- `SPARK_VERSIONS=3.4,3.5,4.0`，使其进入主 publish 命令的 `-DsparkVersions` 参数。
- 新增注释行说明 Spark 4.0 仅支持 Scala 2.13，无需单独指定 scalaVersion 发布命令。

## 总结

一次发布构建脚本更新提交，将 Spark 4.0 加入 `stage-binaries.sh` 的 `SPARK_VERSIONS` 列表，使发布构建能暂存 Spark 4.0 的 Iceberg 产物到 Maven 仓库，并注释说明 Spark 4.0 仅支持 Scala 2.13 的特性。无功能代码变更。
