# 提交 2591：[docs] Add Kafka->Iceberg blog post to blogs.md (#13965)

## 提交信息

- **序号**：2591 / 4088
- **哈希**：170189e17ca0fd423e690d0de52380fa092177fd
- **短哈希**：170189e17
- **日期**：2025-09-03 09:59:05 -0700
- **作者**：Robin Moffatt
- **提交说明**：[docs] Add Kafka->Iceberg blog post to blogs.md (#13965)
- **PR/Issue**：#13965

## 总体目的

本次提交是一个纯文档更新，在 Iceberg 官网的博客列表页面中新增一篇关于"Kafka 到 Iceberg"的博文链接。

这篇博文由 Robin Moffatt（Confluent 员工）撰写，发布于 2025 年 8 月 18 日，标题为"Kafka to Iceberg - Exploring the Options"，探讨了将 Kafka 数据流入 Iceberg 表的各种方案。Kafka 到 Iceberg 的数据管道是流式数据入湖的常见场景，社区对此类实践文章有持续需求。

Iceberg 的 blogs.md 页面维护了一个按时间倒序排列的第三方公司博客列表，供用户参考学习。新增博文需要按时间顺序插入到列表顶部位置。

## 如何达成设计目的

在 `site/docs/blogs.md` 文件中，按照现有的博文条目格式，在列表最顶部（即最新博文位置）插入新条目。格式包括 markdown 链接检查禁用注释、博文标题链接、日期、公司和作者信息。

## 修改详情

### `site/docs/blogs.md` (+5/-0 lines)

**修改目的**：在博客列表中新增 Kafka 到 Iceberg 的博文条目。

**工作逻辑**：在 "Here is a list of company blogs..." 说明文字之后、原有最新博文（BladePipe 的 MySQL 到 Iceberg 文章）之前，插入新条目：

```markdown
<!-- markdown-link-check-disable-next-line -->
### [Kafka to Iceberg - Exploring the Options](https://rmoff.net/2025/08/18/kafka-to-iceberg-exploring-the-options/)
**Date:** August 18, 2025, **Company**: Confluent
**Author**: [Robin Moffatt](https://www.linkedin.com/in/robinmoffatt)
```

`<!-- markdown-link-check-disable-next-line -->` 注释用于在 CI 的 markdown 链接检查中跳过对该链接的检查（因为外部博客链接可能不稳定）。条目格式与列表中其他条目保持一致。

## 总结

这是一个简单的文档维护提交，将一篇关于 Kafka 到 Iceberg 数据集成方案的实践博文添加到官网博客列表。这类文章有助于社区用户了解不同的数据入湖方案，体现了 Iceberg 生态在流式数据集成方面的活跃度。
