# 提交 1735：Site: Learn More to point to Spark QuickStart Doc (#12272)

## 提交信息

- **序号**：1735 / 4088
- **哈希**：abb47830e7df7dc2ae93c74b0ad97f06cdd37aad
- **短哈希**：abb47830e
- **日期**：2025-02-14 12:44:54 -0600
- **作者**：Danica Fine
- **提交说明**：Site: Learn More to point to Spark QuickStart Doc (#12272)
- **PR/Issue**：#12272

## 总体目的

Iceberg 官方网站的"关于"页面（about.md）上有一个"Learn More"按钮，此前该按钮链接到 `/getting-started` 页面。随着文档结构的调整和优化，`/getting-started` 页面可能已不再是最合适的入门入口，而 `/spark-quickstart` 页面提供了更直接、更实用的 Spark 快速上手指南。

本提交的目标是将"Learn More"按钮的链接从 `/getting-started` 更新为 `/spark-quickstart`，引导新用户直接进入 Spark 快速入门文档，降低上手门槛。

## 如何达成设计目的

提交直接修改 `site/docs/about.md` 文件中的一行 HTML 链接，将 `href` 属性从 `/getting-started` 改为 `/spark-quickstart`。这是一个单行修改，不涉及其他文件或逻辑变更。

## 修改详情

### `site/docs/about.md`（修改, +1/-1 lines）

**修改目的**：更新"Learn More"按钮的链接指向。

**工作逻辑**：将 `<a href="/getting-started" class="btn btn-default btn-lg">` 修改为 `<a href="/spark-quickstart" class="btn btn-default btn-lg">`。按钮的样式类（`btn btn-default btn-lg`）和显示文本（`Learn More`）保持不变。

## 小结

- **成效**：将网站"关于"页面的"Learn More"按钮重新指向 Spark 快速入门文档，使新用户能更直接地找到实用的上手指南。
- **影响范围**：仅涉及网站文档的一个链接修改，不影响任何代码功能。
- **回迁到 1.4.x 的注意事项**：此提交为纯文档链接修改，无前置依赖，回迁风险极低。需确认 1.4.x 分支的网站文档结构中 `/spark-quickstart` 页面存在。如果 1.4.x 的文档结构不同，需调整链接路径。建议回迁。
