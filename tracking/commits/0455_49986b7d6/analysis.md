# 提交 0455：Add REST spec for data access mechanisms (#9628)

## 提交信息

- **序号**：0455
- **完整哈希**：49986b7d6567818500b96e936f490be19884d6ce
- **短哈希**：49986b7d6
- **日期**：2024-02-03 12:29:14 -0800
- **作者**：Daniel Weeks <dweeks@apache.org>
- **提交说明**：Add REST spec for data access mechanisms (#9628)。提交体内列出四个迭代提交：Add REST spec for data access mechanisms / Update header and value names / Update description to reference other doc sections / Update description layout
- **关联 PR**：#9628
- **修改文件**：1 个，共 29 行新增

## 总体目的

本提交为 Iceberg REST Catalog OpenAPI 规范补充"数据访问委托机制"的客户端信号通道。在此之前的规范中，客户端在 `loadTable`（加载表）或 `createTable`（建表并加载）时，服务端可以返回 `LoadTableResult`，其中 `config` 可能包含 `vended-credentials`（凭据下发）形式的访问凭据，使客户端能直接用下发凭据访问底层存储；另一种访问机制是 `remote-signing`（远端签名），常见于 S3，由服务端代为对请求签名。然而规范中缺少一个明确的客户端→服务端信号：客户端支持哪种访问机制？服务端无法据此决定该下发凭据还是走远端签名，只能按自身默认行为返回。

本提交引入一个标准的 HTTP 请求头 `X-Iceberg-Access-Delegation`，作为客户端在 `loadTable` 与 `createTable` 请求中向服务端声明"我支持哪些访问委托机制"的可选信号。头部值是逗号分隔的枚举列表，目前定义两个取值：`vended-credentials`（客户端接受服务端下发凭据）与 `remote-signing`（客户端接受服务端远端签名）。服务端"may choose to supply access via any or none of the requested mechanisms"——即可从客户端请求的机制中任选其一或都不选，保留服务端决策权。这种设计既给了客户端表达偏好的能力，又不强制服务端必须按客户端意愿返回，符合 REST Catalog 在多租户、多存储后端下的灵活性需求。

规范层面，该头部被定义为一个可复用的 `data-access` 参数（位于 `components/parameters`），并被 `createTable` 与 `loadTable` 两个操作通过 `$ref` 引用，避免在两处重复定义。描述中显式引用 `LoadTableResult`（vended-credentials 的具体属性文档位置）与 `aws` 模块下的 `s3-signer-open-api.yaml`（remote-signing 的协议文档位置），形成跨文档导航。提交体说明本 PR 经过四轮迭代：初版添加机制 / 调整 header 与 value 名称（最终定为 `X-Iceberg-Access-Delegation` 与 `vended-credentials`/`remote-signing`）/ 调整描述以引用其他文档章节 / 调整描述排版。

## 如何达成设计目的

实现路径分三步，全部发生在 `open-api/rest-catalog-open-api.yaml` 单文件内：第一步在 `components/parameters` 段新增 `data-access` 参数定义，声明 `X-Iceberg-Access-Delegation` 请求头的名称、位置（header）、可选性（required: false）、schema（string 枚举，两个值）、风格（style: simple, explode: false，符合 OpenAPI 对简单逗号分隔头的约定）、示例（`"vended-credentials,remote-signing"`）以及描述（解释机制语义并交叉引用 `LoadTableResult` 与 `s3-signer-open-api.yaml`）；第二步在 `createTable` 操作（POST `/v1/{prefix}/namespaces/{namespace}/tables`）的 `parameters` 中通过 `$ref: '#/components/parameters/data-access'` 引入该头；第三步在 `loadTable` 操作（GET `/v1/{prefix}/namespaces/{namespace}/tables/{table}`）已有的 `parameters` 列表（原含 `snapshots` 等 query 参数）中追加同一 `$ref`。这样两个建/加载表的入口都支持该可选头，且定义集中、可维护。

## 修改详情

### open-api/rest-catalog-open-api.yaml

**修改目的**：为 REST Catalog 规范增加 `X-Iceberg-Access-Delegation` 请求头定义，并在 `createTable` 与 `loadTable` 两个操作中启用。

**工作逻辑**：

1. `createTable` 操作新增 `parameters` 引用：在 `operationId: createTable`（POST `/v1/{prefix}/namespaces/{namespace}/tables`）下，原本该操作没有显式 `parameters` 块（路径级参数 `prefix`、`namespace` 已在路径定义中），本次新增：
   ```yaml
   parameters:
     - $ref: '#/components/parameters/data-access'
   ```
   使建表请求可携带 `X-Iceberg-Access-Delegation` 头，客户端可在建表并立即加载的场景下声明支持的访问机制，服务端据此在返回的 `LoadTableResult` 中选择下发凭据或安排远端签名。

2. `loadTable` 操作追加参数引用：在 `operationId: loadTable`（GET `/v1/{prefix}/namespaces/{namespace}/tables/{table}`）原有的 `parameters` 列表（已含 `snapshots` 等 query 参数）首部追加：
   ```yaml
   - $ref: '#/components/parameters/data-access'
   ```
   使加载表请求同样支持该头，客户端在每次加载表时声明偏好的访问机制。

3. `components/parameters` 新增 `data-access` 参数定义（26 行）：
   - `name: X-Iceberg-Access-Delegation`：HTTP 头名，遵循 `X-Iceberg-*` 项目命名前缀。
   - `in: header`：声明这是请求头参数。
   - `description`（多行折叠）：说明这是客户端向服务端发出的可选信号，值为逗号分隔的访问机制列表，服务端可任选其一或都不选；并交叉引用——`vended-credentials` 的具体属性见本规范 `LoadTableResult` schema 章节，`remote-signing` 的协议见 `aws` 模块的 `s3-signer-open-api.yaml`。
   - `required: false`：可选头，客户端不携带时服务端按自身默认行为处理。
   - `schema`：`type: string`，`enum: [vended-credentials, remote-signing]`，限定单值只能取这两个枚举之一（但通过 `style: simple` + `explode: false` 允许逗号分隔多值传递）。
   - `style: simple`、`explode: false`：OpenAPI 风格约定，`simple` 风格按逗号分隔序列化多值，不展开，对应 HTTP 头的标准多值表达 `vended-credentials,remote-signing`。
   - `example: "vended-credentials,remote-signing"`：示例同时声明两种机制都支持。

整个修改是纯规范文档扩展，不涉及任何实现代码；定义可复用参数并在两处引用，符合 OpenAPI 的 DRY 原则。服务端实现可据此决定在 `LoadTableResult` 中是否填充 `config` 凭据（vended-credentials 路径）或返回 S3 签名所需信息（remote-signing 路径），客户端实现可据此在请求时设置头并在响应中按机制消费。

## 小结

本提交为 Iceberg REST Catalog OpenAPI 规范补充数据访问委托机制的客户端信号通道：新增可复用请求头参数 `X-Iceberg-Access-Delegation`（枚举值 `vended-credentials` 与 `remote-signing`，逗号分隔多值，可选），并在 `createTable` 与 `loadTable` 两个操作中通过 `$ref` 引入。客户端据此声明支持的访问机制，服务端可在两者中任选其一或都不选，凭据下发具体属性由 `LoadTableResult` schema 定义、远端签名协议由 `aws` 模块的 `s3-signer-open-api.yaml` 定义，描述中做了交叉引用。修改属于规范扩展，单文件 29 行新增，不涉及实现代码，为后续客户端与服务端在访问机制协商上提供标准化契约。
