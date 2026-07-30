# 提交 0880：Docs: Add BigQuery docs url to sidebar (#10574)

## 提交信息

- **序号**：0880 / 4088
- **哈希**：f9f30f6b90515b084b9292ead67508a54e115968
- **短哈希**：f9f30f6b9
- **日期**：2024-06-27 10:37:06 +0800（Wed Jun 26 23:37:06 2024 -0300）
- **作者**：edson duarte <eduarte.uatach@gmail.com>
- **提交说明**：Docs: Add BigQuery docs url to sidebar (#10574)
- **PR/Issue**：#10574

## 总体目的

Iceberg 项目官网（基于 MkDocs 构建，配置文件为 `docs/mkdocs.yml`）在导航栏的 "Compute" 一节中列出了一系列第三方计算引擎对 Iceberg 的支持文档链接，包括 Starrocks、Amazon Athena、Amazon EMR、Snowflake、Impala、Doris 等。这些链接让用户从 Iceberg 文档直接跳转到对应引擎的官方文档中关于 Iceberg 集成的章节。

Google BigQuery 是 Google Cloud 上支持 Iceberg 表格式的重要计算引擎（BigQuery Iceberg Tables），但此前 Iceberg 文档侧边栏中没有对应的链接，用户无法从 Iceberg 文档一键跳转到 BigQuery 关于 Iceberg 的官方文档。这在大数据场景下对使用 BigQuery 的用户来说是不便利的——他们需要自行搜索 BigQuery 文档。

本提交的目的是在 `docs/mkdocs.yml` 的导航配置中新增一行指向 BigQuery Iceberg 表文档的链接，让 Iceberg 文档侧边栏的 "Compute" 一节也包含 BigQuery，与其它计算引擎并列展示，提升文档的完整性与可发现性。

## 如何达成设计目的

实现方式非常直接：在 `docs/mkdocs.yml` 文件的 `nav` 配置中，找到 "Compute" 一节的列表，在 "Amazon EMR" 之后、"Snowflake" 之前插入一行：

```yaml
- Google BigQuery: https://cloud.google.com/bigquery/docs/iceberg-tables
```

这是一行标准的 MkDocs 导航项（`- 显示名称: 链接URL`），不需要任何额外配置，也不需要新增 markdown 文件——MkDocs 会直接渲染为一个外部链接。链接指向 Google Cloud 官方 BigQuery 文档中关于 Iceberg Tables 的页面。

## 修改详情

### `docs/mkdocs.yml`

**修改目的**：在 Iceberg 文档侧边栏的 "Compute" 一节中新增 BigQuery 文档链接。

**工作逻辑**：在 `nav` 配置中，"Compute" 列表位于第 55-66 行附近。本次修改在第 61 行（"Amazon EMR" 之后）插入一行：

```diff
   - Starrocks: https://docs.starrocks.io/en-us/latest/data_source/catalog/iceberg_catalog
   - Amazon Athena: https://docs.aws.amazon.com/athena/latest/ug/querying-iceberg.html
   - Amazon EMR: https://docs.aws.amazon.com/emr/latest/ReleaseGuide/emr-iceberg-use-cluster.html
+  - Google BigQuery: https://cloud.google.com/bigquery/docs/iceberg-tables
   - Snowflake: https://docs.snowflake.com/en/user-guide/tables-iceberg
   - Impala: https://impala.apache.org/docs/build/html/topics/impala_iceberg.html
   - Doris: https://doris.apache.org/docs/dev/lakehouse/datalake-analytics/iceberg
```

`- Google BigQuery:` 是侧边栏中显示的菜单项名称，`https://cloud.google.com/bigquery/docs/iceberg-tables` 是点击后跳转到的目标 URL。MkDocs 会自动识别这是外部链接（不以本站相对路径开头），渲染为带外链图标的菜单项。

## 小结

- **成效**：在 Iceberg 文档网站侧边栏的 "Compute" 一节新增了 Google BigQuery 文档链接，使用户能从 Iceberg 文档直接跳转到 BigQuery Iceberg Tables 官方文档，与 Starrocks、Amazon Athena、Amazon EMR、Snowflake、Impala、Doris 等并列展示，提升了文档的完整性与可发现性。
- **影响范围**：仅 `docs/mkdocs.yml` 一个文件、1 行新增，不涉及任何代码、测试、构建配置或运行时行为。
- **回迁到 1.4.x 的注意事项**：这是一个零风险的文档链接新增，**对 1.4.x 回迁与否都不会影响功能**。如果 1.4.x 分支的 `docs/mkdocs.yml` 中"Compute"一节确实缺少 BigQuery 链接，则可以随手回迁以保持文档一致；如果 1.4.x 的文档结构已有差异（例如导航分组顺序不同），则按 1.4.x 的实际位置插入即可。即使忽略此提交，对 1.4.x 的功能与兼容性都不会产生影响。
