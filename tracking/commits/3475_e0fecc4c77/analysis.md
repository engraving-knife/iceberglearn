# 提交 3475：Build: Bump astral-sh/setup-uv from 7.3.1 to 7.6.0 (#15803)

## 提交信息

- **序号**：3475 / 4088
- **哈希**：e0fecc4c77e6df87198983f6e5db641cbf84e2e0
- **短哈希**：e0fecc4c77
- **日期**：2026-03-27 16:58:23 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump astral-sh/setup-uv from 7.3.1 to 7.6.0 (#15803)
- **PR/Issue**：#15803

## 总体目的

这是一个由 Dependabot 自动生成的 GitHub Action 版本升级提交。将 `astral-sh/setup-uv` action 从 commit SHA `5a095e7a...`（对应版本 7.3.1）升级到 commit SHA `37802ad...`（对应版本 7.6.0）。

`astral-sh/setup-uv` 用于在 CI 中安装 `uv` Python 包管理器，在 open-api 工作流中使用。这是在提交 #15707 中将 action 从版本标签固定到 commit SHA 后，Dependabot 重新启用 GitHub Actions 更新（提交 #15801）后首次更新的 action 之一。

## 如何达成设计目的

- Dependabot 检测到 `astral-sh/setup-uv` 有新版本
- 在 `open-api.yml` 工作流中更新 commit SHA 引用
- 新版本 7.6.0 对应 commit `37802adc94f370d6bfd71619e3f0bf239e1f3b78`

## 修改详情

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：升级 astral-sh/setup-uv action 的 commit SHA。

**工作逻辑**：
- `astral-sh/setup-uv@5a095e7a2014a4212f075830d4f7277575a9d098` → `astral-sh/setup-uv@37802adc94f370d6bfd71619e3f0bf239e1f3b78`
- 对应版本从 7.3.1 升级到 7.6.0
- 这是次版本升级（7.3 → 7.6），可能包含新功能和改进

## 总结

该提交是 Dependabot 自动生成的 GitHub Action 升级，将 `astral-sh/setup-uv` 从 7.3.1 升级到 7.6.0。这是重新启用 Dependabot for GitHub Actions 后的首次 action 升级，通过更新 commit SHA 实现，保持了安全固定策略。
