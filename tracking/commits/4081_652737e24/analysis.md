# 提交 4081：API: Add tests for DateTimeUtil.microsToMillis

## 提交信息

- **序号**：4081 / 4088
- **哈希**：652737e243abe607c14373ee268dddbb656919e1
- **短哈希**：652737e24
- **日期**：2026-07-22 14:43:22 +0200
- **作者**：vishnu prakash
- **提交说明**：API: Add tests for DateTimeUtil.microsToMillis (#16773)
- **PR/Issue**：#16773

## 总体目的

`DateTimeUtil.microsToMillis(long micros)` 负责把微秒精度的时间戳截断为毫秒精度。它的实现使用 `Math.floorDiv(micros, MICROS_PER_MILLIS)`（即 1000），而不是简单的 `/ 1000`。

关键差异在于对负数（1970 年之前的时间戳）的处理：
- 整数除法 `/` 在 Java 中是向零截断（truncation toward zero），对负数会向上取整（例如 `-1510871468000001L / 1000 = -1510871468000`，丢失了那 1 微秒的负向偏移，导致毫秒值偏大）；
- `Math.floorDiv` 是向下取整（floor），对负数会得到更小的值（例如 `-1510871468000001L` floorDiv 1000 = `-1510871468001`），这正确反映了"1970 年之前的时刻在毫秒精度下应该向更负的方向取整"的语义（与时间轴方向一致）。

该方法此前没有专门的单元测试覆盖正数和负数两种情况。本提交补全这一测试缺口，确保 floor 语义在正负数下都正确，防止未来重构（例如误改为普通除法）破坏负时间戳的截断行为。

## 如何达成设计目的

在 `TestDateTimeUtil` 中新增一个 `microsToMillis` 测试方法，分别用一正一负两个边界值断言转换结果，特别覆盖了带余数（`...0001` 微秒尾数）的情况以验证 floor 行为。

## 修改详情

### `api/src/test/java/org/apache/iceberg/util/TestDateTimeUtil.java` (+6/-0 lines)

**修改目的**：为 `microsToMillis` 添加正负数边界单测。

**工作逻辑**：

```java
@Test
public void microsToMillis() {
  assertThat(DateTimeUtil.microsToMillis(1510871468000001L)).isEqualTo(1510871468000L);
  assertThat(DateTimeUtil.microsToMillis(-1510871468000001L)).isEqualTo(-1510871468001L);
}
```

- 正数 `1510871468000001L`（带 1 微秒尾数）截断为 `1510871468000L`，与普通除法结果一致；
- 负数 `-1510871468000001L` 截断为 `-1510871468001L`（而不是 `-1510871468000`），这正是 `Math.floorDiv` 与普通 `/` 的区别所在——验证了 1970 年之前时间戳的 floor 截断语义。

测试方法放在已有的 `nanosToMicros` 测试之前，与文件中其他时间单位转换测试风格保持一致。

## 总结

一次纯测试补全提交。为 `DateTimeUtil.microsToMillis` 增加了正负数两个用例，重点验证了对 1970 年之前时间戳（负数）使用 `Math.floorDiv` 而非普通除法的截断语义，覆盖了此前缺失的测试边界，对防止未来回归具有实际价值。
