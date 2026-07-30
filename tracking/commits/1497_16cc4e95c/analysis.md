# 提交 1497：Build: Bump mkdocs-material from 9.5.47 to 9.5.48 (#11790)

## 提交信息

- **序号**：1497 / 4088
- **哈希**：16cc4e95c8260b91b22d29acf808eae9435ba19c
- **短哈希**：16cc4e95c
- **日期**：2024-12-16（Mon Dec 16 15:49:36 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.47 to 9.5.48 (#11790)
- **PR/Issue**：#11790

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 官网文档站点（`site/`）构建所用的 MkDocs Material 主题从 `9.5.47` 升级到 `9.5.48`（一个补丁版本）。MkDocs Material 是基于 MkDocs 的静态文档站点主题，Iceberg 用它来生成 https://iceberg.apache.org 文档站点。

升级补丁版本的目的通常是获得主题的小修复、安全补丁或微调（如样式、搜索、可访问性等方面），保持文档站点构建工具链不落后。

## 如何达成设计目的

修改 `site/requirements.txt` 中 `mkdocs-material` 的固定版本号，由 `9.5.47` 改为 `9.5.48`。该文件是构建文档站点时的 Python 依赖清单（pip 安装），仅版本号一处变更。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 mkdocs-material 主题升级到 9.5.48。

**工作逻辑**：文件以 `mkdocs-material==<version>` 形式固定版本（`==` 精确锁定），修改这一行即让文档构建环境安装新版主题。

```diff
- mkdocs-material==9.5.47
+ mkdocs-material==9.5.48
```

Dependabot 提交信息中还附带了 release notes、changelog、commits 对比链接，便于人工核对版本差异。

## 小结

- **成效**：文档站点主题升级到 9.5.48，获得补丁版本的修复；版本精确锁定，升级可控。
- **影响范围**：仅 `site/requirements.txt` 一个文件，1 行变更。无源代码逻辑改动，仅影响文档站点构建产物。
- **回迁到 1.4.x 的注意事项**：这是文档构建工具链升级，与产品运行时完全无关。1.4.x 作为维护分支，其文档站点主题版本一般与 main 独立维护。**无需回迁**；即使 1.4.x 的文档站点仍用 9.5.47 也不影响任何发布产物或运行时行为。仅当 1.4.x 文档构建因旧版本主题存在已知 bug 而失败时，才考虑同步升级。
