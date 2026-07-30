# 提交 2638：API: Enables sanitizing Variant data type #11479 (#13137)

## 提交信息

- **序号**：2638 / 4088
- **哈希**：3e1ea48d36531fc4956d370b51e341da2b5e3f6f
- **短哈希**：3e1ea48d3
- **日期**：2025-09-15 14:38:33 -0500
- **作者**：Manikandan R
- **提交说明**：API: Enables sanitizing Variant data type #11479 (#13137)
- **PR/Issue**：#13137（关联 issue #11479）

## 总体目的

Iceberg 引入了 Variant 数据类型（一种半结构化数据类型，类似 JSON）。`ExpressionUtil.sanitize` 用于在日志和错误信息中对表达式字面量做脱敏处理，避免泄露敏感数据值——例如将字符串脱敏为哈希、将日期脱敏为日期范围桶、将数字脱敏为位数范围等。此前，Variant 类型的字面量在脱敏时走的是通用的 `toString()` 兜底逻辑，只做了简单的字符串哈希，无法体现 Variant 内部结构（对象字段名、数组元素、各种原始类型），脱敏粒度不够细致。

本提交为 Variant 数据类型实现了专门的脱敏逻辑：递归遍历 Variant 的对象、数组、原始值结构，对每种物理类型（INT8/INT16/INT32/INT64/FLOAT/DOUBLE/DECIMAL/DATE/TIMESTAMP/TIME 等）应用对应的脱敏策略，使脱敏结果既能保护敏感数据又能保留有意义的结构信息用于调试。

## 如何达成设计目的

1. 在 `Literals` 中新增 `VariantLiteral` 内部类，使 `Literals.from()` 能识别 `Variant` 值并创建对应 Literal，并在 `sanitize` 中分支到 Variant 专用脱敏。
2. 在 `ExpressionUtil` 中新增一系列 `sanitizeVariant` 方法，递归处理 Variant 的对象/数组/原始值三种节点类型，按物理类型分发到已有的 `sanitizeNumber`、`sanitizeDate`、`sanitizeTimestamp` 等方法。
3. 对象的字段名通过哈希脱敏为 `(hash-xxxx)`，字段值按类型脱敏。
4. 新增测试验证对象、数组、原始值的脱敏输出。
5. 调整了一些 Variant 测试工具方法的可见性和返回类型。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java` (+88/-8 lines)

**修改目的**：实现 Variant 类型的脱敏逻辑。

**工作逻辑**：
- 在 `sanitize` 的 `Type.TypeID` switch 中，将 `VARIANT` 分支从原先的兜底 `sanitizeSimpleString` 改为调用 `sanitizeVariant((Variant) value, now, today)`。
- 在字面量脱敏 `sanitizeUnbound` 中，新增对 `Literals.VariantLiteral` 的分支。
- 新增方法：
  - `sanitizeVariant(Variant value, ...)`：委托给 `sanitizeVariant(value.value(), ...)`。
  - `sanitizeVariant(VariantValue value, ...)`：按节点类型分发到对象/原始值/数组。
  - `sanitizeVariantObject`：遍历字段名，输出 `{(hash-field): value, ...}`，字段名哈希脱敏，值递归脱敏。
  - `sanitizeVariantArray`：遍历元素，输出 `[value, ...]`，元素递归脱敏。
  - `sanitizeVariantValue(VariantValue, PhysicalType, ...)`：按物理类型 switch：
    - 数值类型（INT8..DECIMAL16）→ `sanitizeNumber`。
    - DATE → `sanitizeDate`。
    - TIMESTAMP 系列 → `sanitizeTimestamp`。
    - TIME → `"(time)"`。
    - ARRAY/OBJECT → 递归。
    - 其余 → `sanitizeSimpleString`。

### `api/src/main/java/org/apache/iceberg/expressions/Literals.java` (+30/-1 lines)

**修改目的**：新增 VariantLiteral 支持创建 Variant 字面量。

**工作逻辑**：
- `Literals.from()` 新增分支：若 value 是 `Variant`，则创建 `new VariantLiteral((Variant) value)`。
- 新增 `VariantLiteral extends BaseLiteral<Variant>`：`to(Type)` 仅对 VARIANT 类型返回自身；`comparator()` 返回 null（不支持比较）；`typeId()` 返回 `VARIANT`。

### `api/src/test/java/org/apache/iceberg/expressions/TestExpressionUtil.java` (+251/-6 lines)

**修改目的**：验证 Variant 脱敏输出。

**工作逻辑**：
- Schema 新增字段 11 `var`（VariantType）。
- `testSanitizeVariantPrimitive`：验证 INT8/INT16/DOUBLE/TIMESTAMP 原始值的脱敏输出（如 `(2-digit-INT8)`、`(-224-digit-DOUBLE)`、`(timestamp)`）。
- `testSanitizeVariantArray`：验证含嵌套类型的数组脱敏输出。
- `testSanitizeVariantObject`：验证包含多种类型字段的对象脱敏输出，字段名哈希、值按类型脱敏。
- 新增辅助方法 `createTimestamp`、`createTimestampNanos`、`createArrayWithNestedTypes`。

### `api/src/test/java/org/apache/iceberg/variants/TestSerializedArray.java` (+1/-1 lines) 等

**修改目的**：适配 `createString` 返回类型从 `SerializedPrimitive` 改为 `VariantPrimitive<?>`。

**工作逻辑**：`VariantTestUtil.createString` 返回类型改为公开的 `VariantPrimitive<?>`，相应测试中的局部变量类型同步调整。

## 总结

本提交为 Variant 数据类型实现了专门的脱敏逻辑，使 `ExpressionUtil.sanitize` 能够递归处理 Variant 的对象/数组/原始值结构，按物理类型应用合适的脱敏策略。这补齐了 Variant 类型在表达式脱敏上的能力缺口，使日志和错误信息在保护敏感数据的同时保留有意义的结构信息，便于调试。配套测试覆盖了各种原始类型和嵌套结构。
