# 提交 1418：Docs: Add new blog post to Iceberg Blogs (#11627)

## 提交信息

- **序号**：1418 / 4088
- **哈希**：f2b1b91d304039027333570451477e7575f7d39d
- **短哈希**：f2b1b91d3
- **日期**：2024-11-22（Fri Nov 22 15:49:27 2024 +0100）
- **作者**：ismail simsek <6005685+ismailsimsek@users.noreply.github.com>
- **提交说明**：Docs: Add new blog post to Iceberg Blogs (#11627)
- **PR/Issue**：#11627

## 总体目的

Iceberg 官网维护着一个公司/社区博客列表 `site/docs/blogs.md`，按时间倒序收录与 Iceberg 相关的外部博客文章，方便用户了解社区实践案例。社区作者 Ismail Simsek 在 Medium 发布了一篇题为"Building a Data Lake with Debezium and Apache Iceberg"的系列文章（Part 1），介绍如何用 Debezium（CDC 工具）配合 Apache Iceberg 构建数据湖。本提交把该博客条目添加到列表最上方（最新位置），让访客能看到这篇最新的实践分享。

## 如何达成设计目的

直接编辑 `site/docs/blogs.md`，在文件开头说明文字之后、原最新条目（Dremio 的 PyIceberg 文章，2024-10-22）之前，插入一条新的博客条目，包含标题链接、发布日期、公司、作者信息。同时在条目前保留 `<!-- markdown-link-check-disable-next-line -->` 注释（与列表中其他条目一致），用于在 markdown-link-check 工具运行时跳过对该链接的可达性检查，避免因 Medium 链接的访问限制导致 CI 失败。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：在博客列表顶部新增一条 Debezium + Iceberg 的博客条目。

**工作逻辑**：在原有 "Here is a list of company blogs..." 说明行之后插入：

```markdown
<!-- markdown-link-check-disable-next-line -->
### [Building a Data Lake with Debezium and Apache Iceberg](https://medium.com/@ismail-simsek/building-a-data-lake-with-debezium-and-apache-iceberg-part-1-25124daf2a95)
**Date**: November 15th, 2024, **Company**: Memiio Community

**Author**: [Ismail Simsek](https://www.linkedin.com/in/ismailsimsek/)
```

该条目格式与列表中其他条目一致：`###` 三级标题包裹可点击的标题链接，下方一行写日期与公司，再一行写作者（带 LinkedIn 链接）。公司名"Memiio Community"应为作者所在社区/项目名。条目顺序保持"最新在最前"的既有约定。

## 小结

- **成效**：官网博客列表新增一篇 2024-11-15 的 Debezium + Iceberg 数据湖实践博客，丰富了社区实践案例展示。
- **影响范围**：仅 `site/docs/blogs.md` 一个文档文件，新增 6 行，无代码或构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是纯文档内容更新，与产品版本功能无关，对 1.4.x 运行时无任何影响。1.4.x 作为维护分支一般不单独维护博客列表（博客列表由 main 分支统一维护并作用于官网），**无需回迁**。即使 1.4.x 分支的 `blogs.md` 与 main 不同，也不影响其发布产物。
