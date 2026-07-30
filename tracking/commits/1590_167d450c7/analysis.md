# 提交序号 1590 167d450c7 分析

## 提交信息
- 哈希：167d450c78e1e2f0f3338073d53b9a81caff9df9
- 日期：2025-01-16（Thu Jan 16 20:42:38 2025 +0900）
- 作者：dmgkeke <osch1120@gmail.com>
- 消息：API: Support sanitizing a `Literal<?>` (#11943)

## 总体目的

本提交修复 `ExpressionUtil.sanitize` 在处理已绑定（bound）的字面量谓词时抛出 `ClassCastException` 的 Bug，通过新增一个接受 `Literal<?>` 参数的 `sanitize` 重载方法，正确从中取出字面量值再进行脱敏。

**背景：** `ExpressionUtil` 提供表达式脱敏能力，把谓词中的具体字面量值替换为类型描述（如把日期 `2022-04-29` 脱敏成 `(date)`），用于日志、计划展示等不希望泄露真实值的场景。脱敏的核心方法是私有的 `sanitize(Type type, Object value, long now, int today)`，它根据 `type.typeId()` 用 switch 分支处理，例如 DATE 分支做 `sanitizeDate((int) value, today)`——这里把 `value` 强转为 `int`。

**Bug：** 在处理已绑定的 `BoundLiteralPredicate` 时，调用点是：
```java
(T) sanitize(bound.term().type(), bound.literal(), now, today)
```
`bound.literal()` 返回的是 `Literal<?>` 对象（而非字面量的值）。此前没有 `sanitize(Type, Literal<?>, ...)` 重载，所以编译器把 `Literal<?>` 当作 `Object` 匹配到 `sanitize(Type, Object, ...)`。进入方法后，DATE 分支执行 `sanitizeDate((int) value, today)`，此时 `value` 是个 `Literal` 对象而非 `Integer`，强转 `int` 必然抛 `ClassCastException`。也就是说，对一个已绑定的日期相等谓词调用 `sanitize` 会直接崩溃，脱敏功能在该路径下不可用。

**修复：** 新增重载 `sanitize(Type type, Literal<?> lit, long now, int today)`，它内部调用 `lit.value()` 取出真实值，再委托给 `sanitize(Type, Object, ...)`。由于方法重载按最具体参数类型匹配，`bound.literal()`（`Literal<?>`）现在会匹配到新重载而非 `Object` 版本，从而正确取值。

## 如何达成设计目的

改动非常聚焦：在 `ExpressionUtil` 中新增一个私有重载方法，让编译器对 `Literal<?>` 参数选择正确的脱敏入口。同时新增一个测试用例验证已绑定的日期谓词能被正确脱敏。

### 修改详情

#### api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java

**修改目的**：新增 `sanitize(Type, Literal<?>, long, int)` 重载，修复已绑定字面量谓词脱敏时的 `ClassCastException`。

**新增方法**（紧邻原有 `sanitize(Type, Object, ...)` 之前）：
```java
private static String sanitize(Type type, Literal<?> lit, long now, int today) {
  return sanitize(type, lit.value(), now, today);
}
```

**工作逻辑**：

- 该重载接受 `Literal<?>`，调用 `lit.value()` 取出字面量持有的事实值（如日期的 `int` 值、时间戳的 `long` 值等），再传给原有的 `sanitize(Type, Object, ...)` 做类型分支脱敏。
- 关键在于 Java 方法重载决议：对于调用 `sanitize(type, bound.literal(), now, today)`，`bound.literal()` 的编译时类型是 `Literal<?>`。`Literal<?>` 比 `Object` 更具体，因此编译器优先选择新增的 `sanitize(Type, Literal<?>, ...)` 重载，而不再错误地落入 `sanitize(Type, Object, ...)`。
- 修复后调用链：`sanitize(Type, Literal<?>, ...)` → `lit.value()` 取出真实值 → `sanitize(Type, Object, ...)` → 按 `typeId()` 正确分派（DATE 走 `sanitizeDate((int) value, today)`，此时 `value` 已是 `Integer`，强转成功）。

该改动不影响其他调用点：
- `sanitize(pred.term().type(), lit, ...)`（IN/NOT_IN 的 `literalSet()` 元素）——`lit` 来自 `literalSet()` 返回的是值（`T`），不是 `Literal`，仍匹配 `Object` 重载，行为不变。
- `sanitize(pred.literal(), now, today)`（单参 `Literal` 的脱敏）——匹配的是另一个独立的 `sanitize(Literal<?>, long, int)` 方法（无 `Type` 参数），与本改动无关。

#### api/src/test/java/org/apache/iceberg/expressions/TestExpressionUtil.java

**修改目的**：新增回归测试，验证已绑定的日期谓词能被正确脱敏。

**新增断言**（加入已有的 `testSanitizeDate()` 测试方法）：
```java
assertEquals(
    Expressions.equal("date", "(date)"),
    ExpressionUtil.sanitize(Expressions.equal("date", "2022-04-29").bind(STRUCT, true)));
```

**工作逻辑**：
- `Expressions.equal("date", "2022-04-29")` 构造一个未绑定的相等谓词，字面量是字符串 `"2022-04-29"`。
- `.bind(STRUCT, true)` 把它绑定到含 `date` 字段（DATE 类型）的结构，绑定后字面量变为 `Literal<Integer>`（日期在 Iceberg 内部以 `int` 存储）。
- 调用 `ExpressionUtil.sanitize(...)` 应返回 `Expressions.equal("date", "(date)")`——日期值被脱敏为 `(date)`。
- 修复前此调用会抛 `ClassCastException`（`Literal` 强转 `int` 失败）；修复后正常返回脱敏结果。该测试精确覆盖了 Bug 路径。

紧邻的上下文中已有对照用例：`ExpressionUtil.sanitize(Expressions.equal("test", "2022-04-29"))`（未绑定）和 `ExpressionUtil.sanitize(STRUCT, Expressions.equal("date", "2022-04-29"), true)`（另一条 sanitize 入口）。新增用例补上了"先 bind 再 sanitize"这第三条路径的覆盖。

## 小结

- **成效**：修复了 `ExpressionUtil.sanitize` 在处理已绑定字面量谓词（`BoundLiteralPredicate`）时抛 `ClassCastException` 的 Bug。新增 `sanitize(Type, Literal<?>, ...)` 重载让编译器对 `Literal<?>` 参数选择正确的脱敏入口，正确取出字面量值后再做类型分派。这是一个典型的"方法重载决议导致的类型不匹配"Bug，修复方式干净且无副作用。
- **影响范围**：仅 `api` 模块两个文件：`ExpressionUtil` 新增 4 行私有方法，`TestExpressionUtil` 新增 4 行测试断言。改动极小，不影响公开 API 签名，对已有调用点行为无变化。
- **回迁到 1.4.x 的注意事项**：这是一个影响运行时正确性的 Bug 修复——任何在 1.4.x 上对已绑定表达式调用 `sanitize` 的代码（如日志输出查询计划、REST Catalog 返回脱敏表达式）都会受此 Bug 影响。改动小且自包含，回迁风险极低，建议回迁。需确认 1.4.x 的 `ExpressionUtil` 中调用 `bound.literal()` 的代码路径与 main 一致（即同样存在重载决议问题）。
