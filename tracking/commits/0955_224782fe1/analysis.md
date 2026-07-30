# 提交 0955：Build: Bump mkdocs-material from 9.5.28 to 9.5.29 (#10734)

## 提交信息

- **序号**：0955 / 4088
- **哈希**：224782fe11677e5878e9487a73147cb9f69a1450
- **短哈希**：224782fe1
- **日期**：2024-07-22（Mon Jul 22 09:16:53 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.28 to 9.5.29 (#10734)
- **PR/Issue**：#10734

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。mkdocs-material 是 Iceberg 文档站点（位于 `site/` 目录）使用 MkDocs Material 主题构建项目文档站所依赖的 Python 包。本次提交将 mkdocs-material 从 9.5.28 升级到 9.5.29（semver patch 版本升级），属于 patch 级别的小版本升级，通常仅包含 bug 修复和小幅改进，不引入新特性或破坏性变更。

目的是保持文档构建依赖的最新状态，获取上游的 bug 修复，避免文档站点构建或渲染时出现已知问题。

## 如何达成设计目的

实现方式为修改文档站点的 Python 依赖声明文件 `site/requirements.txt`，将其中 `mkdocs-material` 的版本固定从 `9.5.28` 改为 `9.5.29`。由于该文件以 `==` 精确锁定版本，CI 在构建文档时会拉取新版本。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 mkdocs-material 从 9.5.28 升级到 9.5.29。

**工作逻辑**：仅修改一行：

```diff
-mkdocs-material==9.5.28
+mkdocs-material==9.5.29
```

修改后，文档站点构建流程（如 ReadTheDocs 或 CI 文档构建任务）会安装 9.5.29 版本的 mkdocs-material 主题包。

## 小结

- **成效**：完成 mkdocs-material 从 9.5.28 到 9.5.29 的 patch 版本升级，保持文档构建依赖为最新修复版本。
- **影响范围**：仅修改 `site/requirements.txt` 一个文件，1 行改动。仅影响文档站点构建，不涉及任何 Iceberg 代码或测试。
- **回迁到 1.4.x 的注意事项**：纯文档构建依赖升级，**适合回迁**，风险极低。patch 版本升级通常完全兼容，回迁到 1.4.x 可让维护分支的文档构建同样获得最新修复。若 1.4.x 分支不维护独立的文档站点，可按需决定是否回迁。
