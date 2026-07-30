# 提交 3075：API, Core: Scan API for partition stats (#14640)

## 提交信息

- **序号**：3075 / 4088
- **哈希**：51d548a4fc101df3494e17a5f7ff5c615c5979cc
- **短哈希**：51d548a4f
- **日期**：2026-01-07
- **作者**：gaborkaszab
- **提交说明**：API, Core: Scan API for partition stats (#14640)
- **PR/Issue**：#14640

## 总体目的

本提交为 Iceberg 新增分区统计的 Scan API，提供一种标准化的、面向接口的方式来读取已持久化的分区统计文件（partition statistics file）。Iceberg 此前已具备分区统计的写入能力（通过 `PartitionStatsHandler.computeAndWriteStatsFile` 计算并提交分区统计文件，以及 `updatePartitionStatistics` API），但读取这些统计文件的方式较为底层——用户需要直接调用 `PartitionStatsHandler.readPartitionStatsFile(schema, inputFile)`，自行处理 schema 构建和文件 IO，缺乏统一的高层抽象。

本提交引入两个新的 API 接口：`PartitionStatistics`（分区统计记录接口）和 `PartitionStatisticsScan`（扫描配置接口），以及 core 层的实现类 `BasePartitionStatistics` 和 `BasePartitionStatisticsScan`。用户现在可以通过 `table.newPartitionStatisticsScan().scan()` 获取 `CloseableIterable<PartitionStatistics>`，无需关心底层文件格式和 schema 细节。Scan API 支持 `useSnapshot` 选择特定快照的统计文件（虽然 `filter` 和 `project` 当前抛出 `UnsupportedOperationException`，为未来扩展预留接口）。

同时，本提交将旧的 `PartitionStats` 类和 `PartitionStatsHandler.readPartitionStatsFile` 方法标记为 `@Deprecated`（将在 1.12.0 移除），推动用户迁移到新的 Scan API。新的 `BasePartitionStatistics` 基于 `SupportsIndexProjection`，支持投影读取，比旧的 `PartitionStats`（实现 `StructLike`）更灵活。

## 如何达成设计目的

在 API 层定义 `PartitionStatistics` 接口（继承 `StructLike`，包含 13 个统计字段）和 `PartitionStatisticsScan` 接口（含 `useSnapshot`、`filter`、`project`、`scan` 方法），并在 `Table` 接口新增 `newPartitionStatisticsScan()` 默认方法。在 core 层实现 `BasePartitionStatistics`（基于 `SupportsIndexProjection` 的记录类）和 `BasePartitionStatisticsScan`（根据快照 ID 查找统计文件、用 `InternalData` 读取），并在 `BaseTable` 中覆写 `newPartitionStatisticsScan()`。测试方面，新建 `PartitionStatisticsTestBase`（提取公共测试基础设施）和 `PartitionStatisticsScanTestBase`（Scan API 测试），以及 Avro/Parquet/ORC 三种格式的具体测试类。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionStatistics.java` (+65/-0 lines, 新文件)

**修改目的**：定义分区统计记录的 API 接口。

**工作逻辑**：
`PartitionStatistics` 继承 `StructLike`，定义 13 个访问方法：`partition()`（分区值）、`specId()`（分区规范 ID）、`dataRecordCount()`（数据记录数）、`dataFileCount()`（数据文件数）、`totalDataFileSizeInBytes()`（数据文件总字节数）、`positionDeleteRecordCount()`（位置删除记录数，含 DV 记录数）、`positionDeleteFileCount()`、`equalityDeleteRecordCount()`、`equalityDeleteFileCount()`、`totalRecords()`（总记录数，当前未计算，保持 null）、`lastUpdatedAt()`（最后更新时间戳）、`lastUpdatedSnapshotId()`（最后更新快照 ID）、`dvCount()`（删除向量数）。

### `api/src/main/java/org/apache/iceberg/PartitionStatisticsScan.java` (+59/-0 lines, 新文件)

**修改目的**：定义分区统计扫描的 API 接口。

**工作逻辑**：
定义四个方法：`useSnapshot(long snapshotId)` 指定快照、`filter(Expression filter)` 过滤分区（当前未实现）、`project(Schema schema)` 投影列（当前未实现）、`scan()` 返回 `CloseableIterable<PartitionStatistics>`。接口设计遵循 Iceberg 其他 Scan API（如 `TableScan`）的流式构建模式，方法返回 `PartitionStatisticsScan` 以支持链式调用。

### `api/src/main/java/org/apache/iceberg/Table.java` (+12/-0 lines)

**修改目的**：在 Table 接口中新增 `newPartitionStatisticsScan()` 入口。

**工作逻辑**：
新增默认方法 `default PartitionStatisticsScan newPartitionStatisticsScan()`，默认抛出 `UnsupportedOperationException("Partition statistics scan is not supported")`。默认方法设计保证向后兼容，不支持该功能的 Table 实现无需修改。

### `core/src/main/java/org/apache/iceberg/BasePartitionStatistics.java` (+201/-0 lines, 新文件)

**修改目的**：提供 `PartitionStatistics` 的 core 层实现，支持投影读取。

**工作逻辑**：
继承 `SupportsIndexProjection`（提供基于位置的 get/set 投影能力），实现 `PartitionStatistics` 接口。定义 13 个私有字段对应 13 个统计值，`STATS_COUNT = 13`。构造函数接收 `Types.StructType projection`（用于内部 reader 实例化投影）。`internalGet(int pos)` 和 `internalSet(int pos, T value)` 通过 switch 按位置读写字段。`internalSet` 中若 value 为 null 直接返回（跳过设置），`totalRecordCount` 和 `lastUpdatedAt`/`lastUpdatedSnapshotId` 使用 `Long`（装箱类型）而非 `long`，以支持 null 语义。

### `core/src/main/java/org/apache/iceberg/BasePartitionStatisticsScan.java` (+86/-0 lines, 新文件)

**修改目的**：提供 `PartitionStatisticsScan` 的 core 层实现。

**工作逻辑**：
持有 `Table` 和可空的 `snapshotId`。`useSnapshot` 校验快照存在后设置 ID。`filter` 和 `project` 抛出 `UnsupportedOperationException`。`scan()` 的核心逻辑：
1. 若 `snapshotId` 为 null，取 `table.currentSnapshot()` 的 ID；若当前无快照则返回空迭代。
2. 从 `table.partitionStatisticsFiles()` 中按 `snapshotId` 过滤找到对应的 `PartitionStatisticsFile`；若不存在返回空迭代。
3. 根据 `Partitioning.partitionType(table)` 和 `TableUtil.formatVersion(table)` 通过 `PartitionStatsHandler.schema()` 构建 schema。
4. 从文件名推断 `FileFormat`，使用 `InternalData.read(fileFormat, inputFile).project(schema).setRootType(BasePartitionStatistics.class).build()` 读取并返回 `CloseableIterable<PartitionStatistics>`。

### `core/src/main/java/org/apache/iceberg/BaseTable.java` (+5/-0 lines)

**修改目的**：在 `BaseTable` 中实现 `newPartitionStatisticsScan()`。

**工作逻辑**：
覆写方法返回 `new BasePartitionStatisticsScan(this)`，将扫描绑定到当前表实例。

### `core/src/main/java/org/apache/iceberg/PartitionStats.java` (+6/-0 lines)

**修改目的**：将旧的 `PartitionStats` 类标记为废弃。

**工作逻辑**：
新增 `@Deprecated` 注解和 Javadoc 注释 `@deprecated will be removed in 1.12.0. Use {@link BasePartitionStatistics instead}`，引导用户迁移到新的 `BasePartitionStatistics`。

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+2/-0 lines)

**修改目的**：将旧的 `readPartitionStatsFile` 方法标记为废弃。

**工作逻辑**：
在 `readPartitionStatsFile` 方法上新增 `@Deprecated` 注解和 Javadoc `@deprecated will be removed in 1.12.0, use {@link PartitionStatisticsScan} instead`，引导用户改用 `table.newPartitionStatisticsScan().scan()`。

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsTestBase.java` (+114/-0 lines, 新文件)

**修改目的**：提取分区统计测试的公共基础设施。

**工作逻辑**：
定义抽象基类，包含：`@TempDir` 临时目录、`SCHEMA`（c1/c2/c3 三列）和 `SPE`（按 c2、c3 identity 分区）定义、各统计字段在 StructLike 中的位置常量、`invalidOldSchema()`（构造 field id 从 0 开始的旧 schema）、`randomStats()`（生成随机 PartitionStats）、`tempDir()`（创建子临时目录）、`partitionRecord()`（构造分区记录）等工具方法。这些方法此前散落在 `PartitionStatsHandlerTestBase` 中，提取到独立基类以便 Scan 测试复用。

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsScanTestBase.java` (+480/-0 lines, 新文件)

**修改目的**：为 Scan API 提供全面的测试覆盖。

**工作逻辑**：
继承 `PartitionStatisticsTestBase`，定义抽象方法 `format()` 供子类指定文件格式。包含 7 个测试：
- `testEmptyTable`：空表扫描返回空。
- `testInvalidSnapshotId`：不存在的快照 ID 抛出 `IllegalArgumentException`。
- `testNoStatsForSnapshot`：有快照但无统计文件时返回空。
- `testReadingStatsWithInvalidSchema`：用旧 schema 写入统计文件后通过 Scan 读取，Parquet 抛出 `IllegalArgumentException`（"Not a primitive type: struct"），Avro 抛出 `ClassCastException`。
- `testV2toV3SchemaEvolution`：v2 统计文件在升级到 v3 后仍可正确读取。
- `testScanPartitionStatsForCurrentSnapshot`：核心测试，构造多分区多快照场景（含数据文件、位置删除、等值删除、DV），验证扫描结果的 13 个字段全部正确，包括 `lastUpdatedAt`/`lastUpdatedSnapshotId` 随快照演进、`dvCount` 在 v3 格式下正确统计。
- `testScanPartitionStatsForOlderSnapshot`：验证可通过 `useSnapshot` 读取历史快照的统计。
- `computeAndValidatePartitionStats`：辅助方法，计算并提交统计文件后通过 Scan API 读取，断言 13 个字段。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (+6/-96 lines)

**修改目的**：重构为继承 `PartitionStatisticsTestBase`，复用公共基础设施，并标记旧测试为废弃。

**工作逻辑**：
- 改为 `extends PartitionStatisticsTestBase`，移除已上提的 SCHEMA、SPEC、temp、RANDOM、位置常量、`invalidOldSchema`、`randomStats`、`tempDir`、`partitionRecord` 等定义（约减少 96 行）。
- 对 `testPartitionStats`、`testReadingStatsWithInvalidSchema`、`testV2toV3SchemaEvolution` 三个使用旧 `PartitionStats` API 的测试方法新增 `@Deprecated` 注解和 Javadoc（将在 1.12.0 移除），与新 API 迁移策略一致。

### `core/src/test/java/org/apache/iceberg/avro/TestAvroPartitionStatisticsScan.java` (+29/-0 lines, 新文件)

**修改目的**：Avro 格式的 Scan API 测试。

**工作逻辑**：
继承 `PartitionStatisticsScanTestBase`，`format()` 返回 `FileFormat.AVRO`。

### `orc/src/test/java/org/apache/iceberg/orc/TestOrcPartitionStatisticsScan.java` (+59/-0 lines, 新文件)

**修改目的**：ORC 格式的 Scan API 测试。

**工作逻辑**：
继承 `PartitionStatisticsScanTestBase`，`format()` 返回 `FileFormat.ORC`。由于 `InternalData` 当前不支持 ORC 格式写入，覆写 `testScanPartitionStatsForCurrentSnapshot`、`testScanPartitionStatsForOlderSnapshot`、`testReadingStatsWithInvalidSchema`、`testV2toV3SchemaEvolution` 四个测试，断言调用父类方法时抛出 `UnsupportedOperationException("Cannot write using unregistered internal data format: ORC")`。这表明 Scan API 的读取路径目前对 ORC 格式的分区统计文件尚不完全支持（因为生成测试数据需要 InternalData 写入）。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetPartitionStatisticsScan.java` (+30/-0 lines, 新文件)

**修改目的**：Parquet 格式的 Scan API 测试。

**工作逻辑**：
继承 `PartitionStatisticsScanTestBase`，`format()` 返回 `FileFormat.PARQUET`。

## 总结

本提交为 Iceberg 引入了标准化的分区统计 Scan API（`PartitionStatistics`/`PartitionStatisticsScan` 接口及 core 层实现），使用户可通过 `table.newPartitionStatisticsScan().scan()` 高效读取分区统计文件，无需关心底层格式和 schema 细节。同时将旧的 `PartitionStats` 类和 `readPartitionStatsFile` 方法标记为废弃（1.12.0 移除），推动迁移。测试覆盖 Avro/Parquet/ORC 三种格式，包含空表、无效快照、schema 演进、多快照等场景，是一个功能完整、测试充分的新特性提交。
