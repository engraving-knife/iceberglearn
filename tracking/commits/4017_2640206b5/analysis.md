# 提交 4017：Build: Bump datamodel-code-generator from 0.66.0 to 0.67.0 (#17166)

## 提交信息

- **序号**：4017 / 4088
- **哈希**：2640206b5643bc8bcd453960a9d84bfc442051e6
- **短哈希**：2640206b5
- **日期**：2026-07-11 23:58:08 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.66.0 to 0.67.0 (#17166)
- **PR/Issue**：#17166

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，将 Python 工具 `datamodel-code-generator` 从 0.66.0 升级到 0.67.0。该工具用于从 OpenAPI 规范生成 Python 数据模型代码（Pydantic 模型等），是 Iceberg REST OpenAPI 规范校验/代码生成流水线的一部分。

## 如何达成设计目的

在 `open-api/requirements.txt` 中更新 `datamodel-code-generator` 版本号。属于 minor 版本升级（0.66.0 → 0.67.0），可能包含新功能和改进。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：
```
# 修改前
datamodel-code-generator==0.66.0
# 修改后
datamodel-code-generator==0.67.0
```
使用 `==` 精确版本锁定，确保可重现构建。

## 总结

这是一次常规的 Dependabot 依赖升级，Python 工具 minor 版本更新（0.66.0 → 0.67.0），影响 OpenAPI 代码生成流水线。无功能影响，保持工具链最新版本。
