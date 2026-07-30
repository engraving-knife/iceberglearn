# 提交 3885：Docs: add blog post for C++ 0.3.0 release (#16817)

## 提交信息

- **序号**：3885 / 4088
- **哈希**：12b7f7de01511c3e008545903269a88548afa050
- **短哈希**：12b7f7de0
- **日期**：2026-06-15 11:06:52 +0800
- **作者**：Junwang Zhao
- **提交说明**：Docs: add blog post for C++ 0.3.0 release (#16817)
- **PR/Issue**：#16817

## 总体目的

为 Apache Iceberg C++ 0.3.0 版本发布添加官方博客文章。Iceberg C++ 是 Iceberg 表格式的原生 C++ 实现，提供在 C++ 应用中读取、写入和管理 Iceberg 表的库。此次 0.3.0 是一个重要里程碑版本，包含超过 140 个 PR 和 23 位贡献者（其中 11 位首次贡献者）的工作。

博客文章的目的是向社区宣布发布、展示版本亮点、感谢贡献者并引导社区参与。

## 如何达成设计目的

在 `site/docs/blog/posts/` 目录下新增一篇 Markdown 格式的博客文章，遵循 Iceberg 网站的博客模板，包含 front matter（日期、标题、作者、分类）和正文内容。

## 修改详情

### `site/docs/blog/posts/2026-06-14-iceberg-cpp-0.3.0-release.md` (+102 lines, new file)

**修改目的**：新增 C++ 0.3.0 发布博客文章。

**工作逻辑**：
博客文章包含以下部分：
1. **Front matter**：声明发布日期、标题、作者（iceberg-pmc）、分类（release）
2. **概述**：宣布 0.3.0 发布，统计 140+ PR、23 位贡献者、11 位首次贡献者
3. **Release Highlights**（发布亮点），分为五个方面：
   - Scan Planning and Data Access：增量扫描 API、MOR 数据访问、列选择、位置删除位图等
   - Table Operations and Maintenance：MergingSnapshotUpdate、SnapshotManager、快照过期清理等
   - Catalogs and Integrations：REST catalog 改进（OAuth2、基本认证等）、S3 FileIO、SQL catalog
   - Metrics and Observability：指标报告器、Avro/Parquet writer 指标
   - Metadata and File Format Support：Puffin 支持、Iceberg v3 未知类型和纳秒时间戳、表达式序列化
4. **Contributors**：列出所有贡献者及贡献数，欢迎 11 位首次贡献者
5. **Roadmap for 0.4.0**：下一步聚焦 Iceberg v3 支持和表维护 API
6. **Getting Involved**：引导社区参与

## 总结

为 Iceberg C++ 0.3.0 发布新增官方博客文章，全面展示了该版本在扫描规划、表操作、目录集成、可观测性和元数据格式支持等方面的重大进展。这是 C++ 实现迈向成熟的重要里程碑，也体现了 Iceberg 生态多语言发展的战略方向。
