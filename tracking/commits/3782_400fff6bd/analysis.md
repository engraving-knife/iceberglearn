# 提交 3782：Build: Bump zizmorcore/zizmor-action from 0.5.3 to 0.5.6 (#16550)

## 提交信息

- **序号**：3782 / 4088
- **哈希**：400fff6bd689120b4aa92ad9ff4369933b429cf1
- **短哈希**：400fff6bd
- **日期**：2026-05-24 10:30:42 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump zizmorcore/zizmor-action from 0.5.3 to 0.5.6 (#16550)
- **PR/Issue**：#16550

## 总体目的

这是 Dependabot 自动生成的 GitHub Action 依赖升级提交，将 `zizmorcore/zizmor-action` 从 0.5.3 升级到 0.5.6。Zizmor 是一个 GitHub Actions 安全扫描工具，用于检测工作流中的安全漏洞和最佳实践问题。

## 如何达成设计目的

更新 zizmor 工作流中引用的 Action 版本。

## 修改详情

### `.github/workflows/zizmor.yml` (+1/-1 lines)

**修改目的**：升级 Zizmor 安全扫描 Action 版本。

**工作逻辑**：将 `uses: zizmorcore/zizmor-action@b1d7e1fb... # v0.5.3` 更新为 `@5f14fd08... # v0.5.6`，升级 3 个 patch 版本。

## 总结

常规的 CI 安全工具依赖维护提交，将 Zizmor GitHub Action 安全扫描工具从 0.5.3 升级到 0.5.6，获取最新的安全检测规则和改进。
