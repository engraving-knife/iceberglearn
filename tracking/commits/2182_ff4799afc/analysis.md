# 提交 2182：API: Add Variant toString for time/nano timestamps type (#13151)

## 提交信息

- **序号**：2182 / 4088
- **哈希**：ff479afcd279dfdb3bd051bb0d4ffc9cd861575
- **短哈希**：ff4799afc
- **日期**：2025-05-29 10:48:50 -0700
- **作者**：Aihua Xu
- **提交说明**：API: Add Variant toString for time/nano timestamps type (#13151)
- **PR/Issue**：#13151

## 总体目的

此提交为 Variant 数据类型的 `toString()` 方法添加对 TIME 和纳秒级时间戳（TIMESTAMPTZ_NANOS、TIMESTAMPNTZ_NANOS）类型的支持。Variant 是 Iceberg 中的一种半结构化数据类型（类似 JSON），可以存储多种类型的值。原来的 `toString()` 方法已经支持 DATE、TIMESTAMPTZ、TIMESTAMPNTZ 和 BINARY 类型的格式化输出，但缺少对 TIME 类型和纳秒级时间戳类型的处理。这会导致这些类型的 Variant 值在调用 `toString()` 时无法正确格式化。此提交补全了这些缺失的类型分支。

## 如何达成设计目的

- 在 `VariantPrimitive` 接口的 `toString()` 方法的 switch 语句中，添加 TIME、TIMESTAMPTZ_NANOS、TIMESTAMPNTZ_NANOS 三个 case 分支
- 使用对应的 DateTimeUtil 工具方法进行格式化

## 修改详情

### `api/src/main/java/org/apache/iceberg/variants/VariantPrimitive.java` (修改, +6/-0 lines)

**修改目的**：为 Variant 的 toString 添加缺失的时间类型支持。

**工作逻辑**：在 switch 语句中添加三个新的 case：
- `TIME`：使用 `DateTimeUtil.microsToIsoTime((Long) get())` 将微秒值格式化为 ISO 时间字符串
- `TIMESTAMPTZ_NANOS`：使用 `DateTimeUtil.nanosToIsoTimestamptz((Long) get())` 将纳秒值格式化为带时区的 ISO 时间戳字符串
- `TIMESTAMPNTZ_NANOS`：使用 `DateTimeUtil.nanosToIsoTimestamp((Long) get())` 将纳秒值格式化为不带时区的 ISO 时间戳字符串

## 总结

此提交补全了 Variant 类型 `toString()` 方法中对 TIME 和纳秒级时间戳类型的格式化支持。修改简洁，添加了三个 switch case 分支，使用 DateTimeUtil 中对应的工具方法进行格式化。这使得 Variant 类型能正确显示所有时间相关的子类型。
