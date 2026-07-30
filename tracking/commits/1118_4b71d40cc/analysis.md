# 提交 1118：Flink: add unit tests for range distribution on bucket partition column (#11033)

## 提交信息

- **序号**：1118 / 4088
- **哈希**：4b71d40cc16af328acaa2bcdbe4ffc704846d66e
- **短哈希**：4b71d40cc
- **日期**：2024-08-29（Thu Aug 29 09:27:23 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: add unit tests for range distribution on bucket partition column (#11033)
- **PR/Issue**：#11033

## 总体目的

Iceberg Flink Sink 支持三种写入分布模式（`DistributionMode`）：`NONE`、`HASH`、`RANGE`。对于按 `bucket` 列分区的表（如 `bucket(uuid, 60)`），传统做法是 `HASH` 分布（按 `keyBy` 把同一 bucket 路由到同一 writer），但存在三个工程问题（详见测试类 Javadoc 引用的 PR #4228）：

1. **低基数 keyBy 不均衡**：当 bucket 数较少（如 60）而 writer 并行度较大时，`keyBy` 可能导致部分 writer 空闲、部分过载，流量分布不均。
2. **bucket 数不能整除 writer 并行度**：例如 60 个 bucket、40 个 writer，hash 取模后某些 writer 分到 2 个 bucket、某些分到 1 个，分布不均。
3. **bucket 数小于 writer 并行度**：例如 60 个 bucket、120 个 writer，一半 writer 永远空闲。

`RANGE` 分布通过全局统计 + 范围分区器（`RangePartitioner`）解决上述问题：基于数据采样构造范围边界，把流量均匀切分到所有 writer，无论 bucket 数与 writer 并行度关系如何。但此前缺少针对"bucket 分区列 + range 分布"组合的端到端测试，难以验证 range 分布在不均衡场景下确实生效。

本提交为 Flink 1.19 与 1.20 两个版本模块各新增一个端到端测试类 `TestFlinkIcebergSinkRangeDistributionBucketing`，覆盖 5 种 bucket 数 vs writer 并行度的组合（相等、小于不可整除、小于可整除、大于不可整除、大于可整除），验证 range 分布能限制每次 checkpoint 提交的数据文件数量。

## 如何达成设计目的

通过在 `flink/v1.19` 与 `flink/v1.20` 模块各新增一份完全相同的测试类（253 行）实现。测试设计要点：

1. **表结构**：Schema 含 `ts`(timestamp)、`uuid`(uuid)、`data`(string) 三列；分区规格为 `hour("ts").bucket("uuid", NUM_BUCKETS=4)`，即按小时 + 4 个 bucket 分区。
2. **分布配置**：表属性 `WRITE_DISTRIBUTION_MODE = RANGE`，并设置 sort order `asc(bucket("uuid", 4))`，让 writer 仅按 bucket 列排序，避免每个 writer 同时写多个 bucket 文件。
3. **MiniCluster**：4 个 TaskManager × 4 slots = 最大并行度 16，支持测试并行度 2-8。
4. **数据生成**：用 Flink `DataGeneratorSource` 按 `RateLimiterStrategy.perCheckpoint(200)` 每个 checkpoint 生成 200 行，共 4 个 checkpoint（800 行）。`RowGenerator` 用固定时间戳（让所有行进同一小时分区）+ 随机 UUID（分布到 4 个 bucket）。
5. **5 个测试方法**：分别用 `testParallelism(4)`、`(6)`、`(8)`、`(3)`、`(2)` 覆盖 5 种 bucket-vs-parallelism 关系。
6. **断言**：
   - 至少 4 个 checkpoint 提交（数据生成不精确，可能更多）；
   - 取最后 2 个 checkpoint（range 分布需 2 个 checkpoint 周期完成统计收集与应用），断言其 `addedDataFiles` 数量 ≤ `NUM_BUCKETS + parallelism`（4 + parallelism）。

**为何上界是 `NUM_BUCKETS + parallelism`**：range 分布基于采样统计，小样本下流量并非完美均衡；范围边界可能跨越 writer 边界，导致单次 commit 的文件数略多于 parallelism 或 bucket 数，但不应超过两者之和。若用 hash 分布，每 commit 文件数可达 4 × parallelism（因每 writer 可能写 4 个 bucket），range 分布应明显优于该值。

两份测试类（v1.19 与 v1.20）内容完全一致，仅为不同 Flink 版本提供独立测试。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkRangeDistributionBucketing.java`（新文件，253 行）

**修改目的**：为 Flink 1.19 模块新增 range 分布 + bucket 分区列的端到端测试。

**工作逻辑**：

1. **集群与 catalog 装置**：

```java
@RegisterExtension
public static final MiniClusterExtension MINI_CLUSTER_EXTENSION =
    new MiniClusterExtension(
        new MiniClusterResourceConfiguration.Builder()
            .setNumberTaskManagers(4)
            .setNumberSlotsPerTaskManager(4)
            .setConfiguration(DISABLE_CLASSLOADER_CHECK_CONFIG)
            .build());

@RegisterExtension
private static final HadoopCatalogExtension CATALOG_EXTENSION =
    new HadoopCatalogExtension(TestFixtures.DATABASE, TestFixtures.TABLE);
```

2. **Schema 与分区规格**：

```java
private static final int NUM_BUCKETS = 4;
private static final Schema SCHEMA =
    new Schema(
        Types.NestedField.optional(1, "ts", Types.TimestampType.withoutZone()),
        Types.NestedField.optional(2, "uuid", Types.UUIDType.get()),
        Types.NestedField.optional(3, "data", Types.StringType.get()));
private static final PartitionSpec SPEC =
    PartitionSpec.builderFor(SCHEMA).hour("ts").bucket("uuid", NUM_BUCKETS).build();
```

3. **`before()`**：创建表，设置 `WRITE_DISTRIBUTION_MODE = RANGE`，设置 sort order `asc(bucket("uuid", 4))`。

4. **5 个 `@Test` 方法**：

```java
testBucketNumberEqualsToWriterParallelism()        // parallelism=4，bucket=4，相等
testBucketNumberLessThanWriterParallelismNotDivisible()  // parallelism=6，bucket=4，bucket<parallelism 不可整除
testBucketNumberLessThanWriterParallelismDivisible()     // parallelism=8，bucket=4，bucket<parallelism 可整除
testBucketNumberHigherThanWriterParallelismNotDivisible() // parallelism=3，bucket=4，bucket>parallelism 不可整除
testBucketNumberHigherThanWriterParallelismDivisible()   // parallelism=2，bucket=4，bucket>parallelism 可整除
```

每个方法调用 `testParallelism(int parallelism)`。

5. **`testParallelism(int parallelism)`**：

```java
try (StreamExecutionEnvironment env = ...) {
  DataGeneratorSource<RowData> generatorSource = new DataGeneratorSource<>(
      new RowGenerator(),
      ROW_COUNT_PER_CHECKPOINT * NUM_OF_CHECKPOINTS,
      RateLimiterStrategy.perCheckpoint(ROW_COUNT_PER_CHECKPOINT),
      FlinkCompatibilityUtil.toTypeInfo(ROW_TYPE));
  DataStream<RowData> dataStream = env.fromSource(generatorSource, WatermarkStrategy.noWatermarks(), "Data Generator");
  FlinkSink.forRowData(dataStream).table(table).tableLoader(tableLoader).writeParallelism(parallelism).append();
  env.execute(getClass().getSimpleName());

  table.refresh();
  List<Snapshot> snapshots = Lists.newArrayList(table.snapshots().iterator());
  snapshots = snapshots.stream().filter(...).collect(...);
  assertThat(snapshots).hasSizeGreaterThanOrEqualTo(NUM_OF_CHECKPOINTS);

  // 取最后 2 个 checkpoint 周期
  List<Snapshot> rangePartitionedCycles = snapshots.subList(snapshots.size() - 2, snapshots.size());
  for (Snapshot snapshot : rangePartitionedCycles) {
    List<DataFile> addedDataFiles = Lists.newArrayList(snapshot.addedDataFiles(table.io()).iterator());
    assertThat(addedDataFiles).hasSizeLessThanOrEqualTo(maxAddedDataFilesPerCheckpoint(parallelism));
  }
}
```

6. **`maxAddedDataFilesPerCheckpoint(int parallelism)`**：

```java
return NUM_BUCKETS + parallelism;
```

注释解释：小样本下流量非完美均衡，范围边界可能跨越 subtask 边界，故每 checkpoint 文件数可能略多于 parallelism 或 bucket 数，但不应超过两者之和；若用 hash 分布则可达 4 × parallelism。

7. **`RowGenerator`**：

```java
private static class RowGenerator implements GeneratorFunction<Long, RowData> {
  private final long ts = System.currentTimeMillis();  // 固定时间戳，所有行进同一小时分区
  @Override
  public RowData map(Long index) throws Exception {
    UUID uuid = UUID.randomUUID();  // 随机 UUID，分布到 4 个 bucket
    ByteBuffer uuidByteBuffer = ByteBuffer.allocate(16);
    uuidByteBuffer.putLong(uuid.getMostSignificantBits());
    uuidByteBuffer.putLong(uuid.getLeastSignificantBits());
    return GenericRowData.of(
        TimestampData.fromEpochMillis(ts),
        uuidByteBuffer.array(),
        StringData.fromString("row-" + index));
  }
}
```

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkRangeDistributionBucketing.java`（新文件，253 行）

**修改目的**：为 Flink 1.20 模块新增相同的 range 分布 + bucket 分区列端到端测试。

**工作逻辑**：与 v1.19 版本完全一致（同一份代码复制到 v1.20 模块），覆盖 Flink 1.20 的 range 分布行为。详见上文 v1.19 部分的逐字段说明。

## 小结

- **成效**：Flink 1.19 与 1.20 模块各新增 5 个端到端测试方法，覆盖 bucket 数（4）与 writer 并行度（2/3/4/6/8）的 5 种关系，验证 range 分布能在不均衡场景下把每 checkpoint 提交的文件数控制在 `NUM_BUCKETS + parallelism` 以内，远优于 hash 分布的 4 × parallelism；同时验证了 range 分布需要 2 个 checkpoint 周期完成统计收集与应用的行为。
- **影响范围**：2 个新文件、506 行新增，全部为测试代码，不影响主代码与发布产物。
- **回迁到 1.4.x 的注意事项**：这是新增测试，不修复任何 bug，**通常无需回迁到 1.4.x**。1.4.x 的 Flink 模块版本可能是 1.17/1.18/1.19/1.20 中的部分子集；若 1.4.x 也包含 v1.19 或 v1.20 模块且希望验证 range 分布行为，可考虑回迁对应版本的测试类，但需确认：1.4.x 的 Flink Sink 是否已支持 `WRITE_DISTRIBUTION_MODE = RANGE` 与 `RangePartitioner`（这是较新特性，1.4.x 可能尚未引入）；若 1.4.x 的 range 分布实现与 main 不同，测试断言阈值（`NUM_BUCKETS + parallelism`）可能不适用。回迁前应先核对 1.4.x 的 Flink Sink range 分布能力，**默认不建议回迁**。
