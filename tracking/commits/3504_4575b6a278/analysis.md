# 提交 3504：Build: Bump astral-sh/setup-uv from 7.6.0 to 8.0.0 (#15888)

## 提交信息

- **序号**：3504 / 4088
- **哈希**：4575b6a278ea7ea2c987fed231545668b7e3a7d2
- **短哈希**：4575b6a278
- **日期**：2026-04-04 21:51:51 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump astral-sh/setup-uv from 7.6.0 to 8.0.0 (#15888)
- **PR/Issue**：#15888

## 总体目的

Dependabot 自动升级 GitHub Action `astral-sh/setup-uv` 从 7.6.0 到 8.0.0（major 版本升级）。该 Action 用于在 CI 中安装 uv（Python 包管理器），用于 OpenAPI 规范验证。

## 如何达成设计目的

更新 `.github/workflows/open-api.yml` 中的 action 引用 commit SHA 和版本注释。

## 修改详情

### `.github/workflows/open-api.yml` (+1/-1 line)

**修改目的**：升级 setup-uv action 版本。

**工作逻辑**：`astral-sh/setup-uv@37802adc94f370d6bfd71619e3f0bf239e1f3b78 # v7.6.0` → `astral-sh/setup-uv@cec208311dfd045dd5311c1add060b2062131d57 # v8.0.0`。更新 commit SHA 和版本注释。

## 总结

Dependabot 自动依赖升级提交，将 astral-sh/setup-uv GitHub Action 从 7.6.0 升级到 8.0.0（major 版本）。该 Action 用于 OpenAPI CI 中的 uv 安装。
