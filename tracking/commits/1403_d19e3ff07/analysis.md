# 提交 1403：API, Core: Remove unnecessary casts to Iterable<T> (#11601)

## 提交信息

- **序号**：1403 / 4088
- **哈希**：d19e3ff07653167d902865281601a5da4e2f2def
- **短哈希**：d19e3ff07
- **日期**：2024-11-20（Wed Nov 20 17:32:11 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：API, Core: Remove unnecessary casts to Iterable<T> (#11601)
- **PR/Issue**：#11601

## 总体目的

代码清理：在 `ExpressionUtil` 与 `ExpressionParser` 中存在「先以更具体的类型（如 `String` / `Object`）组装 `Iterable`，再把整个 `Iterable` 强转为 `Iterable<T>`」的写法。这种整体强转依赖 heap pollution 风险较高的 unchecked cast，并且把类型不匹配的问题推迟到迭代时才暴露。

本提交把「集合级强转」改写为「元素级强转」：让 lambda / 单值在进入 `Iterable` 之前就先被强转为 `T`，从而直接得到 `Iterable<T>`，整体无需再强转。这是没有行为变化的纯代码质量改进。

## 如何达成设计目的

1. 在 `ExpressionUtil.sanitize` 处理 `IN`/`NOT_IN` 谓词时，把原来 `Iterable<String>` + `(Iterable<T>) iter` 改为 `Iterable<T>` + 在 `map` 里对单个元素 `(T) sanitize(...)`，省去外层强转。
2. 在 `ExpressionParser` 解析单值谓词（LT/GT/EQ 等）时，把 `Object value` + `(Iterable<T>) ImmutableList.of(value)` 改为 `T value` + 直接 `ImmutableList.of(value)`。
3. 两处方法本身都在 `@SuppressWarnings("unchecked")` 上下文里，元素级强转依旧是 unchecked，但更清晰，且消除了对整个 Iterable 的可疑强转。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java`

**修改目的**：消除 `IN`/`NOT_IN` 谓词 sanitize 流程中的 `Iterable<T>` 强转。

**工作逻辑**：在 `StringSanitizer.predicate(UnboundPredicate<T> pred)` 的 `case IN` / `case NOT_IN` 分支：

修改前：
```java
Iterable<String> iter =
    () -> pred.literals().stream().map(lit -> sanitize(lit, now, today)).iterator();
return new UnboundPredicate<>(pred.op(), pred.term(), (Iterable<T>) iter);
```

修改后：
```java
Iterable<T> iter =
    () -> pred.literals().stream().map(lit -> (T) sanitize(lit, now, today)).iterator();
return new UnboundPredicate<>(pred.op(), pred.term(), iter);
```

`sanitize(literal, now, today)` 这个重载返回 `String`（用于字符串脱敏），原代码先把流元素类型固定为 `String` 形成 `Iterable<String>`，再整体强转为 `Iterable<T>`；新代码在 `map` 内部把每个 `String` 显式强转为 `T`，于是直接得到 `Iterable<T>`，调用 `new UnboundPredicate` 时不再需要外层强转。这与同文件中 `BoundSetPredicate` 分支的写法（`bound.literalSet().stream().map(lit -> (T) sanitize(...))`）保持一致。

### `core/src/main/java/org/apache/iceberg/expressions/ExpressionParser.java`

**修改目的**：消除单值谓词 JSON 解析中的 `Iterable<T>` 强转。

**工作逻辑**：在 `predicate(op, term, node, schema)` 的单值谓词分支（LT/LT_EQ/GT/GT_EQ/EQ/NOT_EQ/STARTS_WITH/NOT_STARTS_WITH）：

修改前：
```java
Object value = literal(JsonUtil.get(VALUE, node), convertValue);
return Expressions.predicate(op, term, (Iterable<T>) ImmutableList.of(value));
```

修改后：
```java
T value = literal(JsonUtil.get(VALUE, node), convertValue);
return Expressions.predicate(op, term, ImmutableList.of(value));
```

`literal(JsonNode, Function<JsonNode, T>)` 是泛型方法 `<T> T literal(...)`，本应直接返回 `T`，原先却用 `Object` 接收再整体强转 `ImmutableList<Object>` 为 `Iterable<T>`；新写法让 `value` 直接是 `T`，于是 `ImmutableList.of(value)` 推导为 `ImmutableList<T>`，无需强转即可传入 `Expressions.predicate`。

## 小结

- **成效**：移除了两处不必要的「集合级」unchecked 强转 `(Iterable<T>)`，改为「元素级」强转，使类型流向更清晰、更符合泛型惯用法，并消除潜在的 heap pollution 警告来源。运行时行为完全不变。
- **影响范围**：仅 api 与 core 各一处表达式相关工具类，5 行修改，无 API/行为变化。
- **回迁到 1.4.x 的注意事项**：这是纯代码质量改进，不修复任何 bug，对 1.4.x 运行时无影响。1.4.x 若存在同样写法可以同步清理，但不回迁也不会造成功能差异。回迁时注意 `ExpressionUtil` 在 1.4.x 中 `sanitize` 重载签名是否一致；若已存在相同结构则直接套用即可。
