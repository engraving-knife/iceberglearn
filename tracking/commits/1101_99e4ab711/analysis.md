# 提交 1101：Docs: Rename Clickhouse to ClickHouse (#10998)

## 提交信息

- **序号**：1101 / 4088
- **哈希**：99e4ab711d42fef2d108b62f7717bc9e557e7e3e
- **短哈希**：99e4ab711
- **日期**：2024-08-26 10:50:17 +0200（作者本地时区 +0900）
- **作者**：Yuya Ebihara
- **提交说明**：Docs: Rename Clickhouse to ClickHouse (#10998)
- **PR/Issue**：#10998

## 总体目的

本提交修正 Iceberg 文档站点导航中 ClickHouse 产品名称的大小写：将 "Clickhouse" 改为 "ClickHouse"。

ClickHouse（开源列式数据库）的官方品牌名称中 "H" 为大写，即 "ClickHouse"。Iceberg 文档站点的 `mkdocs.yml` 导航配置中，将 ClickHouse 列为生态系统集成之一，但导航项的显示文本误写为 "Clickhouse"（H 小写），与官方品牌名称不一致。本提交将显示文本修正为 "ClickHouse"，使文档与官方品牌名称保持一致。

## 如何达成设计目的

修改 `docs/mkdocs.yml` 中导航配置的一项，将 `- Clickhouse: https://...` 改为 `- ClickHouse: https://...`。链接 URL 保持不变，仅修正导航项的显示文本。

## 修改详情

### `docs/mkdocs.yml`

**修改目的**：修正文档导航中 ClickHouse 的品牌名称大小写。

**工作逻辑**：在 `nav` 配置的生态系统集成列表中，将 `- Clickhouse: https://clickhouse.com/docs/en/engines/table-engines/integrations/iceberg` 改为 `- ClickHouse: https://clickhouse.com/docs/en/engines/table-engines/integrations/iceberg`。仅修改导航项的 key（显示名称），value（URL）不变。

## 小结

- **成效**：修正了文档导航中 ClickHouse 的品牌名称大小写，使其与官方品牌名称 "ClickHouse" 一致。
- **影响范围**：仅修改 `docs/mkdocs.yml` 一个文件，一处导航项的显示文本（纯文档修正）。
- **回迁到 1.4.x 的注意事项**：纯文档修正，无代码影响，**可安全回迁到 1.4.x**。若 1.4.x 文档导航中存在同样的拼写，回迁有益；若 1.4.x 文档结构不同则无需回迁。
