# 提交 1108：Docs: Add Druid docs url to sidebar (#10997)

## 提交信息

- **序号**：1108 / 4088
- **哈希**：64b36999d7ff716ae2534fb0972fcc10d22a64c2
- **短哈希**：64b36999d
- **日期**：2024-08-26（Mon Aug 26 19:40:20 2024 -0700）
- **作者**：Charles Smith <techdocsmith@gmail.com>
- **提交说明**：Docs: Add Druid docs url to sidebar (#10997)
- **PR/Issue**：#10997
- **影响模块**：docs（`docs/mkdocs.yml`）

## 总体目的

Iceberg 官网（基于 mkdocs 构建）的侧边栏导航有一个"外部引擎/集成"段落，列出了已经原生支持 Iceberg 的查询引擎（如 Snowflake、Impala、Doris 等）的官方文档链接，方便用户跳转了解如何在对应引擎中使用 Iceberg。

Apache Druid 是一个支持 Iceberg 作为数据源的实时分析数据库，但其文档此前**未在 Iceberg 官网侧边栏列出**。本提交在 `Doris` 之后追加一行 `Druid` 条目，把 Druid 官方关于 Iceberg 扩展的文档链接加入侧边栏，让用户能从 Iceberg 官网直接跳转到 Druid 文档。

## 如何达成设计目的

直接在 `docs/mkdocs.yml` 的 `nav` 列表中找到现有外部引擎段（`Snowflake`/`Impala`/`Doris`），在 `Doris` 之后追加一行 `- Druid: <url>`，指向 `https://druid.apache.org/docs/latest/development/extensions-contrib/iceberg/`。mkdocs 会自动把该条目渲染为侧边栏可点击链接，跳转到外部 URL。

## 修改详情

### `docs/mkdocs.yml`

**修改目的**：在侧边栏外部集成段追加 Druid 文档链接。

**工作逻辑**：在 `nav` 的既有外链列表中，`Doris` 行之后新增：

```yaml
  - Druid: https://druid.apache.org/docs/latest/development/extensions-contrib/iceberg/
```

完整的局部上下文：

```yaml
nav:
  ...
  - Snowflake: https://docs.snowflake.com/en/user-guide/tables-iceberg
  - Impala: https://impala.apache.org/docs/build/html/topics/impala_iceberg.html
  - Doris: https://doris.apache.org/docs/dev/lakehouse/datalake-analytics/iceberg
  - Druid: https://druid.apache.org/docs/latest/development/extensions-contrib/iceberg/
  - Integrations:
    - aws.md
    ...
```

这是纯文档配置改动，无代码逻辑。

## 小结

- **成效**：Iceberg 官网侧边栏现包含 Druid 文档链接，用户可直接跳转查看 Druid 的 Iceberg 集成扩展文档。
- **影响范围**：仅 `docs/mkdocs.yml` 一行新增，无代码、构建或运行时影响。
- **回迁到 1.4.x 的注意事项**：这是文档站点导航改动，与产品版本无关，由 main 分支统一维护并作用于整个官网。**无需回迁**到 1.4.x 维护分支；1.4.x 不维护独立的文档站点。
