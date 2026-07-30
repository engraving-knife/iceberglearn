# 提交 3986：Build: Bump datamodel-code-generator from 0.64.1 to 0.66.0 (#17100)

## 提交信息

- **序号**：3986 / 4088
- **哈希**：0f485542bddec25fde19c8e83da409d6ab0d76fd
- **短哈希**：0f485542b
- **日期**：2026-07-06 10:29:19 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.64.1 to 0.66.0 (#17100)
- **PR/Issue**：#17100

## 总体目的

Dependabot 自动升级 `datamodel-code-generator` 工具从 0.64.1 到 0.66.0，次版本升级。这是提交 3964 升级到 0.64.1 后的后续升级。该工具用于从 OpenAPI YAML 规范生成 Python 模型代码。

## 如何达成设计目的

修改 `open-api/requirements.txt` 中的版本号。本次升级未重新生成 Python 模型代码（与提交 3964 不同），说明 0.64.1 到 0.66.0 的变更不影响生成输出。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：
```
datamodel-code-generator==0.66.0  # 原为 0.64.1
```

## 总结

常规工具升级，将 datamodel-code-generator 从 0.64.1 升级到 0.66.0，不影响生成代码输出。
