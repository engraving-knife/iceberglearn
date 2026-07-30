# 提交 1614 e13a87f7d 分析

## 提交信息
- 哈希：e13a87f7d93d065b55f7483268bd4914593bcbaa
- 日期：2025-01-21 11:39:30 -0700
- 作者：Amogh Jahagirdar
- 消息：Spark 3.4: Backport writing DVs to Spark 3.4 (#12019)

## 总体目的

本提交将"写入 DV（Deletion Vector，删除向量）"的能力从 Spark 3.5 模块回迁（backport）到 Spark 3.4 模块。DV 是 Iceberg 表格式 v3 引入的新型删除文件格式，存储为 PUFFIN 文件中的 deletion vector blob，相比传统的位置删除（position deletes）能显著降低元数据膨胀并提升读取性能。

Iceberg 表格式 v3 的核心改动之一就是引入 DV 作为位置删除的替代方案：当表升级到 format-version=3 后，写入位置删除时应生成 DV 文件而非传统的位置删除文件。Spark 3.5 集成模块早已支持此特性，本提交让 Spark 3.4 集成模块也具备同样的能力，从而保持两个 Spark 版本在 v3 表上的功能一致性。

具体来说，本提交实现了：当 Spark 3.4 向 v3 表写入位置删除时，自动切换到使用 `PartitioningDVWriter` 生成 DV 文件；并在读取侧（用于"重写删除"优化）相应调整逻辑，使得已有的位置删除文件能被正确合并到新生成的 DV 中。同时为这套机制补充了完整的扩展测试覆盖。

## 如何达成设计目的

设计思路是把 Spark 3.5 中已经验证过的 DV 写入相关代码移植到 Spark 3.4 模块。核心改动分布在三处：(1) `SparkWriteConf.deleteFileFormat()` 中，当表是 v3+ 且非元数据表时，强制返回 `FileFormat.PUFFIN`，使后续写入逻辑据此选择 DV 写入器；(2) `SparkBatchQueryScan.rewritableDeletes()` 中，区分"为 DV 重写"和"为 file-scoped 删除重写"两种模式，前者要把所有非等值删除都纳入重写，后者只重写 file-scoped 删除；(3) `SparkPositionDeltaWrite.newDeleteWriter()` 中，根据 `useDVs()` 选择 `PartitioningDVWriter` 替代传统的 `ClusteredPositionDeleteWriter`/`FanoutPositionOnlyDeleteWriter`。

测试侧则在 `SparkRowLevelOperationsTestBase` 的参数化矩阵中加入 `formatVersion` 维度（在已有 v2 参数基础上新增两个 v3 参数组合），并新增 `testDeleteWithDVAndHistoricalPositionDeletes`、`testMergeWithDVAndHistoricalPositionDeletes`、`testUpdateWithDVAndHistoricalPositionDeletes` 等测试，验证 v2 表先产生历史位置删除、再升级到 v3 后写 DV 时能正确合并历史删除位的场景。

### 修改详情

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java

`deleteFileFormat()` 方法开头加入判断：若表不是 `BaseMetadataTable` 且 `TableUtil.formatVersion(table) >= 3`，则直接返回 `FileFormat.PUFFIN`，跳过原本读取 `spark.ext.delete.file-format` 等配置的解析逻辑。这意味着 v3 表的位置删除文件格式由表版本强制决定，用户无法用配置项覆盖，避免 v3 表仍写出旧式位置删除导致格式不一致。

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkBatchQueryScan.java

`rewritableDeletes()` 方法新增 `boolean forDVs` 参数，并把判断"哪些删除文件需要被重写到新删除文件"的逻辑抽到 `shouldRewrite(DeleteFile, boolean)` 中：
- `forDVs=true` 时，所有非等值删除（即所有位置删除）都需要重写——因为 DV 需要包含文件中所有已删除位置；
- `forDVs=false` 时，沿用原逻辑只重写 `ContentFileUtil.isFileScoped(deleteFile)` 的删除（即 file-scoped 删除）。

这是 DV 写入的关键：DV 是按数据文件维度构建的位图，必须知道该数据文件历史上所有被位置删除标记过的行，因此需要把所有现存位置删除都"折叠"进新的 DV。

#### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java

这是写入侧的核心改动，分布在几处：

1. `broadcastRewritableDeletes()` 的判断条件从 `context.deleteGranularity() == DeleteGranularity.FILE && scan != null` 改为 `scan != null && shouldRewriteDeletes()`，新增的 `shouldRewriteDeletes()` 返回 `context.useDVs() || context.deleteGranularity() == DeleteGranularity.FILE`——即只要走 DV 路径或 file 粒度路径，就需要广播可重写的删除集合。调用 `rewritableDeletes` 时传入 `context.useDVs()` 作为 `forDVs` 参数。

2. `newDeleteWriter()` 方法改造：新增 `Function<CharSequence, PositionDeleteIndex> previousDeleteLoader = PreviousDeleteLoader.create(table, rewritableDeletes)`，并在 `context.useDVs()` 为真时直接返回 `new PartitioningDVWriter<>(files, previousDeleteLoader)`，否则保留原有的 `ClusteredPositionDeleteWriter`/`FanoutPositionOnlyWriter` 选择逻辑。`PartitioningDVWriter` 在写入 DV 时通过 `previousDeleteLoader` 加载该数据文件已有的位置删除位图，与新删除位合并，从而保证 DV 完整覆盖历史删除。

3. `PreviousDeleteLoader` 改为私有构造 + 静态工厂 `create(Table, Map<String, DeleteFileSet>)`：当 `deleteFiles` 为 null 时直接返回 `path -> null`（无需加载历史删除），避免在非 DV 路径下创建无意义的 loader 实例。

4. 新增 `Context.useDVs()` 方法，返回 `deleteFileFormat == FileFormat.PUFFIN`——把"是否使用 DV"的判定集中到一处，由 `SparkWriteConf.deleteFileFormat()` 返回 PUFFIN 间接驱动。

#### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java

新增重载方法 `deleteFiles(Table table, Snapshot snapshot)`：通过 `table.newScan().useSnapshot(snapshot.snapshotId()).planFiles()` 收集该快照下所有 `FileScanTask` 的删除文件，便于测试在指定快照上验证 DV 与位置删除的产出情况。

#### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/SparkRowLevelOperationsTestBase.java

参数化矩阵新增 `formatVersion` 维度（构造函数新增参数、`@Parameters` 名称模板新增 `{9}` 占位符）。原有 4 组参数全部补 `2`，并新增 2 组 `formatVersion=3` 的参数组合（一组 testhadoop + parquet + LOCAL，一组 spark_catalog + hive + avro + DISTRIBUTED），让所有行级操作测试在 v2、v3 两种表格式下都跑一遍。

`initTable()` 的 ALTER TABLE 语句新增 `'format-version' = formatVersion` 子句，让测试表按参数化版本初始化。`validateSnapshotSummary` 在 v3 表上额外校验 `ADDED_DVS_PROP` 等于传入的删除文件数、且不包含 `ADD_POS_DELETE_FILES_PROP`（因为 v3 表的位置删除全部以 DV 形式产出）。

新增 `createTableWithDeleteGranularity(String, String, DeleteGranularity)` 辅助方法，便于子类构造带特定删除粒度的表。

#### spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java、TestMerge.java、TestUpdate.java、TestCopyOnWriteDelete.java、TestCopyOnWriteMerge.java、TestCopyOnWriteUpdate.java

这些子类的构造函数与父类同步新增 `formatVersion` 参数并向上传递。CopyOnWrite 系列测试的 `extraTableProperties()` 中移除了硬编码的 `FORMAT_VERSION=2`（统一由 `initTable` 通过参数化设置）。`TestMergeOnReadDelete/Merge/Update` 中相应移除 `FORMAT_VERSION=2` 硬编码，保留 `DELETE_MODE/MERGE_MODE/UPDATE_MODE=MERGE_ON_READ`。

#### TestMergeOnReadDelete.java、TestMergeOnReadMerge.java、TestMergeOnReadUpdate.java

新增 `testXxxWithDVAndHistoricalPositionDeletes` 系列测试，统一思路：先在 v2 表上分别用 PARTITION 粒度和 FILE 粒度做删除/更新/合并操作，产生历史的位置删除文件；然后把表升级到 format-version=3，再做一次删除/更新/合并操作，断言：
- 新快照中只有 1 个 DV 文件（`ContentFileUtil.isDV` 过滤后 `hasSize(1)`）；
- 该 DV 的 `recordCount` 等于 3（2 个历史删除位 + 1 个新删除位）；
- DV 文件通过 `FileFormat.fromFileName` 判定为 `FileFormat.PUFFIN`。

这覆盖了"DV 写入必须吸收历史位置删除"的关键场景。原有的 `testDeleteFileGranularity`、`testDeletePartitionGranularity` 等测试加上 `assumeThat(formatVersion).isEqualTo(2)`，因为这些测试断言的是传统位置删除文件的粒度，对 v3 表无意义。

## 小结

本次回迁效果是把 Spark 3.5 已成熟的 DV 写入能力完整搬到 Spark 3.4，使 v3 表在 Spark 3.4 上也能产出 PUFFIN 格式的 DV，并正确吸收历史位置删除。新增的"DV + 历史位置删除"测试覆盖了最容易出错的合并场景。

影响范围：仅限 Spark 3.4 模块（`spark/v3.4/spark` 与 `spark/v3.4/spark-extensions`）。对 v2 表行为完全不变（`formatVersion=2` 时所有新逻辑都不触发，等价于改动前）。对 v3 表则改变了删除文件产出格式（PUFFIN DV 代替 Parquet 位置删除）。

回迁到 1.4.x 分支的注意事项：1.4.x 分支通常较老，可能根本没有 `spark/v3.4` 模块，或 1.4.x 时期的 spark/v3.4 还不支持 v3 表/DV。DV 写入依赖 core 模块的 `PartitioningDVWriter`、`TableUtil.formatVersion`、`DeleteFileSet`、`ContentFileUtil.isDV`、`SnapshotSummary.ADDED_DVS_PROP` 等基础设施，这些都需要 core 侧先具备。回迁前必须先确认 1.4.x 的 core 模块是否已经引入 DV 相关基础设施；若 core 侧尚不具备，则不能单独回迁本 Spark 3.4 提交，需要先回迁 core 侧的 DV 支持。此外，参数化测试新增的 `formatVersion` 维度会让测试用例数翻倍，CI 资源消耗增加需评估。
