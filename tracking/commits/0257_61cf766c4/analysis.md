# 提交 0257：Build: Bump datamodel-code-generator from 0.24.2 to 0.25.1 (#9199)

## 提交信息

- **序号**：0257 / 4088
- **哈希**：61cf766c4301b44fd077ee5487b4f605f54905cb
- **短哈希**：61cf766c4
- **日期**：2023-12-11
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump datamodel-code-generator from 0.24.2 to 0.25.1 (#9199)
- **PR/Issue**：#9199

## 总体目的

这是一次由 Dependabot 自动发起的依赖版本升级，针对 Iceberg OpenAPI 模块用于代码生成的 Python 依赖 `datamodel-code-generator`。该工具负责把 `rest-catalog-open-api.yaml` 这份 OpenAPI 规范转写成 `rest-catalog-open-api.py` 中的 Pydantic 模型类，是 OpenAPI 模块「规范 → 可执行 Python 模型」链路的关键一环。保持该工具为较新版本可以持续获得代码生成质量、对新版 Pydantic / OpenAPI 特性（如本批次中 0259 引入的 `discriminator`）的支持以及缺陷修复。

需要特别说明的是：提交说明标题写的是「from 0.24.2 to 0.25.1」，但实际 diff 修改的是 `0.25.0` → `0.25.1`（一个 patch 级补丁升级）。这说明在 Dependabot 发起本次 PR 与最终合入之间，仓库里该依赖的基线已经被另一笔升级推到了 0.25.0，因此本提交在文件层面只体现了 0.25.0 → 0.25.1 的 patch bump，而非标题宣称的 0.24.2 → 0.25.1 跨 minor 升级。

## 如何达成设计目的

仅修改 OpenAPI 模块的 Python 依赖清单 `open-api/requirements.txt`，将 `datamodel-code-generator` 的固定版本号上移一个 patch 版本，使后续运行 OpenAPI 代码生成时使用新版本。无源代码逻辑变更。

## 修改详情

### `open-api/requirements.txt`

**修改目的**：把 `datamodel-code-generator` 的固定版本从 0.25.0 升级到 0.25.1。

**工作逻辑**：

```diff
 openapi-spec-validator==0.7.1
-datamodel-code-generator==0.25.0
+datamodel-code-generator==0.25.1
```

采用 `==` 精确版本锁定，与该文件中 `openapi-spec-validator` 的风格一致，保证代码生成环境的可重复性。

## 小结

一次例行的 Dependabot patch 级依赖升级，将 OpenAPI 代码生成器 datamodel-code-generator 锁定到 0.25.1，为后续 OpenAPI 规范的代码生成提供更新版本的工具链。
