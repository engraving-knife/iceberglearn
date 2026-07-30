# 提交 2266：Spark-3.5, 4.0: Add unit tests for ColumnarBatchUtil (#12275)

## 提交信息

- **序号**：2266 / 4088
- **哈希**：b7154119f97870608429d5a9950aaaad6d2a0276
- **短哈希**：b7154119f
- **日期**：2025-06-24 11:46:23 -0700
- **作者**：Anurag Mantripragada
- **提交说明**：Spark-3.5, 4.0: Add unit tests for ColumnarBatchUtil
- **PR/Issue**：#12275

## 总体目的

本提交为 Spark 3.5 和 4.0 的 `ColumnarBatchUtil` 类添加了全面的单元测试。`ColumnarBatchUtil` 是 Iceberg Spark 模块中的一个工具类，负责在向量化读取（vectorized read）场景下处理列式批处理数据，包括行 ID 映射（rowIdMapping）、行 ID 映射带等值删除（rowIdMapping with equality deletes）、列向量构建等操作。这些操作涉及删除过滤（delete filter）、位置删除索引（position delete index）等复杂逻辑，对数据正确性至关重要。

在此提交之前，`ColumnarBatchUtil` 缺少专门的单元测试，其逻辑的正确性主要依赖于端到端的集成测试。本提交通过使用 Mockito 模拟 `ColumnVector`、`DeleteFilter`、`PositionDeleteIndex` 等依赖，针对各种场景编写了细粒度的单元测试，提高了测试覆盖率和代码质量保证。

## 如何达成设计目的

- 新增 `TestColumnarBatchUtil` 测试类，覆盖 Spark 3.5 和 4.0 两个模块。
- 使用 Mockito 模拟底层依赖（ColumnVector、DeleteFilter 等），隔离测试 ColumnarBatchUtil 的逻辑。
- 测试覆盖的主要场景包括：无删除的行 ID 映射、有位置删除的行 ID 映射、等值删除场景、列向量构建等。
- 同时重构了一些现有的 Parquet 向量化读取测试，调整测试基类继承关系。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/TestColumnarBatchUtil.java` (新增, +302/0 lines)

**修改目的**：为 ColumnarBatchUtil 添加全面的单元测试。

**工作逻辑**：测试类使用 Mockito 创建模拟的 `ColumnVector` 数组和 `DeleteFilter`。核心测试方法包括：
- `testBuildRowIdMappingNoDeletes()`：验证无删除行时的行 ID 映射构建，映射应为连续的 0 到 N。
- `testBuildRowIdMappingWithDeletes()`：验证有位置删除时的行 ID 映射，被删除的行对应 -1，其余行重新编号。
- 其他测试覆盖等值删除、列向量构建等场景。
- `@BeforeEach` 方法中初始化模拟对象，每个测试用例配置不同的删除场景并验证结果。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/TestColumnarBatchUtil.java` (新增, +302/0 lines)

**修改目的**：为 Spark 4.0 添加相同的单元测试。

### 测试文件重构 (各 +2/-2 lines)

- `TestParquetDictionaryEncodedVectorizedReads.java`
- `TestParquetDictionaryFallbackToPlainEncodingVectorizedReads.java`
- `TestParquetVectorizedReads.java`（从 `spark/data` 移动到 `spark/data/vectorized` 包）

**修改目的**：调整测试类的包路径和继承关系，使向量化读取相关测试统一位于 `vectorized` 包下。

## 总结

本提交为 Spark 3.5 和 4.0 的 `ColumnarBatchUtil` 工具类添加了 302 行的全面单元测试，覆盖行 ID 映射、删除过滤等核心向量化读取逻辑。通过使用 Mockito 进行依赖隔离，测试能够精确验证各种删除场景下的正确行为。同时整理了向量化读取测试的包结构。这是一个纯测试改进提交，不修改任何生产代码，提升了代码质量保证。
