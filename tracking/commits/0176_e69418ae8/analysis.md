# 提交 0176：Spark 3.5: Extend action for rewriting manifests to support deletes (#9020)

## 提交信息

- **序号**：0176 / 4088
- **哈希**：e69418ae8d6d041acae0af4fe59ea3c8b8f8705f
- **短哈希**：e69418ae8
- **日期**：2023-11-17 18:34:25 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Extend action for rewriting manifests to support deletes (#9020)
- **PR/Issue**：#9020

## 总体目的

Apache Iceberg 通过 manifest 文件跟踪表中的数据文件（data files）和删除文件（delete files，包括 position deletes 与 equality deletes）。当一个表经历过多次写入和删除操作后，相关 manifest 容易产生大量小 manifest 或不均匀的分布，影响读取计划的效率。`RewriteManifestsSparkAction` 提供了一种"重写 manifest"（不动数据/删除文件本身，只重新组织描述它们的 manifest）的能力，以便合并小 manifest、按目标大小拆分大 manifest。

在该提交之前，Spark 3.5 模块下的 `RewriteManifestsSparkAction` 只处理 **data manifest**（即只重写描述 data files 的 manifest），完全忽略 **delete manifest**。这意味着启用了 v2 格式（format version >= 2）并使用行级删除（row-level deletes）的表在重写 manifest 时，delete manifest 不会被合并或重新组织，遗留的小 delete manifest 会持续累积，导致后续读取需要扫描更多 manifest，降低规划性能。

本提交的目的就是扩展 `RewriteManifestsSparkAction`，使其在重写 data manifest 之后，也对当前快照中的 delete manifest 进行重写，从而完整地覆盖 v2 表的 manifest 重组需求。同时，为了让重写流程能正确处理 `DeleteFile`，本提交还重构了 `SparkDataFile`，将公共逻辑上提到新的抽象基类 `SparkContentFile<F>`，并新增对应的 `SparkDeleteFile` 实现。

这是 Iceberg 在 Spark 引擎侧对 manifest 维护能力的实质性补强，对 v2 表（带删除的表）的元数据健康度与查询性能具有长期意义。

## 如何达成设计目的

整体设计思路有两层：

1. **重写 action 层面**：把原来一次性处理 data manifest 的流程拆成"按 `ManifestContent`（DATA / DELETES）分别处理"的两段式流程。`doExecute()` 先重写 data manifest，再重写 delete manifest，最后统一调用 `replaceManifests(...)` 提交。匹配、写入、写入器选择等都通过 `ManifestContent` 参数化。
2. **文件包装器层面**：原本只有 `SparkDataFile` 直接实现 `DataFile` 接口，所有从 Spark `Row` 中读取文件字段的逻辑都内嵌在它里面。本提交把这些通用字段读取逻辑提到抽象基类 `SparkContentFile<F> implements ContentFile<F>`，新增 `SparkDeleteFile extends SparkContentFile<DeleteFile> implements DeleteFile`，从而以最小的代码重复支持 `DeleteFile` 的 Spark `Row` 包装，供重写 delete manifest 时使用。

此外，提交还把原先内联在 `RewriteManifestsSparkAction` 中的 `mapPartitions` 逻辑抽成抽象类 `WriteManifests<F extends ContentFile<F>>`，并派生出 `WriteDataManifests` 与 `WriteDeleteManifests` 两个子类，分别负责使用 `SparkDataFile`/`SparkDataFile` 包装器和 `newRollingManifestWriter()`/`newRollingDeleteManifestWriter()` 写入器。`ManifestWriterFactory` 也相应新增了 `newRollingDeleteManifestWriter()` 与底层 `newDeleteManifestWriter()`。

测试侧，`TestRewriteManifestsAction` 新增三个端到端用例（非分区表小 delete manifest 合并、分区表小 delete manifest 合并、分区表大 delete manifest 拆分），并补充了构造 position/equality delete 文件的辅助方法；`TestSparkDataFile` 也扩展为同时验证 `SparkDataFile` 与 `SparkDeleteFile` 的字段转换正确性，断言库从 JUnit `Assert` 切换到 AssertJ。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java`（新增）

**修改目的**：抽取 `SparkDataFile` 中所有从 Spark `Row` 读取 `ContentFile` 通用字段（路径、格式、分区、记录数、文件大小、各种统计、key metadata、split offsets、sort order id、equality ids 等）的逻辑到一个抽象基类，使 data file 和 delete file 包装器共享同一套字段定位与读取实现，避免重复代码并为新增 `SparkDeleteFile` 提供基础。

**工作逻辑**：
- 该类声明为 `public abstract class SparkContentFile<F> implements ContentFile<F>`，泛型 `F` 表示具体的文件类型（`DataFile` 或 `DeleteFile`）。
- 构造函数接收 `Types.StructType type`（Iceberg 文件 schema）、`Types.StructType projectedType`（可选的分区投影类型）、`StructType sparkType`（Spark 侧 schema），通过遍历 `type.fields()` 并调用 `sparkType.fieldIndex(name)` 构建字段名到位置的映射，缓存所有字段的位置常量；对 unpartitioned 表中缺失的 `partition` 字段做特殊处理（返回 -1）。
- 分区处理上同时维护 `wrappedPartition`（基于 `SparkStructLike`）和可选的 `projectedPartition`（通过 `StructProjection` 包装前者），以支持投影分区。
- `wrap(Row)` 方法把传入的 Spark `Row` 赋给内部 `wrapped` 字段并把分区列 wrap 到 `SparkStructLike`，然后通过抽象方法 `asFile()` 返回具体子类实例（典型的模板方法 + 自返回型 fluent 风格）。
- `lowerBounds()`、`upperBounds()`、`keyMetadata()` 通过 `SparkValueConverter.convert(valueType, value)` 把 Spark 侧的值转换回 Iceberg 类型。
- `pos()` 返回 `null`、`specId()` 返回 `-1`，`copy()`/`copyWithoutStats()` 抛 `UnsupportedOperationException`，符合"该包装器只用于读取 manifest entry、不参与写入"的语义。
- 新增对 `equalityFieldIds()` 与 `content()` 字段的读取支持（原本 `SparkDataFile` 没有这两项），是支持 delete file 的关键。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkDataFile.java`

**修改目的**：将 `SparkDataFile` 改造为继承 `SparkContentFile<DataFile>` 的薄壳子类，删除所有重复字段与方法实现。

**工作逻辑**：保留两个构造函数（带与不带 `projectedType`），改为转发到 `super(...)`；实现 `asFile()` 返回 `this`；将原本 `SparkDataFile` 没有的 `equalityFieldIds()` 重写为返回 `null`（data file 没有等值字段）。其余所有 `ContentFile` 方法实现都来自基类，类体量从约 200 行降到约 30 行。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkDeleteFile.java`（新增）

**修改目的**：提供 `DeleteFile` 在 Spark 侧的 `Row` 包装实现，用于重写 delete manifest 时从 manifest entry 中读出 `DeleteFile`。

**工作逻辑**：与 `SparkDataFile` 类似的薄壳结构，`public class SparkDeleteFile extends SparkContentFile<DeleteFile> implements DeleteFile`，仅保留两个构造函数与 `asFile()` 返回 `this`。`equalityFieldIds()`、`content()` 等行为直接复用基类实现（基类已读取 `equalityIdsPosition` 和 `fileContentPosition`），无需任何额外代码。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：扩展重写流程，使其同时处理 data manifest 与 delete manifest；并把写入 manifest 的 mapPartitions 逻辑重构为可参数化的 `WriteManifests<F>` 抽象体系。

**工作逻辑**：
- 引入 `ManifestContent`、`ContentFile`、`DeleteFile`、`Iterator`、`SparkContentFile`、`SparkDeleteFile` 等类型。
- 新增 `EMPTY_RESULT` 静态常量（空 rewritten/added 列表），消除重复构造空结果。
- 把原先的 `manifestEncoder` 字段下沉到 `WriteManifests` 内部（见下文），构造函数中移除该字段初始化。
- `doExecute()` 重写：先调用 `rewriteManifests(ManifestContent.DATA)`，再调用 `rewriteManifests(ManifestContent.DELETES)`，把两次的 rewritten/added 合并；若合并后没有 rewritten，直接返回 `EMPTY_RESULT`；否则调用 `replaceManifests(rewrittenManifests, addedManifests)` 一次性提交，最后构建聚合结果。注意原先 `replaceManifests` 是在每次 `rewriteManifests` 内部调用，现在改为只在外层调用一次，避免分两次 commit。
- 新增私有方法 `rewriteManifests(ManifestContent content)`：保留原有的"匹配→估算目标数→构造 manifestEntryDF→按分区/非分区写入"流程，但全部参数化到 `content`。`findMatchingManifests(content)` 通过 `loadManifests(content, snapshot)` 选择 `dataManifests` 或 `deleteManifests`；`writeUnpartitionedManifests`/`writePartitionedManifests` 改为接收 `ManifestContent content` 并通过 `newWriteManifestsFunc(content, sparkType)` 取得对应的写入函数。
- `newWriteManifestsFunc`：根据 `content == ManifestContent.DATA` 返回 `WriteDataManifests`，否则返回 `WriteDeleteManifests`；同时计算 `combinedFileType`/`fileType`（注意这里复用 `DataFile.getType(...)` 取得 Iceberg 文件 schema，因为 data/delete file 的字段结构在 v2 中一致，只是 `content` 字段语义不同）。
- 新增 `repartitionAndSort` 辅助方法把分区表的 repartitionByRange + sortWithinPartitions 抽出来。
- `WriteDataManifests` 与 `WriteDeleteManifests`：抽象类 `WriteManifests<F extends ContentFile<F>> implements MapPartitionsFunction<Row, ManifestFile>` 的两个具体子类。抽象类持有 `writers`、`combinedFileType`、`fileType`、`sparkFileType`；提供 `apply(Dataset<Row>)` 用 `MANIFEST_ENCODER`（即原 `manifestEncoder`）调用 `mapPartitions`；`call` 方法遍历 Row，把第 4 列的 `Row` 用 `newFileWrapper()` 包装后通过 `writer.existing(...)` 写入，全部状态为 EXISTING（重写时所有文件都是已存在的），最后返回 `writer.toManifestFiles().iterator()`。子类只覆盖 `newFileWrapper()`（返回 `SparkDataFile` 或 `SparkDeleteFile`）与 `newManifestWriter()`（返回 rolling writer 或 rolling delete writer）。
- `ManifestWriterFactory` 新增 `newRollingDeleteManifestWriter()`，内部调用 `ManifestFiles.writeDeleteManifest(formatVersion, spec(), newOutputFile(), null)`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：为新增的 delete manifest 重写能力补充端到端测试覆盖。

**工作逻辑**：
- 新增导入 `DeleteFile`、`FileMetadata`、`Files`、`ManifestContent`、`RowDelta`、`FileHelpers`、`GenericRecord`、`Record`、`CharSequenceSet`、`Pair` 等。
- 新增 `testRewriteSmallDeleteManifestsNonPartitionedTable`：formatVersion > 1 的非分区表，写入 4 条数据，再分别提交一个 position delete 文件（删除 c1=1 或 c1=2 的记录）和一个 equality delete 文件（删除 c1=3 的记录），使当前快照有 1 个 data manifest + 2 个 delete manifest。执行 `rewriteManifests` 后，断言 2 个 delete manifest 被重写为 1 个、新 delete manifest 只含 EXISTING 文件、data manifest 未被改写（仍只有 ADDED 文件），并验证读取结果只剩 c1=4 的记录。
- 新增 `testRewriteSmallDeleteManifestsPartitionedTable`：分区表（按 c3 分区），写入 5 条数据后分多次提交 4 个 delete 文件（2 个 position + 2 个 equality，分布在 4 个分区）。设置 `MANIFEST_TARGET_SIZE_BYTES` 使 4 个 delete manifest 应合并为 2 个，并用 `rewriteIf(manifest -> manifest.content() == ManifestContent.DELETES)` 限定只重写 delete manifest。断言 rewritten 4 个、added 2 个，每个新 manifest 含 2 个 EXISTING 文件，读取结果只剩 c1=5 的记录。
- 新增 `testRewriteLargeDeleteManifestsPartitionedTable`：构造 1000 个 delete 文件提交到 1000 个分区，使原始 delete manifest 较大；把 `MANIFEST_TARGET_SIZE_BYTES` 设为原 manifest 长度的一半以强制拆分；验证 rewritten 为 1、added 至少为 2，且新增 manifest 落在自定义 `stagingLocation`。
- 新增辅助方法 `actualRecords()`、`newDeleteFile(Table, String)`、`generatePosDeletes(String)`、`writePosDeletes(...)`（含重载）、`writeEqDeletes(...)`（含重载），用于构造 position/equality delete 文件并提交到 `RowDelta`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataFile.java`

**修改目的**：扩展原有 `SparkDataFile` 字段转换测试，使其也覆盖新增的 `SparkDeleteFile`，并验证 `SparkContentFile` 抽象基类对 position delete 与 equality delete 都能正确还原所有字段。

**工作逻辑**：
- 引入 `ContentFile`、`DeleteFile`、`FileMetadata`、`MetadataColumns`、`Metrics`、`PartitionField`、`RowDelta`、`SortOrder`、`UpdatePartitionSpec`、`Conversions`、`SparkDeleteFile`、`ImmutableMap` 等。
- 断言从 JUnit `Assert.assertEquals` 全面切换到 AssertJ `assertThat(...).isEqualTo(...)`/`hasSize(...)`。
- 原 `checkSparkDataFile(Table)` 重命名为 `checkSparkContentFiles(Table)`，并在内部追加 delete file 验证流程：先写数据文件，再通过 `UpdatePartitionSpec` 移除原分区字段（构造"投影分区"场景），然后对每个 data file 构造一个 position delete 文件（带 path 的 lower/upper bound metrics、key metadata），并构造两个 equality delete 文件（equality field ids = 3,4），统一通过 `RowDelta` 提交。
- 通过 `spark.read().format("iceberg").load(tableLocation + "#data_files")` 与 `... #delete_files` 分别读取，用 `shuffleColumns(Dataset<Row>)` 随机打乱列顺序验证任意投影下都能正确读取。
- 对 position delete 用 `SparkDeleteFile` 包装验证；对 equality delete 用 `SparkDeleteFile` 包装验证 equality field ids 与所有公共字段。
- 抽出 `checkContentFile(ContentFile<?>, ContentFile<?>)`、`checkDeleteFile(...)`、`checkDataFile(...)` 三组断言，覆盖 content、path、format、recordCount、fileSizeInBytes、value/null/nan counts、lower/upper bounds、keyMetadata、splitOffsets、sortOrderId、equalityFieldIds、partition 等。
- 新增 `createPositionDeleteFile(Table, DataFile)` 与 `createEqualityDeleteFile(Table)` 辅助方法。

## 小结

本提交通过抽象 `SparkContentFile` 与新增 `SparkDeleteFile`、把重写流程参数化为 `ManifestContent.DATA`/`DELETES` 两段式，使 Spark 3.5 的 `RewriteManifestsSparkAction` 第一次具备了对 v2 表 delete manifest 的合并/拆分能力，补齐了 manifest 维护功能在行级删除场景下的关键缺口。
