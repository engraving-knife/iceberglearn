# 提交 2303：Spark 3.4: Backport tests from #13070 to 3.4 (#13440)

## 提交信息

- **序号**：2303 / 4088
- **哈希**：8aa86db08fef9a23036075492b71bbcc2138afc4
- **短哈希**：8aa86db08
- **日期**：2025-07-01 13:14:34 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.4: Backport tests from #13070 to 3.4 (#13440)
- **PR/Issue**：#13440

## 总体目的

本提交将 PR #13070（Avro 行谱系继承支持）的测试部分回移植到 Spark 3.4 分支。PR #13070 为 Avro 读取器添加了行谱系继承功能，本提交确保 Spark 3.4 也有对应的测试覆盖。

需要注意的是，本提交只回移植了测试代码，不包括 #13070 中的核心实现代码（`ValueReaders.java` 的修改）。这是因为 Spark 3.4 可能已有或将以其他方式获得实现支持，此提交专注于确保测试覆盖。

## 如何达成设计目的

回移植包括两部分测试变更：

1. **`TestRowLevelOperationsWithLineage`**：扩展测试参数化，添加 Avro 格式 + SparkSessionCatalog 的测试组合，并移除此前限制只测试 Parquet 格式的 `@BeforeEach` 假设条件。
2. **`TestSparkAvroReader`**：重构测试以支持行谱系测试，包括使用 `DataWriter` 替代 `FileAppender`、传入常量映射、添加 `supportsRowLineage()` 方法。

## 修改详情

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRowLevelOperationsWithLineage.java` (+27/-6 lines)

**修改目的**：扩展行谱系测试以覆盖 Avro 格式。

**工作逻辑**：
- 移除 `@BeforeEach` 方法中的 `assumeThat(fileFormat).isEqualTo(FileFormat.PARQUET)` 限制。此前该方法还包含 `assumeThat(formatVersion).isGreaterThanOrEqualTo(3)`，但 Parquet 格式限制被移除后，formatVersion 假设也不需要了（因为参数化配置已经控制了 formatVersion）。
- 在 `parameters()` 方法中添加新的测试参数组：使用 `SparkSessionCatalog`、Hive 类型、Avro 格式、非向量化、RANGE 分布模式、DISTRIBUTED 规划模式、format version 3。
- 在 `latestSnapshot` 方法中添加 `table.refresh()` 调用，确保获取最新的表快照状态，避免缓存导致读取到旧快照。
- 添加 `SparkSessionCatalog` 的导入，移除不再使用的 `BeforeEach` 导入。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkAvroReader.java` (+33/-10 lines)

**修改目的**：重构 Avro 读取器测试以支持行谱系继承测试。

**工作逻辑**：
- 添加新的 `writeAndValidate` 重载方法，接收 `List<Record>` 参数，允许外部提供测试数据。
- 原有 `writeAndValidate` 方法改为调用新方法，使用 `RandomGenericData.generate` 生成测试数据。
- 写入方式从 `InMemoryOutputFile` + `FileAppender` 改为临时文件 + `DataWriter`。`DataWriter` 支持写入分区规范和元数据，这对于行谱系测试是必要的。
- 读取时使用 `SparkPlannedAvroReader.create(schema, ID_TO_CONSTANT)` 传入常量映射（包含 `firstRowId` 等元数据常量）。
- 验证使用 `GenericsHelpers.assertEqualsUnsafe` 替代 `TestHelpers.assertEqualsUnsafe`，支持传入常量映射和行索引。
- 新增 `supportsRowLineage()` 方法返回 `true`，启用 Avro 数据测试基类中的行谱系相关测试。

## 总结

本提交是 PR #13070 测试部分的 Spark 3.4 回移植。通过扩展行谱系测试覆盖 Avro 格式，并重构 Avro 读取器测试以支持行谱系常量映射，确保 Spark 3.4 分支对 Avro 行谱系继承有充分的测试覆盖。这体现了 Iceberg 项目在多版本维护中对测试一致性的重视。
