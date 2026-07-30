# 提交 0242：Spark 3.5: Fix testReplacePartitionField for rewriting manifests (#9250)

## 提交信息

- **序号**：0242 / 4088
- **哈希**：504c13428cd4ad4d343c644736b957d7f2653b37
- **短哈希**：504c13428
- **日期**：2023-12-08 09:18:56 -0800
- **作者**：bknbkn
- **提交说明**：Spark 3.5: Fix testReplacePartitionField for rewriting manifests (#9250)
- **PR/Issue**：#9250

## 总体目的

Spark 3.5 的 `TestRewriteManifestsProcedure#testReplacePartitionField` 测试存在两个问题，导致它不能稳定地、有意义地验证 manifest 重写流程。一是测试只 insert 了一行数据，然后调用 `rewrite_manifests` 但完全没有检查过程的返回结果，使得"是否真的发生了重写"这一关键事实未被断言；二是查询断言里 `SELECT * FROM ... WHERE ts < current_timestamp()` 没有 `ORDER BY`，而 `current_timestamp()` 在分布式执行下不同 task 可能取到略有不同的时间点，再加上无序返回，会让结果行的顺序不确定，造成测试偶发失败。

本提交针对 Spark 3.5 修复这两个问题：插入两行数据使重写有实际意义，并断言 `rewrite_manifests` 的返回值为 `row(2, 1)`（重写 2 个 manifest、新增 1 个），同时对查询加上 `order by 1 asc` 保证结果顺序确定。这是一个纯测试修复，不改动产品代码，目的是提升测试的可信度与稳定性。

## 如何达成设计目的

通过在测试中增加第二行 insert、对 `rewrite_manifests` 返回值做断言、对断言用的 SELECT 加 `order by 1 asc` 三处修改，让测试既覆盖了"重写确实发生"这一行为，又消除了顺序不确定带来的 flaky 风险。改动仅限于 Spark 3.5 的测试文件。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteManifestsProcedure.java`

**修改目的**：修复 `testReplacePartitionField` 测试的覆盖不足与顺序不确定性。

**工作逻辑**：

- 在第一行 `INSERT INTO ... VALUES (1, CAST('2022-01-01 10:00:00' AS TIMESTAMP), CAST('2022-01-01' AS DATE))` 之后，新增第二行 `INSERT INTO ... VALUES (2, CAST('2022-01-01 11:00:00' AS TIMESTAMP), CAST('2022-01-01' AS DATE))`，使表中有两行数据，从而后续 `rewrite_manifests` 真正有内容可重写。
- 把 `rewrite_manifests` 调用的结果捕获到 `List<Object[]> output`，并新增 `assertEquals("Procedure output must match", ImmutableList.of(row(2, 1)), output)`：`row(2, 1)` 表示 rewritten_count=2、added_count=1，这正好对应"把两行 insert 产生的两个 manifest 合并为一个新 manifest"的语义。这是该测试首次真正断言重写结果。
- 把重写前后的两条 `SELECT * FROM %s WHERE ts < current_timestamp()` 查询都加上 `order by 1 asc`，期望结果也由单行扩展为两行（`row(1, ...)` 与 `row(2, ...)`），从而消除顺序不确定性并验证两行数据在重写后仍然完整可见。

## 小结

本提交通过补全数据、断言重写返回值、加排序，修复了 Spark 3.5 上 `testReplacePartitionField` 测试覆盖不足和顺序不确定的问题，使该测试能稳定地验证 manifest 重写的正确性。
