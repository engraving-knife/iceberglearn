# 提交 0160：Build: Bump mkdocs-macros-plugin from 1.0.4 to 1.0.5 (#9058)

## 提交信息

- **序号**：0160 / 4088
- **哈希**：9cfbbdc9ef8a39bfc800a1cb7892b3900bd57be7
- **短哈希**：9cfbbdc9e
- **日期**：2023-11-14 07:58:28 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-macros-plugin from 1.0.4 to 1.0.5 (#9058)
- **PR/Issue**：#9058

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 MkDocs Macros 插件从 1.0.4 升级到 1.0.5。这是 `version-update:semver-patch` 级别的补丁升级，也是 0159 提交（为 site 新增 pip 生态系统 dependabot 配置）合入后由 Dependabot 发起的首批站点依赖自动升级之一。

`mkdocs-macros-plugin` 是 Iceberg 文档站点（`site/`）所用的 MkDocs 扩展插件，允许在 Markdown 文档中通过 Jinja2 宏语法插入动态内容（变量、计算结果、外部数据等），是 MkDocs Material 主题下常用的内容增强工具。它的版本声明在 [`site/requirements.txt`](../../../site/requirements.txt) 中。1.0.4 -> 1.0.5 是一个 patch 升级，按上游 [CHANGELOG](https://github.com/fralau/mkdocs_macros_plugin/blob/master/CHANGELOG.md) 通常包含 bug 修复与兼容性改进，不引入破坏性变更。

引入升级的意义在于：在 0159 启用 pip dependabot 后，文档站点的 Python 依赖首次进入自动化升级轨道。该提交验证了自动化链路已生效（Dependabot 成功识别 `site/requirements.txt` 并发起 PR），同时让宏插件与上游稳定版本保持同步，减少文档构建时的潜在 bug。

## 如何达成设计目的

通过修改 [`site/requirements.txt`](../../../site/requirements.txt) 中 `mkdocs-macros-plugin` 的版本锁定，从 `1.0.4` 改为 `1.0.5`。该文件是 pip 风格的依赖清单，使用 `==` 精确锁定版本，由文档站点的构建脚本通过 `pip install -r site/requirements.txt` 安装。

## 修改详情

### `site/requirements.txt`

**修改目的**：将 mkdocs-macros-plugin 从 1.0.4 升级到 1.0.5，跟进上游 patch 版本修复。

**工作逻辑**：在 `site/requirements.txt` 中将 `mkdocs-macros-plugin==1.0.4` 修改为 `mkdocs-macros-plugin==1.0.5`。该文件采用 `==` 精确版本锁定（pinning），确保文档构建的可复现性。文件中并列的其他依赖包括 `mkdocs-awesome-pages-plugin==2.9.2`、`mkdocs-material==9.1.21`、`mkdocs-material-extensions==1.1.1`、`mkdocs-monorepo-plugin==1.0.5` 等，构成完整的 MkDocs 文档站点构建链。本次只升级 macros-plugin 一项，与 dependabot 一次一 PR 的策略一致。

## 小结

通过 dependabot 自动升级 mkdocs-macros-plugin 到 1.0.5，是 0159 启用 pip 生态系统后站点依赖自动化维护链路的一次具体生效，验证了文档站点依赖升级的自动化已落地。
