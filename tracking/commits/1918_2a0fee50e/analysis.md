# 提交 1918：Add Estuary blog post showing how to load data into Apache Iceberg (#12587)

## 提交信息

- **序号**：1918 / 4088
- **哈希**：2a0fee50e81fc4f6fbc4a7d7da74eed8a16a5d9b
- **短哈希**：2a0fee50e
- **日期**：2025-03-24 17:34:18 -0600
- **作者**：SourabhEstuary
- **提交说明**：Add Estuary blog post showing how to load data into Apache Iceberg (#12587)
- **PR/Issue**：#12587

## 总体目的

Iceberg 官网 `site/docs/blogs.md` 维护了一份按时间倒序排列的第三方公司博客列表，用于汇总社区关于 Iceberg 的实践文章。本提交在该列表最前面（最新）新增一篇 Estuary 公司发布的博客文章 "How to Load Data into Apache Iceberg: A Step-by-Step Tutorial"（2025-03-20），向读者介绍如何将数据加载到 Apache Iceberg。

## 如何达成设计目的

在 `blogs.md` 列表顶部插入一条新博客条目，遵循既有格式：标题为 markdown 链接、附 `Date`/`Company`/`Author` 字段，并在链接上方加 `<!-- markdown-link-check-disable-next-line -->` 注释以跳过该外部链接的链接检查（避免 CI 因外部 URL 状态波动而失败）。

## 修改详情

### `site/docs/blogs.md` (修改, +5 lines)

**修改目的**：新增 Estuary 博客条目。

**工作逻辑**：

```markdown
<!-- markdown-link-check-disable-next-line -->
### [How to Load Data into Apache Iceberg: A Step-by-Step Tutorial](https://estuary.dev/blog/loading-data-into-apache-iceberg/)
**Date**: March 20, 2025, **Company**: Estuary
**Author**: [Dani Pálma](https://www.linkedin.com/in/danthelion)
```

插入位置在列表最前（最新），其后紧跟原首条 Debezium + Iceberg 博客。

## 总结

本提交在 Iceberg 官网博客列表顶部新增一篇 Estuary 关于"如何向 Apache Iceberg 加载数据"的教程博客条目，并按要求格式附带日期、公司与作者信息，禁用该外部链接的 CI 检查。
