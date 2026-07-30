# 提交 0825：Build: Bump mkdocs-material from 9.5.25 to 9.5.26 (#10464)

## 提交信息

- **序号**：0825 / 4088
- **哈希**：fa95e1277fc38dae2f2cbe1c687b163d928b03aa
- **短哈希**：fa95e1277
- **日期**：2024-06-11 03:40:06 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.5.25 to 9.5.26 (#10464)
- **PR/Issue**：#10464

## 总体目的

本提交由 Dependabot 自动生成，将 MkDocs Material 主题从 `9.5.25` 升级到 `9.5.26`。MkDocs Material 是 Iceberg 项目文档站点（`site/` 目录）使用的 MkDocs 主题，用于构建和渲染项目文档网站。

此次升级属于 SemVer patch 级别升级（9.5.25 → 9.5.26），目的是获取该主题的缺陷修复和小幅改进。

## 如何达成设计目的

提交仅修改了 `site/requirements.txt` 中的一行版本号声明：

1. **版本锁定声明**：将 `mkdocs-material==9.5.25` 改为 `mkdocs-material==9.5.26`。该文件是 Python 依赖声明文件，使用 `==` 精确锁定版本，确保文档构建环境可重现。

2. **依赖安装机制**：文档站点构建时通过 `pip install -r site/requirements.txt` 安装所有 Python 依赖（包括 MkDocs 及其插件和主题），修改版本号后下次构建即使用新版本主题。

3. **无需修改其他文件**：MkDocs 配置文件（`mkdocs.yml`）中引用主题的方式不涉及版本号，主题升级后配置无需改动。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 mkdocs-material 主题从 9.5.25 升级到 9.5.26。

**工作逻辑**：
- 修改位于 `site/requirements.txt` 文件中（Apache 许可证头之后），第 20 行附近。
- 该文件列出了文档站点构建所需的全部 Python 依赖，包括：
  - `mkdocs-awesome-pages-plugin==2.9.2`：自动生成页面导航插件。
  - `mkdocs-macros-plugin==1.0.5`：宏模板插件。
  - `mkdocs-material==9.5.26`：Material 主题（本提交修改项）。
  - `mkdocs-material-extensions==1.3.1`：Material 主题扩展。
  - `mkdocs-monorepo-plugin`：多仓库插件（从 Git 仓库安装）。
  - `mkdocs-redirects==1.2.1`：重定向插件。
- 9.5.25 到 9.5.26 的 patch 升级通常包含：主题样式微调、渲染缺陷修复、浏览器兼容性改进等，不影响文档内容本身。
- 使用 `==` 精确版本锁定，保证不同环境（本地预览与 CI 构建）生成一致的文档站点。

## 小结

- **成效**：MkDocs Material 主题升级到 9.5.26，获取了 patch 版本的样式和渲染修复，保持文档站点主题的最新状态。
- **影响范围**：仅影响文档站点（`site/`）的构建和渲染外观，不影响项目源代码、构建逻辑或运行时行为。文档内容和结构不受影响，仅主题渲染细节可能有微小变化。
- **回迁注意事项**：回迁到 1.4.x 分支时需注意——当前 1.4.x 分支可能不存在 `site/requirements.txt` 文件（文档站点结构可能与 main 分支有差异）。回迁前需确认 1.4.x 分支是否已使用 MkDocs Material 主题及当前版本。若存在该文件且版本低于 9.5.25，建议整体评估文档依赖升级。MkDocs Material 9.5.x 内部 patch 升级向后兼容，回迁风险极低，主要需确保 Python 环境中其他 MkDocs 插件与新版本主题兼容。
