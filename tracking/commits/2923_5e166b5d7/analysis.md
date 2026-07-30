# 提交 2923：Build: Bump actions/setup-python from 5 to 6 (#14690)

## 提交信息

- **序号**：2923 / 4088
- **哈希**：5e166b5d77fe6021aba4954c72273db17af0d041
- **短哈希**：5e166b5d7
- **日期**：2025-11-25 23:34:34 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump actions/setup-python from 5 to 6
- **PR/Issue**：#14690

## 总体目的

这是 dependabot 自动生成的 GitHub Actions 版本升级提交。`actions/setup-python` 是 GitHub 官方的 Python 环境配置 Action，用于在 CI 中设置指定版本的 Python。Iceberg 项目在 OpenAPI 验证和文档站点构建的 CI workflow 中使用此 Action。此次从 v5 升级到 v6，属于主版本升级。

## 如何达成设计目的

通过修改使用 setup-python Action 的两个 workflow 文件，将引用版本从 v5 更新为 v6。

## 修改详情

### `.github/workflows/open-api.yml` (+1/-1 lines)

**修改目的**：将 OpenAPI 验证 workflow 中的 setup-python 从 v5 升级到 v6。

### `.github/workflows/site-ci.yml` (+1/-1 lines)

**修改目的**：将文档站点构建 workflow 中的 setup-python 从 v5 升级到 v6。

**工作逻辑**：两处均将 `actions/setup-python@v5` 修改为 `actions/setup-python@v6`，python-version 配置不变（分别为 3.9 和 3.x）。

## 总结

本提交是 GitHub Actions setup-python 的主版本升级（v5 到 v6），影响 OpenAPI 验证和文档构建两个 CI workflow。主版本升级可能包含行为变更，需关注 setup-python v6 的迁移指南。
