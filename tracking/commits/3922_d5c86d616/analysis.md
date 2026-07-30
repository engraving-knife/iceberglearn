# 提交 3922：Build: Bump pymarkdownlnt from 0.9.37 to 0.9.38 (#16895)

## 提交信息

- **序号**：3922 / 4088
- **哈希**：d5c86d6164404ef758f3fc174f8d3afe5bef1697
- **短哈希**：d5c86d616
- **日期**：2026-06-21 00:08:34 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump pymarkdownlnt from 0.9.37 to 0.9.38 (#16895)
- **PR/Issue**：#16895

## 总体目的

这是由 Dependabot 发起的依赖升级，将 PyMarkdown linter（`pymarkdownlnt`）从 0.9.37 升级到 0.9.38。PyMarkdown 是一个用于检查 Markdown 文档风格和规范一致性的 Python 工具。Iceberg 项目使用它来对文档站点（site）的 Markdown 文件进行 lint 检查，保证文档格式的一致性与可读性。

此次升级为 semver-patch 更新，主要获取上游的 bug 修复和规则改进，属于低风险常规维护。

## 如何达成设计目的

通过修改文档站点构建依赖文件 `site/requirements.txt`，将 pymarkdownlnt 的版本约束从 `0.9.37` 更新为 `0.9.38`。该 requirements 文件用于在构建文档站点时安装所需的 Python 工具链。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：升级 PyMarkdown linter 版本。

**工作逻辑**：
将 `pymarkdownlnt==0.9.37` 改为 `pymarkdownlnt==0.9.38`。使用精确版本约束（`==`）确保可重现构建。

## 总结

这是一次 PyMarkdown linter 的补丁版本升级，通过更新文档站点构建依赖到 0.9.38，获取上游的规则改进和 bug 修复。作为补丁版本更新，预期向后兼容，风险极低。
