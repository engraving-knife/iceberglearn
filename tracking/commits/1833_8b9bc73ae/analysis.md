# 提交 1833：Docs: fix typo in `rest-catalog-open-api.yaml` (#12480)

## 提交信息

- **序号**：1833 / 4088
- **哈希**：8b9bc73ae1711677b46f63d903a62528ac16496d
- **短哈希**：8b9bc73ae
- **日期**：2025-03-08 20:26:15 +0100
- **作者**：slfan1989
- **提交说明**：Docs: fix typo in `rest-catalog-open-api.yaml` (#12480)
- **PR/Issue**：#12480

## 总体目的

本提交修复了 REST Catalog OpenAPI 规范文件 `rest-catalog-open-api.yaml` 中的一个拼写错误。在 `NamespaceAsPathVariable` 示例的 `summary` 字段中，"path parameter" 被误拼为 "path paremeter"（少了一个 a，多了一个 e）。这是一个纯文档修复，不影响 API 行为，但会影响 OpenAPI 文档生成工具输出的文档质量，以及阅读者的体验。

REST Catalog OpenAPI 规范是 Iceberg REST Catalog 接口的权威定义，被各种工具链（如 Swagger UI、OpenAPI Generator）消费，文档中的拼写错误会直接反映在生成的 API 文档中，因此修复是有意义的。

## 如何达成设计目的

直接在 `open-api/rest-catalog-open-api.yaml` 文件中将 `NamespaceAsPathVariable` 示例的 summary 从 "A single part namespace, as represented in a path paremeter" 修改为 "A single part namespace, as represented in a path parameter"。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (修改, 1 line)

**修改目的**：修正 `NamespaceAsPathVariable` 示例 summary 中的拼写错误。

**工作逻辑**：将第 4734 行的 `summary: A single part namespace, as represented in a path paremeter` 中的 "paremeter" 改为 "parameter"。`NamespaceAsPathVariable` 是 OpenAPI 规范中定义的一个示例对象，用于展示命名空间作为路径参数时的正确表示方式（如 `accounting`），其 summary 字段描述该示例的用途。

## 小结

本提交是纯文档拼写修复，改动极小（1 行），不影响任何代码逻辑或 API 行为。回迁到 1.4.x 无风险，可直接应用。
