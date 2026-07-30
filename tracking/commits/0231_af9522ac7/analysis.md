# 提交 0231：Docs: Document reading in Spark using branch and tag identifiers (#9238)

## 提交信息

- **序号**：0231 / 4088
- **哈希**：af9522ac7e8e25dc044622c566b66301b6df9581
- **短哈希**：af9522ac7
- **日期**：2023-12-07 12:11:30 +0100
- **作者**：Wing Yew Poon
- **提交说明**：Docs: Document reading in Spark using branch and tag identifiers (#9238)
- **PR/Issue**：#9238

## 总体目的

这个提交是纯文档变更，用于补充 Iceberg Spark 查询文档中关于"使用 branch（分支）和 tag（标签）标识符读取表"这一能力的说明。Iceberg 的分支与标签（[SnapshotRef](https://github.com/apache/iceberg/blob/main/core/src/main/java/org/apache/iceberg/SnapshotRef.java)）机制允许用户为某个快照命名，以便引用特定版本的数据。在 Spark 中，此前文档已经介绍了通过 `VERSION AS OF '<branch-name>'`、`FOR SYSTEM_VERSION AS OF '<tag-name>'` 等 SQL 子句来按分支/标签读取表，但还有另一种基于元数据表（metadata table）类似语法的访问方式未被文档覆盖，即使用 `branch_<branchname>` 或 `tag_<tagname>` 作为标识符后缀来读取。

这种语法（`prod.db.table.`branch_audit-branch``）与 Iceberg 元数据表的访问语法（如 `prod.db.table.metadata_log_entries`）一致，把分支/标签当作表名的一部分，通过反引号转义后追加到表标识符后。该提交在 [`docs/spark-queries.md`](../../../../docs/spark-queries.md) 的 "Time travel" 小节末尾（紧接在 `FOR SYSTEM_TIME AS OF` 时间戳示例之后、`#### DataFrame` 之前）补充了这种语法示例、对含连字符标识符需反引号转义的提示，以及"分支/标签标识符不能与 `VERSION AS OF` 组合使用"的限制说明。

这一文档补全对 Iceberg 演进的意义在于：让用户了解到分支/标签读取的完整语法选项，尤其是与元数据表语法一致的那一种，降低了使用门槛并消除了文档空白。

## 如何达成设计目的

整体改动极为聚焦：仅在 `docs/spark-queries.md` 的 "Time travel" 小节中新增 11 行 Markdown，包含两段 SQL 代码示例与三条说明性文字。改动位于已有的 `VERSION AS OF` / `FOR SYSTEM_VERSION AS OF` / `TIMESTAMP AS OF` 说明之后，作为对分支/标签读取方式的补充，不涉及任何代码逻辑变更。

## 修改详情

### `docs/spark-queries.md`

**修改目的**：补充说明在 Spark SQL 中使用 `branch_<branchname>` / `tag_<tagname>` 标识符语法读取 Iceberg 表的特定分支或标签快照。

**工作逻辑**：

新增内容包含：

1. 一段引导文字，说明分支或标签也可使用与元数据表类似的语法指定，形如 `branch_<branchname>` 或 `tag_<tagname>`。
2. 两条 SQL 示例：
   ```sql
   SELECT * FROM prod.db.table.`branch_audit-branch`;
   SELECT * FROM prod.db.table.`tag_historical-snapshot`;
   ```
3. 一条提示：含 `-` 的标识符在 SQL 中不是合法标识符，必须用反引号转义。
4. 一条限制说明：该分支/标签标识符语法不能与 `VERSION AS OF` 组合使用。

这些内容紧接在已有的 Unix 时间戳示例（`TIMESTAMP AS OF 499162860`）之后，与上文已有的 `VERSION AS OF 'audit-branch'`（字符串形式指定分支）形成互补，使文档完整覆盖了 Spark 中按分支/标签读取表的两种语法途径。

## 小结

这个提交补全了 Iceberg Spark 文档中关于 `branch_`/`tag_` 标识符语法读取分支与标签的说明，消除了文档空白，让用户能更完整地了解时间旅行查询的可用语法。
