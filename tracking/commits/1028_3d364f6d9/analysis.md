# 提交 1028：Docs: Fix SQL in branching docs (#10876)

## 提交信息

- **序号**：1028 / 4088
- **哈希**：3d364f6d95600be4e5320fc5931bb51b2af61de6
- **短哈希**：3d364f6d9
- **日期**：2024-08-06 05:50:21 +0900
- **作者**：k.nakagaki <141020064+nakaken-churadata@users.noreply.github.com>
- **提交说明**：Docs: Fix SQL in branching docs (#10876)
- **PR/Issue**：#10876

## 总体目的

Iceberg 的分支（branching）文档 `docs/docs/branching.md` 中有一个示例，演示如何修改表 schema：先删除一个列、再新增一个列。但该示例中的 `ALTER TABLE ... DROP COLUMN` 语句写错了要删除的列名——示例上下文描述的是"删除 `col` 列并新增 `new_col` 列"，而 SQL 语句里写的却是 `drop column float`，列名 `float` 与上下文不符，会让读者困惑甚至直接照抄后报错（表里根本没有 `float` 列）。

本提交将该 SQL 示例中的列名从 `float` 修正为 `col`，使示例语句与文档描述一致。

## 如何达成设计目的

直接修改 `branching.md` 中那行 `ALTER TABLE` 语句，把 `drop column float` 改为 `drop column col`，一字之差。不涉及任何代码或构建改动。

## 修改详情

### `docs/docs/branching.md`

**修改目的**：修正 schema 演进示例 SQL 中错误的列名，使 `DROP COLUMN` 的目标列与上下文描述一致。

**工作逻辑**：改动前后对比如下：

```diff
-ALTER TABLE db.table drop column float;
+ALTER TABLE db.table drop column col;
```

文档上下文为"Modify the table's schema by dropping the `col` column and adding a new column named `new_col`"，下文紧跟 `ALTER TABLE db.table add column new_col date;`。修正后 `DROP COLUMN col` 与描述的 `col` 列、新增的 `new_col` 列前后呼应，示例逻辑自洽。

## 小结

- **成效**：修正了分支文档中 schema 演进示例 SQL 的列名错误，使示例与上下文描述一致，避免读者误解或照抄出错。
- **影响范围**：仅 `docs/docs/branching.md` 一个文件，1 行改动，无代码或构建影响。
- **回迁到 1.4.x 的注意事项**：纯文档拼写修正，回迁风险极低。若 1.4.x 分支的 `branching.md` 也存在同样的列名错误，可安全回迁；若 1.4.x 文档版本不同则需确认具体位置后改。优先级低，但零风险。
