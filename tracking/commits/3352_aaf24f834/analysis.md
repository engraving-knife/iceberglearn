# 提交 3352：Build: Bump datamodel-code-generator from 0.54.0 to 0.54.1 (#15535)

## 提交信息

- **序号**：3352 / 4088
- **哈希**：aaf24f834c16d28c872a67b7fbd8e73e1f674c7e
- **短哈希**：aaf24f834
- **日期**：2026-03-07 23:15:48 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.54.0 to 0.54.1 (#15535)
- **PR/Issue**：#15535

## 总体目的

`datamodel-code-generator` 是一个 Python 工具，能从 OpenAPI / JSON Schema 等 spec 文件自动生成 Pydantic 数据模型代码。Iceberg 在 `open-api` 模块中用它从 REST Catalog 的 OpenAPI 规范生成 Python 客户端模型（供 OpenAPI 契约测试 / TCK 使用），`open-api/requirements.txt` 固定了该工具及其配套工具（`openapi-spec-validator`、`yamllint`）的版本。

本次 dependabot 把 `datamodel-code-generator` 从 `0.54.0` 升到 `0.54.1`（语义版本 semver-patch 升级）。patch 升级只包含 bug 修复，不引入新功能或破坏性变更，目的是获取生成器的修复以保持生成产物稳定、规避已知的代码生成缺陷。由于是 patch 级，对生成的模型代码与测试流程没有预期影响。

## 如何达成设计目的

仅需在 `requirements.txt` 中把版本号固定值更新，重新安装依赖即可让 CI 使用新版本生成器。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：把 datamodel-code-generator 固定版本升到 0.54.1。

**工作逻辑**：`datamodel-code-generator==0.54.0` 改为 `datamodel-code-generator==0.54.1`。该文件还固定了 `openapi-spec-validator==0.8.3` 与 `yamllint==1.38.0`，本次只动这一行。使用 `==` 精确固定版本，保证 CI 环境可复现。

## 总结

本提交把 OpenAPI 模型代码生成器 datamodel-code-generator 从 0.54.0 升到 0.54.1（semver-patch），获取上游 bug 修复以维持代码生成产物稳定。改动仅一行版本固定值，属低风险依赖维护。
