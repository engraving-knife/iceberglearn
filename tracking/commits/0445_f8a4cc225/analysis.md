# 提交 0445：Spark 3.4: Extend action for rewriting manifests to support deletes (#9616)

## 提交信息

- **序号**：0445
- **哈希**：f8a4cc225db96a50088d2fb3693fa91f783d8f26
- **短哈希**：f8a4cc225
- **日期**：2024-02-01 16:07:43 -0800
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.4: Extend action for rewriting manifests to support deletes (#9616)
- **PR/Issue**：#9616

## 总体目的

本提交把 Spark 3.4 的 `RewriteManifestsSparkAction` 从"只能重写数据 manifest"扩展为"同时能重写删除 manifest"。在此之前的实现里，`doExecute` 只调用 `currentSnapshot.dataManifests(table.io())` 取数据 manifest，重写后用 `replaceManifests` 提交；删除 manifest 完全不在重写范围内。随着 V2 表里 position/equality delete 文件越来越多，删除 manifest 同样会出现"小文件过多"或"单个 manifest 过大"的问题，用户却无法用 `rewriteManifests` action 来整理它们，只能依赖 `RewritePositionDeletes` 这类更重的 action。本提交补齐了这一缺口，使 `rewriteManifests` 在一次执行中既重写数据 manifest、又重写删除 manifest，并允许通过 `rewriteIf` 谓词按 `ManifestContent` 过滤（例如只重写 DELETES）。

为了支持删除 manifest，需要解决两个具体问题。第一，原有的 Spark 侧文件包装器 `SparkDataFile` 只实现了 `DataFile`，无法包装 `DeleteFile`（删除文件多了 `content`、`equalityFieldIds` 等字段）。第二，`RewriteManifestsSparkAction` 内部的写入逻辑（`toManifests` 静态方法返回 lambda）硬编码了 `SparkDataFile` 和 `newRollingManifestWriter`，无法复用于删除 manifest 的写入（需要 `SparkDeleteFile` 和 `newRollingDeleteManifestWriter`）。因此本提交一方面抽出 `SparkContentFile<F>` 公共基类、新增 `SparkDeleteFile` 子类，另一方面把 manifest 写入逻辑重构成 `WriteManifests<F>` 抽象类加 `WriteDataManifests` / `WriteDeleteManifests` 两个子类，使两条路径共享同一套 repartition/sort/wrap 流程。

设计上保留了向后兼容：默认行为是数据 manifest 和删除 manifest 都重写（各自独立判断是否需要重写，不需要时返回空结果），最终只有在确实有 manifest 被重写时才提交快照；`rewriteIf` 谓词对两类 manifest 都生效，用户可以精确控制只重写某一类。`EMPTY_RESULT` 常量集中表达"无需重写"的返回值。

## 如何达成设计目的

实现路径分三层：(1) 文件包装层——抽出 `SparkContentFile<F>` 抽象基类（承载所有字段位置计算、分区包装、metrics 转换、`ContentFile` 接口实现），`SparkDataFile` 改为继承它并只保留构造函数和 `asFile()`，新增 `SparkDeleteFile` 同样继承基类；(2) action 层——`doExecute` 改为先后调用 `rewriteManifests(DATA)` 和 `rewriteManifests(DELETES)`，聚合结果后统一 `replaceManifests`；`rewriteManifests(ManifestContent)` 由原 `doExecute` 抽出并参数化；`findMatchingManifests` 和写入路径都按 content 分流；写入逻辑从静态方法 `toManifests` 重构为 `WriteManifests<F>` 抽象类 + 两个具体子类；(3) 测试层——新增三个端到端测试覆盖非分区/分区/大 manifest 三种场景，并把 `TestSparkDataFile` 扩展为同时验证 `SparkDeleteFile` 的字段读回正确性。

## 修改详情

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java（新增，243 行）

**修改目的**：抽出 `SparkDataFile` 与 `SparkDeleteFile` 的公共逻辑，作为 `ContentFile<F>` 的统一 Spark 行包装实现。

**工作逻辑**：`public abstract class SparkContentFile<F> implements ContentFile<F>`。构造函数接收 `Types.StructType type`（合并分区类型对应的文件类型）、`Types.StructType projectedType`（当前 spec 的分区类型对应的文件类型，可为 null）、`StructType sparkType`（Spark 行 schema）。构造时：
- 取 `lowerBoundsType`/`upperBoundsType`/`keyMetadataType` 用于 metrics 转换；
- 构建 `wrappedPartition = new SparkStructLike(partitionType)`，若 `projectedType != null` 则用 `StructProjection.create(partitionType, projectedPartitionType).wrap(wrappedPartition)` 得到 `projectedPartition`，否则 `projectedPartition = wrappedPartition`（这样读回的分区是按当前 spec 投影后的形式，兼容分区演化场景）；
- 遍历 `type.fields()`，用 `fieldPosition(name, sparkType)`（内部捕获 `fieldIndex` 异常，对无分区表的 `partition` 字段返回 -1）建立字段名到行位置的映射，缓存所有位置常量。

`wrap(Row)` 把行存入 `wrapped`、若有分区则同步包装分区并返回 `asFile()`（抽象方法，子类返回强类型 self）。所有 `ContentFile` 接口方法（`path`/`format`/`partition`/`recordCount`/`fileSizeInBytes`/`columnSizes`/`valueCounts`/`nullValueCounts`/`nanValueCounts`/`lowerBounds`/`upperBounds`/`keyMetadata`/`splitOffsets`/`sortOrderId`/`content`/`equalityFieldIds`）都从 `wrapped` 按位置取值，maps 类字段先 `isNullAt` 判空，`lowerBounds`/`upperBounds`/`keyMetadata` 经 `SparkValueConverter.convert` 转换。`pos()` 返回 null、`specId()` 返回 -1（manifest entry 里这两项由外层 entry 提供）。`copy()`/`copyWithoutStats()` 抛 `UnsupportedOperationException`。相比原 `SparkDataFile`，新增了 `fileContentPosition`/`equalityIdsPosition` 和 `content()`/`equalityFieldIds()` 实现，使基类同时服务于数据文件和删除文件。字段名统一改用 `DataFile.CONTENT.name()` 等常量而非硬编码字符串。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkDataFile.java（从 208 行精简到 29 行）

**修改目的**：让 `SparkDataFile` 复用 `SparkContentFile` 基类，删除全部重复实现。

**工作逻辑**：`public class SparkDataFile extends SparkContentFile<DataFile> implements DataFile`。仅保留两个构造函数（都 `super(...)` 转发）和 `@Override protected DataFile asFile() { return this; }`。同时重写 `equalityFieldIds()` 返回 `null`——数据文件没有 equality 字段 id，而基类默认会尝试从行里读取 `equalityIdsPosition`，对数据 manifest 行该位置不存在，故显式返回 null 避免误读。原文件里所有字段位置常量、`wrap`、各 `ContentFile` 方法、`fieldPosition`、`convert` 全部移入基类。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkDeleteFile.java（新增，40 行）

**修改目的**：提供删除文件的 Spark 行包装实现，与 `SparkDataFile` 对称。

**工作逻辑**：`public class SparkDeleteFile extends SparkContentFile<DeleteFile> implements DeleteFile`。结构与 `SparkDataFile` 完全平行：两个构造函数转发 `super(...)`，`@Override protected DeleteFile asFile() { return this; }`。不重写 `equalityFieldIds()`，因此继承基类实现——从行的 `equalityIdsPosition` 读取，对 position delete 返回 null（行里该字段为 null），对 equality delete 返回字段 id 列表。`content()` 也由基类从 `fileContentPosition` 读取，正确区分 position/equality。

### spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java（重构，+237/-60 左右）

**修改目的**：让 action 同时处理数据 manifest 和删除 manifest，并把写入逻辑抽象成可按 content 类型分派的形态。

**工作逻辑**：

1. **`doExecute` 重写**：不再单次处理数据 manifest，而是依次调用 `rewriteManifests(ManifestContent.DATA)` 和 `rewriteManifests(ManifestContent.DELETES)`，把各自的 `rewrittenManifests`/`addedManifests` 累加到两个列表；若 `rewrittenManifests` 为空直接返回 `EMPTY_RESULT`（无需提交），否则 `replaceManifests(rewrittenManifests, addedManifests)` 一次性提交。这意味着数据 manifest 和删除 manifest 在同一个快照里被原子替换。

2. **新增 `rewriteManifests(ManifestContent content)`**：从原 `doExecute` 抽出。`findMatchingManifests(content)` 取对应类型的 manifest，空则返回 `EMPTY_RESULT`；`targetNumManifests == 1 && matchingManifests.size() == 1` 也返回 `EMPTY_RESULT`（无需重写）；否则按 `spec.isUnpartitioned()` 调 `writeUnpartitionedManifests(content, ...)` 或 `writePartitionedManifests(content, ...)`，返回 `Result{rewritten=matchingManifests, added=newManifests}`。注意这里不再自己调 `replaceManifests`，改由上层 `doExecute` 统一提交。

3. **`findMatchingManifests(ManifestContent content)` + `loadManifests`**：按 content 用 `snapshot.dataManifests(io)` 或 `snapshot.deleteManifests(io)` 加载，再按 `specId` 和用户 `predicate` 过滤。

4. **`writeUnpartitionedManifests` / `writePartitionedManifests`**：原 `writeManifestsForUnpartitionedTable` / `writeManifestsForPartitionedTable` 改名并新增 `ManifestContent content` 参数。两者都通过 `newWriteManifestsFunc(content, schema)` 拿到一个 `WriteManifests<?>` 函数对象，对 DataFrame 做 repartition（无分区直接 `repartition(numManifests)`，有分区用 `repartitionAndSort` = `repartitionByRange(numManifests, col).sortWithinPartitions(col)`），再 `writeFunc.apply(transformedDF).collectAsList()`。

5. **`newWriteManifestsFunc`**：根据 `content` 构造 `WriteDataManifests` 或 `WriteDeleteManifests`。两者都接收 `ManifestWriterFactory`、`combinedFileType`（=`DataFile.getType(Partitioning.partitionType(table))`）、`fileType`（=`DataFile.getType(spec.partitionType())`）、`sparkFileType`（从 schema 取 `data_file` 字段）。

6. **`WriteManifests<F extends ContentFile<F>>` 抽象类**：替代原静态 `toManifests` 方法。`implements MapPartitionsFunction<Row, ManifestFile>`，持静态 `MANIFEST_ENCODER`（原 `manifestEncoder` 字段移入此处）。`apply(Dataset<Row>)` 返回 `input.mapPartitions(this, MANIFEST_ENCODER)`。`call(Iterator<Row>)` 的逻辑与原 lambda 一致：创建 `SparkContentFile<F> fileWrapper = newFileWrapper()` 和 `RollingManifestWriter<F> writer = newManifestWriter()`，遍历行取 `snapshotId`/`sequenceNumber`/`fileSequenceNumber`/`file`，调 `writer.existing(fileWrapper.wrap(file), ...)`，最后 `writer.close()` 并返回 `writer.toManifestFiles().iterator()`。两个抽象方法 `newFileWrapper()` 和 `newManifestWriter()` 由子类实现。

7. **`WriteDataManifests` / `WriteDeleteManifests`**：前者 `newFileWrapper()` 返回 `new SparkDataFile(...)`、`newManifestWriter()` 返回 `writers().newRollingManifestWriter()`；后者 `newFileWrapper()` 返回 `new SparkDeleteFile(...)`、`newManifestWriter()` 返回 `writers().newRollingDeleteManifestWriter()`。

8. **`ManifestWriterFactory` 扩展**：新增 `newRollingDeleteManifestWriter()`（用 `RollingManifestWriter` 包装 `newDeleteManifestWriter`）和私有 `newDeleteManifestWriter()`（`ManifestFiles.writeDeleteManifest(formatVersion, spec(), newOutputFile(), null)`）。

9. **其他**：`EMPTY_RESULT` 提为类常量；`manifestEncoder` 字段移入 `WriteManifests`；新增 `repartitionAndSort` 小工具。整体把"按 content 分流"的需求通过泛型 `F` 和模板方法模式干净地表达出来，避免了 if/else 散落。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java（+318）

**修改目的**：端到端验证重写删除 manifest 的正确性，覆盖非分区、分区小 manifest 合并、分区大 manifest 拆分三种场景。

**工作逻辑**：新增三个测试 + 一组辅助方法。

1. **`testRewriteSmallDeleteManifestsNonPartitionedTable`**：建无分区 V2+ 表，写 4 行数据，分别提交 1 个 position delete（删 c1=1,2）和 1 个 equality delete（删 c1=3），共 3 个 manifest（1 data + 2 delete）。执行 `rewriteManifests`，断言 `rewrittenManifests` 为 2 且全是 DELETES、`addedManifests` 为 1 且全是 DELETES；新删除 manifest 只有 EXISTING 文件（2 个），无 ADDED/DELETED；数据 manifest 保持只有 ADDED；最终查询只剩 `c1=4` 一行。

2. **`testRewriteSmallDeleteManifestsPartitionedTable`**：建按 c3 分区 V2+ 表（关闭 `MANIFEST_MERGE_ENABLED`），写 5 行数据，分 4 次提交 2 个 position delete（c1=1, c1=2，各带分区）和 2 个 equality delete（c1=3, c1=4），共 5 个 manifest。计算 manifest entry 大小后把 `MANIFEST_TARGET_SIZE_BYTES` 设为 `1.05 * 2 * entrySize`，使 4 个删除 manifest 重写后合并成 2 个、每个 2 条。用 `rewriteIf(manifest -> manifest.content() == ManifestContent.DELETES)` 只重写删除 manifest。断言 rewritten=4/added=2 全是 DELETES，两个新 manifest 各 2 个 EXISTING 文件，最终只剩 `c1=5` 一行。

3. **`testRewriteLargeDeleteManifestsPartitionedTable`**：建按 c3 分区 V2+ 表，直接用 `newDeleteFile` 生成 1000 个 position delete 文件（不同分区路径），一次 `RowDelta` 提交，得到 1 个删除 manifest。把 `MANIFEST_TARGET_SIZE_BYTES` 设为它长度的一半强制拆分，用自定义 `stagingLocation` 执行 `rewriteManifests`，断言 rewritten=1、added≥2 全是 DELETES，并校验新 manifest 位于 stagingLocation，最终快照删除 manifest 数≥2。

4. **辅助方法**：`actualRecords()` 读表排序返回；`newDeleteFile(Table, String partitionPath)` 用 `FileMetadata.deleteFileBuilder` 构造 position delete 元数据；`generatePosDeletes(String predicate)` 用 Spark 读 `_file`/`_pos` 生成位置删除对；`writePosDeletes` 两个重载（带/不带分区）调 `FileHelpers.writeDeleteFile`；`writeEqDeletes` 两个重载用 `GenericRecord` 构造 equality 删除并写文件。这些辅助方法让删除文件的构造与提交在测试里变得简洁。

### spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDataFile.java（+194/-78）

**修改目的**：扩展原 `TestSparkDataFile` 使其同时验证 `SparkDeleteFile` 的字段读回正确性，并验证分区演化场景下数据/删除文件的分区投影。

**工作逻辑**：

1. `checkSparkDataFile` 重命名为 `checkSparkContentFiles`。原逻辑只读 `#files` 元数据表（现改为 `#data_files` 新命名）并用 `SparkDataFile` 包装校验；扩展为：写数据后，先 `updateSpec` 移除所有分区字段（演化 spec），再通过 `RowDelta` 为每个数据文件加一个 position delete 文件，并加 2 个 equality delete 文件。然后分别读 `#data_files`、`#delete_files where content=1`（position）、`#delete_files where content=2`（equality），用 `SparkDataFile`/`SparkDeleteFile` 包装后逐一校验。注意 position delete 用演化前的 `dataFilesSpec` 构造类型（因为 delete 文件带的是原 spec 的分区），equality delete 用演化后的 `table.spec()`——这恰好覆盖了"删除文件分区 spec 与当前表 spec 不一致"的真实场景。

2. 新增 `shuffleColumns(Dataset<Row>)`：把原内联的"打乱列顺序"逻辑抽出，对三种元数据表查询都复用，验证 `SparkContentFile` 对任意列顺序的鲁棒性（因为 `fieldPosition` 按字段名查索引而非按位置）。

3. 校验方法重构：`checkDataFile`/`checkDeleteFile` 都委托给新的 `checkContentFile(ContentFile<?>, ContentFile<?>)`（校验 content/path/format/recordCount/fileSize/valueCounts/nullValueCounts/nanValueCounts/lowerBounds/upperBounds/keyMetadata/splitOffsets/sortOrderId）和 `checkStructLike`；`checkDataFile` 额外断言 `equalityFieldIds` 为 null，`checkDeleteFile` 额外断言 `equalityFieldIds` 相等。全部从 JUnit `Assert.assertEquals` 迁移到 AssertJ `assertThat`。

4. 新增 `createPositionDeleteFile(Table, DataFile)`：用 `FileMetadata.deleteFileBuilder` 构造 position delete，带 `DELETE_FILE_PATH` 的 lower/upper bounds metrics 和加密 key 元数据，分区取自数据文件，用于验证 metrics/keyMetadata 字段读回。`createEqualityDeleteFile(Table)`：构造 equality delete（`ofEqualityDeletes(3, 4)`），带 sortOrder 和加密 key。

## 小结

这是把 `RewriteManifestsSparkAction` 从"仅数据 manifest"扩展到"数据 + 删除 manifest"的功能增强。核心手段是把 `SparkDataFile` 的实现抽成 `SparkContentFile<F>` 公共基类并新增 `SparkDeleteFile`，再把 action 内的写入逻辑从静态方法重构为 `WriteManifests<F>` 模板方法类加两个子类，使数据/删除两条路径共享同一套 repartition/sort/wrap/commit 流程而仅在"文件包装器"和"manifest writer 工厂"两点上分派。`doExecute` 改为先后处理 DATA 和 DELETES 并统一提交，保持原子性和向后兼容（无重写则不提交）。测试覆盖了非分区合并、分区合并、大 manifest 拆分三种端到端场景，以及 `SparkDeleteFile` 在分区演化和任意列顺序下的字段读回正确性。
