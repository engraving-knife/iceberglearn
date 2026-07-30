# 提交 2288：Build: Bump datamodel-code-generator from 0.31.1 to 0.31.2 (#13413)

## 提交信息

- **序号**：2288 / 4088
- **哈希**：084aa3823bbd29e90675bf10c4d1ce7492c26fd8
- **短哈希**：084aa3823
- **日期**：2025-06-30 07:46:42 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.31.1 to 0.31.2 (#13413)
- **PR/Issue**：#13413

## 总体目的

本提交由 Dependabot 自动生成，将 `datamodel-code-generator` 从 0.31.1 升级到 0.31.2。datamodel-code-generator 是一个 Python 工具，用于从 OpenAPI/Swagger 规范自动生成 Pydantic 数据模型代码。Iceberg 项目在 `open-api` 模块中使用该工具从 REST Catalog API 的 OpenAPI 规范生成 Python 客户端数据模型。

这是一次补丁版本升级（0.31.1 → 0.31.2），通常包含缺陷修复和小的改进。升级确保从 OpenAPI 规范生成的代码模型与最新工具版本保持一致，避免已知的生成器缺陷。

## 如何达成设计目的

- 修改 `open-api/requirements.txt` 中 `datamodel-code-generator` 的版本锁定，从 `0.31.1` 改为 `0.31.2`。
- `requirements.txt` 是 Python 依赖锁定文件，确保 Python 环境中安装的工具版本精确可控。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本号。

**工作逻辑**：将 `datamodel-code-generator==0.31.1` 改为 `datamodel-code-generator==0.31.2`。`==` 表示精确版本锁定，确保在生成 OpenAPI Python 模型代码时使用 0.31.2 版本的生成器。`openapi-spec-validator==0.7.2` 保持不变。

## 总结

本提交是常规的 Dependabot 依赖升级，将 OpenAPI 代码生成工具 datamodel-code-generator 从 0.31.1 升级到 0.31.2，仅需一行 Python 依赖文件修改。保持代码生成工具链的时效性，确保从 OpenAPI 规范生成的 Python 模型代码质量。
