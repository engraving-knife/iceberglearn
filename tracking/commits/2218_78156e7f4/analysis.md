# 提交 2218：Spark 4.0: Add a test for DataFrame API support for MERGE INTO (#13230)

## 提交信息

- **序号**：2218 / 4088
- **哈希**：78156e7f4c829880ad1c62f35588c0a57d5ff18b
- **短哈希**：78156e7f4
- **日期**：2025-06-05 18:44:49 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Spark 4.0: Add a test for DataFrame API support for MERGE INTO (#13230)
- **PR/Issue**：#13230

## 总体目的

这个提交为 Spark 4.0 新增的 DataFrame API MERGE INTO 功能添加测试用例。Spark 4.0 通过 `DataFrameWriterV2` API 增加了使用 DataFrame 进行 MERGE INTO 操作的能力（如 `mergeInto`、`whenMatched`、`whenNotMatched` 等链式调用）。此前 Iceberg 的 TestMerge 测试类仅测试 SQL 语法的 MERGE INTO，没有覆盖 DataFrame API 方式。本提交通过重构现有的 `testMergeWithAllClauses` 测试，提取出公共的 setup 和 verify 方法，然后新增 `testMergeWithAllClausesUsingDataFrameAPI` 测试，使用 DataFrame API 执行完全等价的 MERGE 操作并验证结果一致。这确保了 Iceberg 在 Spark 4.0 下对 DataFrame API MERGE INTO 的支持是正确的，与 SQL 方式行为一致。

## 如何达成设计目的

- 将原 `testMergeWithAllClauses` 测试方法中的表数据准备逻辑提取为 `setupMergeWithAllClauses` 私有方法。
- 将结果验证逻辑提取为 `verifyMergeWithAllClauses` 私有方法。
- 原 `testMergeWithAllClauses` 调用 setup 后执行 SQL MERGE，再调用 verify。
- 新增 `testMergeWithAllClausesUsingDataFrameAPI` 测试：调用相同的 setup，使用 DataFrame API（`mergeInto`、`whenMatched().updateAll()`、`whenMatched().delete()`、`whenNotMatched().insertAll()`、`whenNotMatchedBySource().update()`、`whenNotMatchedBySource().delete()`、`.merge()`）执行等价的 MERGE 操作，再调用相同的 verify 验证结果。

## 修改详情

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java` (修改, +42/-12 lines)

**修改目的**：新增 DataFrame API MERGE INTO 测试并重构现有测试。

**工作逻辑**：
- 新增导入 `col` 函数和 `mapAsScalaMapConverter`（用于 Java Map 转 Scala Map）。
- 提取 `setupMergeWithAllClauses()`：创建目标表（4条记录）和 source 表（3条记录）。
- 提取 `verifyMergeWithAllClauses()`：验证 MERGE 后结果为 3 条记录（id=1 更新、id=3 更新为 invalid、id=5 新插入；id=2 和 id=4 被删除）。
- 原 `testMergeWithAllClauses`：setup → SQL MERGE（包含 WHEN MATCHED UPDATE/DELETE、WHEN NOT MATCHED INSERT、WHEN NOT MATCHED BY SOURCE UPDATE/DELETE 所有子句）→ verify。
- 新增 `testMergeWithAllClausesUsingDataFrameAPI`：setup → DataFrame API MERGE（使用 `spark.table("source").mergeInto()` 链式调用，设置 ON 条件和各 WHEN 子句，update 子句使用 `scala.collection.immutable.Map.from` 包装 Java Map）→ verify。两个测试验证完全相同的结果，确保 SQL 和 DataFrame API 行为一致。

## 总结

该提交是测试类改动，为 Spark 4.0 DataFrame API 的 MERGE INTO 功能添加了等价测试。通过重构提取公共 setup/verify 方法，避免代码重复，同时确保 DataFrame API 与 SQL 方式产生一致的 MERGE 结果。测试覆盖了 MERGE 的所有子句（matched update/delete、not matched insert、not matched by source update/delete），验证全面。
