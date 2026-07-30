# 提交 3823：Site: Add 1.11.0 release blog post (#16516)

## 提交信息

- **序号**：3823 / 4088
- **哈希**：5ec9bd09037caf5c221a1a4ab167f33d50ce6a4a
- **短哈希**：5ec9bd090
- **日期**：2026-06-04 09:20:47 -0700
- **作者**：Aihua Xu <aihuaxu@gmail.com>
- **提交说明**：Site: Add 1.11.0 release blog post (#16516)
- **PR/Issue**：#16516
- **协作者**：gaborkaszab、Claude Sonnet 4.6

## 总体目的

本提交在 Iceberg 官方网站博客栏目发布 Apache Iceberg 1.11.0 版本的发布博客文章。1.11.0 是一个重要版本，包含超过 1000 个提交、200+ 贡献者的工作。博客文章旨在向社区宣告此次发布，并系统梳理本版本在 REST catalog 协议、OpenAPI 规范、规范扩展（SQL UDF、地理空间类型）、性能与可靠性、以及各引擎集成等方面的主要新特性与改进，方便用户了解升级价值与迁移要点。

文章经过多轮评审修订（提交说明中可见多次 "Address review comments"），并由 AI（Claude Sonnet 4.6）协助起草与修改，体现了项目在重要发布沟通上的人机协作流程。

## 如何达成设计目的

通过在 `site/docs/blog/posts/` 目录下新增一个 Markdown 博客文件（文件名含日期 2026-05-19 与版本 slug），遵循 Iceberg 网站博客的 front matter 格式，正文用 `<!-- more -->` 分隔摘要与详细内容，按主题分类详细罗列本次发布亮点并附 PR 链接供深入查阅。文章还包含 Breaking Changes、迁移建议等章节。

## 修改详情

### `site/docs/blog/posts/2026-05-19-iceberg-1.11.0-release.md` (+198/-0 lines, new file)

**修改目的**：发布 Iceberg 1.11.0 版本博客。

**工作逻辑**：
文章结构涵盖：
- **概述**：宣布 1.11.0 发布，1000+ 提交、200+ 贡献者，指向 release notes。
- **REST Catalog: A More Complete Protocol**：详细介绍 REST catalog 协议的重大进展，包括：
  - 远程 scan planning（服务端规划扫描，减少客户端内存压力，扩展到增量扫描与元数据表，支持按表 opt-out）。
  - Freshness-aware table loading（基于 ETag 的元数据缓存，304 响应减少往返）。
  - Idempotency key 支持（幂等键保证重试不重复执行）。
  - Register View（REST API 注册视图，补全视图生命周期）。
  - 自定义 Table/View Operations（可注入扩展）。
- **OpenAPI Specification Updates**：命名空间分隔符可由服务端配置、register-view 端点、`CommitTableResponse` 的 ETag、S3 signing 端点提升到主规范、分区统计纳入 `TableUpdate`、scan planning 响应携带存储凭证。
- **Spec: SQL UDFs and Geospatial Types**：SQL UDF 规范（版本化、多方言、跨引擎）、地理空间边界框类型与 `INTERSECTS` 谓词、V3 几何类型限制澄清、`added-rows` 快照字段、`referenced-by` 依赖追踪、`scan-planning-mode` 广告、config 端点对缺失 warehouse 返回 404。
- **Performance and Reliability**：LIMIT 下推、向量化读取扩展到更多 Parquet 编码（BYTE_STREAM_SPLIT、DELTA_LENGTH_BYTE_ARRAY、DELTA_BYTE_ARRAY）、快照过期清理模式、唯一表位置（UUID 防止 DeleteOrphanFiles 误删）、AWS S3FileIO 与 GCS FileIO 定时凭证刷新、GCSAnalyticsCore 集成。
- 引擎集成、Breaking Changes、迁移建议等章节。

## 总结

本提交是项目对外发布 Iceberg 1.11.0 版本的博客文章，属于文档/沟通类工作，不涉及代码逻辑改动。文章系统梳理了 1.11.0 在 REST catalog 协议、OpenAPI 规范、SQL UDF/地理空间类型规范、性能与可靠性等方面的重大进展，为用户提供了清晰的升级与迁移指引。1.11.0 是 REST catalog 协议自引入以来最重大的演进，本博客有效地传达了这一里程碑。文章由人与 AI（Claude Sonnet 4.6）协作完成，体现了 AI 辅助在项目对外沟通文档撰写中的应用。
