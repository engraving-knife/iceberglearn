# 提交 3459：Flink: Fix HashKeyGenerator SelectorKey cache ignoring writeParallelism and distributionMode (#15740)

## 提交信息

- **序号**：3459 / 4088
- **哈希**：027f088d99d24fbb4a3eeb14e5da44d9f9b9efc0
- **短哈希**：027f088d99
- **日期**：2026-03-25 16:07:29 +0100
- **作者**：Hayoung Lee
- **提交说明**：Flink: Fix HashKeyGenerator SelectorKey cache ignoring writeParallelism and distributionMode (#15740)
- **PR/Issue**：#15740

## 总体目的

修复 Flink HashKeyGenerator 中 SelectorKey 缓存键忽略 `writeParallelism` 和 `distributionMode` 的 bug。`SelectorKey` 是 `KeySelector` 缓存的键类，用于复用已创建的 KeySelector。但 `SelectorKey` 的 `equals()` 和 `hashCode()` 方法没有包含 `distributionMode` 和 `writeParallelism` 字段，导致当这两个参数变化时，缓存会错误地返回旧的 KeySelector，而不是创建新的。

这会导致数据分布不正确，因为不同的 `writeParallelism` 或 `distributionMode` 应该产生不同的哈希分区策略。

## 如何达成设计目的

- 在 `SelectorKey` 类中添加 `distributionMode` 和 `writeParallelism` 字段
- 更新构造函数以接收这两个新参数
- 更新 `equals()`、`hashCode()` 和 `toString()` 方法以包含新字段
- 在创建 `SelectorKey` 时从 `DynamicRecord` 中提取 `distributionMode` 和 `writeParallelism`

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/HashKeyGenerator.java` (+25/-4 lines)

**修改目的**：在 SelectorKey 缓存键中包含 distributionMode 和 writeParallelism。

**工作逻辑**：

1. **创建 SelectorKey 时传入新参数**：
   ```java
   new SelectorKey(
       ...,
       dynamicRecord.equalityFields(),
       MoreObjects.firstNonNull(dynamicRecord.distributionMode(), DistributionMode.NONE),
       Math.min(dynamicRecord.writeParallelism(), maxWriteParallelism));
   ```
   - `distributionMode` 为 null 时默认为 `DistributionMode.NONE`
   - `writeParallelism` 取与 `maxWriteParallelism` 的最小值

2. **SelectorKey 类新增字段**：
   ```java
   private final DistributionMode distributionMode;
   private final int writeParallelism;
   ```

3. **更新 equals()**：添加 `distributionMode == that.distributionMode` 和 `writeParallelism == that.writeParallelism` 比较

4. **更新 hashCode()**：在 `Objects.hash()` 中添加 `distributionMode` 和 `writeParallelism`

5. **更新 toString()**：添加 `distributionMode` 和 `writeParallelism` 到 MoreObjects toString

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestHashKeyGenerator.java` (+68/-0 lines)

**修改目的**：添加缓存失效测试。

**工作逻辑**：

1. **`testCacheMissOnWriteParallelismChange()`**：
   - 创建两个 DynamicRecord，只有 `writeParallelism` 不同（2 vs 4）
   - 验证第一次 generateKey 后缓存大小为 1
   - 验证第二次 generateKey 后缓存大小为 2（缓存未命中，创建了新 KeySelector）

2. **`testCacheMissOnDistributionModeChange()`**：
   - 创建两个 DynamicRecord，只有 `distributionMode` 不同（NONE vs HASH）
   - 验证缓存正确区分不同 distributionMode 的记录

## 总结

该提交修复了 Flink HashKeyGenerator 中 SelectorKey 缓存键忽略 `writeParallelism` 和 `distributionMode` 的 bug。当这两个参数变化时，缓存错误地返回旧的 KeySelector，导致数据分布不正确。修复方案是在 SelectorKey 中添加这两个字段并更新 equals/hashCode 方法。
