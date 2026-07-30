# 提交 0284：API, Core: Track partition statistics in TableMetadata (#8502)

## 提交信息

- **序号**：0284 / 4088
- **哈希**：6e21bbf4c4cd2c8351a64636f91d05d00492dff2
- **短哈希**：6e21bbf4c
- **日期**：2023-12-18
- **作者**：Ajantha Bhat
- **提交说明**：API, Core: Track partition statistics in TableMetadata (#8502)
- **PR/Issue**：#8502

## 总体目的

Iceberg 在本次提交之前已经支持 snapshot 级别的 statistics 文件（`StatisticsFile`，通常以 Puffin 格式存储诸如 ndv 之类的统计信息），并把这些 statistics 文件作为 `TableMetadata` 的一部分持久化在 metadata.json 中，同时通过 `updateStatistics()` API 进行增删、随 `ExpireSnapshots` 一起清理。然而 partition 级别的 statistics（按分区聚合的数据文件计数、记录数、文件大小等信息，可用于查询计划时跳过空分区、优化 split 分配、加速元数据表扫描等）此前并没有一个统一的、被 table metadata 跟踪的载体。这意味着引擎或外部工具生成的 partition statistics 文件无法被 Iceberg 自己管理生命周期，也无法通过 metadata 一站式发现。

本次提交的目标是在 Iceberg 的 API、Core 层引入"partition statistics 文件"这一等价的、可被 TableMetadata 持久化跟踪的对象，使其具备与现有 `StatisticsFile` 一致的能力：可通过公开 API 增删、随 metadata.json 序列化/反序列化、作为 `MetadataUpdate` 在事务中流转、并在快照过期时被自动清理。这是为后续引擎（Spark/Flink 等）利用 partition statistics 优化查询打地基的基础设施改动，本身不实现 partition statistics 的写入与读取逻辑，只完成"被跟踪"的能力。

## 如何达成设计目的

整体方案是参照已有 `StatisticsFile`/`SetStatistics`/`MetadataUpdate.SetStatistics`/`StatisticsFileParser` 的设计镜像一套 partition statistics 的对应物：新增 `PartitionStatisticsFile` 接口（仅含 `snapshotId`、`path`、`fileSizeInBytes`）、`UpdatePartitionStatistics` PendingUpdate 接口、`SetPartitionStatistics` 实现、`GenericPartitionStatisticsFile` Immutable value type、`PartitionStatisticsFileParser` JSON 序列化器、`MetadataUpdate.SetPartitionStatistics/RemovePartitionStatistics` 两个 update 类型，并在 `MetadataUpdateParser` 与 `TableMetadataParser` 中加入对应分支。`TableMetadata` 新增 `partitionStatisticsFiles` 字段并在 Builder 中提供 set/remove 方法、在 `removeSnapshots` 中联动移除、在构造函数中接收新参数。`Table`/`Transaction` 接口及所有核心实现（`BaseTable`/`BaseTransaction`/`CommitCallbackTransaction`/`BaseMetadataTable`/`BaseReadOnlyTable`/`SerializableTable`）都新增 `updatePartitionStatistics()` 与 `partitionStatisticsFiles()` 方法。最后扩展 `FileCleanupStrategy` 的清理逻辑使其同时考虑 partition statistics 文件，并补充多个层次的测试。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionStatisticsFile.java`（新增）

**修改目的**：定义 partition statistics 文件的公开接口。

**工作逻辑**：定义 `interface PartitionStatisticsFile`，三个方法：`long snapshotId()`（关联的快照 ID）、`String path()`（文件全路径，never null）、`long fileSizeInBytes()`（文件字节数）。接口 Javadoc 强调 statistics 是 informational 的——reader 可以选择忽略，statistics 不是正确读取表的必要条件。

### `api/src/main/java/org/apache/iceberg/UpdatePartitionStatistics.java`（新增）

**修改目的**：定义更新 partition statistics 文件的 PendingUpdate API。

**工作逻辑**：`interface UpdatePartitionStatistics extends PendingUpdate<List<PartitionStatisticsFile>>`，两个方法：
- `setPartitionStatistics(PartitionStatisticsFile)`：为某 snapshot 设置 partition statistics 文件，替换该 snapshot 已有的 partition statistics 文件。
- `removePartitionStatistics(long snapshotId)`：移除某 snapshot 对应的 partition statistics 文件。

### `api/src/main/java/org/apache/iceberg/Table.java`

**修改目的**：在 Table 接口暴露 partition statistics 的访问与更新入口。

**工作逻辑**：
- 新增 `default UpdatePartitionStatistics updatePartitionStatistics()`，默认抛 `UnsupportedOperationException`（与 `updateStatistics` 模式一致，保证向后兼容）。
- 新增 `default List<PartitionStatisticsFile> partitionStatisticsFiles()`，默认返回 `ImmutableList.of()`，让旧实现无需修改也能编译。
- 引入 `ImmutableList` import。

### `api/src/main/java/org/apache/iceberg/Transaction.java`

**修改目的**：在 Transaction 接口暴露 partition statistics 的更新入口。

**工作逻辑**：新增 `default UpdatePartitionStatistics updatePartitionStatistics()`，默认抛 `UnsupportedOperationException`，与 `updateStatistics` 模式一致。

### `core/src/main/java/org/apache/iceberg/GenericPartitionStatisticsFile.java`（新增）

**修改目的**：提供 `PartitionStatisticsFile` 的 Immutable value type 实现，便于通过 builder 创建不可变实例。

**工作逻辑**：使用 `@Value.Immutable` 注解定义 `interface GenericPartitionStatisticsFile extends PartitionStatisticsFile`，由 Immutables 生成 `ImmutableGenericPartitionStatisticsFile` 与 builder。这与 `GenericStatisticsFile` 风格一致。

### `core/src/main/java/org/apache/iceberg/SetPartitionStatistics.java`（新增）

**修改目的**：实现 `UpdatePartitionStatistics`，提供 partition statistics 文件的增删与提交。

**工作逻辑**：
- 持有 `TableOperations ops`、`Map<Long, PartitionStatisticsFile> statsToSet`（按 snapshotId 索引待设置项）、`Set<Long> statsToRemove`（待移除的 snapshotId）。
- `setPartitionStatistics(file)`：参数校验非空后 `statsToSet.put(file.snapshotId(), file)`，同一 snapshotId 后设覆盖先设。
- `removePartitionStatistics(snapshotId)`：`statsToRemove.add(snapshotId)`。
- `apply()`：返回 `internalApply(ops.current()).partitionStatisticsFiles()`。
- `commit()`：取 `base = ops.current()`，计算 `newMetadata = internalApply(base)`，调用 `ops.commit(base, newMetadata)`。
- `internalApply(base)`：`TableMetadata.buildFrom(base)`，对所有 `statsToSet.values()` 调 `builder::setPartitionStatistics`，对所有 `statsToRemove` 调 `builder::removePartitionStatistics`，最后 `build()`。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java`

**修改目的**：新增 partition statistics 的 MetadataUpdate 类型，使其能在事务 changes 列表与 metadata 增量更新中流转。

**工作逻辑**：
- 新增 `class SetPartitionStatistics implements MetadataUpdate`：持有一个 `PartitionStatisticsFile`，`applyTo(builder)` 调用 `builder.setPartitionStatistics(partitionStatisticsFile)`；提供 `snapshotId()` 与 `partitionStatisticsFile()` 访问器。
- 新增 `class RemovePartitionStatistics implements MetadataUpdate`：持有 `long snapshotId`，`applyTo(builder)` 调用 `builder.removePartitionStatistics(snapshotId)`；提供 `snapshotId()` 访问器。

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java`

**修改目的**：扩展 metadata update 的 JSON 序列化，支持新增的两个 partition statistics update。

**工作逻辑**：
- 新增两个 action 常量 `SET_PARTITION_STATISTICS = "set-partition-statistics"`、`REMOVE_PARTITION_STATISTICS = "remove-partition-statistics"`，以及字段名常量 `PARTITION_STATISTICS = "partition-statistics"`。
- 在 class→action 与 action→class 的映射表中注册两个新 update 类型。
- `write`/`read` 分支中新增两个 case：
  - `writeSetPartitionStatistics`：写 `partition-statistics` 字段，委托 `PartitionStatisticsFileParser.toJson(file, gen)`。
  - `writeRemovePartitionStatistics`：写 `snapshot-id` 字段。
  - `readSetPartitionStatistics`：从 `partition-statistics` 节点用 `PartitionStatisticsFileParser.fromJson` 解析，构造 `SetPartitionStatistics`。
  - `readRemovePartitionStatistics`：读 `snapshot-id` 构造 `RemovePartitionStatistics`。

### `core/src/main/java/org/apache/iceberg/PartitionStatisticsFileParser.java`（新增）

**修改目的**：提供 `PartitionStatisticsFile` 的 JSON 序列化/反序列化。

**工作逻辑**：
- 字段常量：`SNAPSHOT_ID = "snapshot-id"`、`STATISTICS_PATH = "statistics-path"`、`FILE_SIZE_IN_BYTES = "file-size-in-bytes"`。
- `toJson(file)` / `toJson(file, pretty)` 通过 `JsonUtil.generate` 生成字符串。
- `toJson(file, generator)` 写一个对象：`snapshot-id`、`statistics-path`、`file-size-in-bytes` 三字段。
- `fromJson(node)` 读三字段，通过 `ImmutableGenericPartitionStatisticsFile.builder()` 构造实例。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：把 `partitionStatisticsFiles` 纳入 TableMetadata 持久化状态与 Builder 变更跟踪。

**工作逻辑**：
- 主类新增 `private final List<PartitionStatisticsFile> partitionStatisticsFiles`，构造函数新增对应参数并用 `ImmutableList.copyOf(...)` 保存，新增 `partitionStatisticsFiles()` 访问器。
- Builder 新增 `private final Map<Long, List<PartitionStatisticsFile>> partitionStatisticsFiles`（按 `snapshotId` 分组）。
  - `buildFrom(base)` 时调用新静态方法 `indexPartitionStatistics(base.partitionStatisticsFiles)` 进行分组（同时也把原来 `statisticsFiles` 的分组逻辑抽到 `indexStatistics` 静态方法，统一风格）。
  - 新增 `setPartitionStatistics(file)`：`partitionStatisticsFiles.put(file.snapshotId(), ImmutableList.of(file))`（覆盖该 snapshotId 下已有项），并 `changes.add(new MetadataUpdate.SetPartitionStatistics(file))`。
  - 新增 `removePartitionStatistics(snapshotId)`：若 map 中无该 key 直接返回，否则移除并 `changes.add(new MetadataUpdate.RemovePartitionStatistics(snapshotId))`。
  - `removeSnapshots(snapshotsToRemove)` 在删除过期 snapshot 时，除了既有的 `removeStatistics(snapshotId)`，再调用 `removePartitionStatistics(snapshotId)`，让 partition statistics 文件随快照过期一起被移除。
  - `build()` 末尾把 `partitionStatisticsFiles.values()` 拍平为 List 传入 `TableMetadata` 构造函数。
- 顺带删除 `removeStatistics` 中无意义的 `Preconditions.checkNotNull(snapshotId, ...)`——`snapshotId` 是 `long` primitive，不可能为 null。

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java`

**修改目的**：在 metadata.json 的读写中加入 `partition-statistics` 字段。

**工作逻辑**：
- 新增常量 `PARTITION_STATISTICS = "partition-statistics"`。
- 写入：在 `statistics` 数组之后写 `partition-statistics` 数组，遍历 `metadata.partitionStatisticsFiles()` 调用 `PartitionStatisticsFileParser.toJson(file, generator)`。
- 读取：若 node 含 `PARTITION_STATISTICS`，调用新增的 `partitionStatsFilesFromJson(node.get(PARTITION_STATISTICS))`，否则用 `ImmutableList.of()`。`partitionStatsFilesFromJson` 校验是数组后遍历调用 `PartitionStatisticsFileParser.fromJson`。
- 把 `partitionStatisticsFiles` 传入 `TableMetadata` 构造函数。

### `core/src/main/java/org/apache/iceberg/BaseTable.java`

**修改目的**：在标准表实现上暴露 partition statistics 能力。

**工作逻辑**：
- 实现 `updatePartitionStatistics()`：返回 `new SetPartitionStatistics(ops)`。
- 实现 `partitionStatisticsFiles()`：返回 `ops.current().partitionStatisticsFiles()`。

### `core/src/main/java/org/apache/iceberg/BaseReadOnlyTable.java`

**修改目的**：在只读表基类中显式禁用 partition statistics 更新。

**工作逻辑**：实现 `updatePartitionStatistics()` 抛 `UnsupportedOperationException("Cannot update partition statistics of a " + descriptor + " table")`，与既有的 `updateStatistics` 一致。`partitionStatisticsFiles()` 则沿用 `Table` 接口的默认实现（返回 `ImmutableList.of()`）。

### `core/src/main/java/org/apache/iceberg/BaseMetadataTable.java`

**修改目的**：在元数据表（如 `ManifestsTable` 等）中返回空 partition statistics。

**工作逻辑**：覆写 `partitionStatisticsFiles()` 返回 `ImmutableList.of()`，因为元数据表本身不持有 partition statistics。`updatePartitionStatistics()` 沿用 `BaseReadOnlyTable` 抛异常的实现。

### `core/src/main/java/org/apache/iceberg/BaseTransaction.java`

**修改目的**：在事务上下文中暴露 partition statistics 更新与读取。

**工作逻辑**：
- `BaseTransaction` 实现 `updatePartitionStatistics()`：`checkLastOperationCommitted("UpdatePartitionStatistics")`，创建 `new SetPartitionStatistics(transactionOps)`，加入 `updates` 列表后返回。
- 内部 `TransactionTable` 同时转发 `updatePartitionStatistics()` 到外层 `BaseTransaction.this.updatePartitionStatistics()`，并转发 `partitionStatisticsFiles()` 到 `current.partitionStatisticsFiles()`。

### `core/src/main/java/org/apache/iceberg/CommitCallbackTransaction.java`

**修改目的**：让 commit 回调包装事务也能转发 partition statistics 调用。

**工作逻辑**：实现 `updatePartitionStatistics()` 委托给 `wrapped.updatePartitionStatistics()`。

### `core/src/main/java/org/apache/iceberg/SerializableTable.java`

**修改目的**：让可序列化表代理也能访问 partition statistics，但禁止更新。

**工作逻辑**：
- 实现 `partitionStatisticsFiles()` 委托给 `lazyTable().partitionStatisticsFiles()`。
- 实现 `updatePartitionStatistics()` 抛 `UnsupportedOperationException(errorMsg("updatePartitionStatistics"))`，因为 `SerializableTable` 主要用于序列化传输到 executor，更新操作不支持。

### `core/src/main/java/org/apache/iceberg/FileCleanupStrategy.java`

**修改目的**：让快照过期清理逻辑同时识别与收集 partition statistics 文件。

**工作逻辑**：
- 新增 `protected boolean hasAnyStatisticsFiles(TableMetadata tableMetadata)`：返回 `!statisticsFiles().isEmpty() || !partitionStatisticsFiles().isEmpty()`，供子类判断是否需要清理 statistics 文件。
- 扩展 `statsFileLocations(tableMetadata)`：原来只收集 `StatisticsFile::path`，现在改为先把 `statisticsFiles` 路径加进 `Set<String>`，再把 `partitionStatisticsFiles` 路径也加进去。同时移除了不再需要的 `Collectors.toSet()` import。

### `core/src/main/java/org/apache/iceberg/IncrementalFileCleanup.java` 与 `core/src/main/java/org/apache/iceberg/ReachableFileCleanup.java`

**修改目的**：把清理前的判断从"仅看 statisticsFiles"扩展到"看任意 statistics 文件"。

**工作逻辑**：把 `if (!beforeExpiration.statisticsFiles().isEmpty())` 改为 `if (hasAnyStatisticsFiles(beforeExpiration))`，这样当只有 partition statistics 文件需要清理时也会进入清理分支，复用 `expiredStatisticsFilesLocations(beforeExpiration, afterExpiration)` 计算需要删除的路径。

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java`

**修改目的**：覆盖新增的两个 metadata update 类型的 JSON 序列化/反序列化。

**工作逻辑**：
- 新增 `testSetPartitionStatistics`：验证 `{"action":"set-partition-statistics","partition-statistics":{...}}` 的 fromJson/toJson 双向一致性。
- 新增 `testRemovePartitionStatistics`：验证 `{"action":"remove-partition-statistics","snapshot-id":...}` 的双向一致性。
- 在 `assertEquals(action, expected, actual)` 分发中新增两个 case，分别调用新增的 `assertEqualsSetPartitionStatistics` 与 `assertEqualsRemovePartitionStatistics` 私有方法，比较 snapshotId/path/fileSizeInBytes。

### `core/src/test/java/org/apache/iceberg/TestSetPartitionStatistics.java`（新增）

**修改目的**：端到端验证 `UpdatePartitionStatistics` API 在直接 commit 与 transaction 两种使用方式下的行为。

**工作逻辑**：参数化跑 formatVersion=1/2。
- `testEmptyUpdateStatistics`：直接 `table.updatePartitionStatistics().commit()`，验证 metadata 版本+1 但 base metadata 实例不变（无变更仍生成新 metadata 文件）。
- `testEmptyTransactionalUpdateStatistics`：通过 transaction 调用空更新并提交，验证 metadata 版本不变（transaction 在无变更时不 commit）。
- `testUpdateStatistics`：append 一个 snapshot 后，构造 `PartitionStatisticsFile` 调用 `setPartitionStatistics().commit()`，验证 metadata 版本+1、current snapshot 不变、`metadata.partitionStatisticsFiles()` 包含设置项。
- `testRemoveStatistics`：在 set 之后再 `removePartitionStatistics(snapshotId).commit()`，验证 metadata 版本再+1、`partitionStatisticsFiles()` 变回空。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java`

**修改目的**：覆盖 TableMetadata 与 TableMetadataParser 对 partition statistics 的处理。

**工作逻辑**：
- 多处既有 `new TableMetadata(...)` 调用补上 `ImmutableList.of()` 作为新的 `partitionStatisticsFiles` 参数（构造函数签名变了，所有调用点都要改）。
- `testJsonConversion`：构造带一个 partition statistics file 的 metadata，toJson 后 fromJson，验证 `metadata.partitionStatisticsFiles()` 与原 list 相等。
- `testPartitionStatistics`：验证新建 metadata 默认 `partitionStatisticsFiles()` 为空。
- `testSetPartitionStatistics`：通过 Builder `setPartitionStatistics` 设置一项，再 `setPartitionStatistics` 同 snapshotId 用不同 path/size 覆盖，验证 `partitionStatisticsFiles()` 始终只有一项且为最新值。
- `testRemovePartitionStatistics`：先设置两项（snapshotId 43/44），再 `removePartitionStatistics(42)`（不存在的 id）返回原 metadata（assertSame），再 `removePartitionStatistics(43)` 后只剩 snapshotId 44 的项。
- `testParsePartitionStatisticsFiles`：从新增的 JSON 资源文件 `TableMetadataPartitionStatisticsFiles.json` 读取，验证解析出的 partition statistics file 字段（snapshotId=3055729675574597004L，path=`s3://a/b/partition-stats.parquet`，fileSizeInBytes=43）。

### `core/src/test/resources/TableMetadataPartitionStatisticsFiles.json`（新增）

**修改目的**：为 `testParsePartitionStatisticsFiles` 提供输入 fixture。

**工作逻辑**：一份完整的 v2 metadata JSON，含一个 snapshot 与一个 `partition-statistics` 数组项（`snapshot-id`、`statistics-path`、`file-size-in-bytes`）。

### `core/src/test/java/org/apache/iceberg/TestRemoveSnapshots.java`

**修改目的**：验证 `ExpireSnapshots` 在有过期 partition statistics 文件时能正确从 metadata 中移除并从文件系统删除，且复用场景下不误删。

**工作逻辑**：
- 引入 `UncheckedIOException`、`PositionOutputStream` 等用于创建物理文件。
- 顺手把已有 `new File(statsFileLocation1).exists()).isFalse()` 改为更地道的 `assertThat(new File(statsFileLocation1)).doesNotExist()` / `.exists()`。
- 新增 `testExpireWithPartitionStatisticsFiles`：
  - 创建两个 snapshot，分别为其 commit 一个 partition statistics 文件（用辅助方法 `writePartitionStatsFile` 在 statsLocation 上创建一个空文件并返回 `PartitionStatisticsFile`，再用 `commitPartitionStats` 走 `table.updatePartitionStatistics().setPartitionStatistics(file).commit()`）。
  - `expireOlderThan(tAfterCommits)` 后只保留当前 snapshot，验证 `table.partitionStatisticsFiles()` 只剩 statisticsFile2，且 statsFileLocation1 物理文件被删除、statsFileLocation2 仍存在。
- 新增 `testExpireWithPartitionStatisticsFilesWithReuse`：
  - 第二个 snapshot 复用第一个的 partition statistics 文件路径（通过 `reusePartitionStatsFile` 构造同 path 的 `PartitionStatisticsFile`）。
  - 过期掉第一个 snapshot 后，验证只剩 statisticsFile2（snapshotId 是第二个的，path 是复用的），且复用的物理文件仍存在（因为 live snapshot 仍引用）。
- 新增辅助方法 `writePartitionStatsFile`、`reusePartitionStatsFile`、`commitPartitionStats`。

## 小结

本次提交通过在 API 层新增 `PartitionStatisticsFile`/`UpdatePartitionStatistics` 接口、在 Core 层新增 `GenericPartitionStatisticsFile`/`SetPartitionStatistics`/`PartitionStatisticsFileParser` 与两个 `MetadataUpdate`、扩展 `TableMetadata`/`TableMetadataParser`/`MetadataUpdateParser` 持久化与流转 partition statistics 文件、让所有 Table/Transaction 实现转发新接口、并扩展 `FileCleanupStrategy` 与两个 cleanup 子类在快照过期时一并清理，建立了 partition statistics 文件被 Iceberg table metadata 全生命周期跟踪的能力，为后续引擎利用 partition statistics 优化查询打下基础；同时通过 parser 测试、TableMetadata 测试、`TestSetPartitionStatistics` 端到端测试与 `TestRemoveSnapshots` 过期清理测试覆盖了正确性。
