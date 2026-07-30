# 提交 2895：REST Spec: Add Idempotency-Key to OpenAPI (#14196)

## 提交信息

- **序号**：2895 / 4088
- **哈希**：f60b582eb24e74af9e8b583def3cb4c2f55a03b6
- **短哈希**：f60b582eb
- **日期**：2025-11-19 16:03:40 -0800
- **作者**：Huaxin Gao
- **提交说明**：REST Spec: Add Idempotency-Key to OpenAPI (#14196)
- **PR/Issue**：#14196

## 总体目的

本提交为 Iceberg REST Catalog 的 OpenAPI 规范引入幂等键（Idempotency-Key）支持，使客户端在重试变更（mutation）操作时能够安全地避免重复副作用。

在分布式环境中，客户端向 REST Catalog 发起提交请求后，可能因为网络超时、连接中断或服务端 5xx 错误而无法确认请求是否成功。此时客户端通常需要重试，但对于创建表、提交事务、重命名等有副作用的操作，盲目重试可能导致重复创建、重复提交数据文件等问题。引入幂等键机制后，客户端在首次请求时携带一个全局唯一的 `Idempotency-Key`，服务端据此识别并去重：若该键对应的请求已最终化（finalized），服务端直接返回等价的最终响应而不再执行任何额外副作用。

本次修改仅涉及规范层面（OpenAPI 定义），并未改动任何服务端或客户端的实现代码，而是先确立契约，供后续各 catalog 实现与客户端 SDK 逐步对接。规范定义了键的格式（UUIDv7，36 字符字符串）、生命周期语义（通过 `idempotency-key-lifetime` 声明服务端承诺的重用窗口）、最终化与重放规则（200/201/204 及确定性终态 4xx 会最终化并重放，5xx 不最终化），并明确了客户端在重试同一逻辑操作时必须复用同一键、对不同操作必须生成新键的要求。

## 如何达成设计目的

修改集中在两个 OpenAPI 规范文件：`rest-catalog-open-api.yaml`（规范主体）和 `rest-catalog-open-api.py`（由规范生成的 Python datamodel 模型）。整体思路是：先在 `components/parameters` 中定义可复用的 `idempotency-key` 请求头参数，再将该参数通过 `$ref` 引用到全部变更端点；同时在 `CatalogConfig` 响应模型中新增 `idempotency-key-lifetime` 字段，让服务端向客户端声明其幂等支持与重用窗口。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+65/-1 lines)

**修改目的**：在 OpenAPI 规范中定义 Idempotency-Key 请求头参数，并将其应用到所有变更端点；同时在 catalog 配置响应中声明幂等键生命周期。

**工作逻辑**：
- **新增 `idempotency-key` 参数定义**：在 `components/parameters` 下新增 `idempotency-key`，类型为 `header`、可选（`required: false`）、`format: uuid`、`minLength/maxLength: 36`，示例为 `"017F22E2-79B0-7CC3-98C4-DC0C0C07398F"`。其描述详细规定了语义：服务端对携带相同键的请求保证不产生额外副作用；若先前请求已最终化则返回等价最终响应（响应体可能反映比提交时刻更新的 catalog 状态）。最终化规则明确 200/201/204 及确定性终态 4xx（含 409 如 AlreadyExists、NamespaceNotEmpty 等）会被最终化并重放，5xx 不最终化也不存储/重放。键格式要求 UUIDv7（RFC 9562），必须全局唯一，且客户端重试同一逻辑操作须复用同一键、不同操作须生成新键。

- **应用到变更端点**：通过 `- $ref: '#/components/parameters/idempotency-key'` 将该参数引用添加到以下 12 个变更端点：`createNamespace`、`dropNamespace`、`updateProperties`（命名空间属性）、`createTable`、`registerTable`、`updateTable`、`dropTable`、`renameTable`、`commitTransaction`、`replaceView`、`dropView`、`renameView`。对于原本已有 `parameters` 字段的端点（如 `createTable`、`dropTable`）则在现有参数列表中追加引用，其余端点新增 `parameters` 字段。

- **新增 `idempotency-key-lifetime` 配置字段**：在 `CatalogConfig` 的 schema（`components/schemas/CatalogConfig` 的 `overrides` 属性下）新增 `idempotency-key-lifetime`，类型 `string`、`format: duration`，描述其为 ISO-8601 时长（如 `PT30M`、`PT24H`），表示从首次提交到最后一次重试期间客户端可复用该键的最大窗口；服务端应在该时长内接受重试并可附加宽限期；客户端不应在窗口过期后复用键。该字段的存在表示服务端支持变更端点的幂等键语义，缺省则客户端必须假定不支持幂等。

- **更新配置响应示例**：在 `/v1/config` 端点的响应示例中加入 `"idempotency-key-lifetime": "PT30M"`，与新增字段保持一致。

### `open-api/rest-catalog-open-api.py` (+7/-0 lines)

**修改目的**：同步更新由 OpenAPI 规范生成的 Python datamodel 模型，使 `CatalogConfig` 类包含新增的幂等键生命周期字段。

**工作逻辑**：在 `CatalogConfig`（继承 `BaseModel`）中新增 `idempotency_key_lifetime` 字段，类型为 `Optional[timedelta]`，使用 `Field` 并设置别名 `alias='idempotency-key-lifetime'` 以匹配规范中的连字符键名。`description` 与 `example='PT30M'` 与 YAML 规范保持一致。为此在文件顶部 import 中补充了 `timedelta`（`from datetime import date, timedelta`）。该文件通常由规范自动生成，此处随 YAML 一同更新以保持两者同步。

## 总结

本提交为 Iceberg REST Catalog 规范引入了幂等键机制，这是提升提交类操作在不可靠网络下安全性的重要能力。通过定义标准化的 `Idempotency-Key` 请求头（UUIDv7）和 `idempotency-key-lifetime` 配置声明，规范了客户端重试去重与服务端最终化/重放的行为契约，并将其应用到全部 12 个变更端点。该改动仅落在规范层面，为后续服务端实现与客户端 SDK 适配奠定基础。
