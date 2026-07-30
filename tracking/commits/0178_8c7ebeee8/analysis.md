# 提交 0178：Build: Bump openapi-spec-validator from 0.5.2 to 0.7.1 (#9057)

## 提交信息

- **序号**：0178 / 4088
- **哈希**：8c7ebeee82b59904753fb6eab61a1e28e9852ae3
- **短哈希**：8c7ebeee8
- **日期**：2023-11-19 07:57:53 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump openapi-spec-validator from 0.5.2 to 0.7.1 (#9057)
- **PR/Issue**：#9057

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，针对 `open-api` 模块下的 `openapi-spec-validator`。该 Python 库用于校验 OpenAPI 规范文档的合法性，是 Iceberg REST Catalog OpenAPI 工程在 CI/构建过程中验证 `open-api/iceberg-open-api.yaml` 规范文件的工具依赖。

本次升级把 `openapi-spec-validator` 从 0.5.2 提升到 0.7.1（跨越两个 minor 版本），属于常规依赖维护：跟随上游以获取对 OpenAPI 3.1 规范更完善的校验支持、bug 修复以及对较新 Pydantic 生态的兼容性。与相邻的 0177（升 datamodel-code-generator）、0179（解除 pydantic 钉扎）一起，构成 Iceberg 团队对该 Python 构建链的同步刷新。

## 如何达成设计目的

Dependabot 通过单一行 diff 完成升级：把 `open-api/requirements.txt` 中 `openapi-spec-validator` 的精确版本钉从 `0.5.2` 改为 `0.7.1`，其余依赖保持不变。该提交紧接 0177 落地，因此基线已是 `datamodel-code-generator==0.24.2` + `pydantic<2.4.0`，本次升级不动这两个钉扎。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：将 `openapi-spec-validator` 版本钉从 0.5.2 提升到 0.7.1。

**工作逻辑**：`openapi-spec-validator==0.5.2` 改为 `openapi-spec-validator==0.7.1`。`datamodel-code-generator==0.24.2` 与 `pydantic<2.4.0` 两行未改动。`pydantic<2.4.0` 的钉扎及其注释（"2.4.0 has a bug"）将在下一个提交 0179 中被移除。

## 小结

一次常规的 Dependabot 依赖升级，将 OpenAPI 规范校验工具 `openapi-spec-validator` 升级到 0.7.1，保持 Iceberg REST OpenAPI 校验链与上游同步。
