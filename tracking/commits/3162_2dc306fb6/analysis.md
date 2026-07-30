# 提交 3162：Docs: add blog post for C++ 0.2.0 release (#15141)

## 提交信息

- **序号**：3162 / 4088
- **哈希**：2dc306fb6173fb7417c68e7de9ed10639d9678c0
- **短哈希**：2dc306fb6
- **日期**：2026-01-26 17:47:51 -0800
- **作者**：Gang Wu
- **提交说明**：Docs: add blog post for C++ 0.2.0 release
- **PR/Issue**：#15141

## 总体目的

Apache Iceberg C++ 库（`apache/iceberg-cpp`）即将发布 0.2.0 版本，这是该库继 0.1.0 之后的一个重要里程碑版本，合并了超过 200 个 PR、来自 23 位贡献者。社区需要在 Iceberg 官网博客发布一篇发布说明文章，向用户公示本次发布的核心能力亮点、贡献者名单与后续路线图，同时为 C++ 生态用户提供了获取与参与项目的入口。本提交即新增该博客文章。

文章内容与紧随其前的 #15107（在实现状态矩阵加入 C++ 列）相互呼应：#15107 是结构化的能力矩阵公示，本提交是面向社区的故事性发布通告，二者共同标志着 Iceberg C++ 库正式进入官方生态宣传序列。文章由 Gang Wu 起草，Kevin Liu 参与了评审与修订（作为 Co-authored-by）。

## 如何达成设计目的

在 `site/docs/blog/posts/` 目录下新增一篇符合 MkDocs Material 博客插件 front matter 规范的 Markdown 文件，按"发布亮点（分类罗列）— 贡献者统计 — 路线图 — 参与方式"的结构组织内容，并通过 `<!-- more -->` 分隔摘要与正文以控制博客列表的摘要展示。

## 修改详情

### `site/docs/blog/posts/2026-01-26-iceberg-cpp-0.2.0-release.md` (+101/-0 lines, 新增)

**修改目的**：新增 C++ 0.2.0 版本发布博客文章。

**工作逻辑**：文件 front matter 设置 `date: 2026-01-26`、`title: Apache Iceberg C++ 0.2.0 Release`，作者为 `iceberg-pmc`，分类为 `release`，符合 MkDocs Material 博客插件的元数据约定。正文结构如下：

- **开篇摘要**：宣布 0.2.0 发布，统计为"超过 200 个合并 PR、23 位贡献者"，并链接到 `v0.1.0...v0.2.0` 的对比与完整 changelog。`<!-- more -->` 之后的内容在博客列表页折叠，仅展示摘要。
- **Release Highlights**：按能力域分组列出本次发布亮点——Table Scan and Data Access（v2 删除与元数据列读取、ManifestReader 投影与过滤、Arrow C Stream 集成的文件扫描任务读取器）、Table Operations（Schema 演进、表更新属性/排序/分区/位置/统计、带快照管理的事务 API）、REST Catalog（完整客户端含 namespace 与表 CRUD、集成测试）、Expression System（完整表达式框架、类型转换、二进制序列化、metrics/manifest/residual 评估器、聚合表达式与投影）、Performance and I/O（优化 Avro 读写器、可配置 Avro/Parquet 读写器）、Catalog and Metadata（InMemoryCatalog、Location provider、Schema 选择/投影、表元数据构建器）、Miscellaneous（Meson 构建、初始文档网站与 devcontainer、代码组织与类型安全改进）。
- **Contributors**：通过 `git shortlog` 统计（排除 dependabot）列出 23 位贡献者及其提交数，向社区致谢。
- **Roadmap for 0.3.0**：链接到 GitHub issue #523 说明下一个版本在推进中。
- **Getting Involved**：提供 GitHub Issues 与 dev 邮件列表参与入口。

这些亮点与 #15107 中公示的 C++ 能力矩阵一致（如 REST Catalog 全表操作支持、Parquet/Avro 支持、扫描与删除文件规划支持等），具有实际的版本选型与能力了解指导价值。

## 总结

本提交通过新增 C++ 0.2.0 版本发布博客文章，向社区正式通告该里程碑版本的核心能力亮点、贡献者阵容与后续路线图，与实现状态矩阵的 C++ 列公示形成呼应，为 C++ 生态用户提供了权威的版本信息与参与入口，提升了 Iceberg C++ 库的可见度与社区参与度。
