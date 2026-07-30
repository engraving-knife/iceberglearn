# 提交 0065：Flink: Reverting the default custom partitioner for bucket column (#8848)

## 提交信息

- **序号**：0065 / 4088
- **哈希**：f7f165446f55f44a1d30e1063a47fa2b5046b3ac
- **短哈希**：f7f165446
- **日期**：2023-10-17
- **作者**：kengtin
- **提交说明**：Flink: Reverting the default custom partitioner for bucket column (#8848)
- **PR/Issue**：#8848

## 总体目的

该提交回退了此前 PR #7161（commit `01ab0d931` 引入于 Flink 1.16，由 `21acd34fa` 回port 到 Flink 1.15/1.17）所引入的"在 HASH 分布模式下、当分区 spec 恰好只有一个 bucket 字段时，默认改用 `BucketPartitioner` + `BucketPartitionKeySelector` 做 `partitionCustom`"这一默认行为变更，恢复为原先对所有分区表统一使用 `keyBy(new PartitionKeySelector(...))` 的实现。

PR #7161 的初衷是优化 bucket 分区表的写入数据分布：当表只有一个 bucket 分区字段时，用 `BucketPartitioner` 直接按 bucket 编号把数据分发到对应并行子任务，可以让每个 writer 严格只写自己负责的 bucket，减少小文件、提升写入均衡度。但作为"默认行为"埋入 `FlinkSink.distributeDataStream` 的 HASH 分支后，它改变了所有既有 bucket 分区表用户的下游数据流分区语义，引入了非预期的兼容性问题。提交说明标题明确写 "Reverting the default custom partitioner"——即只回退"默认启用"这一部分，`BucketPartitioner`/`BucketPartitionKeySelector`/`BucketPartitionerUtil` 这些类本身保留，用户仍可在自己的 DataStream 上显式调用 `.partitionCustom(new BucketPartitioner(...), new BucketPartitionKeySelector(...))` 来获得等价效果，只是 Iceberg 不再替用户自动接管。

回退同步作用于 Flink 1.15、1.16、1.17 三个版本模块，每个版本的 `FlinkSink.java` 与 `TestBucketPartitionerFlinkIcebergSink.java` 改动完全一致，符合 Iceberg 多 Flink 版本并行维护时"同源改动同步"的惯例。

## 如何达成设计目的

整体思路是"删除自动分支，恢复统一 keyBy，并把测试改为显式 opt-in"：

1. 在 `FlinkSink.distributeDataStream` 的 `HASH` 分支中，删除 `if (BucketPartitionerUtil.hasOneBucketField(partitionSpec)) { ... partitionCustom ... } else { ... keyBy ... }` 的二选一逻辑，无条件走 `keyBy(new PartitionKeySelector(partitionSpec, iSchema, flinkRowType))`。
2. 在 `TestBucketPartitionerFlinkIcebergSink` 中，把原本依赖 Iceberg 默认行为（`distributionMode(HASH)`）触发 `BucketPartitioner` 的测试，改为在构造 DataStream 时显式 `.partitionCustom(new BucketPartitioner(...), new BucketPartitionKeySelector(...))`，并把 `distributionMode` 由 `HASH` 改为 `NONE`（因为现在由测试自己负责分区，Iceberg 不应再二次分区）。
3. 删除 `testMultipleBucketsFallback` 测试——该测试验证的是"当 spec 有多个 bucket 字段时 `BucketPartitionerUtil.hasOneBucketField` 返回 false、回退到 `keyBy`"的旧行为；既然默认分支已整体删除，这个 fallback 场景不再存在，测试无意义。

## 修改详情

### `flink/v1.{15,16,17}/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`

**修改目的**：移除 HASH 分布模式下对 `BucketPartitioner` 的自动启用，恢复统一 `keyBy(PartitionKeySelector)` 行为。三个 Flink 版本模块改动完全相同。

**工作逻辑**：`distributeDataStream` 的 `case HASH:` 分支中，当 `equalityFieldIds.isEmpty()` 且表已分区（非 unpartitioned）时，原代码为：

```java
if (BucketPartitionerUtil.hasOneBucketField(partitionSpec)) {
  return input.partitionCustom(
      new BucketPartitioner(partitionSpec),
      new BucketPartitionKeySelector(partitionSpec, iSchema, flinkRowType));
} else {
  return input.keyBy(new PartitionKeySelector(partitionSpec, iSchema, flinkRowType));
}
```

回退后改为单行：

```java
return input.keyBy(new PartitionKeySelector(partitionSpec, iSchema, flinkRowType));
```

这样无论 spec 是否含 bucket 字段、含几个 bucket 字段，HASH 模式下一律走 `PartitionKeySelector`，与 PR #7161 之前的实现完全一致。`BucketPartitionerUtil.hasOneBucketField` 在主代码路径中不再被调用（该工具类本身仍保留，供用户测试或自定义 pipeline 使用）。

行为影响：对依赖默认 HASH 分布的既有 bucket 分区表用户，写入数据分布回到 PR #7161 之前的语义（按 `PartitionKeySelector` 计算的 key 做 Flink `keyBy`，可能让一个 writer 收到多个 bucket 的数据）；希望获得 `BucketPartitioner` 行为的用户需要在 `FlinkSink.forRowData(...)` 之前自己调 `.partitionCustom(...)`。

### `flink/v1.{15,16,17}/flink/src/test/java/org/apache/iceberg/flink/sink/TestBucketPartitionerFlinkIcebergSink.java`

**修改目的**：让 `BucketPartitioner` 测试改为显式 opt-in，并删除已无意义的 fallback 测试。三个 Flink 版本模块改动完全相同。

**工作逻辑**：测试 `appendRowsToTable`（构造测试数据流并写入 Iceberg）中，原来依赖 `FlinkSink` 在 HASH 模式下自动启用 `BucketPartitioner`：

```java
.map(converter::toInternal, FlinkCompatibilityUtil.toTypeInfo(SimpleDataUtil.ROW_TYPE));

FlinkSink.forRowData(dataStream)
    .table(table)
    .tableLoader(tableLoader)
    .writeParallelism(parallelism)
    .distributionMode(DistributionMode.HASH)   // 旧：靠 HASH 触发 BucketPartitioner
    .append();
```

改为在 DataStream 上显式 `partitionCustom`，并把 `distributionMode` 改为 `NONE`：

```java
.map(converter::toInternal, FlinkCompatibilityUtil.toTypeInfo(SimpleDataUtil.ROW_TYPE))
.partitionCustom(
    new BucketPartitioner(table.spec()),
    new BucketPartitionKeySelector(
        table.spec(),
        table.schema(),
        FlinkSink.toFlinkRowType(table.schema(), SimpleDataUtil.FLINK_SCHEMA)));

FlinkSink.forRowData(dataStream)
    .table(table)
    .tableLoader(tableLoader)
    .writeParallelism(parallelism)
    .distributionMode(DistributionMode.NONE)   // 新：自己已分区，Iceberg 不再二次分区
    .append();
```

要点：

1. **显式 `partitionCustom`**：直接在 `map` 之后链式调用 `.partitionCustom(new BucketPartitioner(table.spec()), new BucketPartitionKeySelector(table.spec(), table.schema(), FlinkSink.toFlinkRowType(...)))`，等价于 PR #7161 自动注入的那段，但现在由测试自己负责，正好示范了用户 opt-in 的正确用法。
2. **`distributionMode(NONE)`**：因为数据流已经被 `partitionCustom` 按期望分布，再让 `FlinkSink` 在 HASH 模式下做 `keyBy(PartitionKeySelector)` 会破坏 `BucketPartitioner` 的分布效果（Flink 的 `partitionCustom` 与 `keyBy` 是两次独立重分布，后者会覆盖前者）。设为 `NONE` 让 `FlinkSink` 不再做分布，保留 `BucketPartitioner` 的分布结果。这也印证了回退的合理性：在 HASH 模式下自动注入 `partitionCustom` 与 `keyBy` 在语义上会冲突，用户难以预期。
3. **删除 `testMultipleBucketsFallback`**：该测试原用 `@EnumSource(value = TableSchemaType.class, names = "TWO_BUCKETS")` 验证"spec 含两个 bucket 字段时 `hasOneBucketField` 返回 false、回退到 `keyBy`、每个 bucket 恰好 1 个文件"。回退后主代码不再有这个分支，fallback 概念不存在，测试随之删除。

## 小结

该提交回退了 PR #7161 在 Flink `FlinkSink` HASH 分布模式下默认启用 `BucketPartitioner` 的行为，恢复为统一的 `keyBy(PartitionKeySelector)`，同时保留相关类供用户显式 opt-in，是针对默认行为兼容性问题的及时修正，体现了 Iceberg 在"开箱优化"与"行为可预期/向后兼容"之间选择后者的工程取舍。
