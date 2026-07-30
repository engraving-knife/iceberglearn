# 提交 0192：Docs: Remove UNIQUE keyword as it is not supported in Flink (#9046)

## 提交信息

- **序号**：0192 / 4088
- **哈希**：c1190990760509e40dd8e10092196a569d684f1b
- **短哈希**：c11909907
- **日期**：2023-11-23 12:05:44 +0100
- **作者**：Prabhu Joseph
- **提交说明**：Docs: Remove UNIQUE keyword as it is not supported in Flink (#9046)
- **PR/Issue**：#9046

## 总体目的

这是一处文档修正。Iceberg 的 Flink 写入文档 `docs/flink-writes.md` 中，在演示 v2 表 UPSERT 用法的建表示例里，给 `id` 列加了 `UNIQUE` 关键字（`id INT UNIQUE COMMENT 'unique id'`）。但 Flink SQL 并不支持列级 `UNIQUE` 约束关键字，用户照抄该示例建表会直接报语法错误。

该提交移除了示例中多余的 `UNIQUE` 关键字，使文档示例与 Flink SQL 语法保持一致，避免用户被误导。对于 Iceberg 而言，UPSERT 的唯一性约束本身是由 `PRIMARY KEY(...) NOT ENFORCED` 表达的，`UNIQUE` 在此处既无必要也不合法，删除它让文档更准确地反映实际可用的语法。

## 如何达成设计目的

仅修改 `docs/flink-writes.md` 一行，把 `` `id`  INT UNIQUE COMMENT 'unique id' `` 改为 `` `id` INT COMMENT 'unique id' ``，去掉 `UNIQUE`，同时顺手规整了 `INT` 前的多余空格。其余建表结构（`PRIMARY KEY(id) NOT ENFORCED`、`format-version=2`、`write.upsert.enabled=true`）保持不变。

## 修改详情

### `docs/flink-writes.md`

**修改目的**：修正 UPSERT 建表示例，去掉 Flink 不支持的 `UNIQUE` 列关键字。

**工作逻辑**：示例展示 v2 表基于主键的 UPSERT 写法。原写法 `` `id`  INT UNIQUE COMMENT 'unique id' `` 中的 `UNIQUE` 在 Flink SQL 中并非合法的列约束语法，删除后示例可直接执行。主键唯一性语义由 `PRIMARY KEY(\`id\`) NOT ENFORCED` 承担，与 Iceberg 表格式一致。

## 小结

修正 Flink 写入文档中不被支持的 `UNIQUE` 列关键字，使 UPSERT 建表示例可直接运行，提升文档准确性与用户体验。
