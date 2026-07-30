# 提交 3931：ORC: Fix lower/upper bounds for timestamp_ns columns in OrcMetrics (#16922)

## 提交信息

- **序号**：3931 / 4088
- **哈希**：ef07755dd32b59e62346e8599753f75076e57151
- **短哈希**：ef07755dd
- **日期**：2026-06-23 17:28:38 +0200
- **作者**：Eunbin Son
- **提交说明**：ORC: Fix lower/upper bounds for timestamp_ns columns in OrcMetrics (#16922)
- **PR/Issue**：#16922

## 总体目的

这次提交修复了 ORC 文件格式中 `timestamp_ns`（纳秒精度时间戳）列的 lower/upper bounds 计算错误。在 Iceberg 中，`TIMESTAMP` 类型列的 bounds 以微秒（micros）为单位存储，而 `TIMESTAMP_NANO` 类型列的 bounds 应以纳秒（nanos）为单位存储（参见 `Conversions` 类中对两种类型的处理）。

然而，`OrcMetrics` 在计算列的 min/max bounds 时，对所有时间戳列统一调用 `DateTimeUtil.microsFromInstant(v.toInstant())`，将 ORC 列统计中的时间戳值转换为微秒。这对 `TIMESTAMP` 列是正确的，但对 `TIMESTAMP_NANO` 列则会导致 bounds 值比预期小约 1000 倍（因为纳秒值被截断为微秒），从而严重影响基于 bounds 的文件裁剪（file pruning）和数据跳过（data skipping）效率——查询引擎可能错误地跳过包含目标数据的文件，或无法跳过明显不相关的文件。

修复通过引入 `timestampBound` 方法，根据列的 Iceberg 类型决定使用纳秒还是微秒来计算 bounds。

## 如何达成设计目的

抽取一个 `timestampBound(Type type, Instant instant)` 私有静态方法，根据 `type.typeId()` 判断：如果是 `TIMESTAMP_NANO` 则使用 `ChronoUnit.NANOS.between(EPOCH, instant.atOffset(UTC))` 计算纳秒值，否则使用原有的 `DateTimeUtil.microsFromInstant(instant)` 计算微秒值。将 min 和 max 的计算都替换为调用此方法，保持代码 DRY。

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/OrcMetrics.java` (+20/-6 lines)

**修改目的**：修复 timestamp_ns 列的 bounds 单位错误。

**工作逻辑**：
1. 新增 import：`java.time.Instant`、`java.time.ZoneOffset`、`java.time.temporal.ChronoUnit`。
2. 将 min 计算中的 `.map(v -> DateTimeUtil.microsFromInstant(v.toInstant()))` 替换为 `.map(v -> timestampBound(type, v.toInstant()))`。
3. 将 max 计算中同样的替换。
4. 新增 `timestampBound` 私有静态方法：
```java
private static long timestampBound(Type type, Instant instant) {
  if (type.typeId() == Type.TypeID.TIMESTAMP_NANO) {
    return ChronoUnit.NANOS.between(DateTimeUtil.EPOCH, instant.atOffset(ZoneOffset.UTC));
  }
  return DateTimeUtil.microsFromInstant(instant);
}
```

### `data/src/test/java/org/apache/iceberg/orc/TestOrcMetrics.java` (+35/-0 lines)

**修改目的**：验证 timestamp_ns 列的 bounds 保持纳秒精度。

**工作逻辑**：
新增 `timestampNanoBoundsKeepNanoPrecision` 测试方法，构造一个 `TIMESTAMP_NANO` 类型的 schema，写入两个具有亚微秒纳秒值的时间戳（1500ns 和 123456789ns），然后读取 metrics 并断言：
- lower bounds 等于预期的纳秒值（1500L 和 123456789L）。
- 特别添加 `assertThat(actualLower).isEqualTo(1500L)` 作为回归守护，确保 bounds 不是被截断为微秒后的值（1 或 2）。

## 总结

这次提交修复了 ORC metrics 中 timestamp_ns 列 bounds 的单位错误——原本纳秒值被错误地截断为微秒，导致 bounds 比预期小约 1000 倍。修复通过根据列类型选择纳秒或微秒单位，恢复了正确的 bounds 计算，这对基于时间戳范围的数据跳过和文件裁剪至关重要。测试中特别加入了亚微秒精度的回归守护断言。
