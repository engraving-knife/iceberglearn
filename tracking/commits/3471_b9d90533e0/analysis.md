# 提交 3471：ci: add cooldown to dependabot (#15796)

## 提交信息

- **序号**：3471 / 4088
- **哈希**：b9d90533e0e8b376d1fe57a0f25fe30be9d281bf
- **短哈希**：b9d90533e0
- **日期**：2026-03-27 14:31:25 -0700
- **作者**：Kevin Liu
- **提交说明**：ci: add cooldown to dependabot (#15796)
- **PR/Issue**：#15796

## 总体目的

为 Dependabot 添加冷却时间（cooldown）配置。冷却时间使 Dependabot 在依赖发布新版本后等待指定天数再创建升级 PR，避免频繁升级刚发布可能存在问题的版本。这是一种最佳实践，让新版本在社区中经过一定时间的验证后再被采用。

## 如何达成设计目的

- 在 `dependabot.yml` 中为 gradle 和 github-actions 生态系统添加 `cooldown.default-days: 7`
- 这意味着新版本发布后 7 天内 Dependabot 不会创建升级 PR

## 修改详情

### `.github/dependabot.yml` (+4/-0 lines)

**修改目的**：为 Dependabot 添加 7 天冷却时间。

**工作逻辑**：
- 在 gradle 生态系统配置中添加：
  ```yaml
  cooldown:
    default-days: 7
  ```
- 在 github-actions 生态系统配置中添加相同的冷却配置
- 效果：新版本发布后 7 天内不会触发 Dependabot 升级 PR

## 总结

该提交为 Dependabot 的 gradle 和 github-actions 生态系统配置添加了 7 天的默认冷却时间。这使得新版本发布后有一段缓冲期，让社区验证新版本的稳定性后再自动创建升级 PR，减少因新版本 bug 导致的构建失败。
