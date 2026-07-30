# 提交 1803：Data: Add partition stats writer and reader (#11216)

## 提交信息

- **序号**：1803 / 4088
- **哈希**：e230f5d79d82a50439029db5c73f8b59497b2e9f
- **短哈希**：e230f5d79
- **日期**：2025-02-28 14:21:53 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Data: Add partition stats writer and reader (#11216)
- **PR/Issue**：#11216

## 总体目的

Iceberg 支持分区统计（Partition Statistics）功能，用于记录每个分区的数据文件数、记录数、文件大小、删除文件数等聚合统计信息，帮助查询引擎在计划阶段跳过不必要的分区。此前已有 `PartitionStats` 数据模型和 `PartitionStatsUtil` 计算工具，以及 `PartitionStatisticsFile` 元数据模型和 `SetPartitionStatistics`/`UpdatePartitionStatistics` API，但缺少实际的“将分区统计写入文件”和“从文件读取分区统计”的实现。

本提交新增 `PartitionStatsHandler` 类，提供分区统计文件的写入和读取能力：
1. 根据表的统一分区类型生成统计文件 schema。
2. 计算并写入分区统计文件（支持表默认文件格式 Parquet/Avro，ORC 暂不支持内部读写器）。
3. 读取分区统计文件并转换为 `PartitionStats` 对象。

同时调整了相关 API 和测试基础设施以配合此功能：`UpdatePartitionStatistics`/`SetPartitionStatistics` 允许传入 null 文件（no-op），`TestTables` 增加了支持自定义属性和分区规格的建表重载。

## 如何达成设计目的

整体设计采用“通用读写器”方案，通过 `PartitionStatsHandler` 静态工具类封装：
1. **Schema 生成**：`schema(StructType unifiedPartitionType)` 方法根据统一分区类型生成统计文件 schema，包含分区值、spec id 以及各项统计指标列。
2. **写入流程**：`computeAndWriteStatsFile(table, branch)` → 获取当前快照 → `PartitionStatsUtil.computeStats` 计算统计 → 排序 → `writePartitionStatsFile` 用 `DataWriter` 按表默认格式写入 → 返回 `PartitionStatisticsFile` 元数据。
3. **读取流程**：`readPartitionStatsFile(schema, inputFile)` → 用 `dataReader` 按文件格式读取 → 转换 `StructLike` 为 `PartitionStats`。
4. **格式支持**：通过 `dataWriter`/`dataReader` 内部 switch 支持 Parquet 和 Avro，ORC 因内部读写器未实现而抛出 `UnsupportedOperationException`。
5. **列定义**：用 `Column` 枚举统一管理统计文件各列的 id 和名称，避免魔数。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/PartitionStatsHandler.java`（新增, +284 lines）

**修改目的**：提供分区统计文件的 schema 生成、写入和读取能力。

**工作逻辑**：
- **`Column` 枚举**：定义 12 个列（PARTITION、SPEC_ID、DATA_RECORD_COUNT、DATA_FILE_COUNT、TOTAL_DATA_FILE_SIZE_IN_BYTES、POSITION_DELETE_RECORD_COUNT、POSITION_DELETE_FILE_COUNT、EQUALITY_DELETE_RECORD_COUNT、EQUALITY_DELETE_FILE_COUNT、TOTAL_RECORD_COUNT、LAST_UPDATED_AT、LAST_UPDATED_SNAPSHOT_ID），每个列带 id。
- **`schema(StructType)`**：根据统一分区类型生成 Schema，分区列 required，spec id 和数据统计基础列 required，删除相关和 last_updated 列 optional。
- **`computeAndWriteStatsFile(table)`/`computeAndWriteStatsFile(table, branch)`**：获取指定分支的最新快照，若为 null 且是主分支则返回 null（非主分支抛异常），否则计算统计、排序、写入文件。统计为空时返回 null。
- **`writePartitionStatsFile(table, snapshotId, dataSchema, records)`**：根据表属性确定文件格式（默认 parquet），生成输出文件路径 `partition-stats-<snapshotId>-<uuid>.<ext>`（位于 metadata 目录），用 `DataWriter` 写入所有记录，返回 `PartitionStatisticsFile`（含 snapshotId、path、fileSizeInBytes）。
- **`readPartitionStatsFile(schema, inputFile)`**：根据文件名后缀判断格式，用对应 reader 读取为 `CloseableIterable<StructLike>`，再转换为 `CloseableIterable<PartitionStats>`。
- **`dataWriter`/`dataReader`**：按格式 switch，Parquet 用 `InternalWriter`/`InternalReader`，Avro 用 `InternalWriter`/`InternalReader`（avro 包），ORC 抛 `UnsupportedOperationException`。
- **`recordToPartitionStats`**：从 `StructLike` 逐列提取值，构造 `PartitionStats` 并设置各统计字段。

### `api/src/main/java/org/apache/iceberg/UpdatePartitionStatistics.java`（修改, ±1 lines）

**修改目的**：更新接口文档说明允许 null 文件。

**工作逻辑**：在 `setPartitionStatistics` 方法的 Javadoc 中补充 “No-op if the provided file is null.”，说明传入 null 时为空操作。

### `core/src/main/java/org/apache/iceberg/SetPartitionStatistics.java`（修改, +4 -2 lines）

**修改目的**：允许 `setPartitionStatistics` 接收 null 文件。

**工作逻辑**：将原来的 `Preconditions.checkArgument(null != file, ...)` 校验改为 `if (file == null) { return this; }`，即 null 时直接返回 this（no-op），不再抛异常。同时移除不再需要的 `Preconditions` import。这与 `PartitionStatsHandler.computeAndWriteStatsFile` 在无统计时返回 null 的行为配合——调用方可将 null 直接传给 `setPartitionStatistics` 而不会报错。

### `core/src/test/java/org/apache/iceberg/TestTables.java`（修改, +38 -25 lines）

**修改目的**：重构 `TestTables` 建表方法，支持自定义属性和分区规格，为分区统计测试提供基础设施。

**工作逻辑**：
- 将原有无 spec 参数的 `create(temp, name, schema, properties, formatVersion)` 方法改为 `create(temp, name, schema, spec, sortOrder, formatVersion)`。
- 将原有 `create(temp, name, schema, spec, sortOrder, formatVersion)` 方法改为 `create(temp, name, schema, spec, sortOrder, formatVersion, reporter)`。
- 新增 `create(temp, name, schema, spec, formatVersion, properties)` 重载。
- 新增私有 `createTable(temp, name, schema, spec, formatVersion, properties, sortOrder, reporter)` 统一实现建表逻辑，支持自定义属性、分区规格、排序顺序和 MetricsReporter，reporter 为 null 时用无 reporter 构造器。各公共重载委托给此方法。

### `core/src/test/java/org/apache/iceberg/TestRowLineageMetadata.java`（修改, +3 -2 lines）

**修改目的**：适配 `TestTables.create` 方法签名变更。

**工作逻辑**：将原调用 `TestTables.create(tableDir, "test", TEST_SCHEMA, ImmutableMap.of(ROW_LINEAGE, "true"), formatVersion)` 改为 `TestTables.create(tableDir, "test", TEST_SCHEMA, PartitionSpec.unpartitioned(), formatVersion, ImmutableMap.of(ROW_LINEAGE, "true"))`，使用新的带 spec 和 properties 的重载。

### `data/src/test/java/org/apache/iceberg/data/TestPartitionStatsHandler.java`（新增, +598 lines）

**修改目的**：为 `PartitionStatsHandler` 提供全面的测试覆盖。

**工作逻辑**：
- 使用参数化测试，参数为 `FileFormat`（PARQUET、ORC、AVRO）。
- 定义测试 schema（c1 int, c2 string, c3 string）和分区规格（identity c2, identity c3）。
- 测试场景包括：
  - 空表、空分支、无效分支的统计计算。
  - 有数据文件时的统计写入和读取验证。
  - 包含 position delete 和 equality delete 文件时的统计。
  - 多分区、多 spec 的统计。
  - 文件格式为 ORC 时的 `UnsupportedOperationException` 断言（使用 `Assumptions` 跳过 ORC 的读写测试）。
  - 统计文件路径、大小等元数据的正确性。

## 小结

- **成效**：新增 `PartitionStatsHandler` 实现了分区统计文件的写入和读取，补全了分区统计功能链路中的关键一环；同时调整 API 允许 null 统计文件、扩展测试基础设施。
- **影响范围**：涉及 `api`、`core`、`data` 三个模块。新增 `PartitionStatsHandler`（data 模块）、`TestPartitionStatsHandler`（data 模块测试），修改 `UpdatePartitionStatistics`（api）、`SetPartitionStatistics`（core）、`TestTables`/`TestRowLineageMetadata`（core 测试）。支持 Parquet 和 Avro 格式，ORC 暂不支持。
- **回迁到 1.4.x 的注意事项**：建议回迁，但需注意前置依赖：
  - 1.4.x 分支必须已存在 `PartitionStats`、`PartitionStatsUtil`、`PartitionStatisticsFile`、`ImmutableGenericPartitionStatisticsFile` 等类型，否则无法编译。若 1.4.x 缺少这些前置类，需先回迁它们。
  - `TestTables` 的改动与提交 1802（TestTable 清理）有交集，回迁时需注意合并冲突。
  - ORC 不支持是预期行为，回迁后 ORC 表的分区统计会抛 `UnsupportedOperationException`，需确认 1.4.x 上游调用方能处理此情况。
  - `SetPartitionStatistics` 允许 null 的改动是行为变更（原抛异常现 no-op），需确认 1.4.x 上无依赖原异常行为的调用方。
