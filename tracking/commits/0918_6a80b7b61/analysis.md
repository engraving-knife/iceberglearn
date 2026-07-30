# 提交 0918：Flink, Spark: Rename constants to be all uppercase (#10675)

## 提交信息

- **序号**：0918 / 4088
- **哈希**：6a80b7b61b8fc111f10c7c3f9ae9327e3ef6b353
- **短哈希**：6a80b7b61
- **日期**：2024-07-10（Wed Jul 10 16:44:14 2024 +0200）
- **作者**：Attila Kreiner <kreiner.attila@gmail.com>
- **提交说明**：Flink, Spark: Rename constants to be all uppercase (#10675)
- **PR/Issue**：#10675

## 总体目的

本提交是 Iceberg 代码风格统一工作的一部分：把 `static final` 常量字段的命名从驼峰式（camelCase，如 `catalogExtension`、`tableName`、`idCounter`、`tableMap`）改为 Java 规范的"全大写下划线"（UPPER_SNAKE_CASE，如 `CATALOG_EXTENSION`、`TABLE_NAME`、`ID_COUNTER`、`TABLE_MAP`）。Java 编码规范（Oracle、Google）要求 `static final` 常量使用 UPPER_SNAKE_CASE，但 Iceberg 的 Flink 与 Spark 集成模块（特别是测试代码）历史上遗留了大量 camelCase 的 `static final` 字段，与规范不一致。

本提交专门针对 `flink/v1.17`、`flink/v1.18`、`flink/v1.19`、`spark/v3.3`、`spark/v3.4`、`spark/v3.5` 这 6 个模块下的相关文件做统一重命名。它是更大范围清理 #10673（见序号 0919）的子集，#10673 覆盖整个仓库并加入 checkstyle 强制规则；本提交先针对 Flink 与 Spark 模块完成实际的重命名改动。除测试代码外，还包含 3 个生产代码文件（`ArrowVectorAccessors.java` 的 spark v3.3/3.4/3.5 版本）的字段重命名。

## 如何达成设计目的

机械式重命名：对每个文件中所有 `private static final` / `public static final` 的常量字段，把名称从 camelCase 改为 UPPER_SNAKE_CASE，并同步更新所有引用处。改动不涉及任何逻辑变化，纯粹是字段名替换。典型重命名包括：
- `catalogExtension` / `catalogResource` → `CATALOG_EXTENSION`（不同文件原本用不同名字，统一为 `CATALOG_EXTENSION`）
- `tableName` → `TABLE_NAME`
- `idCounter` → `ID_COUNTER`
- `tableMap` → `TABLE_MAP`
- `configToOverride` → `CONFIG_TO_OVERRIDE`、`hadoopPrefixedConfigToOverride` → `HADOOP_PREFIXED_CONFIG_TO_OVERRIDE`、`configOverrideValue` → `CONFIG_OVERRIDE_VALUE`
- `emptyQueryResult` → `EMPTY_QUERY_RESULT`、`struct` → `STRUCT`
- `factory` → `FACTORY`（生产代码 ArrowVectorAccessors）
- `queues` → `QUEUES`、`numSinks` → `NUM_SINKS`、`availabilities` → `AVAILABILITIES`
- `sourceName` → `SOURCE_NAME`

由于是纯重命名，编译器和测试运行时行为完全不变。

## 修改详情

### Flink 模块（`flink/v1.17`、`flink/v1.18`、`flink/v1.19`）

以下文件在每个 Flink 版本模块下都有对应改动（3 个版本内容基本一致）：

#### `flink/.../sink/TestBucketPartitionerFlinkIcebergSink.java`
**修改目的**：把 `catalogExtension` 重命名为 `CATALOG_EXTENSION`。6 行改动。同步更新 `setupEnvironment` 中对 `catalogExtension.catalog().createTable(...)` 与 `catalogExtension.tableLoader()` 的调用。

#### `flink/.../sink/TestFlinkIcebergSink.java`
**修改目的**：把 `catalogResource` 重命名为 `CATALOG_EXTENSION`（与其他文件统一命名）。14 行改动。涉及 `before()`、`writeTwoBranchTableAndCommit()` 等方法中多处 `catalogResource.catalog().createTable(...)`、`catalogResource.catalogLoader()` 调用。

#### `flink/.../sink/TestFlinkIcebergSinkBranch.java`
**修改目的**：同上，`catalogResource` → `CATALOG_EXTENSION`，6 行改动。

#### `flink/.../sink/TestFlinkIcebergSinkV2.java`
**修改目的**：同上，`catalogResource` → `CATALOG_EXTENSION`，6 行改动。

#### `flink/.../sink/TestFlinkIcebergSinkV2Branch.java`
**修改目的**：同上，`catalogResource` → `CATALOG_EXTENSION`，6 行改动。

#### `flink/.../source/TestFlinkInputFormat.java`
**修改目的**：常量重命名，8 行改动。

#### `flink/.../source/TestFlinkScan.java`
**修改目的**：常量重命名，30 行改动（改动量较大，因为涉及多个常量）。

#### `flink/.../source/TestFlinkScanSql.java`、`TestFlinkSource.java`、`TestFlinkSourceSql.java`、`TestIcebergSourceBounded.java`、`TestIcebergSourceBoundedSql.java`、`TestIcebergSourceSql.java`、`TestIcebergSourceWithWatermarkExtractor.java`、`TestSqlBase.java`
**修改目的**：各自常量重命名，2-10 行改动。

#### `flink/.../source/enumerator/TestContinuousSplitPlannerImpl.java`
**修改目的**：常量重命名，8 行改动。

#### `flink/.../source/reader/TestColumnStatsWatermarkExtractor.java`
**修改目的**：常量重命名，2 行改动。

#### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/CollectingSink.java`
**修改目的**：把 `queues` → `QUEUES`、`numSinks` → `NUM_SINKS`，16 行改动。

**工作逻辑**：

```diff
-  private static final List<BlockingQueue<Object>> queues =
+  private static final List<BlockingQueue<Object>> QUEUES =
       Collections.synchronizedList(Lists.newArrayListWithExpectedSize(1));
-  private static final AtomicInteger numSinks = new AtomicInteger(-1);
+  private static final AtomicInteger NUM_SINKS = new AtomicInteger(-1);
```

`CollectingSink` 是测试用的 Sink，通过静态 `QUEUES` 列表收集每个 sink 实例的输出，`NUM_SINKS` 用于为每个实例分配索引。所有引用处（构造函数、`remainingOutput`、`isEmpty`、`poll`、内部 `WriteRecord` 等）同步更新。

#### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/ManualSource.java`
**修改目的**：把 `queues` → `QUEUES`、`availabilities` → `AVAILABILITIES`，20 行改动。

**工作逻辑**：与 `CollectingSink` 类似，`ManualSource` 是测试用的 Source，通过静态 `QUEUES` 与 `AVAILABILITIES` 列表管理每个 source 实例的数据队列与可用性 future。所有引用处同步更新。

### Spark 模块（`spark/v3.3`、`spark/v3.4`、`spark/v3.5`）

#### `spark/.../spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java`
**修改目的**：把 `emptyQueryResult` → `EMPTY_QUERY_RESULT`、`struct` → `STRUCT`，18 行改动。

**工作逻辑**：

```diff
-  private static final List<Object[]> emptyQueryResult = Lists.newArrayList();
+  private static final List<Object[]> EMPTY_QUERY_RESULT = Lists.newArrayList();
-  private static final StructField[] struct = {
+  private static final StructField[] STRUCT = {
     ...
   };
```

`EMPTY_QUERY_RESULT` 是测试中校验"空查询结果"的常量；`STRUCT` 是测试用的 Spark `StructType` 字段定义，被 `singlePartitionedDF`、`singleNullRecordDF` 等辅助方法引用。

#### `spark/.../spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSnapshotTableProcedure.java`
**修改目的**：把 `sourceName` → `SOURCE_NAME`，spark v3.3/v3.4 各 40 行、v3.5 56 行改动（v3.5 改动更多可能因为该版本有更多测试用例）。

**工作逻辑**：`SOURCE_NAME = "spark_catalog.default.source"` 是 snapshot procedure 测试中源表名常量，被 `removeTables()`、`testSnapshot()`、`testSnapshotWithLocation()`、`testSnapshotWithProperties()`、`testSnapshotReplace()` 等多个测试方法引用，用于 `CREATE TABLE`、`INSERT INTO`、`DROP TABLE`、`SELECT` 等 SQL 语句。

#### `spark/.../src/main/java/org/apache/iceberg/spark/data/vectorized/ArrowVectorAccessors.java`（生产代码！）
**修改目的**：把 `factory` → `FACTORY`，4 行改动。这是本提交中唯一涉及生产代码的文件，spark v3.3/v3.4/v3.5 各有一份。

**工作逻辑**：

```diff
-  private static final ArrowVectorAccessorFactory factory = new ArrowVectorAccessorFactory();
+  private static final ArrowVectorAccessorFactory FACTORY = new ArrowVectorAccessorFactory();

   static ArrowVectorAccessor<Decimal, UTF8String, ColumnarArray, ArrowColumnVector>
       getVectorAccessor(VectorHolder holder) {
-    return factory.getVectorAccessor(holder);
+    return FACTORY.getVectorAccessor(holder);
   }
```

`ArrowVectorAccessors` 是 Spark 向量化读取时获取 Arrow 向量访问器的工具类，`FACTORY` 是单例工厂。该字段是 `private`，重命名不影响外部 API。

#### `spark/.../src/test/java/org/apache/iceberg/spark/data/vectorized/TestParquetVectorizedReads.java`
**修改目的**：常量重命名，24 行改动。

#### `spark/.../src/test/java/org/apache/iceberg/spark/source/LogMessage.java`
**修改目的**：把 `idCounter` → `ID_COUNTER`，18 行改动。

**工作逻辑**：`LogMessage` 是测试用的 POJO，`ID_COUNTER` 是静态 `AtomicInteger`，用于 `debug`/`info`/`warn`/`error` 等静态工厂方法为每条 `LogMessage` 分配唯一 id。所有 8 个工厂方法中的 `idCounter.getAndIncrement()` 调用同步更新为 `ID_COUNTER.getAndIncrement()`。

#### `spark/.../src/test/java/org/apache/iceberg/spark/source/ManualSource.java`
**修改目的**：把 `tableMap` → `TABLE_MAP`，12 行改动。

**工作逻辑**：`ManualSource` 是测试用的 Spark `TableProvider`，通过静态 `TABLE_MAP` 注册表名到 `Table` 实例的映射，供 `setTable`、`clearTables`、`getTable` 等方法使用。

#### `spark/.../src/test/java/org/apache/iceberg/spark/source/TestSparkCatalog.java`
**修改目的**：把 `tableMap` → `TABLE_MAP`，12 行改动。

#### `spark/.../src/test/java/org/apache/iceberg/spark/source/TestSparkCatalogCacheExpiration.java`
**修改目的**：常量重命名，8 行改动（v3.3/v3.4）/ 4 行改动（v3.5，可能因为部分常量此前已重命名）。

#### `spark/.../src/test/java/org/apache/iceberg/spark/source/TestSparkCatalogHadoopOverrides.java`
**修改目的**：把 `configToOverride` → `CONFIG_TO_OVERRIDE`、`hadoopPrefixedConfigToOverride` → `HADOOP_PREFIXED_CONFIG_TO_OVERRIDE`、`configOverrideValue` → `CONFIG_OVERRIDE_VALUE`，32 行改动。

**工作逻辑**：这 3 个常量用于测试 Spark Catalog 的 Hadoop 配置覆盖功能，在 `@Parameters` 方法中作为参数传入，并在多个 `@TestTemplate` 方法中通过 `conf.get(CONFIG_TO_OVERRIDE, ...)` 等方式校验配置是否被正确覆盖。

#### `spark/v3.3`、`v3.4`、`v3.5` 各自的 `TestCompressionSettings.java`
**修改目的**：把 `tableName` → `TABLE_NAME`，18-19 行改动。

#### `spark/.../src/test/java/org/apache/iceberg/spark/sql/TestTimestampWithoutZone.java`
**修改目的**：常量重命名，30-34 行改动。

#### spark/v3.5 专属：
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkCatalogOperations.java`：13 行改动。
- `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestSnapshotTableAction.java`：12 行改动。

## 小结

- **成效**：把 Flink（v1.17/v1.18/v1.19）与 Spark（v3.3/v3.4/v3.5）模块下 85 个文件中的 `static final` 常量字段从 camelCase 重命名为 UPPER_SNAKE_CASE，符合 Java 编码规范。共 510 行新增 / 510 行删除（纯重命名，行数对称）。
- **影响范围**：以测试代码为主，但包含 3 个生产代码文件（`ArrowVectorAccessors.java` 在 spark v3.3/v3.4/v3.5 下的 `src/main/`）。涉及 Flink 的 sink/source/enumerator/reader/maintenance 与 Spark 的 extensions/source/sql/actions 等子模块。无逻辑变化，纯字段名替换。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯重命名提交，不涉及逻辑变化，回迁风险主要在于与 1.4.x 分支其他改动的冲突。如果 1.4.x 这些文件与 main 差异较大，cherry-pick 时可能产生冲突需要手动解决。
  - 由于改动量大（85 文件），且属于风格统一而非功能修复，1.4.x 作为维护分支建议不主动回迁，避免引入大量无谓的冲突与代码评审负担。
  - 若 1.4.x 决定跟进 checkstyle 强制规则（#10673，见序号 0919），则需要把本提交与 #10673 一起回迁，确保重命名与 checkstyle 规则同步落地。
  - 注意 `ArrowVectorAccessors.java` 是生产代码，回迁时需确认该字段为 `private` 不影响二进制兼容性（仅源码级重命名，无 API 变化）。
