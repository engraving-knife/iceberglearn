# 提交 0671：docs: Fix links of `Get Started` and `Community` parts in footer (#10098)

## 提交信息
- **序号**：0671 / 4088
- **哈希**：96793bf621524f57e99cd19d410cde734bf588eb
- **短哈希**：96793bf62
- **日期**：2024-04-11 04:35:09 +0800
- **作者**：Wei Guo
- **提交说明**：docs: Fix links of `Get Started` and `Community` parts in footer (#10098)
- **PR/Issue**：#10098

## 总体目的

这个提交修复了 Iceberg 官网（基于 MkDocs Material 主题）页脚（footer）中 `Get Started` 与 `Community` 两个分区的链接问题。原本页脚中的链接使用了相对路径（如 `spark-quickstart`、`community/#slack`），在某些部署/路由上下文下这些相对路径无法正确解析到站点根，导致用户点击后跳转到错误页面（例如 404 或拼接到当前页面 URL 之后的无效地址）。

提交将所有这些相对链接改为以 `/` 开头的根路径绝对链接（如 `/spark-quickstart`、`/community/#slack`），使得无论用户当前处于站点中哪一层级页面（例如 `/docs/latest/`、`/blogs/some-post/`），页脚链接都能稳定地指向站点根下的对应资源，避免相对路径解析受当前页面路径影响。

这类文档修复虽然不涉及核心代码逻辑，但对于用户访问文档、快速上手以及参与社区至关重要，属于提升用户访问体验与站点可访问性的常见维护性改动。

## 如何达成设计目的

整体策略非常直接：在自定义页脚模板 `site/overrides/partials/footer.html` 中，将 `Get Started` 与 `Community` 两组链接的 `href` 属性值统一前置 `/`，把相对路径变为相对站点根的绝对路径。MkDocs Material 支持通过 `overrides/partials/footer.html` 覆盖默认页脚，因此只需修改这一个模板文件即可全站生效。改动没有触及任何构建配置或站点导航的 `mkdocs.yml`，影响面控制在页脚模板本身。

## 修改详情

### `site/overrides/partials/footer.html`

**修改目的**：将页脚中 `Get Started` 与 `Community` 两组共 12 个链接从相对路径改为根路径绝对路径，避免相对路径在不同页面层级下解析错误。

**工作逻辑**：

1. `Get Started` 区块下 6 个链接全部前置 `/`：
   - `spark-quickstart` → `/spark-quickstart`
   - `hive-quickstart` → `/hive-quickstart`
   - `spec/` → `/spec/`
   - `docs/latest` → `/docs/latest`
   - `blogs/` → `/blogs/`
   - `talks/` → `/talks/`

2. `Community` 区块下 6 个链接（含锚点）全部前置 `/`：
   - `community/#slack` → `/community/#slack`
   - `community/#mailing-lists` → `/community/#mailing-lists`
   - `community/#iceberg-community-events` → `/community/#iceberg-community-events`
   - `community/#issues` → `/community/#issues`
   - `community/#contribute` → `/community/#contribute`
   - `community/#community-guidelines` → `/community/#community-guidelines`

注意对于带锚点的链接，`/` 仅前置到路径部分，锚点 `#xxx` 仍保持在路径之后，浏览器会先按根路径定位页面再滚动到对应锚点，符合 HTML 规范行为。代码中没有改动注释掉的 `Solr Logos and Assets` 链接（已是 `/logos-and-assets.html` 形式）。

## 小结
- **成效**：成功将页脚链接从相对路径改为根路径绝对路径，解决了在子页面访问时链接解析错误的问题。
- **影响范围**：仅影响 Iceberg 官网页脚模板，所有页面底部 `Get Started` 与 `Community` 链接行为统一、稳定。
- **回迁到 1.4.x 的注意事项**：无特别注意事项。这是纯文档/前端模板改动，与 1.4.x 代码逻辑无耦合，可直接 cherry-pick。需注意仓库 `site/` 目录在 1.4.x 分支是否仍存在且结构一致；若一致则回迁无风险。
