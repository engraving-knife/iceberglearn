# 提交 1672：Build: Bump mkdocs-material from 9.5.50 to 9.6.1 (#12157)

## 提交信息

- **序号**：1672 / 4088
- **哈希**：1c0c7832ca2ff70afc12136fb93ae1e36a40c726
- **短哈希**：1c0c7832c
- **日期**：2025-02-02（Sun Feb 2 07:48:28 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.50 to 9.6.1 (#12157)
- **PR/Issue**：#12157

## 总体目的

Dependabot 自动升级，把 MkDocs Material 主题（Iceberg 文档站点 `https://iceberg.apache.org` 使用的静态文档生成器主题）从 `9.5.50` 升到 `9.6.1`。`9.5.x → 9.6.x` 是 minor 版本升级，通常包含新功能、UI 改进、bug 修复以及对新版 MkDocs 的兼容性。

MkDocs Material 是基于 MkDocs（Python 文档生成器）的开源文档主题，Iceberg 在 `site/` 目录下维护文档源码与 Python 依赖清单（`requirements.txt`），通过 MkDocs + Material 构建 API 文档与用户指南。此依赖属于文档构建工具链，不进入 Java 发布产物。

## 如何达成设计目的

修改 `site/requirements.txt` 中 `mkdocs-material` 的版本号。该文件是 Python pip 依赖清单，文档构建时通过 `pip install -r requirements.txt` 安装。

## 修改详情

### `site/requirements.txt`（修改，1 行）

```diff
-mkdocs-material==9.5.50
+mkdocs-material==9.6.1
```

该文件以 `==` 精确锁定版本，避免不同构建环境间文档样式漂移。同文件还包含 `mkdocs-awesome-pages-plugin`、`mkdocs-macros-plugin`、`mkdocs-redirects` 等文档插件。

## 小结

- **成效**：MkDocs Material 主题升级到 9.6.1，获取上游 minor 版本的新功能与修复。
- **影响范围**：仅文档站点构建（`site/` 目录），不影响 Java 代码、发布产物或运行时行为。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick，无风险。仅影响文档构建，建议回迁后本地 `mkdocs serve` 验证文档渲染正常（minor 版本升级偶有模板/配色微调）。1.4.x 的 `site/requirements.txt` 结构应与 main 一致。
