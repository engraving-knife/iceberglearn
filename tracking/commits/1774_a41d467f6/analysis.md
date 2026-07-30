# 提交 1774：Build: Bump mkdocs-material from 9.6.4 to 9.6.5 (#12386)

## 提交信息

- **序号**：1774 / 4088
- **哈希**：a41d467f6f7253a7ef2fe62fa324acf25f1709dd
- **短哈希**：a41d467f6
- **日期**：2025-02-24 09:05:24 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.4 to 9.6.5 (#12386)
- **PR/Issue**：#12386

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将文档站点构建工具 mkdocs-material 从 9.6.4 版本升级到 9.6.5 版本。mkdocs-material 是 Iceberg 项目文档站点（site 目录）使用的 Material 主题，用于生成项目文档网站。此次升级为补丁版本升级（semver-patch），属于常规的依赖维护，确保文档站点使用最新补丁版本，获取 bug 修复和小改进。

## 如何达成设计目的

提交通过更新 `site/requirements.txt` 文件中 mkdocs-material 的版本号来完成升级。这是 Python pip 依赖管理方式，Dependabot 检测到新版本后自动创建 PR 修改版本号。

## 修改详情

### `site/requirements.txt`（修改, +1/-1 lines）

**修改目的**：升级 mkdocs-material 版本。

**工作逻辑**：将 `mkdocs-material==9.6.4` 修改为 `mkdocs-material==9.6.5`。

## 小结

- **成效**：将文档站点主题 mkdocs-material 升级到 9.6.5 补丁版本。
- **影响范围**：仅影响文档站点构建，不影响 Iceberg 核心功能代码。
- **回迁到 1.4.x 的注意事项**：低优先级回迁。文档构建依赖升级对核心功能无影响，可根据 1.4.x 分支的文档维护策略决定是否回迁。无前置依赖。
