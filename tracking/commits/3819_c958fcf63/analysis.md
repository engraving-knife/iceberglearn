# 提交 3819：iceberg go 0.6.0 release blog (#16649)

## 提交信息

- **序号**：3819 / 4088
- **哈希**：c958fcf6328d36c78d853acc1774c6c1a75e028e
- **短哈希**：c958fcf6
- **日期**：2026-06-02 14:24:42 -0700
- **作者**：Neelesh Salian <nssalian@users.noreply.github.com>
- **提交说明**：iceberg go 0.6.0 release blog (#16649)
- **PR/Issue**：#16649

## 总体目的

本提交在 Iceberg 官方网站博客栏目发布一篇介绍 `apache/iceberg-go` 0.6.0 版本发布的博客文章。`iceberg-go` 是 Apache Iceberg 表格式的原生 Go 语言实现，提供在 Go 应用中读写、管理 Iceberg 表的库。0.6.0 版本自 0.5.0（2026 年 3 月发布）以来历经约三个月开发，合并了近 200 个 PR，由 40 位贡献者参与，其中 26 位是首次贡献者。

博客文章旨在向社区宣告此次发布，并详细列出本次版本的主要新特性与改进，方便 Go 生态用户了解 `iceberg-go` 的能力进展和升级价值。这是项目对外沟通和版本宣传的常规工作。

## 如何达成设计目的

通过在 `site/docs/blog/posts/` 目录下新增一个 Markdown 博客文章文件（文件名包含日期与版本 slug），遵循 Iceberg 网站的博客 front matter 格式（date、title、slug、authors、categories），正文用 `<!-- more -->` 分隔摘要与正文，按主题分类罗列本次发布的亮点特性，并附上对应 PR 链接供读者深入查看。

## 修改详情

### `site/docs/blog/posts/2026-06-01-iceberg-go-0.6.0-release.md` (+157/-0 lines, new file)

**修改目的**：发布 iceberg-go 0.6.0 版本博客。

**工作逻辑**：
文章结构：
- **front matter**：日期 2026-06-01，标题 "Apache Iceberg Go 0.6.0 Release"，作者 iceberg-pmc，分类 release。
- **概述**：宣布 0.6.0 发布，说明自 0.5.0 以来约三个月、近 200 PR、40 贡献者（26 首次），指向完整 changelog。
- **Release Highlights** 按主题分组介绍主要特性：
  - **Iceberg V3 Table Spec Support**：Variant 类型、Deletion vectors（读写 API、校验）、Row lineage、纳秒时间戳（Parquet 与 Arrow 映射）、Defaults（write-default/initial-default）、Format-version gating（V3/V2 字段按版本门控）、`Transaction.UpgradeFormatVersion` API。
  - **Row-Level Deletes**：完整 equality-delete 路径（写、读、分区表）、RowDelta 原子行级变更 API、Overwrite with deletes。
  - **Table Maintenance and Compaction**：`RewriteDataFiles` 压缩与 bin-pack 策略、`Analyze` dry-run、dangling equality deletes 清理、`RewriteFiles` snapshot-op builder。
  - **Concurrency and Conflict Resolution**：冲突校验框架并接入 producers、`doCommit` 重试与 refresh-and-replay、`ErrCommitFailed` 包装、RowDelta 分区级冲突检查。
  - **Hadoop Catalog**：完整的 Hadoop catalog 实现（脚手架、命名空间与表操作、CLI 集成）。
  - **Catalog Improvements**：`TransactionalCatalog` 多表提交、OAuth token/vended credential 刷新、audience/resource 参数、Hive catalog register table/create view、REST `RegisterView`。
- 文章末尾感谢贡献者并指向 GitHub release 与 changelog。

## 总结

本提交是项目对外发布 iceberg-go 0.6.0 版本的博客文章，属于文档/沟通类工作，不涉及代码逻辑改动。文章详细梳理了 0.6.0 在 V3 规范支持、行级删除、表维护、并发冲突、Hadoop catalog、catalog 改进等方面的进展，为 Go 生态用户提供了清晰的升级指引。这体现了 Iceberg 社区对各语言实现（Java 之外的 Go）生态建设的重视。
