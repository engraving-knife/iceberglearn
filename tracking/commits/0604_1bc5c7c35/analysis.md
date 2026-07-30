# 提交 0604：Build: Bump mkdocs-material from 9.5.9 to 9.5.14

## 提交信息

- **序号**：0604 / 4088
- **哈希**：1bc5c7c35d5e921dfc08ab4ce4cfd0404d7a24b8
- **短哈希**：1bc5c7c35
- **日期**：2024-03-18（Mon Mar 18 08:41:20 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.9 to 9.5.14 (#9983)

  完整提交说明（节选）：
  > Bumps [mkdocs-material](https://github.com/squidfunk/mkdocs-material) from 9.5.9 to 9.5.14.
  > update-type: version-update:semver-patch

- **PR/Issue**：#9983（Dependabot 自动 PR）

## 总体目的

本提交由 Dependabot 自动发起，把 Python 文档主题包 `mkdocs-material` 从 `9.5.9` 升级到 `9.5.14`，属于补丁版本（semver-patch）依赖跟进。共跨越 5 个补丁版本（9.5.10、9.5.11、9.5.12、9.5.13、9.5.14）。

`mkdocs-material` 是 MkDocs 静态站点生成器最流行的主题包，由 squidfunk 维护，提供现代化的 Material Design 风格界面。在 Iceberg 项目中，它被用于构建官方文档站点 https://iceberg.apache.org/ 。源码位于 `site/` 子目录，是一个独立的 MkDocs 项目，包含站点级（非版本化）的页面（如 about、view-spec、首页等），并通过 `mkdocs-monorepo-plugin` 与 `/docs/` 目录下的版本化文档子站编织成完整的 Iceberg 站点。

按 Dependabot 的分类，这次属于 `version-update:semver-patch`（补丁版本升级），是 0601 提交配置生效后仍会正常发起的 PR 类型，符合依赖治理策略。

## 如何达成设计目的

`site/requirements.txt` 是 Python pip 风格的依赖锁定文件（使用 `==` 精确版本固定），列出构建 Iceberg 文档站点所需的全部 MkDocs 插件与主题包：

```
mkdocs-awesome-pages-plugin==2.9.2          # 自动生成导航结构
mkdocs-macros-plugin==1.0.5                  # 模板宏支持
mkdocs-material==9.5.9                       # Material 主题（本提交升级对象）
mkdocs-material-extensions==1.3.1            # Material 主题扩展（图标、emoji 等）
mkdocs-monorepo-plugin @ git+https://...     # 单仓多 MkDocs 项目编排
mkdocs-redirects==1.2.1                       # URL 重定向插件
```

Dependabot 识别出 `mkdocs-material` 有新版 `9.5.14`，遂把对应行从 `==9.5.9` 改为 `==9.5.14`，单行修改即完成升级。

升级的具体执行路径：
1. Dependabot 周期性扫描 PyPI，发现 `mkdocs-material` 最新版本为 `9.5.14`。
2. 生成单行 PR，触发 CI 验证。
3. 由于 `site/` 的构建是手动触发（`make build` / `make deploy`，无 GitHub Actions workflow 守护），CI 主要验证仓库其他模块不受影响。站点构建结果需在合并后由维护者手动 `make build` 验证。

## 修改详情

### `site/requirements.txt`

**修改目的**：把 `mkdocs-material` 的固定版本从 `9.5.9` 升到 `9.5.14`。

**工作逻辑**：

文件第 20 行从：
```
mkdocs-material==9.5.9
```
改为：
```
mkdocs-material==9.5.14
```

**消费链路**：

`site/mkdocs.yml` 中配置了 Material 主题及其功能特性：
```yaml
theme:
  custom_dir: overrides
  static_templates:
    - home.html
  name: material
  language: en
  logo: assets/images/Iceberg-logo.svg
  favicon: assets/images/favicon-96x96.png
  font:
    text: Nunito Sans
  palette:
    scheme: iceberg
  features:
    - navigation.tabs
    - navigation.tabs.sticky
    - navigation.path
    - navigation.top
    - navigation.tracking
    - toc.follow
    - offline
    - search.suggest
    - search.highlight
    - content.tabs.link
    - content.code.copy
    - content.code.annotate
```

`mkdocs-material` 提供的核心能力包括：
- **主题渲染**：基于 Material Design 的 HTML/CSS/JS 资源，包括自定义调色板 `scheme: iceberg`（在 `overrides/` 中定义）。
- **导航特性**：`navigation.tabs`（顶部标签页导航）、`navigation.tabs.sticky`（滚动时保持粘性）、`navigation.path`（面包屑路径）、`navigation.tracking`（URL 跟踪当前章节）、`toc.follow`（侧边栏目录跟踪）。
- **搜索特性**：`search.suggest`（搜索建议）、`search.highlight`（搜索结果高亮）。
- **内容特性**：`content.tabs.link`（标签页同步）、`content.code.copy`（代码块一键复制）、`content.code.annotate`（代码注释）。
- **离线支持**：`offline` 特性生成可离线浏览的站点。

**构建与部署流程**：

`site/Makefile` 提供四个目标：
- `make serve`：本地清理、构建并用开发服务器预览（调用 `dev/serve.sh`）
- `make build`：清理并构建静态站点（调用 `dev/build.sh` → `mkdocs build`）
- `make deploy`：清理、构建并部署到 GitHub 的 `asf-site` 分支（调用 `dev/deploy.sh` → `mkdocs gh-deploy --no-history --remote-branch=asf-site`）
- `make clean`：清理本地构建产物

部署流程通过 `mkdocs gh-deploy` 把构建好的静态站点推送到 `asf-site` 分支，Apache 基础设施会从该分支发布到 https://iceberg.apache.org/ 。整个过程是手动触发的，没有 PR 级 CI 守护站点构建结果，因此 `mkdocs-material` 升级后需要维护者本地 `make build` 验证渲染正常再合并。

**版本跨度说明**：

`9.5.9 → 9.5.14` 跨越 5 个补丁版本。`mkdocs-material` 的 9.5.x 是 9.x 主版本下的稳定维护线，补丁版本通常包含 bug 修复、小的样式调整、依赖更新，不引入破坏性变更。9.5.x 系列的主题 API 与功能特性集稳定，因此升级风险很低。

## 小结

本提交是一个由 Dependabot 自动生成的单行 Python 文档主题依赖升级，把 `mkdocs-material` 从 `9.5.9` 升到 `9.5.14`。改动极小（1 行），仅影响 Iceberg 文档站点的构建工具链。

- **影响范围**：仅 `site/` 子目录的文档构建与渲染。运行时（Java/Scala 代码）无影响。文档站点的视觉效果可能因主题补丁而有细微调整（如样式微调、bug 修复带来的渲染差异），但不影响内容。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支的 `site/requirements.txt` 可能仍使用更旧的 `mkdocs-material` 版本。可直接 cherry-pick，单行修改冲突风险极低。
  - 由于站点构建无 PR 级 CI 守护，回迁后建议维护者本地执行 `cd site && make install && make build` 验证站点能正常构建且渲染符合预期。
  - 注意 `mkdocs-material` 与 `mkdocs-material-extensions` 的版本兼容性。当前 `mkdocs-material-extensions==1.3.1` 与 `mkdocs-material 9.5.x` 兼容，回迁时无需同步升级。
  - 1.4.x 是已发布版本线，若 1.4.x 的 `site/` 目录与 main 有较大差异（如 mkdocs.yml 配置不同），需确认新主题版本仍支持所用的 features 与 palette 配置。
