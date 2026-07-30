# 提交 2706：Site: Remove Blogs and Talks From Site (#14110)

## 提交信息

- **序号**：2706 / 4088
- **哈希**：d1fddd8d6300438711ed4b0bb2a0efa329e162cb
- **短哈希**：d1fddd8d6
- **日期**：2025-09-30 07:53:42 -0700
- **作者**：Russell Spitzer
- **提交说明**：Site: Remove Blogs and Talks From Site (#14110)
- **PR/Issue**：#14110

## 总体目的

本提交从 Iceberg 官方站点移除"Blogs"（博客）页面，并将"Talks"（演讲）页面从逐一罗列大量历史演讲条目精简为仅指向官方 YouTube 频道与 Iceberg Summit 2025 播放列表的链接。这是站点内容维护策略的调整。

此前站点维护着一份手工编辑的博客列表（`site/docs/blogs.md`，约 800 行）和一份手工编辑的演讲列表（`site/docs/talks.md`，约 139 行），列举各公司/作者发布的 Iceberg 相关博客文章与会议演讲视频。这类手工维护的外部内容列表存在几个问题：

1. **维护成本高**：需要社区持续手工添加新条目，容易过时；
2. **链接易失效**：外部博客/视频链接随时间可能失效，需持续检查（页面中大量 `markdown-link-check-disable-next-line` 注释印证了这点）；
3. **选择性偏差**：列表难免遗漏或不全面，难以做到公正完整；
4. **并非项目核心文档**：博客与演讲属于社区衍生内容，非 Iceberg 项目本身的技术文档。

本次调整将博客页面彻底移除，演讲页面改为指向官方 YouTube 频道（`@ApacheIceberg`）与 Summit 2025 播放列表，由官方频道作为演讲视频的权威来源，减轻站点维护负担。同时清理导航与页脚中对博客页面的引用，以及文档正文中对博客页面的链接。

## 如何达成设计目的

1. 删除 `site/docs/blogs.md` 文件（连同其 800 行手工维护的博客条目）。
2. 重写 `site/docs/talks.md`：移除全部逐条演讲列表，仅保留"Official Apache Iceberg Youtube Channel"与"Iceberg Summit 2025 Playlist"两个链接。
3. 从 `site/nav.yml` 导航配置中移除 `Blogs: blogs.md` 条目（保留 Talks）。
4. 从 `site/overrides/partials/footer.html` 页脚模板中移除 Blogs 链接（保留 Talks）。
5. 在 `docs/docs/aws.md` 中删除指向博客页面的句子（"Search the Iceberg blogs page for tutorials..."）。

## 修改详情

### `docs/docs/aws.md` (+1/-2 lines)

**修改目的**：移除正文中对博客页面的引用。

**工作逻辑**：删除 EKS 小节中"Search the [Iceberg blogs](../../blogs.md) page for tutorials around running Iceberg with Docker and Kubernetes."一行；同时文件末尾补上缺失的换行符（`\ No newline at end of file` 消除）。

### `site/docs/blogs.md` (+0/-800 lines, 删除)

**修改目的**：移除手工维护的博客列表页面。

**工作逻辑**：整文件删除。该文件原含约 800 行，按时间倒序列举各公司/作者的 Iceberg 相关博客文章（含标题、日期、公司、作者、链接），每条用 `markdown-link-check-disable-next-line` 注释禁用链接检查。删除后博客内容不再由 Iceberg 站点维护。

### `site/docs/talks.md` (+5/-134 lines)

**修改目的**：精简演讲页面，改指向官方 YouTube 频道。

**工作逻辑**：移除全部逐条演讲列表（数十条 YouTube 视频条目，含标题、日期、作者、链接），替换为两条链接：
- "Official Apache Iceberg Youtube Channel: https://www.youtube.com/@ApacheIceberg"
- "Apache Iceberg Summit 2025 Playlist: Iceberg Summit 2025（YouTube 播放列表链接）"

保留页面标题 `title: "Talks"` 与 "## Iceberg Talks" 标题。

### `site/nav.yml` (+0/-1 lines)

**修改目的**：从站点导航移除 Blogs 条目。

**工作逻辑**：在 `Community` 导航组下删除 `- Blogs: blogs.md` 行，保留 `Community`、`Talks`、`Vendors` 等条目。

### `site/overrides/partials/footer.html` (+0/-1 lines)

**修改目的**：从页脚移除 Blogs 链接。

**工作逻辑**：删除页脚链接列表中 `<li><a href="/blogs/">Blogs</a></li>` 行，保留 Talks 等其它链接。

## 总结

本提交是站点内容策略调整：移除手工维护的博客列表页面，将演讲页面精简为指向官方 YouTube 频道与 Summit 播放列表，并同步清理导航、页脚与正文中的相关引用。这降低了站点维护成本与外部链接失效风险，将社区衍生内容（博客/演讲视频）的权威来源交给官方 YouTube 频道。属于纯站点/文档类修改，不涉及代码逻辑。
