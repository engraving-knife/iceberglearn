# 提交分析：Arrow: Avoid deprecated ArrowType.Decimal constructor

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2124 |
| 短哈希 | `0aab58191` |
| 完整哈希 | `0aab58191ab34b04e98edce6a03494796583c2fb` |
| 作者 | Yuya Ebihara |
| 邮箱 | ebyhry@gmail.com |
| 日期 | 2025-05-14 23:00:38 2025 +0900 |
| 提交信息 | Arrow: Avoid deprecated ArrowType.Decimal constructor (#13051) |

## 总体目的

本提交将 Arrow 库中已弃用的 `ArrowType.Decimal(int precision, int scale)` 构造函数替换为新的 `ArrowType.Decimal(int precision, int scale, int bitWidth)` 构造函数。新构造函数要求显式指定位宽（bitWidth），默认值为 128 位。

## 设计目的的实现方式

将所有使用 `new ArrowType.Decimal(precision, scale)` 的调用替换为 `new ArrowType.Decimal(precision, scale, 128)`，显式传入 128 位宽度。

## 修改详情

### 1. 修改 `ArrowSchemaUtil.java`

**文件**：`arrow/src/main/java/org/apache/iceberg/arrow/ArrowSchemaUtil.java`

**修改内容**：

```java
// 修改前：
arrowType = new ArrowType.Decimal(decimalType.precision(), decimalType.scale());

// 修改后：
arrowType = new ArrowType.Decimal(decimalType.precision(), decimalType.scale(), 128);
```

**目的**：在 Iceberg 到 Arrow 的类型转换中，将 Decimal 类型创建从弃用的双参数构造函数切换为新的三参数构造函数，显式指定 128 位宽度。

### 2. 修改 `ArrowReaderTest.java`

**文件**：`arrow/src/test/java/org/apache/iceberg/arrow/vectorized/ArrowReaderTest.java`

**修改内容**：将测试中两处 `new ArrowType.Decimal(9, 2)` 替换为 `new ArrowType.Decimal(9, 2, 128)`。

**目的**：同步测试代码中的构造函数调用，保持一致性。

## 总结

本提交是一个简单的 API 弃用修复，将 Arrow 库中弃用的 `ArrowType.Decimal(precision, scale)` 双参数构造函数替换为 `ArrowType.Decimal(precision, scale, bitWidth)` 三参数构造函数，显式传入 128 位宽度。共修改 2 个文件，3 处调用。
