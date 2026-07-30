# 提交 3142：API, Core: Move partition stat schema creation to API (#15083)

## 提交信息

- **序号**：3142 / 4088
- **哈希**：1a4f423397882b8f7d44c4eb027faf9e5e5fb7bb
- **短哈希**：1a4f42339
- **日期**：2026-01-22
- **作者**：gaborkaszab
- **提交说明**：API, Core: Move partition stat schema creation to API (#15083)
- **PR/Issue**：#15083

## 总体目的

分区统计（partition statistics）文件的 schema 定义——即各字段常量（`spec_id`、`data_record_count`、`data_file_count`、`dv_count` 等）以及按格式版本（v2/v3）生成 `Schema` 的 `schema(StructType, int)` 方法——此前全部位于 `core` 模块的 `PartitionStatsHandler` 中。但分区统计文件格式属于 Iceberg 规范本身，其 schema 在 `api` 层的 `PartitionStatistics` 接口与扫描路径（`BasePartitionStatisticsScan`）中被消费。把 schema 的“权威定义”放在 `core` 会导致两个问题：①仅依赖 `api` 的模块无法访问 schema 定义，无法独立构建/解析分区统计；②`PartitionStatistics`（api）接口与其实际的存储 schema 之间存在跨模块的认知割裂。

本提交把分区统计 schema 的创建逻辑上移到 `api` 模块的 `PartitionStatistics` 接口：在该接口中新增全部字段常量与静态 `schema(StructType, int)` 方法（含 `v2Schema`/`v3Schema` 私有方法），并把 `core` 中 `PartitionStatsHandler` 的对应常量与方法标记为 `@Deprecated`（计划 1.12.0 移除）且 javadoc 指向新的 api 入口，内部调用点改为调用 `PartitionStatistics.schema(...)`。测试侧把对 `PartitionStatsHandler.*` 常量与方法的静态导入/调用统一切换到 `PartitionStatistics.*`。这样 api 层成为分区统计 schema 的唯一权威来源，core 仅保留实现逻辑，依赖关系更清晰。

## 如何达成设计目的

整体思路是“上移权威定义 + 旧入口标废弃 + 调用点切换”：在 `api/PartitionStatistics` 中复制并集中字段常量与 schema 构造逻辑（保持 v2/v3 行为一致，仅校验略放宽以不依赖 core 的 `TableMetadata`）；在 `core/PartitionStatsHandler` 中给旧常量与方法加 `@Deprecated` 注解并保留实现以兼容外部调用者；把 `BasePartitionStatisticsScan` 与 `PartitionStatsHandler` 自身内部调用切换到 api 入口；测试基类把静态导入与调用一并迁移。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionStatistics.java` (+93/-0 lines)

**修改目的**：在 api 层定义分区统计 schema 的字段常量与构造方法。

**工作逻辑**：
`PartitionStatistics` 接口新增一组 `Types.NestedField` 常量，按规范定义分区统计文件的全部列：`EMPTY_PARTITION_FIELD`(id=1, partition, 空 struct)、`SPEC_ID`(2)、`DATA_RECORD_COUNT`(3)、`DATA_FILE_COUNT`(4)、`TOTAL_DATA_FILE_SIZE_IN_BYTES`(5)、`POSITION_DELETE_RECORD_COUNT`(6, optional)、`POSITION_DELETE_FILE_COUNT`(7, optional)、`EQUALITY_DELETE_RECORD_COUNT`(8, optional)、`EQUALITY_DELETE_FILE_COUNT`(9, optional)、`TOTAL_RECORD_COUNT`(10, optional)、`LAST_UPDATED_AT`(11, optional)、`LAST_UPDATED_SNAPSHOT_ID`(12, optional)、`DV_COUNT`(13, required, 带初始/写入默认值 `Literal.of(0)`，注释说明“为支持 v3 reader 读 v2 写的文件”而设默认值)。

新增静态方法 `schema(Types.StructType unifiedPartitionType, int formatVersion)`：校验 `!unifiedPartitionType.fields().isEmpty()`（表必须分区）与 `formatVersion > 0`，按 `formatVersion <= 2` 走 `v2Schema` 否则走 `v3Schema`。`v2Schema` 用 optional 形式的 delete 计数列与 12 个字段；`v3Schema` 把 position/equality delete 的 record/file 计数改为 required 并追加 `DV_COUNT`，共 13 个字段。partition 列复用 `EMPTY_PARTITION_FIELD.fieldId()/name()` 但 type 替换为传入的 `unifiedPartitionType`。与旧 `PartitionStatsHandler.schema` 相比，校验不再依赖 `TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION`（core 类），因此 api 层无需反向依赖 core。

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+71/-11 lines)

**修改目的**：把旧的 schema 常量与方法标记废弃，内部改用 api 入口。

**工作逻辑**：
- `PARTITION_FIELD_ID`、`PARTITION_FIELD_NAME`、`SPEC_ID`、`DATA_RECORD_COUNT`、`DATA_FILE_COUNT`、`TOTAL_DATA_FILE_SIZE_IN_BYTES`、`POSITION_DELETE_RECORD_COUNT`、`POSITION_DELETE_FILE_COUNT`、`EQUALITY_DELETE_RECORD_COUNT`、`EQUALITY_DELETE_FILE_COUNT`、`TOTAL_RECORD_COUNT`、`LAST_UPDATED_AT`、`LAST_UPDATED_SNAPSHOT_ID`、`DV_COUNT` 共 14 个常量，以及 `schema(StructType, int)` 方法，全部加上 `@Deprecated` 注解与 javadoc `@deprecated will be removed in 1.12.0. Use {@link PartitionStatistics#...}`，指向 api 层对应的新常量/方法。方法体保留（仍调用本地 `v2Schema`/`v3Schema`），保证外部旧调用者仍可编译运行。
- 内部 `computeAndWriteStatsFile` 中原先调用 `schema(partitionType, TableUtil.formatVersion(table))` 改为 `PartitionStatistics.schema(partitionType, TableUtil.formatVersion(table))`，使核心写统计文件路径直接使用 api 权威定义，避免逻辑漂移。

### `core/src/main/java/org/apache/iceberg/BasePartitionStatisticsScan.java` (+1/-1 lines)

**修改目的**：扫描路径改用 api 层 schema 构造。

**工作逻辑**：
`buildScan`/读取分区统计文件时，把 `PartitionStatsHandler.schema(partitionType, TableUtil.formatVersion(table))` 改为 `PartitionStatistics.schema(partitionType, TableUtil.formatVersion(table))`，使扫描端与写入端共用同一 schema 来源。

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsScanTestBase.java` (+4/-6 lines)

**修改目的**：测试切换到 api 层常量与方法。

**工作逻辑**：
静态导入由 `org.apache.iceberg.PartitionStatsHandler.PARTITION_FIELD_ID` 改为 `org.apache.iceberg.PartitionStatistics.EMPTY_PARTITION_FIELD`；`PartitionStatsHandler.schema(...)` 两处调用改为 `PartitionStatistics.schema(...)`；通过 `EMPTY_PARTITION_FIELD.fieldId()` 取代旧的 `PARTITION_FIELD_ID` 来定位 partition 字段类型。

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsTestBase.java` (+13/-13 lines)

**修改目的**：测试静态导入统一切换。

**工作逻辑**：
把 12 个 `import static org.apache.iceberg.PartitionStatsHandler.*` 改为 `import static org.apache.iceberg.PartitionStatistics.*`（含新增的 `EMPTY_PARTITION_FIELD`），删除对 `PARTITION_FIELD_NAME` 的导入并改用 `EMPTY_PARTITION_FIELD`，其余常量名一致仅包路径变化。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (+11/-12 lines)

**修改目的**：handler 测试基类切换到 api 层 schema 入口。

**工作逻辑**：
静态导入 `EMPTY_PARTITION_FIELD` 替代 `PARTITION_FIELD_ID`；`PartitionStatsHandler.schema(partitionSchema, formatVersion)` 两处改为 `PartitionStatistics.schema(partitionSchema, formatVersion)`；`dataSchema.findField(PARTITION_FIELD_ID)` 改为 `dataSchema.findField(EMPTY_PARTITION_FIELD.fieldId())`，确保测试与新的权威定义一致。

## 总结

该提交把分区统计文件 schema 的字段常量与构造逻辑从 `core` 上移到 `api` 模块的 `PartitionStatistics` 接口，使 api 层成为该规范 schema 的唯一权威来源，消除了 api 模块对 core 的反向依赖隐患；旧入口在 `PartitionStatsHandler` 中标记为 1.12.0 移除的废弃 API 以保持兼容，core 内部与所有测试统一切换到新入口，整体提升了模块边界的清晰度与可维护性。
