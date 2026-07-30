# 提交 1647：OpenAPI: Changes for freshness-aware table loading (#11946)

## 提交信息

- **序号**：1647 / 4088
- **哈希**：e1d24016fa744150af8838e29f52662138671a45
- **短哈希**：e1d24016f
- **日期**：2025-01-28（Tue Jan 28 15:13:49 2025 +0100）
- **作者**：gaborkaszab <gaborkaszab@cloudera.com>
- **提交说明**：OpenAPI: Changes for freshness-aware table loading (#11946)
- **PR/Issue**：#11946

## 总体目的

Iceberg REST Catalog 的 `LoadTable` 接口（`GET /v1/{prefix}/namespaces/{namespace}/tables/{table}`）用于客户端加载表元数据。在大规模查询场景下，客户端（如 Spark/Trino 引擎）会频繁调用 LoadTable 检查表是否更新——每次都返回完整的 `LoadTableResult`（含完整 metadata JSON），即使表元数据自上次加载后未变化，造成不必要的网络传输与服务端序列化开销。

标准的 HTTP 缓存机制（`ETag` + `If-None-Match` + `304 Not Modified`）正是为这种"条件化获取"设计的：服务端在首次返回表元数据时附带 `ETag` 响应头（标识元数据版本）；客户端下次请求时带 `If-None-Match: <ETag>`，服务端比对后若元数据未变则返回 `304 Not Modified`（无 body），客户端复用缓存。这能显著降低带宽与服务端负载。

本提交为 REST Catalog OpenAPI 规范补齐这套"新鲜度感知"（freshness-aware）的表加载机制：在 LoadTable 接口增加 `If-None-Match` 请求头与 `304` 响应，定义 `ETag` 参数并在 `LoadTableResponse`/`CreateTableResponse` 等响应中返回 `ETag` 头。

## 如何达成设计目的

通过在 `open-api/rest-catalog-open-api.yaml` 中扩展规范实现：

1. **LoadTable 请求侧**：在 `GET /v1/.../tables/{table}` 的 parameters 中新增可选 `If-None-Match` 请求头（type string），描述说明其值为之前 CreateTable/LoadTable 响应收到的 ETag，服务端可据此返回 304；
2. **LoadTable 响应侧**：新增 `304` 响应码，描述 "Not Modified - Based on the content of the 'If-None-Match' header the table metadata has not changed since."；
3. **定义 ETag 参数**：在 `components/parameters` 中新增 `etag`（name `ETag`，in header，optional string），描述 "Identifies a unique version of the table metadata."，作为可复用的响应头定义；
4. **响应头注入**：在 `LoadTableResponse` 与 `CreateTableResponse`（实际是 `LoadTableResult` schema 的两个 response 引用）的 `headers` 中引用 `etag` 参数，使这两个响应都返回 `ETag` 头。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`（修改，+27）

**修改目的**：为 LoadTable 接口添加条件化获取支持，定义 ETag 响应头。

**工作逻辑**：

1. **LoadTable path 的 parameters**（`GET /v1/{prefix}/namespaces/{namespace}/tables/{table}`）：在 `data-access` 参数之后新增 `If-None-Match` header 参数：
   - `name: If-None-Match`、`in: header`、`required: false`、`schema: type: string`；
   - description："An optional header that allows the server to return 304 (Not Modified) if the metadata is current. The content is the value of the ETag received in a CreateTableResponse or LoadTableResponse."。

2. **LoadTable path 的 responses**：在 `200`（LoadTableResponse）之后新增 `304` 响应：
   - description："Not Modified - Based on the content of the 'If-None-Match' header the table metadata has not changed since."；
   - 无 content（304 不返回 body）。

3. **components/parameters 新增 `etag`**：
   - `name: ETag`、`in: header`、`required: false`、`schema: type: string`；
   - description："Identifies a unique version of the table metadata."。

4. **LoadTableResponse 与 CreateTableResponse 的 headers**：两处 response（均引用 `LoadTableResult` schema，分别用于 load 与 create 场景）的 content 之外新增 `headers: etag: $ref: '#/components/parameters/etag'`，使响应携带 ETag 头。

注意：OpenAPI 中 header 参数与 response header 复用同一 `parameters` 定义，故 `etag` 参数同时作为响应头被 `$ref` 引用。

## 小结

- **成效**：REST Catalog 规范支持基于 ETag/If-None-Match 的条件化表加载，客户端可用缓存避免重复下载未变化的元数据，降低带宽与服务端开销。这是标准 HTTP 缓存语义在 Iceberg REST 协议上的落地。
- **影响范围**：仅 OpenAPI 规范文件，不改 Java 实现。规范是新增可选请求头与响应头及 304 状态码，对不实现该机制的客户端透明（客户端不发送 If-None-Match 时服务端按原逻辑返回 200）。ETag 的具体取值规则（如 metadata 文件位置或哈希）由服务端实现决定，规范只约束其作为版本标识的语义。
- **回迁到 1.4.x 的注意事项**：纯规范变更，回迁安全。但若要让 1.4.x 的 REST 服务端真正支持 304，需在 Java 实现（`org.apache.iceberg.rest` 包）中添加 ETag 生成与 If-None-Match 比对逻辑，本提交未含实现。1.4.x 若已有 REST 服务端实现，回迁规范后客户端可能开始发送 If-None-Match，服务端若不识别会忽略该头仍返回 200，向前兼容。需注意 ETag 值的稳定性——同一元数据版本应产生相同 ETag，否则 304 机制失效。
