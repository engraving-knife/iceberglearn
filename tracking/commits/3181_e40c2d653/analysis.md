# 提交 3181：Core: Fix data loss in partial variant shredding (#15087)

## 提交信息

- **序号**：3181 / 4088
- **哈希**：e40c2d653793c3ec27fee5be32de38f57c0dd22e
- **短哈希**：e40c2d653
- **日期**：2026-01-29
- **作者**：yan zhang
- **提交说明**：Core: Fix data loss in partial variant shredding (#15087)
- **PR/Issue**：#15087（关联 issue #15086）

## 总体目的

Iceberg 的 Variant 类型支持"分片"（shredding）优化：将 variant 对象中频繁查询的字段提取为独立列存储，其余字段保留在未分片的 variant 列中，以提升查询性能。"部分分片"（partial shredding）指仅对部分字段执行分片的场景。

本提交修复了一个在部分分片场景下导致数据丢失的严重 bug。问题根源在于 `ShreddedObject.SerializationState` 构造函数中的**变量遮蔽（variable shadowing）**缺陷。

具体而言，`SerializationState` 构造函数的参数原名 `shreddedFields`，与类字段 `this.shreddedFields` 同名。构造函数内执行 `this.shreddedFields = Maps.newHashMap(shreddedFields)` 创建了一份字段副本。但随后在处理未分片字段（`else if (unshredded != null)` 分支）时，代码执行：

```java
for (String name : unshredded.fieldNames()) {
  boolean replaced = shreddedFields.containsKey(name) || removedFields.contains(name);
  if (!replaced) {
    shreddedFields.put(name, unshredded.get(name));
  }
}
```

此处 `shreddedFields` 引用的是**构造函数参数**（即外部传入的原始 map 引用），而非刚创建的字段副本 `this.shreddedFields`。这意味着：来自未分片对象的字段值被错误地写入了原始参数 map，而非被序列化使用的字段副本 `this.shreddedFields`。

后果是连锁的：
1. `totalDataSize` 计算时遍历的是参数 map（含新增字段），得到的尺寸偏大。
2. 但 `writeTo()` 方法使用的是 `this.shreddedFields`（字段副本，**不含**新增字段），实际写入的数据量少于声明的尺寸。
3. `numElements` 计算也基于参数 map 的 size，与实际写入的元素数不匹配。
4. 最终导致序列化结果中部分字段丢失或数据损坏——这就是数据丢失 bug。

该 bug 仅在 `ShreddedObject` 通过 `put()` 方法构建（即 `unshredded` 不是 `SerializedObject` 而是普通 `VariantObject`）且执行部分分片序列化时触发。

## 如何达成设计目的

修复方式极为精简：将构造函数参数从 `shreddedFields` 重命名为 `shredded`，消除变量遮蔽。重命名后，构造函数体内所有对 `shreddedFields` 的引用都正确指向 `this.shreddedFields`（字段副本），未分片字段被正确写入副本中，序列化时数据完整。同时新增两个测试覆盖该场景：单元测试验证 `ShreddedObject` 经 `put()` 修改后的序列化往返，集成测试验证 Parquet 写入读取后 variant 字段完整。

## 修改详情

### `core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java` (+2/-2 lines)

**修改目的**：消除 `SerializationState` 构造函数中的变量遮蔽，修复数据丢失。

**工作逻辑**：
将构造函数参数 `Map<String, VariantValue> shreddedFields` 重命名为 `Map<String, VariantValue> shredded`，对应地将 `this.shreddedFields = Maps.newHashMap(shreddedFields)` 改为 `this.shreddedFields = Maps.newHashMap(shredded)`。

重命名后，构造函数体内（特别是 `else if (unshredded != null)` 分支中）对 `shreddedFields` 的引用不再被参数遮蔽，而是正确解析为实例字段 `this.shreddedFields`。这样，未分片字段通过 `shreddedFields.put(name, unshredded.get(name))` 被写入字段副本，后续 `totalDataSize` 计算、`numElements` 统计与 `writeTo()` 写入均使用同一份完整的数据，保证了序列化一致性。

### `core/src/test/java/org/apache/iceberg/variants/TestShreddedObject.java` (+28/-0 lines)

**修改目的**：添加复现 bug 的单元测试。

**工作逻辑**：
新增测试 `testPartiallyShreddedUnserializedObjectSerializationMinimalBuffer`：
1. 通过新增的辅助方法 `createUnserializedObject(FIELDS)` 创建一个基于 `SerializedMetadata` 但使用 `ShreddedObject`（非 `SerializedObject`）的对象。
2. 对该对象执行 `put("c", ...)` 替换字段 c 的值，`remove("b")` 移除字段 b——模拟用户通过 API 修改部分分片对象。
3. 执行 `roundTripMinimalBuffer` 序列化往返。
4. 断言字段 a（值 34）、字段 c（DATE 类型，对应日期）均正确保留。

同时新增辅助方法 `createUnserializedObject`：使用 `VariantTestUtil.createMetadata` 创建元数据，通过 `SerializedMetadata.from` 包装，再以 `createShreddedObject(metadata, fields)` 构造 `ShreddedObject`。该路径确保 `unshredded` 参数为 `ShreddedObject` 而非 `SerializedObject`，从而触发 bug 所在的 `else if` 分支。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantWriters.java` (+51/-0 lines)

**修改目的**：添加端到端 Parquet 写读测试，验证部分分片下数据完整性。

**工作逻辑**：
新增测试 `testPartialShreddingWithShreddedObject`（注释引用 issue #15086）：
1. 创建包含 `id`、`name`、`city` 三个字段的 variant metadata。
2. 使用 `Variants.object(metadata)` + `obj.put(...)` 构建 3 条记录的 ShreddedObject（每条有不同 id/name/city 值），包装为 `Variant`。
3. 定义部分分片函数 `partialShredding`，仅对 `id` 字段执行分片（`shreddedObject.put("id", Variants.of(1234L))`），通过 `ParquetVariantUtil.toParquetSchema` 转换。
4. 调用 `writeAndRead(partialShredding, records)` 写入 Parquet 并读回。
5. 逐条断言：记录数一致、结构化字段相等，且读回的 variant 对象 `numFields()` 为 3，三个字段值均与原始写入一致。

该测试直接验证了修复后部分分片场景下 `name` 和 `city`（未分片字段）不会丢失。

## 总结

本提交修复了一个由变量遮蔽导致的严重数据丢失 bug：`ShreddedObject.SerializationState` 构造函数参数与字段同名，导致未分片字段被错误写入参数 map 而非字段副本，序列化时这些字段丢失。修复仅需将参数重命名为 `shredded` 即可消除遮蔽，但影响深远——确保了通过 `put()` API 构建的 variant 对象在部分分片序列化（含 Parquet 写入）后数据完整。两个测试分别从单元和集成层面覆盖了该场景。
