# 提交 4034：Flink: Fix timestamp-micros conversion in AvroToRowDataConverters (#17194)

## 提交信息

- **序号**：4034 / 4088
- **哈希**：4eb37b01369d2e817a31103675fbe9e7866dcfa8
- **短哈希**：4eb37b013
- **日期**：2026-07-15 07:37:12 +0200
- **作者**：Sergei Nikolaev
- **提交说明**：Flink: Fix timestamp-micros conversion in AvroToRowDataConverters (#17194)
- **PR/Issue**：#17194

## 总体目的

本提交修复 Flink Avro 到 RowData 转换中 timestamp-micros（微秒精度时间戳）转换的算术错误，导致亚毫秒部分（微秒的低位）被错误放大的 bug。

原代码在 `AvroToRowDataConverters` 中处理 precision <= 6（微秒精度）时：
```java
TimestampData.fromEpochMillis(
    Math.floorDiv(timeLong, 1000L), (int) Math.floorMod(timeLong, 1000L) * 1_000_000);
```
问题在于运算符优先级：`(int) Math.floorMod(timeLong, 1000L) * 1_000_000` 实际是 `((int) Math.floorMod(...)) * 1_000_000`，把微秒余数（0-999）乘以 1,000,000 转成纳秒。但正确的换算应该是微秒余数（0-999 微秒）乘以 1000 转成纳秒（1 微秒 = 1000 纳秒），而非乘以 1,000,000。

原代码把微秒余数当成了"毫秒内的微秒"再乘 1,000,000，相当于把微秒值放大了 1000 倍，导致时间戳的纳秒部分错误（例如 456 微秒会被算成 456,000,000 纳秒 = 456 毫秒，而非正确的 456,000 纳秒 = 456 微秒）。

本提交将 `1_000_000` 改为 `1000`，正确地将微秒余数转为纳秒。同时更新测试数据生成器，注入非零的亚毫秒微秒值以覆盖此场景。

## 如何达成设计目的

1. 修正 `AvroToRowDataConverters` 中 `(int) Math.floorMod(timeLong, 1000L) * 1_000_000` 为 `* 1000`。
2. 在 `DataGenerators` 测试中新增 `MICROS_OF_MILLI_20220110 = 456` 常量，构造带亚毫秒微秒的时间戳期望值和 Avro 输入值，使测试能捕获此 bug。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/formats/avro/AvroToRowDataConverters.java` (+1/-1 lines)

**修改目的**：修复微秒到纳秒的换算系数。

**工作逻辑**：
```java
// 修改前
return TimestampData.fromEpochMillis(
    Math.floorDiv(timeLong, 1000L), (int) Math.floorMod(timeLong, 1000L) * 1_000_000);
// 修改后
return TimestampData.fromEpochMillis(
    Math.floorDiv(timeLong, 1000L), (int) Math.floorMod(timeLong, 1000L) * 1000);
```
`TimestampData.fromEpochMillis(millis, nanosOfMillis)` 第二个参数是毫秒内的纳秒部分。`floorMod(timeLong, 1000)` 得到的是微秒余数（0-999 微秒），乘以 1000 转为纳秒（0-999000 纳秒），正确。原 `* 1_000_000` 会超出纳秒范围（最大 999,000,000 纳秒 = 999 毫秒，与毫秒部分重复）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/DataGenerators.java` (+9/-4 lines)

**修改目的**：测试数据注入亚毫秒微秒值以覆盖该场景。

**工作逻辑**：
- 新增 `MICROS_OF_MILLI_20220110 = 456` 常量。
- 期望的 `TimestampData` 从 `fromEpochMillis(millis)` 改为 `fromEpochMillis(millis, MICROS_OF_MILLI_20220110 * 1000)`，即期望纳秒部分为 456,000（456 微秒）。
- Avro 输入的微秒值从 `millis * 1000L` 改为 `millis * 1000L + MICROS_OF_MILLI_20220110`，注入 456 微秒的亚毫秒部分。
- 注释说明现在 AvroToRowDataConverters 正确支持微秒，需注入正确的微秒尺度值。

## 总结

本提交修复了 Flink Avro 转换器中 timestamp-micros 微秒到纳秒换算系数错误的 bug（`* 1_000_000` 应为 `* 1000`），该错误导致亚毫秒部分被放大 1000 倍。修复后微秒精度的 Avro 时间戳能正确转换为 Flink `TimestampData`。配套更新测试数据生成器注入非零亚毫秒微秒值，防止回归。这是一个影响数据正确性的重要修复。
