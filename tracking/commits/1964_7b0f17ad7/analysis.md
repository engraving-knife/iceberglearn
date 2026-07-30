# 提交 1964：Build: Bump mkdocs-material from 9.6.9 to 9.6.11 (#12728)

## 提交信息

- **序号**：1964 / 4088
- **哈希**：7b0f17ad7e9916906f09c82803edf333e318be1f
- **短哈希**：7b0f17ad7
- **日期**：2025-04-06 12:46:58 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.9 to 9.6.11 (#12728)
- **PR/Issue**：#12728

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，将 Iceberg 官网文档站点使用的 MkDocs Material 主题从 9.6.9 升级到 9.6.11。MkDocs Material 是构建 `site/` 文档站点的主题框架，本次为 patch 版本升级，通常包含 bug 修复与小改进，不引入破坏性变更。

## 如何达成设计目的

通过修改 `site/requirements.txt`（文档站点的 Python 依赖清单）中 mkdocs-material 的版本声明来完成升级。该依赖仅用于文档构建，不影响 Iceberg 运行时。

## 修改详情

### `site/requirements.txt` (修改, +1/-1 lines)

**修改目的**：升级 mkdocs-material 版本声明。

**工作逻辑**：将 mkdocs-material 的版本从 `9.6.9` 改为 `9.6.11`。

## 总结

本提交是 Dependabot 发起的依赖升级，将文档站点主题 mkdocs-material 从 9.6.9 升级到 9.6.11，仅修改 `site/requirements.txt` 一行版本声明。
