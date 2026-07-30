# 提交 3643：Sink connector crashes on timestamps with fractional seconds and colon-separated UTC offset (Fixes #15838) (#15839)

## 提交信息

- **序号**：3643 / 4088
- **哈希**：0841cdea98645d14eb2394cc731d01713b67ad2b
- **短哈希**：0841cdea9
- **日期**：2026-05-05 08:41:50 -0700
- **作者**：Soumyajit Sahu
- **提交说明**：Sink connector crashes on timestamps with fractional seconds and colon-separated UTC offset (Fixes #15838) (#15839)
- **PR/Issue**：#15839 (修复 #15838)

## 总体目的

这个提交修复了 Kafka Connect Iceberg Sink Connector 在处理带小数秒和冒号分隔 UTC 偏移的时间戳时崩溃的问题（issue #15838）。

`RecordConverter` 中的 `ensureTimestampFormat` 方法负责将各种时间戳字符串规范化为可解析的格式，其中包含一步：去除时区偏移中的冒号（如将 `+00:00` 转为 `+0000`）。但原实现硬编码假设时区符号（`+` 或 `-`）固定出现在索引 19 的位置（即 `yyyy-MM-ddTHH:mm:ss` 之后）。当时间戳包含小数秒时（如 `2026-03-31T03:17:37.260514+00:00`），时区符号实际出现在索引 26 处，导致原逻辑无法正确识别并去除冒号，最终解析失败使 connector 崩溃。

## 如何达成设计目的

将 `ensureTimestampFormat` 方法中固定索引 19 检查时区符号的逻辑改为动态搜索：从索引 19 开始向后遍历查找第一个 `+` 或 `-` 字符作为时区符号位置，然后判断该符号后第 3 个字符是否为冒号，若是则去除冒号。这样无论是否有小数秒都能正确定位时区偏移。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordConverter.java` (+13/-4 lines)

**修改目的**：修复带小数秒时间戳的时区冒号去除逻辑。

**工作逻辑**：
原代码：
```java
if (result.length() > 22
    && (result.charAt(19) == '+' || result.charAt(19) == '-')
    && result.charAt(22) == ':') {
  result = result.substring(0, 19) + result.substring(19).replace(":", "");
}
```
新代码：
```java
// 从秒部分之后（索引 19+）开始搜索时区偏移符号。
// 带小数秒时（如 "...T03:17:37.260514+00:00"），符号出现位置晚于索引 19，
// 因此必须动态定位而非假设固定位置。
int signIdx = -1;
for (int i = 19; i < result.length(); i++) {
  char ch = result.charAt(i);
  if (ch == '+' || ch == '-') {
    signIdx = i;
    break;
  }
}
if (signIdx != -1 && signIdx + 3 < result.length() && result.charAt(signIdx + 3) == ':') {
  result = result.substring(0, signIdx + 3) + result.substring(signIdx + 4);
}
```
新逻辑精确去除时区偏移中的冒号（仅去除 `+HH:MM` 中的冒号），而非原逻辑对整个时区部分做 `replace(":", "")`，更安全。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/TestRecordConverter.java` (+33 lines)

**修改目的**：新增带小数秒时间戳的转换测试。

**工作逻辑**：
1. `testTimestampWithZoneAndFractionalSecondsConversion`：验证带时区和小数秒的多种输入格式（`+00:00`、`+0000`、`Z`、空格分隔）都能正确转换为 `OffsetDateTime`。
2. `testTimestampWithoutZoneAndFractionalSecondsConversion`：验证不带时区和小数秒的多种输入格式都能正确转换为 `LocalDateTime`。

## 总结

这个提交修复了 Kafka Connect Iceberg Sink Connector 在处理带小数秒和冒号分隔 UTC 偏移的时间戳时崩溃的 bug。根本原因是时间戳格式规范化逻辑硬编码了时区符号的位置索引，无法适配带小数秒的情况。修复方案改为动态搜索时区符号位置，并精确去除冒号。同时补充了针对小数秒场景的测试用例，覆盖带/不带时区、多种分隔符和偏移格式。
