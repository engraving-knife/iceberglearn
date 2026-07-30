# 提交 1741：Build: Bump mkdocs-material from 9.6.3 to 9.6.4 (#12284)

## 提交信息

- **序号**：1741 / 4088
- **哈希**：bcbbd0344623ffea5b092e2de5debb0bc12892a1
- **短哈希**：bcbbd0344
- **日期**：2025-02-17 16:44:20 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.6.3 to 9.6.4 (#12284)
- **PR/Issue**：#12284

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级 PR。`mkdocs-material` 是 Iceberg 项目网站文档站点使用的 Material 主题 for MkDocs 静态站点生成器。本次升级将 `mkdocs-material` 从 9.6.3 升级到 9.6.4，属于补丁版本升级，包含主题的 bug 修复和改进。

Dependabot 定期扫描项目依赖并自动创建版本升级 PR，帮助项目保持文档工具链的最新状态。

## 如何达成设计目的

提交修改了网站文档的 Python 依赖文件 `site/requirements.txt`，将 `mkdocs-material` 版本从 `9.6.3` 更新为 `9.6.4`。这是一个单行修改。

## 修改详情

### `site/requirements.txt`（修改, +1/-1 lines）

**修改目的**：升级 `mkdocs-material` 文档主题版本。

**工作逻辑**：将 `mkdocs-material==9.6.3` 更新为 `mkdocs-material==9.6.4`。该文件列出了构建 Iceberg 文档站点所需的所有 Python 依赖及其精确版本号。

## 小结

- **成效**：将 `mkdocs-material` 从 9.6.3 升级到 9.6.4，获取了最新的文档主题 bug 修复。
- **影响范围**：仅影响文档站点的构建和展示，不影响任何 Iceberg 源代码或运行时功能。
- **回迁到 1.4.x 的注意事项**：此提交为文档工具版本升级，回迁风险极低。如果 1.4.x 分支已使用更高版本则无需回迁。建议回迁以保持文档工具链一致。
