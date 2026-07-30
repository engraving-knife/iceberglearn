# 提交 0324：Docs: CREATE TABLE LIKE is not supported in Spark DDL (#9358)

## 提交信息

- **序号**：0324 / 4088
- **哈希**：27e8c421358378bd80bed8b328d5b69e884b7484
- **短哈希**：27e8c4213
- **日期**：2024-01-04 08:40:23 +0100
- **作者**：Manu Zhang
- **提交说明**：Docs: CREATE TABLE LIKE is not supported in Spark DDL (#9358)
- **PR/Issue**：#9358

## 总体目的

这个提交在 Iceberg 的 Spark DDL 文档中明确补充了一条限制说明：`CREATE TABLE ... LIKE ...` 语法不被支持。`CREATE TABLE ... LIKE ...` 是 Spark SQL 原生提供的一种建表语法，用于基于一个已有表的结构（schema、分区、表属性等）来创建一张新表，在数据仓库场景下较为常用。然而 Iceberg 的 Spark 集成（基于 DataSourceV2 API 的 `SparkCatalog`/`SparkSessionCatalog`）并未实现该语法的支持。

在文档补充之前，用户只能从 DDL 文档中看到 Iceberg 支持哪些创建子句（`PARTITIONED BY`、`LOCATION`、`COMMENT`、`TBLPROPERTIES`、`USING` 等），但对于 `CREATE TABLE ... LIKE ...` 是否可用并无明确说明。这会导致用户在尝试使用该语法时遇到不明确的错误或意外行为，且难以判断这是 Iceberg 的设计限制还是临时 bug。通过在文档中显式声明"不支持"，可以让用户在建表前就了解这一限制，避免无效尝试，并明确这是已知的行为而非缺陷。

补充的位置经过考量：文档放在 `## CREATE TABLE` 章节内，紧跟在介绍 `USING` 子句的段落之后、`### PARTITIONED BY` 子章节之前。这正好处于介绍"建表命令支持哪些子句"的上下文中，将"不支持 LIKE"作为对支持范围的补充说明，逻辑上连贯，读者在了解支持项的同时也能立刻看到不支持项。

## 如何达成设计目的

改动方式非常直接：在 `docs/spark-ddl.md` 中对应位置新增一行 `` `CREATE TABLE ... LIKE ...` syntax is not supported. ``。使用反引号包裹语法关键字，与文档中其他语法描述的风格一致。该行作为独立段落插入，不修改原有任何内容，纯粹是增量补充。

## 修改详情

### `docs/spark-ddl.md`

**修改目的**：在 Spark DDL 文档的 `CREATE TABLE` 章节中补充说明 `CREATE TABLE ... LIKE ...` 语法不被支持，使用户在使用前明确这一限制。

**工作逻辑**：在介绍 `USING` 子句的段落（"Create commands may also set the default format with the `USING` clause..."）之后，新增一段独立文本：`` `CREATE TABLE ... LIKE ...` syntax is not supported. ``。该段落使用反引号标记 SQL 语法片段，与文档整体的排版风格一致。新增内容位于 `## CREATE TABLE` 章节内、`### PARTITIONED BY` 子章节之前，处于"建表支持子句列表"的上下文范围内，使读者在阅读建表能力说明时能同步获知该限制。原文档内容未做任何删改，仅作增量补充。

## 小结

本提交通过在 `docs/spark-ddl.md` 的 `CREATE TABLE` 章节中新增一行说明，明确告知用户 `CREATE TABLE ... LIKE ...` 语法不被 Iceberg 的 Spark 集成支持，使用户在尝试建表前即可了解该限制，避免无效尝试与对错误来源的误解，提升了文档的完整性与用户体验。
