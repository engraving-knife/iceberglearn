# 提交 3320：[Docs] Add Spark MERGE INTO fields in snapshot summary (#15390)

## 提交信息

- **序号**：3320 / 4088
- **哈希**：017e81300c02cdbd8b7586d5d447aecbda442a0f
- **短哈希**：017e81300
- **日期**：2026-02-26
- **作者**：Szehon Ho
- **提交说明**：[Docs] Add Spark MERGE INTO fields in snapshot summary (#15390)
- **PR/Issue**：#15390

## 总体目的

Iceberg 在每次提交后都会生成一个 snapshot summary，其中以键值对形式记录本次操作的统计信息（如新增/删除/更新文件数、记录数等）。Spark 4.1 起，在执行 `MERGE INTO` 后会向 snapshot summary 中写入一组以 `spark.merge-into.` 为前缀的行级统计字段，用以反映此次合并对目标表行级的影响：包括被复制（未匹配任何动作）、删除、更新、插入的目标行数，以及按 `MATCHED` 与 `NOT MATCHED BY SOURCE` 子句细分的更新/删除行数。这些指标对用户审计、监控 MERGE 行为与排查数据变更规模很有价值。

然而在这之前，文档 `spark-writes.md` 的 `MERGE INTO` 章节只描述了语法（`WHEN MATCHED`、`WHEN NOT MATCHED`、`WHEN NOT MATCHED BY SOURCE` 等），并未说明提交后 snapshot summary 中会出现哪些字段及其含义。本提交补上这一缺口，在 MERGE INTO 章节末尾新增"Snapshot summary"小节，以表格形式列出全部 8 个字段及描述，并明确这些字段仅在 Spark 4.1 及更高版本可用、当值未知时字段会被省略。这使用户能够直接从文档获知可观察的合并指标，无需翻阅代码或测试。

## 如何达成设计目的

改动只涉及文档文件 `docs/docs/spark-writes.md`，在 `MERGE INTO` 语法说明与示例之后、`INSERT OVERWRITE` 章节之前插入一个 `#### Snapshot summary` 子节。该子节先用一段文字说明字段语义（值为非负计数的字符串形式，未知时省略，且仅在 Spark 4.1+ 可用，用 mkdocs 的 `!!! info` 提示块标注），随后以 markdown 表格列出 8 个字段名与描述。文档列出的字段与代码侧 `spark/v4.1/spark-extensions` 中 `TestMergeMetrics` 测试断言的字段完全对应，确保文档与实现一致。

## 修改详情

### `docs/docs/spark-writes.md` (+18/-0 lines)

**修改目的**：在 MERGE INTO 文档中补充 snapshot summary 字段说明。

**工作逻辑**：
在 `WHEN NOT MATCHED BY SOURCE` 示例之后新增 `#### Snapshot summary` 子节。首段说明：`MERGE INTO` 提交后 snapshot summary 可能包含下列字段，每个值是非负计数的字符串形式，当值未知（如 Spark 未上报）时该字段被省略。随后以 `!!! info` 提示块标注"仅在 Spark 4.1 及更高版本可用"。接着用 markdown 表格列出 8 个字段：

- `spark.merge-into.num-target-rows-copied`：未匹配任何动作、原样复制的目标行数
- `spark.merge-into.num-target-rows-deleted`：被删除的目标行数
- `spark.merge-into.num-target-rows-updated`：被更新的目标行数
- `spark.merge-into.num-target-rows-inserted`：被插入的目标行数
- `spark.merge-into.num-target-rows-matched-updated`：由 `MATCHED` 子句更新的行数
- `spark.merge-into.num-target-rows-matched-deleted`：由 `MATCHED` 子句删除的行数
- `spark.merge-into.num-target-rows-not-matched-by-source-updated`：由 `NOT MATCHED BY SOURCE` 子句更新的行数
- `spark.merge-into.num-target-rows-not-matched-by-source-deleted`：由 `NOT MATCHED BY SOURCE` 子句删除的行数

这组字段与 Spark 4.1 MERGE INTO 实现产出的 summary 属性一一对应，便于用户通过查询 snapshot summary 获取合并的行级影响统计。

## 总结

本次提交为 Spark MERGE INTO 文档补充了 snapshot summary 字段说明，列出 Spark 4.1+ 提交后会产出的 8 个行级统计字段及其含义。这填补了文档空白，使用户能直接从文档了解可观测的合并指标，与代码侧测试保持一致，提升了 MERGE INTO 行为的可观测性与文档完整性。
