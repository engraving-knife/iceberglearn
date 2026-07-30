# 提交 2610：Revert "Site: Bump up mkdocs-monorepo-plugin to 1.1.2 (#14015)" (#14022)

## 提交信息

- **序号**：2610 / 4088
- **哈希**：f35e88da47705430b96cfa777b77dda22d1949b7
- **短哈希**：f35e88da4
- **日期**：2025-09-08 17:14:12 +0200
- **作者**：Manu Zhang
- **提交说明**：Revert "Site: Bump up mkdocs-monorepo-plugin to 1.1.2 (#14015)" (#14022)
- **PR/Issue**：#14022（revert 了 #14015）

## 总体目的

此提交回滚了先前一次 mkdocs-monorepo-plugin 依赖升级（PR #14015）。被回滚的提交将依赖从 git 仓库的 `url-fix` 分支安装方式改为 PyPI 上发布的 1.1.2 正式版本，但该升级随后被发现存在问题，因此通过此 revert 恢复为原来的 git URL 安装方式。

从 diff 内容可以推断，发布到 PyPI 的 1.1.2 版本很可能并未包含此前在 `bitsondatadev/mkdocs-monorepo-plugin` 仓库 `url-fix` 分支中修复的 URL 相关问题（分支名 `url-fix` 明确指向该修复）。也就是说，虽然版本号升到了 1.1.2，但实际打包的内容缺少 Iceberg 文档站点所依赖的关键修复，导致升级反而引入了回归。

## 如何达成设计目的

通过标准的 `git revert` 操作，将 `site/requirements.txt` 中 `mkdocs-monorepo-plugin` 这一行从固定版本 `==1.1.2` 还原回通过 git 仓库 `url-fix` 分支安装的形式，从而恢复文档站点的正确构建依赖。

## 修改详情

### `site/requirements.txt` (+1/-1 lines)

**修改目的**：回滚 mkdocs-monorepo-plugin 的依赖声明，恢复使用包含 URL 修复的 git 分支版本。

**工作逻辑**：将依赖行从 `mkdocs-monorepo-plugin==1.1.2`（PyPI 正式版本）改回 `mkdocs-monorepo-plugin @ git+https://github.com/bitsondatadev/mkdocs-monorepo-plugin@url-fix`（直接从 GitHub 的 `url-fix` 分支安装）。这表明 PyPI 上发布的 1.1.2 版本未包含 Iceberg 文档站点所需的 URL 修复，必须继续依赖该 fork 分支。

## 总结

此提交是一次快速回滚，用于修复因升级 mkdocs-monorepo-plugin 到 PyPI 1.1.2 版本而导致的文档构建回归。它提醒我们版本号升级并不总是包含所有必要的修复，在 PyPI 发布版本追上 fork 修复之前，Iceberg 文档站点仍需依赖 git 分支版本。
