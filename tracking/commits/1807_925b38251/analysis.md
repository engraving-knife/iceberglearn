# 提交 1807：Build: Bump mkdocs-material from 9.6.5 to 9.6.6 (#12432)

## 提交信息

- **序号**：1807 / 4088
- **哈希**：925b38251c25930dd900d36995ec2f0d18fef70a
- **短哈希**：925b38251
- **日期**：2025-03-02 16:39:28 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.5 to 9.6.6 (#12432)
- **PR/Issue**：#12432

## 总体目的

Iceberg 官网使用 MkDocs Material 主题构建文档站点，依赖固定在 `site/requirements.txt` 中。mkdocs-material 从 9.6.5 发布了 9.6.6 补丁版本，包含 bug 修复和改进。本提交由 Dependabot 自动创建，将依赖版本从 9.6.5 升级到 9.6.6，以跟进上游修复。

这是标准的依赖维护操作，确保文档站点使用最新补丁版本，避免已知问题。

## 如何达成设计目的

通过修改 `site/requirements.txt` 中 `mkdocs-material` 的版本固定，从 `mkdocs-material==9.6.5` 改为 `mkdocs-material==9.6.6`。这是一个 patch 级别升级，向下兼容。

## 修改详情

### `site/requirements.txt`（修改, ±1 lines）

**修改目的**：升级 mkdocs-material 到 9.6.6。

**工作逻辑**：将 `mkdocs-material==9.6.5` 改为 `mkdocs-material==9.6.6`。该文件列出了文档站点构建所需的 Python 依赖，CI 在构建文档时会 `pip install -r site/requirements.txt`，升级后文档站点将使用 9.6.6 版本的主题。

## 小结

- **成效**：将文档站点主题 mkdocs-material 升级到 9.6.6，跟进上游补丁修复。
- **影响范围**：仅影响文档构建依赖 `site/requirements.txt`，不影响代码和文档内容。
- **回迁到 1.4.x 的注意事项**：纯依赖升级，无风险，无前置依赖。patch 版本升级向下兼容，可直接回迁。但 1.4.x 分支的文档构建依赖版本可能已有自己的维护节奏，回迁前可确认 1.4.x 是否也需要此修复。
