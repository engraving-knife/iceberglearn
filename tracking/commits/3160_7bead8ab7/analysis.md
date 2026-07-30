# 提交 3160：Core: Invalidate ShreddedObject serialization cache on remove (#15097)

## 提交信息

- **序号**：3160 / 4088
- **哈希**：7bead8ab72639f1087b337bbc71bf64a59d058ea
- **短哈希**：7bead8ab7
- **日期**：2026-01-26 12:56:42 -0600
- **作者**：Huaxin Gao
- **提交说明**：Core: Invalidate ShreddedObject serialization cache on remove
- **PR/Issue**：#15097

## 总体目的

`ShreddedObject` 是 Iceberg Variants（变体类型）模块中实现"部分/全部 shredding（切碎）"的 `VariantObject` 实现。它内部维护一份惰性计算的序列化缓存 `serializationState`：当调用 `sizeInBytes()` 或 `writeTo(ByteBuffer, int)` 时，若 `serializationState` 为 null，则根据当前的 `shreddedFields`（已切碎字段）与 `removedFields`（已移除字段）快照构造一个 `SerializationState` 并缓存，后续序列化直接复用该缓存以避免重复计算。

问题在于：`put(String, VariantValue)` 方法在更新字段后正确地执行了 `this.serializationState = null` 使缓存失效，但 `remove(String)` 方法只更新了 `shreddedFields` 与 `removedFields`，**遗漏了缓存失效**。这会导致一个数据正确性缺陷：若对象在 `remove` 之前已被序列化过（缓存已预热），随后调用 `remove` 移除某字段，再次序列化时仍会命中陈旧的 `serializationState`——该缓存基于移除前的字段集合构建，会把已被移除的字段重新写入序列化结果。也就是说，`remove` 在读取路径（`get`/`numFields`/`fieldNames`）上生效了，但在序列化输出路径上未生效，造成"读不到却仍被序列化"的不一致，产生错误的 Variant 二进制数据。

本提交修复该缺陷，并在 `TestShreddedObject` 中新增回归测试 `testRemoveInvalidatesSerializationState` 锁定该行为。

## 如何达成设计目的

在 `remove` 方法中补一行 `this.serializationState = null`，与 `put` 方法保持一致的缓存失效策略，确保任何改变字段集合的变更都会使惰性序列化缓存失效，下次序列化时基于最新字段集重建缓存。

## 修改详情

### `core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java` (+1/-0 lines)

**修改目的**：在 `remove` 方法中使序列化缓存失效，避免输出陈旧序列化结果。

**工作逻辑**：`remove(String field)` 原实现为 `shreddedFields.remove(field); removedFields.add(field);`，新增 `this.serializationState = null;`。这与同文件中 `put` 方法末尾的 `this.serializationState = null;` 形成对称：`serializationState` 是 `sizeInBytes()`/`writeTo()` 惰性构建的缓存（由 `metadata`、`unshredded`、`shreddedFields`、`removedFields` 快照生成），任何修改 `shreddedFields` 或 `removedFields` 的写操作都必须置空该缓存，否则后续序列化会复用过期快照。修复后 `remove` 与 `put` 行为一致，保证序列化结果始终反映最新的字段集。

### `core/src/test/java/org/apache/iceberg/variants/TestShreddedObject.java` (+23/-0 lines)

**修改目的**：新增回归测试验证 `remove` 后序列化缓存被正确失效。

**工作逻辑**：`testRemoveInvalidatesSerializationState` 的测试步骤为：先用 `createShreddedObject(FIELDS)` 创建含 3 个字段的对象，调用 `roundTripMinimalBuffer(object, metadata)` 预热序列化缓存，断言结果为 `SerializedObject` 且 `numFields()` 为 3；随后调用 `object.remove("b")`，断言读取路径已生效（`get("b")` 为 null、`numFields()` 为 2）；最后再次 `roundTripMinimalBuffer` 重新序列化，断言结果 `numFields()` 为 2 且 `get("b")` 为 null——即移除的字段不出现在序列化输出中。若无本次修复，最后一次断言会失败（缓存仍含字段 "b"，序列化出 3 个字段）。

## 总结

本提交修复了 `ShreddedObject.remove` 遗漏序列化缓存失效的正确性缺陷：移除字段后若不失效缓存，后续序列化会输出已被移除的字段，导致 Variant 二进制数据与对象实际状态不一致。修复仅需一行 `this.serializationState = null`，使 `remove` 与 `put` 的缓存失效策略对齐，并辅以回归测试锁定该行为，保障了 Variants shredding 序列化路径的数据正确性。
