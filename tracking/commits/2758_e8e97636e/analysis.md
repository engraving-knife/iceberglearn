# 提交 2758：Data: Replace LongMath.checkedMultiply with Math.multiplyExact (#14346)

## 提交信息

- **序号**：2758 / 4088
- **哈希**：e8e97636e37ebebf19074fd32e5055dcc521aa37
- **短哈希**：e8e97636e
- **日期**：2025-10-16 16:43:05 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Data: Replace LongMath.checkedMultiply with Math.multiplyExact (#14346)
- **PR/Issue**：#14346

## 总体目的

本提交将 `BaseDeleteLoader` 中使用的 Guava `LongMath.checkedMultiply` 替换为 JDK 标准库的 `Math.multiplyExact`，减少对 Guava relocated 工具类的依赖。

背景在于：Iceberg 项目将 Guava 的部分工具类以 `org.apache.iceberg.relocated.com.google.common` 包名重新定位（relocate）后内嵌，以避免与用户依赖中的 Guava 版本冲突。`LongMath.checkedMultiply` 用于在计算删除文件预估大小时，将删除记录数（recordCount）与单条记录预估大小（recordSize）相乘，并在溢出时抛出 `ArithmeticException`。

Java 8 起的标准库 `Math.multiplyExact(long, long)` 提供了完全相同的语义：两个 long 相乘，溢出时抛出 `ArithmeticException`。因此可以用标准库替代 relocated Guava 工具类，减少不必要的依赖。这是 Iceberg 逐步用 JDK 标准库替代 Guava 工具类的小型清理工作的一部分。

## 如何达成设计目的

改动非常局部：在 `BaseDeleteLoader.estimateMemoryUsage`（或类似方法）中，将 `LongMath.checkedMultiply(recordCount, recordSize)` 替换为 `Math.multiplyExact(recordCount, recordSize)`，并移除对应的 `LongMath` import。外层的 `try { ... } catch (ArithmeticException e) { return Long.MAX_VALUE; }` 逻辑保持不变，因为两者溢出时都抛出 `ArithmeticException`。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java` (+1/-2 lines)

**修改目的**：用 `Math.multiplyExact` 替换 `LongMath.checkedMultiply`，并移除 Guava import。

**工作逻辑**：
- 移除 import `org.apache.iceberg.relocated.com.google.common.math.LongMath`。
- 在计算 `return LongMath.checkedMultiply(recordCount, recordSize)` 处改为 `return Math.multiplyExact(recordCount, recordSize)`。
- 该调用位于 `try` 块内，外层 `catch (ArithmeticException e) { return Long.MAX_VALUE; }` 在溢出时返回 `Long.MAX_VALUE`，表示删除文件过大、无法精确估计。`Math.multiplyExact` 与 `LongMath.checkedMultiply` 在溢出时都抛出 `ArithmeticException`，因此 catch 逻辑无需修改。

## 总结

本提交是一个小型的依赖清理：将 `BaseDeleteLoader` 中用于删除文件大小估算的 `LongMath.checkedMultiply` 替换为功能等价的 JDK 标准库 `Math.multiplyExact`，并移除了对应的 relocated Guava import。两者语义完全一致（long 相乘，溢出抛 `ArithmeticException`），因此行为无任何变化，但减少了对内嵌 Guava 工具类的依赖。这符合 Iceberg 用 JDK 标准库替代 Guava 的一贯清理方向。
