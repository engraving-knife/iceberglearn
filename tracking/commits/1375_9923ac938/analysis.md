# 提交 1375：Spark 3.4: Support Spark Column Stats (#11532)

## 提交信息

- **序号**：1375 / 4088
- **哈希**：9923ac9384900f9644efa4118ef953095f15d0fd
- **短哈希**：9923ac938
- **日期**：2024-11-14（Thu Nov 14 14:57:19 2024 +0530）
- **作者**：Sai Tharun <56219964+saitharun15@users.noreply.github.com>
- **提交说明**：Spark 3.4: Support Spark Column Stats (#11532)
- **PR/Issue**：#11532

## 总体目的

Spark 的 DataSource V2 API 提供了 `SupportsReportStatistics` 接口，允许数据源向 Spark 的 Catalyst 优化器上报列级统计（distinct count、min/max、null count、avg/max len、histogram 等），用于基于成本的优化（CBO）。Iceberg 已在表元数据中支持 Statistics File（Puffin 文件）存储 Apache DataSketches Theta Sketch 类型的 blob，其中通过 `ndv` 属性记录每列的去重值数（NDV）。

此前 Spark 3.5 模块已经实现了将该 NDV 统计上报给 Spark 的能力（`SparkColumnStatistics` 类与 `SparkScan.estimateStatistics` 中的列统计逻辑），但 Spark 3.4 模块尚未支持——`Stats` 类只上报 `sizeInBytes` 和 `numRows`，不上报 `columnStats`。这意味着在 Spark 3.4 下使用 Iceberg 时，即便表上采集了 statistics 文件，CBO 也拿不到列级 NDV，可能生成次优计划。

本提交将 Spark 3.5 已有的列统计上报能力**对齐（cherry-pick/移植）到 Spark 3.4 模块**，使两个 Spark 版本行为一致。同时顺手修复了 Spark 3.5 测试中一处错误的断言写法（`assertThat(columnStats.isEmpty())` 实际不断言任何东西，改为 `assertThat(columnStats).isEmpty()`）。

## 如何达成设计目的

整体思路是让 Spark 3.4 的扫描链路上报列统计，关键链路为：读配置开关 → 扫描时读取 statistics 文件 → 解析 DataSketches blob 的 NDV → 包装为 Spark `ColumnStatistics` → 通过 `Stats.columnStats()` 返回。

具体步骤：

1. **新增配置开关**：在 `SparkSQLProperties` 增加 `REPORT_COLUMN_STATS = "spark.sql.iceberg.report-column-stats"`，默认值 `true`（默认开启上报）；在 `SparkReadConf` 增加 `reportColumnStats()` 方法解析该开关。该开关允许用户在出现问题时关闭列统计上报。
2. **新增列统计载体类**：新建 `SparkColumnStatistics` 实现 Spark 的 `org.apache.spark.sql.connector.read.colstats.ColumnStatistics` 接口，持有 distinctCount、min、max、nullCount、avgLen、maxLen、histogram 七个字段（当前只填充 distinctCount/NDV，其余为空 Optional）。
3. **扩展 Stats 类**：`Stats` 新增 `Map<NamedReference, ColumnStatistics> colstats` 字段与构造参数，并实现 `columnStats()` 方法返回该 map。所有 `new Stats(...)` 调用点（`SparkScan`、`SparkChangelogScan`）同步加传 `colStatsMap`（changelog 场景传空 map，因为 changelog 不需要列统计）。
4. **在 SparkScan.estimateStatistics 中读取并解析 statistics 文件**：仅当 `readConf.reportColumnStats()` 为 true 且 Spark 的 `SQLConf.CBO_ENABLED` 为 true 时才计算列统计（双开关，尊重 Spark 全局 CBO 设置）。逻辑为：取 `table.statisticsFiles()` 的第一个文件，按 `blobMetadata.fields().get(0)`（列 field id）分组，对每个列找出 `type` 为 `APACHE_DATASKETCHES_THETA_V1` 的 blob，从其 `properties` 中读 `ndv` 字符串并解析为 Long；用列名构造 `FieldReference.column(colName)` 作为 key，构造 `SparkColumnStatistics(ndv, null, ...)` 作为 value 放入 map。非 DataSketches 类型的 blob 仅 debug 日志记录。
5. **新增测试**：在 `TestSparkScan` 中新增 5 个测试方法，覆盖无统计文件、有非 DataSketches 统计、单列 NDV、单列 NDV + 其它类型 blob、双列 NDV 等场景；并验证开关关闭或 CBO 关闭时不报列统计。辅助方法 `checkColStatisticsNotReported` 与 `checkColStatisticsReported` 封装断言。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java`

修改目的：新增列统计上报开关配置键。

工作逻辑：新增两个常量——`REPORT_COLUMN_STATS = "spark.sql.iceberg.report-column-stats"` 与 `REPORT_COLUMN_STATS_DEFAULT = true`，并附注释说明其控制是否向 Spark 上报表列统计以供查询优化。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java`

修改目的：提供解析开关的便捷方法。

工作逻辑：新增 `reportColumnStats()` 方法，通过 `confParser.booleanConf().sessionConf(SparkSQLProperties.REPORT_COLUMN_STATS).defaultValue(...).parse()` 读取会话级配置并返回布尔值。使用 `sessionConf` 表示该配置可由 Spark session 级别动态调整。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkColumnStatistics.java`（新增）

修改目的：实现 Spark 列统计接口，作为 Iceberg 统计到 Spark 统计的适配载体。

工作逻辑：实现 `org.apache.spark.sql.connector.read.colstats.ColumnStatistics`。构造函数接收 7 个参数（distinctCount、min、max、nullCount、avgLen、maxLen、histogram），将 `Long` 类型按 null 转为 `OptionalLong.empty()` 或 `OptionalLong.of(...)`，将对象类型用 `Optional.ofNullable` 包装。各 getter 返回对应 Optional。当前只有 distinctCount 会被填充实际值，其余传 null（即空 Optional），表示 Iceberg 暂不上报这些统计。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/Stats.java`

修改目的：让 Stats 携带列统计 map。

工作逻辑：新增 `Map<NamedReference, ColumnStatistics> colstats` 字段与构造参数；实现 `columnStats()` 返回该 map。原 `Stats(sizeInBytes, numRows)` 二参构造被替换为三参构造 `Stats(sizeInBytes, numRows, colstats)`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScan.java`

修改目的：在估算统计时读取 Iceberg statistics 文件并填充列统计。

工作逻辑：
- 新增 import：`BlobMetadata`、`StatisticsFile`、`Strings`、`Maps`、`FieldReference`、`NamedReference`、`ColumnStatistics`、`SQLConf`。
- 新增成员 `private final SparkSession spark;` 与常量 `NDV_KEY = "ndv"`，在构造函数中赋值 `this.spark = spark`（用于读取 Spark session 配置判断 CBO 是否开启）。
- 改造 `estimateStatistics(Snapshot)`：
  - 空表场景 `new Stats(0L, 0L)` 改为 `new Stats(0L, 0L, Collections.emptyMap())`。
  - 新增列统计计算块：先读 `cboEnabled = spark.conf().get(SQLConf.CBO_ENABLED().key(), "false")`；当 `readConf.reportColumnStats() && cboEnabled` 时，取 `table.statisticsFiles()` 第一个文件的 `blobMetadata()`，按 `fields().get(0)`（列 field id）分组；对每列遍历其 blob，匹配 `StandardBlobTypes.APACHE_DATASKETCHES_THETA_V1` 类型，从 `properties().get("ndv")` 解析 NDV（缺失则 debug 日志）；用 `table.schema().findColumnName(fieldId)` 得到列名，构造 `FieldReference.column(colName)` 与 `SparkColumnStatistics(ndv, null, null, null, null, null, null)` 放入 map。非 DataSketches 类型 blob 仅 debug 日志。
  - 分区表与非分区表的 `new Stats(...)` 调用均补传 `colStatsMap`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkChangelogScan.java`

修改目的：适配 Stats 新签名。

工作逻辑：`estimateStatistics()` 中 `new Stats(sizeInBytes, rowsCount)` 改为 `new Stats(sizeInBytes, rowsCount, Collections.emptyMap())`。Changelog 场景不上报表列统计，故传空 map。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`

修改目的：覆盖列统计上报的各种场景。

工作逻辑：新增 5 个测试方法与 2 个辅助方法：
- `testTableWithoutColStats`：表无 statistics 文件，验证 CBO 开关开关下均不报列统计。
- `testTableWithoutApacheDatasketchColStat`：statistics 文件中只有非 DataSketches 类型 blob（自定义 `DUMMY_BLOB_TYPE = "sum-data-size-bytes-v1"`），验证 NDV 为空。
- `testTableWithOneColStats`：单列 id 的 DataSketches blob，ndv=4，验证开启开关时上报正确 NDV。
- `testTableWithOneApacheDatasketchColStatAndOneDifferentColStat`：同一列既有 DataSketches blob 又有其它类型 blob，验证只取 DataSketches 的 NDV。
- `testTableWithTwoColStats`：id 与 data 两列各一个 DataSketches blob（ndv 分别为 4 与 2），验证双列 NDV 均正确上报。
- 辅助 `checkColStatisticsNotReported`：断言 `columnStats` 为空。
- 辅助 `checkColStatisticsReported`：按预期 NDV map 断言每列 distinctCount，空 map 时断言所有列 distinctCount 为空。
测试通过 `GenericStatisticsFile`/`GenericBlobMetadata` 构造内存中的统计文件并 `table.updateStatistics().setStatistics(...)` 提交，用 `withSQLConf` 切换 CBO 与 report-column-stats 开关。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkScan.java`

修改目的：修复一处无效断言。

工作逻辑：将 `assertThat(columnStats.isEmpty());`（此写法不断言任何东西，只是调用 isEmpty() 后丢弃结果）改为 `assertThat(columnStats).isEmpty();`（正确断言 map 为空）。v3.5 的生产代码已具备列统计能力，此处仅修测试。

## 小结

- 成效：Spark 3.4 模块现可在 Spark CBO 开启且 `spark.sql.iceberg.report-column-stats`（默认 true）开启时，将 Iceberg statistics 文件中 DataSketches Theta Sketch blob 的 NDV 上报给 Spark 优化器，与 Spark 3.5 行为对齐；同时修复了 v3.5 测试中一处无效断言。
- 影响范围：仅 Spark 3.4 模块新增功能 + v3.5 测试修复。新增 1 个类（88 行）、修改 4 个生产类、新增 305 行测试。默认开启但不强制（受 Spark CBO 总开关与本模块开关双重控制），对不开 CBO 的用户无行为变化。
- 局限：当前只上报 NDV（distinctCount），min/max/nullCount/avgLen/maxLen/histogram 均不上报（传 null）；只读取第一个 statistics 文件；只识别 `APACHE_DATASKETCHES_THETA_V1` 一种 blob 类型。这些是后续可扩展点。
- 回迁到 1.4.x 的注意事项：**视 1.4.x 的 Spark 版本支持情况决定**：
  1. 本提交针对 Spark 3.4 模块。若 1.4.x 维护分支仍包含 Spark 3.4 模块且尚未支持列统计上报，则可回迁以获得 CBO 收益。需确认 1.4.x 的 Spark 3.4 模块结构与本提交一致（`SparkReadConf`、`SparkScan`、`Stats` 等类签名）。
  2. 依赖 Iceberg statistics 文件与 DataSketches blob 功能，这些属于 Iceberg 核心能力，1.4.x 应已具备（不依赖 v3/DV）。
  3. 风险低：纯增量功能，默认开启但受双开关控制，不影响不开 CBO 的用户。回迁后建议跑 `TestSparkScan` 的列统计用例验证。
  4. 注意 v3.5 测试断言修复（`assertThat(columnStats.isEmpty())` → `assertThat(columnStats).isEmpty()`）也应一并回迁到 1.4.x 的 v3.5 测试（若存在同样写法），否则该断言无效。
