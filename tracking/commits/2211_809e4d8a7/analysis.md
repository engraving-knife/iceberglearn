# 提交 2211：Docs: Add Redpanda to vendor-related documentation (#13242)

## 提交信息

- **序号**：2211 / 4088
- **哈希**：809e4d8a7472b8e5d48e0a598fd0dd6b7bf018da
- **短哈希**：809e4d8a7
- **日期**：2025-06-05 03:12:53 -0400
- **作者**：Lea Fester
- **提交说明**：Docs: Add Redpanda to vendor-related documentation (#13242)
- **PR/Issue**：#13242

## 总体目的

这个提交是文档类改动，目的是将 Redpanda 添加到 Iceberg 官方文档的厂商相关页面中。Redpanda 是一家提供流式数据平台的公司，其 Iceberg 集成功能可以将 Kafka 消息自动转换为 Iceberg 表。本次改动在 Iceberg 的官方文档中三处添加了 Redpanda 相关内容：导航配置、博客列表、厂商页面。这有助于提升 Redpanda 作为 Iceberg 生态厂商的可发现性，同时丰富 Iceberg 官方文档对支持厂商的覆盖范围。该改动由 Redpanda 公司员工提交，属于厂商主动接入生态文档的常见协作模式。

## 如何达成设计目的

- 在 `docs/mkdocs.yml` 导航配置中新增 Redpanda 条目，链接到 Redpanda 官方关于 Iceberg topics 的文档。
- 在 `site/docs/blogs.md` 博客列表顶部新增 Redpanda 发布的关于 Apache Iceberg 表的博客文章条目。
- 在 `site/docs/vendors.md` 厂商页面中新增 Redpanda 段落，描述其流式平台与 Iceberg 的集成能力。

## 修改详情

### `docs/mkdocs.yml` (修改, +1 line)

**修改目的**：在文档导航中添加 Redpanda 入口。

**工作逻辑**：在 nav 列表中，于 Tinybird 之后、RisingWave 之前新增 `- Redpanda: https://docs.redpanda.com/current/manage/iceberg/about-iceberg-topics`，外链到 Redpanda 官方文档。

### `site/docs/blogs.md` (修改, +5 lines)

**修改目的**：在博客列表中添加 Redpanda 的博客文章。

**工作逻辑**：在博客列表最顶部（最新位置）新增 Redpanda 博客文章 "What Are Apache Iceberg Tables? Benefits and challenges"，日期 2025-05-21，作者 Redpanda。使用 `<!-- markdown-link-check-disable-next-line -->` 注释禁用链接检查，保持与其他条目一致的格式。

### `site/docs/vendors.md` (修改, +4 lines)

**修改目的**：在厂商页面中添加 Redpanda 的介绍。

**工作逻辑**：在 PuppyGraph 之后、RisingWave 之前新增 Redpanda 段落，描述 Redpanda 作为云原生和自托管流式平台，其 Iceberg topics 可自动将 Kafka 消息转换为 Iceberg 表，并提到其与 Iceberg catalog 和查询引擎的集成能力。

## 总结

该提交是纯文档改动，将 Redpanda 作为支持 Iceberg 的厂商添加到官方文档的导航、博客列表和厂商介绍页面，提升了 Iceberg 生态对 Redpanda 集成能力的可见性。改动简洁、格式规范，无代码逻辑变更。
