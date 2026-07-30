# 提交 3314：API, Core: Align offsets of field stats with Design doc / Spec (#15432)

## 提交信息

- **序号**：3314 / 4088
- **哈希**：17891bc4e300c49b3678117be3189864aaf3866b
- **短哈希**：17891bc4e
- **日期**：2026-02-25
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Align offsets of field stats with Design doc / Spec (#15432)
- **PR/Issue**：#15432

## 总体目的

Iceberg 的内容统计（Content Stats）功能为每个字段维护一组统计信息（值计数、null 计数、NaN 计数、平均大小、最大大小、下界、上界、精确边界标志），这些统计信息在 Avro 序列化结构中以字段 ID 偏移的方式布局。`FieldStatistic` 枚举为此定义了每个统计类型的偏移量（offset）。

此前 `FieldStatistic` 的 offset 从 0 开始（VALUE_COUNT=0, NULL_VALUE_COUNT=1, ... EXACT_BOUNDS=7），这与 Iceberg 设计文档/规范中定义的偏移量不一致——规范中 base 字段的统计从 base field ID + 1 开始（即偏移从 1 开始）。这种不一致导致代码中出现了混乱的偏移补偿：例如 `StatsUtil` 中调用 `fieldStatsFor(field.type(), fieldId + 1)` 时需要手动加 1 来补偿，而 `BaseFieldStats.get(pos, ...)` 又直接使用 offset 作为位置索引。此外 `size()` 方法返回 7 而非实际的 8 个统计项（漏算了 EXACT_BOUNDS），存在 off-by-one 错误。

本提交将 offset 从 1 开始对齐设计文档，引入 `position()`（0-based 序号）与 `offset()`（从 base field ID 的偏移）两个概念的区分，修复 `size()` 返回值，并统一所有调用点使用正确的语义。

## 如何达成设计目的

将 `FieldStatistic` 枚举的 offset 值全部加 1（从 1-8 改为对应的 1-8），新增 `position()` 方法返回 `offset - 1`（即 0-based 序号），将 `fromOffset` 改为 `fromPosition`。在 `fieldStatsFor` 方法中参数从 `fieldId` 改名为 `baseFieldId` 以明确语义，offset 直接加到 baseFieldId 上。`StatsUtil` 中不再需要 `fieldId + 1` 补偿。`BaseFieldStats` 中 `get` 使用 `fromPosition`、`size` 修正为 8。所有测试同步更新。

## 修改详情

### `api/src/main/java/org/apache/iceberg/stats/FieldStatistic.java` (+57/-37 lines)

**修改目的**：对齐 offset 与设计文档，区分 offset 与 position 概念。

**工作逻辑**：
- 枚举值 offset 从 `0-7` 改为 `1-8`：`VALUE_COUNT(1, "value_count")` ... `EXACT_BOUNDS(8, "exact_bounds")`。
- 新增 `position()` 方法返回 `offset - 1`，即 0-based 序号位置（VALUE_COUNT.position()=0, ..., EXACT_BOUNDS.position()=7）。
- `fromOffset(int offset)` 重命名为 `fromPosition(int position)`，内部使用 switch 表达式（`return switch (position) { case 0 -> VALUE_COUNT; ... }`），异常消息从 "Invalid statistic offset" 改为 "Invalid statistic position"。
- `fieldStatsFor(Type type, int fieldId)` 参数改名为 `baseFieldId`，内部 `baseFieldId + VALUE_COUNT.offset()` 等。由于 offset 现在从 1 开始，`baseFieldId + 1` 即为 VALUE_COUNT 的字段 ID，与规范一致。
- 为 `offset()`、`position()`、`fieldName()` 添加 Javadoc。

### `api/src/main/java/org/apache/iceberg/stats/StatsUtil.java` (+1/-1 lines)

**修改目的**：移除手动 offset 补偿。

**工作逻辑**：
此前代码 `FieldStatistic.fieldStatsFor(field.type(), fieldId + 1)` 中的 `+ 1` 是因为 offset 从 0 开始、需要跳过 base field 本身。现在 offset 从 1 开始，`fieldStatsFor` 内部 `baseFieldId + offset()` 已自动跳过，因此改为 `FieldStatistic.fieldStatsFor(field.type(), fieldId)`，移除了手动补偿。

### `api/src/test/java/org/apache/iceberg/stats/TestStatsUtil.java` (+12/-12 lines)

**修改目的**：更新测试以匹配新的 offset 语义。

**工作逻辑**：
两处测试中 `fieldStatsFor` 的第二个参数从 `fieldId + 1`（如 10001）改为 `fieldId`（如 10000），因为不再需要手动加 1。例如 `FieldStatistic.fieldStatsFor(Types.IntegerType.get(), 10001)` 改为 `FieldStatistic.fieldStatsFor(Types.IntegerType.get(), 10000)`。

### `core/src/main/java/org/apache/iceberg/stats/BaseFieldStats.java` (+2/-2 lines)

**修改目的**：修复 size() 返回值并使用 position 索引。

**工作逻辑**：
- `size()` 从返回 `7` 改为返回 `8`（此前漏计了 EXACT_BOUNDS，这是一个 off-by-one bug）。
- `get(int pos, Class<X> javaClass)` 中 `FieldStatistic.fromOffset(pos)` 改为 `FieldStatistic.fromPosition(pos)`，因为 `get` 的 pos 参数是 0-based 序号位置，应使用 `fromPosition` 而非 `fromOffset`。

### `core/src/test/java/org/apache/iceberg/stats/TestContentStats.java` (+11/-11 lines)

**修改目的**：更新测试以使用 position() 替代 offset() 作为 record 索引。

**工作逻辑**：
所有 `record.set(VALUE_COUNT.offset(), ...)` 改为 `record.set(VALUE_COUNT.position(), ...)`，因为 `record.set` 的索引是 0-based 位置，应使用 `position()`（0-7）而非 `offset()`（1-8）。异常消息断言从 "Invalid statistic offset" 改为 "Invalid statistic position"。

### `core/src/test/java/org/apache/iceberg/stats/TestFieldStats.java` (+11/-11 lines)

**修改目的**：同上，更新字段统计测试。

**工作逻辑**：
所有 `fieldStats.get(VALUE_COUNT.offset(), Long.class)` 改为 `fieldStats.get(VALUE_COUNT.position(), Long.class)`。越界测试的异常消息从 "Invalid statistic offset: 10" 改为 "Invalid statistic position: 10"。

## 总结

本提交将 `FieldStatistic` 的 offset 从 0-based 对齐到设计文档/规范中的 1-based，引入 `position()` 方法区分"字段 ID 偏移"与"0-based 序号位置"两个概念，修复了 `BaseFieldStats.size()` 返回 7 而非 8 的 off-by-one bug，并统一了所有调用点。这消除了代码中手动 `+1` 补偿的混乱，使统计字段的 ID 布局与 Iceberg 规范一致。
