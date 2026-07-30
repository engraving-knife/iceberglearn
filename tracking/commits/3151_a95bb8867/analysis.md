# 提交 3151：Build: Bump mkdocs-rss-plugin from 1.17.4 to 1.17.9 (#15131)

## 提交信息

- **序号**：3151 / 4088
- **哈希**：a95bb8867c4496d74b5dcb8c5cfb2c5376acc3b6
- **短哈希**：a95bb8867
- **日期**：2026-01-24 21:09:37 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump mkdocs-rss-plugin from 1.17.4 to 1.17.9 (#15131)
- **PR/Issue**：#15131

## 总体目的

本提交是 Dependabot 自动发起的依赖升级，将文档站点的 MkDocs 插件 `mkdocs-rss-plugin` 从 `1.17.4` 升级到 `1.17.9`。`mkdocs-rss-plugin` 是 MkDocs Material 文档站点的 RSS 订阅源生成插件，用于为 Iceberg 文档站点自动生成 RSS feed，使订阅者能跟踪文档更新。该依赖声明在 `site/requirements.txt` 中，与文档站点的其他构建依赖一同管理。

本次升级为补丁版本（patch）升级（`version-update:semver-patch`，1.17.4 → 1.17.9），跨 5 个补丁版本，按语义化版本约定仅包含向后兼容的缺陷修复与小幅改进，预期不会改变 RSS 插件的配置接口或生成的 feed 结构。值得注意的是，本提交紧随 3150（pymarkdownlnt 升级）之后，且 diff 显示两者对 `site/requirements.txt` 的改动存在依赖关系：3151 修改的文件基线已包含 3150 引入的 `pymarkdownlnt==0.9.35`，说明这两个 Dependabot PR 是按顺序合并的。Dependabot 在提交信息中附带了上游 release notes、changelog 与 commits 对比链接。

## 如何达成设计目的

直接在 `site/requirements.txt` 中将 `mkdocs-rss-plugin` 的版本固定从 `1.17.4` 改为 `1.17.9`，无需其他改动。该文件以 `==` 精确固定版本，文档站点构建时会拉取新版本插件。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：将 mkdocs-rss-plugin 版本从 1.17.4 提升到 1.17.9。

**工作逻辑**：将 `mkdocs-rss-plugin==1.17.4` 修改为 `mkdocs-rss-plugin==1.17.9`。该插件在 MkDocs 文档站点构建时为站点生成 RSS 订阅源，让用户可订阅文档更新。升级到 1.17.9 获取上游 5 个补丁版本的修复与改进，属 semver-patch 级别，预期向后兼容，不会影响文档站点的构建流程与 RSS feed 输出结构。

## 总结

本提交通过将文档站点 RSS 生成插件 mkdocs-rss-plugin 从 1.17.4 升级到 1.17.9，获取上游补丁修复，保持文档站点构建依赖的及时更新；作为 semver-patch 升级，对站点构建与 RSS 输出预期无破坏性影响。
