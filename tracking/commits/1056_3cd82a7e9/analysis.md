# 提交 1056：Docs, Infra: Mount local versioned doc branch for testing (#10838)

## 提交信息

- **序号**：1056 / 4088
- **哈希**：3cd82a7e996db0bfb2cdc599d5dea3ada04f51aa
- **短哈希**：3cd82a7e9
- **日期**：2024-08-13 12:58:48 +0200
- **作者**：gaborkaszab
- **提交说明**：Docs, Infra: Mount local versioned doc branch for testing (#10838)
- **PR/Issue**：#10838

## 总体目的

Iceberg 网站文档构建流程中，版本化文档（versioned docs）默认从远程的 `iceberg_docs` 仓库拉取。开发者在修改历史版本化文档时，需要先把改动推到远程仓库才能在本地构建测试，这导致测试本地修改的反馈循环非常低效。本提交的目的就是为本地文档构建增加一种机制，允许开发者把本地 git 分支挂载为版本化文档的来源，从而无需推送到远程就能在本地预览和测试修改。

需要注意的是，`nightly` 版本的文档本身已经是 `docs/` 文件夹的软链接，因此只有历史版本化文档存在这一痛点。本提交针对的就是历史版本化文档（以及 javadoc）的本地测试场景。

通过环境变量控制挂载来源，本方案既保留了默认从远程拉取的行为，又为开发者提供了灵活的本地测试入口，降低了贡献文档修改的门槛。

## 如何达成设计目的

实现思路很简单：在拉取版本化文档的脚本中，引入两个可选的环境变量 `ICEBERG_VERSIONED_DOCS_BRANCH` 和 `ICEBERG_VERSIONED_JAVADOC_BRANCH`，分别用于指定文档和 javadoc 的本地 git 分支。如果未设置则回退到原来的远程分支（`${REMOTE}/docs`、`${REMOTE}/javadoc`）。同时更新 `site/README.md`，向开发者说明如何使用这两个环境变量。

## 修改详情

### `site/README.md` (+10/-0 lines)

**修改目的**：补充文档，向开发者说明如何在本地构建时挂载本地版本化文档分支。

**工作逻辑**：
在 "Testing local changes on versioned docs" 小节中说明：默认情况下版本化文档从上游远程仓库 `iceberg_docs` 挂载，例外是 `nightly` 版本（指向本地 `docs/`）。如果开发者在本地 git 分支中修改了历史版本化文档，可以通过设置环境变量 `ICEBERG_VERSIONED_DOCS_BRANCH`（对应 `docs/`）和 `ICEBERG_VERSIONED_JAVADOC_BRANCH`（对应 `javadoc/`）来挂载本地分支代替远程分支。

### `site/dev/common.sh` (+9/-3 lines)

**修改目的**：在 `pull_versioned_docs` 函数中实现根据环境变量选择挂载来源的逻辑。

**工作逻辑**：
原代码直接使用远程分支构造 worktree：

```bash
git worktree add -f docs/docs "${REMOTE}/docs"
git worktree add -f docs/javadoc "${REMOTE}/javadoc"
```

修改后通过环境变量并提供默认值：

```bash
local docs_branch="${ICEBERG_VERSIONED_DOCS_BRANCH:-${REMOTE}/docs}"
local javadoc_branch="${ICEBERG_VERSIONED_JAVADOC_BRANCH:-${REMOTE}/javadoc}"
git worktree add -f docs/docs "${docs_branch}"
git worktree add -f docs/javadoc "${javadoc_branch}"
```

利用 bash 参数展开 `${VAR:-default}` 语法，在变量未设置或为空时回退到默认的远程分支，从而保持原有行为不变；当开发者设置了本地分支时则使用本地分支构造 worktree。

## 总结

这是一个面向开发者体验的小型基础设施改进，通过两个环境变量让本地版本化文档的修改可以即时挂载测试，无需先推到远程仓库。改动量小、风险低，默认行为保持不变，只在显式设置环境变量时生效，对现有构建流程没有破坏性影响。
