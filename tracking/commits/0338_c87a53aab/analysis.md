# 提交 0338：Build: Bump mkdocs-monorepo-plugin from 1.0.5 to 1.1.0 (#9430)

## 提交信息

- **序号**：0338
- **哈希**：c87a53aab9222c4345955854795dc40b5c884bd4
- **短哈希**：c87a53aab
- **日期**：2024-01-08 04:19:08 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-monorepo-plugin from 1.0.5 to 1.1.0 (#9430)
- **PR/Issue**：#9430

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 文档站点构建依赖的 `mkdocs-monorepo-plugin` 从 `1.0.5` 升级到 `1.1.0`，属于 `version-update:semver-minor` 级别的依赖更新。

`mkdocs-monorepo-plugin` 是 Backstage 团队维护的 MkDocs 插件，用于在 monorepo 结构下把多个子目录中的 MkDocs 站点合并为一个统一的文档站点——通过 `!include` 语法在 `mkdocs.yml` 中引用子项目的 `mkdocs.yml`，让大型多团队项目（如 Backstage 自身、Iceberg 这种多模块项目）可以把文档与代码同仓管理、各自独立维护 mkdocs 配置，同时输出单一可浏览的文档站点。Iceberg 的 `site/` 目录就是用 MkDocs + Material 主题构建的官方文档站，依赖该 monorepo 插件处理多语言/多版本/多子模块的文档聚合。

`mkdocs-monorepo-plugin` 1.1.0 是该插件 1.x 系列的一个 minor 版本升级。1.1.x 系列主要带来对 MkDocs 1.5+ 的更好兼容性、修复 `!include` 在 Windows 路径下的若干 bug、改进子项目解析的健壮性，以及对 Python 3.12 的支持验证。由于是 minor 版本升级，按 semver 不包含破坏性 API 变更，Dependabot 把它归为 `version-update:semver-minor`，风险较低。Iceberg 维护者合并此 PR 即表示认可升级在 Iceberg 文档构建流程中的兼容性，让文档站构建工具链跟进上游最新稳定版本，避免长期停留在旧版本导致后续升级跨度变大或与新版 MkDocs 不兼容。

## 如何达成设计目的

改动只涉及一处版本常量：在文档站点的 Python 依赖清单 `site/requirements.txt` 中，将 `mkdocs-monorepo-plugin==1.0.5` 改为 `mkdocs-monorepo-plugin==1.1.0`。该 `requirements.txt` 是 Iceberg 文档站点构建（通常通过 CI 流水线执行 `pip install -r site/requirements.txt` 后再 `mkdocs build`）的依赖清单，单独于 Java/Gradle 主构建。升级后下次文档构建会自动拉取 1.1.0 版本，无需修改 `mkdocs.yml` 配置——monorepo 插件的 `!include` 语法在 1.0→1.1 间保持兼容。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 `mkdocs-monorepo-plugin` 的版本从 1.0.5 升级到 1.1.0。

**工作逻辑**：`site/requirements.txt` 是 Iceberg 文档站点构建的 Python 依赖清单（pip 格式），用 `==` 精确锁版本。文件以 Apache 许可证头开头，随后列出 MkDocs 生态各依赖的精确版本（`mkdocs-awesome-pages-plugin`、`mkdocs-macros-plugin`、`mkdocs-material`、`mkdocs-material-extensions`、`mkdocs-monorepo-plugin`、`mkdocs-redirects` 等）。改动位于文件末尾的字母序区域（`mkdocs-material-extensions==1.3` 与 `mkdocs-redirects==1.2.1` 之间），原行 `mkdocs-monorepo-plugin==1.0.5` 被改为 `mkdocs-monorepo-plugin==1.1.0`。`pip install -r site/requirements.txt` 在下次文档构建时解析到 1.1.0 版本，monorepo 插件以更新后的实现处理 `mkdocs.yml` 中的 `!include` 指令（聚合子项目文档），输出统一的 Iceberg 文档站点。该升级仅影响文档构建产物，不影响 Iceberg Java/Scala 主体代码与运行时行为。

## 小结

该提交由 Dependabot 自动将 `mkdocs-monorepo-plugin` 从 1.0.5 升级到 1.1.0，使 Iceberg 文档站点构建工具链跟进上游最新稳定版本，获取 1.1.x 系列对 MkDocs 1.5+ 与 Python 3.12 的兼容性改进、Windows 路径 bug 修复以及子项目解析健壮性提升。改动仅一处版本常量，依赖 `pip install -r site/requirements.txt` 机制自动传递到文档构建流程，属于低风险、低成本的文档构建依赖维护工作。
