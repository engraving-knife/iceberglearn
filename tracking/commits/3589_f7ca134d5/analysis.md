# 提交 3589：Build: Bump zizmorcore/zizmor-action from 0.5.2 to 0.5.3 (#16122)

## 提交信息

- **序号**：3589 / 4088
- **哈希**：f7ca134d5f75635709640713fb5c8d4962a1e50f
- **短哈希**：f7ca134d5
- **日期**：2026-04-25 23:48:06 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump zizmorcore/zizmor-action from 0.5.2 to 0.5.3 (#16122)
- **PR/Issue**：#16122

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 GitHub Actions 中用于 GitHub Actions 安全扫描的 `zizmorcore/zizmor-action` 从版本 0.5.2 升级到 0.5.3。Zizmor 是一个用于扫描 GitHub Actions 工作流安全漏洞的工具。这是一个 semver-patch（补丁版本）升级。

## 如何达成设计目的

Dependabot 自动扫描工作流文件中引用的 Action 版本，发现新版本后自动创建 PR 升级版本引用（包括 commit SHA 和版本标签）。

## 修改详情

### `.github/workflows/zizmor.yml` (+1/-1 lines)

**修改目的**：更新 zizmor-action 版本引用。

**工作逻辑**：
将 `zizmorcore/zizmor-action` 的引用从 `@71321a20a9ded102f6e9ce5718a2fcec2c4f70d8 # v0.5.2` 更新为 `@b1d7e1fb5de872772f31590499237e7cce841e8e # v0.5.3`。

## 总结

这是一个 CI/CD 依赖维护提交，通过补丁版本升级保持 zizmor 安全扫描 Action 的最新状态。
