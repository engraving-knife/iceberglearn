# 提交 0677：Spec: Make request bodies required

## 提交信息
- **序号**：0677 / 4088
- **哈希**：1e666578744d26c8f778c3cf276827d111305b16
- **短哈希**：1e6665787
- **日期**：2024-04-12 11:28:59 -0600
- **作者**：westse
- **提交说明**：Spec: Make request bodies required (#10125)
- **PR/Issue**：#10125

## 总体目的

本提交对 Iceberg REST Catalog 的 OpenAPI 规范（`rest-catalog-open-api.yaml`）进行精确修正：为 8 个端点的 `requestBody` 显式添加 `required: true` 声明。在 OpenAPI 规范中，`requestBody` 若不显式标注 `required`，其默认值为 `false`（即可选请求体）。然而这 8 个端点的请求体在语义上都是必须的——缺少请求体，操作无法完成。将它们标注为必填，使规范准确反映 API 的实际行为契约。

受影响的端点包括：OAuth token 交换（`/v1/oauth/tokens`）、创建命名空间（`createNamespace`）、更新命名空间属性（`updateNamespaceProperties`）、设置命名空间属性（`setNamespaceProperties`）、注册表（`registerTable`）、创建表（`createTable`）、创建视图（`createView`）、提交视图更新（`updateView`/commit view）。这些操作无一例外地需要客户端在请求体中提供结构化数据（如建表所需的 schema/properties、注册表所需的元数据、token 交换所需的凭证等），不存在"不传请求体也能成功"的情形。

修正规范的意义在于：(1) 提升 API 文档的准确性，让规范读者与 API 消费方明确这些端点必须携带请求体；(2) 当使用 openapi-generator 等工具从规范生成客户端 SDK 或服务端桩代码时，生成的代码会强制要求传入请求体参数，避免调用方遗漏；(3) 服务端校验层可据此在反序列化前就拒绝空请求体的请求，提前返回 400 而非进入业务逻辑后失败。

## 如何达成设计目的

策略非常直接：逐一为这 8 个 `requestBody` 节点添加 `required: true` 这一行。这是一种纯规范层面的声明性修正，不触及任何代码逻辑。修改集中在一个文件中，通过 8 处相同的模式化添加完成。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`
**修改目的**：为 8 个端点的 `requestBody` 添加 `required: true`，使请求体在规范层面为必填。
**工作逻辑**：在以下 8 处的 `requestBody:` 节点下、`content:` 之前插入 `required: true`：
1. OAuth token 交换端点（`/v1/oauth/tokens` 的 POST）——token 交换必须携带 subject token 等表单数据。
2. `createNamespace`——创建命名空间需提供 `CreateNamespaceRequest`（含 properties）。
3. `updateNamespaceProperties`——更新属性需提供 `UpdateNamespacePropertiesRequest`（含 removals/updates）。
4. `setNamespaceProperties`（`/{prefix}/namespaces/{namespace}/properties` 的 POST）——设置属性需提供 `NamespacePropertiesRequest`。
5. `registerTable`——注册外部表需提供 `RegisterTableRequest`（含 metadata_location）。
6. `createTable`——建表需提供 `CreateTableRequest`（含 schema、partition spec 等）。
7. `createView`——建视图需提供 `CreateViewRequest`。
8. `updateView`（commit view，`/{prefix}/namespaces/{namespace}/views/{view}` 的 POST）——提交视图更新需提供 `CommitViewRequest`（含 updates）。

每处修改均是在 `requestBody:` 与 `content:` 之间补一行 `        required: true`（缩进对齐），不改动 schema 定义本身。

## 小结
- **成效**：成功达成目的。8 个端点的请求体现在在规范中明确为必填，规范与实际 API 契约一致。
- **影响范围**：仅 REST Catalog OpenAPI 规范文件，不影响已有 Java/Python 实现代码。影响面在于基于该规范生成的客户端/服务端桩代码会开始强制要求请求体。
- **回迁到 1.4.x 的注意事项**：纯规范修改，回迁无风险。若 1.4.x 分支的 REST 规范文件与 main 有分叉（如端点集合不同），需按相同原则逐一核对哪些 `requestBody` 应标注 `required: true`，不能机械地套用 main 的行号。
