# 提交 0491：Docs: Add/update Snowflake (#9669)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0491 |
| 完整哈希 | 3b8fd904ea6c3b0f81afdae59b17ab12e45c5faf |
| 短哈希 | 3b8fd904e |
| 日期 | 2024-02-07 16:54:59 -0800 |
| 作者 | Scott Teal <s.teal726@gmail.com> |
| 说明 | Docs: Add/update Snowflake (#9669) |
| PR | #9669 |

提交信息（来自 commit body）包含三个子改动：
1. Update sidebar —— 添加 Snowflake 并使其更接近字母顺序，保持开源项目靠上、厂商靠下；
2. Update Snowflake's description —— 包含最新的文档链接；
3. Add Snowflake without disrupting ordering —— 在不破坏顺序的前提下添加 Snowflake。

## 总体目的

本提交是一个纯文档类改动，目的是将 Snowflake 作为 Iceberg 生态的集成厂商正式纳入官方文档站点，并同步更新 Snowflake 的产品描述，使其反映 Snowflake 对 Apache Iceberg 的最新支持方式。

在 1.4.x 维护周期内，Iceberg 的厂商生态持续扩张。Snowflake 作为重要的数据云厂商，其对 Iceberg 的支持模式（Iceberg Tables、catalog 集成等）已经从早期博客式的宣传性描述，演变为有正式产品文档支撑的稳定能力。因此官方文档需要：一是在站点侧边栏（sidebar）导航中为 Snowflake 增加可直达的入口，二是在 vendors.md 厂商列表页中把过时的描述替换为指向最新官方文档链接的准确说明。

值得注意的是，本次改动同时兼顾了"内容正确性"与"导航可发现性"两个维度：既修正了 Snowflake 描述中不再准确的措辞和链接，又通过在 mkdocs 侧边栏新增条目让用户能够从首页导航直接跳转到 Snowflake 的官方 Iceberg 文档。

## 如何达成设计目的

实现路径分为两部分：通过修改 `docs/mkdocs.yml` 在导航栏的厂商列表中按近似字母顺序插入 Snowflake 条目（置于 Amazon EMR 与 Impala 之间，保持开源靠上、厂商靠下的既有约定）；通过修改 `site/docs/vendors.md` 更新 Snowflake 段落的正文与超链接，将旧的产品页/博客链接替换为指向 Snowflake 官方用户指南的稳定文档链接，并重写描述文字以体现"Snowflake 托管的 Iceberg Tables"与"通过 catalog 集成外部托管的 Iceberg Tables"两种当前支持模式。

## 修改详情

### docs/mkdocs.yml

**修改目的**：在文档站点的侧边栏导航（nav）中新增 Snowflake 的入口链接。

**工作逻辑**：在 `nav` 配置的厂商列表区域，于 `Amazon EMR` 条目之后、`Impala` 条目之前插入一行：

```yaml
  - Snowflake: https://docs.snowflake.com/en/user-guide/tables-iceberg
```

该位置的选择遵循提交说明所述的"更接近字母顺序"原则，同时保持已有的组织约定——开源项目排在前面，商业厂商排在后面。链接直接指向 Snowflake 官方文档中关于 Iceberg Tables 的用户指南页面，便于读者从 Iceberg 文档侧边栏一键跳转。

### site/docs/vendors.md

**修改目的**：更新 Snowflake 厂商段落的描述文字与超链接，使其反映当前的支持方式。

**工作逻辑**：原段落内容为：

```
### [Snowflake](http://snowflake.com/)

[Snowflake](https://www.snowflake.com/data-cloud/) is a single, cross-cloud platform that enables every organization to mobilize their data with Snowflake's Data Cloud. Snowflake supports Apache Iceberg by offering [native support for Iceberg Tables](https://www.snowflake.com/blog/iceberg-tables-powering-open-standards-with-snowflake-innovations/) for full DML as well as connectors to [External Tables](https://www.snowflake.com/blog/expanding-the-data-cloud-with-apache-iceberg/) for read-only access.
```

修改后变为：

```
### [Snowflake](http://snowflake.com/)

[Snowflake](https://www.snowflake.com/en/) is a single, cross-cloud platform that enables every organization to mobilize their data with Snowflake's Data Cloud. Snowflake supports Apache Iceberg by offering [Snowflake-managed Iceberg Tables](https://docs.snowflake.com/en/user-guide/tables-iceberg#use-snowflake-as-the-iceberg-catalog) for full DML as well as [externally managed Iceberg Tables with catalog integrations](https://docs.snowflake.com/en/user-guide/tables-iceberg#use-a-catalog-integration) for read-only access.
```

具体变化有三处：
1. 标题链接保持 `http://snowflake.com/` 不变，但正文内的 Snowflake 链接由旧的 `https://www.snowflake.com/data-cloud/` 更新为更通用的 `https://www.snowflake.com/en/`；
2. "native support for Iceberg Tables"（链接指向博客文章）改为 "Snowflake-managed Iceberg Tables"（链接指向官方用户指南中"以 Snowflake 作为 Iceberg catalog"的锚点），表述更精确，链接从营销性博客升级为正式文档；
3. "External Tables"（链接指向博客文章）改为 "externally managed Iceberg Tables with catalog integrations"（链接指向官方用户指南中"使用 catalog 集成"的锚点），体现了 Snowflake 当前的外部托管 Iceberg Tables 能力依赖 catalog 集成，而非旧式的 External Tables 概念。

## 小结

本次提交是 Iceberg 1.4.x 周期内的一个文档维护改动，规模很小（2 个文件，2 行增改 / 2 行删除），但价值在于保持厂商集成信息的准确性与可发现性。一方面通过 mkdocs 侧边栏新增 Snowflake 入口，提升导航完整性；另一方面把 vendors.md 中 Snowflake 的描述从过时的博客链接更新为指向官方稳定文档的准确说明，反映 Snowflake 已从"原生 Iceberg Tables + External Tables"演进为"Snowflake 托管 Iceberg Tables + 通过 catalog 集成的外部托管 Iceberg Tables"的产品形态。该改动不涉及任何代码逻辑，属于纯文档类提交，回溯风险极低。
