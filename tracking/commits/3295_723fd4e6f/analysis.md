# 提交 3295：Build: Bump actions/stale from 10.1.1 to 10.2.0 (#15398)

## 提交信息

- **序号**：3295 / 4088
- **哈希**：723fd4e6f0b27563a8bb0f3a2c044172e05facb3
- **短哈希**：723fd4e6f
- **日期**：2026-02-21
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/stale from 10.1.1 to 10.2.0 (#15398)
- **PR/Issue**：#15398

## 总体目的

这是一次由 Dependabot 自动发起的 GitHub Actions 依赖升级。被升级的 `actions/stale` 是 GitHub 官方提供的"过期议题/PR 自动管理"动作，Iceberg 仓库在 `.github/workflows/stale.yml` 中使用它来定期标记并关闭长期无活动的 issue，以维护议题队列的健康度。

具体到本仓库的用法：`stale.yml` 工作流通过 cron `0 0 * * *` 每天定时运行（仅当 `github.repository_owner == 'apache'` 时执行），调用 `actions/stale` 将 180 天无活动的 issue 打上 `stale` 标签并留言提醒，若再过 14 天仍无活动则自动关闭；通过 `days-before-pr-stale: -1` 配置明确只处理 issue、不触碰 PR；带有 `not-stale` 标签的 issue 被豁免。该工作流是 Apache Iceberg 社区管理议题积压的自动化手段。

本次升级将 `actions/stale` 从 `v10.1.1` 升到 `v10.2.0`，属于语义化版本的 minor 升级（`version-update:semver-minor`，依赖类型为 `direct:production`）。按 Dependabot 的元数据描述，这是一个次版本更新，预期包含向后兼容的新特性与缺陷修复，不会破坏现有工作流配置（`with` 参数与运行逻辑保持不变）。升级的动机是跟进上游动作的常规维护，获取最新修复与改进，避免使用过时版本。

## 如何达成设计目的

Dependabot 直接修改 `.github/workflows/stale.yml` 中 `uses: actions/stale@v10.1.1` 这一行，将其改为 `@v10.2.0`，工作流的其余配置（标签、天数、消息、豁免规则等）完全不变。这是 Dependabot 对 GitHub Actions 依赖的标准升级方式——通过钉住的版本标签引用新版本，由 Action 运行时拉取对应发行版。

## 修改详情

### `.github/workflows/stale.yml` (+1/-1 lines)

**修改目的**：升级 `actions/stale` 动作到 10.2.0。

**工作逻辑**：
将 `stale` job 的第一步 `uses: actions/stale@v10.1.1` 改为 `uses: actions/stale@v10.2.0`。该步骤后续的 `with` 配置（`stale-issue-label: 'stale'`、`exempt-issue-labels: 'not-stale'`、`days-before-issue-stale: 180`、`days-before-issue-close: 14`、`days-before-pr-stale: -1`、`stale-issue-message`、`close-issue-message` 等）保持不变，因此 stale 行为（180 天标记、14 天关闭、仅 issue、豁免 not-stale）在升级后完全一致，仅受益于 10.2.0 带来的上游改进。运行环境 `runs-on: ubuntu-24.04` 与权限 `permissions.issues: write` 也未改动。

## 总结

本提交是 Dependabot 对 Iceberg 仓库"过期议题自动管理"工作流所用的 `actions/stale` GitHub Action 进行的一次例行 minor 版本升级（10.1.1 → 10.2.0），改动仅一行版本引用，工作流行为与配置不变，属于保持 CI/自动化依赖最新、获取上游兼容性改进的常规维护，风险极低。
