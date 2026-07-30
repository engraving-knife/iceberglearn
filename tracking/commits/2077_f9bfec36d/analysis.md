# 提交 2077：Build: Bump datamodel-code-generator from 0.28.5 to 0.30.1

## 提交信息

- **序号**：2077 / 4088
- **哈希**：f9bfec36d13f4965b75d508d81bffce9fc6645ff
- **短哈希**：f9bfec36d
- **日期**：2025-05-05 12:19:55 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.28.5 to 0.30.1 (#12961)
- **PR/Issue**：#12961

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 `datamodel-code-generator` 从 0.28.5 升级到 0.30.1。这是一个跨两个 minor 版本（0.28 → 0.29 → 0.30）的升级，可能包含新功能和改进。

`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI 规范自动生成 Pydantic 数据模型代码。Iceberg 的 `open-api/` 模块使用该工具根据 REST Catalog 的 OpenAPI 规范生成 Python 客户端数据模型，因此该工具的版本由 Python 的 `requirements.txt` 管理。

## 如何达成设计目的

通过修改 `open-api/requirements.txt` 中的版本锁定，完成升级。

## 修改详情

### `open-api/requirements.txt` (修改, +1/-1 lines)

**修改目的**：将 datamodel-code-generator 版本从 0.28.5 升级到 0.30.1。

**工作逻辑**：
将 `datamodel-code-generator==0.28.5` 修改为 `datamodel-code-generator==0.30.1`。使用精确版本锁定（`==`）确保生成的代码模型一致性。

## 总结

本提交由 Dependabot 自动生成，将 Python 依赖 `datamodel-code-generator` 从 0.28.5 升级到 0.30.1（跨两个 minor 版本），用于 OpenAPI 规范到 Pydantic 模型的代码生成，仅修改 `requirements.txt` 中一行版本号。
