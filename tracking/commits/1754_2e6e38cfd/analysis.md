# 提交 1754：Docs: Fix refs in Apache Amoro docs (#12332)

## 提交信息

- **序号**：1754 / 4088
- **哈希**：2e6e38cfd3d97cf0d7fda2d5866989ee25247fe6
- **短哈希**：2e6e38cfd
- **日期**：2025-02-19 10:18:06 +0100
- **作者**：ConradJam
- **提交说明**：Docs: Fix refs in Apache Amoro docs (#12332)
- **PR/Issue**：#12332

## 总体目的

本提交旨在修复在前一个提交（1751，Docs: Add Apache Amoro docs）中引入的 Apache Amoro 文档页面中的两处链接引用问题。

具体来说：
1. **Self-optimizing 架构图链接**：原链接指向 GitHub 仓库中的 blob 路径（`https://github.com/apache/amoro/blob/master/docs/images/concepts/self-optimizing_arch.png`），这是一个网页视图链接而非直接的图片链接，在 Markdown 文档中无法正确渲染为图片。需要改为指向 Amoro 官方文档站点的图片直链。
2. **REST Catalog 链接**：原链接指向 Iceberg 文档站点的 catalog 概念页面（`https://iceberg.apache.org/concepts/catalog/#decoupling-using-the-rest-catalog`），该页面可能已不存在或锚点不正确。需要改为指向 REST Catalog 的 OpenAPI 规范（通过 Swagger Editor 在线查看）。

## 如何达成设计目的

提交直接修改 `amoro.md` 文件中的两处 Markdown 链接 URL，将错误的链接替换为正确的目标地址。

## 修改详情

### `docs/docs/amoro.md`（修改, +2/-2 lines）

**修改目的**：修复两处错误的文档链接引用。

**工作逻辑**：

1. **Self-optimizing 架构图链接**：
   - 原链接：`https://github.com/apache/amoro/blob/master/master/docs/images/concepts/self-optimizing_arch.png`（GitHub blob URL，不是图片直链）
   - 新链接：`https://amoro.apache.org/docs/latest/images/concepts/self-optimizing_arch.png`（Amoro 官方文档站点的图片直链）
   - 修复后图片可以在 Markdown 中正确渲染显示。

2. **REST Catalog 链接**：
   - 原链接：`https://iceberg.apache.org/concepts/catalog/#decoupling-using-the-rest-catalog`（Iceberg 文档站点的 catalog 概念页面锚点）
   - 新链接：`https://editor-next.swagger.io/?url=https://raw.githubusercontent.com/apache/iceberg/main/open-api/rest-catalog-open-api.yaml`（通过 Swagger Editor 在线查看 Iceberg REST Catalog OpenAPI 规范）
   - 修复后链接指向 REST Catalog 的 OpenAPI 定义，用户可直接在浏览器中查看 API 规范。

## 小结

- **成效**：修复了 Apache Amoro 文档中两处无法正确访问或渲染的链接引用，改善了文档的可用性。
- **影响范围**：仅涉及文档 `amoro.md`，不影响代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯文档变更，无代码依赖，可安全回迁。此修复依赖前一个提交 1751（新增 Amoro 文档），如果 1.4.x 分支回迁了 1751，则应一并回迁本提交。
