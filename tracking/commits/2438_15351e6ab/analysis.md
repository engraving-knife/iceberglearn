# 提交 2438：Core: Fix decimal type for variant (#13692)

## 提交信息

- **序号**：2438 / 4088
- **哈希**：15351e6ab06cbfa2cbf771fe81512268c22bd75d
- **短哈希**：15351e6ab
- **日期**：2025-07-31 13:28:49 -0700
- **作者**：Aihua Xu
- **提交说明**：Core: Fix decimal type for variant (#13692)
- **PR/Issue**：#13692

## 总体目的

本提交修复了 Iceberg Variant 类型中 `BigDecimal` 到 Variant 物理类型（DECIMAL4/DECIMAL8/DECIMAL16）的选择逻辑。此前，代码使用 `value.unscaledValue().bitLength()`（未缩放值的位长度）来决定使用哪种物理类型，而正确的方式应当使用 `value.precision()`（精度）来判断。

Variant 是 Iceberg 新引入的半结构化数据类型，支持存储类似 JSON 的变体数据。Variant 的 decimal 值根据精度分为三种物理存储类型：DECIMAL4（精度 1-9，可用 32 位）、DECIMAL8（精度 10-18，可用 64 位）、DECIMAL16（精度 19-38，可用 128 位）。

原实现的问题在于：`bitLength < 32` 并不等价于"精度 <= 9"。例如 `123456.7890` 的精度是 10，但其未缩放值 `1234567890` 的 bitLength 是 31（< 32），会被错误地归类为 DECIMAL4，而实际上它应该使用 DECIMAL8。这会导致存储空间不足或语义不正确。修复后改用 precision 区间判断，确保类型选择与 Variant 规范一致。

## 如何达成设计目的

1. 将 `Variants.of(BigDecimal)` 中的判断条件从 `bitLength < 32/64/128` 改为基于 `precision` 的区间判断：`precision 1-9 → DECIMAL4`，`precision 10-18 → DECIMAL8`，`precision <= 38 → DECIMAL16`。
2. 更新相关测试中的测试数据，使 decimal4/decimal8 的测试用例精度真正落在对应区间内（原测试数据恰好是 bug 的反例）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/variants/Variants.java` (+11/-5 lines)

**修改目的**：修正 decimal 物理类型的选择逻辑。

**工作逻辑**：
- 原逻辑：`int bitLength = value.unscaledValue().bitLength();`，`bitLength < 32 → DECIMAL4`，`bitLength < 64 → DECIMAL8`，`bitLength < 128 → DECIMAL16`。
- 新逻辑：`int precision = value.precision();`，`precision 1-9 → DECIMAL4`，`precision 10-18 → DECIMAL8`，`precision <= 38 → DECIMAL16`。
- 异常信息中的 `value.precision()` 改为 `precision`（复用局部变量）。

### `api/src/test/java/org/apache/iceberg/variants/TestSerializedPrimitives.java` (+20/-10 lines)

**修改目的**：修正测试数据使其精度与物理类型匹配。

**工作逻辑**：
- `testDecimal4`：原数据 `123456.7890`（精度 10）被改为 `12345.6789`（精度 9），对应的字节序列也更新。这确保测试数据真正属于 DECIMAL4 范围。
- `testDecimal8`：原数据 `1234567890.987654321`（精度 19）被改为 `123456789.987654321`（精度 18），字节序列更新。确保数据属于 DECIMAL8 范围。

### `core/src/test/java/org/apache/iceberg/variants/TestPrimitiveWrapper.java` (+8/-4 lines)

**修改目的**：同步修正测试数据。

**工作逻辑**：将 decimal4 测试数据从 `123456.7890`/`-123456.7890` 改为 `12345.6789`/`-12345.6789`；decimal8 测试数据从 `1234567890.987654321`/`-1234567890.987654321` 改为 `123456789.987654321`/`-123456789.987654321`。decimal16 数据不变。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantReaders.java` (+8/-4 lines)

**修改目的**：同步修正测试数据。

**工作逻辑**：与 `TestPrimitiveWrapper` 相同的数据修改。

## 总结

本提交修复了 Variant 类型中 BigDecimal 到物理存储类型的选择逻辑，从基于未缩放值的 bitLength 改为基于 precision 区间判断，使类型选择符合 Variant 规范定义。原逻辑会导致某些精度跨界的值被错误归类（如精度 10 但 bitLength < 32 的值被误判为 DECIMAL4）。同时修正了测试数据，使其精度真正落在对应物理类型的区间内。
