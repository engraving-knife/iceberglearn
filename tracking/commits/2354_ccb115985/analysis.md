# 提交 2354：[docs] Add two Iceberg blogs (#13493)

## 提交信息

- **序号**：2354 / 4088
- **哈希**：ccb11598544bc06c6c4d8e2e2296676ac1e8f052
- **短哈希**：ccb115985
- **日期**：2025-07-15 12:11:24 +0200
- **作者**：Robin Moffatt
- **提交说明**：[docs] Add two Iceberg blogs (#13493)
- **PR/Issue**：#13493

## 总体目的

这个提交在 Iceberg 官方博客列表中新增两篇由 Robin Moffatt（Confluent）撰写的关于 Iceberg 的博客文章链接。Iceberg 文档站点维护着一个社区博客列表，按时间倒序列出讨论 Iceberg 的公司博客，方便用户发现相关的实践文章。

新增的两篇文章分别是：
1. "Writing to Apache Iceberg on S3 using Kafka Connect with Glue catalog"（2025年7月4日）——介绍如何使用 Kafka Connect 将数据写入 S3 上的 Iceberg 表（配合 Glue catalog）。
2. "Writing to Apache Iceberg on S3 using Flink SQL with Glue catalog"（2025年6月24日）——介绍如何使用 Flink SQL 将数据写入 S3 上的 Iceberg 表（配合 Glue catalog）。

## 如何达成设计目的

在博客列表文件中按时间倒序插入两个新条目，每个条目包含标题（带链接）、日期、公司和作者信息。

## 修改详情

### `site/docs/blogs.md` (+10/-0 lines)

**修改目的**：在博客列表中新增两篇文章条目。

**工作逻辑**：在列表顶部（Ryft 文章之后）插入两个条目，每个条目使用 `<!-- markdown-link-check-disable-next-line -->` 注释禁用链接检查（因 rmoff.net 为个人博客域名），并标注日期、公司（Confluent）和作者（Robin Moffatt）。两篇文章分别介绍使用 Kafka Connect 和 Flink SQL 写入 Iceberg 的实践。

## 总结

该提交为 Iceberg 官方博客列表新增了两篇 Confluent 关于 Iceberg 实践的文章，内容涵盖使用 Kafka Connect 和 Flink SQL 写入 S3 上的 Iceberg 表。纯文档变更，无代码改动。
