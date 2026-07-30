# 提交 3145：Site: Add RSS feed for blogs (#15071)

## 提交信息

- **序号**：3145 / 4088
- **哈希**：672e8603a4568bfe57ca1406b7ce69a1bb6a6ec3
- **短哈希**：672e8603a
- **日期**：2026-01-23
- **作者**：Manu Zhang
- **提交说明**：Site: Add RSS feed for blogs (#15071)
- **PR/Issue**：#15071

## 总体目的

Apache Iceberg 官网（`https://iceberg.apache.org`）使用 mkdocs-material 构建，其中 `blog` 插件（`blog_dir: blog`）维护博客文章。但此前站点没有 RSS 订阅源，用户和 RSS 阅读器无法订阅博客更新，新文章发布只能靠人工访问站点发现。对于一个开源项目而言，缺乏 RSS 源会降低社区跟进博客（版本发布说明、最佳实践、案例分享等）的便利性。

本提交通过引入 `mkdocs-rss-plugin` 为博客自动生成 RSS 订阅源（`rss.xml`），让读者可以通过 RSS 阅读器订阅 Iceberg 博客更新。同时把 Iceberg logo 配置为 feed 的 image，使订阅源在阅读器中有品牌识别度。改动仅涉及站点构建配置（`mkdocs.yml`）与 Python 依赖（`requirements.txt`），不改变博客内容本身。

## 如何达成设计目的

在 `site/mkdocs.yml` 中新增 `site_url`（RSS 插件生成绝对 URL 的前提）并注册 `rss` 插件，配置其只匹配博客文章路径（`match_path: "blog/posts/.*"`）、使用文章 meta 中的 `date` 作为创建时间、用 `categories` 字段作为分类、输出文件名为 `rss.xml`、以 Iceberg logo 作为 feed 图片；在 `site/requirements.txt` 中固定 `mkdocs-rss-plugin==1.17.4` 版本，确保 CI/本地构建能安装该插件。

## 修改详情

### `site/mkdocs.yml` (+11/-0 lines)

**修改目的**：为站点启用并配置 RSS feed 插件。

**工作逻辑**：
- 新增 `site_url: "https://iceberg.apache.org"`。`mkdocs-rss-plugin` 依赖 `site_url` 生成 feed 中每篇文章的绝对链接，此前缺失该字段会导致 RSS 条目链接无法解析为完整 URL。
- 在 `plugins` 列表中、`blog` 插件之后新增 `rss` 插件配置块：
  - `feed_description: "Blogs for Apache Iceberg"`——feed 的描述文案。
  - `match_path: "blog/posts/.*"`——只把 `blog/posts/` 路径下的页面纳入 RSS，与 `blog` 插件的 `blog_dir: blog` 及文章生成路径对应，避免把非博客页面（如文档页）混入 feed。
  - `image: "https://iceberg.apache.org/assets/images/Iceberg-logo.svg"`——feed 的 channel image，使用线上 Iceberg logo 的绝对 URL，使 RSS 阅读器展示品牌图标。
  - `date_from_meta: { as_creation: date }`——从文章 front matter 的 `date` 字段取创建时间作为 RSS 条目的发布时间，保证排序与时间戳准确。
  - `categories: [categories]`——把文章 meta 中的 `categories` 字段映射为 RSS 条目分类。
  - `feeds_filenames: { rss_created: rss.xml }`——指定按创建时间生成的 RSS 输出文件名为 `rss.xml`（站点根下可访问为 `https://iceberg.apache.org/rss.xml`）。
- 该插件块位于 `blog` 之后、`macros`/`monorepo`/`privacy` 等插件之前，确保博客文章先生成再被 RSS 采集。

### `site/requirements.txt` (+1/-0 lines)

**修改目的**：声明 RSS 插件依赖。

**工作逻辑**：
新增 `mkdocs-rss-plugin==1.17.4`，与其它 mkdocs 插件一样固定具体版本，确保站点构建环境一致。该插件会在 `mkdocs build` 时被加载并按 `mkdocs.yml` 的 `rss` 配置生成 `rss.xml`。其余依赖（`mkdocs-material==9.6.23`、`mkdocs-monorepo-plugin`、`mkdocs-redirects==1.2.2`、`pymarkdownlnt==0.9.34` 等）保持不变。

## 总结

该提交为 Apache Iceberg 官网博客引入 `mkdocs-rss-plugin` 自动生成 RSS 订阅源，通过配置 `site_url`、匹配博客文章路径、以文章 meta 的 `date`/`categories` 作为发布时间与分类、并用 Iceberg logo 作为 feed 图片，使读者可通过 RSS 阅读器订阅博客更新，提升了项目博客的可订阅性与社区信息触达效率。
