# 提交 3473：CI: Add back Dependabot for GitHub Actions (#15801)

## 提交信息

- **序号**：3473 / 4088
- **哈希**：2b212578d45572176bc8b0e501a2e41495a79aef
- **短哈希**：2b212578d4
- **日期**：2026-03-27 16:48:20 -0700
- **作者**：Kevin Liu
- **提交说明**：CI: Add back Dependabot for GitHub Actions (#15801)
- **PR/Issue**：#15801

## 总体目的

重新为 GitHub Actions 添加 Dependabot 自动更新配置。在提交 #15711（提交 3443）中，Dependabot 的 GitHub Actions 更新被移除，因为当时将 action 从版本标签改为固定 commit SHA。现在经过安全加固（添加 ASF 允许列表检查、zizmor 扫描等），可以安全地重新启用 Dependabot for GitHub Actions，并配合 7 天冷却时间使用。

## 如何达成设计目的

- 在 `dependabot.yml` 中重新添加 `github-actions` 生态系统配置
- 包含 7 天冷却时间（与提交 #15796 一致）
- 每周日在 main 分支上检查更新

## 修改详情

### `.github/dependabot.yml` (+7/-0 lines)

**修改目的**：重新添加 GitHub Actions 的 Dependabot 配置。

**工作逻辑**：
```yaml
- package-ecosystem: "github-actions"
  directory: "/"
  schedule:
    interval: "weekly"
    day: "sunday"
  cooldown:
    default-days: 7
```

- 每周日检查 GitHub Actions 更新
- 7 天冷却时间确保新版本经过社区验证
- 与之前移除的配置相比，增加了冷却时间安全措施

## 总结

该提交在经过一系列 CI/CD 安全加固（commit SHA 固定、zizmor 扫描、ASF 允许列表检查）后，重新为 GitHub Actions 启用了 Dependabot 自动更新。配合 7 天冷却时间，确保新版本经过社区验证后再被采用。
