# 提交 2465：Revert "Coerce UUID to String in `readable-metrics` (#13087)" (#13754)

## 提交信息

- **序号**：2465 / 4088
- **哈希**：1ea9dbe4047f073151ad1aeaf50b52db9b7ab86a
- **短哈希**：1ea9dbe40
- **日期**：2025-08-06 17:37:47 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Revert "Coerce UUID to String in `readable-metrics` (#13087)" (#13754)
- **PR/Issue**：#13754（回退 #13087）

## 总体目的

该提交回退了此前 PR #13087 引入的变更，该变更在 `readable-metrics` 中将 UUID 类型的上下界值强制转换为 String。

原 PR #13087 的目的是解决 UUID 类型在可读指标（readable-metrics）中的显示问题——UUID 类型的上下界值在 `readable_metrics` 视图中可能无法被某些查询引擎正确处理或显示，因此将其转换为 String 表示。然而，这个转换引入了问题（可能是兼容性问题或其他副作用），导致维护者决定回退此变更。

回退后，上下界值将恢复为使用 `Conversions.fromByteBuffer()` 直接从 ByteBuffer 转换为原始类型值，不再对 UUID 进行特殊的 String 转换处理。

## 如何达成设计目的

通过 `git revert` 操作回退 PR #13087 的所有变更：

1. **恢复 MetricsUtil 中的上下界提取逻辑**：将 lower_bound 和 upper_bound 的值提取器从包含 UUID 到 String 转换的逻辑，恢复为直接使用 `Conversions.fromByteBuffer()` 转换的简单逻辑。

2. **移除 UUID 相关的测试**：删除 `TestMetrics` 中专门针对 UUID 字段指标的测试方法 `testMetricsForUUIDField()`，以及相关的 UUID 导入。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsUtil.java` (+10/-18 lines)

**修改目的**：回退 UUID 到 String 的强制转换逻辑。

**工作逻辑**：

lower_bound 和 upper_bound 的 `ReadableMetricColDefinition` 值提取器从：

```java
// 修改前（#13087 引入的转换逻辑）
(file, field) -> {
    if (file.lowerBounds() == null) {
        return null;
    }
    Object value = Conversions.fromByteBuffer(field.type(), file.lowerBounds().get(field.fieldId()));
    return (value instanceof java.util.UUID) ? value.toString() : value;
}
```

恢复为：

```java
// 修改后（回退后的原始逻辑）
(file, field) ->
    file.lowerBounds() == null
        ? null
        : Conversions.fromByteBuffer(field.type(), file.lowerBounds().get(field.fieldId()))
```

upper_bound 的处理也做了相同的回退。

### `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+0/-52 lines)

**修改目的**：移除 UUID 字段指标的测试用例。

**工作逻辑**：
- 移除 `java.util.UUID`、`java.util.stream.Collectors`、`java.util.stream.Stream` 的导入
- 移除 `testMetricsForUUIDField()` 测试方法，该方法此前验证了 UUID 类型字段的上下界在 readable_metrics 中被正确转换为 String

## 总结

该提交回退了 PR #13087 引入的 UUID 到 String 强制转换逻辑。回退后，`readable-metrics` 中的上下界值将恢复为原始类型（UUID 类型保持为 UUID 对象，不做 String 转换）。这表明 UUID 到 String 的转换可能引入了不期望的副作用或兼容性问题。该修改涉及 Core 模块的生产代码和测试代码，恢复了原始的类型处理行为。
