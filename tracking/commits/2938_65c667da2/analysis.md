# 提交 2938：Nit: Move unchecked suppression down to violating assignment in `ParquetMetricsRowGroupFilter` (#14013)

## 提交信息

- **序号**：2938 / 4088
- **哈希**：65c667da2f7231bdfa571864e0436868bfbd4918
- **短哈希**：65c667da2
- **日期**：2025-11-30
- **作者**：Sreesh Maheshwar
- **提交说明**：Nit: Move unchecked suppression down to violating assignment in `ParquetMetricsRowGroupFilter`
- **PR/Issue**：#14013

## 总体目的

这是一处代码整洁性（"Nit"）的微调，目的是把 `@SuppressWarnings("unchecked")` 注解的作用范围从整个方法收窄到真正发生未检查转换的那一行赋值语句上。

在 `ParquetMetricsRowGroupFilter` 的 `startsWith` 与 `notStartsWith` 两个方法中，原本把 `@SuppressWarnings("unchecked")` 标注在整个方法上，这样做虽然能消除编译告警，但会"屏蔽"方法体内所有未检查转换告警——包括将来可能新引入的、与本次无关的告警，不利于在 review 和编译阶段及早发现新的类型安全问题。真正触发告警的其实只有一处局部变量赋值：

```java
Statistics<Binary> colStats = (Statistics<Binary>) stats.get(id);
```

这里 `stats.get(id)` 返回的是更宽泛的 `Statistics<?>`（或 `Object`），向 `Statistics<Binary>` 强转属于 unchecked cast。

通过把注解从方法级别下移到局部变量声明上，告警仍被正确抑制，但作用域被严格限定在那一次赋值，方法内其他位置若再出现 unchecked 转换将被编译器如实报告，提升后续维护的类型安全可见性。

## 如何达成设计目的

改动落在 `parquet` 模块的 `ParquetMetricsRowGroupFilter.java` 中，对 `startsWith`、`notStartsWith` 两个方法对称地做同一处理：删除方法上的 `@SuppressWarnings("unchecked")` 注解，改而在 `Statistics<Binary> colStats = ...` 局部变量声明前加注解。这样既保持编译通过，又使抑制范围最小化。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetricsRowGroupFilter.java` (+2/-2 lines)

**修改目的**：把 `@SuppressWarnings("unchecked")` 的作用域从方法级别收窄到真正发生 unchecked cast 的局部变量赋值上。

**工作逻辑**：
对 `startsWith(BoundReference<T>, Literal<T>)` 方法（约 438 行起）：
- 删除方法上方的 `@SuppressWarnings("unchecked")`；
- 在方法体内、第一个非空检查之后，给 `Statistics<Binary> colStats = (Statistics<Binary>) stats.get(id);` 一行加上 `@SuppressWarnings("unchecked")`。

对 `notStartsWith(BoundReference<T>, Literal<T>)` 方法（约 487 行起）做完全对称的处理。

这样改造后，注解紧贴违规赋值出现的位置：`stats.get(id)` 返回值被强转为 `Statistics<Binary>`，由于运行时泛型擦除，编译器无法校验集合里实际存储的是否就是 `Binary` 统计，因此报 unchecked 告警；用局部声明上的注解精准抑制即可。其他无关代码（如 `allNulls`、`mayContainNull` 调用以及 `ROWS_CANNOT_MATCH`/`ROWS_MIGHT_MATCH` 的早返回逻辑）将不再被"一锅端"地抑制，类型安全的检查覆盖面因此更精确。

## 总结

本次提交属于代码质量层面的微优化，无行为变化。通过把 `@SuppressWarnings("unchecked")` 从方法级下移到局部变量级，使告警抑制精确覆盖真正违规的强转赋值，避免无意中屏蔽方法体内其他潜在 unchecked 转换，提升了后续维护中类型安全告警的可见性与代码整洁度。
