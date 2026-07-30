# 提交 3960：Build: Bump actions/checkout from 6.0.3 to 7.0.0 (#16986)

## 提交信息

- **序号**：3960 / 4088
- **哈希**：45975ec1555b0365e4f2f6bdffa948ff2441711a
- **短哈希**：45975ec15
- **日期**：2026-06-27 23:48:30 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/checkout from 6.0.3 to 7.0.0 (#16986)
- **PR/Issue**：#16986

## 总体目的

这是 Dependabot 自动升级 GitHub Actions 的 `actions/checkout` 从 v6.0.3 到 v7.0.0 的提交。这是一个主版本升级（`version-update:semver-major`），可能包含行为变更。`actions/checkout` 是几乎所有 CI workflow 都使用的代码检出动作。

## 如何达成设计目的

通过批量更新所有 GitHub Actions workflow 文件中的 `actions/checkout` 引用，将 commit SHA 从 `df4cb1c069e1874edd31b4311f1884172cec0e10`（v6.0.3）更新为 `9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0`（v7.0.0）。

## 修改详情

### 多个 GitHub workflow 文件 (`.github/workflows/*.yml`)

**修改目的**：升级 checkout action 版本。

**工作逻辑**：在每个 workflow 文件中，将：
```yaml
- uses: actions/checkout@df4cb1c069e1874edd31b4311f1884172cec0e10 # v6.0.3
```
更新为：
```yaml
- uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0
```

涉及的 workflow 文件包括：api-binary-compatibility、asf-allowlist-check、codeql、cve-scan、delta-conversion-ci、docs-ci 等多个 CI 配置文件。

## 总结

常规 CI 工具链升级，将 actions/checkout 从 v6.0.3 升级到 v7.0.0。虽然是主版本升级，但 checkout action 通常保持向后兼容，影响范围仅限 CI 环境。
