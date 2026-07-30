# 提交 2331：Spark 4.0: Port Avro lineage reader test changes from #13070 (#13496)

## 提交信息

- **序号**：2331 / 4088
- **哈希**：0d753dc6f6459553069867c4deb29ea48771fc95
- **短哈希**：0d753dc6f
- **日期**：2025-07-09 08:34:28 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 4.0: Port Avro lineage reader test changes from #13070 (#13496)
- **PR/Issue**：#13496

## 总体目的

本提交将 PR #13070 中关于 Avro lineage reader 测试的变更移植（port）到 Spark 4.0 模块。这是 Iceberg 行级血缘（row lineage）功能在 Spark 4.0 上的测试适配工作。

Iceberg 的行级血缘功能允许追踪数据行的来源和变更历史。PR #13070 在主分支上对 Avro 数据读取器测试进行了修改以支持行级血缘，但这些变更需要同步到 Spark 4.0 模块（`spark/v4.0/`），因为 Iceberg 维护了多个 Spark 版本的并行模块（3.3、3.4、3.5、4.0），各模块的测试代码需要保持一致。

此次移植的核心变更是让 `TestSparkAvroReader` 使用 `DataWriter` 写入带有分区规范和行级血缘信息的数据，并通过 `SparkPlannedAvroReader` 的带 ID 到常量映射的创建方法来读取，从而验证行级血缘在 Avro 格式下的正确读写。

## 如何达成设计目的

设计思路是修改 `TestSparkAvroReader` 的 `writeAndValidate` 方法，使其支持行级血缘测试，同时保持向后兼容。

关键设计点：
1. 使用 `Avro.writeData()` 替代 `Avro.write()`，创建 `DataWriter` 而非 `FileAppender`，以便写入带有元信息（如 spec、行级血缘 ID）的数据文件。
2. 读取时使用 `SparkPlannedAvroReader.create(schema, ID_TO_CONSTANT)` 替代 `SparkPlannedAvroReader.create(schema)`，传入 ID 到常量的映射。
3. 新增 `writeAndValidate` 重载方法，允许传入预生成的记录列表，便于行级血缘测试场景使用。
4. 通过 `supportsRowLineage()` 返回 `true` 声明该测试支持行级血缘。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkAvroReader.java` (+30/-13 lines)

**修改目的**：适配行级血缘功能，更新 Avro 读写测试。

**工作逻辑**：

1. **导入变更**：移除了 `org.apache.avro.generic.GenericData.Record` 和 `FileAppender`，新增 `PartitionSpec`、`RandomGenericData`、`DataWriter` 和 `Record`（iceberg.data 包）。

2. **`writeAndValidate` 方法重写**：新增三参数版本 `writeAndValidate(Schema, Schema, List<Record>)`，核心逻辑：
   - 使用 `Avro.writeData()` 创建 `DataWriter`，配置 `createWriterFunc`、`schema` 和 `PartitionSpec.unpartitioned()`。
   - 读取时使用 `SparkPlannedAvroReader.create(schema, ID_TO_CONSTANT)` 创建读取器。
   - 比较时使用 `GenericsHelpers.assertEqualsUnsafe` 替代 `TestHelpers.assertEqualsUnsafe`，传入 `ID_TO_CONSTANT` 和行索引。

3. **原两参数方法委托**：原 `writeAndValidate(Schema, Schema)` 保留，委托给三参数版本，使用 `RandomGenericData.generate` 生成测试数据。

4. **`supportsRowLineage()`**：重写返回 `true`，声明 Spark 4.0 Avro 读取器支持行级血缘。

## 总结

本提交是 Spark 4.0 模块的测试移植工作，将行级血缘功能在 Avro 读取器测试中的变更同步到 v4.0 分支。这确保了 Spark 4.0 用户在使用 Avro 格式时能正确使用行级血缘功能，并保持各 Spark 版本模块间测试的一致性。
