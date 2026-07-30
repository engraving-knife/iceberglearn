# 提交 1189：Core: Add a util to compute partition stats (#11146)

## 提交信息

- **序号**：1189 / 4088
- **哈希**：7bd13a32fdfc61391e79709e2d413097fb3088ad
- **短哈希**：7bd13a32f
- **日期**：2024-09-26（Thu Sep 26 21:44:49 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Core: Add a util to compute partition stats (#11146)
- **PR/Issue**：#11146

## 总体目的

Iceberg 表的元数据（manifest 文件）记录了每个数据/删除文件的分区、记录数、文件大小等信息，但**没有直接提供"按分区聚合的统计概览"**——例如某个分区下有多少数据文件、多少记录、数据文件总大小、有多少位置删除/等值删除文件、最后被哪个 snapshot 更新等。这类分区级统计对运维（识别小文件分区、识别倾斜分区、决定是否触发 compaction）和查询优化（基于分区数据量裁剪）非常有价值。

本提交的目的是新增一套核心工具，用于从表的某个 snapshot 出发，遍历所有 manifest 的 manifest entry，按分区聚合统计，输出每个分区的 `PartitionStats`（包含 12 个统计字段）。这是后续在 Spark/REST 等引擎层暴露分区统计能力（例如作为元数据表 `partition_stats`）的基础设施。

具体新增：
1. `PartitionStats`：单个分区的统计聚合体，实现 `StructLike` 以便作为行式数据消费。
2. `PartitionStatsUtil`：聚合统计的入口工具类，支持并行遍历 manifest 并合并。
3. `Partitioning.isPartitioned(Table)`：判断表是否分区（任一 spec 分区即视为分区表）。
4. `BaseScan.scanColumns(ManifestContent)`：从 `PartitionsTable` 上提为 `BaseScan` 的静态方法，供 `PartitionStatsUtil` 复用，避免重复。
5. 测试与基准：`TestPartitionStatsUtil`（429 行）覆盖空表、未分区表、含数据/位置删除/等值删除、schema 演进等场景；`PartitionStatsUtilBenchmark`（JMH）评估大规模 manifest 的性能。
6. 测试辅助 `FileGenerationUtil.generateEqualityDeleteFile`：补齐等值删除文件生成能力，供测试使用。

## 如何达成设计目的

1. **`PartitionStats` 作为可变聚合体**：每个分区对应一个 `PartitionStats` 实例，字段包括 partition（分区值）、specId、dataRecordCount、dataFileCount、totalDataFileSizeInBytes、positionDeleteRecordCount、positionDeleteFileCount、equalityDeleteRecordCount、equalityDeleteFileCount、totalRecordCount、lastUpdatedAt、lastUpdatedSnapshotId。提供 `liveEntry(ContentFile, Snapshot)` 累加 live entry 的统计、`deletedEntry(Snapshot)` 仅更新最后修改时间、`appendStats(PartitionStats)` 合并另一个实例。实现 `StructLike`（size/get/set）以便后续作为行消费（例如写入元数据表）。
2. **`PartitionStatsUtil.computeStats` 并行聚合**：取 snapshot 的所有 manifest，用 `Tasks.foreach(...).executeWith(ThreadPools.getWorkerPool())` 并行处理每个 manifest；每个 manifest 产出 `PartitionMap<PartitionStats>`（按 specId+分区键索引）；最后通过 `mergeStats` 把所有 manifest 的统计合并到统一 map。读取 manifest 时用 `BaseScan.scanColumns(manifest.content())` 做列裁剪，不读取文件级 metrics 列以减少 IO。
3. **分区键统一**：由于表可能有多个 spec（schema 演进），不同 spec 的分区结构不同。通过 `Partitioning.partitionType(table)` 得到统一分区 schema，再用 `PartitionUtil.coercePartition` 把各 spec 的分区值 coerce 到统一类型，作为 `PartitionStats` 的 key。这样同一逻辑分区（即使跨 spec）能正确聚合。
4. **`sortStats`**：基于 `Comparators.forType(partitionType)` 对分区值排序，便于输出有序结果。
5. **`BaseScan.scanColumns` 上提**：原 `PartitionsTable` 私有方法 `scanColumns` 上移为 `BaseScan` 静态方法，`PartitionsTable` 改为调用 `BaseScan.scanColumns`，消除重复。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionStats.java`（新文件，252 行）

**修改目的**：定义分区统计聚合体。

**工作逻辑**：
- 12 个字段，`STATS_COUNT = 12`。`lastUpdatedAt` 与 `lastUpdatedSnapshotId` 默认 null（Long 装箱）。
- 构造函数 `PartitionStats(StructLike partition, int specId)`。
- 12 个访问器方法。
- `liveEntry(ContentFile<?> file, Snapshot snapshot)`：按 `file.content()` 分发——DATA 累加 dataRecordCount/dataFileCount/totalDataFileSizeInBytes；POSITION_DELETES 累加 positionDelete 统计；EQUALITY_DELETES 累加 equalityDelete 统计；其它抛 `UnsupportedOperationException`。然后调用 `updateSnapshotInfo` 更新 lastUpdatedAt/lastUpdatedSnapshotId（取更大时间戳）。注释说明 `TOTAL_RECORD_COUNT` 暂不计算（需要扫描数据）。
- `deletedEntry(Snapshot snapshot)`：仅更新 snapshot 信息（删除条目不计入文件数/记录数，但反映分区被修改时间）。
- `appendStats(PartitionStats entry)`：把另一个实例的统计累加到本实例，specId 必须一致。合并 lastUpdatedAt 时取更大值。
- `updateSnapshotInfo(snapshotId, updatedAt)`：仅当新时间戳更大时更新，保证 lastUpdatedAt 是分区最近一次修改时间。
- `size()`/`get(pos, Class)`/`set(pos, value)`：实现 `StructLike`，按固定顺序映射 12 个字段。`set` 对可选计数字段（position/equality/totalRecord）允许 null（视为 0），与 spec 中这些字段 optional 的语义对齐。

### `core/src/main/java/org/apache/iceberg/PartitionStatsUtil.java`（新文件，136 行）

**修改目的**：提供统计计算入口。

**工作逻辑**：
- `computeStats(Table table, Snapshot snapshot)`：校验 table 非空、表分区、snapshot 非空。取 `partitionType = Partitioning.partitionType(table)`，`manifests = snapshot.allManifests(table.io())`。用 `Tasks.foreach(manifests).stopOnFailure().throwFailureWhenFinished().executeWith(ThreadPools.getWorkerPool())` 并行调用 `collectStats`，结果存入并发队列 `statsByManifest`。最后 `mergeStats` 合并。
- `sortStats(Collection, StructType)`：用 `Comparators.forType(partitionType)` 对分区值排序。
- `collectStats(Table, ManifestFile, StructType)`：打开 manifest reader（用 `BaseScan.scanColumns` 做列裁剪），遍历 entries。对每个 entry：取 `file.partition()`，用 `PartitionUtil.coercePartition(partitionType, spec, file.partition())` coerce 到统一分区类型，再用 `keyTemplate.copyFor(coercedPartition)` 复制为独立 key（避免共享引用）。用 `PartitionMap.computeIfAbsent(specId, key, () -> new PartitionStats(key, specId))` 获取或创建统计实例。`entry.isLive()` 调 `liveEntry`，否则调 `deletedEntry`。
- `openManifest`：根据 manifest.content() 选择 scan columns，`ManifestFiles.open(manifest, table.io()).select(projection)`。
- `mergeStats`：遍历所有 manifest 的 PartitionMap，用 `statsMap.merge(key, value, (existing, newEntry) -> { existing.appendStats(newEntry); return existing; })` 合并同分区统计。

### `core/src/main/java/org/apache/iceberg/Partitioning.java`

**修改目的**：新增 `isPartitioned(Table)`。

**工作逻辑**：
```java
public static boolean isPartitioned(Table table) {
  return table.specs().values().stream().anyMatch(PartitionSpec::isPartitioned);
}
```
任一 spec 分区即视为分区表（因为多 spec 表可能某些 spec unpartitioned，但只要有一个分区就需按分区统计）。

### `core/src/main/java/org/apache/iceberg/BaseScan.java`

**修改目的**：上提 `scanColumns` 方法供复用。

**工作逻辑**：新增静态方法：
```java
static List<String> scanColumns(ManifestContent content) {
  switch (content) {
    case DATA: return BaseScan.SCAN_COLUMNS;
    case DELETES: return BaseScan.DELETE_SCAN_COLUMNS;
    default: throw new UnsupportedOperationException("Cannot read unknown manifest type: " + content);
  }
}
```
包级可见，`PartitionStatsUtil` 与 `PartitionsTable` 均可访问。

### `core/src/main/java/org/apache/iceberg/PartitionsTable.java`

**修改目的**：复用上提后的 `BaseScan.scanColumns`。

**工作逻辑**：将原私有方法 `scanColumns` 删除（约 10 行），调用点 `.select(scanColumns(manifest.content()))` 改为 `.select(BaseScan.scanColumns(manifest.content()))`。

### `core/src/test/java/org/apache/iceberg/FileGenerationUtil.java`

**修改目的**：补齐等值删除文件生成能力。

**工作逻辑**：新增 `generateEqualityDeleteFile(Table table, StructLike partition)`：用 `FileMetadata.deleteFileBuilder(spec).ofEqualityDeletes().withPartition(partition).withPath(...).withFileSizeInBytes(...).withFormat(PARQUET).withRecordCount(...).build()` 构造一个等值删除文件元数据，供测试使用（与已有的 `generatePositionDeleteFile`、`generateDataFile` 对称）。

### `core/src/test/java/org/apache/iceberg/TestPartitionStatsUtil.java`（新文件，429 行）

**修改目的**：验证统计正确性。

**工作逻辑**：覆盖以下场景：
- `testPartitionStatsOnEmptyTable`：空表 currentSnapshot 为 null，断言抛 `IllegalArgumentException("snapshot cannot be null")`。
- `testPartitionStatsOnUnPartitionedTable`：未分区表，断言抛 `IllegalArgumentException("table must be partitioned")`。
- `testPartitionStats`：分区表，3 次提交相同数据文件（产生 3 个 manifest），验证 dataRecordCount/dataFileCount/totalDataFileSizeInBytes 是单次的 3 倍；然后加位置删除文件、加等值删除文件，验证 position/equality delete 统计与对应 snapshot 的时间戳/snapshotId 正确。
- `testPartitionStatsWithSchemaEvolution`：先用 spec0（按 c2 分区）提交 2 次，再演进 spec 加 c3，用 spec1 提交。验证：(a) 旧 spec 的分区统计保留（specId=0，时间戳为旧 snapshot）；(b) 新 spec 的分区统计正确（specId=1，时间戳为新 snapshot）；(c) 同名分区值（如 "bar"）在 spec0 与 spec1 下分别聚合为不同条目（因为 specId 不同），且旧 spec 分区在统一分区类型下 c3 为 null。
- 辅助方法 `partitionData`、`prepareDataFiles`、`prepareDataFilesOnePart`、`computeAndValidatePartitionStats`（用 AssertJ `extracting` + `Tuple` 断言 12 个字段）。

### `core/src/jmh/java/org/apache/iceberg/PartitionStatsUtilBenchmark.java`（新文件，105 行）

**修改目的**：评估大规模 manifest 的性能。

**工作逻辑**：构造 10000 个 manifest，每个 100 个分区、每分区 20 个数据文件（共 2M 文件）。`@Benchmark` 方法调用 `computeStats` + `sortStats`，断言结果大小为 100。使用 `Mode.SingleShotTime`（单次执行计时），1 fork、2 warmup、5 measurement。

## 小结

- **成效**：新增核心工具 `PartitionStats` + `PartitionStatsUtil`，可从任意 snapshot 并行计算分区级统计（数据/位置删除/等值删除的记录数与文件数、数据文件总大小、最后修改 snapshot）。支持多 spec 表的分区键统一与跨 spec 聚合。配套完整的单元测试（429 行，覆盖空表、未分区、删除文件、schema 演进）与 JMH 基准（10000 manifest 规模）。
- **影响范围**：核心层新增 2 个主类（388 行）+ 1 个 JMH 基准（105 行）+ 1 个测试（429 行）+ 3 处既有文件的小幅调整（`Partitioning` 新增方法、`BaseScan` 上提方法、`PartitionsTable` 复用、`FileGenerationUtil` 补齐等值删除生成）。共约 965 行新增、12 行删除。属于纯增量功能，对既有代码无破坏性变更（`PartitionsTable` 的私有方法上提为包级静态方法是兼容的内部重构）。
- **回迁到 1.4.x 的注意事项**：这是一个完整的新功能（分区统计基础设施），**回迁价值较高**——若 1.4.x 用户有分区运维/统计需求，回迁可直接受益。回迁时需注意：(1) 依赖 `BaseScan.scanColumns` 的上提重构与 `PartitionsTable` 的调用点变更需一并带回，否则编译失败；(2) `PartitionMap.computeIfAbsent(specId, key, supplier)` 这个签名需确认 1.4.x 中已存在，否则需先回迁相关基础工具；(3) `PartitionUtil.coercePartition` 与 `Partitioning.partitionType` 需在 1.4.x 中可用（这些是较早的基础设施，1.4.x 应已具备）；(4) 测试依赖 `FileGenerationUtil.generateEqualityDeleteFile`，需一并带回；(5) 该功能本身不修改既有写路径或读路径，回迁风险低，但建议回迁后在 1.4.x CI 上跑一遍 `TestPartitionStatsUtil` 确认行为一致；(6) 注意 `totalRecordCount` 字段当前未计算（注释明确），1.4.x 回迁后行为一致，不要误以为是 bug。
