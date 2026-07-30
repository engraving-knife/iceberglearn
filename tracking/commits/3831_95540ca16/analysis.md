# 提交 3831：REST spec: add list/load function endpoints to OpenAPI spec (#15180)

## 提交信息

- **序号**：3831 / 4088
- **哈希**：95540ca160b99aef8ad6fd2e8d613f2cde3ee897
- **短哈希**：95540ca16
- **日期**：2026-06-06 18:01:11 -0700
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：REST spec: add list/load function endpoints to OpenAPI spec (#15180)
- **PR/Issue**：#15180

## 总体目的

本提交在 Iceberg REST catalog 的 OpenAPI 规范中新增"列出函数"（`listFunctions`）与"加载函数"（`loadFunction`）两个端点，以及配套的请求/响应 schema（如 `ListFunctionsResponse`、`LoadFunctionResult`、`FunctionDescriptor`、`FunctionDefinition`、`FunctionDefinitionVersion`、`FunctionSQLRepresentation` 等）。这是 Iceberg 1.11.0 引入的 SQL UDF 规范在 REST catalog 协议层的落地工作，使 catalog 客户端能够通过标准 REST API 列出某命名空间下的函数，并按名称加载函数的完整定义（包括所有重载、版本历史、SQL 实现等）。

在此之前，REST catalog 规范已支持表与视图的 CRUD，但函数管理缺失。随着 SQL UDF 规范的引入（在表格式层面定义了版本化、多方言、跨引擎的 UDF 存储），REST 协议需要相应的端点让引擎发现和加载这些 UDF。本提交填补了这一空白，使 REST catalog 成为统一的元数据管理入口，覆盖表、视图、函数三类对象。

## 如何达成设计目的

设计上遵循现有 REST catalog 规范的风格：
- 新增两个路径：`GET /v1/{prefix}/namespaces/{namespace}/functions`（列出函数，支持分页）与 `GET /v1/{prefix}/namespaces/{namespace}/functions/{function}`（加载单个函数）。
- 新增 `function` 路径参数（函数名）。
- 新增响应 schema：`ListFunctionsResponse`（含分页 token 与 `CatalogObjectIdentifier` 列表）、`LoadFunctionResult`/`LoadFunctionResponse`（含 `FunctionDescriptor` 完整定义）。
- 新增一整套函数模型 schema：`FunctionDescriptor`（函数顶层描述，含 UUID、版本、定义列表、位置、属性、是否安全、文档等）、`FunctionDefinition`（单个重载定义，含参数列表、返回类型、版本历史）、`FunctionDefinitionVersion`（某个定义的一个版本，含实现、确定性、null 处理、创建时间）、`FunctionSQLRepresentation`（SQL 实现，含方言与 SQL 文本）、`FunctionParameter`/`FunctionType` 系列（参数与类型系统，含基础类型、list、map、struct）等。
- 同步更新 Python 模型文件 `rest-catalog-open-api.py`（用 Pydantic 模型镜像 YAML schema），用于校验与客户端生成。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+419/-0 lines)

**修改目的**：在 OpenAPI 规范中新增函数列表与加载端点及配套 schema。

**工作逻辑**：
- 新增路径 `GET /v1/{prefix}/namespaces/{namespace}/functions`（`operationId: listFunctions`）：返回 `ListFunctionsResponse`，支持 `page-token`、`page-size` 分页参数；404 表示命名空间不存在。
- 新增路径 `GET /v1/{prefix}/namespaces/{namespace}/functions/{function}`（`operationId: loadFunction`）：返回 `LoadFunctionResponse`，包含函数的完整定义（所有重载）；404 区分命名空间不存在与函数不存在。
- 新增路径参数 `function`（函数名，示例 `add_one`）。
- 新增大量 schema 组件：
  - `ListFunctionsResponse`：`next-page-token` + `identifiers`（`CatalogObjectIdentifier` 列表）。
  - `LoadFunctionResult`/`LoadFunctionResponse`：包裹 `FunctionDescriptor`。
  - `FunctionDescriptor`：函数顶层（`function-id` UUID、`spec-version`=1、`definitions`、`definition-version-log`、`location`、`properties`、`secure`、`doc`）。
  - `FunctionDefinition`：重载定义（`signature`、`parameters`、`return-type`、`versions`、`current-version-id`、`doc`）。
  - `FunctionDefinitionVersion`：版本（`version-id`、`implementations`、`deterministic`、`null-handling`、`created-at-ms`）。
  - `FunctionSQLRepresentation`：SQL 实现（`dialect`、`sql`）。
  - `FunctionParameter`、`FunctionType` 系列（基础类型、`FunctionListType`、`FunctionMapType`、`FunctionStructType`、`FunctionStructField`）。
  - `FunctionDefinitionVersionRef`、`FunctionDefinitionLogEntry`、`FunctionRepresentation` 等。
- 新增错误示例 `NoSuchFunctionError`。
- 各响应码（400/401/403/404/419/503/5XX）复用现有错误响应定义。

### `open-api/rest-catalog-open-api.py` (+208/-0 lines)

**修改目的**：在 Python Pydantic 模型中镜像新增的函数 schema，用于规范校验与客户端代码生成。

**工作逻辑**：
新增对应的 Pydantic 模型类，例如：
- `ListFunctionsResponse`：`next_page_token` + `identifiers`（`list[CatalogObjectIdentifier]`）。
- `FunctionSQLRepresentation`：`type: Literal['sql']`、`dialect`、`sql`。
- `FunctionDefinitionVersionRef`：`definition_id`、`version_id`。
- `FunctionRepresentation`：`RootModel[FunctionSQLRepresentation]`。
- `FunctionDefinitionLogEntry`：`timestamp_ms`、`definition_version_refs`。
- 以及 `FunctionDescriptor`、`FunctionDefinition`、`FunctionDefinitionVersion`、`FunctionParameter`、`FunctionType` 系列等。
字段别名（alias）与 YAML 中的 JSON 键名一致（如 `next-page-token`、`definition-id`、`version-id`）。

## 总结

本提交为 Iceberg REST catalog 规范补齐了函数管理端点（`listFunctions`、`loadFunction`）与完整的函数模型 schema，是 SQL UDF 规范在 REST 协议层落地的关键一步。新增的函数模型覆盖函数、定义、版本、SQL 实现、参数与类型系统，支持多方言、多版本、重载等场景。OpenAPI YAML 与 Python 模型同步更新，便于规范校验与多语言客户端生成。这使 REST catalog 成为表、视图、函数统一的元数据管理入口，提升了 Iceberg 在 UDF 管理领域的协议完整性。
