# 提交 3058：Spark: Add ordering to TestSelect to remove flakiness (#14956)

## 提交信息

- **序号**：3058 / 4088
- **哈希**：e131329a07fb5d726bc33177d9f0cd23aaebd2fb
- **短哈希**：e131329a0
- **日期**：2026-01-03
- **作者**：Huaxin Gao
- **提交说明**：Spark: Add ordering to TestSelect to remove flakiness (#14956)
- **PR/Issue**：#14956

## 总体目的

本提交修复了 Spark `TestSelect` 测试套件在 Spark 4.0 与 4.1 版本上的 flakiness（随机失败）问题。根因是这些测试在断言查询结果时假设了固定的行顺序，但 Iceberg（尤其是启用远程扫描计划 remote scan planning 后）并不保证返回行的顺序——扫描任务可能以非确定顺序返回数据文件，导致 `SELECT *` 的行顺序不稳定。当测试用 `assertEquals` 或 AssertJ 的 `containsExactly`（要求顺序与内容完全一致）来比较 expected 与 actual 时，就会因为顺序差异而间歇性失败。

具体表现有两类：一是 `testSelect`、`testSplitSize`、`testAggPushDown` 中的 LIMIT 测试以及 `testProjection` 等用例，直接比较 `SELECT * FROM table` 或 `SELECT id FROM table` 的完整结果，隐式依赖行按插入顺序返回；二是 `testFilter` 等带 `WHERE` 条件的用例，结果集较小但仍用 `containsExactly` 强制顺序比较。本提交针对这两类分别处理。

对于需要固定顺序的用例，在 SQL 中追加 `ORDER BY id`，让排序在查询侧显式完成，从而使断言稳定；对于结果顺序本身无关紧要、只需校验集合内容的用例（如 `testSchemaEvolutionAndFilter`、`testFilter` 中的多列过滤），将断言从 `containsExactly` / `containsExactlyElementsOf` 改为 `containsExactlyInAnyOrder` / `containsExactlyInAnyOrderElementsOf`，明确表达"顺序不重要"。这两种处理方式都消除了对底层扫描顺序的隐式依赖，使测试在远程扫描计划等场景下保持稳定。

本提交与此前 3029（针对 Spark 4.0/4.1 的 `TestSelect` 做过类似修复）以及 3063（为 Spark 3.4/3.5 启用远程扫描计划并同步加 ORDER BY）属于同一稳定性治理方向，但本次针对的是 4.0/4.1 版本中仍未覆盖到的断言点。

## 如何达成设计目的

思路是区分两类断言分别治理：对顺序敏感的比较补 `ORDER BY id`；对顺序不敏感的比较改用 `containsExactlyInAnyOrder` 系列。涉及 `spark/v4.0` 与 `spark/v4.1` 两个版本下 `spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java`，改动覆盖 `testSelect`、`testSplitSize`、`testAggPushDown`（LIMIT 部分）、`testProjection`、`testSchemaEvolutionAndFilter`、`testFilter` 等多个测试方法。

## 修改详情

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+27/-16 lines)

**修改目的**：消除 `TestSelect` 在 Spark 4.0 上的随机失败。

**工作逻辑**：
分两类改动。第一类是追加 `ORDER BY id`：在 `testSelect`、`testSplitSize`、`testProjection` 中，将 `sql("SELECT * FROM %s", tableName)` 改为 `sql("SELECT * FROM %s ORDER BY id", tableName)`，`SELECT id FROM %s` 同理；在 `testAggPushDown` 的 LIMIT 校验中，把 `SELECT * FROM %s LIMIT 1/2/3` 改为带 `ORDER BY id` 的版本，确保 `first`/`second`/`third` 的顺序确定，从而 `containsExactly(first)`、`containsExactly(first, second)` 等断言稳定成立。第二类是放宽顺序约束：在 `testSchemaEvolutionAndFilter` 中把 `containsExactlyElementsOf(expected)` 改为 `containsExactlyInAnyOrderElementsOf(expected)`；在 `testFilter` 中把 8 处 `containsExactly(...)` 改为 `containsExactlyInAnyOrder(...)`（涵盖 id、boolean、long、float、double、string、date、timestamp 各列的过滤结果），明确这些用例只关心集合内容而非顺序。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+27/-15 lines)

**修改目的**：同 v4.0，消除 Spark 4.1 上相同的随机失败。

**工作逻辑**：
与 v4.0 完全一致的改动：对 `testSelect`、`testSplitSize`、`testProjection`、`testAggPushDown` 的 LIMIT 部分追加 `ORDER BY id`；对 `testSchemaEvolutionAndFilter` 与 `testFilter` 中的断言改为 `containsExactlyInAnyOrder` / `containsExactlyInAnyOrderElementsOf`。唯一细微差别是 v4.1 的 `testProjection` 在原代码中 `expected` 声明与 `sql(...)` 调用之间有一空行，本次改动一并去掉了该空行，因此增删行数略有差异。

## 总结

本提交通过在顺序敏感的断言上追加 `ORDER BY id`、在顺序无关的断言上改用 `containsExactlyInAnyOrder`，消除了 Spark 4.0/4.1 `TestSelect` 因底层扫描顺序不确定而导致的随机失败，提升了测试套件在远程扫描计划等场景下的稳定性，与同方向的 3029、3063 一并完善了 TestSelect 的稳定性治理。
