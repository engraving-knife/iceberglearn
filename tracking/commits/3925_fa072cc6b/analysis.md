# 提交 3925：Spark 4.1: Throw on unsupported complex types in SparkValueConverter (#16924)

## 提交信息

- **序号**：3925 / 4088
- **哈希**：fa072cc6b7ca3ead3e4ef7e09bd01d00c2988c88
- **短哈希**：fa072cc6b
- **日期**：2026-06-22 10:48:20 +0200
- **作者**：Eunbin Son
- **提交说明**：Spark 4.1: Throw on unsupported complex types in SparkValueConverter (#16924)
- **PR/Issue**：#16924

## 总体目的

这次提交修复了 Spark 4.1 模块中 `SparkValueConverter` 的一个明显 bug。当转换器遇到不支持的结构化类型（STRUCT、LIST、MAP）时，原本的代码使用 `return new UnsupportedOperationException(...)` 而非 `throw new UnsupportedOperationException(...)`。

这是一个典型的"创建了异常对象但未抛出"的缺陷：`return` 语句会将异常对象作为方法的返回值传递给调用方，而非中断执行流程。由于 `convertToSpark` 方法的返回类型是 `Object`，编译器无法在编译期发现这个问题。结果是调用方会收到一个异常对象作为"转换结果"值，而不是预期的失败中断。这会导致下游处理在不知情的情况下使用异常对象作为数据值，可能引发难以追踪的运行时错误或数据污染。

该提交将 `return` 改为 `throw`，使异常行为与方法的 `default` 分支（也是 `throw new UnsupportedOperationException`）保持一致，并补充了对应的单元测试。

## 如何达成设计目的

通过修改 `SparkValueConverter.convertToSpark` 方法的 STRUCT/LIST/MAP 分支，将 `return` 替换为 `throw`，使异常真正被抛出。同时在 `TestSparkValueConverter` 中新增 `testConvertToSparkComplexTypesThrow` 测试方法，使用 AssertJ 的 `assertThatThrownBy` 断言三种复杂类型（struct、list、map）在转换时会抛出 `UnsupportedOperationException` 且消息包含 "Complex types currently not supported"。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkValueConverter.java` (+1/-1 lines)

**修改目的**：修复未抛出异常的 bug。

**工作逻辑**：
将 STRUCT/LIST/MAP 分支的 `return new UnsupportedOperationException("Complex types currently not supported");` 改为 `throw new UnsupportedOperationException("Complex types currently not supported");`。这样当转换器遇到结构化类型时会立即抛出异常，与 default 分支的行为一致，避免异常对象被当作正常返回值传递。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkValueConverter.java` (+20/-0 lines)

**修改目的**：新增针对复杂类型转换抛出异常的测试。

**工作逻辑**：
新增 `testConvertToSparkComplexTypesThrow` 测试方法，构造 STRUCT、LIST、MAP 三种复杂类型，分别调用 `SparkValueConverter.convertToSpark(field.type(), "unused")`，使用 `assertThatThrownBy` 断言抛出 `UnsupportedOperationException` 且消息包含 "Complex types currently not supported"。这确保未来不会再次回归到 `return` 形式。

## 总结

这次提交修复了一个隐蔽但严重的 bug：异常对象被当作返回值传递而非抛出。通过将 `return` 改为 `throw` 并补充测试，确保 `SparkValueConverter` 在遇到不支持的复杂类型时能正确中断执行流程，避免下游数据污染。该修复在 #16925 中被 backport 到 Spark 3.5 和 4.0。
