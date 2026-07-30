# 提交 0554：为 namespaces/tables/views 列表 API 在 OpenAPI 规范中加入分页支持

## 提交信息

- **序号**：0554 / 4088
- **哈希**：acbf96f4fe5586fc7c7b6840eb19ff492e82c756
- **短哈希**：acbf96f4f
- **日期**：2024-02-29（Thu Feb 29 10:37:42 2024 -0800）
- **作者**：Rahil C <32500120+rahil-c@users.noreply.github.com>（与 Rahil Chertara <rchertar@amazon.com> 共同作者）
- **提交说明**：Add pagination to open api spec for listing of namespaces, tables, views (#9660)
- **PR/Issue**：#9660

## 总体目的

Iceberg REST Catalog 规范此前的三个列表端点——`listNamespaces`、`listTables`、`listViews`——都假定服务端能一次性返回全部结果。当一个 catalog 下有数以万计的 namespace 或表时，单次响应会非常大，带来：

- 服务端内存压力与序列化延迟；
- 网络传输成本与客户端超时风险；
- 客户端无法做增量加载/流式处理。

本提交的目的是为这三个列表端点在 OpenAPI 规范中加入统一的分页协议，让支持分页的服务端可以按页返回结果，同时保证**不支持分页的服务端与客户端能够无缝降级**（仍然一次返回全部结果）。这是一次纯规范层的扩展，不改动任何 Java/Spark/Flink 实现，目的是先把协议确定下来，再由后续 PR 在服务端和客户端实现具体的分页逻辑。

## 如何达成设计目的

设计者选择的是**基于 opaque token 的游标分页**（cursor-based pagination），而不是 offset/limit 风格。整体设计要点：

### 1. 两个查询参数

- `pageToken`（不透明字符串）：客户端在首次请求时发送空值（`?pageToken` 或 `?pageToken=`）表示"我希望分页"，服务端返回结果时附带 `next-page-token`；后续请求把上次响应里的 `next-page-token` 作为 `pageToken` 传入，即可拿到下一页。token 对客户端是不透明的，服务端可自由编码游标位置（如 last seen name、内部偏移、加密 token 等）。
- `pageSize`（整数，`minimum: 1`）：客户端建议的每页大小上界。服务端可以返回更少，但不返回更多；不支持分页的服务端会忽略它并返回所有结果。

### 2. 渐进式降级

设计者明确在 description 中说明：
- 支持分页的服务端识别 `pageToken`，并在还有更多结果时返回 `next-page-token`；没有更多结果时不返回该字段（或返回空）。
- 不支持分页的服务端会忽略 `pageToken` 与 `next-page-token`，仍一次性返回所有结果。

这意味着：旧客户端（不发送 `pageToken`）面对支持分页的服务端，会得到一次性全量结果（向后兼容）；新客户端面对不支持分页的服务端，也会得到一次性全量结果。协议完全前后兼容。

### 3. 参数复用

把 `page-token` 和 `page-size` 定义在 `components/parameters` 下，三个端点通过 `$ref` 复用，避免在每处重复定义，也保证三个端点的分页协议完全一致。

### 4. 响应字段

在 `ListTablesResponse` 与 `ListNamespacesResponse` 中新增 `next-page-token` 字段，类型为 `PageToken`（与 query 参数同类型）。这样响应天然带有"是否还有下一页"的信号：有 `next-page-token` 即可继续翻页；无则结束。

### 5. 双语言同步

与 0553 提交一致，本提交同时在 `rest-catalog-open-api.yaml` 与 `rest-catalog-open-api.py` 中建模，Python 端通过新增 `PageToken` 类与在两个 Response 类中加入 `next_page_token: Optional[PageToken]` 字段同步生成。

### 6. PR 评审轨迹

从提交说明可见，作者经过多轮评审迭代：
- 最初可能用了 `NextPageToken` 单独类型，最终简化为统一 `PageToken`（"use only PageToken instead of NextPageToken"）。
- `pageSize` 最初可能允许空值/默认值，最终改为 `minimum: 1` 的纯整数（"make int for pageSize"、"remove empty val true for pageSize"、"use min 1 for pageSize"）。
- 多轮调整 description 措辞以更准确表达"opaque token"与"渐进降级"语义。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在三个列表端点中加入分页参数、定义 `PageToken` schema 与 `page-token`/`page-size` 参数组件，并在两个列表响应中加入 `next-page-token` 字段。

**工作逻辑**：

1. **`paths./v1/{prefix}/namespaces`（listNamespaces）**：在原 `parameters` 数组开头加入两条 `$ref: '#/components/parameters/page-token'` 与 `page-size`。原 `parent` 参数保留。

2. **`paths./v1/{prefix}/namespaces/{namespace}/tables`（listTables）**：原本没有 `parameters` 段（该端点此前只有 path 参数），本提交新增 `parameters:` 段并加入 `page-token` 与 `page-size` 两个 `$ref`。

3. **`paths./v1/{prefix}/namespaces/{namespace}/views`（listViews）**：同上，新增 `parameters:` 段并加入两个分页参数。注意此端点的响应引用仍是 `#/components/responses/ListTablesResponse`（这是规范中预先存在的复用，本提交未改动）。

4. **`components.parameters.page-token`**：
   - `name: pageToken`、`in: query`、`required: false`、`allowEmptyValue: true`。
   - `allowEmptyValue: true` 是关键：它允许客户端发送 `?pageToken` 或 `?pageToken=`（无值）来发起首次分页请求，服务端据此识别"客户端想要分页"。
   - schema 引用 `#/components/schemas/PageToken`。

5. **`components.parameters.page-size`**：
   - `name: pageSize`、`in: query`、`required: false`。
   - description 明确"对支持分页的服务端是上界；对不支持的服务端客户端可能收到超过 `pageSize` 的结果"。
   - schema 是 `type: integer`、`minimum: 1`。

6. **`components.schemas.PageToken`**：
   - `type: string`，描述里详细说明使用流程：首次发空 `pageToken` → 服务端返回 `next-page-token` → 后续请求用该值作为 `pageToken` → 不支持分页的服务端忽略该字段返回全部。
   - 强调"opaque token"——客户端不应解析其内容，只应原样回传。

7. **`components.schemas.ListTablesResponse`** 与 **`ListNamespacesResponse`**：在 `properties` 中加入 `next-page-token: $ref PageToken`。两个响应的 `next-page-token` 都是可选的（未列入 `required`），表示"没有下一页时不出现"。

### `open-api/rest-catalog-open-api.py`

**修改目的**：同步生成 Pydantic 模型，使 Python 客户端能使用分页字段。

**工作逻辑**：

1. **新增 `PageToken` 类**：
   ```python
   class PageToken(BaseModel):
       __root__: str = Field(..., description='An opaque token which allows clients to make use of pagination for a list API (e.g. ListTables). ...')
   ```
   `__root__` 表示这是一个标量字符串类型（Pydantic v1 风格），description 把 YAML 中 `PageToken` 的描述完整搬过来。

2. **`ListTablesResponse`**：新增字段 `next_page_token: Optional[PageToken] = Field(None, alias='next-page-token')`。`alias` 让 Python 字段名（snake_case）与 JSON 字段名（kebab-case）解耦，序列化时正确产生 `next-page-token`。

3. **`ListNamespacesResponse`**：同样新增 `next_page_token: Optional[PageToken]`。

## 小结

本提交把"基于 opaque token 的游标分页"协议正式纳入 Iceberg REST Catalog 规范，覆盖 namespaces/tables/views 三个列表端点，且通过 `allowEmptyValue` + `next-page-token` 可选字段的设计实现了完整的前后兼容：旧客户端、新客户端、旧服务端、新服务端两两组合都能正确工作。

**影响范围**：
- 仅影响 `open-api/` 目录，不动任何运行时代码。
- 对支持分页的服务端，需要后续 PR 在服务端实现 token 编解码、按 `pageSize` 截断、返回 `next-page-token`；客户端则需要循环调用直到 `next-page-token` 缺失。
- 对不支持分页的服务端，本提交的改动完全透明——既不强制服务端实现分页，也不影响旧客户端行为。
- 由于 `next-page-token` 是新增的可选字段，对已有客户端的反序列化无破坏（未知字段通常被忽略）。

**回迁到 1.4.x 的注意事项**：
- 回迁非常安全，纯增字段、纯增参数、纯增 schema，不修改任何已有定义。
- 回迁后，1.4.x 的服务端实现可以选择**不实现分页**（继续一次返回全部结果），客户端即便发送 `pageToken` 也不会被识别——这完全符合协议的降级语义。因此即便服务端不实现，回迁规范本身也不会引入任何行为变化。
- 若 1.4.x 已有自己的分页实现（如基于 offset 的旧式分页），需评估是否切换为 token 风格以保持与 main 一致；但若 1.4.x 完全无分页，则直接回迁本提交即可作为未来实现的协议基础。
- 注意 Pydantic 模型使用 v1 风格（`__root__`、`regex=` 等），若 1.4.x 已升级到 Pydantic v2 需相应改写（与 0553 提交相同的注意点）。
- 规范层面有一个预先存在的复用：`listViews` 响应引用 `ListTablesResponse`（而非一个独立的 `ListViewsResponse`）。本提交未修复这一点，因此 `listViews` 的响应也会因继承 `ListTablesResponse` 而获得 `next-page-token` 字段——这恰好是期望行为，无需额外处理。但若后续 PR 拆分 `ListViewsResponse`，需要同步为其加上 `next-page-token`。
