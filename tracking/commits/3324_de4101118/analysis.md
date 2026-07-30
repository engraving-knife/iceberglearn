# 提交 3324：Core: Detect and merge duplicate DVs for a data file and merge them before committing (#15006)

## 提交信息

- **序号**：3324 / 4088
- **哈希**：de41011180b1e5bd87a12a5177f840c8dface38e
- **短哈希**：de4101118
- **日期**：2026-02-28
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Detect and merge duplicate DVs for a data file and merge them before committing (#15006)
- **PR/Issue**：#15006

## 总体目的

Deletion Vector（DV）是 Iceberg 在 format v3 中引入的位置删除（position delete）格式，每个 DV 以 Puffin blob 形式存储，并通过 `referencedDataFile` 指向它所删除的某一个数据文件。Iceberg 的模型约束是：对一个数据文件，在同一快照内只应有一个 DV，否则读取端需要把多个 DV 合并后才能正确过滤，既冗余又容易出错。

此前的实现在 `MergingSnapshotProducer` 中维护了一个 `newDVRefs` 集合，记录本提交新增 DV 所引用的数据文件路径，并在 `validateAddedDVFiles` 中断言：如果某个数据文件已被"并发提交的快照"新增了 DV，则本提交不能再为它新增 DV（抛 `ValidationException`）。但这套逻辑只防范"跨快照"的并发冲突，并未处理"同一提交内部"出现多个 DV 指向同一数据文件的情形。实际场景下，单个 RowDelta 提交（例如由 Spark/Flink 多任务并发写入同一 commit）完全可能为同一个数据文件产出多个 DV。这些重复 DV 在读取时虽然能被正确合并，但会留下冗余的 Puffin 文件、增加 manifest 与读取开销，并使"每数据文件一个 DV"的不变式被破坏。

本提交将策略由"发现同提交内重复 DV 即视为冲突/保留冗余"改为"在提交前检测重复、就地合并并改写为单个 DV"：在 `prepareDeleteManifests` 阶段，对每个被多个 DV 引用的数据文件，并行读取这些 DV 的位置索引、合并为一个 `PositionDeleteIndex`，再写出一个新的合并 Puffin 文件；只有单个 DV 的数据文件则原样保留。这样提交后每个数据文件至多对应一个 DV，既维持不变式又避免读取端合并开销。

## 如何达成设计目的

在 `MergingSnapshotProducer` 中重构删除文件的内部存储：把原先按 specId 聚合的 `newDeleteFilesBySpec` 与记录 DV 引用的 `newDVRefs` 替换为按引用数据文件分组的 `dvsByReferencedFile`（Map<dataFilePath, List<DeleteFile>>）与存放非 DV（v2 equality/position）删除的 `v2Deletes` 列表。在准备删除 manifest 时调用新增的 `mergeDVs()`，它委托新类 `DVUtil.mergeAndWriteDVsIfRequired` 完成检测、校验、并行读取、合并与写出。为支持直接写入一个指定的合并 Puffin 输出，`BaseDVFileWriter` 新增接收 `Supplier<OutputFile>` 的构造器与批量合并 `delete(path, PositionDeleteIndex, ...)` 方法；`DVFileWriter` 接口增加该方法的默认实现。读取 DV 字节的工具方法被抽取到 `IOUtil.readFully` 供 core 与 data 模块复用，并移除 `SnapshotProducer` 中对 `PendingDeleteFile` 类型的硬性断言（合并后的 DV 是普通 `DeleteFile`，不再是 `PendingDeleteFile`）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DVUtil.java` (新增, +206 lines)

**修改目的**：实现重复 DV 的检测、校验、并行读取、合并与写出。

**工作逻辑**：
- `readDV(DeleteFile, FileIO)`：校验确为 DV 后，按 `contentOffset` 与 `contentSizeInBytes` 从 Puffin 文件读取字节，用 `IOUtil.readFully`（支持 range read）读入后调用 `PositionDeleteIndex.deserialize` 反序列化为位置索引。
- `mergeAndWriteDVsIfRequired(...)`：遍历 `dvsByReferencedFile`，引用数大于 1 的归入 `duplicates` 并记录其分区信息（spec + partition），等于 1 的直接加入 `finalDVs`。若无可合并项则直接返回；否则先 `validateCanMerge`，再 `readAndMergeDVs` 并行读取并按引用文件合并位置索引，最后 `writeDVs` 写出单个合并 Puffin 文件并返回其 `DeleteFile` 列表。
- `validateCanMerge(...)`：对每个被重复引用的数据文件，校验所有 DV 的 `dataSequenceNumber` 一致、`specId` 一致、分区 tuple 相等（用 `Comparators.forType(spec.partitionType())` 比较），任一不符则抛 `IllegalArgumentException`，防止跨序列号/跨分区合并产生错误结果。
- `readAndMergeDVs(...)`：用 `Tasks.range(n).executeWith(pool).stopOnFailure().throwFailureWhenFinished()` 在删除工作线程池中并行读取所有重复 DV，再按 `referencedDataFile` 聚合，对同文件已有的 `PositionDeleteIndex` 调用 `merge` 合并，得到每个数据文件一个合并索引。
- `writeDVs(...)`：用 `BaseDVFileWriter`（以 `Supplier<OutputFile>` 构造）把每个合并后的索引通过 `dvFileWriter.delete(referencedLocation, mergedPositions, spec, partition)` 写入同一个 Puffin 文件，`close` 后返回 `result().deleteFiles()`。

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (+84/-32 lines)

**修改目的**：重构删除文件存储并在 manifest 准备阶段触发 DV 合并。

**工作逻辑**：
- 字段重构：移除 `newDeleteFilesBySpec` 与 `newDVRefs`，新增 `v2Deletes`（非 DV 删除列表）、`dvsByReferencedFile`（按引用数据文件分组的 DV）、`dvMergeAttempt`（AtomicInteger，用于生成唯一输出文件名）；把 `addedFilesSummary` 拆分为 `addedDataFilesSummary` 与 `addedDeleteFilesSummary`（因删除文件 summary 必须在合并后才能生成）。
- `addDeletes`：DV 路由到 `dvsByReferencedFile.computeIfAbsent(referencedDataFile, ...)`，非 DV 加入 `v2Deletes`，置 `hasNewDataFiles=true`。
- `addsDeleteFiles()`：改为检查 `v2Deletes` 非空或 `dvsByReferencedFile` 任一列表非空。
- `prepareDeleteManifests()`：先 `mergeDVs()` 得到合并后的 DV 列表，与 `v2Deletes` 拼接后按 `specId` 分组，再按 spec 写 manifest，并在写之前对每个文件调用 `addedDeleteFilesSummary.addedFile(spec, file)` 生成 summary；manifest 缓存失效时同步 `addedDeleteFilesSummary.clear()`（因合并可能改变最终文件）。
- `mergeDVs()`：对引用数大于 1 的数据文件打 WARN 日志；用 `EncryptingFileIO.combine(ops().io(), ops().encryption())` 构造可加密的 FileIO；按 `locationProvider.newDataLocation("merged-dvs-{snapshotId}-{attempt}.puffin")` 计算输出位置；调用 `DVUtil.mergeAndWriteDVsIfRequired` 并传入 `ThreadPools.getDeleteWorkerPool()` 并行读取。
- `validateAddedDVFiles`：把对 `newDVRefs.contains` 的检查改为 `dvsByReferencedFile.containsKey`，仍拒绝跨快照并发冲突（同提交内重复已在前面被合并而非冲突）。
- summary 构建处分别 merge `addedDataFilesSummary` 与 `addedDeleteFilesSummary`。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+0/-3 lines)

**修改目的**：移除对删除文件类型的硬性断言。

**工作逻辑**：
在 `writeDeleteManifests` 写入删除文件的循环中，删除 `Preconditions.checkArgument(file instanceof Delegates.PendingDeleteFile, "Invalid delete file: must be PendingDeleteFile")`。合并产出的 DV 是通过 `BaseDVFileWriter.result().deleteFiles()` 得到的普通 `DeleteFile`，并非 `PendingDeleteFile`，保留该断言会误报失败。

### `core/src/main/java/org/apache/iceberg/deletes/BaseDVFileWriter.java` (+19/-7 lines)

**修改目的**：支持直接指定输出文件并新增批量位置索引写入。

**工作逻辑**：
- 把 `OutputFileFactory fileFactory` 字段替换为 `Supplier<OutputFile> dvOutputFile`；新增构造器 `(Supplier<OutputFile>, Function<String, PositionDeleteIndex>)`，原 `(OutputFileFactory, Function)` 构造器委托新构造器（用 `() -> fileFactory.newOutputFile().encryptingOutputFile()`）以保持兼容。
- 新增 `delete(String path, PositionDeleteIndex positionDeleteIndex, PartitionSpec spec, StructLike partition)`：按 path 取/建 `Deletes`，调用 `positions().merge(positionDeleteIndex)` 一次性合并整个索引（供 `DVUtil.writeDVs` 写入合并后的 DV，比逐位置调用更高效）。
- `newWriter()` 改用 `dvOutputFile.get()` 获取输出文件。

### `core/src/main/java/org/apache/iceberg/deletes/DVFileWriter.java` (+17/-0 lines)

**修改目的**：在接口层声明批量位置删除方法。

**工作逻辑**：
新增默认方法 `delete(String path, PositionDeleteIndex positionDeleteIndex, PartitionSpec spec, StructLike partition)`，默认实现遍历索引中每个位置调用已有的单位置 `delete(path, position, spec, partition)`。为 `BaseDVFileWriter` 的高效合并实现提供接口契约，同时不破坏其它实现。

### `core/src/main/java/org/apache/iceberg/io/IOUtil.java` (+25/-0 lines)

**修改目的**：抽取通用的带偏移量全量读取工具方法。

**工作逻辑**：
新增 `readFully(InputFile inputFile, long fileOffset, byte[] bytes, int offset, int length)`：打开 `SeekableInputStream`，若流实现 `RangeReadable` 则用 `readFully(fileOffset, bytes, offset, length)` 做 range 读，否则 `seek(fileOffset)` 后调用 `readFully(stream, bytes, offset, length)`。供 `DVUtil.readDV` 与 `BaseDeleteLoader` 复用，统一带 offset 的 DV 字节读取逻辑。

### `data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java` (+8/-19 lines)

**修改目的**：复用抽取后的 `IOUtil.readFully` 替代私有 `readBytes`。

**工作逻辑**：
`loadDV` 中改为 `new byte[length]` 后调用 `IOUtil.readFully(inputFile, offset, bytes, 0, length)` 并捕获 `IOException` 转 `UncheckedIOException`；删除私有的 `readBytes` 方法（其逻辑与 `IOUtil.readFully` 等价），减少重复代码与多余 import。

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+406/-0 lines)

**修改目的**：验证同提交内重复 DV 的合并行为。

**工作逻辑**：
新增多个测试，核心如 `testDuplicateDVsAreMerged`：为同一数据文件构造三个分别覆盖位置 [0,2)、[2,4)、[4,8) 的 DV，在一次 RowDelta 中提交，断言快照新增的删除文件只有 1 个合并后的 DV，且其包含 0–7 全部被删位置。`testDuplicateDVsMergedMultipleSpecs` 进一步在三种不同 partition spec 下为三个数据文件各造两个 DV，验证每个数据文件被合并为一个 DV、共产出 3 个合并 DV。辅助方法 `dvWithPositions` 用 `BaseDVFileWriter` 构造指定位置的 DV，`assertDVHasDeletedPositions` 读取 DV 并断言被删位置集合。

### `spark/v4.0`、`spark/v4.1`、`spark/v4.2` 下 `TestPositionDeletesTable.java` (各 +4/-4 lines)

**修改目的**：修正测试数据以避免触发非预期的 DV 合并。

**工作逻辑**：
多处把对 `dataFileA`（或 `dataFile10`）重复创建 position delete 的语句改为引用对应分区的 `dataFileB`/`dataFile99`。原先测试为不同分区对同一数据文件生成 position delete，在新合并逻辑下会被合并为一个 DV，破坏测试对多 DV 的预期；改为引用各自正确的数据文件后，每个数据文件只产出一个 DV，测试语义不变。

### `spark/v4.0`、`spark/v4.1`、`spark/v4.2` 下 `TestRemoveDanglingDeleteAction.java` (各约 +5/-7 lines)

**修改目的**：修正测试中复用相同数据文件路径导致的 DV 合并干扰。

**工作逻辑**：
把 `FILE_A2`/`FILE_B2`/`FILE_C2` 的路径由复用 `data-a.parquet`/`data-b.parquet`/`data-c.parquet` 改为各自独立的 `data-a2.parquet`/`data-b2.parquet`/`data-c2.parquet`，使其引用不同数据文件；并移除一处对 `fileBDeletes`（与其它删除引用相同数据文件）的添加与断言，避免新合并逻辑把这些删除并入同一 DV 而改变测试预期。

## 总结

本提交为 Iceberg 引入了同提交内重复 DV 的自动检测与合并能力：在准备删除 manifest 时，对引用同一数据文件的多个 DV 并行读取、合并位置索引并改写为单个 Puffin 文件，从而维持"每数据文件至多一个 DV"的不变式，消除冗余删除文件并降低读取端开销。改动以新增的 `DVUtil` 为核心，配合 `MergingSnapshotProducer` 的存储重构、`BaseDVFileWriter`/`DVFileWriter` 的批量写入支持、`IOUtil` 的读取抽取，以及 `SnapshotProducer` 断言的放宽，形成了完整的合并流水线，并通过 core 与多版本 Spark 测试验证了正确性与对既有用例的适配。
