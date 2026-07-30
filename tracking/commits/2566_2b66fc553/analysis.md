# 提交 2566：Docs: Add Memiiso Debezium to third party integrations (#13773)

## 提交信息

- **序号**：2566 / 4088
- **哈希**：2b66fc553acc0e7ddc9edcdb26095daf4e8a04c2
- **短哈希**：2b66fc553
- **日期**：2025-08-27 13:23:38 -0700
- **作者**：ismail simsek
- **提交说明**：Docs: Add Memiiso Debezium to third party integrations (#13773)
- **PR/Issue**：#13773

## 总体目的

该提交将 Memiiso Debezium 添加到 Iceberg 文档的第三方集成列表中。Memiiso Debezium 是一个开源项目，提供 Debezium Server 的 Iceberg sink connector，可以将 CDC（Change Data Capture）数据从 Debezium 直接写入 Apache Iceberg 表。这为使用 Debezium 进行数据库变更数据捕获的用户提供了一种将数据导入 Iceberg 表的集成方案。

Iceberg 文档的第三方集成页面列出了社区开发的各种工具和连接器，方便用户了解可用的集成选项。将 Memiiso Debezium 添加到列表中有助于提高该项目的可见性。

## 如何达成设计目的

- 在 `docs/mkdocs.yml` 的导航配置中，在第三方集成的 Impala 和 Presto 之间新增 Memiiso Debezium 条目，链接到其文档站点。

## 修改详情

### `docs/mkdocs.yml` (+1/-0)

**修改目的**：在文档导航中添加 Memiiso Debezium 第三方集成条目。

**工作逻辑**：在 `nav` 配置的第三方集成列表中，Impala 条目之后新增一行 `- Memiiso Debezium: https://memiiso.github.io/debezium-server-iceberg/`，链接到 Memiiso Debezium Iceberg sink 的文档站点。

## 总结

该提交在 Iceberg 文档的第三方集成导航中新增了 Memiiso Debezium 条目，提供了 Debezium CDC 数据写入 Iceberg 表的开源连接器文档链接，修改仅一行配置。
