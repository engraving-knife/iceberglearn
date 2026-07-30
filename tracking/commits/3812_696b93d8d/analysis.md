# 提交 3812：Arrow: Fix truncation of decimals with precision larger than 18 (#16627)

## 提交信息

- **序号**：3812 / 4088
- **哈希**：696b93d8d0cd01981ebf9871dbd3281bc57cc7a4
- **短哈希**：696b93d8d
- **日期**：2026-06-01 17:25:44 +0200
- **作者**：Vova Kolmakov <wombatukun@gmail.com>
- **提交说明**：Arrow: Fix truncation of decimals with precision larger than 18 (#16627)
- **PR/Issue**：#16627

## 总体目的

本提交修复 Iceberg Arrow 向量化读取器在读取精度大于 18 的 decimal 列时发生静默截断的缺陷。在 Parquet 中，decimal 类型的存储方式取决于精度：精度 ≤ 18 时通常用 INT64 存储（unscaled value 可放入 long），精度 ≥ 19 时用 FIXED_LEN_BYTE_ARRAY 存储（unscaled value 用 `BigInteger` 表示，可能超过 long 范围）。

原 `ArrowVectorAccessors` 中二进制 decimal 访问器的 `ofBigDecimal` 方法实现为：
```java
return BigDecimal.valueOf(value.unscaledValue().longValue(), scale);
```
这里调用 `BigInteger.longValue()` 会把超过 long 范围的 unscaled value 截断为低 64 位，导致读出的 decimal 值被静默改变。例如 `99999999999999999999999999999999999999`（38 位）会被截断成一个完全错误的值。本提交将其改为直接返回原值，因为传入的 `value` 已经具有正确的 unscaled value 与 scale，无需任何转换。

## 如何达成设计目的

修复方式是移除多余的 `longValue()` 窄化转换，直接返回传入的 `BigDecimal`。注释说明：值已经具备正确的 unscaled value 和 scale，且 unscaled value 可能超出 long 范围，因此不能窄化为 long。同时新增一个专门测试，构造精度 38、scale 0、unscaled 值远超 `Long.MAX_VALUE` 的 decimal 列，验证读回的值与写入一致，防止回归。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ArrowVectorAccessors.java` (+3/-1 lines)

**修改目的**：修复高精度 decimal 读取时的截断 bug。

**工作逻辑**：
原实现把 `BigDecimal` 的 unscaled value 通过 `longValue()` 窄化为 long 再重建，这会丢失高位。修复后直接返回原值：
```java
@Override
public BigDecimal ofBigDecimal(BigDecimal value, int precision, int scale) {
  // Return the value unchanged: it already has the correct unscaled value and scale. The
  // unscaled value can exceed the range of a long, so it must not be narrowed to one.
  return value;
}
```

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/TestArrowReader.java` (+67/-0 lines)

**修改目的**：新增针对高精度 decimal 的回归测试。

**工作逻辑**：
新增 `testHighPrecisionDecimalIsReadCorrectly` 测试：
- 构造 `decimal(38, 0)` schema。
- 写入两个 unscaled 值远超 `Long.MAX_VALUE`（约 9.2e18）的 BigDecimal：`12345678901234567890`（20 位）和 `99999999999999999999999999999999999999`（38 位）。
- 用 `Parquet.write` 写入临时文件并追加到表。
- 用 `VectorizedTableScanIterable` 向量化读取，断言每行读回的值与写入值完全相等：
```java
assertThat(batch.column(0).getDecimal(i, precision, scale))
    .as("decimal(%d, %d) value at row %d must not be truncated", precision, scale, i)
    .isEqualTo(values.get(rowIndex));
```
测试注释明确说明：精度 ≥ 19 的 decimal 以 FIXED_LEN_BYTE_ARRAY 存储，读取时不得通过 `BigInteger.longValue()` 窄化。

## 总结

本提交修复了一个会导致数据损坏的严重 bug：高精度（>18）decimal 列在 Arrow 向量化读取时被静默截断。修复极简（一行实质改动），但影响重大——任何使用 Iceberg Arrow 读取器读取高精度 decimal 的用户都会受影响。新增的回归测试覆盖了远超 long 范围的边界值，确保修复稳固。这是数据正确性保障的典型范例。
