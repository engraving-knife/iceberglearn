# 提交 2632：Build: Bump mkdocs-material from 9.6.18 to 9.6.19 (#14074)

## 提交信息

- **序号**：2632 / 4088
- **哈希**：b806966ecb6e1855a4d6c8ee3e7182b0d52f7c53
- **短哈希**：b806966ec
- **日期**：2025-09-14 19:58:06 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.18 to 9.6.19 (#14074)
- **PR/Issue**：#14074

## 总体目的

这是 dependabot 自动生成的依赖升级提交，将 mkdocs-material 主题从 9.6.18 版本升级到 9.6.19 版本。

mkdocs-material 是 Iceberg 项目用于构建文档网站的 MkDocs 主题，提供了文档站点的外观与交互体验。保持主题处于最新版本有助于获取上游的 bug 修复、新特性和安全补丁，确保文档站点正常运行。

本次升级属于 semver-patch 级别，按照语义化版本约定应保持向后兼容。

## 如何达成设计目的

通过修改文档站点依赖文件 `site/requirements.txt`，将 mkdocs-material 的版本固定从 `9.6.18` 改为 `9.6.19`。该文件使用 pip 的精确版本锁定（==），构建文档站点时会安装指定版本。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：更新 mkdocs-material 主题版本。

**工作逻辑**：将 `mkdocs-material==9.6.18` 修改为 `mkdocs-material==9.6.19`。该文件列出了文档站点构建所需的 Python 依赖及其精确版本，确保构建环境一致可重现。

## 总结

这是一次常规的文档站点构建依赖补丁版本升级，由 dependabot 自动完成。升级 mkdocs-material 主题到 9.6.19 可获取文档站点相关的 bug 修复与改进，对 Iceberg 的核心代码无影响。
