# 提交 0623：将 mkdocs-material 从 9.5.14 升级到 9.5.15

## 提交信息

- **序号**：0623 / 4088
- **哈希**：857590f7fa8b04212e9ace2efe5f1378ede4f96f
- **短哈希**：857590f7f
- **日期**：2024-03-24（Sun Mar 24 06:14:58 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-material from 9.5.14 to 9.5.15 (#10031)
- **PR/Issue**：#10031

## 总体目的

这是一个由 GitHub Dependabot 自动生成的依赖升级提交，目的是把 Iceberg 文档站点（`site/`）构建所依赖的 `mkdocs-material` 主题包从 9.5.14 升级到 9.5.15。

`mkdocs-material` 是 Iceberg 文档站点（iceberg.apache.org）使用的 MkDocs 主题，负责站点的视觉样式、搜索、导航、代码高亮等所有前端呈现。Dependabot 会定期扫描 `site/requirements.txt` 中锁定的依赖版本，发现上游有新版本时自动发起 PR 升级。本次升级属于 **patch 级别**（semver 的第三位从 14 到 15），按语义化版本约定只含 bug 修复与小改进，不引入破坏性变更。

## 如何达成设计目的

Dependabot 的工作机制：

1. 扫描 `site/requirements.txt` 发现 `mkdocs-material==9.5.14` 已落后于 PyPI 上的 9.5.15；
2. 自动创建分支并把版本号改为 `mkdocs-material==9.5.15`；
3. 自动生成 PR 说明，包含上游 release notes / changelog / commits 对比链接，并附上 `updated-dependencies` 元数据（标注 `direct:production`、`version-update:semver-patch`）；
4. 由维护者评审合并。本提交即合并后的结果。

提交说明中 `Signed-off-by: dependabot[bot]` 与 `Co-authored-by: dependabot[bot]` 是 Dependabot 的标准签名。

## 修改详情

### `site/requirements.txt`

**修改目的**：升级 mkdocs-material 锁定版本。

**工作逻辑**：

- `-mkdocs-material==9.5.14` 改为 `+mkdocs-material==9.5.15`，仅此一行变化。
- 该文件用 `==` 精确锁版本，保证 CI 与本地构建环境一致。文件中还锁定了其他 mkdocs 插件：`mkdocs-awesome-pages-plugin==2.9.2`、`mkdocs-macros-plugin==1.0.5`、`mkdocs-material-extensions==1.3.1`、`mkdocs-monorepo-plugin`（git 依赖）、`mkdocs-redirects==1.2.1` 等，本次只动 `mkdocs-material` 一行。

## 小结

本提交是 Dependabot 自动化依赖维护的典型产物，把文档主题从 9.5.14 升到 9.5.15，仅一行版本号变更，无破坏性改动。

**影响范围**：

- 仅影响 `site/requirements.txt`，不动任何文档内容、源代码、构建脚本。
- 升级后文档站点的视觉与功能可能获得 9.5.15 的 bug 修复（具体修复项需查阅 mkdocs-material 9.5.15 的 changelog），对终端用户体验是渐进式改善。
- 由于是 patch 升级，构建行为应保持稳定，不会破坏既有文档渲染。

**回迁到 1.4.x 的注意事项**：

- 回迁非常安全，纯依赖版本号变更。
- 若 1.4.x 的 `site/requirements.txt` 仍锁 9.5.14 或更早版本，可直接回迁本提交升到 9.5.15；若 1.4.x 已有更新版本（如 9.5.16+），则无需回迁本提交，保持已有更高版本即可。
- 回迁前可快速跑一次 `mkdocs build` 验证 9.5.15 在 1.4.x 文档结构下渲染正常，但鉴于 patch 升级风险极低，通常可直接合并。
- 注意 Dependabot 的 PR 说明里链接用的是 `github.com/squidfunk/mkdocs-material` 的 `master` 分支 CHANGELOG——上游仓库后续可能已改默认分支名，但这是 PR 说明里的链接，回迁时无需处理。
