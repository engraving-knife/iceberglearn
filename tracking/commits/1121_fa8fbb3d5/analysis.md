# 提交 1121：Build: Bump mkdocs-material from 9.5.33 to 9.5.34 (#11062)

## 提交信息

- **序号**：1121 / 4088
- **哈希**：fa8fbb3d5289a3b17fab810a2b872299fb502314
- **短哈希**：fa8fbb3d5
- **日期**：2024-09-01（Sun Sep 1 06:39:10 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.33 to 9.5.34 (#11062)
- **PR/Issue**：#11062

## 总体目的

Iceberg 官网使用 MkDocs（Material 主题）构建文档站点，依赖在 `site/requirements.txt` 中声明。`mkdocs-material` 是 Material for MkDocs 主题包，9.5.33 → 9.5.34 是一次 patch 版本升级（semver patch），通常包含 bug 修复与小改进，无 breaking change。

Dependabot 自动检测到上游 `squidfunk/mkdocs-material` 发布了 9.5.34，自动创建本 PR 升级依赖，保持文档构建依赖的最新状态，获取上游修复（如渲染 bug、安全补丁、新功能改进等）。

本提交是 Dependabot 自动化依赖升级流程的一部分，无任何代码或文档内容改动，仅升级 pip 依赖版本号。

## 如何达成设计目的

直接修改 `site/requirements.txt` 中 `mkdocs-material` 的版本约束，从 `mkdocs-material==9.5.33` 改为 `mkdocs-material==9.5.34`。`==` 精确版本约束保证 CI 与本地构建使用完全相同的版本，避免因上游小版本差异导致文档渲染不一致。

Dependabot 在 PR 描述中提供了上游 release notes、changelog、commits 对比链接，便于维护者评估升级影响。本提交由 dependabot[bot] 自动创建，由维护者审核合并。

这是单行依赖版本号调整，无任何构建脚本逻辑改动。

## 修改详情

### `site/requirements.txt`

**修改目的**：升级 `mkdocs-material` 依赖版本。

**工作逻辑**：将第 20 行（在 `mkdocs-macros-plugin==1.0.5` 之后、`mkdocs-material-extensions==1.3.1` 之前）的

```
mkdocs-material==9.5.33
```

改为：

```
mkdocs-material==9.5.34
```

其他依赖（`mkdocs-awesome-pages-plugin==2.9.3`、`mkdocs-macros-plugin==1.0.5`、`mkdocs-material-extensions==1.3.1`、`mkdocs-monorepo-plugin @ git+...`、`mkdocs-redirects==1.2.1` 等）保持不变。本次升级是 patch 级别（9.5.33 → 9.5.34），根据 semver 规范保证向后兼容。

## 小结

- **成效**：官网 MkDocs Material 主题依赖从 9.5.33 升级到 9.5.34，获取上游 patch 修复与改进，保持文档构建依赖最新；Dependabot 自动化流程减少人工维护成本。
- **影响范围**：1 个文件、1 增 1 删，纯 pip 依赖版本号调整，不影响任何代码或文档内容；升级为 patch 级别，无 breaking change。
- **回迁到 1.4.x 的注意事项**：这是文档构建依赖的自动升级，与运行时产物无关，**无需回迁到 1.4.x**。1.4.x 维护分支的文档构建（若有）应使用各自分支的 `site/requirements.txt`，由 1.4.x 自己的 Dependabot 流程（若启用）或维护者手动管理依赖版本；即使 1.4.x 的 mkdocs-material 版本滞后，也不影响 1.4.x 发布的 jar 制品。本提交属于 main 分支文档基础设施维护，与 1.4.x 无回迁关系。
