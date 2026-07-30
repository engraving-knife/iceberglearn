# 提交 3385：Spec: Add scan-planning-mode to LoadTableResult config documentation (#14867)

## 提交信息

- **序号**：3385 / 4088
- **哈希**：6e64fb5ba4ea18c55115358b0676308a42f93bd6
- **短哈希**：6e64fb5ba
- **日期**：2026-03-13
- **作者**：Prashant Singh
- **提交说明**：Spec: Add scan-planning-mode to LoadTableResult config documentation (#14867)
- **PR/Issue**：#14867

## 总体目的

本提交在 Iceberg REST Catalog OpenAPI 规范文档中为 `LoadTableResult` 的配置项新增 `scan-planning-mode` 的文档说明，使 REST Catalog 客户端实现者能从规范中得知该配置项的语义与合法取值，从而正确实现"客户端/服务端扫描规划"模式的选择与快速失败（fail fast）逻辑。

Iceberg REST Catalog 支持两种扫描规划模式：客户端规划（client-side scan planning，由客户端自行计算文件扫描任务）与服务端规划（server-side scan planning，通过 `planTableScan` 端点由服务端计算）。服务端在响应 `LoadTable` 请求时，可通过 `config` 中的 `scan-planning-mode` 字段告知客户端该表支持的规划模式。若客户端不支持服务端要求的模式（例如服务端要求 `server` 但客户端未实现 `planTableScan` 调用），客户端应能"快速失败"并给出明确错误，而非在查询时才出现难以诊断的失败。

此前该配置项已在代码实现中使用（如 `RESTCatalogProperties` 中的相关属性），但 OpenAPI 规范文档中缺少对它的描述，导致第三方 REST Catalog 实现者无法从规范获知这一行为契约。本提交补全了这一文档缺口。

## 如何达成设计目的

在 OpenAPI 规范的两个等价表示文件——`rest-catalog-open-api.yaml`（YAML 格式，规范主文件）和 `rest-catalog-open-api.py`（Python Pydantic 模型表示）——的 `LoadTableResult.config` 描述中，于通用配置（General Configurations）段落追加 `scan-planning-mode` 的说明，列出 `client` 与 `server` 两个合法取值及其语义。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+3/-0 lines)

**修改目的**：在 YAML 规范中补充 scan-planning-mode 配置文档。

**工作逻辑**：
在 `LoadTableResult` 的 `config` 字段描述中，紧跟 `token` 配置项之后，新增 `scan-planning-mode` 的说明文本。文档说明该配置项向客户端传达所支持的规划模式，客户端应据此在客户端不支持该模式时快速失败。列出两个合法取值：
- `client`：客户端必须使用客户端侧扫描规划
- `server`：客户端必须通过 `planTableScan` 端点使用服务端侧扫描规划

YAML 中使用缩进的子列表表达这两个取值，符合 OpenAPI 规范中 description 字段的 Markdown 渲染惯例。

### `open-api/rest-catalog-open-api.py` (+3/-0 lines)

**修改目的**：在 Python Pydantic 模型表示中同步补充文档。

**工作逻辑**：
在 `LoadTableResult` 类的 `config` 字段 docstring 中，追加与 YAML 完全一致的 `scan-planning-mode` 说明文本及 `client`/`server` 取值描述。该 `.py` 文件是 OpenAPI 规范的 Python 表示形式，与 `.yaml` 保持内容同步，供使用 Pydantic 生成客户端/服务端代码的实现者使用。

## 总结

本提交为 Iceberg REST Catalog OpenAPI 规范补充了 `scan-planning-mode` 配置项的文档说明，明确了 `client` 与 `server` 两种扫描规划模式的语义及客户端快速失败的行为契约。改动虽仅涉及文档（3 行 × 2 文件），但填补了规范层面的文档缺口，使第三方 REST Catalog 实现者能依据规范正确实现扫描规划模式的选择与降级逻辑，对 REST Catalog 互操作性具有实际的指导价值。
