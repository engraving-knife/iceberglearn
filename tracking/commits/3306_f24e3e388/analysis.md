# 提交 3306：Docs: Add blog post about File Format API (#15380)

## 提交信息

- **序号**：3306 / 4088
- **哈希**：f24e3e3882edf332dd7c19a5597377ee5368170b
- **短哈希**：f24e3e388
- **日期**：2026-02-23
- **作者**：pvary
- **提交说明**：Docs: Add blog post about File Format API (#15380)
- **PR/Issue**：#15380

## 总体目的

Apache Iceberg 长期以来原生支持 Parquet、Avro、ORC 三种文件格式，但格式处理代码是随各引擎（Spark、Flink、Java 通用实现）的成熟而有机增长的，导致逻辑碎片化、大量分支判断、各格式/引擎组合下的功能支持不一致。新格式（如 Vortex、Lance）强调极速随机访问、GPU 原生编码、内置索引等能力，旧架构难以干净地集成这些格式。为解决这一架构瓶颈，社区设计并最终确立了 File Format API。

本提交新增一篇博客文章，正式向社区宣布 File Format API 的定稿。文章面向 Iceberg 用户和引擎开发者，解释了为什么需要新 API、新 API 提供了什么能力、当前状态以及后续路线图（TCK、Vortex 集成、Column Families 等）。这是一个面向社区的公告类文档提交，配合即将发布的 1.11.0 版本，帮助用户了解这一重大架构里程碑的意义。

## 如何达成设计目的

通过在 `site/docs/blog/posts/` 目录下新增一篇 Markdown 博客文章，配以三张架构示意图（注册模型图、注册表图、读写构建器图），用图文并茂的方式阐述 File Format API 的核心概念（`FormatModel`、`FormatModelRegistry`、Read/Write Builders）及其解锁的能力。文章采用 Docusaurus 博客前matter 格式，作者署名为 iceberg-pmc，分类为 announcement。

## 修改详情

### `site/docs/blog/posts/2026-02-20-file-format-api.md` (+139 lines)

**修改目的**：新增博客文章，宣布 File Format API 定稿。

**工作逻辑**：
文章结构分为几个部分：首先阐述新 API 的必要性（碎片化逻辑、大型分支代码路径、功能支持不均、生态创新加速），然后介绍核心概念——`FormatModel` 描述格式的名称/读写构建/配置能力，`FormatModelRegistry` 作为注册表解耦引擎与具体格式，读写操作通过 `FormatModelRegistry.readBuilder(...)` / `dataWriteBuilder(...)` 等方法获取构建器。接着说明新 API 解锁的能力（新格式集成如 Vortex/Lance、Column Families 垂直分割存储），并给出当前状态（API 已定稿、通用模型已实现、引擎集成已合并、TCK 进行中）。最后列出后续步骤：Comet 格式模型迁移、Vortex 集成、完成 TCK、Column Families 实现。

### `site/docs/assets/images/2026-02-20-file-format-api-*.png` (3 个图片文件)

**修改目的**：为博客文章提供架构示意图。

**工作逻辑**：
三张 PNG 图片分别展示：Spark 模型中 `FormatModel` 的角色、`FormatModelRegistry` 的注册表结构、以及通过 Registry 获取读写构建器的关系图。图片以二进制形式新增，辅助文字说明 API 的架构设计。

## 总结

本提交是一篇面向社区的公告类博客，宣布 File Format API 这一使文件格式可插拔、一致化、引擎无关的重大架构里程碑正式定稿，将在 1.11.0 版本中发布。文档详细解释了新 API 的动机、核心概念和后续路线图，对引导社区参与新格式集成和 TCK 开发具有重要指导价值。
