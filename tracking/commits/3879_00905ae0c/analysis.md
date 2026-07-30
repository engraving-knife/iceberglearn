# 提交 3879：Build: Bump datamodel-code-generator from 0.59.0 to 0.60.0 (#16805)

## 提交信息

- **序号**：3879 / 4088
- **哈希**：00905ae0c87d53afa027d091e6fbc4bd3cb61e4d
- **短哈希**：00905ae0c
- **日期**：2026-06-14 00:08:29 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.59.0 to 0.60.0 (#16805)
- **PR/Issue**：#16805

## 总体目的

由 Dependabot 自动生成的依赖升级提交，将 `datamodel-code-generator` 从 0.59.0 升级到 0.60.0。这是一个 Python 工具，用于从 OpenAPI 规范自动生成 Pydantic 数据模型代码。Iceberg 项目在 `open-api` 模块中使用它来生成 REST API 的数据模型。

这是 semver-minor 级别的升级，可能包含新功能和改进。

## 如何达成设计目的

通过修改 `open-api/requirements.txt` 文件中的版本约束，将 `datamodel-code-generator` 从 `0.59.0` 升级到 `0.60.0`。该文件是 Python 项目的依赖声明文件，用于 OpenAPI 相关的代码生成和验证工作流。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：
```diff
-datamodel-code-generator==0.59.0
+datamodel-code-generator==0.60.0
```
使用精确版本约束（`==`），确保 OpenAPI 工作流使用确定版本的代码生成器。

## 总结

常规的 Python 工具依赖升级，将 OpenAPI 代码生成器从 0.59.0 升级到 0.60.0。属于 minor 版本升级，可能引入新的生成功能或改进，但作为工具类依赖，对运行时影响较小。
