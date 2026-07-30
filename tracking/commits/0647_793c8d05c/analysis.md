# 提交 0647：明确 Spark 在使用分支（branch）时的 schema 行为

## 提交信息

- **序号**：0647 / 4088
- **哈希**：793c8d05cee9e4a95ffe2b94e31bef5a617e8c85
- **短哈希**：793c8d05c
- **日期**：2024-03-30
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Clarify schema behavior when working with branches (#10055)
- **PR/Issue**：PR #10055

## 总体目的

本提交主要是一个文档 + 测试性质的改动，目的是向用户明确一个长期存在但容易混淆的行为：当 Iceberg 表在分支（branch）和标签（tag）上做时间旅行（time travel）查询或写入时，到底使用的是哪一份 schema（表当前 schema 还是快照对应的 schema）。

具体来说，社区发现以下行为对用户而言并不直观：

- **查询分支的 head**：使用的是**表当前的 schema**，而不是分支创建时快照所对应的 schema。这意味着，如果分支创建后表 schema 发生了变更（如删列、加列），查询分支 head 会按新 schema 返回，旧数据中已被删除的列会以 NULL 出现。
- **通过快照 ID 做时间旅行**：使用的是**该快照创建时的 schema**。
- **查询 tag**：tag 指向某个具体快照，因此使用的是**快照的 schema**。
- **写入分支**：使用的是**表当前的 schema** 进行校验。

这种"分支 head 用表 schema、快照/tag 用快照 schema"的差异化行为，过去没有清晰文档，容易导致用户疑惑甚至误用。本提交通过补充文档说明和一个完整的回归测试来固化并阐明该行为。

## 如何达成设计目的

设计思路分两条线：

1. **文档线**：在 `branching.md` 中新增"Schema selection with branches and tags"章节，用一个完整示例（建表 -> 建分支 -> 修改表 schema -> 各种查询）演示每种场景使用的 schema；在 `spark-queries.md` 的 time travel 章节补充"Schema selection in time travel queries"小节，按查询类型列出使用的 schema；在 `spark-writes.md` 中以 info 提示框形式说明写入分支时使用表当前 schema 校验。
2. **测试线**：在 `TestSelect.java` 中新增 `readAndWriteWithBranchAfterSchemaChange` 测试用例，通过实际 SQL 操作验证文档中描述的行为，确保未来不会回归。

值得注意的是，本提交**不改变任何运行时行为**，只是把既有行为文档化并用测试固化。

## 修改详情

### `docs/docs/branching.md`

**修改目的**：新增"Schema selection with branches and tags"章节，系统说明分支与 tag 场景下 schema 的选择规则。

**工作逻辑**：通过一个连贯的 SQL 示例演示：

1. 建表 `db.table (id bigint, data string, col float)` 并插入 3 行。
2. 创建分支 `test_branch` 指向当前快照，查询分支 head 返回原始 3 行（此时表 schema 与快照 schema 一致）。
3. 在表上 `DROP COLUMN col` 并 `ADD COLUMN new_col date`，再插入 2 行新数据。此时表 schema 已变更。
4. 查询分支 head（无论是 `db.table.branch_test_branch` 还是 `VERSION AS OF 'test_branch'`）：返回旧 3 行，但 `col` 列消失、`new_col` 列以 NULL 出现 —— 因为使用的是**表当前 schema**。
5. 通过分支的 snapshot id 做 `VERSION AS OF <id>` 时间旅行：返回旧 3 行的原始形态（含 `col=1.0/2.0/3.0`）—— 因为使用的是**快照 schema**。
6. 向分支写入 2 行新数据：必须按表当前 schema（含 `new_col`）提供值，写入后再查分支 head，新旧数据都按表当前 schema 呈现（旧行的 `col` 为 NULL，`new_col` 有值）。

### `docs/docs/spark-queries.md`

**修改目的**：在 time travel 章节补充"Schema selection in time travel queries"小节，按查询类型明确 schema 来源。

**工作逻辑**：用代码块列出 5 种查询及其使用的 schema：

- `TIMESTAMP AS OF '...'` → 快照 schema（该时间点对应快照的 schema）
- `VERSION AS OF <snapshotId>` → 快照 schema
- `VERSION AS OF 'branch-name'` → 表当前 schema（因为分支是可移动引用，head 跟随表演进）
- `db.table.branch_xxx` → 表当前 schema（同上）
- `VERSION AS OF 'tag-name'` / `db.table.tag_xxx` → 快照 schema（tag 不可变，指向固定快照）

### `docs/docs/spark-writes.md`

**修改目的**：在分支写入说明处增加 info 提示框，强调写入分支使用表当前 schema 校验。

**工作逻辑**：以 mkdocs 的 `!!! info` 语法插入提示："Note: When writing to a branch, the current schema of the table will be used for validation." 与分支 head 读使用表 schema 的逻辑保持一致，保证读写一致。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`

**修改目的**：新增 `readAndWriteWithBranchAfterSchemaChange` 测试，验证文档描述的 schema 选择行为。

**工作逻辑**：

1. 加载表，创建分支 `test_branch` 指向当前快照。
2. 断言初始查询返回 3 行（含 `float` 列）。
3. `ALTER TABLE DROP COLUMN float`、`ADD COLUMN new_col date`，插入 2 行新数据。
4. **关键断言 1**：`SELECT * FROM %s VERSION AS OF %s`（用分支的 snapshot id）返回原始 3 行（含 float 值），验证**快照 schema**。
5. **关键断言 2**：`SELECT * FROM %s VERSION AS OF '%s'`（用分支名）返回 3 行但 float 列变 NULL（实际是新 schema 下无 float 列），验证**表 schema**。
6. **关键断言 3**：`SELECT * FROM %s.branch_%s` 返回与断言 2 相同结果，验证 `branch_` 语法也用表 schema。此断言在 `spark_catalog` 下跳过（因为 session catalog 的 `branch_` 语法支持受限），仅在非 spark_catalog 下执行。
7. **写入验证**：向 `db.table.branch_test_branch` 插入 2 行（按新 schema 提供 `new_col` date 值），再查分支 head，5 行均按表当前 schema 呈现。
8. **DataFrameReader 验证**：用 `spark.read().option(BRANCH, branchName)` 读，同样返回表 schema 形态，验证 DataFrame API 一致性。

## 小结

- **成效**：本提交澄清了一个长期容易混淆的行为，文档与测试双管齐下，既提升用户认知，又防止未来回归。对分支用户尤其重要——避免他们在 schema 变更后误以为分支 head 还保留旧 schema。
- **影响范围**：纯文档与测试，无运行时行为变更，对生产代码零影响。
- **回迁到 1.4.x 的注意事项**：
  - 文档改动可直接回迁，无兼容性问题。
  - 测试 `readAndWriteWithBranchAfterSchemaChange` 回迁时需注意 1.4.x 对应的 Spark 版本（可能是 v3.3/v3.4/v3.5），测试位于 `spark/v3.5/`，若 1.4.x 仍维护 v3.5 则可直接回迁；其他版本目录需相应调整或确认行为一致。
  - 测试中对 `spark_catalog` 的特殊跳过逻辑（`if (!"spark_catalog".equals(catalogName))`）需保留，因为 session catalog 对 `branch_` 标识符的支持与 Hive catalog 等不同。
  - 由于 0649 提交会限制 `branch_` 与 `TIMESTAMP AS OF` 的组合用法，回迁本测试时需注意与 0649 的限制不冲突（本测试未使用该组合，无冲突）。
