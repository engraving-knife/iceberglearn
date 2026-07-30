# 提交 3261：infra: remove explicit GITHUB_TOKEN export from labeler workflow (#15335)

## 提交信息

- **序号**：3261 / 4088
- **哈希**：c214f2ed51ba41b11e2b945844ef51804950df97
- **短哈希**：c214f2ed5
- **日期**：2026-02-16
- **作者**：Kevin Liu
- **提交说明**：infra: remove explicit GITHUB_TOKEN export from labeler workflow (#15335)
- **PR/Issue**：#15335

## 总体目的

Iceberg 仓库的 `.github/workflows/labeler.yml` 工作流用于在 Pull Request 上自动打标签（triage labels）。该工作流使用 `on: pull_request_target` 作为触发器——这是一个需要特别谨慎对待的事件：与普通的 `pull_request` 不同，`pull_request_target` 会在目标分支（base branch）的上下文中运行，并能够访问仓库的机密（secrets），其设计初衷是让工作流能安全地处理来自 fork 的 PR。然而正因为其能访问机密，若工作流中显式地将机密 token 传递给第三方 action，便存在被恶意 PR 构造利用的安全风险，这也是 [Apache 基础设施 GitHub Actions 政策](https://infra.apache.org/github-actions-policy.html) 所明确禁止的——该政策禁止在 `pull_request_target` 触发的工作流中导出机密 token。

原先 labeler 工作流在 `actions/labeler@v6` 步骤中显式设置了 `repo-token: "${{ secrets.GITHUB_TOKEN }}"`，把仓库的 `GITHUB_TOKEN` 机密作为输入显式传递给 labeler action。这一写法虽功能正常，但违反了上述 Apache 政策中关于不得在 `pull_request_target` 工作流中导出机密 token 的规定，可能在 Apache 基础设施的安全审计中被标记为不合规。

本次提交移除了这一显式的 `repo-token` 参数。由于 `actions/labeler@v6` 在未提供 `repo-token` 时会自动使用运行环境内置的 `github.token`（其值与 `secrets.GITHUB_TOKEN` 相同，但由 runner 上下文隐式提供而非通过机密引用显式传递），因此标签功能行为完全不变，同时工作流不再出现显式机密导出，满足 Apache 合规要求。工作流本身的 `permissions` 已被限制为最小权限（`contents: read`、`pull-requests: write`），移除显式 token 后安全性进一步收敛。该改动与 apache/iceberg-go 仓库的同类修复（PR #730）相关，属于 Apache Iceberg 系列仓库统一的安全合规整改。

## 如何达成设计目的

仅修改 `.github/workflows/labeler.yml`，删除 `actions/labeler@v6` 步骤 `with` 块中的 `repo-token: "${{ secrets.GITHUB_TOKEN }}"` 一行，保留 `sync-labels: true`。依赖 labeler action 的默认行为（自动使用 `github.token`）来维持原有标签同步功能，从而在不改变运行效果的前提下消除显式机密导出。

## 修改详情

### `.github/workflows/labeler.yml` (+0/-1 lines)

**修改目的**：移除 labeler 工作流中对 `secrets.GITHUB_TOKEN` 的显式传递，以符合 Apache 基础设施关于 `pull_request_target` 工作流不得导出机密 token 的政策。

**工作逻辑**：
该工作流以 `on: pull_request_target` 触发，`permissions` 限定为 `contents: read` 与 `pull-requests: write`。原 `actions/labeler@v6` 步骤的 `with` 块包含两项：`repo-token: "${{ secrets.GITHUB_TOKEN }}"` 与 `sync-labels: true`。删除 `repo-token` 一行后，`with` 块仅剩 `sync-labels: true`。

`actions/labeler@v6` 的 `repo-token` 输入参数在未显式提供时默认取 `github.token`（即运行上下文自动注入的令牌），其权限受工作流 `permissions` 块约束。因此删除后 labeler 仍能正常为 PR 添加/同步标签，行为与原先一致。关键区别在于：原先通过 `${{ secrets.GITHUB_TOKEN }}` 显式引用机密并将其作为输入"导出"给 action，属于政策禁止的情形；改用默认值后，令牌由 runner 在 action 内部隐式获取，不再构成显式机密导出，从而满足合规要求。`sync-labels: true` 保留，确保标签会根据当前 `labeler.yml` 配置与 PR 状态同步（移除不再匹配的标签、添加新匹配的标签）。

## 总结

本次提交从 labeler 工作流中移除了对 `secrets.GITHUB_TOKEN` 的显式 `repo-token` 传递，使该 `pull_request_target` 触发的工作流符合 Apache 基础设施禁止在此类工作流中导出机密 token 的安全政策。由于 `actions/labeler@v6` 默认自动使用 `github.token`，标签功能行为不变。这是一次以合规与安全收敛为目的的基础设施整改，与 Iceberg 系列仓库的同类修复保持一致。
