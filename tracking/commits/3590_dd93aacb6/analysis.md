# 提交 3590：Build: Bump astral-sh/setup-uv from 8.0.0 to 8.1.0 (#16121)

## 提交信息

- **序号**：3590 / 4088
- **哈希**：dd93aacb6bc5a7b329e16c3e9a102fb56ac8638e
- **短哈希**：dd93aacb6
- **日期**：2026-04-25 23:48:21 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump astral-sh/setup-uv from 8.0.0 to 8.1.0 (#16121)
- **PR/Issue**：#16121

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 GitHub Actions 中用于安装 uv（Python 包管理器）的 `astral-sh/setup-uv` Action 从版本 8.0.0 升级到 8.1.0。该 Action 用于 Iceberg 的 OpenAPI 测试工作流中安装 uv 以管理 Python 依赖。这是一个 semver-minor（次版本）升级。

## 如何达成设计目的

Dependabot 自动扫描工作流文件中引用的 Action 版本，发现新版本后自动创建 PR 升级版本引用。

## 修改详情

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：更新 setup-uv Action 版本引用。

**工作逻辑**：
将 `astral-sh/setup-uv` 的引用从 `@cec208311dfd045dd5311c1add060b2062131d57 # v8.0.0` 更新为 `@08807647e7069bb48b6ef5acd8ec9567f424441b # v8.1.0`。

## 总结

这是一个 CI/CD 依赖维护提交，通过次版本升级保持 uv 安装 Action 的最新状态。
