# 提交 0705：升级 mkdocs-material 至 9.5.18

## 提交信息
- **序号**：0705 / 4088
- **哈希**：2510ef861cf135cd11d65fc8b9ee8b924b0ddcf9
- **短哈希**：2510ef861
- **日期**：2024-04-21
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-material from 9.5.17 to 9.5.18 (#10189)
- **PR/Issue**：#10189

## 总体目的

本提交由 Dependabot 自动生成，将 mkdocs-material 从 `9.5.17` 升级到 `9.5.18`，属于 patch 版本级别的依赖升级。

mkdocs-material 是基于 MkDocs 的文档主题，用于构建 Iceberg 项目的官方网站文档（https://iceberg.apache.org）。项目在 `site/requirements.txt` 中通过精确版本锁定（`==`）声明了文档构建所依赖的 Python 包及其版本。文档构建产物本身不进入发布制品（jar 包），仅影响网站展示。

Dependabot 例行升级目的在于获取文档主题的 bug 修复、样式微调和潜在的安全补丁，保持文档站点展示效果的稳定性。从 `9.5.17` 到 `9.5.18` 是单个 patch 版本升级，风险极低。

## 如何达成设计目的

通过修改 `site/requirements.txt` 中 `mkdocs-material` 的版本锁定即可。该文件是 Python pip 的依赖声明文件，文档构建时（通常通过 CI 中的 `mkdocs build` 命令）会按此文件安装指定版本。

## 修改详情

### `site/requirements.txt`
**修改目的**：将 `mkdocs-material` 版本从 `9.5.17` 升级到 `9.5.18`。
**工作逻辑**：`mkdocs-material==9.5.17` → `mkdocs-material==9.5.18`。文件头部有注释行（第 17 行附近），修改发生在 `mkdocs-macros-plugin` 之后、`mkdocs-material-extensions` 之前。`==` 精确锁定确保 CI 构建环境一致。

## 小结
- **成效**：成功达成目的，完成 mkdocs-material 的 patch 版本升级。
- **影响范围**：仅影响文档站点 `site/` 模块的构建产物（即 iceberg.apache.org 的展示效果），不影响任何 Java 制品和运行时行为。
- **回迁到 1.4.x 的注意事项**：可直接回迁。文档构建依赖与 Java 代码无耦合，无冲突风险。若 1.4.x 分支有自己的文档分支或构建流程，同步升级即可保持文档样式一致。
