# 提交 3020：Docs: Add schema selection example for time travel queries (#14825)

## 提交信息

- **序号**：3020 / 4088
- **哈希**：d5bfcaf7ca518d70a4cb47167c948a4a367df9e3
- **短哈希**：d5bfcaf7c
- **日期**：2025-12-16
- **作者**：Vamsi Krishna
- **提交说明**：Docs: Add schema selection example for time travel queries (#14825)
- **PR/Issue**：#14825

## 总体目的

本提交为 Iceberg Spark 查询文档的"Schema selection in time travel queries"（时间旅行查询中的 schema 选择）章节补充一个完整的可运行示例。此前的文档已经简要说明了不同类型的时间旅行查询会使用不同的 schema：通过 snapshot ID 或 timestamp 查询时使用快照对应的 schema，通过 branch 查询时使用表当前 schema，通过 tag 查询时使用 tag 所引用快照的 schema。然而这些说明仅以 SQL 注释形式给出，缺乏可操作的示例，用户难以直观理解这一微妙差异的实际效果。

时间旅行查询的 schema 选择是 Iceberg 中一个容易混淆的特性。当一个表在演进过程中添加了新列时，查询旧快照时哪些列可见、新列的值是什么（NULL 还是报错），取决于查询方式。具体而言：`VERSION AS OF <snapshot_id>` 和 `TIMESTAMP AS OF` 使用快照当时的 schema（新列不存在）；`VERSION AS OF <branch>` 使用表当前 schema（新列可见但旧行对应值为 NULL）；`VERSION AS OF <tag>` 使用 tag 所绑定快照的 schema（同 snapshot ID 查询）。

这一差异对用户编写正确的查询至关重要。例如，如果用户期望查询 branch 时看到旧 schema 的列结构，可能会遇到意外结果——实际上 branch 查询会返回当前 schema 的所有列，旧数据行中新增列的值为 NULL。本提交通过一个完整的端到端示例——从建表、插入数据、添加列、创建 branch 和 tag，到执行各类时间旅行查询——清晰展示了每种查询的 schema 选择行为和结果差异。

## 如何达成设计目的

在 `docs/docs/spark-queries.md` 的"Schema selection in time travel queries"章节末尾（紧跟已有的简短 SQL 注释示例之后）新增一个完整的端到端示例。示例构造了一个名为 `prod.db.orders` 的表，初始 schema 为 `(id, status)`，写入快照 S1；然后添加 `total` 列形成快照 S2；接着分别创建指向 S1 的 branch 和 tag，并演示四种查询方式的结果差异。所有代码块均使用 Spark SQL 语法，可直接在 Spark Shell 中运行验证。

## 修改详情

### `docs/docs/spark-queries.md` (+74/-0 lines)

**修改目的**：为时间旅行查询的 schema 选择行为提供完整的可运行示例。

**工作逻辑**：
新增的 74 行文档以"consider a table that evolves its schema over time"开头，构造了以下场景：

1. **建表与初始快照 S1**：创建 `orders` 表，schema 为 `(id BIGINT, status STRING)`，插入 `(1, 'NEW'), (2, 'PAID')`，记录 S1 的 snapshot_id（如 101）和 committed_at（如 `2025-01-01 10:00:00`）。

2. **Schema 演进与快照 S2**：`ALTER TABLE ADD COLUMN total DOUBLE`，插入 `(3, 'PAID', 100.0)`，S2 成为当前快照，schema 为 `(id, status, total)`。

3. **快照 ID 和 timestamp 查询**：`SELECT * FROM ... VERSION AS OF 101` 和 `TIMESTAMP AS OF '2025-01-01 10:00:00'` 使用 S1 的 schema `(id, status)`，`total` 列不可见。文档解释："The `total` column does not exist in the S1 schema and is not visible, even though the current table schema includes `total`."

4. **Branch 查询**：创建指向 S1 的 branch `audit_branch` 和 tag `first_load`。`SELECT * FROM ... VERSION AS OF 'audit_branch'` 和 `` `branch_audit_branch` `` 使用表当前 schema `(id, status, total)`。对于 S1 的行，`total` 返回 `NULL`，因为该列在写入时不存在。文档明确说明："For the rows from S1, `total` is returned as `NULL` because that column did not exist when those rows were written."

5. **Tag 查询**：`SELECT * FROM ... VERSION AS OF 'first_load'` 和 `` `tag_first_load` `` 使用 tag 所绑定快照 S1 的 schema `(id, status)`，仅返回 `id` 和 `status`。文档解释："tags are bound to a specific snapshot and use that snapshot's schema, even if the table's current schema has evolved."

这个示例的关键教学价值在于清晰对比了 branch 和 tag 在 schema 选择上的根本差异：branch 是"移动指针"，查询时使用当前表 schema；tag 是"固定快照引用"，查询时使用绑定快照的 schema。

## 总结

本提交通过一个精心设计的端到端示例，将 Iceberg 时间旅行查询中 schema 选择的微妙行为具象化，帮助用户理解 branch 与 tag 在 schema 选择上的根本差异，以及 schema 演进对旧数据查询结果的影响。示例可直接运行验证，具有很高的实用指导价值，填补了原有文档仅有注释说明而缺乏可操作示例的空白。
