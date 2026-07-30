# 提交 2104：Docs: Add Databend link (#13002)

## 提交信息

- **序号**：2104 / 4088
- **哈希**：9c8c431f830ed1f413abc355a316b73271385ccf
- **短哈希**：9c8c431f8
- **日期**：2025-05-08 16:10:00 +0200
- **作者**：sundyli <543950155@qq.com>
- **提交说明**：Docs: Add Databend link (#13002)
- **PR/Issue**：#13002

## 总体目的

Databend 是一个支持读取 Iceberg 表的数据分析引擎。本次提交在 Iceberg 官方文档（mkdocs 站点）的导航栏"引擎集成"列表中新增 Databend 的文档链接，使访问 Iceberg 站点的用户能够发现 Databend 对 Iceberg 的支持，并跳转到 Databend 官方文档查看如何用 Databend 访问 Iceberg 数据湖。

## 如何达成设计目的

在 `docs/mkdocs.yml` 的 `nav` 配置中，"引擎/集成"列表里 Impala 之后、Doris 之前插入一行 Databend 条目，指向 `https://docs.databend.com/guides/access-data-lake/iceberg`，与既有条目格式一致。

## 修改详情

### `docs/mkdocs.yml` (修改, +1/-0 lines)

**修改目的**：在文档导航中加入 Databend 链接。

**工作逻辑**：在 `nav` 列表中新增 `- Databend: https://docs.databend.com/guides/access-data-lake/iceberg`，位于 Impala 与 Doris 之间，使 mkdocs 生成的站点导航栏展示 Databend 入口。

## 总结

本次提交是一次纯文档维护：在 Iceberg 官方文档导航中加入 Databend 的集成文档链接，提升 Databend 作为 Iceberg 生态引擎的可发现性。改动仅 1 行。
