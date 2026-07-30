# 提交 3453：Docs: Add blog post for Iceberg Rust 0.9.0 release (#15744)

## 提交信息

- **序号**：3453 / 4088
- **哈希**：7153d0a3fec21032c974e6f73755574aa6272e6f
- **短哈希**：7153d0a3fe
- **日期**：2026-03-24 08:23:24 -0700
- **作者**：Matt Butrovich
- **提交说明**：Docs: Add blog post for Iceberg Rust 0.9.0 release (#15744)
- **PR/Issue**：#15744

## 总体目的

为 Apache Iceberg Rust 0.9.0 版本发布添加博客文章。该版本覆盖了 2026 年 1 月至 3 月的开发工作，合并了来自 28 位贡献者的 109 个 PR，包括 8 位新贡献者。

## 如何达成设计目的

- 在 `site/docs/blog/posts/` 目录下新增博客文章文件
- 文章涵盖版本发布的亮点功能

## 修改详情

### `site/docs/blog/posts/2026-03-10-iceberg-rust-0.9.0-release.md` (+122/-0 lines)

**修改目的**：添加 Iceberg Rust 0.9.0 发布博客文章。

**工作逻辑**：

博客文章内容包括：

1. **版本概览**：0.9.0 版本，109 个 PR，28 位贡献者，8 位新贡献者

2. **发布亮点**：
   - **基于 Trait 的存储架构**：新的 `Storage` trait 解耦存储后端，包含 LocalFsStorage 和 MemoryStorage 两个原生实现，OpenDAL 存储移至独立 crate
   - **增强的 DataFusion 集成**：CREATE TABLE、DROP TABLE、LIMIT pushdown、谓词 pushdown（Boolean、IsNaN、Timestamp、Binary、LIKE）、INSERT INTO 支持
   - **读取性能改进**：字节范围合并、单线程快速路径、元数据大小提示、文件大小传播
   - **写入路径改进**：Parquet 写入器重构、数据文件提交、分区写入支持
   - **Python 绑定（pyiceberg-core）**：表创建、schema 查询、数据扫描支持
   - **目录改进**：REST 目录认证增强、Glue 目录改进
   - **规范合规性**：清单列表写入、schema 序列化

3. **社区感谢**：感谢所有贡献者

## 总结

该提交添加了 Iceberg Rust 0.9.0 版本发布的博客文章，详细介绍了该版本的主要功能亮点，包括基于 trait 的存储架构、DataFusion 集成增强、读取性能改进、写入路径改进、Python 绑定和目录改进等。
