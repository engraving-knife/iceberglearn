# 提交 0475：Build: Bump mkdocs-material from 9.5.3 to 9.5.7 (#9638)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0475 |
| 完整哈希 | b89c395ca743a8778957a56befbbb4ef3d18083b |
| 短哈希 | b89c395ca |
| 日期 | 2024-02-06（Tue Feb 6 19:55:35 2024 +0100） |
| 作者 | dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com> |
| 说明 | Build: Bump mkdocs-material from 9.5.3 to 9.5.7 (#9638) |
| PR | #9638 |
| 依赖类型 | direct:production |
| 更新类型 | version-update:semver-patch（补丁版本升级） |

提交统计：1 个文件修改，1 行新增，1 行删除。

涉及文件：`site/requirements.txt`

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 文档站点所用的 MkDocs Material 主题从 `9.5.3` 升级到 `9.5.7`，跨 4 个补丁版本。MkDocs Material 是基于 MkDocs 的文档站点主题框架，Iceberg 官网（`site/` 目录）即以其为载体：`site/mkdocs.yml` 中 `theme.name: material` 指定使用该主题，并配合 `mkdocs-awesome-pages-plugin`、`mkdocs-macros-plugin`、`mkdocs-material-extensions`、`mkdocs-monorepo-plugin`、`mkdocs-redirects` 等插件构建多文档聚合站点。该依赖属于文档构建工具链，不进入 Iceberg 的 Java/Python 运行时产物，仅在 `site/Makefile` 的 `serve`/`build`/`deploy` 目标通过 `site/dev/*.sh` 脚本构建官网时使用。

升级 9.5.3 → 9.5.7 属补丁级别（`version-update:semver-patch`），按语义化版本约定为向后兼容更新，通常包含主题渲染缺陷修复、可访问性改进与小幅功能微调。值得一提的是，本提交与前一个文档修复提交 0472（Docs: Add newline to fix lists, #9664）同日合并——0472 修复的是“正文紧接列表缺空行导致列表不渲染”以及 admonition 缩进问题，这类问题在新版 MkDocs Material 对 Markdown 规范更严格的解析下更易暴露，因此两者在动机上存在关联：升级主题（更严格）与修补文档（更规范）相辅相成。跟进主题补丁升级可让官网获得上游修复，提升排版与可访问性稳定性；由于仅改动 `site/requirements.txt` 一行且为补丁级别，对 Iceberg 自身代码与公共 API 零影响，回迁 1.4.x 风险极低。

## 如何达成设计目的

实现路径是单点修改：在 `site/requirements.txt` 中把 `mkdocs-material==9.5.3` 改为 `mkdocs-material==9.5.7`，保持 `==` 精确版本锁定风格，与同文件其他依赖（`mkdocs-awesome-pages-plugin==2.9.2`、`mkdocs-macros-plugin==1.0.5`、`mkdocs-material-extensions==1.3`、`mkdocs-redirects==1.2.1` 等）一致。该 requirements 文件由 `site/dev/` 下的构建脚本在执行 `make serve`/`make build`/`make deploy` 时通过 `pip install -r requirements.txt` 安装，安装后 MkDocs 即以新版本的 material 主题渲染 `site/mkdocs.yml` 配置的文档站点。

## 修改详情

### `site/requirements.txt`

修改目的：把 MkDocs Material 主题的锁定版本从 `9.5.3` 提升到 `9.5.7`。

工作逻辑：

- 该文件位于 `site/` 目录，集中声明 Iceberg 官网构建所需的 Python 依赖。有效依赖共 6 行（其余为许可证头）：
  - `mkdocs-awesome-pages-plugin==2.9.2`（自动导航排序）
  - `mkdocs-macros-plugin==1.0.5`（模板宏，如版本号替换 `{{ icebergVersion }}`）
  - `mkdocs-material==9.5.7`（升级前 `9.5.3`，主题本体）
  - `mkdocs-material-extensions==1.3`（主题扩展，含 emoji 等）
  - `mkdocs-monorepo-plugin @ git+https://github.com/bitsondatadev/mkdocs-monorepo-plugin@url-fix`（多仓库文档聚合，以 git URL + ref 形式锁定）
  - `mkdocs-redirects==1.2.1`（URL 重定向）
- `site/mkdocs.yml` 第 24-28 行配置 `theme: name: material`，并设 `custom_dir: overrides` 做自定义覆盖；markdown 扩展中启用 `md_in_html`、`tables`、`toc` 以及 material 的 emoji 扩展（`material.extensions.emoji.twemoji` / `to_svg`），这些能力均由 `mkdocs-material` 与 `mkdocs-material-extensions` 提供。
- `site/Makefile` 暴露 `serve`/`build`/`deploy`/`clean` 四个目标，分别委托给 `site/dev/serve.sh`、`build.sh`、`deploy.sh`、`clean.sh`，这些脚本在执行前会安装 `requirements.txt`，因此本次版本提升后下次构建官网即采用 9.5.7 版主题。
- 由于 Material 主题在 9.5.x 补丁系列内保持兼容，主题自定义（`overrides/` 目录、`mkdocs.yml` 配置）无需任何调整；如新版对个别 Markdown 渲染规则收紧，可能需要配合 0472 那类文档格式修复，但本提交本身不改动任何文档内容。

## 小结

本提交是 Dependabot 触发的文档构建工具链补丁升级：将 `site/requirements.txt` 中 `mkdocs-material` 由 `9.5.3` 升至 `9.5.7`。该主题是 Iceberg 官网（`site/`）的渲染基础，由 `site/mkdocs.yml` 的 `theme.name: material` 启用，并通过 `site/Makefile` 的 `serve`/`build`/`deploy` 目标在构建时安装使用，不进入运行时发布产物。升级属补丁级别、向后兼容，不涉及 Iceberg 自身代码与 API 变更，主要用于跟进主题上游的渲染与可访问性修复；与同日合并的 0472（列表格式修复）在动机上相互呼应。回迁 1.4.x 风险极低。
