# 提交 0657：Docs: Add 5 dremio blogs (#10067)

## 提交信息

- **序号**：0657 / 4088
- **哈希**：319a482aadd96559bfe65ae103e1f7101f46bfe6
- **短哈希**：319a482aa
- **日期**：2024-04-03 09:22:11 -0500
- **作者**：Alex Merced
- **提交说明**：Docs: Add 5 dremio blogs (#10067)
- **PR/Issue**：#10067

## 总体目的

这个提交是纯文档维护，目的是更新 Iceberg 官方网站上的"博客列表"页面（`site/docs/blogs.md`），把一批新近发布的、由 Dremio 团队撰写并涉及 Apache Iceberg 的第三方博客补充进列表，方便社区用户发现与 Iceberg 相关的实践教程与案例。该页面汇总了各公司撰写的、与 Iceberg 相关的博客文章，并按时间从新到旧排列，是 Iceberg 文档站点对外展示生态内容的一个入口。

提交说明标题写的是"Add 5 dremio blogs"，但实际 diff 中新增了 6 篇 Dremio 博客条目（时间跨度为 2024 年 3 月 27 日至 4 月 1 日），标题与实际数量略有出入，不过这并不影响文档的功能性目的。

## 如何达成设计目的

实现方式是在 `site/docs/blogs.md` 文件中，紧接在引导语 "Here is a list of company blogs that talk about Iceberg..." 之后、原有最旧条目（2024 年 3 月 6 日的 "The Apache Iceberg Lakehouse: The Great Data Equalizer"）之前，按"从新到旧"的顺序插入这批新条目，使整个列表继续维持时间倒序的既有约定。每个条目沿用页面已有的统一格式：一个 `<!-- markdown-link-check-disable-next-line -->` 注释（用于在该链接处禁用 markdown 链接检查，避免 CI 误报外链失效）、一个三级标题形式的链接、`Date`/`Company` 行以及 `Author` 行（带作者 LinkedIn 链接）。所有新条目均标注 `Company: Dremio`，作者均为 Alex Merced。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：在博客列表中新增若干 Dremio 出品的 Iceberg 相关博客条目。

**工作逻辑**：在文件引导语之后插入 6 个新条目（按发布日期从新到旧）：

1. **End-to-End Basic Data Engineering Tutorial (Apache Spark, Apache Iceberg, Dremio, Apache Superset, Nessie)** — 2024-04-01，Dremio，发布于 Medium。
2. **From MongoDB to Dashboards with Dremio and Apache Iceberg** — 2024-03-29，Dremio。
3. **From SQLServer to Dashboards with Dremio and Apache Iceberg** — 2024-03-29，Dremio。
4. **BI Dashboards with Apache Iceberg Using AWS Glue and Apache Superset** — 2024-03-29，Dremio。
5. **From Postgres to Dashboards with Dremio and Apache Iceberg** — 2024-03-28，Dremio。
6. **Run Graph Queries on Apache Iceberg Tables with Dremio & Puppygraph** — 2024-03-27，Dremio。

每个条目结构与页面既有条目一致：禁用链接检查的 HTML 注释 + 三级标题链接 + 日期/公司 + 作者（含 LinkedIn 链接）。插入位置在原列表首条（2024-03-06）之前，保持整体倒序。

## 小结

- **成效**：成功达成目的。文档列表按时间倒序补充了新一批 Dremio 博客条目，格式与既有条目一致。
- **影响范围**：仅影响文档站点 `site/docs/blogs.md` 一个文件，不涉及任何代码、构建或运行时行为。
- **回迁到 1.4.x 的注意事项**：纯文档变更，无回迁风险；可直接 cherry-pick。若 1.4.x 分支上该文件结构有差异，仅需保证插入位置与倒序约定一致即可。
