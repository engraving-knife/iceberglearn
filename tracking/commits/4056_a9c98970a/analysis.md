# 提交 4056：Core: Fix ShreddedObject.put not clearing prior remove marker (#17066)

## 提交信息

- **序号**：4056 / 4088
- **哈希**：a9c98970a9267c3d5843020a8be117e7d2a3a4d1
- **短哈希**：a9c98970a
- **日期**：2026-07-16 16:14:24 -0700
- **作者**：Eunbin Son
- **提交说明**：Core: Fix ShreddedObject.put not clearing prior remove marker (#17066)
- **PR/Issue**：#17066

## 总体目的

这个提交修复了 `ShreddedObject`（变体/Variant 的脱壳对象实现）中 `put(field, value)` 方法的一个状态不一致 bug。`ShreddedObject` 是 Iceberg variant 类型系统中用于表示被「脱壳」（shredded，即把部分字段从 variant 二进制中提取到独立列存储）的对象的类，内部维护两个集合：`shreddedFields`（已脱壳的字段值）和 `removedFields`（被标记删除的字段）。

问题在于：当对一个字段先调用 `remove(field)` 再调用 `put(field, newValue)` 时，`put` 会把新值加入 `shreddedFields`，但没有从 `removedFields` 中移除该字段的删除标记。由于查询 API（`get()`、`fieldNames()`、`numFields()`）在遇到 `removedFields` 标记时会短路返回「字段不存在」，而 `writeTo()` 序列化时却会写入 `shreddedFields` 中的新值，导致查询结果与序列化字节不一致——读 API 认为字段不存在，但序列化后字段又出现了。

这种不一致会导致 variant 数据在被读写往返（round-trip）后行为异常，是一个数据正确性 bug。提交信息标注 `Generated-by: Claude Code`，表明该修复由 AI 辅助工具生成。

## 如何达成设计目的

修复方式非常直接且符合对称性原则：在 `put(field, value)` 方法中，在将值放入 `shreddedFields` 之前，先调用 `removedFields.remove(field)` 清除可能存在的删除标记。这与 `remove(field)` 方法中清除 `shreddedFields` 条目的做法形成对称——每个操作都清除对方集合中的对应条目，确保两个集合不会同时持有同一字段的矛盾状态。

同时新增了回归测试 `testPutAfterRemoveClearsRemoveMarker`，覆盖「remove → put → 查询 + 序列化往返」的完整序列，验证 `get()`、`numFields()`、`fieldNames()` 和 `writeTo()` round-trip 后的结果一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java` (+1/-0 lines)

**修改目的**：在 `put` 方法中清除先前的删除标记。

**工作逻辑**：
```java
// allow setting fields that are contained in unshredded. this avoids read-time failures and
// simplifies replacing field values.
removedFields.remove(field);  // 新增：清除删除标记
shreddedFields.put(field, value);
this.serializationState = null;
```
在 `put` 方法中，将字段值加入 `shreddedFields` 之前，先从 `removedFields` 集合中移除该字段。这样 `remove()` 后再 `put()` 同一字段时，删除标记被清除，查询 API 不再短路，能正确返回新值。`this.serializationState = null` 使缓存失效，确保后续序列化反映最新状态。

### `core/src/test/java/org/apache/iceberg/variants/TestShreddedObject.java` (+22/-0 lines)

**修改目的**：添加回归测试覆盖 remove-then-put 序列。

**工作逻辑**：
```java
@Test
public void testPutAfterRemoveClearsRemoveMarker() {
  ShreddedObject object = createShreddedObject(FIELDS);
  VariantMetadata metadata = object.metadata();

  object.remove("b");
  assertThat(object.get("b")).as("removed field should be hidden from reads").isNull();

  object.put("b", Variants.of("rewritten"));
  assertThat(object.get("b")).as("put after remove should restore the field").isNotNull();
  assertThat(object.get("b").asPrimitive().get()).isEqualTo("rewritten");
  assertThat(object.numFields()).as("numFields should include the re-added field").isEqualTo(3);
  assertThat(object.fieldNames()).contains("b");

  // the query API and the serialized bytes must agree on which fields are present
  VariantValue serialized = roundTripMinimalBuffer(object, metadata);
  assertThat(serialized).isInstanceOf(SerializedObject.class);
  SerializedObject actual = (SerializedObject) serialized;
  assertThat(actual.numFields()).isEqualTo(3);
  VariantTestUtil.assertVariantString(actual.get("b"), "rewritten");
}
```
测试验证：remove 后字段对读 API 不可见；put 后字段恢复且值为新值；`numFields` 和 `fieldNames` 包含恢复的字段；序列化往返后查询 API 与序列化字节一致（3 个字段，"b" 值为 "rewritten"）。

## 总结

这是一个数据正确性 bug 修复，解决了 `ShreddedObject` 中 remove-then-put 操作导致删除标记残留、使查询 API 与序列化结果不一致的问题。修复仅一行（`removedFields.remove(field)`），通过清除删除标记与 `remove()` 方法清除 shreddedFields 条目形成对称，确保两个状态集合不矛盾。回归测试覆盖了完整的读写往返场景。该修复由 Claude Code 辅助生成，体现了 AI 工具在发现和修复此类状态不一致 bug 中的价值。
