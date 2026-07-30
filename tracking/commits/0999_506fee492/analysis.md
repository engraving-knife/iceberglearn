# 提交 0999：Spark 3.5: Support Reporting Column Stats (#10659)

## 提交信息

- **序号**：0999 / 4088
- **哈希**：506fee492620bc8e13d7da4f104462fb97ceef82
- **短哈希**：506fee492
- **日期**：2024-07-31（Wed Jul 31 08:41:20 2024 -0700）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.5: Support Reporting Column Stats (#10659)
- **PR/Issue**：#10659

## 总体目的

Spark 在 3.5 引入了数据源 V2 的列级统计（column statistics）报告能力：`SupportsReportStatistics` 接口扩展了 `columnStats()` 方法，允许数据源向 Spark 的 Catalyst 优化器（CBO，基于代价的优化器）上报每列的统计信息（如 distinct count、min、max、null count 等），用于更精确的查询计划选择（如 join 重排、聚合策略）。

Iceberg 已通过 Puffin 文件存储列级统计（NDV，即 distinct count），blob 类型为 `APACHE_DATASKETCHES_THETA_V1`，blob 元数据的 `properties` 中带有 `ndv` 键记录去重计数。但此前的 Spark 3.5 集成并未把这些统计上报给 Spark，导致 Iceberg 表即使有统计文件，Spark CBO 也无法利用，查询计划仍只能依赖大小估算。

本提交让 Iceberg 的 Spark 3.5 source 在 `estimateStatistics()` 中读取表的 `statisticsFiles()`，解析其中的 blob 元数据，把每列的 NDV 包装成 Spark 的 `ColumnStatistics` 上报。这样当 Spark CBO 开启（`spark.sql.cbo.enabled=true`）时，就能用上 Iceberg 的列级 NDV，改善 join 等场景的优化质量。同时新增配置项 `spark.sql.iceberg.report-column-stats` 允许用户关闭该行为。

## 如何达成设计目的

整体设计分三层：

1. **配置开关**：在 `SparkSQLProperties` 新增 `REPORT_COLUMN_STATS` 常量（默认 true），在 `SparkReadConf` 新增 `reportColumnStats()` 读取方法，控制是否上报列统计。
2. **统计载体**：新增 `SparkColumnStatistics` 实现 Spark 的 `ColumnStatistics` 接口，把 Iceberg 侧的 `Long`/`Object` 包装成 `OptionalLong`/`Optional`。目前仅填充 NDV，其余（min/max/nullCount/avgLen/maxLen/histogram）留空。
3. **统计上报**：修改 `SparkScan.estimateStatistics(Snapshot)`，在 CBO 开启且 `reportColumnStats()` 为 true 时，遍历 `table.statisticsFiles()` 第一个文件的 `blobMetadata`，对类型为 `APACHE_DATASKETCHES_THETA_V1` 的 blob 解析其 `ndv` 属性，按字段 id 反查列名构造 `FieldReference`，组装 `Map<NamedReference, ColumnStatistics>` 传入 `Stats`。`Stats` 实现 `columnStats()` 返回该 map。

此外 `SparkChangelogScan.estimateStatistics()` 与 `SparkScan` 中所有 `new Stats(...)` 调用都需适配新的三参数构造函数（新增 `colStatsMap` 参数，无统计处传 `Collections.emptyMap()`）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java`

**修改目的**：新增列统计上报的配置常量。

**工作逻辑**：新增 `REPORT_COLUMN_STATS = "spark.sql.iceberg.report-column-stats"` 与默认值 `REPORT_COLUMN_STATS_DEFAULT = true`，并附注释说明该配置控制是否向 Spark 上报列统计以用于查询优化。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`

**修改目的**：提供读取 `report-column-stats` 配置的方法。

**工作逻辑**：新增 `public boolean reportColumnStats()`，通过 `confParser.booleanConf().sessionConf(SparkSQLProperties.REPORT_COLUMN_STATS).defaultValue(SparkSQLProperties.REPORT_COLUMN_STATS_DEFAULT).parse()` 读取，支持会话级配置。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkColumnStatistics.java`（新增）

**修改目的**：实现 Spark 的 `ColumnStatistics` 接口，承载列级统计。

**工作逻辑**：持有 `OptionalLong distinctCount`、`Optional<Object> min/max`、`OptionalLong nullCount/avgLen/maxLen`、`Optional<Histogram> histogram` 七个字段。构造函数接收可空的 `Long`/`Object`/`Histogram`，空值转为 `OptionalLong.empty()`/`Optional.empty()`。各 getter 返回对应 Optional。当前 Iceberg 只提供 NDV，故调用方仅传入 `distinctCount`，其余为 null。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/Stats.java`

**修改目的**：让 `Stats` 携带并上报列级统计。

**工作逻辑**：新增字段 `Map<NamedReference, ColumnStatistics> colstats`，构造函数由 `Stats(long sizeInBytes, long numRows)` 改为 `Stats(long sizeInBytes, long numRows, Map<NamedReference, ColumnStatistics> colstats)`。新增 `@Override public Map<NamedReference, ColumnStatistics> columnStats()` 返回该 map。新增对应 import。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java`

**修改目的**：在估算统计时读取 Iceberg 统计文件并向 Spark 上报列 NDV。

**工作逻辑**：
- 新增字段 `private final SparkSession spark;`（构造函数中赋值），用于读取 CBO 配置；
- 新增常量 `NDV_KEY = "ndv"`；
- `estimateStatistics(Snapshot)` 中：snapshot 为 null 时返回 `new Stats(0L, 0L, Collections.emptyMap())`；当 `readConf.reportColumnStats() && cboEnabled`（`spark.sql.cbo.enabled`）时，构建 `colStatsMap`：取 `table.statisticsFiles()` 第一个文件的 `blobMetadata()`，对每个 blob：
  - 取 `fields().get(0)` 作为字段 id，`table.schema().findColumnName(id)` 反查列名，构造 `FieldReference.column(colName)`；
  - 若 blob `type` 等于 `APACHE_DATASKETCHES_THETA_V1`，读取 `properties().get(NDV_KEY)` 解析为 `Long ndv`（缺失或空则记 debug 日志，ndv 保持 null）；
  - 否则记 debug 日志 "DataSketch blob is not available"；
  - 用 `new SparkColumnStatistics(ndv, null, null, null, null, null, null)` 包装，放入 map；
- 分区表与非分区表两条返回路径都把 `colStatsMap` 传给 `new Stats(...)`。
- 新增若干 import（`BlobMetadata`、`StatisticsFile`、`Strings`、`Maps`、`FieldReference`、`NamedReference`、`ColumnStatistics`、`SQLConf`）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkChangelogScan.java`

**修改目的**：适配 `Stats` 新构造函数签名。

**工作逻辑**：`estimateStatistics()` 中 `new Stats(sizeInBytes, rowsCount)` 改为 `new Stats(sizeInBytes, rowsCount, Collections.emptyMap())`（changelog scan 不上报列统计）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`

**修改目的**：新增列统计上报的测试。

**工作逻辑**：新增 `testTableWithoutColStats` 与 `testTableWithOneColStats` 等测试：
- `testTableWithoutColStats`：建表写入 4 条记录，分别验证默认（CBO 开但无统计文件）、`REPORT_COLUMN_STATS=false`、`REPORT_COLUMN_STATS=true` 但无统计文件时，`columnStats()` 报告的 NDV 为空 map 或不报告；
- `testTableWithOneColStats`：构造带 `APACHE_DATASKETCHES_THETA_V1` blob 与 `ndv` 属性的 `GenericStatisticsFile`/`GenericBlobMetadata`，验证 `SparkScan` 能正确解析出对应列的 NDV 上报；
- 引入 `GenericBlobMetadata`、`GenericStatisticsFile`、`ImmutableList`、`Maps`、`ColumnStatistics`、`SQLConf`、`Encoders` 等 import，并新增辅助断言方法 `checkColStatisticsNotReported` / `checkColStatisticsReported` 校验 `stats.columnStats()` 内容。

## 小结

- **成效**：Iceberg Spark 3.5 source 现可向 Spark CBO 上报列级 NDV 统计，使带统计文件的 Iceberg 表在 CBO 开启时获得更优查询计划；并提供 `spark.sql.iceberg.report-column-stats` 开关供用户控制。
- **影响范围**：仅 Spark 3.5 模块，7 个文件（1 个新增主类、1 个新增测试方法块、5 个修改），346 insertions / 5 deletions。改动向后兼容（旧 `Stats` 构造函数被替换，但 `Stats` 是包私有内部类）。
- **回迁到 1.4.x 的注意事项**：该功能依赖 Spark 3.5 的 `ColumnStatistics` 接口（`spark.sql.connector.read.colstats`），1.4.x 分支若维护 Spark 3.5 模块且 Spark 版本一致则可回迁。需注意：当前实现只读取 `statisticsFiles()` 第一个文件、且只支持 DataSketches theta blob 的 NDV，回迁后行为同样受限；`reportColumnStats` 默认 true，开启后会增加少量读取统计文件的开销，但对查询计划有利。回迁时需一并迁移测试所用的 `GenericStatisticsFile`/`GenericBlobMetadata` 等工具类（这些是 Iceberg 公共 API，1.4.x 应已存在）。风险中等偏低，主要在统计文件解析的正确性上，建议回迁后跑 `TestSparkScan` 验证。
