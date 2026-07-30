# 提交 1032：Flink: move v1.19 to v.120

## 提交信息

- **序号**：1032 / 4088
- **哈希**：93f7839fa13d1deb40dc1e208d778cf07620d37f
- **短哈希**：93f7839fa
- **日期**：2024-08-06（Mon Aug 5 08:57:16 2024 -0700）
- **作者**：Steven Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: move v1.19 to v.120
- **PR/Issue**：无

## 总体目的

Iceberg 的 Flink 集成采用多版本并行维护的策略，每个 Flink 大版本对应 `flink/` 目录下一个独立子模块（如 `v1.17`、`v1.18`、`v1.19`）。随着 Flink 1.20 发布，Iceberg 需要新增对 Flink 1.20 的支持，同时逐步淘汰老旧的 1.17 版本。

本提交是 Flink 1.20 模块引入四步序列（1032-1035）的第一步。其做法是将现有的 `flink/v1.19/` 目录整体重命名为 `flink/v1.20/`，文件内容不做任何改动。这一步的核心动机是借助 `git mv` 的历史追踪能力，让新的 v1.20 子模块完整继承 v1.19 模块的 git 提交历史。如果直接新建 v1.20 目录再拷贝文件，git 会将其视为全新文件，丢失过去针对 Flink 1.19 的所有修改轨迹；而通过先 `git mv` 改名、再在后续提交中把 v1.19 重新拷贝回来的方式，可以让 v1.20 拥有从 v1.19 沿袭而来的完整历史链。

需要注意的是提交说明中的 `v.120` 是 `v1.20` 的笔误，实际改动是把 v1.19 移动到 v1.20。

## 如何达成设计目的

通过 `git mv flink/v1.19 flink/v1.20` 实现目录级别的重命名。git 在 diff 中将其识别为 325 个文件的纯重命名（rename），所有文件均显示 0 行增删，表明内容完全一致、只是路径发生变化。这是一种典型的"用重命名保留历史"的版本迁移技巧。

## 修改详情

### `flink/v1.19/**` -> `flink/v1.20/**`

**修改目的**：将 Flink 1.19 子模块整体重命名为 Flink 1.20 子模块，使 v1.20 继承 v1.19 的全部文件与 git 历史。

**工作逻辑**：共 325 个文件被重命名，覆盖了子模块的全部内容，包括：
- 构建脚本：`build.gradle`
- 运行时依赖声明：`flink-runtime/LICENSE`、`flink-runtime/NOTICE`
- 主源码：`flink/src/main/java/org/apache/iceberg/flink/` 下所有 Java 类，涵盖 catalog（`FlinkCatalog`、`FlinkCatalogFactory`）、sink（`FlinkSink`、`IcebergFilesCommitter`、`IcebergStreamWriter`）、source（`IcebergSource`、`FlinkSource`）、data 读写（`FlinkParquetReaders`、`FlinkParquetWriters`、`FlinkAvroReader` 等）、sink shuffle（`DataStatisticsCoordinator`、`MapRangePartitioner` 等）以及 maintenance operator 等模块。
- 测试源码：`flink/src/test/java/` 下全部测试类。
- JMH 基准测试：`flink/src/jmh/java/` 下的 `MapRangePartitionerBenchmark`。
- SPI 服务声明文件：`META-INF/services/org.apache.flink.table.factories.Factory` 及 `TableFactory`。

所有文件 0 行增删，仅路径前缀由 `flink/v1.19/` 变为 `flink/v1.20/`。

## 小结

- **成效**：成功将 v1.19 模块重命名为 v1.20，使新模块继承了 v1.19 的全部 git 历史，为后续真正适配 Flink 1.20 API 打下基础。
- **影响范围**：仅涉及 `flink/v1.19/` 到 `flink/v1.20/` 的目录重命名，325 个文件路径变更，无任何代码内容改动。
- **回迁到 1.4.x 的注意事项**：该提交属于 main 分支引入 Flink 1.20 支持的一部分，不适合单独回迁到 1.4.x。1.4.x 维护分支的 Flink 版本矩阵与 main 不同（1.4.x 通常支持更老的 Flink 版本），单独 cherry-pick 此重命名会导致 1.4.x 缺少 v1.19 模块而出现构建断裂。若 1.4.x 需要支持 Flink 1.20，应整体评估 1032-1035 这组提交并配合 1.4.x 自身的版本矩阵调整。
