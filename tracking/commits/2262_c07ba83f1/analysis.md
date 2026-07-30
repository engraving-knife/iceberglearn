# 提交 2262：Build: Bump datamodel-code-generator from 0.31.0 to 0.31.1 (#13362)

## 提交信息

- **序号**：2262 / 4088
- **哈希**：c07ba83f13b04872a295a7e450fbbbd306602e89
- **短哈希**：c07ba83f1
- **日期**：2025-06-22 11:45:02 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.31.0 to 0.31.1
- **PR/Issue**：#13362

## 总体目的

本提交由 Dependabot 自动生成，将 `datamodel-code-generator` Python 依赖从 0.31.0 升级到 0.31.1。`datamodel-code-generator` 是一个用于从 OpenAPI/Swagger 规范自动生成 Pydantic 数据模型代码的工具，Iceberg 项目在 Open API 模块中使用它来从 REST API 规范生成 Python 模型代码。

这是一个 patch 版本升级（0.31.0 -> 0.31.1），通常包含 bug 修复和小改进，不引入破坏性变更。Dependabot 自动检测到新版本并创建此 PR 以保持依赖的最新状态。

## 如何达成设计目的

- 修改 `open-api/requirements.txt` 中的版本号从 `0.31.0` 改为 `0.31.1`。

## 修改详情

### `open-api/requirements.txt` (修改, +1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：将 `datamodel-code-generator==0.31.0` 修改为 `datamodel-code-generator==0.31.1`。该文件定义了 Open API 模块的 Python 依赖，使用精确版本锁定（==）确保可重现构建。

## 总结

本提交是 Dependabot 自动生成的依赖升级，将 `datamodel-code-generator` 从 0.31.0 升级到 0.31.1（patch 版本）。仅修改 1 行 Python 依赖文件，属于常规的依赖维护工作，确保 Open API 代码生成工具保持最新。
