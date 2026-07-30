# 提交 3044：Spark: Move 4.0 as 4.1

## 提交信息

- **序号**：3044 / 4088
- **哈希**：3149892438ec7d4114fc2d79e970188dd3c90a44
- **短哈希**：314989243
- **日期**：2025-12-22
- **作者**：manuzhang
- **提交说明**：Spark: Move 4.0 as 4.1
- **PR/Issue**：无

## 总体目的

本提交将 Iceberg 的 Spark 模块目录 `spark/v4.0` 整体重命名（git rename）为 `spark/v4.1`，涉及 574 个文件、0 行内容改动。这是为引入 Spark 4.1 支持所做的目录结构重构的第一步，与紧随其后的提交 3045（"Copy back 4.1 as 4.0"）配对使用，共同完成一次模块"分叉（fork）"操作。

理解本提交需要结合 Iceberg 的 Spark 多版本支持架构。Iceberg 为每个受支持的 Spark 大版本维护独立的模块目录：此前仓库中存在 `spark/v3.4`、`spark/v3.5`、`spark/v4.0` 三个目录，分别针对 Spark 3.4、3.5、4.0 构建 `iceberg-spark`、`iceberg-spark-extensions`、`iceberg-spark-runtime` 等制品。随着 Spark 4.1 即将发布，Iceberg 需要新增对 Spark 4.1 的支持模块。维护者选择的策略是：以现有的 `spark/v4.0` 代码为基础分叉出 `spark/v4.1`，使两者从相同的起点开始独立演进——`spark/v4.1` 后续将适配 Spark 4.1 的 API/行为，而 `spark/v4.0` 继续维护对 Spark 4.0 的兼容。

本提交（"Move 4.0 as 4.1"）执行的是第一步：把 `spark/v4.0` 整体重命名为 `spark/v4.1`。由于使用 git 的 rename 机制，`spark/v4.1` 下的所有文件保留了从原 `spark/v4.0` 沿袭的完整提交历史（`git log --follow` 可追踪）。这一设计意图明确：让 `spark/v4.1`（代表最新 Spark 版本、未来主要开发方向）继承 v4.0 的开发历史，而把 v4.0 作为新副本在下一步重建。这是 git 中常见的"先 move 再 copy back"分叉手法——通过先 rename 把历史转移给目标分支，再把源路径以新文件形式复制回来，从而让分叉出的两个目录中，被 rename 的那个保有连续历史，被复制回来的那个成为无 rename 历史的新起点。

需要注意的是，重命名后 `spark/v4.1/build.gradle` 内部的 `sparkMajorVersion` 仍为 `'4.0'`，文件内容尚未针对 Spark 4.1 做任何适配——这符合预期，因为本提交与 3045 共同只完成目录结构的分叉，真正的 4.1 适配（修改 `sparkMajorVersion`、调整 CI、版本目录等）将在后续独立提交中进行（实际上仓库中存在独立的 "Spark: Initial support for 4.1.0" 分支提交承担该工作）。本提交与 3045 时间戳完全相同，由 manuzhang 直接推送，无关联 PR 编号。

## 如何达成设计目的

通过对 `spark/v4.0` 下全部 574 个文件执行 git rename（`git mv` 等价操作）至 `spark/v4.1`，使目录树结构由 `spark/{v3.4, v3.5, v4.0}` 变为 `spark/{v3.4, v3.5, v4.1}`。所有文件的 `similarity index` 为 100%（无内容修改），仅路径变更。涉及的文件覆盖 `build.gradle`、JMH 基准测试（`src/jmh/`）、Spark 扩展的 Scala 源码（`spark-extensions/src/main/scala/` 下的分析器/优化器/解析器/逻辑计划等）、ANTLR 语法文件（`IcebergSqlExtensions.g4`）、Java/Scala 测试（`src/test/`）以及测试资源（如 `decimal_dict_and_plain_encoding.parquet`）等整个模块的全部资产。

## 修改详情

### `spark/v4.0/**` → `spark/v4.1/**` (574 files, 0 insertions / 0 deletions)

**修改目的**：将整个 Spark 4.0 模块目录重命名为 Spark 4.1，为分叉出 v4.1 模块并保留其连续 git 历史做准备。

**工作逻辑**：
全部 574 个文件以 100% 相似度从 `spark/v4.0/` 路径下重命名到 `spark/v4.1/` 对应路径，目录层级与文件名完全保留。代表性文件包括：

- `spark/v4.0/build.gradle` → `spark/v4.1/build.gradle`：模块构建脚本，定义 `sparkMajorVersion`、`scalaVersion`、Java 版本守卫及 `iceberg-spark-4.0_2.13` 等子项目配置。
- `spark/v4.0/spark-extensions/src/main/antlr/.../IcebergSqlExtensions.g4` → `spark/v4.1/...`：Iceberg SQL 扩展的 ANTLR 语法。
- `spark/v4.0/spark-extensions/src/main/scala/.../IcebergSparkSessionExtensions.scala` → `spark/v4.1/...`：Spark 会话扩展入口。
- `spark/v4.0/spark-extensions/src/main/scala/.../catalyst/{analysis,optimizer,parser,plans}/` 下一系列 Scala 文件 → `spark/v4.1/...`：视图解析、命令改写、SQL 解析、逻辑计划等。
- `spark/v4.0/spark-extensions/src/jmh/java/.../*Benchmark.java` → `spark/v4.1/...`：JMH 性能基准。
- `spark/v4.0/spark-extensions/src/test/{java,scala}/...` 下大量测试 → `spark/v4.1/...`。
- `spark/v4.0/spark-extensions/src/test/resources/decimal_dict_and_plain_encoding.parquet` → `spark/v4.1/...`：测试用 Parquet 资源文件。

由于 similarity index 为 100%，git 将其识别为纯重命名，文件内容字节级未变，`spark/v4.1` 因此完整继承了原 v4.0 的提交历史。本提交执行后仓库中不再存在 `spark/v4.0`（由下一提交 3045 重建）。

## 总结

本提交将 `spark/v4.0` 模块整体重命名为 `spark/v4.1`（574 文件、零内容改动），是引入 Spark 4.1 支持的目录分叉操作的第一步。通过 git rename，`spark/v4.1` 继承了原 v4.0 模块的完整开发历史，为后续以 v4.1 为主要演进方向、适配 Spark 4.1 奠定结构基础。它与下一提交 3045 配合完成"分叉"：v4.1 保有历史，v4.0 将作为副本重建。
