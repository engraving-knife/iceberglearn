# 提交 0250：Build: Bump actions/stale from 8.0.0 to 9.0.0 (#9265)

## 提交信息

- **序号**：0250 / 4088
- **哈希**：ec92fa33f780d48aa1f0a146428cd26e1c9bdbcc
- **短哈希**：ec92fa33f
- **日期**：2023-12-10 11:01:33 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/stale from 8.0.0 to 9.0.0 (#9265)
- **PR/Issue**：#9265

## 总体目的

这是 GitHub Dependabot 自动生成的依赖升级提交，把仓库 CI 工作流里使用的 `actions/stale` 从 8.0.0 升到 9.0.0（semver 主版本升级）。

`actions/stale` 是 GitHub 官方提供的 action，用于定时（cron）扫描仓库中的 issue/PR，对长时间无活动的打上 stale 标签并最终关闭。在 Iceberg 仓库里，`.github/workflows/stale.yml` 每日 0 点运行，配置为：issue 开 180 天无活动标记 stale，再过 14 天关闭；PR 不参与（`days-before-pr-stale: -1`）；带 `not-stale` 标签的 issue 豁免；仅当 `github.repository_owner == 'apache'` 时才运行。这个 action 是仓库 issue 生命周期管理（控制积压）的关键自动化。

8.0.0 → 9.0.0 是主版本升级，上游通常有破坏性变化。Dependabot 把它归类为 `version-update:semver-major`、`direct:production` 依赖。升级后让仓库跟上 stale action 的最新维护与安全修复，避免 8.x 未来停止接受补丁。属于仓库工程卫生的常规动作，对 Iceberg 代码本身无影响。

## 如何达成设计目的

通过 Dependabot 自动扫描工作流 `uses:` 引用，发现 `actions/stale@v8.0.0` 后发起 PR 把引用改为 `@v9.0.0`。本次只改一个工作流文件、一行引用，没有配套代码改动。提交体附上 release notes / changelog / commits 对比链接供审查。合入后该 action 在下次每日 cron 触发时按 v9 行为执行。

## 修改详情

### `.github/workflows/stale.yml`

**修改目的**：把 Close Stale Issues 工作流中对 `actions/stale` 的引用从 8.0.0 升级到 9.0.0。

**工作逻辑**：`stale` job 的 steps 中，第 34 行由 `- uses: actions/stale@v8.0.0` 改为 `- uses: actions/stale@v9.0.0`，后续 `with:` 块中所有配置保持不变，包括 `stale-issue-label: 'stale'`、`exempt-issue-labels: 'not-stale'`、`days-before-issue-stale: 180`、`days-before-issue-close: 14`、`days-before-pr-stale: -1` 以及 stale/close 消息文案。这些参数语义在 v9 中保持兼容，因此升级不会改变 stale 管理行为。

## 小结

本次提交把关闭过期 issue 工作流使用的 `actions/stale` 从 8.0.0 升到 9.0.0，是 Dependabot 例行的 CI 依赖维护，保持 issue 生命周期自动化与上游最新版本同步。
