# 提交 1703：Build: Bump mkdocs-material from 9.6.1 to 9.6.3 (#12205)

## 提交信息

- **序号**：1703 / 4088
- **哈希**：dda964e7c1e40866fdf1306c1a86ebd14179be55
- **短哈希**：dda964e7c
- **日期**：2025-02-09（Sun Feb 9 08:37:48 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.6.1 to 9.6.3 (#12205)
- **PR/Issue**：#12205

## 总体目的

Dependabot 自动升级提交。`mkdocs-material` 是 Iceberg 网站使用的 MkDocs Material 主题。本提交把该 Python 依赖从 `9.6.1` 升级到 `9.6.3`（patch 级），获取主题的 bug 修复与改进。

## 如何达成设计目的

在 `site/requirements.txt` 中把 `mkdocs-material==9.6.1` 改为 `mkdocs-material==9.6.3`。

## 修改详情

### `site/requirements.txt`（修改，+1/-1 行）

**修改目的**：升级 mkdocs-material 主题版本。

**工作逻辑**：修改 Python 依赖锁定版本号，网站构建时 pip install 会使用新版本。

## 小结

- **成效**：升级网站文档主题到 9.6.3，获取 bug 修复。
- **影响范围**：仅网站构建依赖，无源代码变更。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯文档构建工具升级。直接修改 requirements.txt 即可。
