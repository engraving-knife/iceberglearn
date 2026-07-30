# 提交 2857：Docs: add missing pages (about, benchmarks, fileio, security) to site (#14481)

## 提交信息

- **序号**：2857 / 4088
- **哈希**：e762cd4e9eaee245d4afd002064e569fcf145e95
- **短哈希**：e762cd4e9
- **日期**：2025-11-10 09:09:17 -0800
- **作者**：Enes Yesil
- **提交说明**：Docs: add missing pages (about, benchmarks, fileio, security) to site (#14481)
- **PR/Issue**：#14481

## 总体目的

这个提交为 Iceberg 项目网站添加了缺失的文档页面导航入口。具体包括将 `fileio.md` 文件从 `site/docs/` 移动到 `docs/docs/` 目录，并在 MkDocs 导航配置中添加了 benchmarks（基准测试）和 security（安全）页面的导航入口。

Iceberg 项目使用 MkDocs 构建文档网站，文档内容和导航配置分布在多个配置文件中。此前一些文档页面已存在但未在导航中显示，导致用户无法通过网站导航访问这些页面。

## 如何达成设计目的

通过以下操作完成：

1. 将 `site/docs/fileio.md` 重命名/移动到 `docs/docs/fileio.md`，使其与主文档目录结构一致。
2. 在 `docs/mkdocs.yml` 的导航配置中，在 API 部分添加 File I/O 页面入口。
3. 在 `site/mkdocs-dev.yml` 和 `site/nav.yml` 的 Project 部分添加 Benchmarks 和 Security 页面导航入口。

## 修改详情

### `site/docs/fileio.md` -> `docs/docs/fileio.md` (rename)

**修改目的**：将 fileio.md 文件从 site 目录移动到 docs 目录。

**工作逻辑**：文件内容未变（similarity index 100%），仅改变了文件位置，使其与 `docs/docs/` 下的其他文档保持一致的目录结构。

### `docs/mkdocs.yml` (+1/-0 lines)

**修改目的**：在文档导航中添加 File I/O 页面入口。

**工作逻辑**：在 API 部分的导航配置中，在 Quickstart 和 API 之间添加 `- File I/O: fileio.md`，使用户能通过网站导航访问 File I/O 文档。

### `site/mkdocs-dev.yml` (+2/-0 lines)

**修改目的**：在开发版网站导航中添加 Benchmarks 和 Security 页面入口。

**工作逻辑**：在 Project 部分的导航中，在 Multi-engine support 和 How to release 之间添加 `- Benchmarks: benchmarks.md` 和 `- Security: security.md`。

### `site/nav.yml` (+3/-0 lines)

**修改目的**：在主网站导航中添加 Benchmarks 和 Security 页面入口，并整理格式。

**工作逻辑**：与 mkdocs-dev.yml 类似，在 nav.yml 的 Project 部分添加 Benchmarks 和 Security 导航入口。同时添加了一个空行用于格式整理。

## 总结

这是一个文档导航修复提交，将 fileio.md 移至正确目录并在 MkDocs 导航配置中添加了缺失的 File I/O、Benchmarks 和 Security 页面入口。修改使这些已存在的文档页面可以通过网站导航访问，提升了文档的可发现性。
