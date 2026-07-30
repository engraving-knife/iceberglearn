# 提交 3110：site: Apache Iceberg Project News and Blog (#15013)

## 提交信息

- **序号**：3110 / 4088
- **哈希**：243badb3faa9290e66e710091dd9cd4e6c71a5a2
- **短哈希**：243badb3f
- **日期**：2026-01-13
- **作者**：Kevin Liu
- **提交说明**：site: Apache Iceberg Project News and Blog (#15013)
- **PR/Issue**：#15013

## 总体目的

本提交为 Apache Iceberg 官方网站新增了"Project News and Blog"（项目新闻与博客）板块。在此之前的官网主要是文档 + 规范 + 社区资源，缺少一个集中发布项目公告、活动信息和深度文章的渠道。随着 Iceberg 社区规模扩大、年度 Iceberg Summit 等活动需要向社区广播，建立一个正式的博客入口对项目对外沟通很有必要——既能发布峰会公告、版本亮点等新闻，也能承载技术深度文章。

具体到本提交，它同时完成了"基础设施搭建"和"首篇内容发布"两件事：基础设施上，启用了 mkdocs-material 的 blog 插件并完成目录、导航、页脚的接入；内容上，发布了首篇博文宣布 Iceberg Summit 2026 回归（2026 年 4 月 8-9 日，旧金山 Marriott Marquis），详细介绍活动安排、征稿（Call for Papers）的截止时间与议题方向、会议形式（Breakout/Lightning/Keynote/Panel/Workshop）等，并提供了 Sessionize 投稿入口与社区 Slack 的 #abstracts 答疑渠道。作者配置上定义了 `iceberg-pmc` 这一官方 PMC 作者身份，用于以 PMC 名义发布官方公告类博文。

## 如何达成设计目的

整体通过 mkdocs-material 的 blog 插件实现。新增 `site/docs/blog/` 目录承载博客内容（作者配置、首页、`posts/` 下的文章），在 `mkdocs.yml` 中启用 `blog` 插件并配置目录、TOC、日期格式、URL 格式，在 `mkdocs-dev.yml`、`nav.yml` 的导航树中加入 `Blog` 入口，并在自定义页脚 `footer.html` 中加入 `/blog/` 链接，使用户从任意页面都能进入博客。博文采用 mkdocs-material 博客 front matter（`date`/`title`/`authors`/`categories`）约定，并通过 `<!-- more -->` 控制列表页摘要截断。

## 修改详情

### `site/docs/blog/.authors.yml` (+20/-0 lines, 新建)

**修改目的**：定义博客作者元数据。

**工作逻辑**：新建文件，定义 `iceberg-pmc` 作者：`name: Apache Iceberg PMC`、`description: Apache Iceberg PMC`、`avatar` 指向 iceberg logo 图标。博文 front matter 中 `authors: - iceberg-pmc` 即引用此处的作者定义，渲染时显示作者名与头像。

### `site/docs/blog/index.md` (+18/-0 lines, 新建)

**修改目的**：博客板块首页。

**工作逻辑**：新建文件，内容为标题 `# Project News and Blog`，作为 blog 插件的目录首页。blog 插件会在此目录下自动列出 `posts/` 中的文章。

### `site/docs/blog/posts/2026-01-10-iceberg-summit.md` (+92/-0 lines, 新建)

**修改目的**：发布首篇博文，宣布 Iceberg Summit 2026。

**工作逻辑**：博文 front matter 设置 `date: 2026-01-10`、`title: Announcing Iceberg Summit 2026`、`authors: iceberg-pmc`、`categories: announcement`。正文依次说明：2026 年 4 月 8-9 日在旧金山 Marriott Marquis 举办、扩展为两天线下；活动由 ASF 授权、Iceberg PMC 监督；Call for Papers 开放，列出议题方向（生产实践、技术特性、数据架构、最佳实践、思想领导力）与会议形式（Breakout 30 分钟、Lightning 15 分钟、Keynote 30 分钟、Panel 45 分钟、Workshop 60-180 分钟），给出 Sessionize 投稿链接与社区 Slack #abstracts 答疑安排，并列出关键日期（投稿截止 1 月 18 日、演讲者公布 2 月 6 日）。`<!-- more -->` 之前为列表摘要内容。

### `site/mkdocs-dev.yml` (+1/-0 lines)

**修改目的**：在开发环境导航中加入 Blog 入口。

**工作逻辑**：在 `nav` 的 `Vendors: vendors.md` 之后增加 `- Blog: blog/`，使本地开发预览时导航栏出现博客入口。

### `site/mkdocs.yml` (+5/-0 lines)

**修改目的**：启用并配置 blog 插件。

**工作逻辑**：在 `plugins` 中 `search` 之后加入 `blog` 插件配置：`blog_dir: blog`（博客目录）、`blog_toc: true`（启用目录）、`post_date_format: long`（长日期格式）、`post_url_format: "{slug}"`（URL 使用 slug 形式）。这是博客板块能够运行的核心配置。

### `site/nav.yml` (+1/-1 lines)

**修改目的**：在主导航配置中加入 Blog 入口。

**工作逻辑**：在 `Vendors: vendors.md` 与 `Specification` 之间把原有的空行替换为 `- Blog: blog/`，使线上站点的导航树包含博客入口。

### `site/overrides/partials/footer.html` (+1/-0 lines)

**修改目的**：在页脚加入博客链接。

**工作逻辑**：在页脚链接列表的 `Docs` 之后新增 `<li><a href="/blog/">Blog</a></li>`，使用户在任意页面底部都能跳转到博客。

## 总结

本提交为 Iceberg 官网搭建了完整的博客基础设施（mkdocs blog 插件 + 导航 + 页脚接入 + 作者配置）并发布了首篇官方博文宣布 Iceberg Summit 2026。其核心价值在于为项目提供了一个正式的新闻与内容发布渠道，支撑峰会公告、版本亮点、技术深度文章等对外沟通需求，是社区运营层面的重要补充。
