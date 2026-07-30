# 提交 3170：Fix Internal to Generic conversion of TIMESTAMP_NANO (#15099)

## 提交信息

- **序号**：3170 / 4088
- **哈希**：03ed4ba9af4e47d32bdb22b7e3d033eb2a4b2c83
- **短哈希**：03ed4ba9a
- **日期**：2026-01-28
- **作者**：Ayush Saxena
- **提交说明**：Fix Internal to Generic conversion of TIMESTAMP_NANO (#15099)
- **PR/Issue**：#15099

## 总体目的

Iceberg 类型系统中，`TIMESTAMP_NANO` 是纳秒精度的时间戳类型（区别于微秒精度的 `TIMESTAMP`），同样区分带时区（`withZone()`）和不带时区（`withoutZone()`）两种。内部存储时，时间戳值以 `long` 表示（纳秒数）。`GenericDataUtil.internalToGeneric(Type, Object)` 是 core 模块中负责将 Iceberg 内部存储格式的值转换为 Iceberg 自带的 `GenericRecord`/`GenericData` 表示的工具方法，供无需绑定具体引擎（如 Spark/Flink）的场景使用。

本次提交由 Ayush Saxena 通过 PR #15099 修复一个功能缺失缺陷：`GenericDataUtil.internalToGeneric` 的 `switch` 语句中已处理 `DATE`、`TIME`、`TIMESTAMP`、`FIXED` 等类型，但遗漏了 `TIMESTAMP_NANO` 分支。这意味着当遇到 `TIMESTAMP_NANO` 类型的值时，该方法会落入 `switch` 的 default 路径，直接返回原始的 `Long` 值，而非正确转换为 Java 8 的 `java.time.LocalDateTime`（不带时区）或 `java.time.OffsetDateTime`（带时区）。这会导致 `TIMESTAMP_NANO` 字段在 Generic 数据表示中类型不正确，后续读取/比较/序列化时可能出现类型不匹配错误或数据丢失精度信息。

修复方式是在 `switch` 中补充 `case TIMESTAMP_NANO:` 分支，逻辑与既有 `case TIMESTAMP:` 完全对称：根据 `Types.TimestampNanoType.shouldAdjustToUTC()` 判断是否带时区，分别调用 `DateTimeUtil.timestamptzFromNanos(long)` 或 `DateTimeUtil.timestampFromNanos(long)` 完成转换。同时新增 `TestGenericDataUtil` 测试类，对该工具方法的各种类型转换进行全面覆盖，包括此前缺失的 `TIMESTAMP_NANO` 测试。

## 如何达成设计目的

在 `GenericDataUtil.java` 的 `internalToGeneric` 方法 `switch` 语句中，于 `case TIMESTAMP:` 之后新增 `case TIMESTAMP_NANO:` 分支，复用 `DateTimeUtil` 中已有的纳秒转换方法。新建 `TestGenericDataUtil` 测试类，覆盖 DATE、TIME、TIMESTAMP、TIMESTAMP_NANO、FIXED 以及 null 值的转换路径，确保每种类型都能正确物化为对应的 Java 类型。

## 修改详情

### `core/src/main/java/org/apache/iceberg/data/GenericDataUtil.java` (+6/-0 lines)

**修改目的**：为 internalToGeneric 补充 TIMESTAMP_NANO 类型转换分支。

**工作逻辑**：
在 `switch` 语句中，`case TIMESTAMP:` 分支后新增：
```java
case TIMESTAMP_NANO:
  if (((Types.TimestampNanoType) type).shouldAdjustToUTC()) {
    return DateTimeUtil.timestamptzFromNanos((Long) value);
  } else {
    return DateTimeUtil.timestampFromNanos((Long) value);
  }
```
逻辑与 `case TIMESTAMP:` 对称：`shouldAdjustToUTC()` 为 true 表示带时区，调用 `DateTimeUtil.timestamptzFromNanos` 返回 `OffsetDateTime`；否则调用 `DateTimeUtil.timestampFromNanos` 返回 `LocalDateTime`。输入 `value` 为存储的 `Long` 纳秒值。修复后该方法对所有 Iceberg 时间相关类型都能正确物化。

### `core/src/test/java/org/apache/iceberg/data/TestGenericDataUtil.java` (+109/-0 lines, 新文件)

**修改目的**：新增测试类全面验证 internalToGeneric 的类型转换。

**工作逻辑**：
新建测试类，包含 6 个测试方法：
- `testDateConversion`：验证 `DateType` 转为 `LocalDate`。
- `testTimeConversion`：验证 `TimeType` 转为 `LocalTime`，使用 `microsFromTime` 构造输入。
- `testTimestampConversion`：验证 `TimestampType.withoutZone()` 转为 `LocalDateTime`、`withZone()` 转为 `OffsetDateTime`，使用 `isoTimestampToMicros` 构造微秒输入。
- `testTimestampNanoConversion`：**核心新增**，验证 `TimestampNanoType.withoutZone()` 转为 `LocalDateTime`（纳秒精度 `111_456_789`）、`withZone()` 转为 `OffsetDateTime`，使用 `DateTimeUtil.isoTimestampToNanos("2025-01-15T11:25:20.111456789")` 构造纳秒输入。断言结果类型与值均正确，且纳秒精度（9 位小数）得以保留。
- `testFixedConversion`：验证 `FixedType` 的 `ByteBuffer` 转为 `byte[]`。
- `testNullValueConversion`：验证 null 输入返回 null。

测试用例以 `2025-01-15T11:25:20.111456789` 作为纳秒时间戳基准，能同时区分微秒（`111456000`）与纳秒（`111456789`）的末尾精度，确保 TIMESTAMP_NANO 转换不丢失精度。

## 总结

本次提交修复了 `GenericDataUtil.internalToGeneric` 遗漏 `TIMESTAMP_NANO` 类型分支的缺陷，使其能正确将纳秒时间戳内部值转换为 `LocalDateTime`/`OffsetDateTime`，与 `TIMESTAMP` 类型的处理保持对称。新增的 `TestGenericDataUtil` 测试类填补了该工具方法此前无单元测试的空白，并重点验证了纳秒精度的保留。该修复对使用 Generic 数据表示（如某些 reader/转换路径）处理纳秒时间戳的场景具有重要意义。
