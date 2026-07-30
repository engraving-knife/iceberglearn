# 提交 0234：Flink: Move flink/v1.17 to flink/v1.18

## 提交信息

- **序号**：0234 / 4088
- **哈希**：820fc3ceda386149f42db8b54e6db9171d1a3a6d
- **短哈希**：820fc3ced
- **日期**：2023-12-07 11:10:22 -0800
- **作者**：Rodrigo Meneses
- **提交说明**：Flink: Move flink/v1.17 to flink/v1.18
- **PR/Issue**：无

## 总体目的

这个提交是 Iceberg Flink 集成模块版本演进的机械式第一步：将 `flink/v1.17` 目录整体重命名为 `flink/v1.18`，作为"为 Flink 1.18 新增兼容模块"流程的起点。

Iceberg 为每个支持的 Flink 大版本维护一个独立的源码目录（`flink/v1.15`、`flink/v1.16`、`flink/v1.17`……），每个目录下包含针对该 Flink 版本 API 的 `build.gradle`、`flink-runtime` 子目录以及完整的 `flink/src/main` 与 `flink/src/test` 源码树。当需要新增对下一个 Flink 大版本（这里是 1.18）的支持时，Iceberg 采用一套标准化的"目录分叉"流程，由两个配对提交完成：

1. **本提交（0234）**：把当前最新的版本目录 `flink/v1.17` 通过 `git mv` 整体重命名为 `flink/v1.18`。这是一个纯重命名操作——277 个文件全部以 0 字节变更被记录为 rename，文件内容一字未改。重命名后 `flink/v1.18/build.gradle` 内部仍然引用 `flinkMajorVersion = '1.17'` 与 `libs.flink117.*` 依赖坐标，与重命名前完全一致。
2. **配对提交（0235）**：紧随其后，从 git 历史中恢复 `flink/v1.17` 目录（49082 行新增），使 v1.17 重新存在。

两步合起来达到的效果是：`flink/v1.18` 作为 `flink/v1.17` 的完整副本诞生（内容与原 v1.17 完全相同），而 `flink/v1.17` 依然保留。此后，`flink/v1.18` 将在后续提交中被改造为真正适配 Flink 1.18 API 的模块（修改 `build.gradle` 的版本引用、调整依赖坐标、适配 API 差异），而 `flink/v1.17` 继续按 Flink 1.17 维护。这一"先复制、再分叉"的模式是 Iceberg 管理 Flink 多版本兼容层的既定做法——在 git 历史中可见同样的模式用于 1.18→1.19 的迁移（`fbcd142c5` "Move flink/v1.18 to flink/v1.19" + `f761d98a1` "Recover flink/1.18 files from history"）。

需要强调的是：本提交（以及配对的 0235）只完成目录层面的分叉，**不包含任何构建配置的接线**——`settings.gradle` 未新增 `1.18` 的 include 块，`gradle/libs.versions.toml` 未新增 `flink118` 版本与依赖别名，`gradle.properties` 的 `knownFlinkVersions` 仍为 `1.15,1.16,1.17`。这些接线工作留给后续提交（0236/0237 等）完成。因此本提交单独看会让 v1.17 暂时消失（仅 v1.18 存在但未接线），必须与 0235 配对才构成完整的"分叉"动作。

## 如何达成设计目的

整体设计就是利用 git 的 rename 检测能力，对整个版本目录做一次纯机械的 `git mv flink/v1.17 flink/v1.18`。改动覆盖 277 个文件，涵盖 `build.gradle`、`flink-runtime/LICENSE`、`flink-runtime/NOTICE`、`flink/src/main/java/**`（Catalog、Source、Sink、data 序列化、shuffle 等全部主代码）、`flink/src/test/java/**`（全部测试代码）以及 `flink/src/test/resources/META-INF/services/*`。所有文件的 diff 均为 0 字节变更（rename only），无任何内容修改。这一步的目的是让 git 记录下"v1.17 演进为 v1.18"的谱系关系，为后续在 v1.18 上做版本适配改动保留可追溯的 rename 历史。

## 修改详情

### `flink/v1.17/**` → `flink/v1.18/**`（277 个文件，纯重命名）

**修改目的**：将整个 Flink 1.17 兼容模块目录提升为 1.18，作为新增 Flink 1.18 支持的第一步。

**工作逻辑**：

277 个文件全部以 rename 形式从 `flink/v1.17/` 路径迁移到 `flink/v1.18/` 路径，无内容变更。重命名覆盖以下文件组：

- 构建与法律文件：`build.gradle`、`flink-runtime/LICENSE`、`flink-runtime/NOTICE`
- 主源码 `flink/src/main/java/org/apache/iceberg/flink/`：顶层类（`FlinkCatalog`、`FlinkCatalogFactory`、`FlinkReadConf`/`FlinkWriteConf`、`FlinkReadOptions`/`FlinkWriteOptions`、`FlinkSchemaUtil`、`FlinkFixupTypes`、`TypeToFlinkType`/`FlinkTypeToType`、`IcebergTableSink`、`TableLoader` 等）、`actions/`、`data/`（Avro/ORC/Parquet 读写器）、`sink/`（`FlinkSink`、`IcebergFilesCommitter`、`BaseDeltaTaskWriter`、`shuffle/` 数据统计等）、`source/`（`IcebergSource`、`FlinkSource`、`FlinkInputFormat`、`enumerator/`、`reader/`、`split/`、`assigner/` 等）、`util/`（`FlinkPackage`、`FlinkCompatibilityUtil` 等）
- 测试源码 `flink/src/test/java/org/apache/iceberg/flink/`：与主源码对应的完整测试树（`TestFlinkCatalogTable`、`TestFlinkIcebergSink`、`TestIcebergSourceBounded`、`TestFlinkScan` 等）
- SPI 服务文件：`META-INF/services/org.apache.flink.table.factories.Factory`、`META-INF/services/org.apache.flink.table.factories.TableFactory`

重命名后，`flink/v1.18/build.gradle` 内部仍为 `flinkMajorVersion = '1.17'` 并引用 `libs.flink117.*`——这些版本特定的内容将在后续提交中被改为 1.18 对应的值。本提交不触碰 `settings.gradle`、`gradle/libs.versions.toml`、`gradle.properties`，因此 v1.18 目录此刻尚未被构建系统识别。

## 小结

这个提交通过将 `flink/v1.17` 目录整体重命名为 `flink/v1.18`，启动了 Iceberg 对 Flink 1.18 的支持流程，是"先复制目录、再恢复旧版本、最后接线适配"标准三步式版本迁移的第一步，为后续在 v1.18 上进行 API 适配与构建接线奠定了目录基础。
