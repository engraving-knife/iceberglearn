# 提交 3203：Docs: add blog post for iceberg-rust 0.8.0 release (#15221)

## 提交信息

- **序号**：3203 / 4088
- **哈希**：e786514c91f07af2d1452ad67f4c2f59a10f9195
- **短哈希**：e786514c9
- **日期**：2026-02-03
- **作者**：Kevin Liu
- **提交说明**：Docs: add blog post for iceberg-rust 0.8.0 release (#15221)
- **PR/Issue**：#15221

## 总体目的

该提交为 Iceberg 官网博客新增一篇发布说明文章，宣告 `iceberg-rust` 0.8.0 版本发布。`iceberg-rust` 是 Apache Iceberg 规范的原生 Rust 实现，提供高性能的读写与管理 Iceberg 表的库，并通过 Python 绑定 `pyiceberg-core` 供 Python 生态使用。

该版本覆盖 2025 年 11 月下旬至 2026 年 1 月上旬的开发成果，合并了 37 位贡献者的 144 个 PR。文章的目的是向社区同步这一里程碑版本的核心新特性、破坏性变更、依赖升级与基础设施改进，帮助用户评估升级与采用。博客文章是 Iceberg 项目对外发布沟通的标准渠道，记录在 `site/docs/blog/posts/` 下，发布后会被站点渲染展示。

## 如何达成设计目的

新增一个 Markdown 文件，遵循 Iceberg 博客文章的 front matter 约定（`date`、`title`、`slug`、`authors`、`categories`），正文按"Release Highlights → Breaking Changes → Dependency Updates → Infrastructure Improvements → Getting Involved"结构组织，逐项列出关键改动并附 `iceberg-rust` 仓库对应 PR 链接。文件含 Apache 2.0 许可证头注释，作者署名为 `iceberg-pmc`，分类为 `release`。

## 修改详情

### `site/docs/blog/posts/2026-02-02-iceberg-rust-0.8.0-release.md` (+141/-0 lines)

**修改目的**：新增 iceberg-rust 0.8.0 发布说明博客文章。

**工作逻辑**：
文件以 front matter 开头（`date: 2026-02-02`、标题 `Apache Iceberg Rust 0.8.0 Release`、slug `apache-iceberg-rust-0.8.0-release`、作者 `iceberg-pmc`、分类 `release`），随后是 ASF 许可证注释，再介绍版本概况（144 PR / 37 贡献者，附 changelog 链接）并使用 `<!-- more -->` 标记摘要截断。

正文 Release Highlights 涵盖多个主题：

- **V3 Metadata Support**：支持 Iceberg V3 元数据格式，含 V3 manifest 的 delete file content；
- **Enhanced DataFusion Integration**：INSERT INTO 分区表、分区列投影、repartitioning 算子、`sort_by_partition` 算子、SQLLogicTest 集成、并行写入接口；
- **Advanced Delete File Handling**：共享 delete file 缓存、同一 FileScanTask 同时支持 position 与 equality delete、binary 类型支持、修复大 delete 栈溢出与 scan 死锁、row group 过滤、大小写敏感的 equality delete 匹配；
- **Enhanced Reader Capabilities**：基于位置的投影（支持无 field ID 的 Parquet）、PartitionSpec 支持、schema 演进修复、压缩元数据读取、Date32/struct 默认值、binary 反序列化、`_file` 元数据列；
- **Advanced Writers**：Clustered 与 Fanout writer、可配置 FanoutWriter、非消费式 builder；
- **Catalog Improvements**：SqlCatalog（update_table/register_table/builder）、S3TablesCatalog update_table、Glue 并发错误处理、MemoryCatalog 命名空间修复、REST catalog 鉴权与公开类型；
- **Schema Conversion**：Arrow schema 转 Iceberg schema 并自动分配 field ID；
- **PyIceberg-core Improvements**：更小产物、ABI3 (pyo3 abi3-py310)、RecordBatchTransformerBuilder API。

Breaking Changes 列出：弃用 smol 改用 tokio、MSRV 提升到 Rust 1.88、移除 `FileIO.remove_all`、SnapshotProducer 简化校验、枚举移除 wildcard 模式。Dependency Updates 列出 DataFusion 48→51、Arrow 53→57、apache-avro 0.21.0、OpenDAL v0.55、tera→minijinja。Infrastructure Improvements 涵盖并行 CI、迁移到 uv、license 检查忽略 target/、GitHub workflow 失败通知。最后是 Getting Involved 引导。

每项改动均附 `iceberg-rust` 仓库对应 PR 链接，便于读者追溯细节。

## 总结

该提交新增 iceberg-rust 0.8.0 的发布说明博客文章，系统梳理了该版本在 V3 元数据、DataFusion 集成、delete file 处理、读写器、catalog、Python 绑定等方面的进展，以及破坏性变更与依赖升级。对 Rust/Python 生态用户评估升级与采用具有实际指导价值，是项目对外沟通的重要文档产出。
