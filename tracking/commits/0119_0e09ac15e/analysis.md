# 提交 0119：Docs: Document UNORDERED for spark write (#8958)

## 提交信息

- **序号**：0119 / 4088
- **哈希**：0e09ac15e7e5bef5b5ac063e568d894b09ddeacf
- **短哈希**：0e09ac15e
- **日期**：2023-10-31
- **作者**：Ajantha Bhat
- **提交说明**：Docs: Document UNORDERED for spark write (#8958)
- **PR/Issue**：#8958

## 总体目的

Iceberg 的 Spark DDL 支持通过 `ALTER TABLE ... WRITE ORDERED BY` 设置表的全局写入排序，或用 `WRITE LOCALLY ORDERED BY` 设置任务内排序，用于优化数据布局以提升下游查询的列裁剪与压缩率。但官方文档 `docs/spark-ddl.md` 中"ALTER TABLE ... WRITE ORDERED BY"小节只说明了如何"设置"排序，却没有说明如何"取消"已设置的排序。用户一旦设置了 `WRITE ORDERED BY`，在文档层面就找不到对应的"撤销"语法，造成使用上的困惑。

本提交补全这一文档缺口：在 `WRITE LOCALLY ORDERED BY` 示例之后、`WRITE DISTRIBUTED BY PARTITION` 小节之前，新增一段说明与示例，明确可以使用 `ALTER TABLE ... WRITE UNORDERED` 来取消表的排序顺序。这是一个纯文档变更（+6 行，无代码改动），把已有的 Spark DDL 能力（`UNORDERED` 关键字）补充进文档，使"设置排序 / 设置本地排序 / 取消排序"三者构成完整的文档闭环。

## 如何达成设计目的

在 `docs/spark-ddl.md` 的 `WRITE ORDERED BY` 小节内，紧接 `LOCALLY ORDERED BY` 示例之后插入一段简短说明与 SQL 示例：`ALTER TABLE prod.db.sample WRITE UNORDERED`。改动极小，仅 6 行新增，无任何代码或逻辑变更，定位为文档补全。

## 修改详情

### `docs/spark-ddl.md`

**修改目的**：在 Spark DDL 文档中补充 `WRITE UNORDERED` 语法说明，告知用户如何取消表的写入排序。

**工作逻辑**：在 `WRITE ORDERED BY` 小节中，原 `LOCALLY ORDERED BY` 示例（`ALTER TABLE prod.db.sample WRITE LOCALLY ORDERED BY category, id`）之后，新增一段：

```sql
To unset the sort order of the table, use `UNORDERED`:

ALTER TABLE prod.db.sample WRITE UNORDERED
```

该示例与上文的 `WRITE ORDERED BY` / `WRITE LOCALLY ORDERED BY` 示例保持一致的行文风格，位于同一小节内，使读者能在一个地方看到设置与取消两种操作。`UNORDERED` 关键字对应 Iceberg 表排序顺序的清空（将表的 sort order 重置为无序），常用于不再需要写入排序、或想消除排序带来的写入开销时。

## 小结

本提交补全了 Spark DDL 文档中 `WRITE UNORDERED` 语法的缺失，使用户能完整地查阅"设置 / 取消表写入排序"的操作，是一个小的文档完善。
