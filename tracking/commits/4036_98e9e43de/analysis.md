# 提交 4036：Flink: Backport timestamp-micros conversion fix to 1.20 and 2.0 (#17212)

## 提交信息

- **序号**：4036 / 4088
- **哈希**：98e9e43de56a9378a8e6434cc160bc3faeeab9be
- **短哈希**：98e9e43de
- **日期**：2026-07-15 09:23:15 -0700
- **作者**：Sergei Nikolaev
- **提交说明**：Flink: Backport timestamp-micros conversion fix to 1.20 and 2.0 (#17212)
- **PR/Issue**：#17212

## 总体目的

这个提交是将一个已经修复的 timestamp-micros（微秒时间戳）转换 bug 回移（backport）到 Flink 1.20 和 2.0 两个版本分支。原 bug 出现在 `AvroToRowDataConverters` 中处理精度 <= 6（即微秒精度）的时间戳转换逻辑里。

具体问题是：当 Avro 逻辑类型为微秒时间戳（`timestamp-micros`）时，代码在将微秒值拆分为「毫秒部分」和「毫秒内的纳秒部分」时使用了错误的乘数。原始代码为 `Math.floorMod(timeLong, 1000L) * 1_000_000`，其中 `floorMod` 得到的是 0-999 的微秒余数，而 `TimestampData.fromEpochMillis(long milliseconds, int nanosOfMillisecond)` 的第二个参数期望的是「该毫秒内的纳秒数」（范围 0-999_999）。将 0-999 的微秒余数乘以 1_000_000 会得到 0-999_000_000，远超一个毫秒所能容纳的纳秒数（1_000_000 纳秒），导致时间戳值错误放大。

这次回移确保两个仍被广泛使用的 Flink 版本（1.20 和 2.0）也能享受到该修复，避免用户在读取 Avro 微秒时间戳数据时得到错误的时间值。

## 如何达成设计目的

修复方案非常直接：将错误的乘数 `1_000_000` 改为正确的 `1000`。这样微秒余数（0-999）乘以 1000 后得到正确的纳秒值（0-999_000），落在 `fromEpochMillis` 第二个参数的合法范围内。

同时，测试代码 `DataGenerators` 也做了同步增强：引入了 `MICROS_OF_MILLI_20220110 = 456` 这个非零微秒分量，使测试数据真正包含微秒精度的值（而非整毫秒），从而能覆盖并验证修复后的转换逻辑。测试数据在 Avro generic record 中注入 `millis * 1000 + 456` 微秒值，并在期望的 `TimestampData` 中使用 `fromEpochMillis(millis, 456 * 1000)` 来匹配，确保端到端验证微秒部分被正确转换。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/formats/avro/AvroToRowDataConverters.java` (+1/-1 lines)

**修改目的**：修复 Flink 1.20 中 Avro 微秒时间戳到 RowData 的转换 bug。

**工作逻辑**：
原代码（错误）：
```java
return TimestampData.fromEpochMillis(
    Math.floorDiv(timeLong, 1000L), (int) Math.floorMod(timeLong, 1000L) * 1_000_000);
```
修复后：
```java
return TimestampData.fromEpochMillis(
    Math.floorDiv(timeLong, 1000L), (int) Math.floorMod(timeLong, 1000L) * 1000);
```
`Math.floorDiv(timeLong, 1000L)` 得到毫秒部分；`Math.floorMod(timeLong, 1000L)` 得到 0-999 的微秒余数，乘以 1000 转换为 0-999_000 的纳秒值，作为 `fromEpochMillis` 的「毫秒内纳秒」参数。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/DataGenerators.java` (+11/-4 lines)

**修改目的**：增强测试数据，使其包含非零的微秒分量以覆盖修复逻辑。

**工作逻辑**：
- 新增常量 `MICROS_OF_MILLI_20220110 = 456`。
- 期望的 `TimestampData` 从 `fromEpochMillis(millis)` 改为 `fromEpochMillis(millis, 456 * 1000)`，即明确包含 456 微秒对应的纳秒。
- Avro generic record 中注入的微秒值从 `millis * 1000L` 改为 `millis * 1000L + 456`，使数据真正带有微秒精度，验证转换器能正确处理。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/formats/avro/AvroToRowDataConverters.java` (+1/-1 lines)

**修改目的**：对 Flink 2.0 应用与 1.20 完全相同的修复。

**工作逻辑**：同上，将 `1_000_000` 改为 `1000`。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/DataGenerators.java` (+11/-4 lines)

**修改目的**：对 Flink 2.0 应用与 1.20 完全相同的测试增强。

**工作逻辑**：同上，新增 `MICROS_OF_MILLI_20220110` 常量并更新期望值与 Avro 注入数据。

## 总结

这是一个典型的「回移已修复 bug」的提交，核心修复极小（一个数字的改动），但影响重大：它纠正了 Flink 读取 Avro 微秒精度时间戳时纳秒分量被错误放大 1000 倍的严重数据正确性问题。同步增强的测试用例通过引入非零微秒分量，确保该转换路径被真正覆盖，防止回归。同时为 Flink 1.20 和 2.0 两个维护版本提供一致修复，体现了对多版本并行维护的严谨态度。
