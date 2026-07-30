# 提交 0600：Docs: Add 13 Dremio Blogs + Fix a few incorrect dates

## 提交信息

- **序号**：0600 / 4088
- **哈希**：1c5022785b84ed46d921ab44ca9c03eb02790b7b
- **短哈希**：1c5022785
- **日期**：2024-03-18（Mon Mar 18 03:37:45 2024 -0400）
- **作者**：Alex Merced <alex@alexmerced.dev>
- **提交说明**：Docs: Add 13 Dremio Blogs + Fix a few incorrect dates (#9967)
- **PR/Issue**：#9967

## 总体目的

Apache Iceberg 官方网站（`site/` 目录，基于 Docusaurus）维护着一个「外部博客文章索引」页面 `site/docs/blogs.md`，按时间倒序列举各公司（Dremio、Cloudera、腾讯、Apple 等）发布的关于 Iceberg 的技术博客，供社区与用户检索学习。该页面需要持续手工同步新发布的博客条目，并校正历史录入错误。

本提交由 Dremio 的 Alex Merced（Iceberg 社区贡献者）提交，目的有二：

1. **补充缺失的 Dremio 博客条目**：Dremio 在 2023 年 5 月至 2024 年 3 月期间发布了一批关于 Iceberg 的实践博客（涵盖 Lakehouse 概念、Flink 集成、数据导入、目录配置、数据质量、版本对比等主题），这些条目此前未录入索引。本提交把这一批博客按时间倒序插入到 `blogs.md` 的正确位置。
2. **修正错误的日期**：四篇 2023 年 4-5 月发布的 Dremio 博客此前被错误标为 2022 年，本提交把年份从 2022 改回 2023，恢复正确的时序，确保页面「按最近到最旧排序」的承诺成立。

提交说明自称「Add 13 Dremio Blogs」，但实际 diff 只新增了 **12** 条 `### [...]` 条目（见下方统计）。这是提交说明与实际改动的小幅不一致，可能是作者在编写提交说明时计数有误，或在最终 PR 里删去了一条但未更新说明。不影响功能。

## 如何达成设计目的

作者直接编辑 Markdown 索引文件，采用与现有条目完全一致的格式：

```
### [博客标题](博客URL)
**Date**: 月份 日th, 年份, **Company**: 公司名

**Author**: [作者名](LinkedIn主页)
```

或多人合著时 `**Authors**:` 复数。具体做法：

1. **定位插入点**：在 `blogs.md` 中找到「按时间倒序」的正确位置。新加的 2024 年 2-3 月博客插在文件最顶部（最新），2023 年 5-8 月的博客插在对应时间窗口的现有条目之间（如 8 月博客插在「January 23rd, 2024」条目之后、「July 14th, 2023 Cloudera」条目之前）。
2. **批量补录**：12 条新条目分散在 3 个位置插入——文件顶部（2024 年 7 条）、1 月与 7 月之间（2023 年 8 月与 7 月 2 条）、5 月中旬位置（2023 年 5 月 3 条）。
3. **顺手修正**：在插入 5 月新条目的同时，把同区域的 4 条 2022 年错误日期改成 2023 年（May 12、April 12、April 3 ×2）。这 4 条都是 Dremio 博客，作者很可能在核对新博客时发现了同区域旧条目的年份错误，顺手一并修正。
4. 不涉及 `mkdocs.yml` 或站点导航：`blogs.md` 本就是已注册页面，本次只改内容。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：扩充 Dremio 博客索引、修正 4 处年份错误。

**工作逻辑**：

文件顶部说明文字 `Here is a list of company blogs that talk about Iceberg. The blogs are ordered from most recent to oldest.` 之后，共做了三类改动：

**(A) 新增 12 条 Dremio 博客条目**（按提交说明自称 13，实际 diff 12 条）：

2024 年（7 条，插入文件顶部，最新）：
1. *The Apache Iceberg Lakehouse: The Great Data Equalizer* — 2024-03-06，Alex Merced
2. *Data Lakehouse Versioning Comparison: (Nessie, Apache Iceberg, LakeFS)* — 2024-03-05，Alex Merced
3. *What is Lakehouse Management?: Git-for-Data, Automated Apache Iceberg Table Maintenance and more* — 2024-02-23，Alex Merced
4. *What is DataOps? Automating Data Management on the Apache Iceberg Lakehouse* — 2024-02-23，Alex Merced
5. *What is the Data Lakehouse and the Role of Apache Iceberg, Nessie and Dremio?* — 2024-02-21，Alex Merced
6. *Ingesting Data Into Apache Iceberg Tables with Dremio: A Unified Path to Iceberg* — 2024-02-01，Alex Merced
7. *Open Source and the Data Lakehouse: Apache Arrow, Apache Iceberg, Nessie and Dremio* — 2024-02-01，Alex Merced

2023 年 7-8 月（2 条，插在 2024-01-23 条目与 2023-07-14 Cloudera 条目之间）：
8. *Getting Started with Flink SQL and Apache Iceberg* — 2023-08-08，Dipankar Mazumdar & Ajantha Bhat
9. *Using Flink with Apache Iceberg and Nessie* — 2023-07-28，Alex Merced

2023 年 5 月（3 条，插在 2023-05 中旬的 Rui Li 条目之后、Catalog Migration Tool 条目之前）：
10. *How to Convert JSON Files Into an Apache Iceberg Table with Dremio* — 2023-05-31，Alex Merced
11. *Deep Dive Into Configuring Your Apache Iceberg Catalog with Apache Spark* — 2023-05-31，Alex Merced
12. *Streamlining Data Quality in Apache Iceberg with write-audit-publish & branching* — 2023-05-19，Dipankar Mazumdar & Ajantha Bhat

每条格式统一：三级标题 + URL、`**Date**` + `**Company**` 行、空行、`**Author**/**Authors**` + LinkedIn 链接行、空行。

**(B) 修正 4 处年份错误**（2022 → 2023）：

| 博客标题 | 修改前 | 修改后 |
|---|---|---|
| Introducing the Apache Iceberg Catalog Migration Tool | May 12th, **2022** | May 12th, **2023** |
| 3 Ways to Use Python with Apache Iceberg | April 12th, **2022** | April 12th, **2023** |
| 3 Ways to Convert a Delta Lake Table Into an Apache Iceberg Table | April 3rd, **2022** | April 3rd, **2023** |
| How to Convert CSV Files into an Apache Iceberg table with Dremio | April 3rd, **2022** | April 3rd, **2023** |

这 4 条都是 Dremio 博客，年份被统一少写了 1 年。修正后，它们在「按最近到最旧」序列中的位置仍然落在 2023-05 与 2023-04 区间内，与上下文时序一致，无需调整文件内顺序，只改年份字段即可。

**(C) 统计**：净增 64 行、删除 4 行（4 处年份的 `-` 行），共 +64 / -4。其中 12 条新博客每条约 5 行（标题 + Date + 空行 + Author + 空行）≈ 60 行，加上 4 处年份修正的 `+` 行 ≈ 4 行，合计 64 行，与 stat 一致。

## 小结

- 这是纯文档内容维护提交，1 个文件、+64/-4 行，不涉及任何代码、构建配置、站点结构改动，对运行时与构建零影响。
- 价值在于「内容完整性」：补齐 Dremio 这一 Iceberg 重要贡献厂商在 2023-2024 年的博客矩阵，让社区索引更全面；同时修正 4 处年份错误，维护「按时间倒序」的排序承诺。
- 提交说明与实际改动的小不一致：自称「13 Dremio Blogs」，实际新增 12 条。这是无害的计数误差，不影响合并与回迁。
- 回迁到 1.4.x 的注意事项：
  - **重要前提**：1.4.x 分支上 **不存在** `site/` 目录与 `site/docs/blogs.md` 文件（`site/` 是主线上独立的 Docusaurus 站点项目，1.4.x 可能未引入或已移除）。因此本提交 **无法直接 cherry-pick** 到 1.4.x——cherry-pick 会因目标文件不存在而失败。
  - **后续状态**：即便在 main 上，`site/docs/blogs.md` 也已于 2025-09-30 被提交 `d1fddd8d6 Site: Remove Blogs and Talks From Site (#14110)` 整体移除。这意味着本提交所修改的内容在 main 上也已不存在，回迁到 1.4.x 的实际价值很低。
  - **建议**：如果 1.4.x 不维护 `site/` 站点，则本提交可跳过；若 1.4.x 有等价的博客索引页（例如 `docs/` 下的某个文件），需手动把上述 12 条博客条目按相同格式同步过去，并套用 4 处年份修正。
