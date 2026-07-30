# 提交 0345：Established structured folders to customize MkDocs Material to an Iceberg look and feel

## 提交信息

- **序号**：0345
- **哈希**：9bd5deccddf63537fb1e34788e7c16c3f1fda6da
- **短哈希**：9bd5deccd
- **日期**：2024-01-09 07:04:59 -0800
- **作者**：Brian Olsen（与 Muna Bedan 共同创作）
- **提交说明**：Established structured folders to customize MkDocs Material to an Iceberg look and feel

## 总体目的

本提交是上一个提交（0344 重构站点构建基础设施）的视觉与品牌定制配套。MkDocs Material 是一个功能强大但视觉风格"通用化"的文档主题——默认配色、字体、布局都偏 Material Design 通用风格，与 Apache Iceberg 项目的品牌识别（蓝色调、Logo、社区感）有较大差距。本提交通过 MkDocs Material 的"主题覆盖"（theme customization）机制，建立一套结构化的 `overrides/` 目录与 `assets/` 目录，从模板、样式、脚本、图片四个层面把站点定制成"看起来像 Iceberg 自己的网站"，而不是"用 MkDocs Material 模板搭出来的网站"。

MkDocs Material 的覆盖机制基于"覆盖模板文件"：开发者可以在 `mkdocs.yml` 的 `theme.custom_dir` 字段指定一个目录（这里是 `overrides`），MkDocs Material 在渲染时会优先从这个目录加载模板文件，找不到才回退到主题内置模板。这种机制让开发者既能继承主题的全部功能（如导航 tabs、搜索、TOC、响应式布局），又能针对性修改特定模板（如 `home.html`、`partials/header.html`、`partials/footer.html`）。本提交正是利用这一机制建立了三个 partial 覆盖（header、footer、CTO "Copyright/Trademarks/Other"）与一个完整的首页模板（`home.html`）。

第二个设计重点是"用 Bootstrap 替代默认布局"。MkDocs Material 的内置栅格系统对"营销式首页"（hero section、feature cards、call-to-action 按钮）支持有限，所以本提交引入了 Bootstrap 5 的栅格（`bootstrap-grid.css`）与按钮（`bootstrap-buttons.css`）样式，叠加在 MkDocs Material 的样式之上，让首页 `home.html` 能用 Bootstrap 的 `.container`、`.row`、`.col-lg-12`、`.d-flex flex-row` 等类做布局，呈现经典的"intro 大图 + 中央内容 + 按钮"营销页结构。这种"MkDocs Material + Bootstrap"叠加是社区中常见的混合方案——前者负责文档页面，后者负责首页与营销元素。

附带地，本提交也清理了旧的 `index.md` 内容：原本 `index.md` 中内联了大量 HTML 与 `<style>` 块、引用了 `cdn.jsdelivr.net` 上的 termynal 库做终端动画效果、用 `data-termynal` 属性模拟 SQL 命令行交互；新方案把这些视觉元素全部移到 `overrides/home.html` 模板中，让 `index.md` 只保留 YAML front matter（`title: Apache Iceberg` + `template: home.html`），通过 `template: home.html` 字段告诉 MkDocs Material 用 `home.html` 而不是默认的文档模板渲染该页。这是 MkDocs Material 推荐的"页面级模板选择"机制，让首页与文档页可以共用同一站点但呈现完全不同的视觉结构。

## 如何达成设计目的

实现路径分三层：(1) **MkDocs 配置**——在 `mkdocs.yml` 的 `theme:` 段新增 `custom_dir: overrides`、`static_templates: [home.html]`、`logo: assets/images/Iceberg-logo.svg`、`font.text: Nunito Sans`、`palette.scheme: iceberg`（自定义配色方案），并在 `extra_css` 与 `extra_javascript` 段引入四个 CSS 文件与一个 JS 文件。(2) **模板覆盖**——在 `overrides/` 下创建 `home.html`（321 行的完整首页模板，继承 `main.html`、覆盖 `tabs` 块）与 `overrides/partials/{header,footer,cto}.html` 三个 partial 模板，分别定制页眉、页脚与版权商标区域。(3) **资源文件**——在 `site/docs/assets/` 下创建 `images/`（Logo SVG、footer 背景 webp、向量图 PNG）、`stylesheets/`（extra.css 全局样式、bootstrap-grid.css 栅格、bootstrap-buttons.css 按钮、home.css 首页专用）、`javascript/extra.js`（占位空文件，预留给未来扩展）。

## 修改详情

### `site/mkdocs.yml`

**修改目的**：在主题配置中启用覆盖目录、自定义 Logo/字体/配色，并引入自定义 CSS/JS。

