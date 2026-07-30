# 提交 3450：ci: pin third-party actions to Apache-approved SHAs (#15707)

## 提交信息

- **序号**：3450 / 4088
- **哈希**：17738b7c5ce25fd73c81da7c4a1ffcc59cae12e5
- **短哈希**：17738b7c5c
- **日期**：2026-03-23 12:02:17 -0700
- **作者**：Kevin Liu
- **提交说明**：ci: pin third-party actions to Apache-approved SHAs (#15707)
- **PR/Issue**：#15707

## 总体目的

将 CI 工作流中使用的第三方 GitHub Actions 从版本标签固定到 Apache 批准的特定 commit SHA。这是 Apache 软件基金会对项目 CI/CD 安全的要求，防止供应链攻击。使用版本标签（如 `@v7`）存在安全风险，因为标签可以被重新指向恶意代码。

## 如何达成设计目的

- 在 `open-api.yml` 工作流中，将 `astral-sh/setup-uv` action 从 `@v7` 改为固定的 commit SHA

## 修改详情

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：将 `astral-sh/setup-uv` action 固定到特定 commit SHA。

**工作逻辑**：
- `astral-sh/setup-uv@v7` → `astral-sh/setup-uv@5a095e7a2014a4212f075830d4f7277575a9d098`
- 该 action 用于安装 `uv` Python 包管理器，在 open-api 工作流中使用

## 总结

该提交将 open-api 工作流中的 `astral-sh/setup-uv` GitHub Action 从版本标签 `@v7` 固定到特定的 commit SHA，符合 Apache 软件基金会对 CI/CD 安全的要求，防止供应链攻击。
