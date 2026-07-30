# 提交 2029：Site: Update links to daft docs

## 提交信息

- **序号**：2029 / 4088
- **哈希**：4b131c5eca995c33b2ce67cac1aaf865f1138bd6
- **短哈希**：4b131c5ec
- **日期**：2025-04-22 15:09:33 -0500
- **作者**：ccmao1130
- **提交说明**：Site: Update links to daft docs (#12860)
- **PR/Issue**：#12860

## 总体目的

Daft（一个用 Python 和 Rust 编写的分布式查询引擎）的官方文档站点发生了 URL 结构变更。Iceberg 文档中引用 Daft 文档的链接已过时，导致用户点击后无法正确访问对应的 API 文档页面。

本提交的目的就是将 Iceberg 文档中所有指向 Daft 文档的链接更新为新的 URL 格式，确保文档中的外部引用始终有效，提升用户阅读体验并避免出现死链。

## 如何达成设计目的

整体思路非常直接：定位到 Iceberg 站点文档中引用 Daft 文档的文件 `docs/docs/daft.md`，将其中的所有外链 URL 从旧的路径结构替换为新的路径结构。

关键变更模式：
- 旧路径片段 `api_docs/datatype.html#daft.DataType.XXX` 替换为 `api/datatypes/#daft.datatype.DataType.XXX`
- 旧路径片段 `api_docs/dataframe.html` 替换为 `api/dataframe/`
- 同时修正了 `fixed(L)` 类型对应的锚点，从原来错误指向 `#daft.DataType.binary` 改为正确的 `#daft.datatype.DataType.fixed_size_binary`

## 修改详情

### `docs/docs/daft.md` (修改, +18/-18 lines)

**修改目的**：更新 Daft 文档链接，使其指向 Daft 新版文档站点的正确 URL。

**工作逻辑**：
该文件是 Iceberg 站点中介绍 Daft 与 Iceberg 集成的文档页面。修改涉及两处：

1. 正文段中对 Python DataFrame API 的引用链接，从 `https://www.getdaft.io/projects/docs/en/latest/api_docs/dataframe.html` 更新为 `https://www.getdaft.io/projects/docs/en/latest/api/dataframe/`。

2. 类型映射表格中的 18 个 API 链接，统一将路径从 `api_docs/datatype.html#daft.DataType.XXX` 变更为 `api/datatypes/#daft.datatype.DataType.XXX`。其中值得注意的一个实质性修正是 `fixed(L)` 类型的链接：原来错误地指向 `binary` 的锚点，本次修正为正确的 `fixed_size_binary` 锚点（`#daft.datatype.DataType.fixed_size_binary`），这与 Daft 中 `fixed(L)` 实际对应的 `fixed_size_binary` 类型语义一致。

## 总结

这是一次纯文档链接维护提交，将 Iceberg 文档中引用 Daft 文档的 18 个链接更新为 Daft 新版文档站点的 URL 格式，并顺带修正了 `fixed(L)` 类型链接指向错误锚点的问题，保证外部引用的有效性与准确性。
