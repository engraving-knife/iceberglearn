# 提交 3632：Build: Bump openapi-spec-validator from 0.8.4 to 0.8.5 (#16200)

## 提交信息

- **序号**：3632 / 4088
- **哈希**：1c1aaf038442c490c9aa4617a5327d240d535bf8
- **短哈希**：1c1aaf038
- **日期**：2026-05-02 23:04:27 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump openapi-spec-validator from 0.8.4 to 0.8.5 (#16200)
- **PR/Issue**：#16200

## 总体目的

这个提交将 `openapi-spec-validator` Python 依赖从版本 0.8.4 升级到 0.8.5，这是一个 patch 级别的版本更新。

`openapi-spec-validator` 是一个 Python 工具，用于验证 OpenAPI 规范文档的正确性。Iceberg 的 Open API 模块使用它来验证 REST API 规范文档。升级到 0.8.5 可以获得最新的 bug 修复。

## 如何达成设计目的

通过 Dependabot 自动生成的 PR，更新 `open-api/requirements.txt` 中的版本号。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 openapi-spec-validator 版本。

**工作逻辑**：
```text
openapi-spec-validator==0.8.5  # 从 0.8.4 升级
```

## 总结

这是一个 Dependabot 自动依赖升级提交，将 Open API 模块的 `openapi-spec-validator` 从 0.8.4 升级到 0.8.5（patch 版本）。作为 patch 级别升级，通常包含 bug 修复，风险较低。
