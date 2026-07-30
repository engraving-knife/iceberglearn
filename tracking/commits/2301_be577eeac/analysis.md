# 提交 2301：Core: Maintain passed in ordering of files in Manifest Lists (#13411)

## 提交信息

- **序号**：2301 / 4088
- **哈希**：be577eeac631d77243beb57409e476bf197f79d7
- **短哈希**：be577eeac6
- **日期**：2025-07-01 10:44:34 -0700
- **作者**：Russell Spitzer
- **提交说明**：Core: Maintain passed in ordering of files in Manifest Lists (#13411)
- **PR/Issue**：#13411

## 总体目的

本提交修复了 `SnapshotProducer` 中并行写入 Manifest 文件时丢失文件顺序的问题。此前，当文件被分组并并行写入 Manifest 时，结果通过 `ConcurrentLinkedQueue` 收集，而并发队列不保证元素顺序与提交顺序一致。这导致 Manifest 列表中的 Manifest 文件顺序可能与输入文件分组的顺序不同。

虽然 Iceberg 规范不要求 Manifest 列表中的 Manifest 文件保持特定顺序（读取时会根据序列号等元数据排序），但保持写入顺序有以下好处：
1. **可预测性**：Manifest 列表的顺序可预测，便于调试和验证
2. **性能一致性**：某些读取路径可能依赖 Manifest 的物理顺序，保持一致可以避免性能波动
3. **数据一致性**：确保相同的输入产生相同的输出，有利于快照的可重复性

## 如何达成设计目的

核心思路是用 `AtomicReferenceArray` 替代 `ConcurrentLinkedQueue` 来收集并行任务的结果。每个任务被分配一个索引（对应其在分组中的位置），任务完成后将结果写入数组的对应位置。最后按索引顺序遍历数组，收集结果，从而保持原始顺序。

具体步骤：
1. 将文件分组列表转换为带索引的 `Pair<Integer, List<F>>` 列表
2. 创建与分组数量等大的 `AtomicReferenceArray`
3. 每个并行任务将结果写入数组的对应索引位置
4. 按索引顺序遍历数组，构建有序的 `ImmutableList`

## 修改详情

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (+24/-8 lines)

**修改目的**：在 `prepareManifests` 方法中保持文件分组的原始顺序。

**工作逻辑**：

原实现使用 `Queue<ManifestFile> manifests = Queues.newConcurrentLinkedQueue()` 收集结果，并行任务通过 `manifests.addAll(writeFunc.apply(group))` 向队列添加结果。由于并发队列不保证顺序，最终 `ImmutableList.copyOf(manifests)` 的顺序不确定。

新实现：
1. 创建带索引的分组列表 `List<Pair<Integer, List<F>>> groupsWithIndex`
2. 创建 `AtomicReferenceArray<List<ManifestFile>> results`，大小等于分组数量
3. 并行任务中，每个任务将 `writeFunc.apply(group)` 的结果写入 `results.set(index, groupResults)`
4. 最后按索引顺序遍历 `results`，使用 `ImmutableList.builder()` 构建有序结果列表

同时移除了不再需要的 `Queue` 和 `Queues` 导入，添加了 `AtomicReferenceArray` 和 `Pair` 导入。

### `core/src/jmh/java/org/apache/iceberg/AppendBenchmark.java` (+1/-0 lines)

**修改目的**：修复基准测试的 setup 逻辑。

**工作逻辑**：在 `setupBenchmark` 方法中添加 `dropTable()` 调用，确保每次基准测试运行前先删除已有表。这避免了因表已存在导致的测试失败，使基准测试可以重复运行。

### `jmh.gradle` (+1/-0 lines)

**修改目的**：为 JMH 基准测试设置足够的堆内存。

**工作逻辑**：添加 `jvmArgs = ['-Xmx32g']`，为基准测试 JVM 分配 32GB 堆内存，确保大规模数据写入测试不会因内存不足而失败。

## 总结

本提交修复了并行写入 Manifest 文件时丢失顺序的问题，通过使用 `AtomicReferenceArray` 替代 `ConcurrentLinkedQueue` 确保结果顺序与输入分组顺序一致。这是一个正确性和可预测性的改进，同时附带了基准测试的基础设施修复。修改简洁且不影响性能（仅增加了索引分配的开销，可忽略不计）。
