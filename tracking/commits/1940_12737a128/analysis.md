# 提交 1940：Build: Bump datamodel-code-generator from 0.28.4 to 0.28.5 (#12683)

## 提交信息

- **序号**：1940 / 4088
- **哈希**：12737a1282d2e82432c0e5924b3fe33dc4ffb109
- **短哈希**：12737a128
- **日期**：2025-03-31 18:59:03 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.28.4 to 0.28.5 (#12683)
- **PR/Issue**：#12683

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 `datamodel-code-generator` 从 0.28.4 升级到 0.28.5。`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI 规范生成 Pydantic 数据模型代码。在 Iceberg 项目中，它被用于 `open-api` 模块，根据 Iceberg REST Catalog 的 OpenAPI 规范生成 Python 客户端的数据模型类。

从 0.28.4 到 0.28.5 是一个 semver-patch（补丁版本）升级，通常只包含 bug 修复与小幅改进，不引入破坏性变更。升级目的是保持代码生成工具处于最新补丁版本，获取稳定性与兼容性改进。

## 如何达成设计目的

在 `open-api/requirements.txt` 中将 `datamodel-code-generator` 的版本固定从 `0.28.4` 修改为 `0.28.5`，pip 安装时会按此版本拉取。

## 修改详情

### `open-api/requirements.txt` (修改, +1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：将 `datamodel-code-generator==0.28.4` 修改为 `datamodel-code-generator==0.28.5`。该文件固定 open-api 模块代码生成所用的 Python 依赖版本。

## 总结

本次提交为 Dependabot 自动执行的 Python 依赖升级（datamodel-code-generator 0.28.4 → 0.28.5），仅修改 `open-api/requirements.txt` 中的版本固定，用于 Iceberg REST OpenAPI 规范的 Python 模型代码生成。属于例行的依赖维护。
