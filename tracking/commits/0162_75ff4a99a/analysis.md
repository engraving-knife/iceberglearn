# 提交 0162：Build: Bump mkdocs-material-extensions from 1.1.1 to 1.3 (#9052)

## 提交信息

- **序号**：0162 / 4088
- **哈希**：75ff4a99a9f72bf8735f6062d671ed088380e94e
- **短哈希**：75ff4a99a
- **日期**：2023-11-14 08:09:51 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material-extensions from 1.1.1 to 1.3 (#9052)
- **PR/Issue**：#9052

## 总体目的

这个提交是 Dependabot 自动生成的依赖升级，将 Iceberg 文档站点构建依赖 `mkdocs-material-extensions` 从 1.1.1 升级到 1.3。

`mkdocs-material-extensions` 是 `mkdocs-material` 主题的一个配套扩展包，提供一些主题所需的附加功能（如 RoLinks 等自定义 Markdown 扩展）。在 Iceberg 项目中，`site/` 目录用于构建官方文档站点，使用 MkDocs Material 主题呈现文档。该扩展是主题生态的组成部分，与 `mkdocs-material` 主包协同工作。

本次升级属于 semver-minor 级别的版本更新（Dependabot 元数据标注 `update-type: version-update:semver-minor`），从 1.1.1 跨越到 1.3（上游未发布 1.2，直接进入 1.3）。动机主要是跟随上游扩展包的演进，获取 bug 修复与新特性，同时为后续升级 `mkdocs-material` 主包（见提交 0163）做铺垫——`mkdocs-material` 较新版本往往对扩展包有最低版本要求。

对 Iceberg 演进的意义在于：保持文档站点构建依赖的同步与健康，避免扩展包版本过旧成为后续主题升级的阻塞项，从而保障官方文档站点的可维护性与持续构建能力。

## 如何达成设计目的

设计思路同样是单行依赖版本号修改：Dependabot 仅修改 `site/requirements.txt` 中 `mkdocs-material-extensions` 的锁定版本，使其在下次构建文档站点时拉取新版本。改动结构上只涉及一个文件的一行。

## 修改详情

### `site/requirements.txt`

**修改目的**：将文档站点构建依赖 `mkdocs-material-extensions` 从 1.1.1 升级到 1.3，跟随上游扩展包演进。

**工作逻辑**：该文件维护 Iceberg 文档站点（基于 MkDocs Material 主题）的 Python 依赖。本次改动将 `mkdocs-material-extensions==1.1.1` 改为 `mkdocs-material-extensions==1.3`。文件中其它依赖（`mkdocs-material==9.1.21`、`mkdocs-awesome-pages-plugin==2.9.2`、`mkdocs-macros-plugin==1.0.5`、`mkdocs-monorepo-plugin==1.0.5`、`mkdocs-redirects==1.2.1` 等）保持不变。值得注意的是，本提交与紧随其后的提交 0163（升级 `mkdocs-material` 到 9.4.8）构成一组协同升级：扩展包先升级到 1.3，为主题主包升级扫清前置依赖约束。

## 小结

本次提交通过一行依赖版本号升级，将 Iceberg 文档站点的 mkdocs-material 扩展包推进到 1.3，属于文档构建链路的维护性更新，并为后续 mkdocs-material 主包升级做前置准备。
