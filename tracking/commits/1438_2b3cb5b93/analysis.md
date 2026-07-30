# 提交 1438：Docs: Add blog post showing Nussknacker with Iceberg integration (#11667)

## 提交信息

- **序号**：1438 / 4088
- **哈希**：2b3cb5b935996b5a7b4322b6fe051a2bc76bd209
- **短哈希**：2b3cb5b93
- **日期**：2024-11-27（Wed Nov 27 14:52:19 2024 +0100）
- **作者**：Arek Burdach <arek.burdach@gmail.com>
- **提交说明**：Docs: Add blog post showing Nussknacker with Iceberg integration (#11667)
- **PR/Issue**：#11667
- **作用模块**：`site/docs/blogs.md`（Iceberg 网站博客索引页）

## 总体目的

Iceberg 官方网站维护一份「blogs.md」页面，按时间倒序列出社区与各公司发布的与 Apache Iceberg 相关的博客文章，便于用户发现 Iceberg 的实际使用案例、最佳实践与生态集成。该列表是社区展示生态活力的窗口，也是用户寻找参考实现的重要入口。

Nussknacker 是一个开源的低代码流处理与实时决策平台，支持可视化构建数据处理流。其官方博客发布了一篇「Using Nussknacker with Apache Iceberg: Periodical report example」的文章，演示了 Nussknacker 与 Iceberg 的集成示例（周期性报表场景）。本提交把该博客文章登记到 Iceberg 官网的博客列表中，让关注 Nussknacker + Iceberg 集成方案的用户能够发现这篇内容，同时丰富 Iceberg 生态案例库。

## 如何达成设计目的

直接在 `site/docs/blogs.md` 文件中、按既有的博客条目格式（标题 + 日期 + 公司 + 作者），在合适的时间序位置（2024 年 9 月 27 日，紧随 Alex Merced 的文章之后、Dremio 9 月 20 日文章之前）插入一条新条目。同时在条目前加 `<!-- markdown-link-check-disable-next-line -->` 注释，与其它条目保持一致——这是仓库 CI 中 markdown-link-check 工具的约定，用于在该行禁用外链有效性检查（避免外部链接偶发不可用导致 CI 失败）。改动是纯文档登记，无任何代码或构建逻辑。

## 修改详情

### `site/docs/blogs.md`

**修改目的**：在博客列表中登记 Nussknacker 与 Iceberg 集成的博客文章。

**工作逻辑**：在原有 Alex Merced 文章条目之后、Dremio 文章条目之前插入：

```markdown
<!-- markdown-link-check-disable-next-line -->
### [Using Nussknacker with Apache Iceberg: Periodical report example](https://nussknacker.io/blog/nussknacker-iceberg-example)
**Date**: September 27th, 2024, **Company**: Nussknacker

**Author**: [Arkadiusz Burdach](https://www.linkedin.com/in/arekburdach/)
```

格式与上下文其它条目完全一致：三级标题包含文章标题与外链、`Date` 与 `Company`、`Author` 与 LinkedIn 链接。共新增 6 行，无删除。条目按时间倒序置于 2024-09-27 的位置（介于 9 月 Alex Merced 文章与 9 月 20 日 Dremio 文章之间，符合列表的倒序排列约定）。

## 小结

- **成效**：在 Iceberg 官网博客索引页登记了一篇 Nussknacker 与 Iceberg 集成的示例博客，丰富了生态案例库，便于用户发现该集成方案；条目格式与既有约定一致。
- **影响范围**：仅 `site/docs/blogs.md` 一个文件，新增 6 行；纯文档登记，无代码、构建或测试影响。
- **回迁到 1.4.x 的注意事项**：这是网站内容（博客索引）更新，与产品版本功能无关。1.4.x 维护分支通常不单独维护网站博客列表（网站内容由 main 分支统一发布），无需回迁。即便 1.4.x 分支的 `blogs.md` 与 main 不同步，也不影响 1.4.x 的发布产物。
