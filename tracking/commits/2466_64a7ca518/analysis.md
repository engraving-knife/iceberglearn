# 提交 2466：API: Fix timestamp(9) with identity partitioning. (#13746)

## 提交信息

- **序号**：2466 / 4088
- **哈希**：64a7ca518b4457e19d1a152cd17c33cf2597ba1d
- **短哈希**：64a7ca518
- **日期**：2025-08-06 08:57:32 -0700
- **作者**：Ryan Blue
- **提交说明**：API: Fix timestamp(9) with identity partitioning. (#13746)
- **PR/Issue**：#13746

## 总体目的

该提交修复了使用 `timestamp(9)`（纳秒精度时间戳，TimestampNanoType）进行 identity 分区时，谓词投影（predicate projection）中类型信息丢失的问题。

在 Iceberg 中，identity 分区变换会将列值直接作为分区值。当查询包含对时间戳列的过滤条件时，这些谓词需要通过投影（projection）从表级别下推到分区级别。在 `Identity` 类的 `project` 方法中，处理字面量谓词（literal predicate）时，原代码调用了 `predicate.asLiteralPredicate().literal().value()` 来获取字面量的值，然后使用 `Expressions.predicate(op, name, value)` 重新构建谓词。

问题在于：`literal().value()` 返回的是字面量的原始值（例如 `Long` 类型），而 `Expressions.predicate(op, name, value)` 会通过 `Literals.from(value)` 重新创建字面量。对于纳秒时间戳，这个重新创建过程会将其识别为普通的 `Long` 字面量，而非 `TimestampNanoLiteral`，导致类型信息丢失。这在后续绑定（binding）时可能导致类型不匹配或错误的值解释。

修复方案是将整个 `Literal` 对象传递给 `Expressions.predicate()`，而不是仅传递值，从而保留字面量的类型信息。

## 如何达成设计目的

通过修改 `Identity.java` 中的 `project` 方法，将字面量谓词的处理从传递 `.value()` 改为传递整个 `.literal()` 对象：

```java
// 修改前
return Expressions.predicate(predicate.op(), name, predicate.asLiteralPredicate().literal().value());
// 修改后
return Expressions.predicate(predicate.op(), name, predicate.asLiteralPredicate().literal());
```

同时需要确保 `Expressions.predicate()` 和 `Literals.from()` 能够正确处理传入的 `Literal` 对象（这部分在提交 2467 中进一步完善）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/transforms/Identity.java` (+1/-2 lines)

**修改目的**：在 identity 分区投影中保留字面量的类型信息。

**工作逻辑**：
```java
// 修改前
return Expressions.predicate(
    predicate.op(), name, predicate.asLiteralPredicate().literal().value());
// 修改后
return Expressions.predicate(predicate.op(), name, predicate.asLiteralPredicate().literal());
```

通过传递整个 `Literal` 对象而非其 `.value()`，确保纳秒时间戳的 `TimestampNanoLiteral` 类型信息在投影过程中被保留。

### `api/src/test/java/org/apache/iceberg/transforms/TestProjection.java` (+22/-0 lines)

**修改目的**：添加纳秒时间戳 identity 分区投影的测试用例。

**工作逻辑**：
新增 `testTimestampNanosIdentityProjection()` 测试方法：
- 创建包含 `TimestampNanoType.withoutZone()` 字段的 schema
- 使用 identity 分区
- 构造一个等于条件 `Expressions.equal("ts", "2022-07-26T12:13:14.123456789")`
- 执行投影和绑定
- 验证绑定的谓词是字面量谓词，且字面量值为 `1658837594123456789L`（纳秒精度）

## 总结

该提交修复了 `timestamp(9)`（纳秒精度时间戳）在 identity 分区投影中类型信息丢失的问题。通过在投影时传递整个 `Literal` 对象而非仅传递值，确保 `TimestampNanoLiteral` 的类型信息被保留，避免在后续绑定和查询优化中出现类型不匹配。同时添加了专门的测试用例验证纳秒时间戳的投影正确性。该修改虽小但修复了一个重要的类型安全问题。
