# 提交 0770：Docs: Fix Apache Doris documentation link (#10263)

## 提交信息

- **序号**：0770 / 4088
- **哈希**：f31315e9357ec29f7d42ef234add4d964a0cbc5d
- **短哈希**：f31315e93
- **日期**：2024-05-16 11:18:59 -0300
- **作者**：Marcos Vinícius da Silva
- **提交说明**：Docs: Fix Apache Doris documentation link (#10263)
- **PR/Issue**：#10263

## 总体目的

修复 Iceberg 文档站点导航中 Apache Doris 集成文档的链接。原链接指向 `https://doris.apache.org/docs/dev/lakehouse/multi-catalog/iceberg`，该 URL 已失效（Doris 官方文档结构调整后，该路径不再存在），导致用户从 Iceberg 文档点击"Doris"条目时跳转到 404 或错误页面。本提交将链接更新为新的有效地址 `https://doris.apache.org/docs/dev/lakehouse/datalake-analytics/iceberg`，恢复文档可达性。

## 如何达成设计目的

### 问题背景

Iceberg 使用 MkDocs 构建文档站点，站点导航结构定义在 `docs/mkdocs.yml` 的 `nav` 段中。其中"引擎与集成"部分列出了各个支持 Iceberg 的查询引擎/平台的外部文档链接，Doris 是其中一项。导航中的外部链接以完整 URL 形式直接给出，MkDocs 会在生成的页面中渲染为可点击条目。

Doris 官方文档站点（`doris.apache.org`）在某个版本对其"湖仓分析"（lakehouse）相关文档做了目录重组：原来归属于 `lakehouse/multi-catalog/` 下的 Iceberg 集成文档被迁移到 `lakehouse/datalake-analytics/` 目录下。Iceberg 文档中的旧链接未同步更新，因此失效。

### 修复方式

直接将 `mkdocs.yml` 中 Doris 条目的 URL 从 `https://doris.apache.org/docs/dev/lakehouse/multi-catalog/iceberg` 改为 `https://doris.apache.org/docs/dev/lakehouse/datalake-analytics/iceberg`。修复仅涉及 URL 路径段 `multi-catalog` → `datalake-analytics` 的替换，其余（scheme、host、`/docs/dev/lakehouse/` 前缀、`iceberg` 末尾段）保持不变。这是对 Doris 文档目录重组的最小化、最直接的跟进。

## 修改详情

### `docs/mkdocs.yml`

**修改目的**：更新 Doris 文档链接至新地址。

**工作逻辑**：`nav` 段中 Doris 条目由：

```yaml
- Doris: https://doris.apache.org/docs/dev/lakehouse/multi-catalog/iceberg
```

改为：

```yaml
- Doris: https://doris.apache.org/docs/dev/lakehouse/datalake-analytics/iceberg
```

仅 URL 路径中的 `multi-catalog` 段替换为 `datalake-analytics`，单行单字改动。该条目位于 `Amazon EMR`、`Snowflake`、`Impala` 等其他引擎链接与 `Integrations` 子导航之间，属于外部引擎文档链接列表的一部分。

## 小结

- **成效**：修复了 Iceberg 文档站点中 Doris 集成文档的失效链接，将其指向 Doris 文档重组后的新地址 `lakehouse/datalake-analytics/iceberg`。修复后用户可正常从 Iceberg 文档跳转至 Doris 官方文档查看 Iceberg 集成说明。改动极小（单行 URL 替换），无任何代码或构建逻辑影响。
- **影响范围**：仅影响文档站点（MkDocs 生成）的导航链接，不影响任何运行时代码、构建产物或功能。影响所有通过 Iceberg 文档站点访问 Doris 集成文档的用户。
- **回迁注意事项**：
  1. 这是纯文档链接修复，回迁到 1.4.x 分支无任何技术风险，建议直接回迁以保持文档链接有效。
  2. 回迁前需确认 1.4.x 分支的 `docs/mkdocs.yml` 中 Doris 链接仍为旧的 `multi-catalog` 路径；若已被其他改动调整过，需手动核对。
  3. 该外部链接依赖于 Doris 官方文档站点的稳定性。若未来 Doris 再次调整文档结构，此链接仍可能失效，属于外部依赖的固有风险。
  4. 无需测试验证（文档链接修复无自动化测试覆盖），回迁后人工点击确认链接可达即可。
