# 提交 2443：Coerce UUID to String in `readable-metrics` (#13087)

## 提交信息

- **序号**：2443 / 4088
- **哈希**：7fefc46e95040e96f199d1c8e4d025047ac1bbc6
- **短哈希**：7fefc46e9
- **日期**：2025-08-04 12:56:50 +0200
- **作者**：Chun Wei Lu
- **提交说明**：Coerce UUID to String in `readable-metrics` (#13087)
- **PR/Issue**：#13087

## 总体目的

本提交修复了 Iceberg `readable_metrics` 中 UUID 类型列的 `lower_bound` 和 `upper_bound` 在 Spark 中引发 `ClassCastException` 的问题。

`readable_metrics` 是 Iceberg 提供的一个虚拟表结构，用于暴露每个数据文件的列级统计信息（如上下界值、null 计数等），便于引擎层做查询优化。在构建 readable metrics 的 lower/upper bound 值时，`MetricsUtil` 通过 `Conversions.fromByteBuffer(field.type(), ...)` 将字节数据转换为对应类型的 Java 对象。

对于 UUID 类型，`Conversions.fromByteBuffer` 返回的是 `java.util.UUID` 对象。然而 Spark 在处理 readable metrics 时，期望 lower/upper bound 的值为 String 类型（UUID 在 Spark 中通常以字符串表示），直接使用 UUID 对象会导致 `ClassCastException`。修复方案是在返回值前检查：如果值是 `java.util.UUID` 实例，则调用 `toString()` 转换为字符串。

## 如何达成设计目的

在 `MetricsUtil` 构建 lower_bound 和 upper_bound 的 `ReadableMetricColDefinition` 时，将原本直接返回 `Conversions.fromByteBuffer` 结果的 lambda 改为先获取转换值，再判断是否为 UUID 实例，若是则调用 `toString()`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetricsUtil.java` (+28/-10 lines)

**修改目的**：将 UUID 类型的 lower/upper bound 值强制转换为 String。

**工作逻辑**：
- 原来的 lower_bound lambda：`file.lowerBounds() == null ? null : Conversions.fromByteBuffer(field.type(), file.lowerBounds().get(field.fieldId()))`
- 新的 lower_bound lambda：先判 null 返回 null；否则调用 `Conversions.fromByteBuffer` 获取 value；若 `value instanceof java.util.UUID` 则返回 `value.toString()`，否则原样返回 value。
- upper_bound 的 lambda 做相同修改。
- 两处修改逻辑完全对称，确保 lower 和 upper bound 的 UUID 值都被转为字符串。

### `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+55/-0 lines)

**修改目的**：新增测试验证 UUID 字段的 readable metrics 正确性。

**工作逻辑**：
- 新增 `testMetricsForUUIDField` 测试，跳过 ORC 格式（ORC writer 不写 UUID bounds）。
- 创建仅含 UUID 列的 schema，写入 3 条含不同 UUID 的记录。
- 获取 metrics 并构建 DataFile，通过 `MetricsUtil.readableMetricsStruct` 获取 readable metrics。
- 从结构中读取 lower_bound（索引 4）和 upper_bound（索引 5），断言类型为 `String.class`。
- 将 3 个 UUID 转为字符串并排序，断言 lower_bound 等于最小值、upper_bound 等于最大值。
- 这验证了 UUID 值被正确转为字符串且边界值比较正确。

## 总结

本提交修复了 readable metrics 中 UUID 类型列的 lower/upper bound 在 Spark 中触发 ClassCastException 的问题。根因是 `Conversions.fromByteBuffer` 对 UUID 类型返回 `java.util.UUID` 对象，而 Spark 期望 String。修复方式是在 `MetricsUtil` 中检测 UUID 实例并调用 `toString()` 转换。新增的测试验证了 UUID 字段的 readable metrics 边界值正确性，且确认返回类型为 String。
