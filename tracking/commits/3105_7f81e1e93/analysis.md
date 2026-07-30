# 提交 3105：Core: Use scan API to read partition stats (#14989)

## 提交信息

- **序号**：3105 / 4088
- **哈希**：7f81e1e93084e50fa3676c2e131722f66a26b385
- **短哈希**：7f81e1e93
- **日期**：2026-01-12
- **作者**：gaborkaszab
- **提交说明**：Core: Use scan API to read partition stats (#14989)
- **PR/Issue**：#14989

## 总体目的

本提交是分区统计（partition statistics）读写路径演进的收尾步骤，核心目的是让 `PartitionStatsHandler`（分区统计的生产/增量计算器）在增量计算时改用上一阶段新引入的 Scan API 读取历史统计文件，从而淘汰对已废弃的底层直接读取方法的依赖。

背景：在此前的提交 3075（`API, Core: Scan API for partition stats`）中，Iceberg 引入了面向接口的高层读取抽象——`PartitionStatistics` 接口、`PartitionStatisticsScan` 接口及 core 层实现 `BasePartitionStatistics` / `BasePartitionStatisticsScan`，并在 `Table.newPartitionStatisticsScan()` 上提供入口；同时把旧的 `PartitionStats` 类与 `PartitionStatsHandler.readPartitionStatsFile(schema, inputFile)` 方法标记为 `@Deprecated`（计划在 1.12.0 移除）。但 3075 只完成了"对外提供新 API + 标记旧 API 废弃"，`PartitionStatsHandler` 自身的增量计算逻辑仍内部调用废弃的 `readPartitionStatsFile` 直接读文件，并继续以旧的 `PartitionStats` 类型承载统计记录。

本提交补齐这一缺口：把 `PartitionStatsHandler` 内部从"直接读文件 + `PartitionStats`"迁移到"Scan API + `PartitionStatistics`"。具体而言，增量合并历史统计时不再手工构造 schema 并调用 `readPartitionStatsFile`，而是调用 `table.newPartitionStatisticsScan().useSnapshot(lastSnapshotWithStats).scan()`，让 `BasePartitionStatisticsScan` 统一负责快照定位、文件查找与 schema/格式处理。这一改动使生产者侧也完成迁移，使废弃路径不再有内部调用者，为 1.12.0 移除旧 API 扫清障碍，并让读写共用同一套抽象（一致的投影、格式推断与快照语义），减少重复的 schema 构造逻辑。

同时，本提交把统计字段在 schema 中的位置常量（`PARTITION_POSITION` … `DV_COUNT_POSITION`）从测试基类上移到公共 `PartitionStatistics` 接口，作为 API 契约暴露。这是因为新接口只提供通用的 `StructLike.get/set(pos)`，不再像旧 `PartitionStats` 那样内聚专用的累加方法；位置常量上移后，`PartitionStatsHandler` 中的统计累加逻辑得以改为操作接口的静态辅助方法，并用具名常量取代散落的魔法数字，提升可读性与可维护性。

## 如何达成设计目的

整体思路是"用新 Scan API 替换旧直接读 + 用接口替换旧实现类 + 把行为方法外迁为静态工具"：

1. 在 `PartitionStatistics` 接口公开 13 个位置常量，作为字段位置的唯一事实来源；
2. 给 `BasePartitionStatistics` 增加一个 `(partition, specId)` 构造器用于累加路径初始化零值，并把投影构造器收敛为包级可见；
3. 把 `PartitionStatsHandler` 中所有 `PartitionStats` 类型引用改为 `PartitionStatistics`，把原本是 `PartitionStats` 实例方法的 `liveEntry` / `deletedEntry` / `deletedEntryForIncrementalCompute` / `appendStats` 外迁为 handler 内的 `static` 方法，通过接口的 `get/set(pos)` 操作字段；
4. 将 `computeAndMergeStatsIncremental` 的入参从 `PartitionStatisticsFile previousStatsFile` 改为 `long lastSnapshotWithStats`，内部用 `table.newPartitionStatisticsScan().useSnapshot(lastSnapshotWithStats).scan()` 读取历史统计；
5. 同步更新测试基类的类型引用与常量引用。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionStatistics.java` (+15/-0 lines)

**修改目的**：把分区统计字段的 schema 位置常量提升为公共 API 契约。

**工作逻辑**：
新增 13 个 `int` 常量：`PARTITION_POSITION = 0`、`SPEC_ID_POSITION = 1`、`DATA_RECORD_COUNT_POSITION = 2`、`DATA_FILE_COUNT_POSITION = 3`、`TOTAL_DATA_FILE_SIZE_IN_BYTES_POSITION = 4`、`POSITION_DELETE_RECORD_COUNT_POSITION = 5`、`POSITION_DELETE_FILE_COUNT_POSITION = 6`、`EQUALITY_DELETE_RECORD_COUNT_POSITION = 7`、`EQUALITY_DELETE_FILE_COUNT_POSITION = 8`、`TOTAL_RECORD_COUNT_POSITION = 9`、`LAST_UPDATED_AT_POSITION = 10`、`LAST_UPDATED_SNAPSHOT_ID_POSITION = 11`、`DV_COUNT_POSITION = 12`，并注明"每个统计量在完整 schema 中的位置"。此前这些常量（且只覆盖 2-12）以 `protected` 形式散落在 `PartitionStatisticsTestBase` 中；上移到接口后，生产代码（handler 的静态辅助方法）与测试均可通过 `PartitionStatistics.XXX_POSITION` 引用，消除魔法数字并统一位置契约。

### `core/src/main/java/org/apache/iceberg/BasePartitionStatistics.java` (+33/-31 lines, 净 +2)

**修改目的**：为累加路径提供零值初始化构造器，并把位置魔法数字替换为接口常量。

**工作逻辑**：
新增包级构造器 `BasePartitionStatistics(StructLike partition, int specId)`，调用 `super(STATS_COUNT)` 后设置 `partition`/`specId` 并将所有计数成员初始化为 0（`dataRecordCount=0L`、`dataFileCount=0`、`totalDataFileSizeInBytes=0L`、各 delete 计数为 0、`dvCount=0`）。该构造器供 `PartitionStatsHandler.collectStatsForManifest` 在扫描 manifest 累加统计时创建新记录使用——原来用的是 `new PartitionStats(key, specId)`，现改为 `new BasePartitionStatistics(key, specId)`。

同时把原投影构造器从 `public` 收敛为包级 `BasePartitionStatistics(Types.StructType projection)`（仅内部 reader 实例化投影时使用，不应对外暴露）。

`getByPos` / `setByPos` 两个 switch 的所有 `case 0`…`case 12` 全部替换为 `case PARTITION_POSITION` … `case DV_COUNT_POSITION` 等具名常量，逻辑不变，仅提升可读性并与接口契约对齐。

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+219/-35 lines)

**修改目的**：将增量统计的读取路径切换到 Scan API，并把统计累加逻辑从旧 `PartitionStats` 实例方法迁移为操作 `PartitionStatistics` 接口的静态方法。

**工作逻辑**：

- **类型替换**：方法签名与局部变量中所有 `PartitionStats` 改为 `PartitionStatistics`，包括 `computeAndMergeStatsIncremental`、`computeStatsDiff`、`computeStats`、`collectStatsForManifest`、`mergePartitionMap`、`sortStatsByPartition`、`writePartitionStatsFile` 的入参 `Iterable<PartitionStats>` 等。`collectStatsForManifest` 中创建记录改为 `new BasePartitionStatistics(key, specId)`。

- **读取路径迁移（核心）**：`computeAndMergeStatsIncremental` 签名由 `(Table, Snapshot, StructType partitionType, PartitionStatisticsFile previousStatsFile)` 改为 `(Table, Snapshot, long lastSnapshotWithStats)`。原先用 `readPartitionStatsFile(schema(partitionType, TableUtil.formatVersion(table)), table.io().newInputFile(previousStatsFile.path()))` 直接打开文件读 `CloseableIterable<PartitionStats>`；现在改为 `table.newPartitionStatisticsScan().useSnapshot(lastSnapshotWithStats).scan()` 得到 `CloseableIterable<PartitionStatistics>`。这把 schema 构造、文件定位、格式推断全部交给 `BasePartitionStatisticsScan`，handler 不再需要为读取自行构造 schema。`partitionType` 的计算也因此从方法入口下移到仅用于排序的地方（`sortStatsByPartition` 调用处），因为读取阶段不再需要它。后续增量 diff 的起点从 `table.snapshot(previousStatsFile.snapshotId())` 改为 `table.snapshot(lastSnapshotWithStats)`，入参直接传快照 ID 而非整个文件对象，解耦了对文件引用的依赖。

- **累加逻辑外迁为静态方法**：原本是 `PartitionStats` 实例方法的 `liveEntry(file, snapshot)`、`deletedEntry(snapshot)`、`deletedEntryForIncrementalCompute(file, snapshot)`、`appendStats(other)` 被搬移为 handler 的 `private static` 方法，签名改为接收 `PartitionStatistics stats`（及 `ContentFile`/`Snapshot` 等参数），通过接口的 `get`/`set` 与位置常量操作字段：
  - `liveEntry(stats, file, snapshot)`：按 `file.content()` 分支（`DATA`/`POSITION_DELETES`/`EQUALITY_DELETES`），用 `stats.set(DATA_RECORD_COUNT_POSITION, stats.dataRecordCount() + file.recordCount())` 等方式累加；位置删除中按 `file.format() == FileFormat.PUFFIN` 区分 DV 计数与普通位置删除文件计数；最后调用 `updateSnapshotInfo` 更新 `LAST_UPDATED_AT_POSITION` / `LAST_UPDATED_SNAPSHOT_ID_POSITION`（仅当新值更大时更新）。
  - `deletedEntryForIncrementalCompute(stats, file, snapshot)`：与 `liveEntry` 对称地做减法（因增量计算中已删除条目需从历史统计中扣减）。
  - `deletedEntry(stats, snapshot)`：非增量路径下仅更新快照时间信息。
  - `appendStats(targetStats, inputStats)`：把另一条统计的各项累加到目标上，对可空字段（`dvCount`、`totalRecords`、`lastUpdatedAt`）做 null 感知处理（目标为 null 时直接赋值，否则相加/取较大），并通过 `updateSnapshotInfo` 合并最近更新信息。
  - `updateSnapshotInfo(stats, snapshotId, updatedAt)`：当目标 `lastUpdatedAt` 为 null 或小于传入值时更新时间戳与快照 ID。
  
  这些方法在 `collectStatsForManifest` 与 `mergePartitionMap` / `computeAndMergeStatsIncremental` 的合并回调中被调用，例如 `existingEntry.appendStats(newEntry)` 改为 `appendStats(existingEntry, newEntry)`。外迁后，旧 `PartitionStats` 类不再被 handler 使用，仅作为废弃类型保留供外部过渡。

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsTestBase.java` (+14/-16 lines, 净 -2)

**修改目的**：移除本地位置常量，测试改用接口类型与接口常量。

**工作逻辑**：
删除文件顶部的 11 个 `protected static final int` 位置常量（`DATA_RECORD_COUNT_POSITION` … `DV_COUNT_POSITION`）。`randomStats` 的返回类型从 `PartitionStats` 改为 `PartitionStatistics`，内部 `new PartitionStats(partitionData, RANDOM.nextInt(10))` 改为 `new BasePartitionStatistics(partitionData, RANDOM.nextInt(10))`，对位置的 `set` 调用改用 `PartitionStatistics.DATA_RECORD_COUNT_POSITION` 等接口常量。由于常量上移到接口，子类测试不再需要自带定义。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (+37/-30 lines, 净 +7)

**修改目的**：同步测试到新接口类型，并新增跨类型比较辅助方法。

**工作逻辑**：
将 `partitionStats`、`expected`、`partitionListBuilder` 等局部变量与列表的类型从 `PartitionStats` 改为 `PartitionStatistics`，所有 `set(POSITION_DELETE_RECORD_COUNT_POSITION, null)` 等调用改为 `set(PartitionStatistics.POSITION_DELETE_RECORD_COUNT_POSITION, null)`，断言中 `PartitionStats::positionDeleteRecordCount` 等方法引用改为 `PartitionStatistics::positionDeleteRecordCount`。新增 `private static boolean isEqual(Comparator<StructLike> partitionComparator, PartitionStats stats1, PartitionStatistics stats2)` 方法，逐字段（partition 经比较器比较、specId、各计数、可空的 totalRecords/lastUpdatedAt/lastUpdatedSnapshotId）比较旧 `PartitionStats` 与新 `PartitionStatistics` 两个对象，作为新旧类型并存过渡期用于跨类型断言的桥接工具（带 `@SuppressWarnings("checkstyle:CyclomaticComplexity")` 因字段多导致圈复杂度高）。

## 总结

本提交完成分区统计生产者侧的 Scan API 迁移：`PartitionStatsHandler` 增量计算时改用 `table.newPartitionStatisticsScan().useSnapshot(...).scan()` 读取历史统计，取代废弃的 `readPartitionStatsFile` 直接读文件；内部承载类型从旧 `PartitionStats` 切换到新 `PartitionStatistics` 接口；统计累加逻辑从旧类的实例方法外迁为 handler 的静态辅助方法，并用具名位置常量（上移到 `PartitionStatistics` 接口）替代魔法数字。该改动使读写共用同一套抽象、消除重复 schema 构造、令废弃路径不再有内部调用者，为 1.12.0 移除旧 API 铺路，同时提升代码可读性与一致性。
