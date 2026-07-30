# 提交 2385：Test: Simplify collection and optional assertions (#13613)

## 提交信息

- **序号**：2385 / 4088
- **哈希**：13871b2502fd8e6d271e5d39638eb05b5503887f
- **短哈希**：13871b250
- **日期**：2025-07-23 07:20:33 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Test: Simplify collection and optional assertions (#13613)
- **PR/Issue**：#13613

## 总体目的

本提交对项目中大量测试文件的集合和 Optional 断言进行简化，使用 AssertJ 提供的更简洁的流式 API 替代冗长的传统断言写法。这是代码质量改进工作，旨在统一和简化测试代码风格，提高可读性和可维护性。

在测试代码中，常见的模式是使用 `assertThat(collection).hasSize(n)` 后跟多个 `assertThat(collection).contains(element)` 或 `assertThat(collection.get(0)).isEqualTo(value)` 等冗长的断言。AssertJ 提供了更强大的流式 API，如 `assertThat(collection).hasSize(n).containsExactly(element1, element2)` 可以用一行代码完成多个断言，使测试代码更简洁。类似地，Optional 类型的断言也有更简洁的写法，如 `assertThat(optional).isPresent().get().isEqualTo(value)` 替代 `assertTrue(optional.isPresent()); assertThat(optional.get()).isEqualTo(value)`。

## 如何达成设计目的

通过批量修改测试文件中的断言写法，将冗长的断言替换为 AssertJ 的简洁流式 API。修改涉及 44 个测试文件，跨越多个模块（core、aws、azure、flink、kafka-connect、parquet、spark 等），共减少 7 行代码（146 行新增，153 行删除）。

## 修改详情

### 44 个测试文件 (+146/-153 lines)

**修改目的**：简化集合和 Optional 断言，使用 AssertJ 流式 API。

**工作逻辑**：对以下模块的测试文件进行了断言简化：
- **core 模块**：`TestEvaluator.java`、`DataTableScanTestBase.java`、`TestMetadataTableScans.java`、`TestRowLineageAssignment.java`、`TestSchemaConversions.java`、`TestCachingCatalog.java`、`TestMergingMetrics.java`、`TestGenericData.java`、`TestSnapshotDeltaLakeTable.java` 等
- **aws 模块**：`TestGlueCatalogTable.java`、`TestS3FileIOProperties.java`
- **azure 模块**：`TestADLSLocation.java`
- **flink 模块**（v1.19/v1.20/v2.0）：`TestFlinkParquetReader.java`、`TestRowDataProjection.java`、`TestBucketPartitionerFlinkIcebergSink.java`、`TestDynamicIcebergSink.java`、`TestDynamicWriter.java`、`TestEvolveSchemaVisitor.java`、`TestLRUCache.java`、`TestMapRangePartitioner.java`
- **kafka-connect 模块**：`TestJsonToMapTransform.java`、`TestSinkWriter.java`
- **parquet 模块**：`TestVariantMetrics.java`、`TestVariantWriters.java`
- **spark 模块**（v3.4/v3.5/v4.0）：`TestSparkParquetReader.java`、`DataFrameWriteTestBase.java`

主要简化模式包括：
- 多个 `assertThat(list).contains(x)` 替换为单个 `assertThat(list).containsExactly(a, b, c)`
- `assertTrue(opt.isPresent()); assertThat(opt.get()).isEqualTo(v)` 替换为 `assertThat(opt).isPresent().get().isEqualTo(v)`
- `assertThat(list.size()).isEqualTo(n)` 替换为 `assertThat(list).hasSize(n)`

## 总结

本提交是一个大规模的测试代码质量改进，对 44 个测试文件中的集合和 Optional 断言进行简化，使用 AssertJ 的流式 API 替代冗长的传统写法。虽然每个文件的修改量不大，但总体影响范围广，覆盖了 core、aws、azure、flink、kafka-connect、parquet 和 spark 等多个模块。该改进提升了测试代码的可读性和一致性，不改变任何测试逻辑和断言语义。
