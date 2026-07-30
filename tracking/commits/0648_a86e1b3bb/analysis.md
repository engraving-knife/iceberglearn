# 提交 0648：将基于 map 统计的范围分区器（range partitioner）回迁到 Flink 1.16/1.18

## 提交信息

- **序号**：0648 / 4088
- **哈希**：a86e1b3bbd4101bd1ecb3cc8551590022f0211ac
- **短哈希**：a86e1b3bb
- **日期**：2024-03-30
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: backport PR #9321 for range partitioner on map statistics (#10061)
- **PR/Issue**：PR #10061（backport 自 PR #9321，对应原始提交 81b62c78e，即跟踪序号 0641）

## 总体目的

本提交是 PR #9321（commit 81b62c78e，跟踪序号 0641）的**回迁（backport）**。原始 PR #9321 在 main 分支上为 Flink 1.17 实现了基于 map 数据统计的范围分区器 `MapRangePartitioner`，用于 Iceberg Flink sink 的 shuffle 优化。但 1.4.x 维护分支需要同时支持 Flink 1.16、1.17、1.18 三个版本，原始 PR 只落地了 v1.17 一个目录，导致 1.16 和 1.18 用户无法使用该特性。

本提交的目标是：把 `MapRangePartitioner`、`MapRangePartitionerBenchmark`、`TestMapRangePartitioner` 三个文件原样复制到 `flink/v1.16/` 和 `flink/v1.18/` 目录下，并更新 `jmh.gradle` 让 JMH 基准测试也覆盖这两个版本，从而让三个 Flink 版本的行为和性能基准保持一致。

## 如何达成设计目的

设计思路是纯文件复制 + 构建脚本扩展，不改变任何代码逻辑：

1. **文件复制**：将 `flink/v1.17/flink/src/` 下的三个文件按相同的相对路径复制到 `flink/v1.16/flink/src/` 和 `flink/v1.18/flink/src/`。因为 Iceberg 的 Flink 多版本目录结构是镜像式的（每个版本一份独立源码），跨版本回迁就是逐字复制。
2. **构建脚本扩展**：在 `jmh.gradle` 中，原本只对 `flinkVersions.contains("1.17")` 时把 `iceberg-flink-1.17` 加入 `jmhProjects`；本提交增加了对 `1.16` 和 `1.18` 的同样处理，使得运行 JMH 基准时也能针对这两个版本编译 benchmark。
3. **不修改 v1.17 的任何文件**：因为 v1.17 已经由原始 PR #9321 落地，本提交不动它，避免重复改动。

## 修改详情

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitioner.java`（新增，381 行）

**修改目的**：为 Flink 1.16 提供 `MapRangePartitioner` 实现，与 v1.17 版本完全一致。

**工作逻辑**：`MapRangePartitioner` 实现 `Flink Partitioner<RowData>`，用于低基数（low-cardinality）场景下基于 `MapDataStatistics`（key→权重的精确统计）做范围分区。核心机制：

- **构造**：接收 schema、sortOrder、`MapDataStatistics`、`closeFileCostInWeightPercentage`。校验所有统计权重 > 0。预构造 `RowDataWrapper`、`SortKey`、`Comparator<StructLike>`。
- **partition(row, numPartitions)**：核心路由方法。懒加载构建 assignment 表（因为 numPartitions 在构造时还不知道）。对每行：
  - 用 `sortKey.wrap(rowDataWrapper.wrap(row))` 复用对象包装出当前行的 sort key。
  - 在 assignment 表中查该 key 的 `KeyAssignment`。若未命中（统计中未学到的 key），走 round-robin 降级（用 `newSortKeyCounter % numPartitions`），并每分钟打一次 INFO 日志统计未知 key 出现次数。
  - 命中则调用 `keyAssignment.select()` 选择目标 subtask。
- **assignment(numPartitions)**：懒构建 key→KeyAssignment 映射。算法：
  1. 计算总权重 `totalWeight`、每个 subtask 目标权重 `targetWeightPerSubtask = totalWeight / numPartitions`。
  2. 计算关闭文件成本（close file cost）权重 `closeFileCostInWeight = ceil(targetWeight * closeFileCost% / 100)`。
  3. 对每个 key 估算会被切分的片数 `estimatedSplits = ceil(weight / targetWeight)`，把估算的 close cost 加到该 key 权重上，放入按 comparator 排序的 `NavigableMap<SortKey, Long> sortedStatsWithCloseFileCost`。
  4. 重新计算含 close cost 的总权重和目标权重。
  5. 调 `buildAssignment` 做 bin packing。
- **buildAssignment**：贪心装箱。按 key 顺序遍历，把每个 key 的权重按目标权重切分到一个或多个 subtask：
  - 若 key 剩余权重 < subtask 剩余容量，全部分给当前 subtask，subtask 剩余容量减少。
  - 否则填满当前 subtask，key 剩余权重减少，subtask 推进到下一个。若分配的权重 ≤ close cost，从 key 剩余权重里"借"一点 padding（最多一个 close cost），避免 subtask 权重过小。
  - 若 key 剩余权重 ≤ close cost，直接丢弃残差（不值得再开新 subtask），这是算法可接受的小不精确。
  - 每个 key 处理完后构造 `KeyAssignment` 记录其被分到的 subtask 列表与对应权重。
- **KeyAssignment.select()**：按各 subtask 权重做加权随机选择。若只有一个 subtask 直接返回；否则生成 `[0, keyWeight)` 随机数，在累计权重数组上二分查找定位 subtask。用 `ThreadLocalRandom` 保证性能。
- **KeyAssignment** 内部记录 `assignedSubtasks`、`subtaskWeightsExcludingCloseCost`、`keyWeight`、`cumulativeWeights`，并校验每个 subtask 权重 > close cost（防止过小分配）。

设计上强调：所有操作在 Flink 单 mailbox 线程执行，无需线程安全；贪心算法不追求完美，允许小偏差以换取简单与平衡。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestMapRangePartitioner.java`（新增，448 行）

