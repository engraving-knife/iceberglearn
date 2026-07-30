# 提交 1082：API, Spark 3.5: Action to compute table stats

## 提交信息

- **序号**：1082 / 4088
- **哈希**：2f6e7e6371902bcb72f21deeaea8889d4768004e
- **短哈希**：2f6e7e637
- **日期**：2024-08-21 20:23:03 -0700
- **作者**：Karuppayya
- **提交说明**：API, Spark 3.5: Action to compute table stats (#10288)
- **PR/Issue**：#10288

## 总体目的

Iceberg 表的查询性能高度依赖统计信息（如每列的 NDV，即 distinct value 数量）。Iceberg 已支持把统计信息以 blob 形式写入 Puffin 文件并挂载到表 metadata 上（`StatisticsFile`），但此前缺少一个开箱即用的"对整表计算列级 NDV 并落盘成 Puffin 统计文件"的 action 实现。本提交填补这一空缺：在 API 层定义 `ComputeTableStats` action 接口，并在 Spark 3.5 模块给出具体实现 `ComputeTableStatsSparkAction`，使用 Apache DataSketches 的 Theta Sketch 算法在 Spark 集群上并行估算每列 NDV，最终写入 Puffin 文件并提交到表元数据。

引入此 action 后，用户只需调用 `SparkActions.computeTableStats(table)` 即可针对当前快照（或指定快照）按列收集 NDV 统计，下游引擎（Spark、Trino 等）可利用这些统计做基于成本的查询优化。这是 Iceberg 走向"自动化表维护与统计采集"的一步，也是后续自动维护工作流的基础组件之一。

## 如何达成设计目的

整体设计分三层：

1. **API 层**：在 `org.apache.iceberg.actions` 包中新增 `ComputeTableStats` 接口，提供 `columns(String...)` 和 `snapshot(long)` 两个 fluent 配置方法，并定义 `Result.statisticsFile()`。同时扩展 `ActionsProvider`，新增 `computeTableStats(Table)` 默认方法（抛 `UnsupportedOperationException`），保持向后兼容。
2. **Core 层**：新增 `BaseComputeTableStats` immutables 接口，定义 `Result` 的可空 `statisticsFile` 字段；并在 `GenericBlobMetadata` 上增加 `from(Collection)` 批量转换辅助方法，便于把 Puffin 写出的 blob metadata 一次性转成 Iceberg 的 `BlobMetadata`。
3. **Spark 3.5 层**：实现 `ComputeTableStatsSparkAction`，依赖新增的 `NDVSketchUtil`（生成 NDV blob）和 Scala 自定义聚合函数 `ThetaSketchAgg`（基于 DataSketches Theta Sketch 做分布式聚合）。同时在 `build.gradle` 中引入 `datasketches-java` 依赖并对 runtime jar 做 shade relocate。

执行流程：读取指定快照 → 校验列（仅支持 primitive 类型）→ 用 Spark 聚合对每列生成 Theta Sketch → 序列化为 `Blob` 列表 → 用 `PuffinWriter` 写到表 metadata 目录 → 通过 `table.updateStatistics().setStatistics(...)` 提交统计文件。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/ActionsProvider.java`

**修改目的**：在 ActionsProvider 接口中暴露 `computeTableStats(Table)` 入口，使所有 actions provider 都能被请求执行统计计算。

**工作逻辑**：新增 `default` 方法，默认抛 `UnsupportedOperationException`，保持对已有实现的二进制兼容；只有实现了该能力的 provider（如 SparkActions）才覆写。

### `api/src/main/java/org/apache/iceberg/actions/ComputeTableStats.java`（新增）

**修改目的**：定义计算表统计的 action 接口。

**工作逻辑**：继承 `Action<ComputeTableStats, ComputeTableStats.Result>`，提供 `columns(String...)`（指定分析列，默认全部列）与 `snapshot(long)`（指定分析快照，默认当前快照）两个配置方法。内部 `Result` 接口仅暴露 `statisticsFile()`，返回可能为 null 的 `StatisticsFile`。

### `core/src/main/java/org/apache/iceberg/GenericBlobMetadata.java`

**修改目的**：新增批量构造方法，便于把 Puffin 写出的 `List<BlobMetadata>` 一次性转成 Iceberg 的 `BlobMetadata` 列表。

**工作逻辑**：新增 `from(Collection<org.apache.iceberg.puffin.BlobMetadata>)`，对每个元素调用已有的单元素 `from(puffinMetadata)`，再用 `ImmutableList.toImmutableList()` 收集。

### `core/src/main/java/org/apache/iceberg/actions/BaseComputeTableStats.java`（新增）

**修改目的**：使用 immutables 生成不可变 `Result` 实现，定义可空 `statisticsFile`。

**工作逻辑**：标注 `@Value.Enclosing` 与自定义 `@Value.Style`（生成类名 `ImmutableComputeTableStats`，public 可见性），内部定义 `Result` 接口，`statisticsFile()` 用 `@Nullable` 修饰以表达"无统计可收集"的空结果场景。

### `spark/v3.5/build.gradle`

**修改目的**：引入 `datasketches-java` 依赖，并在 runtime 模块中对其做 shade relocate。

**工作逻辑**：在 `iceberg-spark` 子项目添加 `implementation("org.apache.datasketches:datasketches-java:${libs.versions.datasketches.get()}")`；在 `iceberg-spark-runtime` 的 shadow jar 配置中加入 `relocate 'org.apache.datasketches', 'org.apache.iceberg.shaded.org.apache.datasketches'`，避免与用户 classpath 上的 DataSketches 版本冲突。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/ComputeTableStatsSparkAction.java`（新增）

**修改目的**：Spark 3.5 上的具体 action 实现。

**工作逻辑**：
- 构造时记录 `table`，并把 `snapshot` 默认设为 `table.currentSnapshot()`。
- `execute()`：若 `snapshot` 为 null 返回 `EMPTY_RESULT`；否则校验列后用 `JobGroupInfo` 包装执行 `doExecute()`。
- `doExecute()`：调用 `generateNDVBlobs()` 生成 blob 列表 → `writeStatsFile(blobs)` 写入 Puffin 文件 → `table.updateStatistics().setStatistics(snapshotId(), statisticsFile).commit()` 提交。
- `writeStatsFile`：在表 metadata 目录下生成 `<snapshotId>-<UUID>.stats` 文件，用 `Puffin.write(...)` 写入所有 blob，`writer.finish()` 后用 `GenericStatisticsFile` 与 `GenericBlobMetadata.from(...)` 封装返回。
- `columns()`：若用户未指定列，则取快照 schema 下所有 primitive 类型列名。
- `validateColumns()`：检查列存在且为 primitive 类型。
- `outputPath()`：通过 `((HasTableOperations) table).operations().metadataFileLocation(...)` 生成文件路径。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/NDVSketchUtil.java`（新增）

**修改目的**：封装"用 Spark 对每列计算 Theta Sketch 并组装为 Puffin Blob"的逻辑。

**工作逻辑**：
- `generateBlobs(spark, table, snapshot, columns)`：调用 `computeNDVSketches` 一次性拿到所有列的 sketch 字节数组（一个 Row，每列一个 byte[]），再 `CompactSketch.wrap` 还原为 `Sketch`，按列构造 `Blob`。
- `toBlob`：生成类型为 `StandardBlobTypes.APACHE_DATASKETCHES_THETA_V1` 的 blob，输入字段为该列 fieldId，压缩用 `PuffinCompressionCodec.ZSTD`，并附带 `ndv` 属性（`sketch.getEstimate()` 转长整型字符串），方便后续直接读取估算的 NDV。
- `computeNDVSketches`：用 `spark.read.format("iceberg").option(SNAPSHOT_ID, ...).load(table.name())` 读快照数据，对每列 `select` 一个 `ThetaSketchAgg(colName)` 聚合表达式，`.first()` 取单行结果。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java`

**修改目的**：在 SparkActions 中注册新 action 工厂方法。

**工作逻辑**：覆写 `computeTableStats(Table table)`，返回 `new ComputeTableStatsSparkAction(spark, table)`。

### `spark/v3.5/spark/src/main/scala/org/apache/spark/sql/stats/ThetaSketchAgg.scala`（新增）

**修改目的**：实现 Spark 自定义命令式聚合函数（`TypedImperativeAggregate`），分布式计算每列的 Theta Sketch。

**工作逻辑**：
- 使用 `Family.ALPHA` 的 `UpdateSketch` 作为聚合缓冲区，默认 seed。
- `update`：取 Spark 内部值，先通过 `toIcebergValue` 把 `UTF8String`/`Decimal`/`Array[Byte]` 转成 Iceberg 兼容形式，再用 `Conversions.toByteBuffer(icebergType, value)` 按 Iceberg 单值序列化为字节喂给 sketch，保证跨引擎一致性。
- `merge`：用 `SetOperationBuilder().buildUnion` 合并两个 sketch（Spark 分区合并）。
- `eval`/`serialize`：把 sketch `compact()` 后输出为 byte 数组。
- `deserialize`：用 `CompactSketch.wrap` 还原。
- 返回类型为 `BinaryType`，供 `NDVSketchUtil` 消费。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputeTableStatsAction.java`（新增）

**修改目的**：覆盖新 action 的功能测试，共 417 行。

**工作逻辑**：测试覆盖正常计算 NDV、指定列子集、指定历史快照、空表/无快照场景、列类型校验（非 primitive 报错）、统计文件正确写入并可通过 `table.statistics()` 读取等。

## 小结

- **成效**：在 Spark 3.5 上首次提供开箱即用的 `ComputeTableStats` action，基于 DataSketches Theta Sketch 估算每列 NDV 并写入 Puffin 统计文件，补齐 Iceberg 自动统计采集能力的一块拼图。
- **影响范围**：api 模块新增接口、core 新增 immutables 基类与 `GenericBlobMetadata.from` 批量方法、spark v3.5 模块新增 action 实现与 Scala 聚合函数及依赖配置；不影响已有行为（新接口默认抛 `UnsupportedOperationException`）。
- **回迁到 1.4.x 的注意事项**：本提交属于新功能（feature），引入新 API 与新依赖（datasketches-java）以及新的 Spark 聚合函数。1.4.x 作为维护分支通常只接收 bug fix，不建议回迁此类新功能；若一定要回迁，需同步引入 datasketches 依赖与 shade 配置、确认 1.4.x 已具备 `Puffin`/`StatisticsFile` 相关 API，并移植测试。此外 Theta Sketch 的序列化格式与 Iceberg `StandardBlobTypes.APACHE_DATASKETCHES_THETA_V1` 需与 1.4.x 的 spec 常量保持一致，否则会导致统计文件读取异常。
