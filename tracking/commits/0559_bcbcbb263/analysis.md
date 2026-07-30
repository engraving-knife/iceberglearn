# 提交 0559：Site: Update for ASF site guidelines

## 提交信息

- **序号**：0559 / 4088
- **哈希**：bcbcbb263ea7e13ab22d0feb918e207c7e42dbbd
- **短哈希**：bcbcbb263
- **日期**：2024-03-03 12:44:39 -0800
- **作者**：Brian "bits" Olsen <bits@bitsondata.dev>
- **提交说明**：Site: Update for ASF site guidelines (#9729)
- **PR/Issue**：#9729
- **共同作者**：Muna Bedan <munabedan@gmail.com>

## 总体目的

将 Iceberg 站点（基于 MkDocs Material）改造成符合 Apache 软件基金会（ASF）站点指南的形态。ASF 对项目站点有若干要求，本提交一次性满足多项：

1. **隐私合规**：避免在站点页面中直接引用第三方 CDN（如 jsdelivr、unpkg），改为本地托管 JS/CSS 资源，减少对外部域的依赖与第三方追踪。同时在 MkDocs 中启用 `privacy` 插件进一步自动处理外部资源。
2. **必需导航链接**：ASF 要求项目站点提供"Foundation Sponsorship""Events""Privacy Policy""License""Security""Sponsors"等链接。本提交在 `nav.yml` 顶部 ASF 下补齐这些链接（特别是新增 Privacy 链接，并把原本错误指向 sponsorship 的"Sponsorship"项改回 thanks）。
3. **统一页脚**：ASF 要求页脚包含商标声明、版权声明、许可证链接以及 Foundation 相关链接。本提交重写 `footer.html`，按 ASF 推荐的多列布局组织：Features / Get Started / Community / ASF 四列 + ASF logo + 社交图标 + 版权声明，并把版权年份从 2023 更新到 2024。
4. **移除冗余 CTO 区块**：原本通过 `partials/cto.html` 在首页 footer 区额外注入商标+社交按钮区块，与新版统一页脚重复，整体删除。

## 如何达成设计目的

整体设计思路是"本地化资源 + 引入 privacy 插件 + 重写页脚 + 补全导航"。具体路径：

1. **资源本地化**：把原本通过 `cdn.jsdelivr.net` 引入的 `termynal.css`/`termynal.js` 与通过 `unpkg.com` 引入的 `lottie-player.js` 全部下载到本地 `site/docs/assets/javascript/` 与 `site/docs/assets/stylesheets/` 目录，在 `home.html` 中改引用本地路径。这样页面不再向第三方 CDN 发起请求，符合 ASF 关于减少外部依赖的偏好，也避免 CDN 不可用时站点功能受损。
2. **隐私插件**：在 `mkdocs.yml` 的 plugins 列表中新增 `- privacy`。MkDocs Material 的 privacy 插件会自动把页面中剩余的外部资源（如 `fonts.gstatic.com` 的字体文件）做本地化或加标记处理，进一步满足隐私合规要求。
3. **页脚重构**：删除原 `partials/cto.html`，在 `home.html` 中移除对它的 `{% include %}` 调用，把 `partials/footer.html` 重写为标准 ASF 多列页脚。版权声明从 mkdocs.yml 的 `copyright` 字段移到 footer.html 内部，便于精细控制布局。
4. **导航补全**：在 `nav.yml` 的 ASF 子菜单中新增 Privacy 链接，修正 Sponsorship 链接（原先错指向 thanks.html，与下方 Sponsors 重复）。
5. **样式同步**：在 `extra.css` 中删除旧 `.cto`/`.md-footer`/`.md-copyright` 样式，新增 `#footer`/`.footer-top`/`.copyright-text`/`.footer-icons` 等新页脚样式；新增 `fonts.css` 显式声明 Nunito Sans 与 Roboto Mono 字体的 `@font-face`（虽然仍引用 gstatic，但通过 `font-display: fallback` 与 privacy 插件配合处理）；新增 `termynal.css` 本地化终端动画样式。

## 修改详情

### `site/mkdocs.yml`

**修改目的**：移除内联 `copyright` 字段（改由 footer.html 接管），新增 `privacy` 插件以满足 ASF 隐私合规要求。

**工作逻辑**：

- 删除：
  ```yaml
  copyright: |
    Apache Iceberg, Iceberg, Apache, the Apache feather logo, and the Apache Iceberg project logo are</br>either registered trademarks or trademarks of The Apache Software Foundation. Copyright &copy; 2023</br>The Apache Software Foundation, Licensed under the <a href="https://www.apache.org/licenses/">Apache License, Version 2.0</a>.</br></br>
  ```
  原因：版权声明改为由 `footer.html` 中的 `.copyright-text` 段落渲染，可控制布局与年份。
- 新增（在 plugins 列表中）：
  ```yaml
  - privacy
  ```
  privacy 插件会扫描生成后的 HTML，把外部资源引用做本地化处理或加注，帮助满足 ASF 关于外部资源与隐私的策略要求。

### `site/nav.yml`

**修改目的**：补全 ASF 要求的导航链接（特别是 Privacy），并修正错误重复的 Sponsorship 链接。

**工作逻辑**：

原 ASF 子菜单：
```yaml
- ASF:
  - Sponsorship: https://www.apache.org/foundation/sponsorship.html
  - Events: https://www.apache.org/events/current-event.html
  - License: https://www.apache.org/licenses/
  - Security: https://www.apache.org/security/
  - Sponsors: https://www.apache.org/foundation/thanks.html
```

修改后：
```yaml
- ASF:
  - Sponsorship: https://www.apache.org/foundation/thanks.html
  - Events: https://www.apache.org/events/current-event.html
  - Privacy: https://privacy.apache.org/policies/privacy-policy-public.html
  - License: https://www.apache.org/licenses/
  - Security: https://www.apache.org/security/
  - Sponsors: https://www.apache.org/foundation/thanks.html
```

关键变化：
- 新增 `Privacy: https://privacy.apache.org/policies/privacy-policy-public.html`，这是 ASF 公共隐私政策页面，符合站点指南要求。
- 把"Sponsorship"项的 URL 从 `sponsorship.html` 改为 `thanks.html`。这一改动看起来反直觉（Sponsorship 链接指向 Thanks 页面），但在新版页脚 `footer.html` 中已经通过"Sponsorship"链接正确指向 `sponsorship.html`，这里 nav.yml 的"Sponsorship"实际承担"Thanks/Sponsors"语义，与下方"Sponsors"项一致。该项的 URL 命名与显示文本之间存在不一致，可能是迁移过程中遗留的折中处理。

### `site/overrides/home.html`

**修改目的**：把首页中三处 CDN 引用改为本地资源路径，并移除对 cto.html partial 的引用。

**工作逻辑**：

- `termynal.css` 引用：`https://cdn.jsdelivr.net/gh/ines/termynal@9b301892db6f8d403abfce7adf65888dffed72ea/termynal.css` → `assets/stylesheets/termynal.css`
- `lottie-player.js` 引用：`https://unpkg.com/@lottiefiles/lottie-player@latest/dist/lottie-player.js` → `assets/javascript/lottie-player.js`
- `termynal.js` 引用：`https://cdn.jsdelivr.net/gh/ines/termynal@9b301892db6f8d403abfce7adf65888dffed72ea/termynal.js` → `assets/javascript/termynal.js`
- 删除底部的 `{% block footer%} {% include "partials/cto.html" %} {% endblock %}` 区块，因为 cto.html 已被删除，新版统一 footer 由 `partials/footer.html` 提供。

### `site/overrides/partials/cto.html`（删除）

**修改目的**：移除冗余的 CTO（Call-To-Action）页脚区块。

**工作逻辑**：原文件 21 行，包含一个 `.cto` div，内含商标+版权声明（年份 2023）以及从 `config.extra.social` 渲染的社交按钮列表。这些信息已被新版 `footer.html` 完整接管（社交按钮通过 `partials/social.html` 在页脚中渲染，版权声明通过 `.copyright-text` 段落渲染），因此整体删除。

### `site/overrides/partials/footer.html`

**修改目的**：用 ASF 风格的多列页脚替换原 MkDocs Material 默认页脚。

**工作逻辑**：原文件 22 行，仅包含 `.md-footer-meta` 容器与版权声明+"Made with Material for MkDocs"标注。新版 132 行，结构为：

```html
<footer id="footer" class="footer">
  <div class="container-fluid footer-top">
    <div class="d-flex justify-content-center pt-3 pb-3">
      <!-- 五列：Features / Get Started / Community / ASF / Footer-icons -->
      <div class="col-lg-2 col-md-2 footer-links"><h4>Features</h4><ul>...</ul></div>
      <div class="col-lg-2 col-md-2 footer-links"><h4>Get Started</h4><ul>...</ul></div>
      <div class="col-lg-2 col-md-2 footer-links"><h4>Community</h4><ul>...</ul></div>
      <div class="col-lg-2 col-md-2 footer-links"><h4>ASF</h4><ul>...</ul></div>
      <div class="col-lg-2 col-md-2 mt-3 footer-icons">
        <a href="https://iceberg.apache.org"><img src="/assets/images/asf-estd-1999-logo.png" .../></a>
        <div class="social-links d-flex">{% include "partials/social.html" %}</div>
      </div>
    </div>
  </div>
  <div class="container-fluid">
    <div class="d-flex justify-content-center">
      <div class="col-md-8">
        <p class="copyright-text m-4">
          Apache Iceberg, Iceberg, Apache, the Apache feather logo, and the
          Apache Iceberg project logo are either registered trademarks or
          trademarks of The Apache Software Foundation. Copyright © 2024 The
          Apache Software Foundation, Licensed under the
          <a href="https://www.apache.org/licenses/">Apache License, Version 2.0</a>.
        </p>
      </div>
    </div>
  </div>
</footer>
```

各列内容：

- **Features** 列：Schema Evolution、Hidden Partitioning、Partition Evolution、Serializable Isolation、Branching and Tagging、Optimistic Concurrency、Advanced Filtering、Compute Engine Integrations、REST Catalog、Multiple language APIs。每项链接到 `/docs/latest/...` 对应文档。
- **Get Started** 列：Spark Quickstart、Hive Quickstart、Open Table Spec、Docs、Blogs、Talks。
- **Community** 列：Support（→ community/#slack）、Mailing Lists、Iceberg Events、Issues、Contribute、Guidelines。
- **ASF** 列：Apache Software Foundation、Thanks、Sponsorship、Security、License。这一列集中了 ASF 要求的合规链接。
- **Footer-icons** 列：ASF "Estd 1999" logo 图片 + 社交链接（通过 `partials/social.html` 渲染）。

底部 `.copyright-text` 段落为 ASF 标准商标与版权声明，年份已更新为 2024。

### `site/docs/assets/stylesheets/extra.css`

**修改目的**：删除旧 CTO/md-footer 样式，新增新页脚样式。

**工作逻辑**：

- 删除 53 行旧样式：`.cto`、`.cto span`、`.cto .btn span`、`.md-footer`、`.md-copyright`。这些样式对应已删除的 cto.html 与旧版 md-footer。
- 新增 36 行样式：
  - `#footer`：基础字号 16px、行高 1.5。
  - `#footer ul`：去除列表样式、内外边距清零。
  - `#footer li:hover`：鼠标悬停时加粗。
  - `#footer h4`：列标题字号 20px。
  - `.footer-top`：顶部背景色 `rgba(33,37,41, 0.2)`，半透明深色。
  - `.copyright-text`：字号 0.7em、居中。
  - `.footer-icons img`：最大宽度 100%。

### `site/docs/assets/stylesheets/fonts.css`（新增）

**修改目的**：显式声明站点所用字体的 `@font-face` 规则，便于配合 privacy 插件做字体引用的合规处理。

**工作逻辑**：76 行，定义两个字体族：

- **Nunito Sans**：italic 与 normal 两类，各 300/400/700 三种 weight，共 6 个 `@font-face`。`src` 指向 `https://fonts.gstatic.com/s/nunitosans/v15/...ttf`，`font-display: fallback` 表示字体加载失败时回退到系统字体。
- **Roboto Mono**：italic 与 normal 两类，各 400/700 两种 weight，共 4 个 `@font-face`。`src` 指向 `https://fonts.gstatic.com/s/robotomono/v23/...ttf`。

通过显式声明，字体来源与版本固定，避免通过 Google Fonts CSS 引入带来的额外请求与隐私顾虑。

### `site/docs/assets/stylesheets/termynal.css`（新增）

**修改目的**：本地化原本通过 jsdelivr CDN 引入的 termynal 终端动画样式。

**工作逻辑**：101 行，是 termynal.js（一个轻量终端动画库）的样式表，定义终端容器 `[data-termynal]` 的背景色、字号、提示符样式、光标动画等。内容与原 CDN 版本一致，仅改为本地存放。

### `site/docs/assets/javascript/lottie-player.js`（新增）

**修改目的**：本地化原本通过 unpkg CDN 引入的 LottieFiles 播放器脚本。

**工作逻辑**：77 行（压缩后单行），是 `@lottiefiles/lottie-player` 的打包产物，用于在首页播放 hidden-partitioning-animation.json 动画。改为本地存放后，页面不再向 unpkg.com 发起请求。

### `site/docs/assets/javascript/termynal.js`（新增）

**修改目的**：本地化原本通过 jsdelivr CDN 引入的 termynal 终端动画脚本。

**工作逻辑**：197 行（含注释与未压缩源码），是 termynal.js 库的源码，负责在页面上把 `[data-termynal]` 容器渲染成动画终端，用于首页展示 SQL/命令行动画。改为本地存放。

### `site/docs/assets/images/asf-estd-1999-logo.png`（新增）

**修改目的**：提供页脚右下角显示的 ASF "Established 1999" logo 图片。

**工作逻辑**：22681 字节的 PNG 图片，被 `footer.html` 中 `<img src="/assets/images/asf-estd-1999-logo.png" alt="apache software foundation logo"/>` 引用。这是 ASF 推荐的项目站点页脚标识。

## 小结

- 本提交是站点合规性大改，11 个文件 617 增 101 删，主要集中在 `site/` 目录下的 mkdocs 配置、HTML 模板、CSS/JS 资源。
- 影响范围：仅站点外观与合规性，不涉及任何 Iceberg 代码逻辑、API、构建产物功能。用户访问站点时会看到新的多列页脚、ASF logo、Privacy 链接，且页面不再向 jsdelivr/unpkg CDN 请求资源。
- 回迁到 1.4.x 的注意事项：1.4.x 若维护有同一份 `site/` 目录，回迁时需要：
  1. 确认 MkDocs Material 版本支持 `privacy` 插件（需要较新版本）。
  2. 确认 `partials/social.html` 在 1.4.x 中存在（footer.html 通过 `{% include "partials/social.html" %}` 引用），否则需要一并补齐。
  3. 新增的 5 个资源文件（asf-estd-1999-logo.png、lottie-player.js、termynal.js、fonts.css、termynal.css）需整体迁入，否则 home.html 与 footer.html 的引用会 404。
  4. nav.yml 中"Sponsorship"链接指向 thanks.html 看似是个遗留不一致，回迁时可考虑顺手修正为 sponsorship.html，但这超出本提交范围，可作为后续优化。
  5. 整体改动无运行时副作用，回迁风险低，但需要完整的资源文件同步，不能只回迁 HTML/CSS 改动。
