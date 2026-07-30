# 提交 3628：Build: Bump datamodel-code-generator from 0.56.0 to 0.56.1 (#16114)

## 提交信息

- **序号**：3628 / 4088
- **哈希**：737f043f53b695a387b3e22745a065945ca63256
- **短哈希**：737f043f5
- **日期**：2026-05-01 17:35:02 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.56.0 to 0.56.1 (#16114)
- **PR/Issue**：#16114

## 总体目的

这个提交将 `datamodel-code-generator` Python 依赖从版本 0.56.0 升级到 0.56.1，这是一个 patch 级别的版本更新。

`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI 规范生成 Pydantic 数据模型代码。Iceberg 的 Open API 模块使用它来生成 REST API 的 Python 模型代码。升级到 0.56.1 可以获得最新的 bug 修复。

## 如何达成设计目的

通过 Dependabot 自动生成的 PR，更新 `open-api/requirements.txt` 中的版本号。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：
```text
datamodel-code-generator==0.56.1  # 从 0.56.0 升级
```

## 总结

这是一个 Dependabot 自动依赖升级提交，将 Open API 模块的 `datamodel-code-generator` 从 0.56.0 升级到 0.56.1（patch 版本）。作为 patch 级别升级，通常包含 bug 修复，风险较低。
