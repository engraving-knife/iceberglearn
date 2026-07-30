# 提交 1063：Docs: Cloudera blog in February 2023 (#10947)

## 提交信息

- **序号**：1063 / 4088
- **哈希**：a49202773520072fef7457e0056c37f43e9f9d1b
- **短哈希**：a49202773
- **日期**：2024-08-16 07:08:49 +0200
- **作者**：SaketaChalamchala <saketa.chalamchala@gmail.com>
- **提交说明**：Docs: Cloudera blog in February 2023 (#10947)
- **PR/Issue**：#10947

## 总体目的

Iceberg 官方网站（site/docs）维护一份 `blogs.md` 文档，按时间顺序（从新到旧）汇总社区中介绍 Apache Iceberg 的公司博客文章，作为生态推广和参考资料。该文档对每篇博客列出标题（链接到原文）、发布日期、所属公司以及作者（带 LinkedIn 链接）。

本提交的目的是在该博客列表中追加一篇 Cloudera 在 2023 年 2 月发布的文章《Open Data Lakehouse powered by Apache Iceberg on Apache Ozone》，让访问 Iceberg 官网的用户能够发现并阅读该文。该文作者正是本次提交者本人 Saketa Chalamchala，文章讲述了在 Apache Ozone 上基于 Apache Iceberg 构建开放数据湖仓的实践。

## 如何达成设计目的

由于 `blogs.md` 中的博客条目按发布日期从新到旧排列，本次新增的 2023-02-28 文章需要插入到列表中对应的位置——位于 2023 年 3 月的一篇 Cloudera 博客之后、2022 年 2 月的一篇 Dremio 博客之前。实现方式是直接在该位置追加一段 Markdown 内容，沿用与其他条目一致的格式：

- `<!-- markdown-link-check-disable-next-line -->` 注释，用于让 markdown-link-check 跳过对下一个链接的检查（Medium 文章链接可能存在反爬限制，需要避免被链接检查器误判为失效）；
- `### [标题](原文URL)` 三级标题，标题作为指向原文的超链接；
- `**Date**: ..., **Company**: Cloudera` 行，展示发布日期和公司；
- `**Authors**: [作者名](LinkedIn主页)` 行，展示作者及其 LinkedIn 链接。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：在 Iceberg 官方博客索引列表中追加 Cloudera 2023-02-28 的一篇关于 Iceberg on Apache Ozone 的博客条目。

**工作逻辑**：在已有的 Cloudera 2023-03 博客条目之后、Dremio 2022-02 博客条目之前，插入 6 行新内容，按日期顺序正确就位。新增内容如下：

```markdown
<!-- markdown-link-check-disable-next-line -->
### [Open Data Lakehouse powered by Apache Iceberg on Apache Ozone](https://medium.com/engineering-cloudera/open-data-lakehouse-powered-by-apache-iceberg-on-apache-ozone-a225d5dcfe98/)
**Date**: February 28th, 2023, **Company**: Cloudera

**Authors**: [Saketa Chalamchala](https://www.linkedin.com/in/saketa-chalamchala-3602026a)
```

格式与文档中其他条目完全一致，保持索引页风格统一。

## 小结

- **成效**：在 Iceberg 官方博客索引中新增一篇 Cloudera 2023 年 2 月的博客条目，丰富了社区生态文档。
- **影响范围**：仅修改 `site/docs/blogs.md` 一个文件，新增 6 行，无代码、配置或构建变更。
- **回迁到 1.4.x 的注意事项**：纯文档更新，对功能无任何影响。回迁到 1.4.x 风险极低，但通常文档类提交无需回迁到维护分支，1.4.x 分支的 `site/docs/blogs.md` 内容应跟随自身发布节奏维护；如果 1.4.x 也维护这份博客列表，cherry-pick 时仅需确认插入位置仍然符合日期顺序即可。
