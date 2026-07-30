# 提交 3985：Build: Bump actions/setup-python from 6.2.0 to 6.3.0 (#17105)

## 提交信息

- **序号**：3985 / 4088
- **哈希**：303a538b242256f5703c1b99701b503d5e8869d8
- **短哈希**：303a538b2
- **日期**：2026-07-06 10:21:53 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/setup-python from 6.2.0 to 6.3.0 (#17105)
- **PR/Issue**：#17105

## 总体目的

Dependabot 自动升级 GitHub Actions 的 `actions/setup-python` 从 v6.2.0 到 v6.3.0，次版本升级。`actions/setup-python` 用于在 CI 中配置 Python 环境，主要用于文档构建和 OpenAPI 验证。

## 如何达成设计目的

批量更新相关 workflow 文件中的 `actions/setup-python` 引用。

## 修改详情

### `.github/workflows/docs-ci.yml` 和 `.github/workflows/site-ci.yml` 等

**修改目的**：升级 setup-python action 版本。

**工作逻辑**：将 `actions/setup-python@a309ff8b426b58ec0e2a45f0f869d46889d02405 # v6.2.0` 更新为 `actions/setup-python@ece7cb06caefa5fff74198d8649806c4678c61a1 # v6.3.0`。

## 总结

常规 CI 工具链升级，将 actions/setup-python 从 v6.2.0 升级到 v6.3.0。
