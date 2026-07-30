# 提交 2862：site: remove site/docs/about.md (#14554)

## 提交信息

- **序号**：2862 / 4088
- **哈希**：4aca8fda8df2494d4b7c3b86370c1e5dd55e723e
- **短哈希**：4aca8fda8
- **日期**：2025-11-10 12:53:52 -0800
- **作者**：Kevin Liu
- **提交说明**：site: remove site/docs/about.md (#14554)
- **PR/Issue**：#14554

## 总体目的

这个提交删除了 `site/docs/about.md` 文件。该文件是 Iceberg 网站的一个旧版"关于"页面，内容为 Iceberg 的简短介绍（"What is Iceberg?"），包含项目的基本描述和一个指向 Spark 快速入门的"Learn More"按钮。

删除此文件的原因可能是：
1. 该页面内容已过时或被其他页面替代。
2. 在提交 2856 中，网站导航配置已更新，"about"页面并未被添加到导航中。
3. 网站重构过程中，该旧页面不再需要单独存在。

## 如何达成设计目的

直接删除 `site/docs/about.md` 文件（30 行内容全部删除），包括文件头部的 YAML front matter（Title 元数据）、Apache 许可证声明注释，以及页面正文内容。

## 修改详情

### `site/docs/about.md` (+0/-30 lines)

**修改目的**：删除不再需要的旧版"关于"页面。

**工作逻辑**：删除整个文件。被删除的内容包括：
- YAML front matter：`Title: What is Iceberg?`
- Apache 2.0 许可证声明注释
- 页面正文：Iceberg 的简短介绍文本和一个带有"Learn More"按钮的 HTML 链接（指向 `/spark-quickstart`）

## 总结

这是一个文档清理提交，删除了网站中不再需要的旧版"关于"页面。该文件包含 Iceberg 的简短介绍和导航按钮，在网站重构过程中已被替代或不再需要。删除后保持了网站文档结构的整洁。
