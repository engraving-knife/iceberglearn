# 提交 2713：infra: add analytics for iceberg.apache.org

## 提交信息

- **序号**：2713 / 4088
- **哈希**：a6ea2e38ab7c2ae98cbe85a65095c8abe82d2b8d
- **短哈希**：a6ea2e38a
- **日期**：2025-10-02 09:30:56 -0700
- **作者**：Kevin Liu
- **提交说明**：infra: add analytics for iceberg.apache.org
- **PR/Issue**：#14158

## 总体目的

Apache Iceberg 的文档网站 `iceberg.apache.org` 使用 MkDocs Material 主题构建。在此之前，该网站没有集成任何网站分析工具，项目维护者无法了解网站的访问量、用户行为、热门页面等基本指标。

Apache 软件基金会提供了自托管的 Matomo 分析平台（位于 `analytics.apache.org`），各 Apache 项目可以接入该平台来收集网站访问数据。Matomo 是一个注重隐私的开源分析工具，Apache 基金会推荐各项目使用它来替代 Google Analytics 等第三方分析服务。

此提交的目的是为 Iceberg 文档网站添加 Matomo 分析代码，使项目维护者能够获取网站使用情况的数据，从而做出更好的文档改进决策。

## 如何达成设计目的

MkDocs Material 主题支持通过自定义覆盖（overrides）来扩展模板。该提交在 `site/overrides/` 目录下创建了 `main.html` 文件，继承自基础模板 `base.html`，并在 `extrahead` 块中注入了 Matomo 的 JavaScript 追踪代码。

Matomo 代码遵循 Apache 基金会的标准配置模式：禁用 cookie 和 Do Not Track，以尊重用户隐私。站点 ID 为 82，追踪端点指向 `https://analytics.apache.org/matomo.php`。

## 修改详情

### `site/overrides/main.html` (+22/-0 lines, 新文件)

**修改目的**：创建 MkDocs Material 主题覆盖文件，注入 Matomo 分析脚本。

**工作逻辑**：
1. `{% extends "base.html" %}`：继承 MkDocs Material 的基础模板。
2. `{% block extrahead %}`：重写 `extrahead` 块，在 HTML `<head>` 中添加额外内容。先调用 `{{ super() }}` 保留原有 head 内容。
3. Matomo 追踪脚本：
   - `setDoNotTrack(true)`：尊重浏览器的 Do Not Track 设置
   - `disableCookies()`：禁用 cookie，进一步保护用户隐私
   - `trackPageView()`：追踪页面访问
   - `enableLinkTracking()`：启用链接点击追踪
   - 设置追踪 URL 为 `https://analytics.apache.org/matomo.php`
   - 设置站点 ID 为 `82`
   - 异步加载 `matomo.js` 脚本

## 总结

此提交为 Iceberg 文档网站添加了 Apache 基金会托管的 Matomo 网站分析。配置注重用户隐私（禁用 cookie、尊重 Do Not Track），使项目维护者能够获取网站访问数据来指导文档和网站的改进工作。这是一个纯基础设施变更，不影响任何代码功能。
