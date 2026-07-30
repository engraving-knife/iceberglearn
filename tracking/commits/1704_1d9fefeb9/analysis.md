# 提交 1704：Build: Bump datamodel-code-generator from 0.26.5 to 0.27.2 (#12204)

## 提交信息

- **序号**：1704 / 4088
- **哈希**：1d9fefeb9680d782dc128f242604903e71c32f97
- **短哈希**：1d9fefeb9
- **日期**：2025-02-09（Sun Feb 9 08:38:13 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump datamodel-code-generator from 0.26.5 to 0.27.2 (#12204)
- **PR/Issue**：#12204

## 总体目的

Dependabot 自动升级提交。`datamodel-code-generator` 是一个从 OpenAPI 规范自动生成 Python 数据模型（Pydantic 等）的工具，Iceberg 在 `open-api/` 模块中使用它从 REST catalog 的 OpenAPI 规范生成 Python 客户端模型代码。本提交把该工具从 `0.26.5` 升级到 `0.27.2`（minor 级），可能包含新功能与 bug 修复。

注意：这是 minor 版本升级（0.26.x → 0.27.x），可能存在生成代码格式的变化，升级后需重新生成并验证输出。

## 如何达成设计目的

在 `open-api/requirements.txt` 中把 `datamodel-code-generator==0.26.5` 改为 `datamodel-code-generator==0.27.2`。

## 修改详情

### `open-api/requirements.txt`（修改，+1/-1 行）

**修改目的**：升级 datamodel-code-generator 工具版本。

**工作逻辑**：修改 Python 依赖锁定版本号，重新生成 OpenAPI 客户端模型时使用新版本。

## 小结

- **成效**：升级 OpenAPI 代码生成工具到 0.27.2，获取新功能与 bug 修复。
- **影响范围**：仅 `open-api/` 模块的构建依赖，不直接影响运行时。但 minor 版本升级可能导致生成的代码格式变化，需验证。
- **回迁到 1.4.x 的注意事项**：回迁安全，纯构建工具升级。若 1.4.x 有 OpenAPI 客户端代码生成流程，升级后需重新生成并对比 diff 确认无破坏性变化。
