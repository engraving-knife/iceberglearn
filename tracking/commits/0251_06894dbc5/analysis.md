# 提交 0251：Build: Bump mkdocs-material from 9.4.12 to 9.5.1 (#9256)

## 提交信息

- **序号**：0251 / 4088
- **哈希**：06894dbc5407b59e07aae9c56ebd904bb5388cf5
- **短哈希**：06894dbc5
- **日期**：2023-12-10 11:04:13 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.4.12 to 9.5.1 (#9256)
- **PR/Issue**：#9256

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，用于将 Iceberg 文档站点使用的 mkdocs-material 主题从 9.4.12 升级到 9.5.1（一次 semver-minor 升级）。

mkdocs-material 是 Iceberg 项目 `site/` 目录下文档站点所使用的 MkDocs 主题。Iceberg 官方文档站点基于 MkDocs 构建，并配合 mkdocs-awesome-pages-plugin、mkdocs-macros-plugin、mkdocs-monorepo-plugin、mkdocs-redirects 等多个插件来组织多语言/多版本的文档导航。保持主题库为最新版本能够获得最新的视觉特性、bug 修复与安全性更新，避免文档构建链路因旧版本依赖而出现问题。

这类升级属于项目维护的常规动作：Dependabot 会周期性扫描 `site/requirements.txt` 这类锁定依赖的文件，发现上游有新版本即发起 PR，由维护者评审合入。升级本身不改变 Iceberg 的运行时（Java/Spark/Flink 等）行为，只影响文档站点的构建产物。

## 如何达成设计目的

设计思路很直接：仅修改文档站点的 Python 依赖清单文件 `site/requirements.txt`，把 mkdocs-material 的版本号从 `9.4.12` 改为 `9.5.1`，其余依赖保持不变。改动规模为 1 个文件、1 行。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 mkdocs-material 主题锁定版本从 9.4.12 提升至 9.5.1。

文件中其余依赖（如 mkdocs-awesome-pages-plugin==2.9.2、mkdocs-macros-plugin==1.0.5、mkdocs-material-extensions==1.3、mkdocs-monorepo-plugin==1.0.5、mkdocs-redirects==1.2.1 等）保持不变，仅这一行被替换。mkdocs-material 9.5.x 是一个 minor 版本升级，根据上游 changelog 通常带来新的主题配置项、内置插件增强以及对较新 MkDocs / Python 版本的兼容性改进，对 Iceberg 文档站点的整体外观与可维护性起到正向作用。

## 小结

该提交通过升级文档站点主题依赖 mkdocs-material 至 9.5.1，使 Iceberg 官方文档站点保持在最新的主题版本上，属于常规依赖维护。
