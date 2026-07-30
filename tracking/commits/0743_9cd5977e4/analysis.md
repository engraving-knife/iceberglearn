# 提交 0743：Build: Bump mkdocs-material from 9.5.19 to 9.5.21 (#10272)

## 提交信息

- **序号**：0743 / 4088
- **哈希**：9cd5977e4e63e7a3b54afd785e58fd678f0aa7f0
- **短哈希**：9cd5977e4
- **日期**：2024-05-05 11:46:16 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.5.19 to 9.5.21 (#10272)
- **PR/Issue**：#10272

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 文档站点构建依赖的 `mkdocs-material`（Material for MkDocs 主题）从 `9.5.19` 升级到 `9.5.21`，属于 semver-patch（补丁版本号）级别的依赖更新。

`mkdocs-material` 是 MkDocs 最流行的主题，提供现代化的文档站点外观与交互。Iceberg 的文档站点（`site/` 目录）使用 MkDocs 配合 Material 主题构建。补丁版本升级通常包含 bug 修复、小的 UI 改进与兼容性修复，不引入破坏性变更。Dependabot 在 PR 描述中提供了 [release notes](https://github.com/squidfunk/mkdocs-material/releases)、[changelog](https://github.com/squidfunk/mkdocs-material/blob/master/CHANGELOG) 与 [commits 对比](https://github.com/squidfunk/mkdocs-material/compare/9.5.19...9.5.21) 供维护者审阅。

值得注意的是，与 Gradle 版本目录管理的 Java 依赖不同，`mkdocs-material` 是 Python 包依赖，通过 `site/requirements.txt` 声明，使用 `==` 精确版本锁定。

## 如何达成设计目的

改动只涉及一处版本声明：在文档站点的 Python 依赖文件 `site/requirements.txt` 中，将 `mkdocs-material==9.5.19` 改为 `mkdocs-material==9.5.21`。文档站点构建时 pip 会按此文件安装指定版本。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 `mkdocs-material` 的版本从 9.5.19 升级到 9.5.21。

**工作逻辑**：`site/requirements.txt` 是文档站点构建的 Python 依赖清单，使用 pip 标准的 `==` 精确版本锁定语法。改动位于文件中部，原行 `mkdocs-material==9.5.19` 被改为 `mkdocs-material==9.5.21`。该版本控制文档站点构建时使用的 Material 主题版本，升级后文档站点会使用新版本主题渲染。

## 小结

- **成效**：将 mkdocs-material 主题从 9.5.19 升级到 9.5.21，获取补丁版本的 bug 修复与改进。
- **影响范围**：仅文档站点构建依赖版本，不涉及任何源代码或 Java 构建变更。影响范围限于文档站点的渲染外观。
- **回迁注意事项**：无技术风险。补丁版本升级向后兼容，回迁到 1.4.x 只需同步 `site/requirements.txt` 中的版本行即可。这是一个 Python 依赖（非 Gradle 依赖），与 1.4.x 的 Java 构建无关。
