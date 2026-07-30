# 提交 0667：Docs: Add Upsolver to vendor list

## 提交信息

- **序号**：0667 / 4088
- **哈希**：a351e22b6d24a96e059cd668447db3adaa3ddd52
- **短哈希**：a351e22b6
- **日期**：2024-04-09 10:33:31 +0300
- **作者**：Jason <jasonf20@gmail.com>
- **提交说明**：Docs: Add Upsolver to vendor list (#10096)
- **PR/Issue**：#10096

## 总体目的

本提交是一个**文档类改动**：在 Iceberg 官方网站的厂商列表页面（`site/docs/vendors.md`）中新增 Upsolver 作为支持 Apache Iceberg 的商业厂商/供应商。

### 背景

Iceberg 的生态系统持续扩张，越来越多的商业产品基于或集成 Apache Iceberg。`vendors.md` 页面用于列出所有提供 Iceberg 相关产品或服务的商业厂商，帮助用户了解可用的商业方案。在本次提交之前，该页面已列出 Tabular、Starburst 等厂商，但缺少 Upsolver。Upsolver 是一家提供流式数据摄入和表管理解决方案的厂商，其产品支持将批量和流式数据从文件、流和数据库（CDC）摄入到 Iceberg 表中，并能连接现有的 REST 和 Hive catalog，分析表的健康状况，以及持续优化表（压缩小文件、排序、压缩、重新分区、清理悬挂文件和过期清单等）。

## 如何达成设计目的

在 `site/docs/vendors.md` 文件末尾（Tabular 条目之后）新增一个 Upsolver 的条目，遵循已有厂商条目的格式：三级标题（厂商名称带链接）+ 一段描述性文字说明该厂商的产品和服务。

## 修改详情

### `site/docs/vendors.md`

**修改目的**：在厂商列表中新增 Upsolver 条目。

**工作逻辑**：在文件末尾（第 71 行之后）新增 4 行内容：
- 一个空行作为分隔
- 三级标题 `### [Upsolver](https://upsolver.com)`，带厂商官网链接
- 一个空行
- 一段描述文字，介绍 Upsolver 的核心功能：
  - 流式数据摄入和表管理解决方案
  - 支持从文件、流和数据库（CDC）摄入批量和流式数据到 Iceberg 表
  - 连接现有的 REST 和 Hive catalog
  - 分析表的健康状况
  - 持续优化表（压缩小文件、排序、压缩、重新分区、清理悬挂文件和过期清单）
  - 提供 Upsolver Cloud 或部署在 AWS VPC 中两种使用方式

描述中包含多个外部链接，分别指向 Upsolver 的 Iceberg 表文档、表健康分析文档和注册页面。

## 小结

- **成效**：成功将 Upsolver 添加到 Iceberg 的厂商列表，丰富了生态系统的商业方案展示。
- **影响范围**：仅影响网站文档页面 `site/docs/vendors.md`，不涉及任何代码修改。
- **回迁到 1.4.x 的注意事项**：纯文档改动，回迁无风险。直接 cherry-pick 即可。
