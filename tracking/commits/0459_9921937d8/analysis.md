# 提交 0459：Update blogs.md (#9552)

## 提交信息

- **序号**：0459
- **完整哈希**：9921937d8285dec9a19fd16b0cd82d451a8aca9e
- **短哈希**：9921937d8
- **日期**：2024-02-05 07:41:19 +0530
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Update blogs.md (#9552)，Add the recent blog about "How not to use Apache Iceberg"
- **关联 PR**：#9552
- **修改文件**：1 个，共 5 行新增、0 行删除

## 总体目的

本提交是 Iceberg 官方网站文档的一项内容维护更新。Iceberg 项目在 `site/docs/blogs.md` 中维护了一份按时间倒序排列的社区博客清单，收录各类公司与个人撰写的关于 Iceberg 的技术博客。随着社区持续产出新内容，需要定期将该清单补充更新，以便用户能够发现最新的学习资源与最佳实践讨论。

本次更新添加了作者 Ajantha Bhat（来自 Dremio）于 2024 年 1 月 23 日发布在 Medium 上的博客文章 "How not to use Apache Iceberg"。从标题可以看出，这是一篇反向视角的使用指南，关注的是使用 Iceberg 时应当避免的常见误区与反模式，对于帮助用户正确采用 Iceberg 具有参考价值。

由于博客清单严格按发布时间从新到旧排列，新增条目被插入到列表顶部，即位于此前最新一篇（2023 年 10 月 12 日的 "Apache Hive-4.x with Iceberg Branches & Tags"）之前。

## 如何达成设计目的

实现路径非常直接：在 `site/docs/blogs.md` 文件的博客列表起始处，依照该文件既有的条目格式（三级标题链接 + 加粗的日期与公司 + 加粗的作者链接）新增一条记录。条目内容遵循现有模板，包含博客标题的 Markdown 链接、发布日期、所属公司以及作者的个人 LinkedIn 链接。

## 修改详情

### site/docs/blogs.md

**修改目的**：在 Iceberg 社区博客清单中新增一篇 2024 年 1 月发布的博客条目。

**工作逻辑**：在文件第 22 行（"Here is a list of company blogs..."引导语之后、原有第一条博客条目之前）插入一个新的博客条目块。新条目包含：

1. 三级标题行 `### [How not to use Apache Iceberg](https://medium.com/@ajanthabhat/how-not-to-use-apache-iceberg-046ae7e7c884)`，将博客标题渲染为指向 Medium 文章的链接。
2. `**Date**: January 23rd, 2024, **Company**: Dremio`，标注发布日期与作者所属公司。
3. `**Authors**: [Ajantha Bhat](https://www.linkedin.com/in/ajanthabhat/)`，标注作者并链接到其 LinkedIn 主页。

该条目置于列表最前，保持"most recent to oldest"的排列约定。原有第一条（"Apache Hive-4.x with Iceberg Branches & Tags"）及其后续条目顺序不变。

## 小结

本提交是一次纯文档内容更新，不涉及任何代码或配置逻辑变更。其价值在于保持 Iceberg 官方博客清单的时效性，让社区用户能够及时获取关于 Iceberg 使用误区的新近讨论。提交体量极小（5 行新增），属于低风险的文档维护工作。
