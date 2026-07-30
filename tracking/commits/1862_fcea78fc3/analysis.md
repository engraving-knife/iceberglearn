# 提交 1862：Build: Bump datamodel-code-generator from 0.28.2 to 0.28.4 (#12541)

## 提交信息

- **序号**：1862 / 4088
- **哈希**：fcea78fc3571063fa172edd96be00b1fab0ba68e
- **短哈希**：fcea78fc3
- **日期**：2025-03-16 07:32:48 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.28.2 to 0.28.4 (#12541)
- **PR/Issue**：#12541

## 总体目的

Iceberg 的 `open-api` 模块使用 `datamodel-code-generator` 工具，从 OpenAPI spec 自动生成 Python 数据模型代码（用于 REST catalog 的合规性测试工具 RCK 等）。dependabot 检测到上游发布了 0.28.4（当前 0.28.2 之后的两个补丁版本），提交本 PR 升级版本号。

`datamodel-code-generator` 是 `open-api/requirements.txt` 中的构建/代码生成依赖。0.28.x 系列内的补丁升级通常包含生成代码的 bug 修复、对新 OpenAPI 特性的支持改进以及安全补丁，不引入破坏性变更。保持工具最新有助于让生成的代码模型与上游修复同步，避免生成出有 bug 的代码。

## 如何达成设计目的

修改 `open-api/requirements.txt` 中 `datamodel-code-generator` 的版本号：`datamodel-code-generator==0.28.2` → `datamodel-code-generator==0.28.4`。同文件中的另一个依赖 `openapi-spec-validator==0.7.1` 保持不变。这是 dependabot 自动生成的最小化版本号变更，不涉及任何手写代码或配置逻辑改动。

## 修改详情

### `open-api/requirements.txt` (修改, +1/-1 line)

**修改目的**：升级 datamodel-code-generator 到 0.28.4。

**工作逻辑**：把 `datamodel-code-generator==0.28.2` 改为 `datamodel-code-generator==0.28.4`，`openapi-spec-validator==0.7.1` 不变。文件头部保留 Apache License 注释。该文件由 open-api 模块的代码生成流程消费（pip 安装后运行 datamodel-code-generator 从 OpenAPI spec 生成 Python 模型），升级后下次代码生成会使用 0.28.4 版本。

## 小结

- **成效**：open-api 模块的代码生成工具 datamodel-code-generator 升级到 0.28.4，获得上游补丁修复。
- **影响范围**：仅 `open-api/requirements.txt` 1 个文件、1 行改动。不影响任何 Java 运行时代码，仅影响 open-api 模块的 Python 代码生成与合规性测试工具。
- **回迁到 1.4.x 的注意事项**：纯依赖版本号升级，无前置依赖，回迁零风险。若 1.4.x 分支也维护 open-api 模块且 `open-api/requirements.txt` 仍指向 0.28.2 或更早版本，建议回迁。回迁时只需把对应行改为 `datamodel-code-generator==0.28.4`。建议回迁。
