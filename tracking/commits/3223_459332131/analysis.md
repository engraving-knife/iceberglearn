# 提交 3223：REST: add support for data access parameter to registerTable (#15231)

## 提交信息

- **序号**：3223 / 4088
- **哈希**：459332131f429484177d45acabea8903a45acc4e
- **短哈希**：459332131
- **日期**：2026-02-09
- **作者**：Alexandre Dutra
- **提交说明**：REST: add support for data access parameter to registerTable (#15231)
- **PR/Issue**：#15231

## 总体目的

Iceberg 的 REST Catalog OpenAPI 规范（`open-api/rest-catalog-open-api.yaml`）定义了客户端与 REST Catalog 服务交互的契约。其中 `registerTable` 操作允许客户端在指定 namespace 下通过给定元数据文件位置注册一张已存在的表。REST Catalog 中有一套统一的"数据访问委托"机制：客户端通过请求头 `X-Iceberg-Access-Delegation`（即规范中的 `data-access` 参数）向服务端声明自己支持哪些数据访问方式——`vended-credentials`（由服务端下发短期凭证）或 `remote-signing`（由服务端代为对存储请求签名）。服务端可据此在 `LoadTableResult` 的配置里下发 vended credentials，或参与远程签名，使客户端无需自行持有长期存储凭证即可访问表数据。

在本次改动之前，`createTable`、`loadTable` 等读写表数据的端点都已在 `parameters` 中引用 `#/components/parameters/data-access`，唯独 `registerTable` 遗漏了这一引用。这导致一个语义不对称：`registerTable` 注册的表同样会产生后续的数据访问需求（注册成功后客户端就要读取该表的元数据与数据文件），但客户端却无法在注册请求中表达自己期望的访问委托方式，服务端也就无法在注册响应中一致地按相同机制下发凭证或签名支持。本提交通过在 `registerTable` 的参数列表中补上对 `data-access` 的 `$ref` 引用，补齐这一缺口，使注册表流程与创建/加载表流程在数据访问委托能力上保持一致。

## 如何达成设计目的

整体思路是 OpenAPI 规范层面的最小化一致性修补：复用规范中已定义的 `data-access` 参数组件（HTTP 头 `X-Iceberg-Access-Delegation`，可选，枚举值为 `vended-credentials`/`remote-signing`），在 `registerTable` 路径的 `parameters` 数组里追加一条 `$ref` 引用，使其与同区的 `createTable`、`loadTable` 等端点保持相同的数据访问委托入口。改动仅涉及规范文件一处单行新增，不涉及任何 Java/服务端实现代码，是契约层面的对齐。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+1/-0 lines)

**修改目的**：为 `registerTable` 端点补齐数据访问委托参数引用。

**工作逻辑**：
在 `paths` 下 `registerTable` 操作（`operationId: registerTable`，用于"Register a table in the given namespace using given metadata file location"）的 `parameters` 数组中，新增一行 `- $ref: '#/components/parameters/data-access'`，并置于既有的 `- $ref: '#/components/parameters/idempotency-key'` 之前，与 `createTable`、`loadTable` 等端点的参数排列习惯一致。

被引用的 `data-access` 参数组件定义在 `components/parameters` 下，其内容为：名为 `X-Iceberg-Access-Delegation`、位于 `header`、`required: false` 的字符串参数，`schema` 为枚举 `["vended-credentials", "remote-signing"]`，`style: simple`、`explode: false`，示例 `"vended-credentials,remote-signing"`；其描述说明这是客户端向服务端表达支持何种委托访问机制的可选信号，服务端可选择以任一或都不采用。

补齐该引用后，`registerTable` 与其它表操作端点一样接受可选的 `X-Iceberg-Access-Delegation` 头，服务端实现据此可在注册响应（`LoadTableResponse`/`LoadTableResult`）中按相同规则下发 vended credentials 或参与远程签名，使注册表后的数据访问获得与 `loadTable` 一致的委托访问能力。本次仅修改规范契约，未改服务端实现代码，实际行为取决于各 REST Catalog 实现是否据此读取该头并处理。

## 总结

本提交在 REST Catalog OpenAPI 规范中为 `registerTable` 端点补上了对 `data-access`（`X-Iceberg-Access-Delegation` 头）参数的引用，补齐了此前仅 `createTable`/`loadTable` 等端点才具备的数据访问委托入口，使注册表流程在 vended-credentials / remote-signing 委托访问能力上与其它表操作保持一致；改动为单行 `$ref` 新增，属契约层面一致性修补，不涉及实现代码。
