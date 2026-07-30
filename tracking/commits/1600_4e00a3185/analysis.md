# 提交 1600：Build: Bump datamodel-code-generator from 0.26.4 to 0.26.5 (#12004)

## 提交信息

- **序号**：1600 / 4088
- **哈希**：4e00a318548873f06f53b654504f9b72fde8d48c
- **短哈希**：4e00a3185
- **日期**：2025-01-19（Sun Jan 19 10:24:46 2025 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump datamodel-code-generator from 0.26.4 to 0.26.5 (#12004)
- **PR/Issue**：#12004

## 总体目的

Iceberg 仓库在 `open-api/` 目录下维护 REST Catalog 的 OpenAPI 规范及相关生成工具链。`datamodel-code-generator` 是一个 Python 工具，能根据 OpenAPI / JSON Schema 规范自动生成 Pydantic 数据模型代码。Iceberg 用它从 REST API 规范生成客户端 / 服务端模型代码，确保模型与规范保持同步。该依赖被 pin 在 `open-api/requirements.txt` 中，由 dependabot 自动监控。

dependabot 发现上游发布了 0.26.5（patch 版本升级），自动发起 PR 把依赖从 0.26.4 升级到 0.26.5。本提交是 PR 合并后的结果。升级目的与一般的 dependabot 升级一致：

1. 跟随上游 patch 修复（bug 修复、生成的代码质量改进、对新版 Pydantic / Python 的兼容性补丁等）；
2. 避免依赖陈旧导致的安全 / 兼容性积累问题；
3. 通过自动化降低维护成本。

由于是 semver-patch 升级，预期生成的代码与 API 行为保持稳定，不破坏现有生成产物。

## 如何达成设计目的

直接修改 `open-api/requirements.txt` 中 `datamodel-code-generator` 的版本固定字符串：从 `datamodel-code-generator==0.26.4` 改为 `datamodel-code-generator==0.26.5`。其它依赖行（`openapi-spec-validator==0.7.1`）不变。

dependabot 在 PR 描述中提供了上游 release notes 与 commits 对比链接，便于评审者确认变更范围。本次升级只动一个版本号字符串，无代码逻辑变更。

### 修改详情

#### `open-api/requirements.txt`

**修改目的**：升级 datamodel-code-generator 至 0.26.5。

**工作逻辑**：

```diff
-datamodel-code-generator==0.26.4
+datamodel-code-generator==0.26.5
```

`==` 严格版本固定语法，确保 CI / 本地构建安装相同版本，保证生成代码可重现。

## 小结

- **成效**：OpenAPI 模型代码生成依赖 datamodel-code-generator 升级到 0.26.5，跟随上游 patch 修复。
- **影响范围**：仅 `open-api/requirements.txt` 一个文件，单行版本号变更。无产品 Java 代码 / 测试 / 表格式变更，对运行时 jar 产物无任何影响。
- **回迁到 1.4.x 的注意事项**：纯构建工具依赖升级，与 1.4.x 发布产物无关。1.4.x 若维护独立的 OpenAPI 工具链，可选择性同步；无需回迁。
