# 提交 3051：Build: Bump datamodel-code-generator from 0.46.0 to 0.49.0 (#14938)

## 提交信息

- **序号**：3051 / 4088
- **哈希**：53044611e25313e11e732ddf98fd2a6eae1aa923
- **短哈希**：53044611e
- **日期**：2025-12-27
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.46.0 to 0.49.0 (#14938)
- **PR/Issue**：#14938

## 总体目的

这是一个 Dependabot 自动依赖升级提交。`datamodel-code-generator` 是 Iceberg `open-api/` 模块使用的 Python 工具，用于从 OpenAPI 规范文件自动生成数据模型代码（通常是 Pydantic 模型或类型存根）。Iceberg 维护了一套 REST Catalog 的 OpenAPI 规范，并通过该工具在构建/校验流程中生成对应的客户端模型代码或对规范进行校验。`open-api/requirements.txt` 锁定了该工具的版本。

本次升级从 `0.46.0` 跨到 `0.49.0`，属于 **semver-minor**（次版本）升级（0.46 → 0.49，跨越 3 个次版本）。在 0.x 阶段，次版本升级可能包含新功能与少量行为变化，但通常保持向后兼容。Dependabot 将其归类为 `direct:production` 依赖。升级动机是跟进上游修复与改进，保持代码生成工具的现代性，避免积压过久导致未来升级困难。

## 如何达成设计目的

Dependabot 直接修改 `open-api/requirements.txt` 中 `datamodel-code-generator` 的版本钉（pin），从 `0.46.0` 改为 `0.49.0`，无需改动任何调用代码。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本钉。

**工作逻辑**：将 `datamodel-code-generator==0.46.0` 改为 `datamodel-code-generator==0.49.0`。该文件还锁定 `openapi-spec-validator==0.7.2` 与 `yamllint==1.37.1`，三者共同用于 OpenAPI 规范的校验与代码生成环境。升级后，运行 `open-api/` 下的生成/校验脚本时会拉取 0.49.0 版本。预期影响：获得上游 0.47~0.49 版本累积的 bug 修复与生成器改进（如对新版 OpenAPI 3.1、Pydantic v2 的支持增强），生成产物在类型注解与字段处理上可能更准确；由于是工具链依赖，不影响 Iceberg 运行时 Java/Scala 产物。

## 总结

此提交是 OpenAPI 代码生成工具链的次版本依赖升级，保持 `datamodel-code-generator` 与上游同步，属于低风险的构建依赖维护，不涉及运行时功能变更。
