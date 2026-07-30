# 提交 1814：Docs: Fix typo in DELETE stmt (#12426)

## 提交信息

- **序号**：1814 / 4088
- **哈希**：0ed01a21da6280499477d1b0b8b090f7adb062b1
- **短哈希**：0ed01a21d
- **日期**：2025-03-03 12:56:09 -0800
- **作者**：Wenston Xin
- **提交说明**：Docs: Fix typo in DELETE stmt (#12426)
- **PR/Issue**：#12426

## 总体目的

这是一个文档修复提交，修正了 Spark 写入文档中 DELETE 语句示例的一个拼写错误。

在 `docs/docs/spark-writes.md` 的 WAP（Write-Audit-Publish）分支写入示例中，DELETE FROM 语句引用的表名存在拼写错误。示例上下文使用的是 `prod.db.table.branch_audit`，但 DELETE 语句误写为 `prod.dbl.table.branch_audit`——多了一个字符 `l`，将 `db`（数据库名）误写为 `dbl`。这是一个明显的笔误，会导致读者复制示例代码时无法正确执行。

修正此类文档错误有助于提升用户文档的准确性和可用性，避免用户在使用 WAP 分支审计功能时因示例错误而产生困惑。

## 如何达成设计目的

作者通过直接修改 `docs/docs/spark-writes.md` 文件，将 DELETE 语句中的表名 `prod.dbl.table.branch_audit` 修正为 `prod.db.table.branch_audit`，使其与同一段示例中的 UPDATE 语句表名保持一致。该修改为纯文档变更，不涉及任何代码逻辑。

## 修改详情

### docs/docs/spark-writes.md (修改, 1 line)

修改了 DELETE FROM 语句的表名，将 `prod.dbl.table.branch_audit WHERE id = 2;` 修正为 `prod.db.table.branch_audit WHERE id = 2;`。该示例位于 WAP 分支写入的演示段落中，展示了如何对 audit 分支执行 UPDATE 与 DELETE 操作。修正后表名与同段 UPDATE 语句及上下文保持一致。

## 小结

这是一个低风险的纯文档拼写修正，仅修改一行。回迁到 1.4.x 分支时，若该分支的 spark-writes.md 存在同样的拼写错误，可直接 cherry-pick；由于是文档变更，无任何运行时影响。注意完整哈希对应的短哈希应为 `0ed01a21d`（取前9位）。
