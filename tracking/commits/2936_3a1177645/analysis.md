# 提交 2936：Build: Bump datamodel-code-generator from 0.35.0 to 0.36.0 (#14716)

## 提交信息

- **序号**：2936 / 4088
- **哈希**：3a1177645af8f75c9ed5e3a33a229f1dd5a49ade
- **短哈希**：3a1177645
- **日期**：2025-11-29
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.35.0 to 0.36.0 (#14716)
- **PR/Issue**：#14716

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，目标是将 Iceberg 仓库 `open-api/requirements.txt` 中声明的 `datamodel-code-generator` 从 0.35.0 升级到 0.36.0。

`datamodel-code-generator` 是一个 Python 工具（来自 `koxudaxi/datamodel-code-generator` 项目），它能够根据 OpenAPI / JSON Schema 规范自动生成 Pydantic 模型代码。在 Iceberg 项目中，这个工具被用于 `open-api/` 目录下，从 Iceberg REST OpenAPI 规范自动生成 Python 客户端数据模型，以保证客户端模型与 REST spec 的一致性，避免手写模型容易出现的字段遗漏或类型偏差。

从语义版本上看，本次升级属于 `semver-minor`（次版本号）升级，按依赖维护方的语义化约定，通常会引入新特性但保持向后兼容，理论上不会引入破坏性变更。Dependabot 元数据也将其标注为 `version-update:semver-minor`、`direct:production`，说明这是直接用于生产构建链路的工具版本抬升。

## 如何达成设计目的

改动极其简单，仅将 `open-api/requirements.txt` 中固定版本的声明行从 `datamodel-code-generator==0.35.0` 修改为 `datamodel-code-generator==0.36.0`。通过锁定精确版本（`==`），确保所有开发者和 CI 环境在重新生成 OpenAPI 客户端代码时使用的是同一个新版本，避免因版本漂移导致生成结果不一致。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：将 `datamodel-code-generator` 锁定版本从 0.35.0 抬升到 0.36.0。

**工作逻辑**：
该文件用于固定 `open-api/` 目录生成客户端代码所需的 Python 依赖版本。本次仅修改一行：

```diff
-datamodel-code-generator==0.35.0
+datamodel-code-generator==0.36.0
```

同时文件中保留了 `openapi-spec-validator==0.7.2` 不变。升级后，重新执行基于 OpenAPI spec 的代码生成流程会使用 0.36.0 的生成器，可能带来生成代码风格、类型注解、新字段处理等方面的细微改进，但由于是 minor 升级且无对应代码改动伴随，说明本次升级未对生成产物造成需要回填的破坏性影响。

## 总结

本次提交是常规的依赖维护工作，通过 Dependabot 自动将 OpenAPI 客户端代码生成工具 `datamodel-code-generator` 升级一个 minor 版本，保持构建工具链的时效性与上游同步，属于低风险的纯依赖升级，无源码逻辑改动。
