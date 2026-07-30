# 提交 2419：Build: Bump datamodel-code-generator from 0.31.2 to 0.32.0 (#13688)

## 提交信息

- **序号**：2419 / 4088
- **哈希**：d365ce3ff1bb01d3d59b42a382ef16509eb3f50b
- **短哈希**：d365ce3ff
- **日期**：2025-07-27 08:59:54 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.31.2 to 0.32.0 (#13688)
- **PR/Issue**：#13688

## 总体目的

本提交由 Dependabot 自动生成，将 `datamodel-code-generator` 从 0.31.2 升级到 0.32.0。

`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI/Swagger 规范文件生成数据模型代码。在 Iceberg 项目中，它用于从 REST Catalog OpenAPI 规范（`rest-catalog-open-api.yaml`）生成对应的 Python 数据模型代码，用于 API 验证和测试。

此次升级为 semver minor 版本升级（0.31.2 → 0.32.0），可能包含新功能、改进和 bug 修复。

## 如何达成设计目的

Dependabot 自动检测到 `open-api/requirements.txt` 中 `datamodel-code-generator` 的新版本，并提交了版本升级 PR。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：将 `datamodel-code-generator` 的版本从 `0.31.2` 更新为 `0.32.0`。

## 总结

这是一个常规的依赖升级提交，将 OpenAPI 代码生成工具 datamodel-code-generator 从 0.31.2 升级到 0.32.0，保持工具链的最新状态。
