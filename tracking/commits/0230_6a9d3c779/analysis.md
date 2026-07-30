# 提交 0230：Core: Add PartitionMap (#9194)

## 提交信息

- **序号**：0230 / 4088
- **哈希**：6a9d3c77977baff4295ee2dde0150d73c8c46af1
- **短哈希**：6a9d3c779
- **日期**：2023-12-06
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Add PartitionMap (#9194)
- **PR/Issue**：#9194

## 总体目的

这个提交向 Iceberg core 工具类库新增一个数据结构 `PartitionMap<V>`，用于以"分区（spec ID + 分区值 tuple）"为键关联任意值。它补齐了与已有 `PartitionSet` 配套的"map"侧能力。

背景：Iceberg 中很多按分区粒度的工作需要把每个分区关联一个值——例如每个分区的文件数、数据量大小、最后修改时间、扫描任务数、是否需要重写的标记等。分区在 Iceberg 里由两部分唯一确定：分区规约 ID（`specId`，因为表可以演进多个 partition spec）与分区值 tuple（`StructLike`）。已有 `PartitionSet` 用来表示"一组分区"（如扫描时要跳过的分区集合），但缺少一个对应的 map 结构来承载"分区到值"的映射。

直接用 `Map<Pair<Integer, StructLike>, V>` 或 `Map<Integer, Map<StructLike, V>>` 也能凑合，但有两个隐患：第一，`StructLike` 接口本身不规定 `equals` / `hashCode`，不同实现类（`Row`、`GenericRecord`、`InternalRow` 包装等）互不相等，直接当 key 会导致"同一个分区用不同 StructLike 实现表示时查不到"。这正是上一个提交 0229 修复的 `StructLikeMap` 跨实现类相等问题。第二，缺少类型安全、可读性差的 Pair 操作。

`PartitionMap` 通过两层结构解决这些问题：外层按 `specId` 索引到内层 `StructLikeMap`，内层用 `StructLikeWrapper` 归一化分区值，从而保证"不同 StructLike 实现表示的同一分区"被视为同一 key。对外既提供 `Pair<Integer, StructLike>` 形式的标准 `Map` API（与 `PartitionSet` 一致、可被通用集合代码消费），又提供 `(int specId, StructLike struct)` 形式的原生 API（避免每次构造 Pair 的开销）。这个数据结构是后续引擎集成（Spark/Flink）在分区级统计、增量扫描、分区级裁剪与任务规划等场景的基础工具。

## 如何达成设计目的

整体设计是把 `PartitionMap<V>` 实现为 `AbstractMap<Pair<Integer, StructLike>, V>` 的子类，内部维护 `Map<Integer, Map<StructLike, V>> partitionMaps`（每个 specId 对应一个 `StructLikeMap`），并持有 `Map<Integer, PartitionSpec> specs` 用于按 specId 找到 `PartitionSpec`、进而拿到 `spec.partitionType()` 来构造内层 `StructLikeMap`。

设计要点：
- 内层用 `StructLikeMap` 而不是普通 `HashMap`，直接复用提交 0229 修复后的跨实现类相等性，保证 `CustomRow("aaa")` 与 `Row("aaa")` 作为 key 等价。
- 对外双 API：标准 `Map` 接口方法（`put(Pair, V)`、`get(Object)`、`containsKey(Object)` 等）通过 `execute` 派发到原生方法；原生方法（`put(int, StructLike, V)`、`get(int, StructLike)`、`containsKey(int, StructLike)`、`removeKey(int, StructLike)`、`computeIfAbsent(int, StructLike, Supplier)`）直接操作两层结构，避免 Pair 装箱。
- `keySet()` 复用 `PartitionSet.create(specs)`，把所有 (specId, partition) 倒进去再返回不可修改视图；`entrySet()` 用自定义 `PartitionEntry` 包装 (specId, 内层 entry)。`values()` 拍平所有内层 map 的 value。
- `PartitionEntry` 自带基于 `specId` 与内层 entry 的 `equals` / `hashCode`，并禁止 `setValue`（不可变 entry）。
- 视图（keySet/values/entrySet）均返回不可修改，避免外部破坏内部一致性。
- 显式支持 `null` 分区 tuple（对应未分区 spec，即 `PartitionSpec.unpartitioned()` 的唯一分区）；显式拒绝 `null` Pair key（抛 `NullPointerException`）。
- 文档明确：不支持并发写，但支持无写并发读（依赖 `StructLikeMap` 的 thread-local wrapper）。

同时把 `PartitionSet` 从 `implements Set<Pair<Integer, StructLike>>` 改为 `extends AbstractSet<Pair<Integer, StructLike>>`，使其继承 `AbstractSet` 提供的基于内容的 `equals` / `hashCode`，从而 `PartitionMap.keySet()` 返回的 `PartitionSet` 可以与另一个 `PartitionSet` 正确比较相等（测试 `testKeyAndEntrySetEquality` 即验证此路径）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/PartitionMap.java`（新文件，257 行）

**修改目的**：新增 `PartitionMap<V>` 数据结构。

**工作逻辑**：
- 类签名 `public class PartitionMap<V> extends AbstractMap<Pair<Integer, StructLike>, V>`。字段：`Map<Integer, PartitionSpec> specs`、`Map<Integer, Map<StructLike, V>> partitionMaps`。私有构造，静态工厂 `create(Map<Integer, PartitionSpec> specs)`。
- `size()`：对所有内层 map 求和；`isEmpty()`：所有内层 map 都空才空。
- `containsKey(Object key)` / `get(Object key)` / `remove(Object key)`：通过 `execute(key, action, defaultValue)` 派发。`execute` 检查 key 是否为 `Pair` 且 `first` 是 `Integer`、`second` 是 null 或 `StructLike`，是则调用对应的原生 `(specId, struct)` 动作；否则（包括非 Pair 类型如 String）返回默认值。若 key 为 null 抛 `NullPointerException`（"does not support null keys"）。
- 原生方法：`containsKey(int specId, StructLike)`、`get(int specId, StructLike)`、`put(int specId, StructLike, V)`、`removeKey(int specId, StructLike)`、`computeIfAbsent(int specId, StructLike, Supplier<V>)`。其中 `put` / `computeIfAbsent` 用 `partitionMaps.computeIfAbsent(specId, this::newPartitionMap)` 懒初始化内层 map。
- `newPartitionMap(int specId)`：从 `specs` 取 `PartitionSpec`，`Preconditions.checkNotNull(spec, "Cannot find spec with ID %s: %s", specId, specs)`，再用 `StructLikeMap.create(spec.partitionType())` 构造内层 map——这是保证跨实现类 key 相等的关键。
- `keySet()`：构造一个 `PartitionSet.create(specs)`，遍历 `partitionMaps` 把每个 (specId, partition) `add` 进去，返回 `Collections.unmodifiableSet(keySet)`。
- `values()`：拍平所有内层 map 的 value 到一个 `List`，返回不可修改集合。
- `entrySet()`：构造 `Set<Entry<Pair<Integer, StructLike>, V>>`，对每个内层 entry 包装成 `PartitionEntry(specId, innerEntry)`，返回不可修改集合。
- `putAll(Map)`：`forEach(this::put)`。
- `clear()`：`partitionMaps.clear()`。
- `toString()`：按 spec 用 `spec.partitionToPath(struct)` 把分区转成可读路径（如 `data=aaa -> v1`），用 `", "` 连接，整体包 `{}`。对 `value == this` 用 `"(this Map)"` 防自环。
- 内部类 `PartitionEntry<V> implements Entry<Pair<Integer, StructLike>, V>`：持有 `int specId` 与 `Entry<StructLike, V> structAndValue`。`getKey()` 返回 `Pair.of(specId, structAndValue.getKey())`，`getValue()` 返回内层 value，`setValue` 抛 `UnsupportedOperationException`。`equals` / `hashCode` 基于 `specId` 与 `structAndValue`（内层 entry 自带基于 `StructLikeWrapper` 的相等性）。
- 类 Javadoc 说明：键为 (spec ID, partition tuple)；内部用 `StructLikeMap` 保证一致 hashing/equals；不支持并发写但支持并发读；不支持 null Pair 但支持 null 分区 tuple。

### `core/src/main/java/org/apache/iceberg/util/PartitionSet.java`

**修改目的**：让 `PartitionSet` 继承 `AbstractSet`，获得基于内容的 `equals` / `hashCode`，使 `PartitionMap.keySet()` 返回的 `PartitionSet` 可被正确比较。

**工作逻辑**：新增 `import java.util.AbstractSet;`，把类签名从 `public class PartitionSet implements Set<Pair<Integer, StructLike>>` 改为 `public class PartitionSet extends AbstractSet<Pair<Integer, StructLike>>`。其余实现不变。`AbstractSet` 提供了基于 entrySet 的 `equals`（两个 set 含相同元素即相等）与 `hashCode`（元素 hashCode 之和），这正是 `PartitionMap.keySet()` 在跨 map 比较时所需。这一改动配合 0229 对 `StructLikeMap` 相等性的修复，使 `PartitionMap` 的 keySet/entrySet 相等性完整可用。

### `core/src/test/java/org/apache/iceberg/util/TestPartitionMap.java`（新文件，294 行）

**修改目的**：全面覆盖 `PartitionMap` 的功能、边界与并发读行为。

**工作逻辑**：定义 schema 与三个 spec（unpartitioned、`BY_DATA_SPEC` specId=1、`BY_DATA_CATEGORY_BUCKET_SPEC` specId=3）组成 `SPECS` map。测试用例覆盖：
- `testEmptyMap`：空 map 的 size/values/keySet/entrySet 均空。
- `testSize`：跨 spec、含 null 分区的 4 个 entry size 为 4。
- `testDifferentStructLikeImplementations`：用 `CustomRow` 与 `Row` 混作 key 验证跨实现类归一化（依赖 0229 修复）。
- `testPutAndGet` / `testRemove` / `putAll` / `testClear` / `testValues` / `testEntrySet` / `testKeySet`：基本 CRUD 与视图。
- `testEqualsAndHashCode`：两个 map 分别用 `Row` 与 `CustomRow` 放相同逻辑数据，断言相等且 hashCode 相同。
- `testToString`：验证 `data=aaa -> v1`、`data=ccc/category_bucket=2 -> v3` 等可读路径。
- `testConcurrentReadAccess`：10 线程并发读，确保 thread-local wrapper 生效、并发读安全。
- `testNullKey`：put/get/remove null Pair key 抛 `NullPointerException`。
- `testUnknownSpecId`：用 `Integer.MAX_VALUE` 作 specId 抛 `NullPointerException`（来自 `newPartitionMap` 的 `Preconditions.checkNotNull`），信息含 "Cannot find spec with ID"。
- `testUnmodifiableViews`：对 keySet/values/entrySet 的 add、entrySet 的 setValue 与 iterator remove 均抛 `UnsupportedOperationException`。
- `testKeyAndEntrySetEquality`：两个 map 的 keySet/entrySet 跨实现类相等（依赖 `PartitionSet extends AbstractSet` 改动）。
- `testLookupArbitraryKeyTypes`：用非 Pair 的 String key 调 `containsKey`/`get`/`remove` 返回 false/null/null，不抛异常。

## 小结

本提交新增 `PartitionMap`——一个以 (spec ID, 分区值) 为键、内部复用 `StructLikeMap` 保证跨实现类相等性的 map 数据结构，与 `PartitionSet` 配对补齐了分区粒度工具集，为 Iceberg 引擎集成中按分区聚合值的场景提供类型安全且正确的基础设施。
