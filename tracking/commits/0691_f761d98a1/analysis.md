# 提交 0691：从历史中恢复 flink/1.18 文件

## 提交信息
- **序号**：0691 / 4088
- **哈希**：f761d98a1d53cdb6574aeb84733158924635f25f
- **短哈希**：f761d98a1
- **日期**：2024-04-16 07:44:22 -0700
- **作者**：Rodrigo Meneses <rmenesespinillos@apple.com>
- **提交说明**：Flink: Recover flink/1.18 files from history
- **PR/Issue**：无（提交说明中未标注 PR 号）

## 总体目的

本提交是 Flink 多版本支持演进中的一个关键恢复性提交。要理解它的目的，必须结合其前一个提交 fbcd142c5（"Flink: Move flink/v1.18 to flink/v1.19"）一起看。

在前一个提交中，作者通过 `git mv` 将整个 `flink/v1.18/` 目录重命名为 `flink/v1.19/`，意图把 v1.18 的代码作为起点来开发 v1.19 支持。但这样做的问题在于：重命名之后，仓库中就不再有 `flink/v1.18/` 目录了，意味着 Iceberg 同时失去了对 Flink 1.18 的支持能力。

本提交的目的就是**从 git 历史中恢复出 `flink/v1.18/` 目录的全部文件**，使得 Iceberg 能够同时维护 Flink 1.18 和 1.19 两个版本。这样 v1.19 可以基于原 v1.18 代码继续演进去适配 Flink 1.19 的新特性，而 v1.18 仍然保留以继续支持 Flink 1.18 用户，直到其生命周期结束。

这本质上是 Flink 版本升级（1.18 → 1.19）过程中"先复制再演进"策略的补救步骤：先 move 再 recover，相当于在不丢失原 v1.18 的前提下，为 v1.19 准备了一份独立的工作副本。

## 如何达成设计目的

提交策略非常直接：纯新增（286 个文件，51109 行插入，0 行删除），将前一个提交移走的 v1.18 文件原样恢复回来。所有文件均以 `new file mode 100644` 形式创建，内容来自 git 历史中 move 之前的状态。

这种"先 move 后 recover"的两步操作，相比直接 `cp -r flink/v1.18 flink/v1.19` 的好处是：git 在 move 提交中能正确识别文件重命名关系，保留 rename 历史；而 recover 提交则明确地把 v1.18 作为"重新引入"来处理。两步分离也使得代码审查时能清楚区分"v1.19 的实质性改动"（在后续 0692 提交中完成）和"v1.18 的恢复"（本提交）。

恢复后的 v1.18 代码完全保持原貌：`build.gradle` 中 `flinkMajorVersion = '1.18'`，依赖全部使用 `libs.flink118.*`；`FlinkCatalogFactory.java` 中尚未引入 `DEFAULT_CATALOG_NAME` 常量（该常量是在 0692 提交中为 v1.19 新增的）。这说明恢复的是 v1.18 在 move 之前的纯净历史状态。

## 修改详情

### `flink/v1.18/build.gradle`
**修改目的**：恢复 v1.18 模块的 Gradle 构建脚本。
**工作逻辑**：定义 `iceberg-flink-1.18` 和 `iceberg-flink-runtime-1.18` 两个子项目。`flinkMajorVersion` 设为 `'1.18'`，编译期依赖使用 `libs.flink118.avro`、`libs.flink118.streaming.java`、`libs.flink118.table.api.java.bridge`、`libs.flink118.connector.base`、`libs.flink118.connector.files` 等；测试期依赖使用 `libs.flink118.connector.test.utils`、`libs.flink118.core`、`libs.flink118.runtime`、`libs.flink118.test.utils`、`libs.flink118.test.utilsjunit`。还包含 `libs.hadoop2.*`、`libs.parquet.avro`、`libs.orc.core`、`libs.avro.avro` 等通用依赖，以及各 iceberg 子项目的 testArtifacts 配置。

