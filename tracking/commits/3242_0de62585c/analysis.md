# 提交 3242：SPEC: Add referenced-by in loadTable API (#13810)

## 提交信息

- **序号**：3242 / 4088
- **哈希**：0de62585ce376667c2d1dde436b2175ae0dad0ca
- **短哈希**：0de62585c
- **日期**：2026-02-12
- **作者**：Prashant Singh
- **提交说明**：SPEC: Add referenced-by in loadTable API (#13810)
- **PR/Issue**：#13810

## 总体目的

本提交为 REST Catalog OpenAPI 规范新增了一个 `referenced-by` 查询参数，用于在通过视图（view）加载表或视图时传递视图引用链信息。在 Iceberg 中，视图可以引用底层表或其他视图（嵌套视图），形成"视图 → 视图 → ... → 表"的引用链。此前，当客户端通过视图加载底层表时，REST Catalog 服务端无法得知该加载请求是从哪个视图发起的、经过了怎样的引用链。这导致服务端无法基于视图上下文做细粒度的访问控制、审计日志记录或差异化凭证分发（credential vendoring）。

例如，一个 REST Catalog 实现可能希望：根据发起请求的视图来决定是否授权访问底层表；在审计日志中记录"视图 A 访问了表 T"；或根据视图的归属为底层表分发不同的存储凭证。这些场景都需要服务端知道完整的视图引用链。`referenced-by` 参数正是为此设计——客户端在加载表或视图时，将该实体被哪些视图引用的完整链路以逗号分隔的视图标识符列表传入，服务端据此进行上下文感知的处理。

该参数被添加到三个端点：`loadTable`（加载表）、`loadCredentials`（加载表凭证）和 `loadView`（加载视图），覆盖了表/视图加载和凭证分发的关键路径。

## 如何达成设计目的

在 OpenAPI 规范的 `components/parameters` 中定义一个可复用的 `referenced-by` 查询参数，然后在三个端点的 `parameters` 列表中通过 `$ref` 引用。参数值为逗号分隔的视图标识符字符串，按从最外层视图到直接引用实体的视图的顺序排列。视图标识符格式为 `{namespace}{separator}{viewName}`，其中 `separator` 是 `/config` 端点定义的命名空间分隔符，解析时以分隔符最后一次出现的位置划分命名空间和视图名。参数为可选（`required: false`），不影响不使用视图场景的正常加载。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+29/-0 lines)

**修改目的**：新增 `referenced-by` 查询参数定义并将其应用到三个加载端点。

**工作逻辑**：

- **参数定义（components/parameters/referenced-by）**：新增可复用参数定义。`name: referenced-by`，`in: query`，`required: false`，`schema: type: string`。描述详细说明了：
  - 参数值为逗号分隔的完全限定视图名（命名空间 + 视图名）列表，表示实体（表或视图）通过视图加载时的视图引用链。
  - 列表按从最外层视图到直接引用该实体的视图的顺序排列。简单情况（视图直接引用实体）列表仅含一个视图标识符；嵌套视图情况则含多个标识符表示完整依赖链。
  - 视图标识符格式为 `{namespace}{separator}{viewName}`，`separator` 为 `/config` 端点定义的命名空间分隔符。解析时以分隔符最后一次出现的位置划分命名空间与视图名。多级命名空间须遵循 list namespaces 端点中 parent 参数的编码规则。
  - 多个视图标识符以逗号分隔，服务端应按逗号拆分。若视图名本身含逗号，须 URL 编码为 `%2C`。
  - 示例：`prod%1Fanalytics%1Fquarterly_view,prod%1Fanalytics%1Fmonthly_view`（其中 `prod%1Fanalytics` 是嵌套命名空间，`quarterly_view` 引用 `monthly_view`，后者引用被加载的实体）。

- **应用到 loadTable 端点**（GET `/v1/{prefix}/namespaces/{namespace}/tables/{table}`）：在现有 `parameters` 列表末尾追加 `- $ref: '#/components/parameters/referenced-by'`，使加载表时可传入视图引用链。

- **应用到 loadCredentials 端点**（GET `/v1/{prefix}/namespaces/{namespace}/tables/{table}/credentials`）：在 `planId` 参数后追加 `- $ref: '#/components/parameters/referenced-by'`，使加载表凭证时可传入视图引用链，服务端可据此分发上下文感知的凭证。

- **应用到 loadView 端点**：新增 `parameters:` 区块并添加 `- $ref: '#/components/parameters/referenced-by'`（该端点此前没有显式 parameters 区块）。使加载视图时也可传入引用链，支持嵌套视图场景下的完整链路传递。

## 总结

本提交为 REST Catalog OpenAPI 规范新增了 `referenced-by` 查询参数，使客户端在通过视图加载表或视图时能向服务端传递完整的视图引用链。该参数被应用到 `loadTable`、`loadCredentials` 和 `loadView` 三个核心端点，为服务端实现基于视图上下文的访问控制、审计追踪和差异化凭证分发提供了规范基础。参数设计考虑了嵌套命名空间、嵌套视图链、逗号转义等复杂场景，格式规范清晰。这是一个规范层面的增强，为后续 REST Catalog 实现方提供视图感知能力奠定了基础。
