# 提交 2467：API: Add expression factory methods for timestamp literals. (#13747)

## 提交信息

- **序号**：2467 / 4088
- **哈希**：9ac8b2bcb33f8834e51e523d709cdcc23121929a
- **短哈希**：9ac8b2bcb
- **日期**：2025-08-06 09:04:24 -0700
- **作者**：Ryan Blue
- **提交说明**：API: Add expression factory methods for timestamp literals. (#13747)
- **PR/Issue**：#13747

## 总体目的

该提交在 `Expressions` 类中添加了三个新的工厂方法，用于创建不同精度的时间戳字面量：`micros()`、`millis()` 和 `nanos()`。这些方法分别创建微秒、毫秒和纳秒精度的时间戳 `Literal` 对象。

此前，Iceberg 的 `Expressions` 类没有提供直接创建时间戳字面量的便捷方法。用户如果需要在表达式中使用时间戳字面量，需要通过字符串解析或其他间接方式，这可能导致精度丢失或类型不明确。特别是对于纳秒精度的时间戳（`TimestampNanoType`），缺少直接的字面量创建方法会使得在表达式中使用纳秒时间戳变得困难。

该提交与提交 2466（修复 timestamp(9) identity 分区）密切相关——2466 修改了投影逻辑以传递整个 `Literal` 对象，而本提交确保 `Literals.from()` 能够正确处理已包装为 `Literal` 的输入，并提供了创建时间戳字面量的便捷方法。

## 如何达成设计目的

整体设计包含两个部分：

1. **新增工厂方法**：在 `Expressions` 类中添加三个静态方法：
   - `micros(long micros)`：创建微秒精度的 `TimestampLiteral`
   - `millis(long millis)`：创建毫秒精度的 `TimestampLiteral`（内部乘以 1000 转换为微秒）
   - `nanos(long nanos)`：创建纳秒精度的 `TimestampNanoLiteral`

2. **更新 `Literals.from()` 方法**：在 `Literals.from()` 中添加对 `Literal` 类型输入的处理——如果传入的值已经是 `Literal` 实例，直接返回该实例，避免将 `Literal` 对象误处理为普通值。这使得提交 2466 中传递整个 `Literal` 对象给 `Expressions.predicate()` 的方式能够正常工作。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/Expressions.java` (+30/-0 lines)

**修改目的**：添加时间戳字面量工厂方法。

**工作逻辑**：
```java
public static Literal<Long> micros(long micros) {
    return new Literals.TimestampLiteral(micros);
}

public static Literal<Long> millis(long millis) {
    return new Literals.TimestampLiteral(millis * 1000);
}

public static Literal<Long> nanos(long nanos) {
    return new Literals.TimestampNanoLiteral(nanos);
}
```

- `micros()` 直接使用微秒值创建 `TimestampLiteral`
- `millis()` 将毫秒值乘以 1000 转换为微秒后创建 `TimestampLiteral`
- `nanos()` 使用纳秒值创建 `TimestampNanoLiteral`

### `api/src/main/java/org/apache/iceberg/expressions/Literals.java` (+3/-1 lines)

**修改目的**：让 `Literals.from()` 能够正确处理 `Literal` 类型的输入。

**工作逻辑**：
```java
// 修改前
if (value instanceof Boolean) {
    return (Literal<T>) new Literals.BooleanLiteral((Boolean) value);
}
// 修改后
if (value instanceof Literal) {
    return (Literal<T>) value;
} else if (value instanceof Boolean) {
    return (Literal<T>) new Literals.BooleanLiteral((Boolean) value);
}
```

新增的 `Literal` 类型检查放在最前面，确保如果传入的值已经是 `Literal` 实例，直接返回，避免后续的类型判断逻辑误处理。

### `api/src/test/java/org/apache/iceberg/expressions/TestExpressionHelpers.java` (+52/-0 lines)

**修改目的**：为新添加的工厂方法添加测试。

**工作逻辑**：
新增四个测试方法：
- `testMillisLiteral()`：验证 `millis()` 创建的字面量值正确，且可正确转换为纳秒类型
- `testMicrosLiteal()`：验证 `micros()` 创建的字面量值正确，且可正确转换为纳秒类型
- `testNanosLiteral()`：验证 `nanos()` 创建的字面量值正确，且可正确转换为微秒类型
- `testMixedTimestampLiterals()`：验证不同精度的字面量在 `in` 表达式中能正确去重和绑定（三个表示同一时间的不同精度字面量在绑定后被简化为 `equals` 谓词）

## 总结

该提交在 `Expressions` 类中添加了 `micros()`、`millis()` 和 `nanos()` 三个工厂方法，用于便捷地创建不同精度的时间戳字面量。同时更新了 `Literals.from()` 方法以正确处理 `Literal` 类型的输入，这是提交 2466 中传递整个 `Literal` 对象的配套修改。该提交完善了 Iceberg 表达式 API 对时间戳类型的支持，特别是纳秒精度时间戳的处理能力。
