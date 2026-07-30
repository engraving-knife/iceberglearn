# 提交 3890：Parquet: Variant shredding follow-ups from PR #14297 (#16818)

## 提交信息

- **序号**：3890 / 4088
- **哈希**：85ffa1984e115e80ba1571f3eb017fcf0ba39031
- **短哈希**：85ffa1984
- **日期**：2026-06-16 14:00:47 -0700
- **作者**：Neelesh Salian
- **提交说明**：Parquet: Variant shredding follow-ups from PR #14297 (#16818)
- **PR/Issue**：#16818

## 总体目的

对之前 PR #14297 实现的 Parquet Variant shredding（变体分片）功能进行后续改进和优化。Variant shredding 是 Iceberg 中将 Variant 类型数据的部分字段提取到 Parquet 列式存储中的技术，可以提升查询性能。

本次后续改进主要解决三个方面的问题：
1. **性能优化**：将 `FieldInfo` 中的类型计数从 `Map<PhysicalType, Integer>` 改为 `int[]` 数组，减少哈希查找开销和装箱开销
2. **缓存与去重**：为 `getMostCommonType()` 添加缓存，避免重复计算
3. **字段选择算法优化**：将字段数量超过上限时的截断逻辑从"排序后取前 N"改为使用优先队列（最小堆）的 Top-K 算法，降低时间复杂度
4. **可观测性**：在 `ParquetFormatModel` 中添加 DEBUG 日志，便于诊断 shredding 行为
5. **确定性保证**：修正 `PathNode` 中 `objectChildren` 从 `TreeMap` 改为 `HashMap`，在输出 schema 时显式排序，确保字段顺序确定

## 如何达成设计目的

主要修改 `VariantShreddingAnalyzer` 分析器的内部数据结构和算法，并在 `ParquetFormatModel` 中添加诊断日志。同时新增测试用例验证 UUID 字段的 shredding 行为和字段截断的正确性。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetFormatModel.java` (+9/-0 lines)

**修改目的**：添加 shredding 过程的 DEBUG 日志。

**工作逻辑**：
新增 Logger 实例，在构建 shredded variant appender 时记录 bufferSize，在 variant 推理时记录行数和识别出的 shredded 字段数：
```java
LOG.debug("Building shredded variant appender with bufferSize={}", bufferSize);
// ...
LOG.debug("Variant inference: rows={}, shredded fields={}", bufferedRows.size(), shreddedTypes.size());
```

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantShreddingAnalyzer.java` (+59/-35 lines)

**修改目的**：优化分析器的性能和确定性。

**工作逻辑**：

1. **Top-K 字段选择优化**（替换排序截断为优先队列）：
```java
// 旧：排序全部 entry 后取前 MAX_SHREDDED_FIELDS 个
// 新：使用最小堆（PriorityQueue）维护 Top-K
Comparator<Map.Entry<String, PathNode>> worstFirst =
    Comparator.<Map.Entry<String, PathNode>>comparingInt(e -> e.getValue().info.observationCount)
        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder());
PriorityQueue<Map.Entry<String, PathNode>> topK = new PriorityQueue<>(MAX_SHREDDED_FIELDS, worstFirst);
for (Map.Entry<String, PathNode> entry : node.objectChildren.entrySet()) {
  if (topK.size() < MAX_SHREDDED_FIELDS) {
    topK.offer(entry);
  } else if (worstFirst.compare(entry, topK.peek()) > 0) {
    topK.poll();
    topK.offer(entry);
  }
}
```
将时间复杂度从 O(n log n) 降至 O(n log k)。

2. **FieldInfo 类型计数优化**：
```java
// 旧：Map<PhysicalType, Integer> typeCounts = Maps.newHashMap();
// 新：
private static final PhysicalType[] PHYSICAL_TYPES = PhysicalType.values();
private final int[] typeCounts = new int[PHYSICAL_TYPES.length];
```
使用数组替代 Map，通过 `type.ordinal()` 索引，消除装箱和哈希查找开销。

3. **getMostCommonType 缓存**：
```java
private boolean mostCommonComputed = false;
private PhysicalType mostCommonCached = null;
// 在 observe() 时重置缓存标记，在 getMostCommonType() 时计算并缓存
```

4. **PathNode 数据结构变更**：
```java
// 旧：Map<String, PathNode> objectChildren = Maps.newTreeMap();
// 新：Map<String, PathNode> objectChildren = Maps.newHashMap();
```
将 TreeMap 改为 HashMap（TreeMap 不再需要因为排序移到了输出阶段），在 `buildObjectGroup` 中显式按字段名排序后输出，确保 schema 字段顺序确定。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantShreddingAnalyzer.java` (+26/-0 lines)

**修改目的**：新增 UUID 字段 shredding 测试和字段截断验证。

**工作逻辑**：
1. 增强 `testShreddedObjectWith300Fields` 测试，断言 `field_0000` 和 `field_0299` 存在，`field_0300` 不存在（验证 MAX_SHREDDED_FIELDS=300 截断）
2. 新增 `testUuidFieldIsTrackedAndShredded` 测试：生成 100 行含 UUID 字段的 variant 数据，验证 UUID 字段被正确 shred，且 typed_value 列带有 `UUIDLogicalTypeAnnotation`

## 总结

对 Variant shredding 分析器进行了多项性能和正确性改进：使用数组替代 Map 存储类型计数、添加结果缓存、将字段截断算法优化为 Top-K 优先队列、确保输出 schema 字段顺序确定性，并添加了诊断日志。这些改进提升了 shredding 分析的效率，特别是在处理大量 Variant 字段时，同时新增测试覆盖了 UUID 类型和字段截断边界场景。
