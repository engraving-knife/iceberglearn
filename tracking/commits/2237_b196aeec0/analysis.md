# 提交 2237：Build: Bump datamodel-code-generator from 0.30.2 to 0.31.0

## 提交信息

- **序号**：2237 / 4088
- **哈希**：b196aeec0dd6b280376f666e7d5d8546103017d0
- **短哈希**：b196aeec0
- **日期**：2025-06-15 09:11:27 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.30.2 to 0.31.0
- **PR/Issue**：#13319

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 `datamodel-code-generator` 从 0.30.2 升级到 0.31.0。`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI 规范文件自动生成 Pydantic 数据模型代码。Iceberg 项目在 open-api 目录下使用该工具根据 REST API 规范生成 Python 数据模型。此次升级为 minor 级别更新，可能包含新功能和改进。

## 如何达成设计目的

- 在 open-api 目录的 Python 依赖文件中修改版本号。

## 修改详情

### `open-api/requirements.txt` (修改, +1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：将 `datamodel-code-generator==0.30.2` 修改为 `datamodel-code-generator==0.31.0`，使用 pip 安装时会拉取新版本。

## 总结

常规 Python 依赖升级提交，将 datamodel-code-generator 从 0.30.2 升级到 0.31.0，用于 OpenAPI 规范到 Pydantic 模型的代码生成。
