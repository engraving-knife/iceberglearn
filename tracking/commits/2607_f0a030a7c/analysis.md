# 提交 2607：Site: Bump up mkdocs-monorepo-plugin to 1.1.2 (#14015)

## 提交信息

- **序号**：2607 / 4088
- **哈希**：f0a030a7cf89de3d4f5ddbb8f257b413fe504da5
- **短哈希**：f0a030a7c
- **日期**：2025-09-08 08:39:48 +0200
- **作者**：Manu Zhang
- **提交说明**：Site: Bump up mkdocs-monorepo-plugin to 1.1.2 (#14015)
- **PR/Issue**：#14015

## 总体目的

本次提交将 Iceberg 官网构建工具 mkdocs-monorepo-plugin 从旧版本升级到 1.1.2。

mkdocs-monorepo-plugin 是 MkDocs 的一个插件，用于在 monorepo 结构中管理多个文档子站点。Iceberg 项目使用 MkDocs 构建官方网站，文档分布在不同的目录中（如 `site/docs/`、`docs/docs/` 等），该插件允许将这些分散的文档组织成一个统一的文档站点。

升级到 1.1.2 可能修复了已知的 bug、改善了构建性能或增加了新功能，确保文档构建过程的稳定性和可靠性。

## 如何达成设计目的

在网站构建依赖文件 `site/requirements.txt` 中，将 mkdocs-monorepo-plugin 的版本号升级到 1.1.2。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 mkdocs-monorepo-plugin 版本。

**工作逻辑**：将 `site/requirements.txt` 中的 mkdocs-monorepo-plugin 版本号改为 `1.1.2`。该文件定义了构建 Iceberg 官网所需的 Python 依赖，CI/CD 流水线和本地文档构建时会通过 `pip install -r requirements.txt` 安装指定版本的依赖。

**潜在影响**：升级文档构建工具插件可能改善文档构建的稳定性和功能。1.1.2 版本可能修复了文档链接检查、多站点合并等方面的 bug。对于最终用户（文档读者）没有直接影响，但对文档维护者和 CI 流水线有积极意义。

## 总结

这是一个文档构建工具升级提交，将 mkdocs-monorepo-plugin 升至 1.1.2。这类升级确保文档构建工具链保持最新，减少构建失败的风险并可能获得新功能支持。
