# 提交 3325：Build: Bump openapi-spec-validator from 0.7.2 to 0.8.3 (#15480)

## 提交信息

- **序号**：3325 / 4088
- **哈希**：e6af9db74669fffdad4155887350b046facc028e
- **短哈希**：e6af9db74
- **日期**：2026-02-28
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump openapi-spec-validator from 0.7.2 to 0.8.3 (#15480)
- **PR/Issue**：#15480

## 总体目的

这是一次由 Dependabot 自动发起的 Python 依赖升级，把 Iceberg 仓库 `open-api/` 模块所用的 `openapi-spec-validator` 从 0.7.2 升级到 0.8.3。`openapi-spec-validator` 是一个用于校验 OpenAPI 规范文档正确性的 Python 工具。在 Iceberg 项目中，它在 `open-api/Makefile` 的 `validate-spec` 目标里被用来校验两份 OpenAPI 规范文件：Iceberg REST Catalog 规范 `rest-catalog-open-api.yaml` 与 AWS S3 signer 规范 `../aws/src/main/resources/s3-signer-open-api.yaml`，执行命令为 `uv run openapi-spec-validator --errors all <file>`。`--errors all` 表示报告全部校验错误，因此校验器版本的严格度直接影响 CI 中 spec 校验的结果。

该依赖与 `datamodel-code-generator`、`yamllint` 一起在 `open-api/requirements.txt` 中以精确版本号（`==`）固定，用于保证 OpenAPI 规范的校验、lint 与 Python 数据模型代码生成的可复现性。本次升级属于 0.x 系列内的次版本更新（0.7 -> 0.8），按 Dependabot 元数据分类为 `version-update:semver-minor`。对于 0.x 库而言，次版本号（第二位）的递增通常可能包含行为变化或新增校验规则，因此升级后可能在校验时暴露此前未触发的问题，需通过 `make validate-spec` 验证两份规范仍能通过；若新版本更严格，则可能需要同步修正规范文件。升级的动机是跟随上游维护版本，获取 0.8.x 中包含的缺陷修复与校验规则改进，保持 spec 校验工具链的时效性。

## 如何达成设计目的

改动仅涉及 `open-api/requirements.txt` 中的一行版本号，由 `openapi-spec-validator==0.7.2` 改为 `openapi-spec-validator==0.8.3`。由于该文件以精确版本固定依赖，且 Makefile 通过 `uv pip install -r requirements.txt` 安装、`uv run openapi-spec-validator ...` 执行，单行改动即可让 `validate-spec` 目标在下次安装环境后使用新版本进行校验。Dependabot 在 PR 描述中附上了上游 release notes 与 commits 对比链接，便于审查者评估 0.7.2 到 0.8.3 之间的变更范围。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：将 OpenAPI 规范校验工具升级到 0.8.3。

**工作逻辑**：
该文件以 `==` 精确固定 `open-api` 模块构建/校验链路的三个 Python 工具版本。本次将 `openapi-spec-validator==0.7.2` 改为 `openapi-spec-validator==0.8.3`，其余两项（`datamodel-code-generator==0.54.0`、`yamllint==1.38.0`）保持不变。该工具在 `Makefile` 的 `validate-spec` 目标中被 `uv run openapi-spec-validator --errors all rest-catalog-open-api.yaml` 与对 S3 signer 规范的同样调用所使用。升级到 0.8.3 后，CI 在执行 `make validate-spec`（属于 `lint` 目标）时会用新版校验器对两份 OpenAPI 规范做完整校验；若 0.8.x 引入了更严格的规则，可能需要后续修正规范文件以使其继续通过。

## 总结

本次提交通过单行版本号改动，把 Iceberg OpenAPI 规范校验所用的 `openapi-spec-validator` 从 0.7.2 升级到 0.8.3，获取上游次版本中的校验改进与缺陷修复。改动集中在 `open-api/requirements.txt`，影响 `make validate-spec` 对 REST Catalog 与 S3 signer 规范的校验行为，是 Python 工具链依赖维护的常规操作，需配合 CI 验证规范仍能通过新版校验。
