# 提交 0896：Apply IntelliJ inspection findings to older Spark + Flink versions (#10625)

## 提交信息

- **序号**：0896 / 4088
- **哈希**：c7eba348d86563e1aefd711e664b45e393a44261
- **短哈希**：c7eba348d8
- **日期**：2024-07-04（Thu Jul 4 08:23:54 2024 +0200）
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：Apply IntelliJ inspection findings to older Spark + Flink versions (#10625)
- **PR/Issue**：#10625

## 总体目的

本提交是 0891 号提交（#10583，"Address IntelliJ inspection findings"）的姊妹提交。0891 只把 IntelliJ Inspection 的清理应用到 main 分支当前对应的较新版本目录（`spark/v3.5` 与 `flink/v1.19`），而 Iceberg 仓库同时还维护多个较老版本目录（`spark/v3.3`、`spark/v3.4`、`flink/v1.17`、`flink/v1.18`），这些目录中的代码与 v3.5/v1.19 高度同构（多为同一份代码的不同版本副本），同样存在 IntelliJ Inspection 报告的字段缺失 `final`、冗余 cast、冗余 `toString()`/拆箱方法调用、`Class.isInstance` 改 `instanceof`、方法尾分号等问题。

本提交的目的是把 0891 中应用的同类清理同步应用到 older Spark + Flink 版本目录，使所有版本目录的代码风格保持一致，避免"新版本干净、老版本仍有告警"的不一致状态，也便于后续维护时减少跨版本 diff 噪音。

## 如何达成设计目的

实现方式与 0891 完全相同：对每个 older 版本目录中对应的同名文件，应用与 0891 中 v3.5/v1.19 版本完全相同的 Inspection quick-fix。改动模式包括：

1. 给只在构造器中赋值的 private 字段补 `final` 修饰符；
2. 删除冗余的显式类型转换（如 `(String) RandomUtil.generatePrimitive(...)`）；
3. 用 `instanceof` 替代 `Class.isInstance(...)`；
4. 删除 `(int) bytearr[...]` 这类对 byte 自动加宽到 int 时的冗余 cast；
5. 测试代码中给字段加 `final`、删除冗余 cast、删除 `location.toString()` 等。

所有改动均不改变运行时行为，仅做代码风格清理。

## 修改详情

本提交涉及 70 个文件，120 行新增、128 行删除，全部位于 `flink/v1.17`、`flink/v1.18`、`spark/v3.3`、`spark/v3.4` 这四个 older 版本目录下。下面按目录分组说明，每个文件改动与 0891 中对应 v3.5/v1.19 版本的改动等价。

### `flink/v1.17/flink/` 与 `flink/v1.18/flink/` 下多个文件

**修改目的**：把 0891 中对 `flink/v1.19` 应用的清理同步到 v1.17、v1.18。

**工作逻辑**：涉及文件包括：
- `actions/Actions.java`：`env`、`table` 字段加 `final`；
- `actions/RewriteDataFilesAction.java`：字段加 `final`；
- `sink/AvroGenericRecordToRowDataMapper.java`、`sink/shuffle/MapDataStatistics.java`、`source/RowDataToAvroGenericRecordConverter.java`、`source/enumerator/ContinuousSplitPlannerImpl.java`、`source/enumerator/IcebergEnumeratorState.java`、`util/FlinkPackage.java`：字段加 `final` 或清理 cast；
- `source/enumerator/IcebergEnumeratorStateSerializer.java`、`source/split/SerializerHelper.java`：删除 `(int) bytearr[...]` 冗余 cast（byte 自动加宽到 int）；
- 测试 `TestFlinkCatalogTablePartitions.java`、`TestTableLoader.java`、`source/reader/TestColumnStatsWatermarkExtractor.java`、`maintenance/operator/FlinkSqlExtension.java`（仅 v1.18，因为 v1.17 可能无 maintenance 模块或略有差异）：同类清理。

### `spark/v3.3/spark/` 与 `spark/v3.4/spark/` 下多个文件

**修改目的**：把 0891 中对 `spark/v3.5` 应用的清理同步到 v3.3、v3.4。

**工作逻辑**：涉及文件包括：
- `spark-extensions/.../SparkRowLevelOperationsTestBase.java`：清理 cast；
- `spark/src/jmh/.../RandomGeneratingUDF.java`：`rand` 字段加 `final`，`(String)` cast 删除；
- `spark/src/main/.../PruneColumnsWithReordering.java`：`StringType.class.isInstance(...)` 改为 `instanceof`；
- `spark/src/main/.../Spark3Util.java`、`SparkTableUtil.java`：字段加 `final`；
- `spark/src/main/.../actions/DeleteOrphanFilesSparkAction.java`、`RewriteDataFilesSparkAction.java`、`RewriteManifestsSparkAction.java`、`RewritePositionDeleteFilesSparkAction.java`：字段加 `final`；
- `spark/src/main/.../data/SparkParquetWriters.java`：清理 cast；
- `spark/src/main/.../source/SparkScanBuilder.java`、`SparkStagedScanBuilder.java`：字段加 `final`；
  - 注意：`spark/v3.4` 还多改了 `SparkFileWriterFactory.java` 与 `NoSuchProcedureException.java`（v3.3 可能无对应文件或略有差异），同样补 `final`；
- 测试 `TestCreateActions.java`、`RandomData.java`、`LogMessage.java`、`TestBaseReader.java`、`TestDataFrameWrites.java`（含 `sparkSchema`/`icebergSchema`/`data0`/`data1` 字段加 `final`、lambda 中 `(MapPartitionsFunction<Row, Row>)` cast 删除、`location.toString()` 改 `location`）、`TestIdentityPartitionData.java`、`TestPartitionPruning.java`、`TestStructuredStreaming.java`、`sql/TestCreateTable.java`：
  - 注意：`spark/v3.3` 还多改了 `TestCompressionSettings.java`（v3.4/v3.5 在 0891 中已有），同样补 `final`；
- `spark/v3.3` 中 `TestBase.java`、`TestSparkCatalogOperations.java`、`sql/TestNamespaceSQL.java` 也做了同类清理（v3.4/v3.5 在 0891 中已有）。

所有改动均与 0891 中对应文件的改动完全等价，只是目标目录不同。

## 小结

- **成效**：把 IntelliJ Inspection 清理从 `spark/v3.5` 与 `flink/v1.19` 扩展到 `spark/v3.3`、`spark/v3.4`、`flink/v1.17`、`flink/v1.18` 四个 older 版本目录，使所有版本目录的代码风格保持一致，消除"老版本仍有告警"的不一致。
- **影响范围**：70 个文件，120 行新增、128 行删除，全部位于 older 版本目录。无 API 签名变更，无业务逻辑变更，运行时行为等价。
- **回迁到 1.4.x 的注意事项**：该提交是代码风格清理，理论上**可以安全回迁**到 1.4.x，但需要注意：
  1. 1.4.x 维护的 Spark/Flink 版本目录与 main 当前维护的可能不同。1.4.x 通常维护 `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 与 `flink/v1.17`、`flink/v1.18`、`flink/v1.19`，因此本提交涉及的四个目录（v3.3、v3.4、v1.17、v1.18）在 1.4.x 中应都存在；
  2. 建议与 0891 一起回迁，覆盖 1.4.x 支持的所有 Spark/Flink 版本目录，避免部分目录干净、部分仍有告警；
  3. 由于 older 版本目录的代码在不同分支间通常是逐字拷贝的，1.4.x 中对应文件的代码应与 main 中高度一致，cherry-pick 冲突概率低；
  4. 若 1.4.x 已有自定义 patch 改动了这些文件，cherry-pick 时可能需要手动解决冲突，但冲突应限于字段 `final` 修饰与 cast 清理这些行级改动，影响可控；
  5. 整体属于"非必需但有价值"的风格清理，回迁性价比取决于 1.4.x 维护团队对代码风格一致性的要求程度。
