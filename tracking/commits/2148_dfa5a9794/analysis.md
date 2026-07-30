# 提交 2148：Spark 4: Migrate tests implementing SparkCatalogTestBase to Junit5

## 提交信息

- **序号**：2148 / 4088
- **哈希**：dfa5a979437783877e0f4aedfb62943d4cfcdf8e
- **短哈希**：dfa5a9794
- **日期**：2025-05-20 12:58:15 +0900
- **作者**：Tom Tanaka
- **提交说明**：Spark 4: Migrate tests implementing SparkCatalogTestBase to Junit5 (#13096)
- **PR/Issue**：#13096

## 总体目的

这个提交将 Spark 4 中继承 SparkCatalogTestBase（实际为 CatalogTestBase）的测试类迁移到 JUnit5。作为项目从 JUnit4 到 JUnit5 迁移工作的一部分，需要将这些测试类从 JUnit4 的参数化测试机制迁移到 JUnit5 的 ParameterizedTestExtension 机制。这些测试类原来使用 JUnit4 的参数化测试运行器，现在通过添加 @ExtendWith(ParameterizedTestExtension.class) 注解和必要的导入来完成迁移。

## 如何达成设计目的

1. 在四个继承 CatalogTestBase 的测试类中添加 @ExtendWith(ParameterizedTestExtension.class) 注解。
2. 添加必要的导入语句（ParameterizedTestExtension 和 ExtendWith）。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestRequiredDistributionAndOrdering.java` (修改, +3/-0 lines)

**修改目的**：迁移到 JUnit5 参数化测试扩展。

**工作逻辑**：添加 `import org.apache.iceberg.ParameterizedTestExtension;` 和 `import org.junit.jupiter.api.extension.ExtendWith;` 导入，并在类上添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkCatalogHadoopOverrides.java` (修改, +3/-0 lines)

**修改目的**：同上，迁移到 JUnit5。

**工作逻辑**：与 TestRequiredDistributionAndOrdering 相同的修改模式。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkStagedScan.java` (修改, +3/-0 lines)

**修改目的**：同上，迁移到 JUnit5。

**工作逻辑**：与上述文件相同的修改模式。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkTable.java` (修改, +3/-0 lines)

**修改目的**：同上，迁移到 JUnit5。

**工作逻辑**：与上述文件相同的修改模式。

## 总结

这个提交是 JUnit4 到 JUnit5 迁移工作的一部分，将 Spark 4 中四个继承 CatalogTestBase 的测试类迁移到使用 JUnit5 的 ParameterizedTestExtension。修改模式统一，每个文件添加相同的注解和导入，属于机械性迁移。
