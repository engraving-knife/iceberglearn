# 提交 1116：Core: Add benchmark for appending files (#11029)

## 提交信息

- **序号**：1116 / 4088
- **哈希**：6c7964002b097b933053d8f6db63c0851a55a39a
- **短哈希**：6c7964002
- **日期**：2024-08-28（Wed Aug 28 16:51:38 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Add benchmark for appending files (#11029)
- **PR/Issue**：#11029

## 总体目的

Iceberg 表的"追加文件"操作（`AppendFiles` / `newAppend` 与 `newFastAppend`）是写入路径的核心：每次 commit 都要构建 manifest、写入 manifest-list、提交表元数据。在不同文件数量、不同 append 模式下，commit 路径的开销差异显著，但目前缺少针对 core 模块 append 操作的微基准，难以量化优化效果。

本提交新增 `core/src/jmh/java/org/apache/iceberg/AppendBenchmark.java`，用 JMH 测量向未分区表追加大量数据文件的耗时。该基准支持两个维度：

1. **文件数量**：`numFiles` 参数取 `500000`、`1000000`、`2500000`，模拟从 50 万到 250 万文件的极端规模。
2. **append 模式**：`fast` 布尔参数控制使用 `table.newFastAppend()` 还是 `table.newAppend()`，便于对比快路径（直接追加新 manifest）与普通路径（可能合并 manifest）的性能差异。

该基准与提交 1117（Spark 3.5 的 `PlanningBenchmark` 复用 `FileGenerationUtil`）配套——`FileGenerationUtil` 即为本基准生成虚拟数据文件 `DataFile` 的工具，避免依赖真实文件 IO，让基准专注测量 commit 路径开销。

## 如何达成设计目的

通过新增一个 JMH 基准类实现，置于 `core` 模块的 `src/jmh/java` 源集（已被 `baseline.gradle` 的 Spotless 与 JMH 插件覆盖）。基准设计要点：

- **`@BenchmarkMode(Mode.SingleShotTime)`**：单次执行测量（而非吞吐），适合 commit 这种本身耗时较长的操作，避免 JMH 反复调用造成干扰。
- **`@Fork(1)`、`@Warmup(iterations = 3)`、`@Measurement(iterations = 5)`**：单 fork、3 次预热、5 次测量迭代，平衡稳定性与运行时长。
- **`@Timeout(time = 10, timeUnit = TimeUnit.MINUTES)`**：单次 benchmark 最长 10 分钟，防止超大规模参数导致卡死。
- **`@Threads(1)`**：单线程，符合 append commit 串行特性。
- **`@Param`**：让 JMH 自动遍历参数组合（3 个 numFiles × 2 个 fast = 6 组）。
- **`@Setup` / `@TearDown`**：每个 benchmark 参数组合前初始化表与数据文件、后删除表，保证隔离。
- **数据文件生成**：调用 `FileGenerationUtil.generateDataFile(table, null)`（partition 传 null 因为表未分区），生成虚拟 DataFile 元数据而不真正写文件，让基准专注元数据 commit 路径。

## 修改详情

### `core/src/jmh/java/org/apache/iceberg/AppendBenchmark.java`（新文件，123 行）

**修改目的**：提供 core 模块 append 操作的 JMH 基准。

**工作逻辑**：

1. **Schema 与表定义**：定义一个含 7 列的 Schema（int、long、decimal(10,10)、date、timestamp、timestamp_tz、string），分区规格为 `PartitionSpec.unpartitioned()`（未分区，专注 commit 开销），使用 `HadoopTables` 在本地临时路径创建表。

2. **状态字段**：

```java
private Table table;
private List<DataFile> dataFiles;

@Param({"500000", "1000000", "2500000"})
private int numFiles;

@Param({"true", "false"})
private boolean fast;
```

3. **`setupBenchmark()`**（标注 `@Setup`）：
   - 调用 `initTable()` 通过 `TABLES.create(SCHEMA, SPEC, TABLE_IDENT)` 创建表；
   - 调用 `initDataFiles()` 用 `FileGenerationUtil.generateDataFile(table, null)` 循环生成 `numFiles` 个虚拟 DataFile，存入 `dataFiles` 列表。

4. **`tearDownBenchmark()`**（标注 `@TearDown`）：调用 `dropTable()` 删除表，避免污染下次迭代。

5. **`appendFiles(Blackhole blackhole)`**（标注 `@Benchmark`、`@Threads(1)`）：

```java
AppendFiles append = fast ? table.newFastAppend() : table.newAppend();
for (DataFile dataFile : dataFiles) {
  append.appendFile(dataFile);
}
append.commit();
```

根据 `fast` 选择 `newFastAppend()` 或 `newAppend()`，循环追加所有 DataFile，最后调用 `commit()` 触发 manifest 写入与表元数据提交。`Blackhole` 参数用于避免 JVM 死代码消除（虽然这里实际未使用其消费方法，但保留参数符合 JMH 惯例）。

6. **类注释中的运行命令**：

```
./gradlew :iceberg-core:jmh
    -PjmhIncludeRegex=AppendBenchmark
    -PjmhOutputPath=benchmark/append-benchmark.txt
```

输出会落到 `core/benchmark/append-benchmark.txt`，该路径已被提交 1115 的 `*/benchmark/*` 忽略规则覆盖，不会误提交。

## 小结

- **成效**：core 模块新增 append 操作的 JMH 基准，可在 50 万至 250 万文件规模下测量 `newFastAppend` 与 `newAppend` 的 commit 耗时，为后续优化（如 manifest 合并、元数据提交路径）提供量化依据；与 `FileGenerationUtil` 配套，避免真实文件 IO 干扰。
- **影响范围**：1 个新文件、123 行新增，仅位于 `src/jmh/java`，不影响主代码与测试，对发布产物无影响。
- **回迁到 1.4.x 的注意事项**：这是开发期性能基准，不影响运行时行为，**通常无需回迁到 1.4.x**。若 1.4.x 也希望评估 append 性能（例如在 1.4.x 上验证某次 commit 优化的效果），可顺手回迁该文件，但需确认两点：一是 1.4.x 的 `core` 模块是否已有 `FileGenerationUtil` 与对应 `generateDataFile(Table, StructLike)` 签名（该方法被本基准调用，若签名不同需调整）；二是 1.4.x 的 `baseline.gradle` 是否已包含 `src/jmh/java` 在 Spotless target 中（提交 1112 仅扩展了 testFixtures，jmh 早已在 target 内）。回迁风险低，但属可选操作。
