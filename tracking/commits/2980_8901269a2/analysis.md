# 提交 2980：OpenAPI: Add idempotency key for the mutating plan endpoints (#14730)

## 提交信息

- **序号**：2980 / 4088
- **哈希**：8901269a21853199e7b297756308cd9e208a0342
- **短哈希**：8901269a2
- **日期**：2025-12-08
- **作者**：Prashant Singh
- **提交说明**：OpenAPI: Add idempotency key for the mutating plan endpoints (#14730)
- **PR/Issue**：#14730

## 总体目的

Iceberg REST Catalog 协议已经为许多会改变服务端状态（mutating）的端点支持了 `Idempotency-Key` 请求头，允许客户端在安全重试时携带同一个 key，服务端确保重复请求不会产生额外副作用并返回等价的最终响应。规范中已经存在一个可复用的 `idempotency-key` 参数（位于 `components/parameters`），并已被 commit 事务、创建/更新命名空间、创建表等多个端点通过 `$ref` 引用。

本提交的目的是把同样的幂等性保证扩展到三个"会改变服务端状态的 plan（计划扫描任务）端点"：`planTableScan`（启动表扫描规划）、`cancelPlanning`（取消规划）、`fetchScanTasks`（按 plan task 拉取扫描任务结果）。这三个端点虽然语义上是"读"数据，但它们会在服务端创建/修改 planning 会话状态（plan-id、plan task 队列、缓存等），属于"mutating planning"操作。在网络抖动或客户端重试场景下，重复调用可能导致服务端创建重复的 planning 会话或产生其他副作用。为这三个端点补上 `Idempotency-Key`，使客户端在重试时能获得与首次请求等价的最终响应，不会因为重试而放大服务端负担或造成状态错乱。

这是纯 OpenAPI 规范改动，只新增了对既有 `idempotency-key` 参数的 `$ref` 引用，没有改动参数定义本身，也不涉及代码生成产物的更新。

## 如何达成设计目的

整体思路非常直接：复用规范中已定义的 `components/parameters/idempotency-key` 参数，在三个 plan 端点的 `parameters` 列表中通过 `$ref: '#/components/parameters/idempotency-key'` 引用。该参数定义为可选（`required: false`）、类型为 UUID 字符串（`format: uuid`，固定 36 字符长度），并附带 finalize & replay 规则（200/201/204 与确定性 4xx 终态会被服务端缓存并重放，5xx 不会被缓存）。改动文件仅 `open-api/rest-catalog-open-api.yaml` 一个，共 6 行新增。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+6/-0 lines)

**修改目的**：为三个 mutating plan 端点补上 `Idempotency-Key` 请求头支持。

**工作逻辑**：
在 `paths` 下三个端点的定义中分别新增 `parameters: - $ref: '#/components/parameters/idempotency-key'`：

1. `planTableScan`（`POST /v1/{prefix}/namespaces/{namespace}/tables/{table}/plan`，operationId `planTableScan`）：启动表扫描规划，会创建 plan-id 并在服务端维护 planning 状态。新增 `parameters` 字段引用 `idempotency-key`。

2. `cancelPlanning`（operationId `cancelPlanning`）：取消某个 plan-id 的扫描规划，会改变服务端 planning 会话状态。新增引用。

3. `fetchScanTasks`（operationId `fetchScanTasks`）：按 plan task 拉取扫描任务结果，同样会推进/消费服务端 planning 状态。新增引用。

引用的 `idempotency-key` 参数本身无需新增——它在 `components/parameters` 中已存在且已被多个其他 mutating 端点（如 commit、create table、create namespace 等）复用，定义包括：`name: Idempotency-Key`、`in: header`、`required: false`、`schema.type: string`、`format: uuid`、`minLength/maxLength: 36`，以及 finalize & replay 语义说明（同 key 重试时服务端返回等价最终响应，不重复执行操作；200/201/204 与确定性 4xx 终态会被缓存重放，5xx 不缓存）。因此本次改动只是把既有幂等性契约扩展到这三个 plan 端点，客户端在重试这些端点时即可享受与 commit 等端点一致的幂等性保证。

## 总结

该提交为 Iceberg REST Catalog OpenAPI 规范中的三个 mutating plan 端点（`planTableScan`、`cancelPlanning`、`fetchScanTasks`）补上了 `Idempotency-Key` 请求头支持，通过 `$ref` 复用既有的 `idempotency-key` 参数定义。核心价值在于让客户端在网络重试这些会改变服务端 planning 状态的端点时获得幂等性保证，避免重复调用产生副作用，与协议中其他 mutating 端点的行为保持一致。改动仅 6 行 YAML，是纯协议契约层面的增强。
