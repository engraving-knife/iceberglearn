# 提交 3196：Flink: Dynamic Sink: Fix partition field check in non-immediate update path (#15190)

## 提交信息

- **序号**：3196 / 4088
- **哈希**：84fa33f6e1dde6c32307066cd05d6afd061f5a4f
- **短哈希**：84fa33f6e
- **日期**：2026-02-02
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Sink: Fix partition field check in non-immediate update path (#15190)
- **PR/Issue**：#15190

## 总体目的

该提交修复了 Flink 动态 Sink 中 `HashKeyGenerator` 在"非立即更新路径"（non-immediate update path）下做分区字段校验时的一个错误。在 Iceberg 的 `hash` 分布模式下，当用户设置了 equality fields（等值字段，用于 upsert 语义），代码需要校验"分区字段必须包含在 equality fields 中"，否则同一逻辑主键的数据可能被分发到不同的 subtask，导致写出错乱或违反分桶一致性。

问题在于原有校验逻辑使用 `equalityFields.contains(partitionField.name())`。`partitionField.name()` 返回的是分区字段自身的名字（可能是一个 transform 后的名字，例如 `id_bucket`、`days(ts` 等），而不是它在 schema 中的源列名（source column name）。equality fields 列表里存的则是用户给出的源列名（如 `id`、`ts`）。两者命名空间不一致，导致即便源列已经在 equality fields 中，校验仍会误判为"未包含"，从而在合法场景下抛出 `IllegalStateException`，阻断正常写入。

例如分区规格 `bucket("id", 4)` 产生的分区字段名通常是 `id_bucket`，但 equality fields 中是 `id`，旧代码因此误报错误。该 bug 在"non-immediate update path"（即非立刻提交、走 hash 分发的路径）被触发，影响 Flink v2.1 动态 sink 写入 Iceberg 表。

## 如何达成设计目的

修复思路是把校验从"分区字段名是否在 equality fields 中"改为"分区字段对应的源列名是否在 equality fields 中"。具体通过 `schema.findField(partitionField.sourceId())` 先取得该分区字段所引用的源 `Types.NestedField`，再判断 `sourceField.name()` 是否落在 equalityFields 集合内。同时新增对 `sourceField != null` 的空值保护，避免源字段缺失时抛 NPE。测试侧新增两个用例覆盖正例与反例。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+4/-1 lines)

**修改目的**：修正 hash 模式下 equality fields 与分区字段名称比对的对象。

**工作逻辑**：
在遍历 `spec.fields()` 的循环中，原代码直接比对 `equalityFields.contains(partitionField.name())`。新代码先取源字段：

```java
Types.NestedField sourceField = schema.findField(partitionField.sourceId());
Preconditions.checkState(
    sourceField != null && equalityFields.contains(sourceField.name()),
    ...);
```

`partitionField.sourceId()` 指向 schema 中的源列 ID，`schema.findField(...)` 返回对应的 `Types.NestedField`，其 `name()` 才是用户在 equality fields 里书写的列名。同时引入 `import org.apache.iceberg.types.Types;` 以使用 `Types.NestedField`。`sourceField != null` 防御源 ID 在 schema 中已被移除的极端情况，使错误信息更可控。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+42/-0 lines)

**修改目的**：覆盖修复后的正例与反例，防止回归。

**工作逻辑**：
新增两个测试：

1. `testHashModeWithPartitionFieldAndEqualityField`：使用 `bucket("id", 4)` 分区规格，equality columns 为 `{"id","data"}`，构造 `id` 相同但 `data` 不同的两行，断言两行产生相同的 writeKey。这验证了源列 `id` 在 equality fields 中时校验通过，且 hash 分发按源列值聚合。

2. `testHashModeWithPartitionFieldNotInEqualityFieldsFails`：equality columns 仅 `{"data"}`（不含 `id`），断言调用 `getWriteKey` 抛出 `IllegalStateException`，且消息包含 `"partition field"`、分区字段 toString 与 `"should be included in equality fields"`。这验证当源列确实不在 equality fields 时仍能正确报错。

## 总结

该修复解决了 Flink 动态 Sink 在 hash 分布 + equality fields 模式下，因比对"分区字段名"而非"源列名"导致的误报问题，使带 transform 的分区规格（如 bucket）能正常工作。改动小而精准，并补充了正反两个方向的单元测试，有效防止回归。
