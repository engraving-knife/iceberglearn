# 提交 3721：Build: Bump datamodel-code-generator from 0.56.1 to 0.57.0 (#16373)

## 提交信息

- **序号**：3721 / 4088
- **哈希**：c8b35af9b271d76875934eb3a26be29a110a9c17
- **短哈希**：c8b35af9b
- **日期**：2026-05-16 23:16:51 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.56.1 to 0.57.0 (#16373)
- **PR/Issue**：#16373

## 总体目的

Dependabot 自动发起的依赖升级，将 `datamodel-code-generator` 从 0.56.1 升级到 0.57.0。该工具用于根据 OpenAPI 规范自动生成 Pydantic 数据模型代码，Iceberg 项目在 `open-api/` 目录使用它来基于 REST Catalog 的 OpenAPI 规范生成对应的 Python 模型类。本次为 minor 版本升级（0.56.1 → 0.57.0），可能包含新功能、改进和 bug 修复。

## 如何达成设计目的

Dependabot 修改 `open-api/requirements.txt` 中 `datamodel-code-generator` 的版本固定值完成升级。依赖类型为 `direct:production`，更新类型为 `version-update:semver-minor`。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：
将 `datamodel-code-generator==0.56.1` 修改为 `datamodel-code-generator==0.57.0`，使 OpenAPI 代码生成流程使用最新版本。

## 总结

本提交是 Dependabot 自动完成的依赖升级，将 OpenAPI 模型代码生成工具 `datamodel-code-generator` 从 0.56.1 升级到 0.57.0（minor 版本）。改动仅涉及 OpenAPI 工具链构建依赖，属于常规依赖维护，不影响产品运行时行为。
