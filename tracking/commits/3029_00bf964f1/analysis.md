# 提交 3029：Spark: Order results to fix test flakiness with remote scan planning (#14894)

## 提交信息

- **序号**：3029 / 4088
- **哈希**：00bf964f1df2454b24f93bbe7d36278ec229fb48
- **短哈希**：00bf964f1
- **日期**：2025-12-19
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark: Order results to fix test flakiness with remote scan planning (#14894)
- **PR/Issue**：#14894

## 总体目的

本提交修复了在远程扫描计划（remote scan planning）启用后测试用例出现的 flakiness（不稳定性）问题。远程扫描计划将扫描任务（FileScanTask）的规划下推到服务端，服务端可能以不同于本地扫描计划的顺序返回文件扫描任务，导致 Spark 查询结果的行顺序不确定。而 `TestSelect` 中的时间旅行（time travel）相关测试用例（如 `testSnapshotInTableName`、`testSnapshotAtTimestamp`、`testVersionAsOf`、`testTagReference`、`testBranchReference`、`testTimestampAsOf` 等）在比较 expected 与 actual 结果时使用了 `assertEquals` 或 `containsExactly`，这些断言要求顺序完全一致，因此在结果顺序不确定时会随机失败。

本提交的解决方式是在所有受影响的查询语句中添加 `ORDER BY id` 子句，或在 DataFrame 读取后添加 `.orderBy("id")`，使结果按 `id` 列排序后再比较，从而消除因行顺序不确定导致的测试 flakiness。这种做法不改变测试的验证意图（验证时间旅行查询返回正确的行集合），仅消除顺序敏感性，是处理并行/分布式扫描结果顺序不确定性的标准做法。

## 如何达成设计目的

改动集中在单个测试文件 `TestSelect.java`，对约 10 个测试方法中的所有 `SELECT *` 查询语句统一添加 `ORDER BY id`，对使用 `DataFrameReader` 加载的 DataFrame 统一追加 `.orderBy("id")`。部分 `containsExactlyInAnyOrder` 的断言保持不变（因其本身不关心顺序）。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+53/-34 lines)

**修改目的**：为时间旅行相关测试添加结果排序，消除 flakiness。

**工作逻辑**：
修改覆盖以下测试方法，每个方法中将 expected 查询和 actual 查询都加上 `ORDER BY id`（或 `ORDER by id`，大小写略有不同但不影响语义），DataFrame 读取路径追加 `.orderBy("id")`：

- `testSnapshotInTableName()`：通过 `snapshot_id_` 前缀读取指定快照，expected 和 actual 查询及 DataFrame 均加排序。
- `testSnapshotAtTimestamp()`：通过 `at_timestamp_` 前缀读取指定时间戳快照，三处加排序。
- `testVersionAsOf()`：通过 `VERSION AS OF` 和 `FOR SYSTEM_VERSION AS OF` 语法及 DataFrame `versionAsOf` 选项，三处加排序。
- `testTagReference()`：通过 `VERSION AS OF 'test_tag'`、`FOR SYSTEM_VERSION AS OF 'test_tag'`、`tag_` 前缀及 DataFrame `tag` 选项，四处加排序。
- `testTagAndSnapshotIdWithSameName()`：两处 `VERSION AS OF` 查询加排序。
- `testBranchReference()`：通过 `VERSION AS OF 'test_branch'`、`FOR SYSTEM_VERSION AS OF 'test_branch'`、`branch_` 前缀及 DataFrame `branch` 选项，四处加排序。
- `testBranchSchema()`（推测方法名）：含 `containsExactly` 断言的三处查询加排序，含 `containsExactlyInAnyOrder` 的断言中 DataFrame 路径也加了 `.orderBy("id")`（虽不影响 `containsExactlyInAnyOrder` 语义但保持一致）。
- `testTimestampAsOf()`：通过 `TIMESTAMP AS OF`、`FOR SYSTEM_TIME AS OF` 及 DataFrame `timestampAsOf` 选项，五处加排序。

部分 SQL 语句因行长度限制进行了换行格式化调整（如 `sql("SELECT * FROM %s VERSION AS OF %s ORDER BY id", tableName, snapshotId)` 拆为多行）。

## 总结

本提交通过在时间旅行相关测试的所有结果比较查询中统一添加 `ORDER BY id`，消除了远程扫描计划下因文件扫描任务顺序不确定导致的测试 flakiness，使测试在远程扫描计划启用时稳定通过，属于测试质量改进。
