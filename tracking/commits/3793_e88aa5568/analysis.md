# 提交 3793：Flink: Honor schema identifier fields in dynamic-sink record routing (#16243)

## 提交信息

- **序号**：3793 / 4088
- **哈希**：e88aa55687a4767a5fb3152248d3514831da2723
- **短哈希**：e88aa5568
- **日期**：2026-05-28 07:07:57 +0200
- **作者**：Jordan Epstein
- **提交说明**：Flink: Honor schema identifier fields in dynamic-sink record routing (#16243)
- **PR/Issue**：#16243

## 总体目的

这个提交修复了 Flink 动态 Sink（dynamic sink）在记录路由中没有考虑 schema 标识符字段（identifier fields）的问题。

**背景**：Iceberg 表的 schema 可以定义标识符字段（identifier fields），这些字段唯一标识表中的行。在 upsert/equality-delete 场景中，标识符字段用于确定哪些行应该被替换或删除。

**问题**：在 Flink 动态 Sink 中，当用户没有显式设置 `equalityFields` 时，记录路由（record routing）逻辑只检查 `distributionMode` 是否为 null 来决定是否使用 forward 模式（不 shuffle）。但这忽略了 schema 的标识符字段：
1. 如果表 schema 有标识符字段但用户没设置 equalityFields，这些标识符字段应该被用作 equality 字段。
2. 有 equality 字段的记录不能使用 forward 模式，因为共享相同 equality key 的记录必须路由到同一个 writer 子任务以保持 equality-delete 语义。
3. 原来的代码在 forward 模式下不 shuffle，导致有标识符字段的记录可能分散到不同 writer，破坏 equality-delete 正确性。

**修复**：
1. 新增 `DynamicSinkUtil.resolveEqualityFieldNames()` 方法，在用户未设置 equalityFields 时回退到 schema 的标识符字段。
2. 修改 `DynamicRecordProcessor.isForwardEligible()` 方法，当解析出的 equality 字段集非空时不允许 forward 模式。
3. 修改 `HashKeyGenerator` 使用解析后的有效 equality 字段和 distribution mode。

## 如何达成设计目的

1. 在 `DynamicSinkUtil` 中新增 `resolveEqualityFieldNames` 方法，统一处理 equality 字段解析逻辑（用户设置的优先，否则回退到 schema 标识符字段）。
2. 在 `DynamicRecordProcessor` 中新增 `isForwardEligible` 方法，检查 distribution mode 为 null 且解析后的 equality 字段集为空才允许 forward。
3. 在 `HashKeyGenerator` 中提前解析有效的 schema、equality 字段和 distribution mode，确保缓存键和 key selector 使用一致的解析结果。

## 修改详情

### `docs/docs/flink-writes.md` (+2/-2 lines)

**修改目的**：更新文档说明标识符字段的回退行为。

**工作逻辑**：
- 更新 `DistributionMode` 说明：当为 null 时不 shuffle，除非记录解析出非空 equality 字段集，此时回退到 hash 分布。
- 更新 `EqualityFields` 说明：未设置时回退到 schema 的标识符字段用于分布和 equality-delete 推断。标识符字段不自动启用 upsert 模式。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (+12/-1 lines)

**修改目的**：修复 forward 模式判断逻辑，考虑 equality 字段。

**工作逻辑**：
将 `boolean isForward = data.distributionMode() == null;` 改为 `boolean isForward = isForwardEligible(data);`。

新增 `isForwardEligible` 方法：
```java
static boolean isForwardEligible(DynamicRecord data) {
  return data.distributionMode() == null
      && DynamicSinkUtil.resolveEqualityFieldNames(data.equalityFields(), data.schema())
          .isEmpty();
}
```
当 distribution mode 为 null **且**解析后的 equality 字段集为空时才允许 forward。有 equality 字段时必须 hash 分布以保持正确性。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicSinkUtil.java` (+15/-0 lines)

**修改目的**：新增 equality 字段名解析方法。

**工作逻辑**：
```java
static Set<String> resolveEqualityFieldNames(
    @Nullable Set<String> equalityFields, Schema schema) {
  if (equalityFields != null && !equalityFields.isEmpty()) {
    return equalityFields;
  }
  return schema.identifierFieldNames();
}
```
用户设置的 equalityFields 优先，否则回退到 schema 的标识符字段名。这与已有的 `getEqualityFieldIds` 方法逻辑对齐，确保分布和写入侧的 equality 字段推断一致。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+12/-5 lines)

**修改目的**：使用解析后的有效 equality 字段和 distribution mode。

**工作逻辑**：
1. 提前解析有效 schema：`Schema effectiveSchema = MoreObjects.firstNonNull(tableSchema, dynamicRecord.schema());`
2. 提前解析有效 equality 字段：`DynamicSinkUtil.resolveEqualityFieldNames(dynamicRecord.equalityFields(), effectiveSchema);`
3. 提前解析有效 distribution mode：`MoreObjects.firstNonNull(dynamicRecord.distributionMode(), DistributionMode.NONE);`
4. 使用这些有效值构建缓存键和 key selector，确保一致性。

### `flink/v2.1/flink/src/test/java/.../TestDynamicRecordProcessor.java` (+101/-0 lines)
### `flink/v2.1/flink/src/test/java/.../TestHashKeyGenerator.java` (+97/-0 lines)

**修改目的**：测试标识符字段在记录路由中的行为。

**工作逻辑**：新增测试验证：
- 有标识符字段时不允许 forward 模式。
- equality 字段解析的用户设置优先级。
- HashKeyGenerator 使用有效 equality 字段的一致性。

## 总结

这个提交修复了 Flink 动态 Sink 中记录路由忽略 schema 标识符字段的问题。当表有标识符字段但用户未显式设置 equalityFields 时，现在会自动回退到使用标识符字段作为 equality 字段，并在这种情况下禁止 forward 模式以确保 equality-delete 语义正确。这是数据正确性修复，对使用动态 Sink 和 upsert/equality-delete 的场景很重要。
