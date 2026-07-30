# 提交 1141：Docs: Add blogs written by rmoff (#11069)

## 提交信息

- **序号**：1141 / 4088
- **哈希**：026166f2292ef3462e1008ca0a0fc35ab709ba50
- **短哈希**：026166f22
- **日期**：2024-09-10（Tue Sep 10 10:13:06 2024 +0100）
- **作者**：Robin Moffatt <robin@rmoff.net>
- **提交说明**：Docs: Add blogs written by rmoff (#11069)
- **PR/Issue**：#11069

## 总体目的

Iceberg 官网在 `site/docs/blogs.md` 维护一份"社区博客"列表，按时间倒序收录讨论 Iceberg 的第三方公司/个人博客文章，方便用户发现更多实践与教程。本提交由作者 Robin Moffatt（rmoff）本人发起，把自己撰写的三篇与 Iceberg 相关的博客加入该列表。

新增的三篇博客：

1. **Sending Data to Apache Iceberg from Apache Kafka with Apache Flink**（2024-07-18，Decodable）——讲解用 Flink 把 Kafka 数据写入 Iceberg。
2. **How to get data from Apache Kafka to Apache Iceberg on S3 with Decodable**（2024-06-18，Decodable）——讲解用 Decodable 把 Kafka 数据落到 S3 上的 Iceberg 表。
3. **lakeFS ♥️ Apache Iceberg**（2023-06-26，LakeFS）——讲解 lakeFS 与 Iceberg 的结合使用。

前两篇按时间顺序插入到列表顶部区域（2024 年区段），第三篇插入到 2023 年区段对应位置。每条条目使用与列表已有条目一致的格式：`<!-- markdown-link-check-disable-next-line -->` 注释（禁用该行的 markdown 链接检查，避免 CI 因外链失效报错）+ 三级标题链接 + 日期/公司 + 作者 LinkedIn 链接。

## 如何达成设计目的

在 `site/docs/blogs.md` 中按时间倒序找到每篇博客应插入的位置（前两篇在 2024 年区段、第三篇在 2023 年区段），在对应位置插入符合现有格式的条目。纯文档变更，无代码或构建逻辑。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：补充 rmoff 撰写的三篇 Iceberg 博客到社区博客列表。

**工作逻辑**：

1. 在列表开头引导语 `Here is a list of company blogs...` 之后、原第一条（End-to-End Basic Data Engineering Tutorial，2024-04-01 Dremio）之前，插入两篇 2024 年的 Decodable 博客：

```markdown
<!-- markdown-link-check-disable-next-line -->
### [Sending Data to Apache Iceberg from Apache Kafka with Apache Flink](https://www.decodable.co/blog/kafka-to-iceberg-with-flink)
**Date**: July 18th, 2024, **Company**: Decodable

**Author**: [Robin Moffatt](https://www.linkedin.com/in/robinmoffatt)

<!-- markdown-link-check-disable-next-line -->
### [How to get data from Apache Kafka to Apache Iceberg on S3 with Decodable](https://www.decodable.co/blog/kafka-to-iceberg-with-decodable)
**Date**: June 18th, 2024, **Company**: Decodable

**Author**: [Robin Moffatt](https://www.linkedin.com/in/robinmoffatt)
```

这两篇按时间倒序排列（7 月在前、6 月在后），紧接其后是 4 月的 Dremio 文章，顺序正确。

2. 在 2023 年区段，原 `How Bilibili Builds OLAP Data Lakehouse with Apache Iceberg`（2023-06-14 Bilibili）之前，插入 lakeFS 博客（2023-06-26，时间晚于 Bilibili 那篇，所以在其前面）：

```markdown
<!-- markdown-link-check-disable-next-line -->
### [lakeFS ♥️ Apache Iceberg](https://lakefs.io/blog/using-lakefs-with-apache-iceberg/)
**Date**: June 26th, 2023, **Company**: LakeFS

**Author**: [Robin Moffatt](https://www.linkedin.com/in/robinmoffatt)
```

所有条目格式与文件已有条目完全一致：三级标题为链接、`**Date**`/`**Company**` 行、`**Author**` 行带 LinkedIn 链接、行前的 `<!-- markdown-link-check-disable-next-line -->` 注释。

## 小结

- **成效**：社区博客列表新增三篇 rmoff 撰写的 Iceberg 实践文章（Kafka→Iceberg via Flink、Kafka→Iceberg on S3 via Decodable、lakeFS + Iceberg），丰富了官网可发现的学习资源。
- **影响范围**：仅 `site/docs/blogs.md` 一个文件，新增 18 行（三个条目，每个 6 行），无代码或构建变更。
- **回迁到 1.4.x 的注意事项**：这是纯文档（博客列表）补充，对 1.4.x 运行时无任何影响。文档由 main 分支统一维护，1.4.x 通常不需要单独回迁博客列表更新，**无需回迁**。即便 1.4.x 不带此更新，也不影响其发布产物或功能。
