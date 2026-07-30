# 提交 3202：API: Simplify sanitization of literals in predicates (#15224)

## 提交信息

- **序号**：3202 / 4088
- **哈希**：45d2ed0e7ea7230c8f54f02d900c0c454b68cf9d
- **短哈希**：45d2ed0e7
- **日期**：2026-02-03
- **作者**：Kristin Cowalcijk
- **提交说明**：API: Simplify sanitization of literals in predicates (#15224)
- **PR/Issue**：#15224

## 总体目的

该提交简化了 `ExpressionUtil` 中对谓词（predicate）字面量做脱敏（sanitization）的逻辑。`ExpressionUtil` 负责把 `Expression`（含字面量）转换为脱敏后的字符串表示，用于日志、metrics 等场景，避免把敏感数据（如具体字符串值、时间戳）原样输出。

原实现中存在两套并行的脱敏分发机制：
1. `sanitize(Literal<?> literal, long now, int today)`——基于 `Literal` 的运行时类型（`instanceof` 各 `Literals.XxxLiteral`）分发；
2. `sanitize(Type type, Object value, long now, int today)` 与 `sanitize(Type type, Literal<?> lit, long now, int today)`——基于 `Type.typeId()` 的 `switch` 分发。

后者要求先把 `Literal` 拆包成 `value`（`lit.value()`），再按类型 ID 重新分发到各类型处理函数，这与前者形成重复。两套机制维护成本高、易不一致（例如新增类型字面量时需同时改两处），且基于 `Type` 的版本对未覆盖类型会抛 `UnsupportedOperationException`。

本次简化移除了基于 `Type` 的两个方法及其 `switch` 分发，统一让所有调用点使用基于 `Literal` 的 `sanitize(Literal<?>, ...)`。由于 `Literal` 自身已携带类型信息且其子类覆盖了全部类型，这种统一既减少重复代码（净删 41 行），又消除了双路径不一致的风险，是典型的"去重复、收敛到单一分发路径"重构。

## 如何达成设计目的

删除 `sanitize(Type, Object, ...)` 与 `sanitize(Type, Literal<?>, ...)` 两个方法及 `import org.apache.iceberg.types.Type;`，并把四处调用点从"传 type + value/literal"改为"直接传 Literal"（必要时强转为 `Literal<?>`）。所有调用最终汇聚到既有的 `sanitize(Literal<?> literal, long now, int today)`，由其按 `instanceof` 完成类型分发。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java` (+5/-46 lines)

**修改目的**：移除冗余的基于 `Type` 的脱敏分发，统一为基于 `Literal` 的单一分发。

**工作逻辑**：

1. 移除导入 `import org.apache.iceberg.types.Type;`（不再需要按 Type 分发）。

2. 谓词脱敏入口（约 305 行）：`BoundLiteralPredicate` 分支由
   `sanitize(bound.term().type(), bound.literal(), now, today)` 改为
   `sanitize(bound.literal(), now, today)`；`BoundSetPredicate` 分支由
   `sanitize(bound.term().type(), lit, now, today)` 改为
   `sanitize((Literal<?>) lit, now, today)`。这里 `lit` 来自 `bound.literalSet()`，元素类型为 `Object`，故需强转 `Literal<?>`。

3. 字符串化表示 `value(BoundLiteralPredicate<?>)`：由
   `sanitize(pred.term().type(), pred.literal().value(), nowMicros, today)` 改为
   `sanitize(pred.literal(), nowMicros, today)`。原写法先 `pred.literal().value()` 拆包成 `Object` 再按类型分发，新写法直接传 `Literal`，省去拆包与重新分发。

4. `IN` / `NOT IN` 的 `abbreviateValues` 流水线中两处 `sanitize(pred.term().type(), lit, nowMicros, today)` 同样改为 `sanitize((Literal<?>) lit, nowMicros, today)`。

5. 删除两个旧方法：
   - `private static String sanitize(Type type, Literal<?> lit, long now, int today)`（仅转调 `sanitize(type, lit.value(), now, today)`）；
   - `private static String sanitize(Type type, Object value, long now, int today)`，其内部是一段按 `type.typeId()` 分发的 `switch`（INTEGER/LONG→`sanitizeNumber`、FLOAT/DOUBLE、DATE、TIME、TIMESTAMP、TIMESTAMP_NANO、STRING、VARIANT、UNKNOWN、BOOLEAN/UUID/DECIMAL/FIXED/BINARY→`sanitizeSimpleString`），末尾抛 `UnsupportedOperationException`。这段逻辑的功能已被 `sanitize(Literal<?>, ...)` 的 `instanceof` 分发覆盖（各 `Literals.XxxLiteral` 分支内部会取 value 并调用同样的 `sanitizeNumber`/`sanitizeDate`/`sanitizeTimestamp`/`sanitizeString`/`sanitizeVariant`/`sanitizeSimpleString` 等函数）。

保留的 `sanitize(Literal<?> literal, long now, int today)` 方法以 `if (literal instanceof Literals.StringLiteral)` 等形式逐类型处理，是统一后的唯一脱敏入口。

## 总结

该重构将谓词字面量脱敏从"Type + value 双分发"收敛为"Literal 单一分发"，删除约 41 行重复代码，降低了维护成本与双路径不一致的风险，同时行为保持等价。属于 API 模块的纯内部简化，对外接口无变化，提升了代码可读性与可扩展性。
