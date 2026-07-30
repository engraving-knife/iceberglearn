# 提交 4076：Core: Precompute sorted entries in ShreddedObject SerializationState

## 提交信息

- **序号**：4076 / 4088
- **哈希**：1ec15051dc09ef6a0d61fa332eb8f2503f3170f8
- **短哈希**：1ec15051d
- **日期**：2026-07-19 09:35:01 -0700
- **作者**：Neelesh Salian
- **提交说明**：Core: Precompute sorted entries in ShreddedObject SerializationState (#17114)
- **PR/Issue**：#17114

## 总体目的

这个提交针对 `ShreddedObject`（Iceberg 中用于处理 Variant 类型部分/完全 shredding 的对象）的序列化路径进行性能优化。原本在每次 `writeTo` 调用时，都需要在序列化阶段即时对未 shredding 和已 shredding 的字段做合并排序（通过 `SortedMerge` 工具类将两个 sorted key 集合归并），并对每个字段名调用 `metadata.id(name)` 来查找字段 ID。这导致两个性能问题：

1. 排序和归并操作在每次写入时都会重复执行，即使 `SerializationState` 是一次构建、可多次写入的；
2. 字段 ID 查询也是逐次写入时执行，重复工作。

本提交将所有需要序列化的字段条目预先在 `SerializationState` 构造时就完成排序并缓存为 `Entry[]` 数组，同时预先计算好字段 ID 和每个值的大小。这样后续的 `writeTo` 只需顺序遍历数组进行字节写入，无需重复计算。同时，新的实现还引入了对 `writeTo` 实际写入字节数与 `sizeInBytes` 报告大小的一致性校验，提升序列化路径的健壮性。

## 如何达成设计目的

设计思路是引入一个内部 `Entry` 类，统一描述一个待序列化字段条目（包含字段 ID、shredded 值或 raw buffer、以及大小），并在 `SerializationState` 构造函数中：

- 使用 `TreeMap`（按字段名自然排序）一次性收集 shredded 字段、unshredded 的 serialized 字段、以及 unshredded 的非 serialized 字段；
- 同时计算 `fieldId`、`size`，并累加 `totalDataSize`；
- 最终转换为 `Entry[]` 数组存储。

这样 `writeTo` 路径变为一个简单的数组顺序遍历，去掉了 `SortedMerge` 流式归并和 `metadata.id()` 调用。

## 修改详情

### `core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java` (+71/-61 lines)

**修改目的**：重构 `SerializationState` 以预计算排序后的字段条目，消除每次 `writeTo` 的重复排序和字段 ID 查询开销。

**工作逻辑**：

1. **导入清理**：移除了不再使用的 `ImmutableMap` 和 `SortedMerge` 导入。

2. **`SerializationState` 字段替换**：原本持有 `metadata`、`unshreddedFields`（Map）、`shreddedFields`（Map）三个字段，现在改为只持有 `Entry[] entries`。`metadata` 不再保留是因为字段 ID 已经在构造时解析并存储在 Entry 中。

3. **构造函数重写**：使用 `Maps.newTreeMap()` 收集所有字段。依次处理：
   - 先把 shredded 字段加入 `sorted` map，值为 `Entry.ofShredded(id, value, valueSize)`；
   - 若 unshredded 是 `SerializedObject`，遍历其字段，跳过已被 shredded 或被 removed 的字段，剩余字段使用 `Entry.ofBuffer(id, value, valueSize)`；
   - 若 unshredded 是普通 `VariantObject`，遍历其 fieldNames，同样跳过冲突字段，加入 `Entry.ofShredded(...)`；
   - 在加入每个字段时同步检查 `metadata.id(name) >= 0`，避免无效元数据；
   - 最后 `sorted.values().toArray(new Entry[0])` 得到已排序数组。

4. **`writeTo` 简化**：原本通过 `SortedMerge.of(...)` 构建迭代器并交替查找两个 Map；现在改为基于 `entries` 数组的简单 for 循环。同时新增了一致性校验：

```java
int writtenSize = entry.shredded.writeTo(buffer, dataOffset + nextValueOffset);
Preconditions.checkState(
    writtenSize == entry.size,
    "Wrote %s bytes for field id %s but expected %s (writeTo and sizeInBytes disagree)",
    writtenSize,
    entry.id,
    entry.size);
```

对 buffer 类型则直接 `buffer.put(...)` 并使用预计算的 `entry.size` 推进 `nextValueOffset`。

5. **新增内部 `Entry` 类**：私有的不可变值对象，包含 `id`、`shredded`（可为 null）、`buffer`（可为 null）、`size`，并提供 `ofShredded` 和 `ofBuffer` 两个静态工厂方法。

### `core/src/test/java/org/apache/iceberg/variants/TestShreddedObject.java` (+100/-0 lines)

**修改目的**：补充测试覆盖新逻辑和原有未覆盖场景。

**工作逻辑**：

1. **`testPutAfterRemoveIsSerialized`**：验证字段被 remove 后再 put 新值时，序列化结果包含新值而不是被删除。
2. **`testEmptyObject`**：验证空 `ShreddedObject` 的字段数为 0、`sizeInBytes` 为 3（header + numElements + final offset），并能正确 round-trip。
3. **`testRepeatedWriteToProducesSameBytes`**：连续调用 10 次 `writeTo` 验证每次生成的字节完全一致——这是预计算改造的关键回归测试，确保多次写入不会因为内部状态变化而输出不同结果。
4. **`testWriteToDisagreesWithSizeInBytes`**：通过自定义 `MismatchedSizeVariantValue`（让 `sizeInBytes` 报告值比 `writeTo` 实际写入少 1 字节），验证新引入的 `Preconditions.checkState` 能抛出预期的 `IllegalStateException`。
5. **新增辅助类 `MismatchedSizeVariantValue`**：实现 `VariantValue` 接口，委托给真实 value 但 `sizeInBytes` 返回伪造的小值，用于触发一致性校验失败。

## 总结

这是一个典型的"用空间换时间"的性能优化：将序列化时反复执行的排序与字段 ID 查询前移到构造阶段一次性完成，使 `writeTo` 路径变成线性数组遍历。同时附带了一致性校验，提升了 `ShreddedObject` 在 Variant 序列化路径上的健壮性。新测试覆盖了空对象、repeated write、size 不一致等关键边界场景，保证重构的正确性。
