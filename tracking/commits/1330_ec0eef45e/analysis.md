# 提交 1330：Build: Bump mkdocs-material from 9.5.42 to 9.5.43 (#11455)

## 提交信息

- **序号**：1330 / 4088
- **哈希**：ec0eef45ebe11786072a71ae315c0637c2154862
- **短哈希**：ec0eef45e
- **日期**：2024-11-04（Mon Nov 4 15:13:21 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.42 to 9.5.43 (#11455)
- **PR/Issue**：#11455

## 总体目的

由 Dependabot 自动发起的文档站点依赖升级：将 MkDocs Material 主题（`mkdocs-material`）从 `9.5.42` 升级到 `9.5.43`，属 patch 升级。Iceberg 的文档站点位于 `site/` 目录，使用 MkDocs 配合 Material 主题构建（发布到 `iceberg.apache.org`）。`site/requirements.txt` 锁定 Python 依赖版本以保证文档构建可复现。升级目的是获取 9.5.43 中主题 bug 修复与小改进。

Dependabot 标注 `update-type: version-update:semver-patch`，属低风险升级。

## 如何达成设计目的

只修改 `site/requirements.txt` 中 `mkdocs-material` 这一行的版本号。该文件是 pip 安装文档站点依赖的清单，MkDocs Material 主题通过 pip 安装。

## 修改详情

### `site/requirements.txt`

**修改目的**：升级 MkDocs Material 主题版本号。

**工作逻辑**：将文件中（约第 20 行）的版本声明由

```
mkdocs-material==9.5.42
```

改为

```
mkdocs-material==9.5.43
```

文件中其他依赖（`mkdocs-awesome-pages-plugin==2.9.3`、`mkdocs-macros-plugin==1.3.7` 等）保持不变。使用 `==` 精确锁定版本，确保文档构建环境一致。

## 小结

- **成效**：MkDocs Material 主题升级至 9.5.43，获取 patch 修复。属文档构建工具链维护性升级，不影响 Iceberg 运行时行为，仅影响文档站点的构建与渲染。
- **影响范围**：仅 1 个文件、1 行版本号变更。下游影响限于 `mkdocs build` 任务的执行产物（即文档站点的 HTML/CSS/JS 输出）；patch 升级通常保持主题行为兼容。
- **回迁到 1.4.x 的注意事项**：**无需回迁**。文档站点由 main 分支统一构建发布，1.4.x 作为维护分支不单独维护文档站点。即使 1.4.x 分支下也存在 `site/requirements.txt`，它不会用于生产文档构建（生产文档始终来自最新 main）。因此本提交对 1.4.x 的运行时与发布产物无任何影响，无需回迁。