### `flink/v1.18/flink-runtime/LICENSE` 和 `flink/v1.18/flink-runtime/NOTICE`
**修改目的**：恢复 flink-runtime 子模块的法务文件。LICENSE 为 Apache License 2.0 全文（502 行），NOTICE 列出 Apache Iceberg 及其包含的 Apache ORC 等组件的版权声明（91 行）。这两个文件是发布 runtime jar 包所必需的。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/**`（主代码）
**修改目的**：恢复 v1.18 的全部主源代码。
**工作逻辑**：包含 Flink 集成的核心组件：
- **Catalog 层**：`FlinkCatalog`（833 行，Flink Catalog 接口实现）、`FlinkCatalogFactory`（213 行，工厂类）、`CatalogLoader`（215 行）、`FlinkDynamicTableFactory`（208 行）。
- **配置与选项**：`FlinkConfigOptions`、`FlinkReadConf`/`FlinkReadOptions`、`FlinkWriteConf`/`FlinkWriteOptions`、`FlinkConfParser`。
- **类型系统**：`FlinkSchemaUtil`、`FlinkTypeToType`、`TypeToFlinkType`、`FlinkTypeVisitor`、`FlinkFixupTypes`、`RowDataWrapper`。
- **Sink（写入）**：`FlinkSink`（654 行，核心 sink 入口）、`IcebergFilesCommitter`（516 行，提交器）、`IcebergStreamWriter`、`FlinkAppenderFactory`、`FlinkFileWriterFactory`、`RowDataTaskWriterFactory`、各类 DeltaWriter、`sink/shuffle/` 下的数据统计与分区器（`MapRangePartitioner`、`DataStatisticsCoordinator`、`SortKeySerializer` 等）。
- **Source（读取）**：`IcebergSource`（543 行）、`FlinkSource`（310 行）、`IcebergTableSource`、`FlinkInputFormat`、`FlinkSplitPlanner`、`source/assigner/`、`source/enumerator/`、`source/reader/`、`source/split/` 等完整的 Source V2 实现。
- **数据读写**：`data/FlinkParquetReaders`（832 行）、`data/FlinkParquetWriters`（504 行）、`data/FlinkOrcReaders/Writers`、`data/FlinkAvroReader/Writer`、`data/RowDataProjection`、`data/StructRowData` 等。
- **其他**：`FlinkFilters`（表达式转换）、`TableLoader`、`IcebergTableSink`、`actions/RewriteDataFilesAction`、`util/FlinkPackage`、`util/FlinkCompatibilityUtil`，以及 SPI 服务注册文件 `org.apache.flink.table.factories.Factory`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/**`（测试代码）
**修改目的**：恢复 v1.18 的全部测试代码。
**工作逻辑**：包含与主代码相对应的完整测试套件，覆盖 Catalog、Schema、Filter、Sink（含 `TestIcebergFilesCommitter` 1148 行、`TestFlinkIcebergSinkV2`、`TestFlinkManifest` 等）、Source（含 `TestFlinkMetaDataTable` 813 行、`TestContinuousSplitPlannerImpl` 692 行、`TestFlinkTableSource` 607 行等）、shuffle、data 序列化等。还包括测试基础设施：`FlinkTestBase`、`TestBase`、`CatalogTestBase`、`MiniClusterResource`、`MiniFlinkClusterExtension`、`HadoopCatalogExtension/Resource`、`SimpleDataUtil`、`TestHelpers`（628 行）、`DataGenerators`（1172 行）等。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java`
**修改目的**：恢复 JMH 基准测试。用于衡量 `MapRangePartitioner` 的分区性能。

## 小结
- **成效**：成功达成目的。恢复后 `flink/v1.18/` 目录完整存在，与 `flink/v1.19/` 并列，Iceberg 重新具备同时构建两个 Flink 版本的能力。后续的 0692 提交在此基础上对 v1.19 做了实质性适配。
- **影响范围**：仅影响 Flink 模块，不涉及 core、spark、hive 等其他模块。对 v1.18 而言是"恢复原状"，无行为变化；对仓库整体而言是新增一个独立的 v1.18 源码树。
- **回迁到 1.4.x 的注意事项**：本提交是纯文件恢复，回迁时需确保 1.4.x 分支上 `flink/v1.18/` 目录此前是否已被移除。若 1.4.x 从未做过 v1.18→v1.19 的 move 操作，则本提交不适用；若 1.4.x 已有 v1.18，则无需恢复。关键前提是 1.4.x 的 `gradle/libs.versions.toml` 中必须已定义 `flink118` 版本及 `flink118-*` 依赖别名，否则 `build.gradle` 会无法解析依赖。同时需注意恢复出的 v1.18 代码是历史快照，可能缺少后续在 v1.19 上做的改进（如 `DEFAULT_CATALOG_NAME` 常量、`dropDatabase` 辅助方法），如需这些改进需另行同步。
