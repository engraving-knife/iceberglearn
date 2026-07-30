# 提交 1987：Flink: fix rateLimit argument check in TableMaintenance (#12773)

## 提交信息

- **序号**：1987 / 4088
- **哈希**：3d0f080f4611639bea128850c3b880375246a853
- **短哈希**：3d0f080f4
- **日期**：2025-04-11 17:08:34 +0200
- **作者**：GuoYu
- **提交说明**：Flink: fix rateLimit argument check in TableMaintenance (#12773)
- **PR/Issue**：#12773

## 总体目的

本提交修复 Flink 维护 API 中 `TableMaintenance.Builder.rateLimit(Duration)` 方法的参数校验错误。该缺陷针对 Flink v1.20 模块。

原代码为：
```java
Preconditions.checkNotNull(rateLimit.toMillis() > 0, "Rate limit should be greater than 0");
```
存在三处问题：
1. **用错校验方法**：`checkNotNull` 用于检查对象非 null，而此处传入的是 `boolean` 表达式（`rateLimit.toMillis() > 0` 会自动装箱为 `Boolean`）。`checkNotNull` 对非 null 的 `Boolean` 永远通过，对 `false`（非 null）也通过，因此该校验实际永远不会失败，形同虚设。正确的做法是对布尔条件使用 `checkArgument`。
2. **检查了错误的变量**：代码引用的是字段 `rateLimit`（当前值）而非方法参数 `newRateLimit`（待设置的新值），因此校验的是旧值而非传入值。
3. **潜在的空指针**：若字段 `rateLimit` 为 null（初始状态），`rateLimit.toMillis()` 会抛 NPE；即便改用 `newRateLimit`，未先判空直接调用 `toMillis()` 同样会在传入 null 时 NPE。

修复后先校验参数非空，再用 `checkArgument` 校验参数为正，且引用正确的参数变量。

## 如何达成设计目的

将单行错误校验拆分为两步正确校验：
1. `Preconditions.checkNotNull(newRateLimit, "Rate limit should not be null")`：确保传入参数非 null，避免后续 `toMillis()` NPE。
2. `Preconditions.checkArgument(newRateLimit.toMillis() > 0, "Rate limit should be greater than 0")`：用正确的 `checkArgument` 对布尔条件校验，并引用参数 `newRateLimit`。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (修改, +3/-1 lines)

**修改目的**：修正 `rateLimit` 方法的参数校验逻辑。

**工作逻辑**：在 `Builder.rateLimit(Duration newRateLimit)` 中，将 `Preconditions.checkNotNull(rateLimit.toMillis() > 0, "Rate limit should be greater than 0")` 替换为两条校验：先 `checkNotNull(newRateLimit, "Rate limit should not be null")`，再 `checkArgument(newRateLimit.toMillis() > 0, "Rate limit should be greater than 0")`。随后 `this.rateLimit = newRateLimit; return this;` 保持不变。

## 总结

本提交修复了 Flink v1.20 `TableMaintenance.Builder.rateLimit()` 的参数校验缺陷：原代码误用 `checkNotNull` 校验布尔条件、引用了字段而非参数、且存在 NPE 风险。修复为先 `checkNotNull` 判空、再 `checkArgument` 判正，并正确引用参数 `newRateLimit`。仅 3 行改动。