**工作逻辑**：theme 段新增字段：
- `custom_dir: overrides`——指定覆盖模板目录为 `overrides/`，MkDocs Material 渲染时优先从这里加载模板。
- `static_templates: [home.html]`——把 `home.html` 注册为静态模板（不渲染 Markdown 内容，直接作为静态 HTML 输出），用于首页。
- `logo: assets/images/Iceberg-logo.svg`——把 Logo 从 PNG 改为新的 SVG（向量图，可无损缩放）。
- `font.text: Nunito Sans`——指定正文字体为 Nunito Sans（开源无衬线字体，符合 Iceberg 品牌风格）。
- `palette.scheme: iceberg`——启用名为 `iceberg` 的自定义配色方案（在 `extra.css` 中通过 `[data-md-color-scheme="iceberg"]` 选择器定义具体颜色）。

新增 `extra_css:` 段引入四个 CSS：
- `assets/stylesheets/extra.css`——全局自定义样式（header、nav、hero、buttons 等）。
- `assets/stylesheets/bootstrap-grid.css`——Bootstrap 5 栅格系统（5069 行，提供 `.container`、`.row`、`.col-*` 等类）。
- `assets/stylesheets/bootstrap-buttons.css`——Bootstrap 按钮样式（68 行，提供 `.btn`、`.btn-default`、`.btn-lg` 等）。
- `assets/stylesheets/home.css`——首页专用样式（45 行，定义 `.container`、`.row`、`.col-6`、`.center-vertically` 及响应式 media query）。

新增 `extra_javascript:` 段引入一个 JS：
- `assets/javascript/extra.js`——占位空文件（仅一行 `// intentionally left blank`），预留未来扩展。

`watch:` 段新增 `- overrides`，让 `mkdocs serve` 监听 `overrides/` 目录变化触发重建。

### `site/overrides/home.html`（新文件，321 行）

**修改目的**：完整的首页模板，呈现 Iceberg 品牌的营销式首页（hero + features + 终端演示）。

**工作逻辑**：模板用 Jinja2 模板语言继承 `main.html`：`{% extends "main.html" %}`。覆盖 `tabs` 块：`{% block tabs %} {{ super() }} ... {% endblock %}`，先调用 `super()` 输出父模板的 tabs 内容，再追加自定义 HTML。结构层次：

1. **隐藏主内容区域**：通过 `<style>` 块设置 `.md-content { display: none; }`、`.md-sidebar--secondary { display: none; }`、`.md-sidebar--primary { display: none; }`，让首页不显示 MkDocs Material 默认的左侧导航、右侧 TOC 与主内容区域——因为首页有自己的全屏 hero 布局。
2. **Hero section**：`<section class="mdx-container">` 内部用 Bootstrap `.container`、`.d-flex flex-row`、`.col-lg-12` 布局，包含 `<h1>Apache Iceberg</h1>`、`<h3>The open table format for analytic datasets.</h3>`、`<hr class="intro-divider" />`、社交按钮列表（用 `{% for social in config.extra.social %}` 遍历 `mkdocs.yml` 中 `extra.social` 配置，渲染 `{% include ".icons/" ~ social.icon ~ ".svg" %}` 图标 + `social.title` 文字 + `social.link` 链接的按钮）。
3. **About section**：`<section id="about">` 用 `.col-lg-2` + `.col-lg-8` 居中布局，包含 `<h2>What is Iceberg?</h2>` 简介文字与 "Learn More" 按钮链接到 `./spark-quickstart`。
4. **Feature sections**：多个 `<section>` 块分别介绍 "Expressive SQL"、"Full Schema Evolution"、"Hidden Partitioning"、"Time Travel and Rollback"、"Data Compaction" 等 Iceberg 核心特性，每个 section 用 `.col-lg-6` 双栏布局，左栏文字描述 + "Learn More" 按钮，右栏 termynal 终端动画（`<div id="termynal" data-termynal="" data-ty-startdelay="..." data-ty-typedelay="20" data-ty-linedelay="500">` 模拟 SQL 命令行交互）。
5. **Lottie 动画**：在 "Hidden Partitioning" section 中引入 `<lottie-player>` 加载 `https://iceberg.apache.org/lottie/hidden-partitioning-animation.json` 动画。
6. **脚本引入**：底部引入 `https://cdn.jsdelivr.net/gh/ines/termynal@9b301892db6f8d403abfce7adf65888dffed72ea/termynal.js` 实现终端动画效果，用 `data-termynal-container` 属性指定哪些元素需要触发动画。

### `site/overrides/partials/header.html`（新文件，99 行）

**修改目的**：覆盖 MkDocs Material 默认的页眉模板，定制品牌呈现。

