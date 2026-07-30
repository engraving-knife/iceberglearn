# 提交 0249：Build: Bump actions/labeler from 4 to 5 (#9264)

## 提交信息

- **序号**：0249 / 4088
- **哈希**：1b80537e821b7bf901a5b221fc3bfe53c8d87794
- **短哈希**：1b80537e8
- **日期**：2023-12-10 11:01:19 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/labeler from 4 to 5 (#9264)
- **PR/Issue**：#9264

## 总体目的

这是 GitHub Dependabot 自动生成的依赖升级提交，把仓库 CI 工作流里使用的 `actions/labeler` 从 v4 升到 v5（semver 主版本升级）。

`actions/labeler` 是 GitHub 官方提供的 action，用于在 PR 上根据 `pull_request_target` 事件以及仓库根目录的 labeler 配置文件（按路径模式匹配规则）自动给 PR 打标签。在 Iceberg 仓库里，它由 `.github/workflows/labeler.yml` 触发，使用 `sync-labels: true` 模式（既添加也移除不再匹配的 label），用 `GITHUB_TOKEN` 鉴权，权限为 `pull-requests: write` / `contents: read`。这个 action 的可用性关系到 PR 分类、triage 流程能否自动运转。

v4 → v5 是主版本升级，上游通常会有破坏性变化（如 labeler v5 调整了配置文件 schema、配置写法等）。Dependabot 把它归类为 `version-update:semver-major`、`direct:production` 依赖。升级后让仓库跟上 labeler 的最新维护与安全修复，避免 v4 未来停止维护。属于仓库工程卫生的常规动作，对 Iceberg 代码本身无影响。

## 如何达成设计目的

通过 Dependabot 自动扫描工作流 `uses:` 引用，发现 `actions/labeler@v4` 后发起 PR 把引用改为 `@v5`。本次只改一个工作流文件、一行引用，没有配套代码改动。提交体附上 release notes / commits 对比链接供审查。合入后该 action 在下次 PR 触发 `pull_request_target` 时按 v5 行为执行。

## 修改详情

### `.github/workflows/labeler.yml`

**修改目的**：把 Pull Request Labeler 工作流中对 `actions/labeler` 的引用从 v4 升级到 v5。

**工作逻辑**：`triage` job 的 steps 中，第 31 行由 `- uses: actions/labeler@v4` 改为 `- uses: actions/labeler@v5`，后续 `with: repo-token: "${{ secrets.GITHUB_TOKEN }}"` 与 `sync-labels: true` 等配置保持不变。该 step 在 `pull_request_target` 事件触发时执行，依据仓库 labeler 规则对 PR 进行自动打标/同步标签。

## 小结

本次提交把 PR 自动打标工作流使用的 `actions/labeler` 从 v4 升到 v5，是 Dependabot 例行的 CI 依赖维护，保持 PR triage 自动化与上游最新版本同步。
