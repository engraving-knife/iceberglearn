# 提交 2970：Build: Bump actions/stale from 10.1.0 to 10.1.1 (#14784)

## 提交信息

- **序号**：2970 / 4088
- **哈希**：3b1ec481a564ae8bce608a1044a6156c4f49ede1
- **短哈希**：3b1ec481a
- **日期**：2025-12-06
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/stale from 10.1.0 to 10.1.1 (#14784)
- **PR/Issue**：#14784

## 总体目的

这是 Dependabot 自动生成的依赖升级。`actions/stale` 是 Iceberg 仓库在 GitHub Actions 中使用的第三方 Action，用于自动标记和关闭长期不活跃的 issue 与 PR（仓库中的 `.github/workflows/stale.yml` 即名为 "Close Stale Issues and PRs" 的工作流）。Dependabot 检测到 `actions/stale` 发布了新版本，将引用从 `v10.1.0` 升到 `v10.1.1`。本次升级属于 `semver-patch`（补丁版本）升级，依据语义化版本约定，预期只包含向后兼容的缺陷修复或小改进，不引入破坏性变更，目的是保持 CI 自动化所依赖的 Action 处于最新稳定版，获取 bug 修复与维护性改进。

## 如何达成设计目的

Dependabot 仅修改工作流中对 `actions/stale` 的版本引用，从 `@v10.1.0` 改为 `@v10.1.1`，工作流的其余配置（stale 标签、判定天数、豁免标签等）保持不变。

## 修改详情

### `.github/workflows/stale.yml` (+1/-1 lines)

**修改目的**：将 stale 工作流中 `actions/stale` 的版本从 `v10.1.0` 升级到 `v10.1.1`。

**工作逻辑**：
stale 工作流在 `apache` 仓库属主条件下、于 `ubuntu-24.04` 运行，其步骤由 `- uses: actions/stale@v10.1.0` 改为 `- uses: actions/stale@v10.1.1`。后续 `with` 块中的 stale-issue-label、stale-pr-label、判定天数等参数不变。升级后，自动标记/关闭陈旧 issue 与 PR 的行为逻辑保持一致，仅底层 Action 自身获得补丁级修复。

## 总结

本提交是 Dependabot 对 GitHub Action `actions/stale` 的补丁版本升级（10.1.0 → 10.1.1），用于 Iceberg 的"陈旧 issue/PR 自动关闭"工作流。属于低风险的维护性升级，预期不影响现有 stale 行为，仅获取 Action 的补丁修复与稳定性改进。
