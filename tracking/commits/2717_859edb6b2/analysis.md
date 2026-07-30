# 提交 2717：Build: Bump mkdocs-material from 9.6.20 to 9.6.21

## 提交信息

- **序号**：2717 / 4088
- **哈希**：859edb6b2090ce56fd278c2dd9eb34fbe8f3beaf
- **短哈希**：859edb6b2
- **日期**：2025-10-04 23:12:02 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.20 to 9.6.21
- **PR/Issue**：#14255

## 总体目的

此提交是 Dependabot 自动生成的依赖版本升级，将 MkDocs Material 主题从 9.6.20 升级到 9.6.21。

MkDocs Material 是 Iceberg 文档网站（`iceberg.apache.org`）使用的主题框架。它基于 MkDocs 静态网站生成器，提供了丰富的文档主题功能。文档网站的构建依赖在 `site/requirements.txt` 中声明。

9.6.20 到 9.6.21 是一个 semver-patch 版本升级，通常只包含 bug 修复，不引入新功能或破坏性变更。

## 如何达成设计目的

通过在 `site/requirements.txt` 中将 `mkdocs-material` 的版本从 `9.6.20` 更新为 `9.6.21` 来完成升级。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 MkDocs Material 文档主题版本。

**工作逻辑**：将 `mkdocs-material==9.6.20` 更改为 `mkdocs-material==9.6.21`。使用 `==` 精确版本锁定，确保文档构建使用指定版本。这是 patch 级别升级，预计只包含 bug 修复和小的改进，不影响文档内容或构建流程。

## 总结

此提交是文档网站依赖的例行维护升级，将 MkDocs Material 从 9.6.20 升级到 9.6.21。作为 patch 级别升级，预计只包含 bug 修复，对文档网站的影响最小。
