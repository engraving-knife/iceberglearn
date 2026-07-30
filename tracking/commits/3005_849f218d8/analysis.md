# 提交 3005：Docs: Add Apache Fluss integration link (#14829)

## 提交信息

- **序号**：3005 / 4088
- **哈希**：849f218d8d9a6783ba04d2d4eafc07754ec3885c
- **短哈希**：849f218d8
- **日期**：2025-12-12 12:44:52 +0100
- **作者**：MehulBatra
- **提交说明**：Docs: Add Apache Fluss integration link (#14829)
- **PR/Issue**：#14829

## 总体目的

Iceberg 官方网站（位于 `site/` 目录，使用 Hugo + `nav.yml` 描述站点导航）维护着一个"Integrations"集成清单页面，按字母序列出所有与 Iceberg 互通的引擎、工具与生态项目（如 Amazon Athena、Apache Doris、ClickHouse、DuckDB、Dremio 等），每项要么指向站内 `integrations/*.md` 文档，要么直接外链到对应项目的 Iceberg 集成文档。这个清单是用户发现"Iceberg 能和哪些系统一起用"的官方入口，新出现的集成需要在这里登记才能被用户找到。

Apache Fluss 是一个面向实时流处理的 Apache 项目，定位是"流式湖仓"（streaming lakehouse），能够与 Iceberg 等 data lake 格式集成，把实时流数据落到 Iceberg 表中。Fluss 官方文档中有专门一页讲述如何与 Iceberg 集成（`https://fluss.apache.org/docs/next/streaming-lakehouse/integrate-data-lakes/iceberg/`）。在本次提交之前，Iceberg 官方集成清单里没有 Fluss 条目，用户从 Iceberg 站点无法发现这条集成路径，需要自行搜索 Fluss 文档。

本提交的目的就是把 Apache Fluss 加入 Iceberg 官方集成清单，让用户在浏览 Iceberg 集成生态时能直接看到并跳转到 Fluss 的 Iceberg 集成文档，提升 Fluss 与 Iceberg 互通的可发现性。

## 如何达成设计目的

整体思路是"在 `site/nav.yml` 的 Integrations 列表中按字母序插入一项"。`nav.yml` 是 Hugo 站点导航的 YAML 配置，Integrations 列表是一个字符串数组，每项格式为 `显示名称: 链接`。由于列表已经按字母序排列，Fluss 应该排在 Druid 之后、BladePipe 之前（注意列表实际是"Apache X"前缀项集中在前，非 Apache 前缀项在后），所以新条目插在 `Apache Druid` 之后、`BladePipe` 之前，与已有的 `Apache Amoro`、`Apache Doris`、`Apache Druid` 等"Apache X"条目保持同一字母段。改动只在 `site/nav.yml` 一个文件加一行。

## 修改详情

### `site/nav.yml` (+1/-0 lines)

**修改目的**：在 Iceberg 官方文档的 Integrations 导航列表中新增 Apache Fluss 条目。

**工作逻辑**：
在 `Integrations:` 列表中、`Apache Druid: ...` 行之后新增一行：

```yaml
- Apache Fluss: https://fluss.apache.org/docs/next/streaming-lakehouse/integrate-data-lakes/iceberg/
```

这一行的语义：

- 显示名称 `Apache Fluss` 与同段其他条目（`Apache Amoro`、`Apache Doris`、`Apache Druid`）命名风格一致，使用项目全名；
- 链接 `https://fluss.apache.org/docs/next/streaming-lakehouse/integrate-data-lakes/iceberg/` 指向 Fluss 官方文档中"streaming lakehouse → integrate data lakes → iceberg"页面，即 Fluss 侧关于如何与 Iceberg 集成的权威说明；
- 用 `https://` 且指向 Flus 官方域名 `fluss.apache.org`，符合 Iceberg 集成清单中外链条目的惯例（与 `Apache Doris`、`Apache Druid`、`ClickHouse` 等外链条目格式一致）。

Hugo 在构建站点时会读取 `nav.yml`，把这一项渲染为 Integrations 页面上的一个可点击链接，用户点击即跳转到 Fluss 的 Iceberg 集成文档。条目插入位置（Druid 之后、BladePipe 之前）保持了"Apache 前缀项按字母序集中排列"的现有约定，不会破坏列表的可读性。

## 总结

该提交是一行文档变更，把 Apache Fluss 加入 Iceberg 官方文档的 Integrations 导航清单，外链到 Fluss 侧的 Iceberg 集成文档。改动本身价值在于提升 Fluss-Iceberg 集成的可发现性——用户在浏览 Iceberg 集成生态时能直接看到 Fluss 条目并跳转，而无需自行搜索 Fluss 文档。条目按字母序插入"Apache X"段，与清单中既有外链条目格式保持一致，对维护 Iceberg 集成生态清单的完整性是有益的补充。
