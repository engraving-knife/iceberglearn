# 提交 2336：Docs: Add Ryft to list of vendors and blog posts (#13504)

## 提交信息

- **序号**：2336 / 4088
- **哈希**：5d938124848487e46a2703d25e8294953f53841a
- **短哈希**：5d9381248
- **日期**：2025-07-10 13:53:18 -0600
- **作者**：Yuval Yogev
- **提交说明**：Docs: Add Ryft to list of vendors and blog posts (#13504)
- **PR/Issue**：#13504

## 总体目的

本提交将 Ryft 公司添加到 Iceberg 官方网站的厂商列表（vendors）和博客列表（blogs）中。Ryft 是一个完全自动化的 Iceberg 管理平台，提供表优化、合规管理和数据生命周期管理等功能。

Iceberg 社区维护着厂商和博客列表，用于展示生态系统中使用 Iceberg 的公司和相关技术文章。当一个新厂商或新的技术博客文章出现时，通过提交 PR 将其添加到对应文档中，帮助用户了解 Iceberg 生态的广度。

## 如何达成设计目的

在两个文档文件中分别添加 Ryft 的厂商描述和博客文章条目，按照文档现有的格式和排序约定（博客按日期从新到旧排列，厂商按字母顺序排列）插入。

## 修改详情

### `site/docs/blogs.md` (+5/-0 lines)

**修改目的**：添加 Ryft 的博客文章条目。

**工作逻辑**：在博客列表顶部（最新位置）添加 Ryft 于 2025 年 7 月 9 日发布的博客文章"Making Sense of Apache Iceberg Statistics"，作者为 Guy Yasoor。条目包含 markdown 链接检查禁用注释、标题链接、日期、公司和作者信息，格式与其他博客条目一致。

### `site/docs/vendors.md` (+5/-0 lines)

**修改目的**：添加 Ryft 厂商描述。

**工作逻辑**：在 RisingWave 和 SingleStore 之间（字母序 R 之后、S 之前）添加 Ryft 的厂商条目。描述了 Ryft 作为完全自动化的 Iceberg 管理平台的核心能力：实时维护和优化 Iceberg 表、智能压缩、合规管理、灾难恢复和数据生命周期管理，以及与现有 catalog、存储和查询引擎的集成能力。

## 总结

本提交是纯文档更新，将 Ryft 公司及其博客文章添加到 Iceberg 官方网站的厂商和博客列表中，丰富了 Iceberg 生态系统的展示内容。
