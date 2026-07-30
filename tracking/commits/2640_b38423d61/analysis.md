# 提交 2640：Infra: update how-to-release doc and site scripts on releasing versioned doc and javadoc based on learnings from 1.10.0 release (#14066)

## 提交信息

- **序号**：2640 / 4088
- **哈希**：b38423d614d819268040ce9f48e4a4593220ba8d
- **短哈希**：b38423d61
- **日期**：2025-09-15 17:37:51 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Infra: update how-to-release doc and site scripts on releasing versioned doc and javadoc based on learnings from 1.10.0 release (#14066)
- **PR/Issue**：#14066

## 总体目的

在 1.10.0 版本发布过程中，发布团队发现文档站点的发布流程存在若干问题：部署脚本（deploy.sh）默认将站点推送到名为 `apache` 的远程仓库，但本地 git 克隆的默认远程名通常是 `origin`，导致部署时需要手动指定远程名或报错；发布文档（how-to-release.md）中关于版本化文档和 javadoc 发布的说明不够清晰，章节顺序不够合理；脚本中使用了 `pip` 而非 `pip3`，部分日志信息不够明确。

本提交基于 1.10.0 发布的经验教训，对文档站点的 Makefile、README、部署脚本和发布文档进行了更新，使发布流程更健壮、说明更清晰。

## 如何达成设计目的

1. 在 `deploy.sh` 中增加 `remote_name` 参数（默认 `apache`），并将该参数传给 `mkdocs gh-deploy` 的 `--remote-name` 选项，使部署可指定远程仓库名。
2. 在 `Makefile` 的 `deploy` 目标中传递 `remote_name` 变量给脚本。
3. 在 `README.md` 中说明默认远程名为 `apache`，可通过 `make deploy remote_name=apache` 指定，并说明需要 committer 写权限。
4. 在 `common.sh` 中将 `pip` 改为 `pip3`，调整若干日志输出顺序（先校验参数再输出日志），使逻辑更清晰。
5. 在 `how-to-release.md` 中重排文档发布章节，将"生成版本化文档"、"生成版本化 Javadoc"、"发布版本化文档和 javadoc"分开说明，提升可读性。

## 修改详情

### `site/Makefile` (+1/-1 lines)

**修改目的**：向部署脚本传递 remote_name 变量。

**工作逻辑**：`deploy` 目标从 `dev/deploy.sh` 改为 `dev/deploy.sh $(remote_name)`，使 `make deploy remote_name=xxx` 能传入远程名。

### `site/README.md` (+5/-2 lines)

**修改目的**：说明部署的远程仓库与权限要求。

**工作逻辑**：更新部署说明，指出推送到 `asf-site` 分支需要 committer 写权限，默认远程名为 `apache`，可用 `make deploy remote_name=apache` 指定。

### `site/dev/common.sh` (+12/-11 lines)

**修改目的**：改进依赖安装和日志输出。

**工作逻辑**：将 `pip` 改为 `pip3`。在 `create_latest`、`update_version`、`search_exclude_versioned_docs` 等函数中，调整日志输出顺序：先进行参数校验（`assert_not_empty`）再输出操作日志，并增加版本号到日志信息中便于调试。

### `site/dev/deploy.sh` (+5/-1 lines)

**修改目的**：支持指定远程仓库名。

**工作逻辑**：新增 `remote_name="${1:-apache}"`，输出部署目标信息，并将 `--remote-name ${remote_name}` 传给 `mkdocs gh-deploy`。

### `site/docs/how-to-release.md` (+6/-4 lines)

**修改目的**：重排发布文档章节。

**工作逻辑**：将"Documentation Release"下的内容重排为三个子节："Generate versioned Docs"、"Generate versioned Javadoc"、"Release versioned docs and javadoc"（指向 site README），使流程更清晰。

## 总结

本提交基于 1.10.0 发布经验改进了文档站点的发布基础设施：使部署脚本支持指定远程仓库名、修复 pip 命令、优化日志输出、重排发布文档章节。这些改进降低了发布流程出错的可能性，提升了可维护性。注意后续提交 2640 将默认远程名从 `apache` 改回了 `origin`。
