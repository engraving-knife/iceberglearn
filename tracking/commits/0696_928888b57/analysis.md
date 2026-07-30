# 提交 0696：OpenAPI: Renaming views should return 204

## 提交信息
- **序号**：0696 / 4088
- **哈希**：928888b579c456ceb82d2771c7ab9f07cc73ad53
- **短哈希**：928888b57
- **日期**：2024-04-17
- **作者**：c-thiel
- **提交说明**：OpenAPI: Renaming views should return 204 (#10166)
- **PR/Issue**：#10166

## 总体目的

本提交修正 Iceberg REST Catalog OpenAPI 规范中"重命名视图（Rename View）"接口的响应状态码定义。

在 RESTful API 设计规范中，当一个操作执行成功但不返回任何响应体内容时，应当使用 HTTP 状态码 `204 No Content` 而非 `200 OK`。`200 OK` 通常表示请求成功并且响应体中包含有用的内容，而 `204 No Content` 则明确表示服务器已成功处理请求但无需返回任何实体内容。

重命名视图操作本身是一个"动作"类接口（通过 `UpdateTableRequirement` / `RenameTableRequest` 类似的请求体提交变更），成功执行后并不需要向客户端返回视图的完整定义或其他业务数据。因此，按照 REST 规范应当返回 204 状态码。此前的 OpenAPI 规范将重命名视图接口的成功响应错误地定义为 `200 OK`，这与 Iceberg REST Catalog 规范中其他类似的无返回内容操作（如删除操作通常使用 204）不一致，也与实际实现行为存在偏差。

本提交将这一规范定义修正为 `204 Success, no content`，使 OpenAPI 文档与正确的 REST 语义保持一致，并为客户端实现提供准确的契约定义。

## 如何达成设计目的

修改方式非常直接：在 OpenAPI YAML 规范文件中，定位到重命名视图接口（`POST /v1/{prefix}/namespaces/{namespace}/views/rename`）的 `responses` 定义部分，将成功响应的状态码从 `200`（描述为 "OK"）修改为 `204`（描述为 "Success, no content"）。

这是一个纯文档层面的修改，不涉及任何代码逻辑变更，仅影响 OpenAPI 规范文档本身的准确性。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`
**修改目的**：修正重命名视图接口的成功响应状态码定义，使其符合 REST 规范。

**工作逻辑**：在该文件第 1386-1389 行附近的 `RenameView` 路径定义中，将 `responses` 部分的成功响应从：
```yaml
200:
  description: OK
```
修改为：
```yaml
204:
  description: Success, no content
```
其余错误响应（400 BadRequest、401 Unauthorized 等）保持不变。这样客户端在依据 OpenAPI 规范生成代码或编写调用逻辑时，会正确预期重命名视图成功时收到 204 状态码而非 200。

## 小结
- **成效**：成功修正了 OpenAPI 规范文档中的响应状态码定义，使其符合 REST 规范中"无内容成功响应使用 204"的约定。
- **影响范围**：仅影响 `open-api/rest-catalog-open-api.yaml` 规范文件，影响所有依据该规范实现或调用 Iceberg REST Catalog "重命名视图"接口的客户端。不改变任何服务端运行时行为，但可能影响严格依据状态码判断的客户端逻辑。
- **回迁到 1.4.x 的注意事项**：此修改为纯文档层面变更，回迁无技术风险。需确认 1.4.x 分支的 OpenAPI 规范文件中该接口路径与 main 分支一致；若 1.4.x 中重命名视图接口尚不存在或路径结构不同，则无需回迁。同时应确认 1.4.x 的 REST Catalog 服务端实现是否已返回 204（若服务端仍返回 200 而仅文档改为 204，则会导致文档与实现不一致）。