**工作逻辑**：从 MkDocs Material 内置的 `partials/header.html` 复制并修改。结构：
- 计算 class：根据 `features` 是否包含 `navigation.tabs.sticky` 与 `navigation.tabs` 决定 header 的 class（`md-header`、`md-header--shadow`、`md-header--lifted`）。
- `<header class="{{ class }}" data-md-component="header">`：页眉容器。
- `<nav class="md-header__inner md-grid">`：内部导航栏，包含：
  - Logo 链接：`<a href="{{ config.extra.homepage | d(nav.homepage.url, true) | url }}" class="md-header__button md-logo">` + `{% include "partials/logo.html" %}` 渲染 Logo。
  - Drawer 按钮：`<label class="md-header__button md-icon" for="__drawer">` + 菜单图标。
  - 标题：`<div class="md-header__title">` 显示站点名与当前页面标题。
  - 搜索按钮：`<label class="md-header__button md-icon" for="__search">` + 搜索图标 + `{% include "partials/search.html" %}` 搜索弹窗。
  - 社交链接：`{% if config.extra.social %} {% include "partials/social.html" %} {% endif %}`。
  - 颜色调色板切换：`{% if config.theme.palette %} {% include "partials/palette.html" %} {% endif %}`。
  - 语言选择：`{% if config.extra.alternate %} {% include "partials/alternate.html" %} {% endif %}`。
  - 仓库信息：`{% if config.repo_url %} <div class="md-header__source">...</div> {% endif %}`。
- Sticky tabs：`{% if "navigation.tabs.sticky" in features %} {% include "partials/tabs.html" %} {% endif %}`。

### `site/overrides/partials/footer.html`（新文件，22 行）

**修改目的**：覆盖 MkDocs Material 默认的页脚模板，定制版权信息呈现。

**工作逻辑**：`<footer class="md-footer">` 包含 `<div class="md-footer-meta md-typeset">` + `<div class="md-footer-meta__inner md-grid">` + `<div class="md-copyright">`，里面输出 Apache Iceberg 商标与版权声明（包含 Apache Software Foundation 链接），并标注"Made with Material for MkDocs"。

### `site/overrides/partials/cto.html`（新文件，21 行）

**修改目的**：CTO（Copyright/Trademarks/Other）partial，输出商标与版权信息 + 社交按钮。

**工作逻辑**：`<div class="cto">` + `<div class="container">` 包含：商标版权文字段落（与 footer 类似但格式不同）+ `<ul class="list-inline intro-social-buttons">` 用 `{% for social in config.extra.social %}` 遍历社交链接渲染按钮。这个 partial 可被首页或其他页面在特定位置插入。

### `site/docs/assets/stylesheets/extra.css`（新文件，470 行）

**修改目的**：全局自定义样式，定义 Iceberg 品牌视觉风格。

**工作逻辑**：包含多组样式：
- **Header**：`.md-header` 设置白色背景、黑色文字、sticky 定位、z-index 4。
- **Nav tabs**：`.md-tabs`、`.md-tabs__list` 设置白色背景、黑色文字、居中对齐、滚动条隐藏。
- **Hero section**：`.mdx-container` 设置背景图（`url("../images/intro-bg.webp")`）、`height: 32rem`、白色文字；`.intro-message h1` 字号 8em、文字阴影；`.intro-divider` 宽 400px；`.intro-message h3` 字号 24px。
- **Buttons**：`.list-inline`、`.list-inline li` inline-block 布局。
- **CTO**：`.topnav-page-selection img` 设置高度。
- **Custom color scheme**：`[data-md-color-scheme="iceberg"]` 定义 Iceberg 配色方案（背景、文字、链接、按钮等颜色变量）。
- **Custom logo**：`.md-header__button.md-logo`、`.md-footer` Logo 样式调整。
- **其他**：响应式 media query、链接 hover 效果、code 块样式等。

### `site/docs/assets/stylesheets/bootstrap-grid.css`（新文件，5069 行）

**修改目的**：引入 Bootstrap 5 栅格系统，提供 `.container`、`.row`、`.col-*` 等布局类。

**工作逻辑**：标准的 Bootstrap 5 grid CSS，定义容器、行、列的响应式布局系统。文件较大（5069 行）因为是完整 Bootstrap grid 模块，包含所有断点（xs/sm/md/lg/xl/xxl）的所有列数组合（1-12）。在首页 `home.html` 中通过 `.container`、`.d-flex flex-row`、`.col-lg-12`、`.col-lg-2`、`.col-lg-8` 等类做布局。

### `site/docs/assets/stylesheets/bootstrap-buttons.css`（新文件，68 行）

**修改目的**：引入 Bootstrap 按钮样式，提供 `.btn`、`.btn-default`、`.btn-lg` 等。

