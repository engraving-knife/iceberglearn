# 提交 2395：Spark 4: Port tests to verify that dropped fields referenced in older partition specs are ignored (#13648)

## 提交信息

- **序号**：2395 / 4088
- **哈希**：badd36d5ef750c92132d3da0c96245b4f5898af2
- **短哈希**：badd36d5e
- **日期**：2025-07-23 15:22:52 -0600
- **作者**：Kevin Liu
- **提交说明**：Spark 4: Port tests to verify that dropped fields referenced in older partition specs are ignored (#13648)
- **PR/Issue**：#13648

## 总体目的

此提交向 Spark 4 分支移植了测试，用于验证当表的分区字段引用的源列被删除后，旧分区 spec 中对这些已删除字段的引用能被正确忽略，不会导致查询失败。

Iceberg 支持分区 spec 演进：可以添加和删除分区字段，也可以删除表中的列。当一列被删除时，如果该列在旧分区 spec 中被引用，查询引擎需要能正确处理这种情况——旧分区 spec 中引用已删除列的部分应被忽略，而不应导致错误。本提交通过覆盖多种分区转换类型（identity、year、month、day、truncate、bucket）来全面验证此行为。

## 如何达成设计目的

通过在 `TestAlterTablePartitionFields` 中新增 `runCreateAndDropPartitionField` 辅助方法和两个测试方法，覆盖以下场景：
1. 创建表并插入数据
2. 添加分区字段（使用特定转换类型）
3. 插入更多数据
4. 删除分区字段
5. 插入更多数据
6. 删除源列
7. 执行查询验证数据正确性

测试覆盖了时间戳列（col_ts）和长整型列（col_long）作为分区列的场景，以及多种分区转换函数。

## 修改详情

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAlterTablePartitionFields.java` (+46/-0 lines)

**修改目的**：新增分区字段源列删除后的查询正确性测试。

**工作逻辑**：`runCreateAndDropPartitionField` 方法执行完整的测试流程：创建包含 `col_int`、`col_ts`、`col_long` 三列的表，插入数据，添加分区字段，再插入数据，删除分区字段，再插入数据，最后删除源列。删除源列后执行带过滤条件的查询，验证返回结果正确。

`testDropPartitionAndSourceColumnLong` 测试删除 `col_ts` 列（分区字段基于 `col_ts` 的 identity/year/month/day 转换），验证 `col_long` 的查询结果。

`testDropPartitionAndSourceColumnTimestamp` 测试删除 `col_long` 列（分区字段基于 `col_long` 的 identity/truncate/bucket 转换），验证 `col_ts` 的查询结果。

## 总结

此提交是纯测试移植，验证了 Iceberg 分区 spec 演进中的一个边界场景：分区字段引用的源列被删除后，查询仍能正确工作。覆盖了多种分区转换类型和列数据类型，确保该行为的可靠性。
