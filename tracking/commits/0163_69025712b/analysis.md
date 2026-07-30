# 提交 0163：Build: Bump mkdocs-material from 9.1.21 to 9.4.8 (#9055)

## 提交信息

- **序号**：0163 / 4088
- **哈希**：69025712b41e744304e2a7d971ccdad784e25bec
- **短哈希**：69025712b
- **日期**：2023-11-14 09:47:44 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.1.21 to 9.4.8 (#9055)
- **PR/Issue**：#9055

## 总体目的

这个提交是 Dependabot 自动生成的依赖升级，将 Iceberg 文档站点构建主题 `mkdocs-material` 从 9.1.21 升级到 9.4.8。

`mkdocs-material` 是 MkDocs 生态中最流行的文档主题，提供现代化外观、搜索、版本化文档、暗色模式、代码高亮、内容卡片等丰富功能。Iceberg 项目使用 MkDocs + mkdocs-material 构建官方文档站点（`site/` 目录），主题版本直接影响文档站点的视觉呈现、可访问性与构建产物质量。

本次升级属于 semver-minor 级别（Dependabot 元数据标注 `update-type: version-update:semver-minor`），从 9.1.21 跨越 3 个次要版本到 9.4.8。9.2~9.4 系列主题在上游持续引入了多项改进，包括但不限于：搜索索引改进、性能优化、对 MkDocs 1.5+ 的更好兼容、新版内容标签页与告示块（ admonitions ）、暗色主题与配色调整、以及对 Python 与构建工具链兼容性的修复。升级动机在于：跟随主题上游演进、获取 bug 修复与新特性、避免版本过旧与生态其它插件（如已升级到 1.3 的 `mkdocs-material-extensions`，见提交 0162）出现兼容性问题。

由于本次升级跨度较大（9.1 → 9.4），它实质上是文档构建链路一次较有意义的更新。配合提交 0162 先行升级的扩展包，二者构成一组协同的文档站点依赖升级。对 Iceberg 演进的意义在于：保障官方文档站点的现代化呈现与持续可维护性，让读者获得更优的文档浏览体验，同时降低未来与 MkDocs 核心或其它插件升级时的兼容性摩擦。

## 如何达成设计目的

设计思路是单行依赖版本号修改：Dependabot 仅修改 `site/requirements.txt` 中 `mkdocs-material` 的锁定版本，使其在下次构建文档站点时拉取 9.4.8。改动结构上只涉及一个文件的一行，与紧邻的提交 0162（扩展包升级）形成配套。

## 修改详情

### `site/requirements.txt`

**修改目的**：将文档站点主题 `mkdocs-material` 从 9.1.21 升级到 9.4.8，跟随主题上游 3 个次要版本的演进。

**工作逻辑**：该文件维护 Iceberg 文档站点的 Python 依赖。本次改动将 `mkdocs-material==9.1.21` 改为 `mkdocs-material==9.4.8`。文件中其它依赖保持不变：`mkdocs-awesome-pages-plugin==2.9.2`、`mkdocs-macros-plugin==1.0.5`、`mkdocs-material-extensions==1.3`（已由提交 0162 升级到位，满足新版主题对扩展包的最低版本要求）、`mkdocs-monorepo-plugin==1.0.5`、`mkdocs-redirects==1.2.1`。依赖顺序也体现了扩展包与主题主包的协同升级节奏。

## 小结

本次提交通过一行依赖版本号升级，将 Iceberg 文档站点的 mkdocs-material 主题从 9.1.21 推进到 9.4.8，属于文档构建链路一次跨度较大的维护性更新，配合扩展包升级共同保障文档站点的现代化呈现与兼容性。
