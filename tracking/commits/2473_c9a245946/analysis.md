# 提交 2473：Core: Support timestamp nanos in single value parser (#13487)

## 提交信息

- **序号**：2473 / 4088
- **哈希**：c9a2459465e9c32c21d805f942f116f7db573751
- **短哈希**：c9a245946
- **日期**：2025-08-08 12:32:33 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Core: Support timestamp nanos in single value parser (#13487)
- **PR/Issue**：#13487

## 总体目的

该提交为 `SingleValueParser` 添加对纳秒级时间戳（TIMESTAMP_NANO）类型的支持，使该解析器能够正确处理 V3 规范中纳秒时间戳类型的默认值序列化与反序列化。

Iceberg V3 规范引入了纳秒精度的时间戳类型 `TimestampNanoType`（含带时区和不带时区两种）。`SingleValueParser` 负责将默认值在 JSON 与 Java 对象之间进行转换，此前已支持 `TIMESTAMP`（微秒精度）类型，但尚未支持 `TIMESTAMP_NANO` 类型。这意味着涉及纳秒时间戳默认值的场景（如 schema 默认值定义）无法被正确解析，会导致功能缺失或异常。该提交补全了这一能力。

## 如何达成设计目的

设计思路与已有的 `TIMESTAMP` 类型处理保持一致，分两个方向实现：

1. **fromJson（解析方向）**：在 switch 语句中新增 `TIMESTAMP_NANO` case。根据 `TimestampNanoType.shouldAdjustToUTC()` 判断是带时区还是不带时区：
   - 带时区：校验偏移必须为 `+00:00`（UTC），调用 `DateTimeUtil.isoTimestamptzToNanos` 解析。
   - 不带时区：调用 `DateTimeUtil.isoTimestampToNanos` 解析。

2. **toJson（序列化方向）**：在 switch 语句中新增 `TIMESTAMP_NANO` case。校验默认值为 Long 类型，根据是否带时区分别调用 `DateTimeUtil.nanosToIsoTimestamptz` 或 `DateTimeUtil.nanosToIsoTimestamp` 写出字符串。

两个方向均复用了 `DateTimeUtil` 中已有的纳秒时间戳工具方法，保持与微秒时间戳处理对称的实现风格。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SingleValueParser.java` (+23/-0 lines)

**修改目的**：添加 TIMESTAMP_NANO 类型的解析与序列化支持。

**工作逻辑**：

fromJson 方向（约第 140 行处）：新增 `case TIMESTAMP_NANO`，校验默认值为文本类型，根据 `shouldAdjustToUTC()` 分支处理。带时区时校验 UTC 偏移并调用 `isoTimestamptzToNanos`，不带时区时调用 `isoTimestampToNanos`。

toJson 方向（约第 303 行处）：新增 `case TIMESTAMP_NANO`，校验默认值为 Long 类型，根据 `shouldAdjustToUTC()` 分别调用 `nanosToIsoTimestamptz` 或 `nanosToIsoTimestamp` 写出 ISO 字符串。

### `core/src/test/java/org/apache/iceberg/TestSingleValueParser.java` (+11/-0 lines)

**修改目的**：为纳秒时间戳类型添加测试覆盖。

**工作逻辑**：

1. 在参数化测试数据中新增两条纳秒时间戳用例：
   - `TimestampNanoType.withoutZone()` 对应 `"2007-12-03T10:15:30.123456789"`
   - `TimestampNanoType.withZone()` 对应 `"2007-12-03T10:15:30.123456789+00:00"`

2. 新增 `testInvalidTimestamptzNano` 测试：验证带时区的纳秒时间戳传入非 UTC 偏移（`+01:00`）时抛出 `IllegalArgumentException`，消息以 "Cannot parse default as a timestamptz_ns value" 开头。

## 总结

该提交为 `SingleValueParser` 补全了 V3 纳秒时间戳类型的支持，使其能够正确解析和序列化纳秒时间戳默认值。实现风格与已有的微秒时间戳处理完全对称，复用 `DateTimeUtil` 工具方法，并配套了往返测试和异常测试。该提交是 Iceberg V3 类型支持在核心解析层的重要补充。
