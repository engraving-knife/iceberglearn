# 提交 1580 5b259e297 分析

## 提交信息
- 哈希：5b259e297e0faa4b8f279826836a6d482ccba29b
- 日期：2025-01-14（Tue Jan 14 17:48:13 2025 +0800）
- 作者：Mingyu Chen (Rayner) <yunyou@selectdb.com>
- 消息：Docs: Update docs link about Apache Doris and update vendors list (#11956)

## 总体目的

本提交对 Iceberg 官方文档做两处维护性更新：一是修正导航配置中 Apache Doris 的文档外链（Doris 官网文档路径已重组，旧链接失效）；二是在厂商列表页新增 VeloDB 条目，丰富 Iceberg 生态的厂商展示。

背景一：Iceberg 文档站点的导航配置 `docs/mkdocs.yml` 在 `Engines and Services` 导航组里维护了一组外部引擎/服务文档链接，其中 Doris 指向 `https://doris.apache.org/docs/dev/lakehouse/datalake-analytics/iceberg`。Doris 官方文档随后重组了 lakehouse 相关页面结构，将原先的 `datalake-analytics/iceberg` 路径迁移到 `catalogs/iceberg-catalog`，旧链接对用户不再有效。本提交将该外链更新为新路径 `https://doris.apache.org/docs/dev/lakehouse/catalogs/iceberg-catalog`，与 Doris 当前文档结构保持一致。

背景二：Iceberg 文档站的 `site/docs/vendors.md` 页面罗列了在产品中支持 Apache Iceberg 的厂商清单（Bodo、Dremio、Starburst、Upsolver 等）。VeloDB 是一家基于 Apache Doris 构建的商业数据仓库，支持对 Iceberg 表的查询加速与数据回写。本提交在厂商清单末尾追加 VeloDB 条目，介绍其产品定位与 Iceberg 集成能力，并附上其企业版、云服务及 Doris-Iceberg 快速上手的外链。

提交者来自 selectdb.com（VeloDB/Doris 生态相关方），同时完成链接修正与厂商登记，属于社区文档贡献。

## 如何达成设计目的

两处修改都是纯文档/配置变更，无代码逻辑。一是改 mkdocs 导航里一行外链 URL，二是在 vendors.md 末尾追加一个厂商小节。

### 修改详情

#### `docs/mkdocs.yml`

**修改目的**：更新 Doris 文档外链至新路径。

**工作逻辑**：在 `nav` 配置的 `Engines and Services` 区块中，将 Doris 条目的 URL 从 `https://doris.apache.org/docs/dev/lakehouse/datalake-analytics/iceberg` 改为 `https://doris.apache.org/docs/dev/lakehouse/catalogs/iceberg-catalog`。该 URL 是导航菜单中"Doris"项点击后跳转的外部链接，mkdocs 不会校验外链可达性，故旧链接会一直保留到人工更新为止。新路径对应 Doris 现行的"Iceberg Catalog"文档页，描述如何在 Doris 中配置 Iceberg catalog。

#### `site/docs/vendors.md`

**修改目的**：新增 VeloDB 厂商条目。

**工作逻辑**：在文件末尾（Upsolver 条目之后）追加一个 `###` 级小节：
```markdown
### [VeloDB](https://velodb.io)

[VeloDB](https://www.velodb.io/) is a commercial data warehouse powered by [Apache Doris](https://doris.apache.org/), an open-source, real-time data warehouse. It also provides powerful [query acceleration for Iceberg tables and efficient data writeback](https://doris.apache.org/docs/dev/lakehouse/catalogs/iceberg-catalog). VeloDB offers [enterprise version](https://www.velodb.io/enterprise) and [cloud service](https://www.velodb.io/cloud), which are fully compatible with open-source Apache Doris. Quick start with Apache Doris and Apache Iceberg [here](https://doris.apache.org/docs/lakehouse/lakehouse-best-practices/doris-iceberg).
```

该条目沿用页面既有格式：标题为带外链的厂商名，正文简述产品定位（基于 Apache Doris 的商业数据仓库）、与 Iceberg 的集成能力（查询加速与数据回写，链接到上一步更新的 Doris Iceberg catalog 文档页），以及企业版/云服务入口和 Doris-Iceberg 快速上手指南。同时在文件末尾保留一个空行，符合 Markdown 文件末尾留空行的惯例。

## 小结

- **成效**：修正了文档导航中失效的 Doris 外链（`datalake-analytics/iceberg` → `catalogs/iceberg-catalog`），并在厂商清单新增 VeloDB 条目，使文档与 Doris 现行文档结构一致、丰富 Iceberg 生态厂商展示。
- **影响范围**：仅 `docs/mkdocs.yml`（1 行 URL 替换）与 `site/docs/vendors.md`（末尾追加 5 行）两个文档文件，无代码、构建、测试逻辑变更。
- **回迁到 1.4.x 的注意事项**：纯文档维护，与 1.4.x 运行时无关。1.4.x 若维护文档站点可顺手回迁以保持外链有效；若 1.4.x 不发布文档站点，则无需回迁，对发布产物无任何影响。
