# 提交 0235：Recover flink/1.17 files from history

## 提交信息

- **序号**：0235 / 4088
- **哈希**：b8ef64a2cd879cac284189455ab0579c5be80441
- **短哈希**：b8ef64a2c
- **日期**：2023-12-07 11:10:22 -0800
- **作者**：Rodrigo Meneses
- **提交说明**：Recover flink/1.17 files from history
- **PR/Issue**：无

## 总体目的

这个提交是 Iceberg Flink 集成模块版本演进的机械式第二步，与上一个提交 0234（"Move flink/v1.17 to flink/v1.18"）构成不可分割的配对。它的目的是从 git 历史中恢复被 0234 重命名移走的 `flink/v1.17` 目录，使 Flink 1.17 兼容模块重新存在。

在 0234 中，整个 `flink/v1.17` 目录被 `git mv` 为 `flink/v1.18`，导致 `flink/v1.17` 暂时消失。本提交通过从 0234 之前的某个历史提交中检出 `flink/v1.17` 的全部文件（典型的操作是 `git checkout <prev-commit> -- flink/v1.17`），将这 277 个文件、共计 49082 行原样恢复到工作树并提交。恢复后的 `flink/v1.17` 内容与 0234 重命名前的 v1.17 完全一致——`build.gradle` 仍引用 `flinkMajorVersion = '1.17'` 与 `libs.flink117.*`，所有主源码与测试源码一字未改。

两步合起来达到的效果是"目录分叉"：`flink/v1.18`（0234 产生，内容=原 v1.17）与 `flink/v1.17`（0235 恢复，内容=原 v1.17）并存且此刻内容完全相同。此后 `flink/v1.18` 将在后续提交中被改造为真正适配 Flink 1.18 API 的模块（修改 build.gradle 版本引用、调整依赖坐标、适配 API 差异、在 settings.gradle/libs.versions.toml/gradle.properties 中接线），而 `flink/v1.17` 继续按 Flink 1.17 独立维护。

之所以采用"rename + recover from history"而非直接 `cp -r flink/v1.17 flink/v1.18`，是为了让 git 记录下 v1.17→v1.18 的 rename 谱系——这样后续在 v1.18 上的改动能通过 `git log --follow` 追溯到 v1.17 时期的历史，保留完整溯源能力。这一"先复制目录、再恢复旧版本、最后接线适配"是 Iceberg 管理 Flink 多版本兼容层的既定模式，在 git 历史中可见同样的模式用于 1.18→1.19 迁移（`fbcd142c5` + `f761d98a1`）。本提交同样不触碰构建接线（settings.gradle、libs.versions.toml、gradle.properties），仅完成目录层面的恢复。

## 如何达成设计目的

整体设计是从 0234 的父提交中检出 `flink/v1.17` 目录的全部 277 个文件并提交。改动表现为 277 个文件、49082 行纯新增（无任何删除或修改），文件内容与 0234 重命名前的 v1.17 完全一致。与 0234 的纯 rename（0 字节变更）不同，本提交是纯新增（add only），因为从 git 的视角看这些文件在 0234 后已不存在、现在重新创建。文件组结构与 0234 中重命名的文件组完全对应。

## 修改详情

### `flink/v1.17/**`（277 个文件，纯新增恢复）

**修改目的**：恢复被 0234 重命名移走的 Flink 1.17 兼容模块，使 v1.17 与 v1.18 并存，完成目录分叉。

**工作逻辑**：

277 个文件从历史中恢复，内容与 0234 重命名前的 v1.17 完全一致，涵盖以下文件组：

- 构建与法律文件：`build.gradle`（263 行，仍为 `flinkMajorVersion = '1.17'`、`libs.flink117.*`）、`flink-runtime/LICENSE`（502 行）、`flink-runtime/NOTICE`（91 行）
- 主源码 `flink/src/main/java/org/apache/iceberg/flink/`：顶层类（`FlinkCatalog` 833 行、`FlinkCatalogFactory` 213 行、`FlinkReadConf`/`FlinkWriteConf`、`FlinkReadOptions`/`FlinkWriteOptions`、`FlinkSchemaUtil`、`FlinkFixupTypes`、`TypeToFlinkType`/`FlinkTypeToType`、`IcebergTableSink`、`TableLoader`、`FlinkDynamicTableFactory` 等）、`actions/`（`Actions`、`RewriteDataFilesAction`）、`data/`（`FlinkParquetReaders` 832 行、`FlinkParquetWriters` 504 行、`FlinkOrcReaders`/`FlinkOrcWriters`、`FlinkValueReaders`/`FlinkValueWriters`、`RowDataProjection`、`StructRowData` 等）、`sink/`（`FlinkSink` 654 行、`IcebergFilesCommitter` 516 行、`BaseDeltaTaskWriter`、`FlinkAppenderFactory`、`FlinkFileWriterFactory`、`shuffle/` 数据统计系列等）、`source/`（`IcebergSource` 558 行、`ScanContext` 561 行、`FlinkSource`、`FlinkInputFormat`、`enumerator/`、`reader/`、`split/`、`assigner/` 等）、`util/`（`FlinkPackage`、`FlinkAlterTableUtil`、`FlinkCompatibilityUtil`）
- 测试源码 `flink/src/test/java/org/apache/iceberg/flink/`：与主源码对应的完整测试树，包括 `DataGenerators`（1172 行）、`TestIcebergFilesCommitter`（1152 行）、`TestFlinkMetaDataTable`（829 行）、`TestFlinkCatalogTable`（692 行）、`TestContinuousSplitPlannerImpl`（692 行）、`TestHelpers`（611 行）等大型测试类
- SPI 服务文件：`META-INF/services/org.apache.flink.table.factories.Factory`、`META-INF/services/org.apache.flink.table.factories.TableFactory`

恢复后的 v1.17 目录此刻即可被已有的构建系统识别——因为 `settings.gradle` 与 `gradle.properties` 中 v1.17 的 include 块与 `knownFlinkVersions` 条目从未被移除（0234 未触碰它们），所以 v1.17 的恢复立即使其重新参与构建。而 v1.18 目录虽然存在但仍未被接线，需等后续提交。

## 小结

这个提交通过从 git 历史中恢复 `flink/v1.17` 目录，完成了与 0234 配对的"目录分叉"动作，使 Flink 1.17 与 1.18 兼容模块并存且内容一致，为后续在 v1.18 上进行 Flink 1.18 API 适配与构建接线、同时继续维护 v1.17 奠定了基础。
