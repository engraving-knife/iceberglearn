# 提交 2691：Add reference to the Starburst connector (#14188)

## 提交信息

- **序号**：2691 / 4088
- **哈希**：582d63b52f9e583bc5cb0eb933eb03b32d10aa32
- **短哈希**：582d63b52
- **日期**：2025-09-27 12:19:30 -0700
- **作者**：Marius Grama
- **提交说明**：Add reference to the Starburst connector (#14188)
- **PR/Issue**：#14188

## 总体目的

本提交向 Iceberg 官方站点的导航配置中添加 Starburst 连接器（connector）的参考链接。Iceberg 生态中有众多计算引擎/查询引擎通过各自的 connector 接入 Iceberg 表格式，站点维护了一个"集成"列表，列出主流引擎对应的官方文档链接，方便用户快速跳转。

Starburst 是 Trino 的商业版本提供商，其 Iceberg connector 文档独立维护在 `docs.starburst.io`，与开源 Trino 的文档（`trino.io`）分开。此前该列表中已有 Trino、Snowflake、StarRocks、Redpanda、RisingWave、Tinybird 等条目，但缺少 Starburst，本次补齐这一缺漏。

## 如何达成设计目的

通过修改站点导航配置文件 `site/nav.yml`，在集成列表中按字母序插入 Starburst 条目，链接指向 Starburst 官方 Iceberg connector 文档。修改极小，仅新增一行。

## 修改详情

### `site/nav.yml` (+1/-0 lines)

**修改目的**：在站点的"集成"导航列表中加入 Starburst 连接器文档链接。

**工作逻辑**：在 `nav.yml` 的集成条目列表中，按字母顺序将 `Starburst` 插入到 `Snowflake` 之后、`Starrocks` 之前，链接为 `https://docs.starburst.io/latest/connector/iceberg.html`。保持与同列表其他条目相同的 `- 名称: URL` 格式，从而在生成的站点导航中显示为可点击条目。

## 总结

本提交是站点文档的轻量补全，将 Starburst Iceberg connector 官方文档纳入 Iceberg 站点的集成导航列表，提升了文档的完整性，方便使用 Starburst 商业版的用户找到对应文档。属于纯文档/站点配置类修改，不涉及代码逻辑。
