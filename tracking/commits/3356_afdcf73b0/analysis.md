# 提交 3356：Build: Bump openapi-spec-validator from 0.8.3 to 0.8.4 (#15536)

## 提交信息

- **序号**：3356 / 4088
- **哈希**：afdcf73b0e223e010981382e46f44a3be38b486d
- **短哈希**：afdcf73b0
- **日期**：2026-03-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump openapi-spec-validator from 0.8.3 to 0.8.4 (#15536)
- **PR/Issue**：#15536

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，用于将 Python 依赖 `openapi-spec-validator` 从 0.8.3 升级到 0.8.4。该依赖属于 Iceberg REST OpenAPI 规范校验工具链的一部分，记录在 `open-api/requirements.txt` 中。

Iceberg 项目在 `open-api` 目录下维护了 REST Catalog 的 OpenAPI 规范，并使用一组 Python 工具对该规范进行校验、代码生成和 lint 检查。`openapi-spec-validator` 用于校验 OpenAPI 规范文档是否符合 OpenAPI Specification 标准，确保生成的 REST API 文档正确无误。从 0.8.3 到 0.8.4 属于补丁版本（semver-patch）升级，按照语义化版本约定只包含向后兼容的缺陷修复，不会引入破坏性变更。

Dependabot 在提交信息中标注了 `update-type: version-update:semver-patch` 和 `dependency-type: direct:production`，表明这是一个直接生产依赖的补丁级升级，预期影响仅限于修复校验器自身的缺陷或改进校验行为，不会影响 Iceberg 的 Java/Scala 主代码逻辑。

## 如何达成设计目的

通过修改 `open-api/requirements.txt` 中锁定的版本号，将 `openapi-spec-validator==0.8.3` 更新为 `openapi-spec-validator==0.8.4`。后续在该目录下执行依赖安装与规范校验时，将自动使用新版本。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：将 `openapi-spec-validator` 版本从 0.8.3 升级到 0.8.4。

**工作逻辑**：
该文件以精确版本（`==`）锁定 OpenAPI 工具链依赖。本次仅修改一行：

```
-openapi-spec-validator==0.8.3
+openapi-spec-validator==0.8.4
```

同文件中的 `datamodel-code-generator==0.54.1` 和 `yamllint==1.38.0` 保持不变。`openapi-spec-validator` 在项目中负责校验 Iceberg REST Catalog 的 OpenAPI 规范文档（位于 `open-api/` 目录下的 YAML/JSON 文件）是否符合规范，升级后可享受 0.8.4 版本的缺陷修复与校验改进。

## 总结

本次为常规的 Python 依赖补丁升级，将 OpenAPI 规范校验工具 `openapi-spec-validator` 升级一个补丁版本，用于保持校验工具链的最新状态并获取缺陷修复，对 Iceberg 核心功能无影响。
