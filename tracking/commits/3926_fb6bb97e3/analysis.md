# 提交 3926：Spark 3.5, 4.0: Throw on unsupported complex types in SparkValueConverter (#16925)

## 提交信息

- **序号**：3926 / 4088
- **哈希**：fb6bb97e393b8af692973e3a1268cccf6ae2c5c2
- **短哈希**：fb6bb97e3
- **日期**：2026-06-22 15:23:27 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Spark 3.5, 4.0: Throw on unsupported complex types in SparkValueConverter (#16925)
- **PR/Issue**：#16925

## 总体目的

这次提交是 #16924（提交 3925）的 backport，将同一个 bug 修复应用到 Spark 3.5 和 Spark 4.0 模块。原 bug 是 `SparkValueConverter.convertToSpark` 方法在遇到不支持的复杂类型（STRUCT、LIST、MAP）时使用 `return new UnsupportedOperationException(...)` 而非 `throw new UnsupportedOperationException(...)`，导致异常对象被当作返回值传递而非中断执行。

由于 Iceberg 维护多个 Spark 版本的并行分支（3.5、4.0、4.1），同一 bug 存在于所有分支中，因此需要在每个分支上分别修复。此提交将修复同步到 3.5 和 4.0，并对 4.1 分支的测试做了小幅重构以保持三个分支的测试代码一致。

## 如何达成设计目的

与 #16924 相同的方式：将 `return` 改为 `throw`，并新增 `testConvertToSparkComplexTypesThrow` 测试方法。此外，将 4.1 分支测试中原本使用 `Types.NestedField[]` 数组遍历的方式重构为使用 `List.of(struct, list, map)` 遍历 `Type` 的方式，使三个分支的测试实现保持一致。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkValueConverter.java` (+1/-1 lines)

**修改目的**：修复 Spark 3.5 中未抛出异常的 bug。

**工作逻辑**：将 STRUCT/LIST/MAP 分支的 `return new UnsupportedOperationException("Complex types currently not supported");` 改为 `throw new UnsupportedOperationException("Complex types currently not supported");`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkValueConverter.java` (+15/-0 lines)

**修改目的**：新增针对复杂类型转换抛出异常的测试。

**工作逻辑**：新增 `testConvertToSparkComplexTypesThrow` 测试方法，构造 STRUCT、LIST、MAP 三种复杂类型，使用 `List.of(struct, list, map)` 遍历并断言抛出 `UnsupportedOperationException`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkValueConverter.java` (+1/-1 lines)

**修改目的**：修复 Spark 4.0 中未抛出异常的 bug。

**工作逻辑**：同 Spark 3.5，将 `return` 改为 `throw`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestSparkValueConverter.java` (+15/-0 lines)

**修改目的**：新增针对复杂类型转换抛出异常的测试。

**工作逻辑**：同 Spark 3.5 的测试实现。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkValueConverter.java` (+5/-10 lines)

**修改目的**：重构 4.1 分支的测试实现以与其他分支保持一致。

**工作逻辑**：将原 4.1 测试中通过 `Types.NestedField.required(...)` 包装后再取 `.type()` 的方式，简化为直接使用 `List.of(struct, list, map)` 遍历 `Type`，减少不必要的 `NestedField` 包装，使三个分支的测试代码完全一致。

## 总结

这次提交是 #16924 的 backport，将 SparkValueConverter 的 `return` → `throw` 修复同步到 Spark 3.5 和 4.0 分支，并统一了三个分支的测试实现。这确保了所有受支持的 Spark 版本都不会再出现异常对象被当作返回值传递的问题。
