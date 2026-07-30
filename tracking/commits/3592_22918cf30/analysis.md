# 提交 3592：Build: Bump github/codeql-action from 4.35.1 to 4.35.2 (#16118)

## 提交信息

- **序号**：3592 / 4088
- **哈希**：22918cf30ce51c54032f55fa5989bda53a5870b8
- **短哈希**：22918cf30
- **日期**：2026-04-25 23:49:02 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump github/codeql-action from 4.35.1 to 4.35.2 (#16118)
- **PR/Issue**：#16118

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 GitHub Actions 中用于代码安全分析的 `github/codeql-action` 从版本 4.35.1 升级到 4.35.2。CodeQL Action 用于 Iceberg 项目的代码安全扫描工作流中，检测代码中的安全漏洞。这是一个 semver-patch（补丁版本）升级。

## 如何达成设计目的

Dependabot 自动扫描工作流文件中引用的 Action 版本，发现新版本后自动创建 PR 升级所有引用位置（init 和 analyze 两处）。

## 修改详情

### `.github/workflows/codeql.yml` (+2/-2 lines)

**修改目的**：更新 CodeQL Action 版本引用。

**工作逻辑**：
将 `github/codeql-action/init` 和 `github/codeql-action/analyze` 两处引用从 `@c10b8064de6f491fea524254123dbe5e09572f13 # v4.35.1` 更新为 `@95e58e9a2cdfd71adc6e0353d5c52f41a045d225 # v4.35.2`。

## 总结

这是一个 CI/CD 依赖维护提交，通过补丁版本升级保持 CodeQL 安全分析 Action 的最新状态。