**修改目的**：为 Flink 1.16 的 `MapRangePartitioner` 提供完整单元测试覆盖，与 v1.17 完全一致。

**工作逻辑**：测试类定义 10 个 sort key（`k0`~`k9`），构造一个总权重 800 的 `MapDataStatistics`（k0=350, k1=230, k2=120, k3=40, k4=k5=...=10 等），覆盖：
- 基本分区正确性、不同 numPartitions 下的分配。
- close file cost 的影响、padding 行为、残差丢弃。
- 未知 key 的 round-robin 降级。
- `KeyAssignment` 的 `select()` 加权随机分布、`equals/hashCode/toString`。
- `assignmentInfo()` 汇总统计。
- 边界与异常参数校验（权重为 0、空分配等抛 `IllegalArgumentException`）。

### `flink/v1.16/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java`（新增，199 行）

**修改目的**：为 Flink 1.16 提供 JMH 性能基准，与 v1.17 一致。

**工作逻辑**：用 JMH 注解（`@Fork(1)`、`@Warmup(3)`、`@Measurement(5)`、`@BenchmarkMode(SingleShotTime)`）测量 10 万样本下的 partition 吞吐，对比不同 key 基数与 numPartitions 组合，用于评估 partitioner 在大数据量下的性能。

### `flink/v1.18/flink/src/...`（新增三个文件，与 v1.16 同名同内容）

**修改目的**：为 Flink 1.18 提供与 v1.16/v1.17 完全相同的实现、测试与基准。文件内容逐字一致。

### `jmh.gradle`

**修改目的**：让 JMH 基准测试构建覆盖 Flink 1.16 与 1.18。

**工作逻辑**：在原有 `if (flinkVersions.contains("1.17"))` 块前后，增加两个对称块：

```groovy
if (flinkVersions.contains("1.16")) {
  jmhProjects.add(project(":iceberg-flink:iceberg-flink-1.16"))
}
// ... 已有的 1.17 ...
if (flinkVersions.contains("1.18")) {
  jmhProjects.add(project(":iceberg-flink:iceberg-flink-1.18"))
}
```

仅当对应版本在构建属性中启用时才加入 JMH 项目列表，避免强制依赖未启用的版本。

## 小结

- **成效**：本回迁让 Flink 1.16、1.17、1.18 三个版本获得一致的 range partitioner 能力与基准覆盖，消除 1.4.x 分支上多版本支持的不一致。对低基数 sink shuffle 场景的负载均衡有实际收益。
- **影响范围**：纯新增文件 + 构建脚本扩展，不改既有逻辑，无运行时行为变化风险。仅影响 Flink 模块。
- **回迁到 1.4.x 的注意事项**：
  - 本提交本身就是向 1.4.x 的回迁（来自 main 的 PR #9321）。回迁时需确保 v1.16/v1.18 目录在 1.4.x 上存在且其 Flink API 与 v1.17 兼容（`Partitioner`、`RowData`、`RowType` 等 API 在 1.16~1.18 间稳定，已验证可行）。
  - 与跟踪序号 0641（原始 PR #9321，commit 81b62c78e）是同源关系：0641 落地 v1.17，0648 补齐 v1.16/v1.18。回迁时两者必须成对，否则会出现版本间能力不一致。
  - 三个版本目录下的文件必须保持逐字一致，后续若修复 bug 需同步改三份（Iceberg 多版本目录结构的固有维护成本）。
  - `jmh.gradle` 的 `flinkVersions` 属性来自 `System.getProperty("flinkVersions")` 或 `defaultFlinkVersions`，回迁时需确认 1.4.x 的 `build.gradle`/`gradle.properties` 中 `defaultFlinkVersions` 包含 1.16 和 1.18，否则 JMH 块不会触发。
