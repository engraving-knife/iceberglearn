# 提交 3732：Spark: Add compaction only benchmark - rewrite data files (#16219)

## 提交信息

- **序号**：3732 / 4088
- **哈希**：4bdb2c25867435b6acc90bb8b0b226e28c20ac53
- **短哈希**：4bdb2c258
- **日期**：2026-05-18 10:03:40 -0500
- **作者**：Varun Lakhyani
- **提交说明**：Spark: Add compaction only benchmark - rewrite data files (#16219)
- **PR/Issue**：#16219

## 总体目的

本提交为 Iceberg 的 Spark 集成新增了一个专门针对"数据文件压缩（rewrite data files）"操作的 JMH 基准测试。Iceberg 的 `rewriteDataFiles` 动作用于将多个小数据文件合并为更大的文件以提升查询性能，是表维护的关键操作。然而此前缺乏专门衡量该操作端到端性能的基准测试，难以评估压缩操作在不同文件数量、不同数据规模下的性能表现，也难以发现压缩路径上的性能回归。

新增此基准测试可以帮助开发者在优化压缩逻辑、调整文件选择策略或升级 Spark 版本时，量化评估 `rewriteDataFiles` 的性能变化，为性能调优和回归检测提供可重复的测量手段。

## 如何达成设计目的

设计上拆分为两个类：
1. `IcebergCompactionBenchmark`：抽象基类，封装通用的 Spark 会话管理、表生命周期、Hadoop 配置、catalog 配置等基础设施，子类只需实现 `tableName()`、`initTable()`、`appendData()` 三个抽象方法即可定义具体的压缩场景。基类使用 JMH 注解配置 `@Fork(1)`、`@BenchmarkMode(Mode.SingleShotTime)`、`@Timeout(1 小时)`，适合测量单次执行的耗时型操作。
2. `IcebergDataCompactionBenchmark`：具体子类，针对 `rewriteDataFiles` 动作。定义 schema（int、string、nullable string 三列），通过 `@Param({"250","500","1000","2000"})` 参数化文件数量，使用 `SparkActions.get().rewriteDataFiles(table()).option(REWRITE_ALL, "true").execute()` 作为 benchmark 方法。

## 修改详情

### `spark/v4.1/spark/src/jmh/java/org/apache/iceberg/spark/action/IcebergCompactionBenchmark.java` (+150/-0 lines, 新增文件)

**修改目的**：提供压缩基准测试的抽象基类，封装通用的 Spark/JMH 基础设施。

**工作逻辑**：
- JMH 配置：`@Fork(1)` 单次 fork，`@State(Scope.Benchmark)`，`@BenchmarkMode(Mode.SingleShotTime)` 单次执行计时（适合耗时型操作），`@Timeout(time = 1, timeUnit = TimeUnit.HOURS)` 防止异常情况长时间挂起。
- 生命周期方法：
  - `setupBench()`（`@Setup`）：初始化 SparkSession。
  - `teardownBench()`（`@TearDown`）：停止 SparkSession。
  - `setupIteration()`（`@Setup(Level.Iteration)`）：每次迭代前初始化表并追加数据。
  - `cleanUpIteration()`（`@TearDown(Level.Iteration)`）：每次迭代后 DROP TABLE。
- 抽象方法：`tableName()`、`initTable()`、`appendData()`，由子类实现具体表结构与数据生成。
- `setupSpark()`：构建 SparkSession，配置 `spark_catalog` 为 `SparkSessionCatalog`，warehouse 指向临时目录，应用 `extraCatalogProperties()` 返回的额外 catalog 属性（默认 `type=hadoop`，可被子类覆盖以切换 S3 等后端）。
- `getCatalogWarehouse()`：在临时目录下创建唯一子目录作为 warehouse。
- `writeData(Dataset<Row>)`：以 Append 模式写入 iceberg 表。

### `spark/v4.1/spark/src/jmh/java/org/apache/iceberg/spark/action/IcebergDataCompactionBenchmark.java` (+114/-0 lines, 新增文件)

**修改目的**：针对 `rewriteDataFiles` 动作的具体基准测试实现。

**工作逻辑**：
- 类配置：`@Warmup(iterations = 3)`、`@Measurement(iterations = 10)`，预热 3 次、测量 10 次。
- 常量：表名 `compactbench`，命名空间 `default`，总行数 `TOTAL_ROWS = 2_000_000L`。
- 参数化：`@Param({"250", "500", "1000", "2000"})` 的 `numFiles`，控制初始数据被分成多少个文件，用于评估不同小文件数量下压缩性能。
- benchmark 方法 `rewriteDataFiles()`：
```java
@Benchmark
@Threads(1)
public void rewriteDataFiles() {
  SparkActions.get()
      .rewriteDataFiles(table())
      .option(SizeBasedFileRewritePlanner.REWRITE_ALL, "true")
      .execute();
}
```
  设置 `REWRITE_ALL=true` 强制重写所有文件，便于纯粹测量压缩性能而不受文件选择策略影响。
- `initTable()`：创建 schema（`intCol` INT、`stringCol` STRING、`nullCol` 可空 STRING），通过 `SparkSessionCatalog` 创建表。
- `appendData()`：使用 `spark.range(0, 2_000_000)` 生成 200 万行数据，添加 intCol、stringCol（`foo_` + id）、nullCol（null），按 `numFiles` 分区后写入，从而产生指定数量的小文件。
- 类注释中给出了运行命令示例：
```
./gradlew :iceberg-spark:iceberg-spark-4.1_2.13:jmh
    -PjmhIncludeRegex=IcebergDataCompactionBenchmark.rewriteDataFiles
    -PjmhOutputPath=benchmark/data-compaction-benchmark-results.txt
    -PjmhJsonOutputPath=benchmark/data-compaction-benchmark-results.json
```

## 总结

本提交新增了 Iceberg Spark 集成的数据文件压缩基准测试，包含一个抽象基类 `IcebergCompactionBenchmark`（封装 Spark/JMH 基础设施）和具体子类 `IcebergDataCompactionBenchmark`（针对 `rewriteDataFiles` 动作，参数化 250/500/1000/2000 个文件，200 万行数据）。该基准测试可量化评估压缩操作在不同小文件数量下的端到端性能，为压缩路径的性能优化和回归检测提供可重复的测量手段。基类设计支持后续扩展其他压缩场景（如 manifest 压缩、position delete 压缩等）。
