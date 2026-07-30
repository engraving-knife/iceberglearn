# 提交 3052：Build: Bump pymarkdownlnt from 0.9.33 to 0.9.34 (#14937)

## 提交信息

- **序号**：3052 / 4088
- **哈希**：b26009c5cc3a47be4fda062ac1cc0efa5f78d72f
- **短哈希**：b26009c5c
- **日期**：2025-12-27
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump pymarkdownlnt from 0.9.33 to 0.9.34 (#14937)
- **PR/Issue**：#14937

## 总体目的

这是一个 Dependabot 自动依赖升级提交。`pymarkdownlnt`（PyMarkdownLint）是 Iceberg 文档站点（`site/`）用于对 Markdown 文档进行 lint 校验的 Python 工具，确保文档遵循一致的格式规范（如标题层级、列表缩进、空白行等）。`site/requirements.txt` 锁定了该工具版本，与 `mkdocs-material` 等 MkDocs 构建依赖并列。

本次升级从 `0.9.33` 到 `0.9.34`，属于 **semver-patch**（补丁）升级。Dependabot 将其归类为 `direct:production` 依赖、`version-update:semver-patch`。补丁版本升级通常只包含 bug 修复与小改进，预期不会引入破坏性变更，但可能修正某些误报或新增个别规则行为，需关注文档 lint 是否因规则微调而报新问题。

## 如何达成设计目的

Dependabot 直接修改 `site/requirements.txt` 中 `pymarkdownlnt` 的版本钉，从 `0.9.33` 改为 `0.9.34`。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 pymarkdownlnt 版本钉。

**工作逻辑**：将 `pymarkdownlnt==0.9.33` 改为 `pymarkdownlnt==0.9.34`。该文件还锁定 `mkdocs-material==9.6.23`、`mkdocs-material-extensions==1.3.1`、`mkdocs-monorepo-plugin`（git 引用）、`mkdocs-redirects==1.2.2`。升级后，文档 lint 流程会使用 0.9.34 版本对 `docs/` 与 `site/` 下 Markdown 文件做格式校验。预期影响：获得上游 0.9.34 的修复（如规则误报修正、解析边界情况处理），文档校验更准确；属工具链依赖，不影响运行时。

## 总结

此提交是文档 lint 工具的补丁级依赖升级，保持 `pymarkdownlnt` 与上游同步，属于低风险的文档构建依赖维护，不涉及运行时功能变更。
