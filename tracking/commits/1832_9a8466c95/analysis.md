# 提交 1832：Site: Fix Footer Link (#12478)

## 提交信息

- **序号**：1832 / 4088
- **哈希**：9a8466c95c323eba7c427cafb44a6c91a81d4bf4
- **短哈希**：9a8466c95
- **日期**：2025-03-07 15:21:54 -0600
- **作者**：Russell Spitzer
- **提交说明**：Site: Fix Footer Link (#12478)
- **PR/Issue**：#12478

## 总体目的

本提交修复了 Iceberg 官网页脚中"REST Catalog"链接指向错误路径的问题。在此之前，页脚的 REST Catalog 链接指向 `/concepts/catalog/#decoupling-using-the-rest-catalog`，但该路径在网站结构中已不存在或不正确，导致用户点击后无法到达预期页面。修复后将链接改为 `/terms/#decoupling-using-the-rest-catalog`，指向正确的术语页面中的对应锚点。

这是一个纯文档/网站修复，不涉及任何代码逻辑变更，影响范围仅限于网站导航体验。

## 如何达成设计目的

通过修改 `site/overrides/partials/footer.html` 中 REST Catalog 链接的 `href` 属性，将路径从 `/concepts/catalog/#decoupling-using-the-rest-catalog` 改为 `/terms/#decoupling-using-the-rest-catalog`。`/terms/` 是 mkdocs-material 站点中实际存在术语说明的页面，`#decoupling-using-the-rest-catalog` 锚点对应其中关于 REST Catalog 解耦的章节。

## 修改详情

### `site/overrides/partials/footer.html` (修改, 1 line)

**修改目的**：修正页脚 REST Catalog 链接的 URL 路径。

**工作逻辑**：将 footer.html 第 40 行的 `<a href="/concepts/catalog/#decoupling-using-the-rest-catalog">REST Catalog</a>` 中的 href 属性值改为 `/terms/#decoupling-using-the-rest-catalog`。这是 mkdocs-material 主题的 footer 覆盖模板，控制网站底部的导航链接列表。修改后链接将正确指向术语页中 REST Catalog 解耦章节。

## 小结

本提交是网站链接修复，改动极小（1 行），不影响任何代码逻辑。回迁到 1.4.x 无风险，只需确认 `site/overrides/partials/footer.html` 文件存在即可直接应用。
