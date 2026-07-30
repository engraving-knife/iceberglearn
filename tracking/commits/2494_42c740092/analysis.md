# 提交 2494：Docs: Fix community links in footer (#13796)

## 提交信息

- **序号**：2494 / 4088
- **哈希**：42c740092713b50978297fa6a41818ff5bf65c4f
- **短哈希**：42c740092
- **日期**：2025-08-13 08:48:23 -0700
- **作者**：Manu Zhang
- **提交说明**：Docs: Fix community links in footer (#13796)
- **PR/Issue**：#13796

## 总体目的

本提交修复了 Iceberg 官方网站页脚中的两处社区链接，使其指向正确的页面锚点。

网站页脚是用户快速访问社区资源的重要导航入口。原有的两个链接存在问题：
1. "Iceberg Events" 链接指向 `/community/#iceberg-community-events`，但实际的锚点 ID 已经变更（参见提交 2495 中 community.md 的调整），应改为 `/community/#connect-with-community-events`。
2. "Contribute" 链接指向 `/community/#contribute`，但贡献页面实际上是一个独立的页面 `/contribute`，而非 community 页面中的锚点。

## 如何达成设计目的

直接修改 `footer.html` 中的两个 `<a href>` 链接地址。

## 修改详情

### `site/overrides/partials/footer.html` (+2/-2 lines)

**修改目的**：修复页脚中的两处错误链接。

**工作逻辑**：
- "Iceberg Events" 链接由 `/community/#iceberg-community-events` 改为 `/community/#connect-with-community-events`。
- "Contribute" 链接由 `/community/#contribute` 改为 `/contribute`。

## 总结

本提交是一个文档链接修复，确保网站页脚的导航链接指向正确的目标页面。虽然改动很小，但对于用户体验和社区入口的可访问性有实际意义。
