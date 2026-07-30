# 提交 2934：Use non-deprecated del_branch_on_merge (#14710)

## 提交信息

- **序号**：2934 / 4088
- **哈希**：52c176df681cd6381ccf4d7f58ef1815ef5db19e
- **短哈希**：52c176df6
- **日期**：2025-11-29 09:13:21 -0800
- **作者**：Kevin Liu
- **提交说明**：Use non-deprecated del_branch_on_merge (#14710)
- **PR/Issue**：#14710

## 总体目的

Apache Iceberg 仓库根目录的 `.asf.yaml` 是 ASF 仓库治理配置文件，其中 `github:` 段用于声明 GitHub 仓库设置（合并策略、分支保护、特性开关等）。该文件中有一项 `del_branch_on_merge: true` 配置，作用是在 PR 合并后自动删除源分支（head branch），保持仓库分支整洁。

在此次改动前，`del_branch_on_merge` 被直接写在 `github:` 段的根层级下（与 `protected_branches`、`features` 等同级）。ASF 的 `.asf.yaml` schema 演进后，`github.del_branch_on_merge` 这一顶层位置被标记为弃用，分支删除配置应改放在新增的 `github.pull_requests` 子段下（即 `github.pull_requests.del_branch_on_merge`）。继续使用旧位置会触发弃用告警，且未来可能不再被 ASF infra bot 识别。

本提交将 `del_branch_on_merge: true` 从 `github:` 根层级迁移到非弃用的 `github.pull_requests:` 子段下，并添加注释说明用途，从而消除弃用告警并确保配置在未来的 schema 中继续生效。

## 如何达成设计目的

在 `.asf.yaml` 的 `github:` 段中新增一个 `pull_requests:` 子段，其中放置 `del_branch_on_merge: true` 及说明注释；同时删除原先位于 `github:` 根层级（`protected_branches` 之后、`features` 之前）的 `del_branch_on_merge: true` 行及其上方空行。改动为净增 4 行、净删 2 行（含结构调整的空行），配置语义不变。

## 修改详情

### `.asf.yaml` (+4/-2 lines)

**修改目的**：将 `del_branch_on_merge` 配置从弃用的 `github:` 根层级迁移到非弃用的 `github.pull_requests:` 子段。

**工作逻辑**：

改动后的结构如下：

```yaml
github:
  ...
  squash: true
  rebase: true

  pull_requests:
    # auto-delete head branches after being merged
    del_branch_on_merge: true

  protected_branches:
    main:
      required_pull_request_reviews:
        required_approving_review_count: 1

      required_linear_history: true

  features:
    wiki: true
```

原先 `del_branch_on_merge: true` 位于 `required_linear_history: true` 之后、`features` 之前（即 `github:` 的直接子键）。改动将其上移到 `squash/rebase` 之后、`protected_branches` 之前，并包裹在新的 `pull_requests:` 子段中，附注释 `# auto-delete head branches after being merged`。`pull_requests` 子段是 ASF `.asf.yaml` schema 中用于 PR 相关设置的非弃用位置，`del_branch_on_merge` 迁入后语义与原先一致——PR 合并后自动删除 head 分支。

## 总结

该提交将 `.asf.yaml` 中 `del_branch_on_merge: true` 配置从弃用的 `github:` 根层级迁移到非弃用的 `github.pull_requests:` 子段并补充注释，消除 ASF schema 弃用告警，确保 PR 合并后自动删除源分支的行为在当前与未来 schema 下持续生效。
