# 提交 1366：Spark: Fix typo in Spark ddl comment (#11517)

## 提交信息

- **序号**：1366 / 4088
- **哈希**：5530605ff982e24a9a7894b21b34ae0eddf7dfef
- **短哈希**：5530605ff
- **日期**：2024-11-12（Tue Nov 12 00:58:41 2024 +0800）
- **作者**：dongwang <mingwbd@gmail.com>
- **提交说明**：Spark: Fix typo in Spark ddl comment (#11517)
- **PR/Issue**：#11517

## 总体目的

Iceberg 的 Spark DDL 文档（`docs/docs/spark-ddl.md`）中介绍了使用 `WRITE ORDERED BY` 设置表写入顺序的语法，其中注释说明了可用 `ASC` 或 `DESC` 关键字指定每个字段的排序方向。原注释中将 `DESC` 误写为 `DEC`，这是一个明显的拼写错误，可能误导用户以为存在 `DEC` 关键字。

本提交将该拼写错误修正为 `DESC`，使注释与下方示例 SQL（`category ASC, id DESC`）一致，提升文档准确性。

## 如何达成设计目的

直接编辑 `docs/docs/spark-ddl.md` 中 `WRITE ORDERED BY` 章节的注释行，将 `ASC/DEC` 改为 `ASC/DESC`。

## 修改详情

### `docs/docs/spark-ddl.md`

**修改目的**：修正 `WRITE ORDERED BY` 语法说明注释中的拼写错误。

**工作逻辑**：在 `ALTER TABLE prod.db.sample WRITE ORDERED BY category, id` 示例上方的注释中，将：

```diff
--- use optional ASC/DEC keyword to specify sort order of each field (default ASC)
+-- use optional ASC/DESC keyword to specify sort order of each field (default ASC)
```

注意：此处文档位于 `docs/docs/spark-ddl.md`（注意是 `docs/docs/` 而非 `site/docs/`），是 Iceberg 文档源的另一处副本。紧接其下方的示例 SQL 已正确使用 `id DESC`，本次仅修正注释使其与示例和 SQL 标准一致。

## 小结

- **成效**：修正了 Spark DDL 文档中 `WRITE ORDERED BY` 注释里 `DEC` 的拼写错误，改为正确的 `DESC`，避免误导用户。
- **影响范围**：仅 `docs/docs/spark-ddl.md` 一个文件，1 行变更，纯文档拼写修正，无代码或构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：纯文档拼写修正，对 1.4.x 运行时和发布产物无任何影响。1.4.x 分支的文档快照已固化，若该拼写错误存在于 1.4.x 的文档中，可选择性回迁以提升文档质量，但**非必需**。该改动不影响任何功能。
