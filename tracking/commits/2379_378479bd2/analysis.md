# 提交 2379：Spark: Unimplement WithAssertions from test classes (#13610)

## 提交信息

- **序号**：2379 / 4088
- **哈希**：378479bd21ddc5de7c08dcfec1595196dd8f4a78
- **短哈希**：378479bd2
- **日期**：2025-07-21 13:46:33 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Unimplement WithAssertions from test classes (#13610)
- **PR/Issue**：#13610

## 总体目的

本提交将 Spark 测试类 `TestVectorizedOrcDataReader` 中的 `WithAssertions` 接口实现移除，改为直接使用 AssertJ 的静态导入。`WithAssertions` 是 AssertJ 提供的一个接口，实现该接口的测试类可以通过 `this` 引用调用各种断言方法（如 `assertThat` 等），这是一种面向对象的断言使用方式。

然而，Iceberg 项目中更推荐使用静态导入方式（`import static org.assertj.core.api.Assertions.assertThat`），这也是大多数测试类采用的模式。移除 `WithAssertions` 接口实现可以统一项目中的断言使用风格，减少不必要的接口依赖，使测试类更简洁。

## 如何达成设计目的

设计思路是将 `WithAssertions` 接口的实现替换为直接静态导入 `assertThat` 方法。关键设计点如下：

1. **移除接口实现**：将类声明从 `implements WithAssertions` 改为不实现任何接口。
2. **替换导入方式**：移除 `import org.assertj.core.api.WithAssertions` 导入，添加 `import static org.assertj.core.api.Assertions.assertThat` 静态导入。
3. **保持功能不变**：测试类中使用的 `assertThat` 方法调用保持不变，因为静态导入提供了相同的方法。

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestVectorizedOrcDataReader.java` (+3/-2 lines)

**修改目的**：移除 WithAssertions 接口实现，改用静态导入。

**工作逻辑**：添加 `import static org.assertj.core.api.Assertions.assertThat` 静态导入，移除 `import org.assertj.core.api.WithAssertions` 导入，将类声明从 `public class TestVectorizedOrcDataReader implements WithAssertions` 改为 `public class TestVectorizedOrcDataReader`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestVectorizedOrcDataReader.java` (+3/-2 lines)

**修改目的**：对 Spark 3.5 版本进行相同的修改。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestVectorizedOrcDataReader.java` (+3/-2 lines)

**修改目的**：对 Spark 4.0 版本进行相同的修改。

## 总结

本提交是一个代码风格统一改进，将三个 Spark 版本（3.4、3.5、4.0）的 TestVectorizedOrcDataReader 测试类中的 `WithAssertions` 接口实现移除，改为使用静态导入 `assertThat` 方法。修改涉及 9 行新增和 6 行删除，不改变任何测试逻辑，仅统一了断言的使用方式。这是代码卫生（code hygiene）改进，使测试风格与项目整体保持一致。
