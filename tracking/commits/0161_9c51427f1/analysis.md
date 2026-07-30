# 提交 0161：Build: Bump datamodel-code-generator from 0.22.0 to 0.23.0 (#9054)

## 提交信息

- **序号**：0161 / 4088
- **哈希**：9c51427f18f37d94aaa55913de0c4b0bb1195608
- **短哈希**：9c51427f1
- **日期**：2023-11-14 07:59:04 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.22.0 to 0.23.0 (#9054)
- **PR/Issue**：#9054

## 总体目的

这个提交是 Dependabot 自动生成的依赖升级，将 Iceberg 仓库 `open-api/` 目录下用于 OpenAPI 规范代码生成的 Python 工具 `datamodel-code-generator` 从 0.22.0 升级到 0.23.0。

`datamodel-code-generator` 是一个 Python 工具，能够根据 OpenAPI / JSON Schema 等规范文件自动生成 Pydantic 数据模型代码。在 Iceberg 项目中，OpenAPI 规范（即 REST Catalog 的 API 定义）需要用它来生成对应的 Python 客户端模型，便于生态侧（如 PyIceberg 及其它基于 REST Catalog 的客户端）消费这些 API 定义。这次升级属于 semver-minor 级别的版本更新，按 Dependabot 元数据 `update-type: version-update:semver-minor` 标注，属于向后兼容的次要版本升级。

升级动机主要是保持构建工具链的时效性：上游会持续修复 bug、改进对 OpenAPI 3.x 规范的解析能力、提升生成代码质量与对 Pydantic v2 的兼容性。保持工具版本较新有助于让 Iceberg 生成的 REST Catalog 客户端模型与上游 OpenAPI 规范保持同步，并减少与新版本 Pydantic 配合时的兼容性风险。仓库 `requirements.txt` 中已显式声明 `pydantic<2.4.0` 以规避 Pydantic 2.4.0 的 bug，本次升级在保留该约束的前提下推进 code-generator 自身版本。

对 Iceberg 演进的意义在于：它属于日常维护性质的依赖更新，确保 OpenAPI 客户端代码生成链路持续健康，为 REST Catalog 规范的下游消费方提供稳定、可与上游同步的代码生成基础。

## 如何达成设计目的

设计思路极其简单：Dependabot 仅修改一行 Python 依赖声明文件，把版本号从 `0.22.0` 改为 `0.23.0`，让 `open-api/` 子项目的代码生成环境在下次安装依赖时拉取新版本。改动结构上只涉及一个文件的一行，没有源代码或测试代码变动。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：将 `datamodel-code-generator` 锁定版本从 0.22.0 提升至 0.23.0，使 OpenAPI 代码生成器使用最新次要版本。

**工作逻辑**：该文件维护 `open-api/` 目录下生成 REST Catalog Python 客户端模型所需的 Python 依赖。本次改动将 `datamodel-code-generator==0.22.0` 改为 `datamodel-code-generator==0.23.0`。同文件中 `openapi-spec-validator==0.5.2` 与 `pydantic<2.4.0` 约束保持不变——后者用于规避 Pydantic 2.4.0 的已知 bug，本次升级未触动该约束，确保新版本 code-generator 仍在受控的 Pydantic 版本范围内工作。

## 小结

本次提交通过一行依赖版本号升级，将 Iceberg OpenAPI 代码生成器推进到 0.23.0，属于维护性的构建工具链更新，保障 REST Catalog 客户端模型生成链路的时效性与稳定性。
