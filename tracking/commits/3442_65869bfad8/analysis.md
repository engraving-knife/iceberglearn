# 提交 3442：Spark 3.4, 3.5: Order results to fix flakiness with remote planning (#15725)

## 提交信息

- **序号**：3442 / 4088
- **哈希**：65869bfad87bc2ef8c3eaecdd02bc3a1506460a5
- **短哈希**：65869bfad8
- **日期**：2026-03-23 07:45:32 +0100
- **作者**：Russell Spitzer
- **提交说明**：Spark 3.4, 3.5: Order results to fix flakiness with remote planning (#15725)
- **PR/Issue**：#15725

## 总体目的

修复 Spark 3.4 和 3.5 测试中因远程规划（remote planning）导致的测试不稳定（flakiness）问题。当使用远程规划时，查询结果的顺序可能不确定，导致测试断言失败。通过对结果排序来消除这种不稳定性。

## 如何达成设计目的

- 在时间旅行（time travel）查询测试中，对 DataFrame 的结果按 `id` 列排序
- 确保无论底层执行计划如何，测试结果的顺序都是确定的
- 在 Spark 3.4 和 3.5 两个版本的测试中应用相同的修复

## 修改详情

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+2/-1 lines)

**修改目的**：修复 Spark 3.4 中时间旅行查询测试的不稳定性。

**工作逻辑**：
- 在通过 `TIMESTAMP_AS_OF` 选项读取 DataFrame 后，添加 `.orderBy("id")` 调用
- 这样在 `collectAsList()` 收集结果时，结果按 id 排序
- 与预期结果比较时不会因顺序差异而失败

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+2/-1 lines)

**修改目的**：修复 Spark 3.5 中相同的时间旅行查询测试不稳定性。

**工作逻辑**：
- 与 Spark 3.4 完全相同的修复方式
- 在 DataFrame 读取后添加 `.orderBy("id")` 调用

## 总结

该提交修复了 Spark 3.4 和 3.5 中 TestSelect 测试的不稳定性问题。由于远程规划可能导致查询结果顺序不确定，通过在时间旅行查询测试中添加 `orderBy("id")` 来确保结果顺序确定，从而消除测试 flakiness。
