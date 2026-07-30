# 提交 0146：Spec: Fix view example (#8966)

## 提交信息

- **序号**：0146 / 4088
- **哈希**：09e6a9f7b8d733ca7a02c3933b8c88917bdb8477
- **短哈希**：09e6a9f7b
- **日期**：2023-11-10 12:31:58 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Spec: Fix view example (#8966)
- **PR/Issue**：#8966

## 总体目的

本提交修复 Iceberg 视图规范（view spec）文档 `format/view-spec.md` 中的一个示例不一致问题。视图规范通过一个完整的端到端示例来展示视图元数据 JSON 的结构：先给出一段 `CREATE OR REPLACE VIEW` 的 SQL，紧接着展示该 SQL 所对应的视图元数据 JSON 文件内容。

问题在于：示例 SQL 与它声称产生的 JSON 元数据并不自洽。JSON 中明确出现了字段 `event_count` 的 `"doc": "Count of events"`（列注释）以及视图属性 `"comment": "Daily event counts"`（视图注释），但对应的 SQL 示例里既没有给列加 `COMMENT`，也没有给视图加 `COMMENT`。读者按原 SQL 执行并不会得到 JSON 里所展示的 `doc`/`comment` 字段，这会让规范的示例可信度下降，也容易误导实现者以为这些字段是系统自动生成的。

此次修复通过在 SQL 示例中补上对应的 `COMMENT` 子句，使“SQL 输入”与“JSON 元数据输出”严格对应，同时补充一句说明文字，点明该示例展示的是通过指定全限定表名（fully-qualified table name）来改写视图底层 SQL 的场景。这是一个纯文档一致性修复，不涉及任何代码或规范语义本身的改变，但对视图规范作为参考文档的严谨性有正向价值——Iceberg 视图规范是跨引擎实现视图互通的契约，示例必须做到可复现、自洽。

## 如何达成设计目的

改动集中在一个文件、一处 hunk：在 `format/view-spec.md` 的视图创建示例中，为列 `event_count` 增加 `COMMENT 'Count of events'`，为视图增加 `COMMENT 'Daily event counts'`，并在 SQL 代码块前补充一句过渡说明，解释该示例是用全限定表名来修改底层 SQL 的演示。这样示例 SQL 与紧随其后的元数据 JSON（含 `doc` 字段与 `comment` 属性）就一一对应了。

## 修改详情

### `format/view-spec.md`

**修改目的**：让视图规范示例的 SQL 与其声称产生的元数据 JSON 保持一致，并补充示例意图说明。

**工作逻辑**：原示例 SQL 为：

```sql
USE prod.other_db;
CREATE OR REPLACE VIEW default.event_agg (
    event_count,
    event_date)
AS
SELECT
    COUNT(1), CAST(event_ts AS DATE)
FROM prod.default.events
GROUP BY 2
```

修改后变为：

```sql
USE prod.other_db;
CREATE OR REPLACE VIEW default.event_agg (
    event_count COMMENT 'Count of events',
    event_date)
COMMENT 'Daily event counts'
AS
SELECT
    COUNT(1), CAST(event_ts AS DATE)
FROM prod.default.events
GROUP BY 2
```

并在 SQL 前补一句：

> In the below example, the underlying SQL is modified by specifying the fully-qualified table name.

这样，列 `event_count` 上的 `COMMENT 'Count of events'` 对应元数据 JSON 中该字段的 `"doc": "Count of events"`；视图级 `COMMENT 'Daily event counts'` 对应 JSON `properties` 里的 `"comment": "Daily event counts"`。补充的说明句点明示例场景：当前会话切到 `prod.other_db`，但通过全限定表名 `prod.default.events` 引用源表、用 `default.event_agg` 限定视图位置，从而演示 `CREATE OR REPLACE VIEW` 改写底层 SQL 并生成新元数据文件（`00002-(uuid).metadata.json`）的流程。

## 小结

这是对视图规范示例的自洽性修复，使 SQL 示例与其展示的元数据 JSON 严格对应，提升了 Iceberg 视图规范作为跨引擎契约文档的可信度与可复现性。
