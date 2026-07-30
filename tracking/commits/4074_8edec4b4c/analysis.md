# 提交 4074：Build: Bump actions/stale from 10.3.0 to 10.4.0 (#17295)

## 提交信息

- **序号**：4074 / 4088
- **哈希**：8edec4b4c6357fc20dd197c248b3ffee01899e61
- **短哈希**：8edec4b4c
- **日期**：2026-07-19 09:31:21 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/stale from 10.3.0 to 10.4.0 (#17295)
- **PR/Issue**：#17295

## 总体目的

Dependabot 自动升级提交，将 `actions/stale` 从 10.3.0 升级到 10.4.0（semver minor 版本升级）。`actions/stale` 是用于自动标记和关闭不活跃的 issue/PR 的 GitHub Action，Iceberg 在 `stale.yml` 工作流中使用它管理仓库中的陈旧 issue 和 PR（如标记为 stale 后一段时间无活动则关闭）。minor 版本升级包含新功能和向后兼容的改进。

## 如何达成设计目的

更新工作流中 `uses` 引用的 SHA 和版本注释，从 `eb5cf3af3ac0a1aa4c9c45633dd1ae542a27a899`（v10.3.0）改为 `1e223db275d687790206a7acac4d1a11bd6fe629`（v10.4.0）。

## 修改详情

### `.github/workflows/stale.yml` (+1/-1 lines)

**修改目的**：升级 stale action 到 10.4.0。

**工作逻辑**：
```yaml
- uses: actions/stale@1e223db275d687790206a7acac4d1a11bd6fe629 # v10.4.0
```
将 SHA 和版本注释从 v10.3.0 更新为 v10.4.0。

## 总结

常规的 CI 工具链维护升级，将陈旧 issue/PR 管理 action 升级到 10.4.0 minor 版本。minor 级别升级风险较低。
