# 提交 3125：Build: Bump yamllint from 1.37.1 to 1.38.0 (#15075)

## 提交信息

- **序号**：3125 / 4088
- **哈希**：491e00a5939e545fe1cd733a9f39274d85312dd5
- **短哈希**：491e00a59
- **日期**：2026-01-17 21:41:03 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump yamllint from 1.37.1 to 1.38.0 (#15075)
- **PR/Issue**：#15075

## 总体目的

这是一次 dependabot 自动依赖升级。`yamllint` 是 Iceberg `open-api` 模块所用的 YAML 代码检查工具（linter），在 `open-api/Makefile` 的 `lint-spec` 目标中以 `uv run yamllint --strict` 对 `rest-catalog-open-api.yaml` 与 `aws/src/main/resources/s3-signer-open-api.yaml` 两个 OpenAPI 规范文件做严格模式 lint，用于在构建/CI 阶段保障 YAML 规范文件的格式质量（缩进、空行、行长度、重复键等）。`lint-spec` 与 `validate-spec` 共同构成 `lint` 目标，是规范变更时的质量门禁。

本次将 `yamllint` 从 `1.37.1` 升级到 `1.38.0`，属于语义版本的次版本号（minor）升级，dependabot 元数据标记为 `version-update:semver-minor`。此类升级通常包含新规则、改进与 bug 修复，一般保持向后兼容。升级动机是跟进上游 linter 新版本，获取检查规则改进与新特性，确保规范文件的 lint 与最新工具一致。需注意 minor 升级可能引入更严格或新增的检查规则，若现有 YAML 触发新告警，后续或需配套调整规范文件或 lint 配置。

## 如何达成设计目的

仅需在 `open-api/requirements.txt` 中将 `yamllint` 的固定版本从 `1.37.1` 改为 `1.38.0`。该文件被 `open-api/Makefile` 在创建虚拟环境与运行 `lint-spec` 目标时安装使用，版本固定后 lint 流程会拉取新版本工具。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：将 OpenAPI 规范文件的 YAML linter 升级到 1.38.0。

**工作逻辑**：
该文件固定 open-api 模块构建/校验所需的 Python 依赖版本：`openapi-spec-validator==0.7.2`（规范合法性校验）、`datamodel-code-generator==0.53.0`（Python 模型生成）、`yamllint`（YAML lint）。本次将 `yamllint==1.37.1` 改为 `yamllint==1.38.0`。在 `Makefile` 的 `lint-spec` 目标中，`uv run yamllint --strict rest-catalog-open-api.yaml` 与对 `s3-signer-open-api.yaml` 的 lint 会使用该版本对两个 OpenAPI YAML 文件做严格格式检查。升级为 minor 版本，预期兼容既有规则；若新版本引入更严格的检查，可能在后续规范提交中以 lint 错误形式体现并需配套修正。该依赖为构建/CI 期工具，不影响 Iceberg 运行时产物。

## 总结

本提交是 dependabot 对 open-api 模块 YAML 检查工具 `yamllint` 的一次 minor 版本升级（1.37.1 → 1.38.0），仅改一行版本固定。该工具用于严格模式 lint 两个 OpenAPI 规范 YAML 文件，属构建/CI 期依赖、不影响运行时。升级跟进上游 linter 改进，维护规范文件格式质量门禁的一致性。
