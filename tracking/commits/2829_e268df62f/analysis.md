# 提交 2829：Core: Fix overflow due to default value on timestamp nanos (#14359)

## 提交信息

- **序号**：2829 / 4088
- **哈希**：e268df62feaf52a72b9ed52ce24593d59eb41ea0
- **短哈希**：e268df62f
- **日期**：2025-11-04 08:37:09 +0100
- **作者**：Yuya Ebihara
- **提交说明**：Core: Fix overflow due to default value on timestamp nanos (#14359)
- **PR/Issue**：#14359

## 总体目的

本提交修复了一个纳秒级时间戳（TimestampNanoType）默认值解析时的整数溢出 bug（对应 issue #13160）。

问题出在 `SchemaParser.defaultFromJson` 方法中。当从 JSON 反序列化 schema 的字段默认值时，对于纳秒级时间戳类型，`SingleValueParser.fromJson` 会正确地返回一个 `long` 类型的纳秒值。但随后代码统一调用 `Expressions.lit(value)` 来创建字面量。

问题在于 `Expressions.lit(long)` 会将 long 值视为微秒（micros），创建 `TimestampLiteral`（微秒精度）。`TimestampLiteral` 内部在将微秒转换为其他精度时（如显示或比较），会将微秒值乘以 1000 来得到纳秒，这会导致数值溢出。因为纳秒时间戳的值已经很大（例如 2024 年的时间戳纳秒值约为 1.7e18），再乘以 1000 会远超 `Long.MAX_VALUE`（约 9.2e18），导致溢出和错误结果。

修复方案是：对于 `TimestampNanoType`，改用 `Expressions.nanos((long) value)` 而非 `Expressions.lit(value)`。`Expressions.nanos()` 会创建 `TimestampNanoLiteral`，它正确地将值视为纳秒，避免了后续的乘法溢出。这个方法是在提交 2467 中专门为纳秒精度时间戳引入的。

## 如何达成设计目的

设计思路是在 `defaultFromJson` 方法中对 `TimestampNanoType` 进行特殊处理：先用 `SingleValueParser` 解析出值，然后根据类型判断——如果是 `TimestampNanoType`，调用 `Expressions.nanos()` 创建纳秒字面量；否则保持原有的 `Expressions.lit()` 调用。

这种特判方式避免了修改 `Expressions.lit()` 的通用行为，只针对纳秒时间戳默认值这一特定场景进行修复，影响面最小。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SchemaParser.java` (+9/-1 lines)

**修改目的**：修复纳秒时间戳默认值解析的溢出问题。

**工作逻辑**：原代码为：
```java
return Expressions.lit(SingleValueParser.fromJson(type, json.get(defaultField)));
```
修改为：
```java
Object value = SingleValueParser.fromJson(type, json.get(defaultField));
if (type instanceof Types.TimestampNanoType) {
  // Call Expressions.nanos instead of Expressions.lit to prevent overflow
  // https://github.com/apache/iceberg/issues/13160
  return Expressions.nanos((long) value);
}
return Expressions.lit(value);
```
先解析出值到变量 `value`，然后检查类型是否为 `TimestampNanoType`：若是，强转为 `long` 后调用 `Expressions.nanos()` 创建纳秒字面量；否则调用原有的 `Expressions.lit(value)`。注释引用了对应的 issue #13160。

### `core/src/test/java/org/apache/iceberg/TestSchemaParser.java` (+8/-0 lines)

**修改目的**：为纳秒时间戳默认值解析新增测试用例。

**工作逻辑**：在参数化测试数据中新增两条纳秒时间戳用例：
- `Types.TimestampNanoType.withZone()`：使用 `Expressions.nanos(DateTimeUtil.isoTimestamptzToNanos("2024-12-17T23:59:59.123456789+00:00"))` 作为期望字面量
- `Types.TimestampNanoType.withoutZone()`：使用 `Expressions.nanos(DateTimeUtil.isoTimestampToNanos("2024-12-17T23:59:59.123456789"))` 作为期望字面量

这些用例验证纳秒精度的默认值能被正确解析为 `TimestampNanoLiteral`（通过 `Expressions.nanos` 创建），不再触发溢出。注意期望值也使用 `Expressions.nanos` 创建，与修复后的代码路径一致。新增了导入 `org.apache.iceberg.expressions.Expressions`。

## 总结

本提交修复了 `SchemaParser.defaultFromJson` 在处理纳秒级时间戳默认值时的整数溢出 bug。根因是对纳秒值错误地使用了 `Expressions.lit()`（将其视为微秒），后续微秒到纳秒的转换导致溢出。修复方案是对 `TimestampNanoType` 特判使用 `Expressions.nanos()` 创建正确的纳秒字面量。新增了带纳秒精度的测试用例验证修复，确保纳秒时间戳默认值能被正确解析。
