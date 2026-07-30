# 提交 2061：Docs: Add DuckDB (#12932)

## 提交信息

- **序号**：2061 / 4088
- **哈希**：767688a33b1bf0af01e1dd06b141f9b891f8570e
- **短哈希**：767688a33
- **日期**：2025-04-30 13:00:08 +0200
- **作者**：Gabor Szarnyas
- **提交说明**：Docs: Add DuckDB (#12932)
- **PR/Issue**：#12932

## 总体目的

Iceberg 官方文档站点的导航中列举了支持 Iceberg 的生态工具（如 Impala、Doris、Druid、Kafka Connect 等）。DuckDB 已经提供了 Iceberg 扩展，但此前未在 Iceberg 文档的"其他引擎/工具"导航中列出。本提交在 `docs/mkdocs.yml` 的导航中加入 DuckDB 入口，方便用户发现 DuckDB 对 Iceberg 的集成支持。

## 如何达成设计目的

通过在 `docs/mkdocs.yml` 的 `nav` 列表中、`Druid` 条目之后、`Kafka Connect` 之前，新增一行 `- DuckDB: https://duckdb.org/docs/preview/core_extensions/iceberg/overview`，作为外部链接条目，点击跳转到 DuckDB 官方文档的 Iceberg 扩展页面。

## 修改详情

### `docs/mkdocs.yml` (修改, +1/-0 lines)

**修改目的**：在文档导航中新增 DuckDB 集成入口。

**工作逻辑**：
在 `nav` 列表中按字母顺序与既有外部引擎条目并列，插入 `- DuckDB: https://duckdb.org/docs/preview/core_extensions/iceberg/overview`。该链接指向 DuckDB 文档站点关于 Iceberg 扩展的概览页，作为外部 URL 直接渲染为导航链接（而非站内页面）。

## 总结

纯文档导航变更，单行新增。把 DuckDB 加入 Iceberg 官方文档的生态集成导航，提升 DuckDB Iceberg 扩展的可发现性。
