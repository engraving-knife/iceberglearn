# 提交 1099：Build: Bump mkdocs-material from 9.5.31 to 9.5.33 (#11002)

## 提交信息

- **序号**：1099 / 4088
- **哈希**：244eb1e8d41078ea8f9d6ed7146dcbd6ab06b8e6
- **短哈希**：244eb1e8d
- **日期**：2024-08-26 10:47:57 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.5.31 to 9.5.33 (#11002)
- **PR/Issue**：#11002

## 总体目的

本提交由 Dependabot 自动生成，将 MkDocs Material 主题从 9.5.31 升级到 9.5.33。MkDocs Material 是 Iceberg 官方文档站点（`site/`）使用的 MkDocs 主题，用于生成项目文档网站。这是一次 patch 级别升级（9.5.x 系列），通常包含 bug 修复与小改进，不引入破坏性变更。

本次升级覆盖 9.5.32 到 9.5.33 共 2 个 patch 版本，目的是让文档站点使用最新的主题版本，获得最新的 UI 修复与功能改进。

## 如何达成设计目的

修改 `site/requirements.txt` 中的 `mkdocs-material` 版本锁定，从 `9.5.31` 改为 `9.5.33`。该文件是 Python 项目的依赖清单（pip requirements），用于在构建文档站点时安装指定版本的 MkDocs Material 主题。

## 修改详情

### `site/requirements.txt`

**修改目的**：升级文档站点的 MkDocs Material 主题版本。

**工作逻辑**：将 `mkdocs-material==9.5.31` 改为 `mkdocs-material==9.5.33`。使用 `==` 精确锁定版本，确保文档构建环境可复现。该文件还锁定其他 MkDocs 插件版本（awesome-pages-plugin、macros-plugin、material-extensions、monorepo-plugin、redirects 等）。

## 小结

- **成效**：将文档站点的 MkDocs Material 主题从 9.5.31 升级到 9.5.33，获得最新的主题修复与改进。
- **影响范围**：仅修改 `site/requirements.txt` 一行，影响文档站点的构建环境，不影响 Iceberg 运行时代码。
- **回迁到 1.4.x 的注意事项**：属于文档构建工具的 patch 升级，**可安全回迁到 1.4.x**（若 1.4.x 维护文档站点）。风险极低，MkDocs Material 9.5.x 系列内升级不涉及破坏性变更。若 1.4.x 不单独维护文档站点，则无需回迁。
