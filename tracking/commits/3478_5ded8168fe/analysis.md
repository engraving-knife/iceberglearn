# 提交 3478：Arrow: Tighten VectorHolder constructor visibility to private (#15804)

## 提交信息

- **序号**：3478 / 4088
- **哈希**：5ded8168fe59f4bc00f4f8177a15e04fa5e1c7ca
- **短哈希**：5ded8168fe
- **日期**：2026-03-27 20:30:39 -0700
- **作者**：Eunbin Son
- **提交说明**：Arrow: Tighten VectorHolder constructor visibility to private (#15804)
- **PR/Issue**：#15804

## 总体目的

收紧 `VectorHolder` 类中三参数构造函数的可见性，从包级私有（package-private）改为 `private`。这是一个代码质量改进，旨在减少不必要的可见性范围。

## 如何达成设计目的

该三参数构造函数 `VectorHolder(FieldVector vec, Types.NestedField field, NullabilityHolder nulls)` 原本是包级私有，但所有调用方都在同一个顶层类内部（`PositionVectorHolder` 内部类和 `vectorHolder()` 工厂方法）。由于 Java 静态内部类可以访问外部类的 private 成员，因此可以安全地将其改为 `private` 而不影响任何调用方。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorHolder.java` (+1/-1 lines)

**修改目的**：收紧构造函数可见性。

**工作逻辑**：
- 将 `VectorHolder(FieldVector vec, Types.NestedField field, NullabilityHolder nulls)` 的访问修饰符从包级私有（无修饰符）改为 `private`。
```java
-  VectorHolder(FieldVector vec, Types.NestedField field, NullabilityHolder nulls) {
+  private VectorHolder(FieldVector vec, Types.NestedField field, NullabilityHolder nulls) {
```

## 总结

这是一个简单的代码质量改进提交，将 `VectorHolder` 的三参数构造函数从包级私有收紧为 `private`，因为所有调用方都在该类内部。这减少了该构造函数的可见性范围，符合最小暴露原则。
