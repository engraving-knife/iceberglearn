# 提交 1539 de54a0867 分析

## 提交信息
- 哈希：de54a0867306c54655bb101fecb14d7264ed62de
- 日期：2024-12-27（Fri Dec 27 09:28:40 2024 +0800）
- 作者：big face cat <731030576@qq.com>（合作者：huyuanfeng <huyuanfeng@huya.com>）
- 消息：Flink: Avoid RANGE mode broken chain when write parallelism changes (#11702)

## 总体目的

Iceberg 的 Flink sink 在 `write-distribution-mode=RANGE` 下，数据流经过：上游 `DataStatisticsOperator`（采集范围统计信息）→ `partitionCustom(RangePartitioner)`（按范围分区）→ 过滤出真实记录 → 提取记录 → 下游 writer 算子（`IcebergStreamWriter`，并行度为 `writerParallelism`）。

Flink 的"算子链"（operator chaining）机制可将相邻算子融合到同一个 task 中执行，避免序列化/反序列化与网络传输开销，是性能优化的关键。但算子链有前提：相邻算子的并行度必须相同。原实现中，范围分区后的 `filter(StatisticsOrRecord::hasRecord).map(StatisticsOrRecord::record)` 没有显式设置并行度，会继承默认并行度（通常等于 env parallelism）。而下游 writer 算子的并行度为 `writerParallelism`。

当 `writerParallelism` 与 env/upstream parallelism 不同时（例如 env=1 但 writerParallelism=2，即"写并行度大于上游并行度"的常见扩写场景），`filter`/`map` 算子的并行度与 writer 不一致，导致算子链断裂（broken chain）。结果是：本可在同 task 内直接传递的记录，被迫经过一次网络 shuffle/序列化，性能下降；同时范围分区的收益被削弱。

本提交将 `filter` + `map` 两步合并为单个 `flatMap`（同时完成"过滤掉纯统计条目"与"提取 RowData 记录"），并显式 `.setParallelism(writerParallelism)`，使该算子与下游 writer 并行度一致，从而促进算子链重建。这是一个 RANGE 模式下并行度变更场景的性能修复。

## 如何达成设计目的

核心改动在 `FlinkSink.distributeStream(...)` 的 RANGE 分支：用 `flatMap` 替代 `filter`+`map`，并设置并行度为 `writerParallelism`。配套地，测试类引入独立的 `writeParallelism` 参数（可与 env `parallelism` 不同），新增 `parallelism=1, writeParallelism=2` 的测试组合，覆盖"并行度变更"这一原断裂场景。

### 修改详情

#### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`

**修改目的**：避免 RANGE 模式下 filter/map 与 writer 之间因并行度不一致而断链。

**工作逻辑**：

修改前（约 669 行）：
```java
return shuffleStream
    .partitionCustom(new RangePartitioner(iSchema, sortOrder), r -> r)
    .filter(StatisticsOrRecord::hasRecord)
    .map(StatisticsOrRecord::record);
```

修改后：
```java
return shuffleStream
    .partitionCustom(new RangePartitioner(iSchema, sortOrder), r -> r)
    .flatMap(
        (FlatMapFunction<StatisticsOrRecord, RowData>)
            (statisticsOrRecord, out) -> {
              if (statisticsOrRecord.hasRecord()) {
                out.collect(statisticsOrRecord.record());
              }
            })
    // Set the parallelism same as writerParallelism to
    // promote operator chaining with the downstream writer operator
    .setParallelism(writerParallelism)
    .returns(RowData.class);
```

关键点：

1. **`flatMap` 合并 filter+map**：`StatisticsOrRecord` 既可能是"纯统计条目"（`hasRecord()==false`，仅承载范围统计信息，参与 RangePartitioner 计算后应被丢弃），也可能是"真实记录"。`flatMap` 在收到条目时，仅当 `hasRecord()` 为真才 `out.collect(record())`，等价于 `filter(hasRecord).map(record)`，但合并为单算子。
2. **`.setParallelism(writerParallelism)`**：显式将该算子并行度设为与下游 writer 一致。Flink 据此可把 `flatMap` 与 writer 融合到同一 task 链中，避免断链。注意上游 `shuffleStream`（DataStatisticsOperator）仍保持 `input.getParallelism()`（与输入一致以鼓励其与上游链式），RangePartitioner 跨网络重分布后，flatMap 在 writer 并行度上接收数据并与 writer 链式。
3. **`.returns(RowData.class)`**：因使用 lambda 定义 `FlatMapFunction`，Java 类型擦除导致 Flink 无法推断输出类型，需通过 `returns(...)` 显式提供 `TypeInformation`，否则运行时报错。原 `map(StatisticsOrRecord::record)` 因方法引用可被反射解析返回类型而不需要此声明。
4. 新增 import `org.apache.flink.api.common.functions.FlatMapFunction`。

#### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkDistributionMode.java`

**修改目的**：覆盖 `writeParallelism` 与 env `parallelism` 不同的场景，验证断链修复。

**工作逻辑**：

1. 新增 `@Parameter(index = 2) private int writeParallelism;` 字段，使写并行度成为独立测试参数。
2. `parameters()` 由原来的 4 组 `{parallelism, partitioned}` 扩展为 6 组 `{parallelism, partitioned, writeParallelism}`，新增 `{1, true, 2}` 与 `{1, false, 2}` 两组——这正是原 bug 触发场景（env 并行度 1，写并行度 2）。
3. `setMaxParallelism(parallelism)` 改为 `setMaxParallelism(Math.max(parallelism, writeParallelism))`，因为 max parallelism 不能小于实际并行度，否则 Flink 配置非法。
4. 所有 `builder.writeParallelism(parallelism)` 改为 `builder.writeParallelism(writeParallelism)`，使写并行度由新参数控制而非绑定到 env 并行度。
5. 断言中"每个 writer task 应只写一个文件"的 `assertThat(addedDataFiles).hasSize(parallelism)` 改为 `hasSize(writeParallelism)`，因为文件数取决于 writer 并行度。
6. "验证 min-max 统计范围无重叠"的条件判断从 `if (parallelism == 2)` 调整为 `if (writeParallelism > 1)`（或 `> 2`，视具体测试方法），以正确触发多文件范围校验。

## 小结

- **成效**：修复了 Flink sink RANGE 分布模式下，当 `writerParallelism` 与上游/env 并行度不同时 `filter`+`map` 与 writer 算子链断裂的性能问题。合并为 `flatMap` 并显式设置 `writerParallelism`，使算子链重建，避免不必要的网络 shuffle 与序列化开销。
- **影响范围**：`flink/v1.20` 模块的 `FlinkSink.java`（主代码 1 处）与对应测试。仅影响 RANGE 分布模式，不影响 HASH/NONE 模式。
- **回迁到 1.4.x 的注意事项**：这是一处**真正的性能 bug 修复**，影响所有在 RANGE 模式下使用与 env 并行度不同的 `writeParallelism` 的用户（扩写场景常见）。若 1.4.x 的 Flink 集成存在相同代码结构（filter+map 未设并行度），**建议回迁**。需注意 1.4.x 可能对应不同 Flink 版本模块（v1.15/v1.16/v1.17/v1.18/v1.19/v1.20），应对每个存在的 Flink 版本模块分别应用相同修复。回迁时同时带回测试参数化改动以验证修复有效。
