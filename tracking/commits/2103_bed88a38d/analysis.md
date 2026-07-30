# 提交 2103：Core: Refactor and use InternalData for partition stats (#12946)

## 提交信息

- **序号**：2103 / 4088
- **哈希**：bed88a38d5624d4062f0679e918780d1a1461834
- **短哈希**：bed88a38d
- **日期**：2025-05-08 12:03:14 +0200
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Core: Refactor and use InternalData for partition stats (#12946)
- **PR/Issue**：#12946

## 总体目的

Iceberg 的分区统计（partition stats）功能此前由 `data` 模块的 `org.apache.iceberg.data.PartitionStatsHandler` 与 `core` 模块的 `PartitionStatsUtil` 提供，二者都依赖 `data` 模块的 `GenericRecord`/`InternalData` 读写器来读写分区统计文件。这带来两个问题：一是 `core` 模块对 `data` 模块有反向依赖倾向（通过 `PartitionStatsUtil` 间接），二是分区统计文件的读写绑死在 `data` 模块的 Generic 实现上，不利于其它引擎（如 Spark/Flink）使用各自的内部数据模型。

本次提交在 `core` 模块新增 `org.apache.iceberg.PartitionStatsHandler`，使用 `core` 自身的 `InternalData`（`StructLike` 读写器）来读写分区统计文件，使分区统计能力成为 `core` 的自包含功能，不再依赖 `data` 模块。同时把旧的 `data.PartitionStatsHandler` 与 `core.PartitionStatsUtil` 标记为 `@Deprecated`（1.11.0 移除），并把测试基类从 `data` 模块迁移到 `core`，新增各文件格式（Avro/ORC/Parquet）的子类测试。

## 如何达成设计目的

1. **新增 `core.PartitionStatsHandler`**：静态工具类，定义分区统计文件 schema（`partition` + `spec_id` + 各类 record/file count + `last_updated_at`/`last_updated_snapshot_id`），提供：
   - `schema(StructType unifiedPartitionType)`：根据统一分区类型生成 stats 文件 schema。
   - `computeAndWriteStatsFile(Table)` / `(Table, long snapshotId)`：计算并写出 `PartitionStatisticsFile`。
   - `writePartitionStatsFile(...)`：用 `InternalData.write(fileFormat, outputFile).schema(dataSchema).build()` 得到 `FileAppender<StructLike>` 写入。
   - `readPartitionStatsFile(Schema, InputFile)`：用 `InternalData.read(fileFormat, inputFile).project(schema).build()` 读出 `StructLike`，再转换为 `PartitionStats`。
   - 内部 `computeStats`/`collectStats`/`mergeStats`/`sortStatsByPartition` 等方法：遍历 manifests，按分区聚合 live/deleted entry 统计，按分区类型排序后写出。
2. **弃用旧实现**：`core.PartitionStatsUtil` 与 `data.PartitionStatsHandler` 加 `@Deprecated`（since 1.10.0, removed in 1.11.0），javadoc 指向新的 `core.PartitionStatsHandler`。
3. **测试迁移**：
   - 新增 `core.PartitionStatsHandlerTestBase`（抽象基类，525 行，从原 `data` 测试基类迁移并改用新 `PartitionStatsHandler` 与 `InternalData`）。
   - 在 `data`（Avro）、`orc`、`parquet` 模块新增 `TestAvroPartitionStatsHandler` / `TestOrcPartitionStatsHandler` / `TestParquetPartitionStatsHandler`，继承 `core.PartitionStatsHandlerTestBase`，只实现 `format()` 抽象方法。
   - 原 `data.PartitionStatsHandlerTestBase` 大幅瘦身（从原 356 行减到约 205 行），保留对旧 `data.PartitionStatsHandler` 的测试。
   - `core.TestPartitionStatsUtil` 加 4 行适配。
   - `PartitionStatsHandlerBenchmark` 改用新 `core.PartitionStatsHandler`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (新增, +313/-0 lines)

**修改目的**：在 core 模块提供自包含的分区统计读写能力，使用 `InternalData`。

**工作逻辑**：
- 定义分区统计文件 schema 常量（`PARTITION_FIELD_ID=0`，`SPEC_ID=1`，`DATA_RECORD_COUNT=2`，...，`LAST_UPDATED_SNAPSHOT_ID=11`），`schema(StructType)` 拼装出完整 schema。
- `computeAndWriteStatsFile(Table, snapshotId)`：校验表已分区 → `computeStats` 用 `Tasks.foreach(manifests).executeWith(ThreadPools.getWorkerPool())` 并行收集每个 manifest 的 `PartitionMap<PartitionStats>` → `mergeStats` 合并 → `sortStatsByPartition` 按分区类型排序 → `writePartitionStatsFile`。
- `collectStats`：打开 manifest，遍历 `ManifestEntry`，用 `PartitionUtil.coercePartition` 把文件分区值统一到 `partitionType`，用 `PartitionData.copyFor` 生成 key，`PartitionStats.liveEntry(file, snapshot)` / `deletedEntry(snapshot)` 累加。
- `writePartitionStatsFile`：按表属性 `DEFAULT_FILE_FORMAT` 选择格式，`InternalData.write(fileFormat, outputFile).schema(dataSchema).build()` 得到 `FileAppender<StructLike>`，写入 `PartitionStats`（其本身是 `StructLike`）。
- `readPartitionStatsFile`：`InternalData.read(fileFormat, inputFile).project(schema).build()` 读出 `CloseableIterable<StructLike>`，`recordToPartitionStats` 把 `StructLike` 转回 `PartitionStats`。
- `newPartitionStatsFile`：用 `HasTableOperations` 取得 metadata 目录，生成 `partition-stats-<snapshotId>-<uuid>.<ext>` 文件名。

### `core/src/main/java/org/apache/iceberg/PartitionStatsUtil.java` (修改, +5/-0 lines)

**修改目的**：弃用旧工具类。

**工作逻辑**：加 `@Deprecated` + javadoc，指向 `core.PartitionStatsHandler`。

### `data/src/main/java/org/apache/iceberg/data/PartitionStatsHandler.java` (修改, +4/-0 lines)

**修改目的**：弃用旧的 data 模块 handler。

**工作逻辑**：加 `@Deprecated` + javadoc，指向 core 模块的 `PartitionStatsHandler`。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` (新增, +525 行，从 data 迁移并重写)

**修改目的**：为新的 core `PartitionStatsHandler` 提供格式无关的测试基类。

**工作逻辑**：抽象方法 `format()` 返回 `FileFormat`；测试覆盖空表、单分区、多 spec、delete 文件、last_updated 字段、排序、读写往返、schema 生成等场景，全部使用 `PartitionStatsHandler.computeAndWriteStatsFile` / `readPartitionStatsFile` 与 `InternalData`，断言用 AssertJ。使用 `@TempDir`、JUnit 5。

### `data/src/test/java/org/apache/iceberg/data/TestAvroPartitionStatsHandler.java` (新增, +29 lines)

**修改目的**：Avro 格式的 core handler 测试子类。

**工作逻辑**：`extends PartitionStatsHandlerTestBase`，`format()` 返回 `FileFormat.AVRO`。

### `core/src/test/java/org/apache/iceberg/data/TestOrcPartitionStatsHandler.java` (新增, +49 lines)

**修改目的**：ORC 格式测试子类（位于 core 模块的 data 测试包）。

**工作逻辑**：`extends PartitionStatsHandlerTestBase`，`format()` 返回 `ORC`，并额外覆盖 ORC 特定的 stats 文件读取（ORC 之前不支持写但需支持读）。

### `parquet/src/test/java/org/apache/iceberg/data/TestParquetPartitionStatsHandler.java` (新增, +26 lines)

**修改目的**：Parquet 格式测试子类。

**工作逻辑**：`extends PartitionStatsHandlerTestBase`，`format()` 返回 `PARQUET`。

### `core/src/test/java/org/apache/iceberg/TestPartitionStatsUtil.java` (修改, +4/-0 lines)

**修改目的**：适配旧 util 弃用注解（抑制告警或调整）。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerTestBase.java` 对应的旧 `data.PartitionStatsHandlerTestBase` (修改, 大幅瘦身)

**修改目的**：保留对旧 `data.PartitionStatsHandler` 的测试，移除已迁移到 core 的部分。

**工作逻辑**：从原 356 行减到约 205 行，删除与新 core handler 重复的用例，保留旧 handler 专属测试。

### `core/src/test/java/org/apache/iceberg/PartitionStatsHandlerBenchmark.java` (修改, +14/-9 lines)

**修改目的**：基准改用新的 core `PartitionStatsHandler`。

## 总结

本次提交把分区统计文件读写能力从 `data` 模块下沉到 `core` 模块：新增 `core.PartitionStatsHandler`，使用 `InternalData`（`StructLike` 读写器）而非 `GenericRecord`，使 core 自包含地支持 Avro/ORC/Parquet 三种格式的分区统计文件读写；旧 `data.PartitionStatsHandler` 与 `core.PartitionStatsUtil` 标记弃用（1.11.0 移除）；测试基类迁移到 core 并按格式拆分子类。这是降低 core 对 data 模块依赖、为多引擎复用分区统计能力的重要重构。
