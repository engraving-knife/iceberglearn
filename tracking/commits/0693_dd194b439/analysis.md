# 提交 0693：移除 Flink 1.16 版本支持

## 提交信息
- **序号**：0693 / 4088
- **哈希**：dd194b4391b947ee7caabbefd5e992fd92b9c524
- **短哈希**：dd194b439
- **日期**：2024-04-16 09:10:27 -0700
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Flink: Removes Flink version 1.16 (#10154)
- **PR/Issue**：#10154

## 总体目的

本提交的目的是彻底移除 Iceberg 对 Flink 1.16 版本的支持。Flink 1.16 已进入生命周期的末期（在 0694 提交的文档更新中被标记为 "End of Life"），Iceberg 需要清理掉对应的源码树以降低维护成本。

值得注意的是，0692 提交已经在构建配置层面（`gradle/libs.versions.toml`、`gradle.properties`、`settings.gradle`、`flink/build.gradle`、CI、打包脚本）移除了 Flink 1.16 的注册。但 `flink/v1.16/` 目录的源文件本身仍然留在仓库中。本提交就是把这 284 个源文件和测试文件全部删除，完成 v1.16 移除的"最后一公里"。

这种"先改配置后删源码"的两步分离策略是合理的：0692 先确保构建系统不再包含 v1.16（这样即使 v1.16 源码还在也不会被构建），避免在同一个提交中同时处理配置和大量文件删除造成 diff 混乱；本提交则专注于纯删除，diff 清晰、易于审查。

## 如何达成设计目的

策略非常简单直接：纯删除操作，284 个文件全部删除，50366 行删除，0 行新增。删除范围涵盖 `flink/v1.16/` 目录下的所有内容，包括：

- 构建文件：`build.gradle`、`flink-runtime/LICENSE`、`flink-runtime/NOTICE`
- 主源码：`flink/src/main/java/org/apache/iceberg/flink/` 下的全部 Java 类（Catalog、Source、Sink、data、shuffle 等完整实现）和 SPI 服务注册文件
- 测试源码：`flink/src/test/java/org/apache/iceberg/flink/` 下的全部测试类和测试基础设施

删除的文件结构与 v1.17/v1.18/v1.19 完全对称，因为 v1.16 也是同一套 Flink 集成代码的版本副本。值得注意的是，被删除的 `IcebergFilesCommitterMetrics.java` 是 96 行版本，而 0691 恢复的 v1.18 对应文件是 73 行版本，说明 v1.16 在 move 之前可能已经有过独立演进，但这不影响删除操作本身。

## 修改详情

### `flink/v1.16/build.gradle`（删除）
**修改目的**：删除 v1.16 模块的构建脚本。原文件定义 `iceberg-flink-1.16` 和 `iceberg-flink-runtime-1.16` 子项目，使用 `libs.flink116.*` 依赖。

### `flink/v1.16/flink-runtime/LICENSE`、`flink/v1.16/flink-runtime/NOTICE`（删除）
**修改目的**：删除 v1.16 runtime jar 的法务文件。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/**`（删除，约 90 个文件）
**修改目的**：删除 v1.16 的全部主源码。包括 `FlinkCatalog`（806 行）、`FlinkSink`（654 行）、`IcebergFilesCommitter`（516 行）、`IcebergSource`（543 行）、`FlinkParquetReaders`（832 行）、`ScanContext`（592 行）等完整实现，以及 SPI 注册文件 `org.apache.flink.table.factories.Factory` 和 `org.apache.flink.table.factories.TableFactory`。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/**`（删除，约 190 个文件）
**修改目的**：删除 v1.16 的全部测试代码。包括 `DataGenerators`（1172 行）、`TestIcebergFilesCommitter`（1148 行）、`TestFlinkMetaDataTable`（813 行）、`TestFlinkTableSource`（607 行）、`TestRowDataProjection`（593 行）、`TestRowProjection`（580 行）、`TestHelpers`（628 行）、`SimpleDataUtil`（443 行）等，以及 `MiniClusterResource`、`FlinkTestBase`、`TestBase`、`CatalogTestBase` 等测试基础设施。

## 小结
- **成效**：成功达成目的。`flink/v1.16/` 目录被完全移除，配合 0692 提交的构建配置变更，Iceberg 不再支持 Flink 1.16。仓库中 Flink 支持的版本变为 1.17、1.18、1.19。
- **影响范围**：仅影响 Flink 模块，纯删除操作，不影响其他版本（v1.17/v1.18/v1.19）的代码，也不影响 core/spark/hive 等其他模块。
- **回迁到 1.4.x 的注意事项**：回迁前需确认 1.4.x 分支是否仍需支持 Flink 1.16。若 1.4.x 的目标用户群中仍有 Flink 1.16 用户，则不应回迁此提交。若决定回迁，需确保 0692 提交中关于 `flink116` 依赖别名和 v1.16 子项目注册的移除也已回迁（否则删了源码但构建配置仍引用会导致构建失败）。两个提交在回迁时应作为一组一起处理。另外，本提交是纯删除，回迁时不会产生冲突风险，只需 `git rm -r flink/v1.16/`。
