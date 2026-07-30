# 提交 0641：Flink: implement range partitioner for map data statistics

## 提交信息

- **序号**：0641 / 4088
- **哈希**：81b62c78e0c230516090becda7d6040ee03e6a91
- **短哈希**：81b62c78e
- **日期**：2024-03-27（Wed Mar 27 13:46:49 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: implement range partitioner for map data statistics (#9321)
- **PR/Issue**：#9321

## 总体目的

本提交为 Iceberg 的 Flink sink shuffle 机制实现了一个基于 Map 数据统计的范围分区器（range partitioner）。这是 Iceberg 在 Flink 写入链路中实现数据分布优化的一部分。

背景动机：
- Iceberg 的 Flink sink 支持按排序键（sort key）写入数据，以便生成按序排列的数据文件，利于后续查询加速。
- 但是当多个上游 subtask 并发写入时，如果使用默认的 hash 或 round-robin 分区，会导致每个 subtask 都接收到所有 key 的数据，写出大量小文件且数据倾斜严重。
- 为了让相同/相近 sort key 的数据被路由到同一下游 subtask，需要基于已收集到的数据统计（DataStatistics）进行范围分区。
- 本提交聚焦于 `MapDataStatistics` 这种统计形式，它适用于低基数（low-cardinality）场景：key 数量有限，可以用 Map 精确记录每个 key 的权重（出现次数/记录数）。
- 该分区器采用贪心装箱（bin packing）算法，将各 sort key 的权重按排序顺序依次分配到下游 subtask，使每个 subtask 的总权重尽量接近目标权重，从而实现写入负载均衡，同时保证相同 key 的数据落到同一（或少数）subtask，减少小文件。

## 如何达成设计目的

整体设计思路：

1. **实现 Flink 的 `Partitioner<RowData>` 接口**：`MapRangePartitioner` 实现 `partition(RowData row, int numPartitions)`，由 Flink runtime 在每条记录下发时调用，决定记录路由到哪个下游 subtask。

2. **基于已学习的 Map 统计构建分配表（assignment table）**：
   - 构造时传入 `MapDataStatistics`（内含 `Map<SortKey, Long>`，key 到权重的映射）和 `closeFileCostInWeightPercentage`（关闭文件成本占目标权重的百分比）。
   - 由于构造时还不知道下游分区数（`numPartitions` 在 `partition()` 调用时才传入），分配表采用懒加载（lazy）方式在首次 `partition()` 调用时构建。

3. **贪心装箱算法（bin packing）**：
   - 按 sort key 的 comparator 排序统计 map，得到 `NavigableMap<SortKey, Long>`。
   - 计算每个 subtask 的目标权重 = 总权重 / 分区数。
   - 将 close file cost（关闭文件成本）折算成权重，叠加到每个 key 的权重上（按预估切分次数计算），得到 `sortedStatsWithCloseFileCost`。
   - 重新计算包含 close file cost 的总权重和目标权重（向上取整）。
   - 调用 `buildAssignment` 顺序遍历排序后的 key，将每个 key 的权重依次"填充"到当前 subtask，填满后切到下一个 subtask；一个 key 可能跨多个 subtask（重 key），多个 key 也可能落在同一 subtask（轻 key）。

4. **带权重概率的 subtask 选择**：
   - 对每个 key 生成 `KeyAssignment`，记录其被分配到的 subtask 列表及各 subtask 的权重（已扣除 close file cost）。
   - 路由时通过 `ThreadLocalRandom` 生成随机数，在累积权重数组上做二分查找，按权重比例选择 subtask。这样重 key 的流量会按权重分散到多个 subtask，而轻 key 只落一个 subtask。

5. **对未知 key 的兜底**：若运行时遇到统计中未学习到的 key，回退到 round-robin（用计数器取模），并限速日志（每分钟一次 INFO）告警。

6. **线程安全说明**：所有操作在单个 Flink mailbox 线程内执行，无需做线程安全处理。

7. **配套基准测试与 JMH 集成**：新增 `MapRangePartitionerBenchmark` 用长尾分布（long-tail distribution）数据衡量 partitioner 路由性能；并在 `jmh.gradle` 中将 `iceberg-flink-1.17` 加入 JMH 项目列表，使 benchmark 可运行。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitioner.java`

**修改目的**：新增范围分区器核心实现，基于 MapDataStatistics 将 RowData 按 sort key 范围分区到下游 subtask。

**工作逻辑**：

- **构造函数**：校验所有统计权重 > 0；初始化 `RowDataWrapper`、`SortKey`、`SortOrderComparators` 生成的 comparator、`mapStatistics`、`closeFileCostInWeightPercentage`，以及新 key 计数器和限速日志时间戳。

- **`partition(RowData row, int numPartitions)`**：
  1. 懒加载调用 `assignment(numPartitions)` 构建分配表。
  2. 复用 `sortKey` 和 `rowDataWrapper`（避免对象分配），将 row 包装成 SortKey。
  3. 查分配表：若 key 不存在，计数器 +1 并 round-robin（取模 numPartitions），限速日志；若存在，调用 `keyAssignment.select()`。

- **`assignment(int numPartitions)`（懒加载构建分配表）**：
  1. 计算 `totalWeight`（原始权重总和）和 `targetWeightPerSubtask = totalWeight / numPartitions`。
  2. 计算 `closeFileCostInWeight = ceil(targetWeightPerSubtask * closeFileCostInWeightPercentage / 100)`。
  3. 构建 `sortedStatsWithCloseFileCost`：对每个 key，按 `ceil(weight / targetWeightPerSubtask)` 估算切分次数，将 `closeFileCostInWeight * 估算切分次数` 叠加到权重上。
  4. 重新计算含 close cost 的总权重和目标权重（向上取整，保证不超额分配 subtask）。
  5. 调用 `buildAssignment` 生成最终的 `Map<SortKey, KeyAssignment>`。

- **`buildAssignment(...)`（贪心装箱核心）**：
  - 用迭代器遍历排序后的 key，维护 `subtaskId`、`keyRemainingWeight`（当前 key 剩余权重）、`subtaskRemainingWeight`（当前 subtask 剩余配额）。
  - 循环条件：`mapKeyIterator.hasNext() || currentKey != null`（即还有 key 未处理完）。
  - 若 `keyRemainingWeight < subtaskRemainingWeight`：把 key 剩余全分给当前 subtask，subtask 配额减去相应权重，key 处理完。
  - 否则：填满当前 subtask（分配 `subtaskRemainingWeight`），key 剩余权重扣减；若分配量 ≤ closeFileCost，则从 key 剩余中再补一点 padding（不超过 closeFileCost），避免给新 subtask 分配过小权重；然后 subtaskId++，subtask 配额重置。
  - 若 key 剩余权重 ≤ closeFileCost，直接丢弃残余（注释说明：给新 subtask 分配小于 close cost 的权重无意义，小范围不精确可接受）。
  - key 处理完后构造 `KeyAssignment` 存入分配表。
  - 安全检查：若 subtaskId 超过 numPartitions 但还有 key，抛 `IllegalStateException`（理论上因 targetWeight 用 ceil 不应发生）。

- **`assignmentInfo()`**：返回每个 subtask 的（已分配权重，key 数量）摘要，用于诊断/测试。

- **内部类 `KeyAssignment`**：
  - 字段：`assignedSubtasks`（int[]）、`subtaskWeightsExcludingCloseCost`（long[]，路由用，已扣除 close cost）、`keyWeight`（总和）、`cumulativeWeights`（累积权重数组，用于二分查找）。
  - 构造校验：subtasks 非空、weights 非空、长度一致、每个 weight > closeFileCostInWeight。
  - `select()`：若只有一个 subtask 直接返回；否则生成 `[0, keyWeight)` 随机数，在 `cumulativeWeights` 上二分查找定位 position（`Math.abs(index + 1)` 处理负返回值），返回对应 subtask。这保证重 key 流量按权重比例分散。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestMapRangePartitioner.java`

**修改目的**：新增单元测试，验证 `MapRangePartitioner` 的分配表构建和路由逻辑正确性。

**工作逻辑**：
- 使用 `TestFixtures.SCHEMA` 和按 `data` 字段升序的 SortOrder，预构造 10 个 SortKey（k0..k9）。
- 构造 `MapDataStatistics`，总权重 800，呈长尾分布（k0=350, k1=230, k2=120, k3=40, k4..k9 各 10）。
- 测试用例覆盖：
  - `testEvenlyDividableNoClosingFileCost`：8 分区、close cost=0，验证每个 subtask 目标权重 100，重 key（k0）跨 4 个 subtask，轻 key（k4..k9）合并到 subtask 7。
  - 其他用例覆盖带 close file cost、不整除、单分区等场景，断言 `KeyAssignment` 的 subtask 列表和权重列表与预期一致。
- 使用 AssertJ（`assertThat`）和 JUnit5（`@Test`）。

### `flink/v1.17/flink/src/jmh/java/org/apache/iceberg/flink/sink/shuffle/MapRangePartitionerBenchmark.java`

**修改目的**：新增 JMH 基准测试，衡量 partitioner 在长尾分布数据下的路由吞吐/延迟。

**工作逻辑**：
- Schema 含 9 个字段（id + 8 个随机字符串），SortOrder 按 id 升序。
- `setupBenchmark()`：
  - 用 `longTailDistribution(100_000, 24, 240, 100, 2.0)` 生成 264 个 key 的长尾权重（前 24 个权重减半衰减，后 240 个为 base weight 随机抖动）。
  - 构造 `MapRangePartitioner`（close cost percentage=2）。
  - 按权重 CDF 预采样 100_000 条 RowData，使采样数据分布与统计一致。
- `@Benchmark testPartitionerLongTailDistribution`：单线程顺序对 100_000 条记录调用 `partitioner.partition(rows[i], 128)`，结果丢入 Blackhole。
- JMH 配置：`@Fork(1)`、`@Warmup(3)`、`@Measurement(5)`、`Mode.SingleShotTime`（单次执行计时，适合衡量一批记录的总耗时）。
- 辅助方法 `binarySearchIndex`（按 CDF 采样）、`randomString`（生成随机字符串模拟真实记录体积）、`longTailDistribution`（生成长尾权重 map）。

### `jmh.gradle`

**修改目的**：将 Flink 1.17 模块纳入 JMH 基准测试项目列表，使新增的 Flink partitioner benchmark 能被 JMH 插件编译运行。

**工作逻辑**：
- 新增读取 `flinkVersions` 系统属性（回退到 `defaultFlinkVersions`）并按逗号分割。
- 若 `flinkVersions` 包含 `"1.17"`，将 `project(":iceberg-flink:iceberg-flink-1.17")` 加入 `jmhProjects` 列表。
- 此后 `configure(jmhProjects)` 块会对该模块应用 `me.champeau.jmh` 插件并配置 JMH 参数。

## 小结

本提交为 Iceberg Flink sink 的 shuffle 机制补上了基于 Map 统计的范围分区器核心实现，是数据分布优化系列（还有基于 Sketch 的高基数统计分区器，见相关 DataStatistics 子类）的低基数版本。

成效：
- 通过贪心装箱 + 权重概率路由，实现按 sort key 范围分区，减少下游小文件、缓解数据倾斜。
- 重 key 自动跨 subtask 分散，轻 key 合并到同一 subtask，兼顾负载均衡与文件紧凑。
- 配套完整单元测试和 JMH 基准，验证正确性与性能。

影响范围：
- 仅新增文件，不改动既有逻辑，风险低。
- 主要影响 `flink/v1.17` 模块；`jmh.gradle` 改动使 Flink 1.17 可参与 JMH benchmark。

回迁到 1.4.x 注意事项：
- 需确认 1.4.x 分支是否已包含 `sink/shuffle` 包及 `MapDataStatistics`、`SortKey`、`SortOrderComparators` 等依赖类；若 shuffle 机制在 1.4.x 尚未引入，则需连同前置提交一起回迁。
- `jmh.gradle` 中需确认 1.4.x 的 Flink 版本范围是否覆盖 1.17；若 1.4.x 主力 Flink 版本不同，需调整版本判断条件。
- 测试使用 JUnit5 和 AssertJ，需确认 1.4.x 测试框架版本兼容。
