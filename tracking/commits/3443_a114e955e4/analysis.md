# 提交 3443：Infra: Remove GitHub Actions updates from dependabot config (#15711)

## 提交信息

- **序号**：3443 / 4088
- **哈希**：a114e955e4f16d9b5636e675d9c59be92f5ab978
- **短哈希**：a114e955e4
- **日期**：2026-03-23 10:17:45 +0100
- **作者**：Kevin Liu
- **提交说明**：Infra: Remove GitHub Actions updates from dependabot config (#15711)
- **PR/Issue**：#15711

## 总体目的

从 Dependabot 配置中移除 GitHub Actions 的自动更新规则。这是 CI/CD 安全加固的一部分，因为后续提交（如 #15707）将 GitHub Actions 的第三方 action 引用从版本标签改为固定的 commit SHA，Dependabot 对 GitHub Actions 的自动更新在这种模式下不再需要。

Apache 项目对 CI/CD 安全有严格要求，将 action 固定到特定 SHA 后，不再需要 Dependabot 自动升级 GitHub Actions 版本。

## 如何达成设计目的

- 从 `.github/dependabot.yml` 中删除 `github-actions` 生态系统的更新配置块
- 保留 `gradle` 生态系统的更新配置

## 修改详情

### `.github/dependabot.yml` (+0/-5 lines)

**修改目的**：移除 GitHub Actions 的 Dependabot 自动更新配置。

**工作逻辑**：
- 删除以下配置块：
  ```yaml
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "sunday"
  ```
- 该配置原用于每周自动检查 GitHub Actions 的版本更新
- 保留 `gradle` 生态系统的 Dependabot 配置不变

## 总结

该提交从 Dependabot 配置中移除了 GitHub Actions 自动更新规则。这与后续将 GitHub Actions 固定到 commit SHA 的安全加固措施配合，确保 CI/CD 流水线中使用的第三方 action 不会被自动升级，需要经过人工审查和批准才能更新。
