# 提交 4038：Infra: Group github/codeql-action bumps into a single dependabot PR (#17160)

## 提交信息

- **序号**：4038 / 4088
- **哈希**：b6eaa6adfc46157acd4126b060ec44acf31f5f3c
- **短哈希**：b6eaa6adf
- **日期**：2026-07-15 17:37:00 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Infra: Group github/codeql-action bumps into a single dependabot PR (#17160)
- **PR/Issue**：#17160

## 总体目的

这个提交修改了 Dependabot 配置，将 `github/codeql-action` 系列的版本升级合并到一个 PR 中，而不是为每个 codeql-action 子组件分别创建 PR。

问题的根源在于：CodeQL 的 GitHub Actions 工作流包含三个步骤——`init`、`autobuild`、`analyze`，它们都必须使用相同版本的 `github/codeql-action`。此前 Dependabot 会为 codeql-action 的不同组件分别发起独立的升级 PR，如果只合并其中一个 PR 而不合并其他 PR，就会导致工作流中 init/autobuild/analyze 步骤使用不同版本的 codeql-action，从而引发版本不匹配，使 CodeQL Analyze 任务失败。

通过 Dependabot 的 `groups` 功能将这些组件归为一组，Dependabot 会一次性把所有匹配 `github/codeql-action*` 的依赖升级合并到同一个 PR 中，确保它们始终一起升级、一起合并，从根本上消除版本不一致的风险。

## 如何达成设计目的

在 `.github/dependabot.yml` 中针对 `github-actions` 生态系统的配置块新增 `groups` 段，定义一个名为 `codeql-action` 的分组，其 `patterns` 为 `["github/codeql-action*"]`。这样 Dependabot 在检测到 codeql-action 升级时，会把所有匹配该模式的依赖放进同一个 PR。配置位于已有的 sunday 调度 + 7 天 cooldown 的块内，因此组升级同样遵循该调度与冷却策略。

## 修改详情

### `.github/dependabot.yml` (+7/-0 lines)

**修改目的**：将 codeql-action 系列依赖升级合并为单个 PR。

**工作逻辑**：
```yaml
groups:
  # The codeql-action init/autobuild/analyze steps must all run the
  # same version - split PRs cause a version mismatch that fails the
  # Analyze jobs. Group them so a single PR bumps all of them together.
  codeql-action:
    patterns:
      - "github/codeql-action*"
```
该配置块被加入到 `github-actions` 生态系统的 update 条目下（与已有的 `day: "sunday"` 调度和 `cooldown: default-days: 7` 同级）。`patterns` 使用通配符 `github/codeql-action*` 匹配 init、autobuild、analyze 等所有 codeql-action 子 action，使它们被分到名为 `codeql-action` 的组中，由 Dependabot 统一在一个 PR 内升级。

## 总结

这是一个基础设施维护性提交，通过 Dependabot 的分组功能解决了 codeql-action 多组件升级不同步导致 CodeQL 分析任务失败的实际痛点。改动极小（7 行配置），但能显著减少因版本不一致导致的 CI 失败和人工合并协调成本，提升了依赖升级流程的可靠性。