**工作逻辑**：定义按钮基础样式（`.btn` 字号、padding、display、user-select 等）与大小变体（`.btn-lg` padding 与字号）与默认变体（`.btn-default` 颜色、背景、边框，及 `:focus`、`:hover`、`:active` 状态）。在首页 `home.html` 中用于 "Learn More"、社交按钮等。

### `site/docs/assets/stylesheets/home.css`（新文件，45 行）

**修改目的**：首页专用样式，定义容器、行、列布局与响应式断点。

**工作逻辑**：定义 `.container`（max-width 1140px、margin auto、padding 15px、margin-top 3rem）、`.row`（flex wrap、margin -15px）、`.col-6`（flex 0 0 50%、max-width 50%）、`.center-vertically`（align-items center、justify-content center、flex-direction column）。响应式 media query `@media (max-width: 767px)`：`.col-6` 在小屏幕下变为 100%（`flex: 0 0 100%`），并调整 `div#termynal-expressive-sql` 位置。

### `site/docs/assets/javascript/extra.js`（新文件，1 行）

**修改目的**：占位文件，预留给未来 JavaScript 扩展。

**工作逻辑**：仅一行 `// intentionally left blank` 注释。存在该文件是为了让 `mkdocs.yml` 的 `extra_javascript` 配置能正确解析（避免指向不存在的文件），同时为未来可能的客户端脚本（如统计、交互效果）预留位置。

### `site/docs/assets/images/Iceberg-logo.svg`（新文件，28 行）

**修改目的**：Iceberg Logo 的 SVG 向量图，作为站点主 Logo。

**工作逻辑**：标准 SVG 文件，包含 Iceberg Logo 的路径定义，可无损缩放到任意尺寸，优于原本的 PNG Logo。

### `site/docs/assets/images/footer-bg.webp`（二进制，10472 字节）

**修改目的**：页脚背景图片，WebP 格式。

**工作逻辑**：WebP 格式的图片，用于 `extra.css` 中 `.mdx-container` 的背景，提供 hero section 的视觉效果。

### `site/docs/assets/images/iceberg-vector-image-asset.png`（二进制，2652369 字节）

**修改目的**：Iceberg 向量图的 PNG 资源（约 2.6MB），可能用于社交媒体预览或大型展示。

**工作逻辑**：高分辨率的 PNG 图片，作为向量图的栅格化版本，用于不支持 SVG 的场景或需要高分辨率展示的位置。

### `site/docs/index.md`

**修改目的**：把首页内容从内联 HTML 改为通过模板引用，让 `index.md` 只承担"指向模板"的职责。

**工作逻辑**：
- front matter 修改：`title: "Intro to Apache Iceberg"` → `title: "Apache Iceberg"` + `template: home.html`（关键改动，让 MkDocs Material 用 `home.html` 模板渲染该页）。
- 删除原本内联的 `<link href="...termynal.css" rel="stylesheet">`、`<style>` 块、`<section id="intro">` HTML、`## Apache Iceberg` Markdown 标题、`## What is Iceberg?` 段、`## Expressive SQL` 段、`## Full Schema Evolution` 段、`## Hidden Partitioning` 段、`## Time Travel and Rollback` 段、`## Data Compaction` 段，以及末尾的 termynal.js 脚本引入。这些内容已全部迁移到 `overrides/home.html` 模板中，`index.md` 只保留 front matter 与 Apache 许可证注释。

### `site/dev/common.sh`

**修改目的**：微调清理脚本，移除多余空行。

**工作逻辑**：在 `clean()` 函数中删除了一处多余空行（`git worktree remove docs/javadoc &> /dev/null` 后的空行），是提交合并时的格式整理。

## 小结

本提交是 Iceberg 文档站点视觉品牌定制的核心改动，通过 MkDocs Material 的覆盖机制建立结构化的 `overrides/` 与 `assets/` 目录，从模板（`home.html` + 三个 partials）、样式（`extra.css` + `bootstrap-grid.css` + `bootstrap-buttons.css` + `home.css`）、脚本（`extra.js` 占位）、图片（Iceberg-logo.svg + footer-bg.webp + 向量图 PNG）四个层面把站点定制成"Iceberg 自己的网站"。设计采用"MkDocs Material 主体 + Bootstrap 5 栅格叠加"的混合方案——前者负责文档页面的导航/搜索/TOC/响应式，后者负责首页的营销式布局（hero、feature cards、call-to-action 按钮）。`mkdocs.yml` 通过 `custom_dir: overrides`、`palette.scheme: iceberg`、`font.text: Nunito Sans`、`extra_css`/`extra_javascript` 等配置启用定制；`index.md` 通过 `template: home.html` 字段让首页走自定义模板而非默认文档模板。本提交与上一个提交（0344 重构构建基础设施）合在一起，构成了 Iceberg 1.4.x 系列文档发布前的"基础设施 + 视觉品牌"双重刷新。
