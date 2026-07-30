# 提交 0236：Remove Flink 1.15

## 提交信息

- **序号**：0236 / 4088
- **哈希**：274390f3d65d9f48b26e3c682225d6f9edb35bce
- **短哈希**：274390f3d
- **日期**：2023-12-07 11:10:22 -0800
- **作者**：Rodrigo Meneses
- **提交说明**：Remove Flink 1.15
- **PR/Issue**：无（提交说明未带 PR 号）

## 总体目的

本提交是 Iceberg Flink 集成版本迁移系列的第一步：从代码仓库中物理删除 Flink 1.15 这一旧版本的全部源代码模块。Iceberg 的 Flink 集成采用多版本并存的目录结构（`flink/v1.15/`、`flink/v1.16/`、`flink/v1.17/`），每个版本对应一个独立的子模块以处理 API 差异。随着 Flink 社区节奏推进，1.15 已进入维护末期，Iceberg 决定收紧支持矩阵，移除 1.15 以降低维护成本、CI 资源消耗和构建复杂度，把精力集中到更新的 1.16/1.17/1.18 上。

需要注意的是，本提交是"纯删除"——276 个文件全部位于 [`flink/v1.15/`](flake/v1.15/) 目录下，共计 48501 行删除、0 行新增，且未触及任何构建配置（`gradle.properties`、`settings.gradle`、`libs.versions.toml`、`flink/build.gradle`、CI 工作流等）。构建配置的清理与 1.18 的引入由紧随其后的孪生提交 0237 完成。两个提交时间戳完全一致、作者相同，是设计上拆分成两步便于审阅的机械式迁移：先删源码，再改构建。这种拆分意味着单看本提交会让仓库处于"构建仍引用 1.15 但源码已删"的中间态，必须配合 0237 才能恢复一致性。

## 如何达成设计目的

通过一次性删除 `flink/v1.15/` 整棵子树来达成目的。删除范围涵盖 1.15 专属的构建脚本（`flink/v1.15/build.gradle`）、打包运行时模块（`flink-runtime/`，含 `LICENSE`/`NOTICE`）、以及完整的 Flink connector 实现与测试源码（`flink/src/main/java/...` 与 `flink/src/test/java/...`），包括 `FlinkCatalog`、`FlinkSink`、`FlinkSource`、`IcebergFilesCommitter`、各类 `TaskWriter`、shuffle 统计、source/enumerator/reader 体系、以及一整套集成测试与单元测试。由于 Iceberg 的多版本 Flink 模块之间源码高度相似（仅依赖坐标与少量 API 适配不同），删除 1.15 不会丢失任何能力，相同功能由 1.16/1.17 模块继续提供。

## 修改详情

### `flink/v1.15/` 整棵目录（276 个文件，纯删除）

**修改目的**：物理移除 Flink 1.15 子模块的全部源码、构建脚本与运行时打包文件。

**工作逻辑**：删除内容可按功能分组：

- 构建与打包：`flink/v1.15/build.gradle`、`flink/v1.15/flink-runtime/LICENSE`、`flink/v1.15/flink-runtime/NOTICE`，以及 SPI 注册文件 `META-INF/services/org.apache.flink.table.factories.Factory`。这些定义了 1.15 子项目的依赖坐标、测试配置和运行时 jar 的清单。
- Connector 主代码（`flink/src/main/java/org/apache/iceberg/flink/`）：包括 catalog 层（`FlinkCatalog`、`FlinkCatalogFactory`、`FlinkDynamicTableFactory`）、配置与选项（`FlinkConfigOptions`、`FlinkReadConf`/`FlinkReadOptions`、`FlinkWriteConf`/`FlinkWriteOptions`）、类型系统桥接（`FlinkSchemaUtil`、`FlinkTypeToType`、`TypeToFlinkType`、`FlinkFixupTypes`、`FlinkTypeVisitor`）、读写滤镜（`FlinkFilters`、`FlinkSourceFilter`）、sink 全套（`FlinkSink`、`IcebergStreamWriter`、`IcebergFilesCommitter`、`BaseDeltaTaskWriter`、`PartitionedDeltaWriter`、`UnpartitionedDeltaWriter`、`RowDataTaskWriterFactory`、`FlinkAppenderFactory`、`FlinkFileWriterFactory`、`FlinkManifestUtil`、`DeltaManifests` 及其序列化器、`BucketPartitioner` 体系、`sink/shuffle/` 下的数据统计协调器与算子）。
- 数据读写编解码（`flink/data/`）：Avro/ORC/Parquet 的 reader/writer、`RowDataProjection`、`RowDataUtil`、`StructRowData`、`FlinkValueReaders`/`FlinkValueWriters`、schema visitor 等。
- Source 体系（`flink/source/`）：`IcebergSource` 及其 enumerator、reader、split、assigner、watermark extractor 等完整流批一体读取实现。
- 测试代码（`flink/src/test/java/...`）：单元测试与集成测试基类（`TestFlinkScan`、`TestFlinkTableSource`、`TestFlinkMetaDataTable`、`TestIcebergSource*`、`TestContinuous*`、各类 assigner/enumerator/reader 测试基类、`FlinkTestBase` 相关等）。

删除规模庞大但模式单一：每个文件都是整体 `deleted file mode 100644`，没有任何跨版本共享代码的调整。这印证了 Iceberg 多版本 Flink 模块的"复制+适配"组织方式——移除一个版本就是删除一个目录分支，不产生对其它版本的连带影响。

## 小结

本提交是 Flink 版本迁移系列"减法"半步：物理删除 1.15 子模块的全部源码与构建文件，配合紧随其后的 0237 完成构建配置同步与 1.18 引入，是 Iceberg 收紧 Flink 支持矩阵、降低维护负担的常规版本治理动作。
