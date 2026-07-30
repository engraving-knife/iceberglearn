# 提交 1989：Docs: Add Estuary to docs and vendors

## 提交信息

- **序号**：1989 / 4088
- **哈希**：6cf4fc13760f59f2e06c1764f7baa63451b6c4b2
- **短哈希**：6cf4fc137
- **日期**：2025-04-11 17:43:41 -0500
- **作者**：aeluce
- **提交说明**：Docs: Add Estuary to docs and vendors (#12764)
- **PR/Issue**：#12764

## 总体目的

本提交将 Estuary 这一数据集成平台添加到 Apache Iceberg 的官方文档与厂商列表中。Estuary 是一个低延迟、高保真的数据移动平台，支持将 Apache Iceberg 作为数据的目标（destination）进行物化（materialization）。

随着 Iceberg 生态的不断扩展，越来越多的厂商和工具支持与 Iceberg 的集成。在文档中登记这些厂商有助于用户了解可用的集成方案。Estuary 提供两种可配置的物化方式：一种用于合并（merge）新更新，另一种用于直接追加（append）数据。

## 如何达成设计目的

通过修改两处文档文件来完成添加：
1. 在 mkdocs 的导航配置（`docs/mkdocs.yml`）中加入 Estuary 的外部文档链接，使其出现在站点导航中。
2. 在厂商列表页面（`site/docs/vendors.md`）中新增 Estuary 的介绍段落，描述其能力与 Iceberg 集成方式。

## 修改详情

### `docs/mkdocs.yml` (修改, +1/-0 lines)

**修改目的**：在文档导航中注册 Estuary 的外部链接。

**工作逻辑**：
在 nav 配置的厂商列表中，于 Daft 与 RisingWave 之间新增一行 `- Estuary: https://docs.estuary.dev/reference/Connectors/materialization-connectors/apache-iceberg/`，使 Estuary 文档链接出现在侧边栏导航中。

### `site/docs/vendors.md` (修改, +6/-0 lines)

**修改目的**：在厂商介绍页面中添加 Estuary 的描述。

**工作逻辑**：
在 Dremio 段落之后、IBM watsonx.data 段落之前，新增 `### [Estuary](https://estuary.dev)` 小节。内容描述了 Estuary 作为低延迟数据移动平台的特性，包括智能模式推断与演进、灵活的部署选项（公有云、私有云、BYOC），以及预构建的数据连接器目录。特别说明 Apache Iceberg 是其主要的目标选项之一，提供 merge 和 append 两种物化方式。

## 总结

本提交是纯文档变更，将 Estuary 数据集成平台登记到 Iceberg 的厂商文档与导航中，方便用户了解并使用 Estuary 与 Iceberg 的集成能力。
