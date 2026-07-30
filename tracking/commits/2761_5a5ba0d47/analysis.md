# 提交 2761：Parquet: Fix UnnecessaryParentheses warning (#14361)

## 提交信息

- **序号**：2761 / 4088
- **哈希**：5a5ba0d473086266fde00f3cbb1e2415ebe8cc6a
- **短哈希**：5a5ba0d47
- **日期**：2025-10-17 17:04:49 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Parquet: Fix UnnecessaryParentheses warning (#14361)
- **PR/Issue**：#14361

## 总体目的

本提交修复 `ParquetIO` 中一处代码风格告警（UnnecessaryParentheses），将一个不必要的中间变量和方法引用的冗余括号简化为直接的方法引用。

背景在于：在 `ParquetIO` 的向量化读取方法 `readVectored` 中，原代码先将 `allocate::allocate` 赋值给一个 `IntFunction<ByteBuffer> delegateAllocate` 局部变量，再将其传给 `delegate.readVectored`。这不仅引入了不必要的中间变量，方法引用外层的括号 `(allocate::allocate)` 也触发了静态分析工具的 UnnecessaryParentheses 告警。可以直接将方法引用 `allocate::allocate` 内联传递给 `delegate.readVectored`，无需中间变量和括号。

## 如何达成设计目的

删除中间变量 `delegateAllocate`，将 `allocate::allocate` 作为方法引用直接传递给 `delegate.readVectored`，并移除不再需要的 `IntFunction` import。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetIO.java` (+1/-3 lines)

**修改目的**：消除 UnnecessaryParentheses 告警，简化向量化读取方法。

**工作逻辑**：
- 移除 import `java.util.function.IntFunction`。
- 在 `readVectored` 方法中，删除 `IntFunction<ByteBuffer> delegateAllocate = (allocate::allocate);` 这一行。
- 将 `delegate.readVectored(delegateRange, delegateAllocate)` 改为 `delegate.readVectored(delegateRange, allocate::allocate)`，直接使用方法引用。

## 总结

本提交是一个小型的代码清理，消除了 `ParquetIO.readVectored` 中的 UnnecessaryParentheses 静态分析告警。通过删除不必要的中间变量 `delegateAllocate` 和冗余括号，直接将 `allocate::allocate` 方法引用传递给 `delegate.readVectored`，使代码更简洁。功能行为无任何变化。
