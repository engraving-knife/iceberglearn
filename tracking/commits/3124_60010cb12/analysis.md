# 提交 3124：Build: Bump datamodel-code-generator from 0.52.2 to 0.53.0 (#15074)

## 提交信息

- **序号**：3124 / 4088
- **哈希**：60010cb1242c08c60763b1c4e7f734704651c6e2
- **短哈希**：60010cb12
- **日期**：2026-01-17 21:19:05 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.52.2 to 0.53.0 (#15074)
- **PR/Issue**：#15074

## 总体目的

这是一次 dependabot 自动依赖升级。`datamodel-code-generator` 是 Iceberg `open-api` 模块所用的开发/构建工具，其命令 `datamodel-codegen` 在 `open-api/Makefile` 的 `generate` 目标中被调用，用于从 `rest-catalog-open-api.yaml`（OpenAPI 规范）自动生成 Python pydantic 模型代码 `rest-catalog-open-api.py`。根据 `open-api/README.md` 说明，生成的 Python 代码并不在项目运行时使用，主要用于帮助开发者直观查看 OpenAPI 定义变更在生成代码中的反映，辅助规范演进的审查。

本次将该工具从 `0.52.2` 升级到 `0.53.0`，属于语义版本的次版本号（minor）升级。按 dependabot 元数据 `update-type: version-update:semver-minor`，此类升级通常包含新特性与改进，一般保持向后兼容。升级动机是跟进上游工具的新版本，获取生成器改进（如更好的类型生成、bug 修复、对新 OpenAPI 特性的支持），确保规范变更时生成的参考代码与最新工具行为一致。

## 如何达成设计目的

仅需在 `open-api/requirements.txt` 中将 `datamodel-code-generator` 的固定版本从 `0.52.2` 改为 `0.53.0`。该文件被 `open-api/Makefile` 在创建虚拟环境与运行 `generate` 目标时安装使用，版本固定后构建流程会拉取新版本工具。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：将 OpenAPI Python 模型生成工具升级到 0.53.0。

**工作逻辑**：
该文件固定了 open-api 模块构建/校验所需的 Python 依赖版本：`openapi-spec-validator==0.7.2`（用于校验规范）、`datamodel-code-generator`、`yamllint`（YAML lint）。本次将 `datamodel-code-generator==0.52.2` 改为 `datamodel-code-generator==0.53.0`。在 `Makefile` 中，`generate` 目标通过 `uv run datamodel-codegen ...` 调用该工具从 YAML 生成 `rest-catalog-open-api.py`（如本次提交前后的 #14869 就新增了 `RegisterViewRequest` 类）。升级为 minor 版本，预期生成器行为向前兼容，主要带来工具侧的改进与新特性；若生成代码风格有细微变化，会在后续规范变更时通过重新生成体现。该依赖为构建期工具，不影响 Iceberg 运行时产物。

## 总结

本提交是 dependabot 对 open-api 模块 Python 模型生成工具 `datamodel-code-generator` 的一次 minor 版本升级（0.52.2 → 0.53.0），仅改一行版本固定。该工具用于从 OpenAPI 规范生成参考性 Python 代码，属构建期依赖、不影响运行时。升级跟进上游改进，保持规范生成流程与最新工具一致。
