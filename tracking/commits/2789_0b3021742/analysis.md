# 提交 2789：Docs: Remove expired Tabular links (#14399)

## 提交信息

- **序号**：2789 / 4088
- **哈希**：0b3021742120c7483d2e63d10e2259dfc01d1287
- **短哈希**：0b3021742
- **日期**：2025-10-23 09:10:11 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Docs: Remove expired Tabular links (#14399)
- **PR/Issue**：#14399

## 总体目的

本提交移除 Iceberg 文档中已过期的 Tabular 博客链接。

`site/docs/fileio.md` 文件中包含两个指向 tabular.io 博客的链接：
1. [Iceberg FileIO: Cloud Native Tables](https://tabular.io/blog/iceberg-fileio/) —— 介绍 FileIO 的博客文章
2. [Using Iceberg's S3FileIO Implementation to Store Your Data in MinIO](https://tabular.io/blog/minio/) —— 介绍 S3FileIO 与 MinIO 使用的博客文章

Tabular 被 Databricks 收购后，这些博客链接可能已失效或重定向，不再可靠。为避免用户访问到失效链接，本提交移除这两个链接及其相关描述文本。

## 如何达成设计目的

1. 移除 FileIO 介绍段落后关于博客文章的引用和链接。
2. 移除文档末尾关于 MinIO 博客文章的引用和链接。

## 修改详情

### `site/docs/fileio.md` (+1/-5 lines)

**修改目的**：移除两个失效的 Tabular 博客链接。

**工作逻辑**：
- 在 "Usage in Processing Engines" 段落中，移除末尾 "A blog post that provides a deeper understanding of FileIO is [Iceberg FileIO: Cloud Native Tables](https://tabular.io/blog/iceberg-fileio/)" 的引用，使段落以 "capture the new state of the table." 结尾。
- 在文档末尾移除 "As an example, take a look at the blog post [Using Iceberg's S3FileIO Implementation to Store Your Data in MinIO](https://tabular.io/blog/minio/) which walks through how to use the Amazon S3 FileIO with MinIO." 整段。

## 总结

本提交是一个简单的文档维护，移除了 `fileio.md` 中两个已失效的 Tabular 博客链接。Tabular 被 Databricks 收购后这些链接不再可靠，移除后文档更干净，避免用户访问到失效页面。
