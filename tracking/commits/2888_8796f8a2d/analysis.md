# 提交 2888：OpenAPI: Add planId as query param to /credentials endpoint (#14519)

## 提交信息

- **序号**：2888 / 4088
- **哈希**：8796f8a2d01e6d7d712d79dc27a9fb5b4b0720a1
- **短哈希**：8796f8a2d
- **日期**：2025-11-19 07:41:41 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：OpenAPI: Add planId as query param to /credentials endpoint (#14519)
- **PR/Issue**：#14519

## 总体目的

Iceberg REST Catalog 规范定义了 `/credentials` 端点，用于客户端从 catalog 获取表级别的临时访问凭证（vended credentials），这些凭证用于访问底层存储（如 S3、GCS 等）。

随着服务端扫描规划（server-side scan planning）功能的引入，凭证刷新需要额外的上下文信息。具体来说，当服务端执行扫描规划时，会为每个规划任务分配一个 `planId`。在刷新用于服务端扫描规划的凭证时，catalog 需要知道该凭证关联的是哪个规划任务，以便正确管理凭证的生命周期和权限范围。

此提交在 OpenAPI 规范中为 `/credentials` 端点新增 `planId` 查询参数，使客户端在请求凭证时可以携带规划任务 ID，为服务端凭证刷新提供必要的上下文。

## 如何达成设计目的

在 OpenAPI YAML 规范文件的 `/credentials` 路径定义中，新增 `parameters` 区段，定义 `planId` 查询参数：
- 参数名：`planId`
- 位置：`query`（查询参数）
- 是否必需：`false`（可选，保持向后兼容）
- 类型：`string`
- 描述：用于服务端扫描规划的规划 ID

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+7/-0 lines)

**修改目的**：为 `/credentials` 端点添加 planId 查询参数定义。

**工作逻辑**：在 `loadCredentials` 操作（operationId）下新增 `parameters` 列表，包含一个参数定义：
```yaml
parameters:
  - name: planId
    in: query
    required: false
    schema:
      type: string
    description: The plan ID that has been used for server-side scan planning
```
该参数为可选（`required: false`），确保不携带此参数的旧客户端仍能正常使用 `/credentials` 端点，保持向后兼容。

## 总结

该提交在 Iceberg REST Catalog OpenAPI 规范中为 `/credentials` 端点新增了可选的 `planId` 查询参数。这是服务端扫描规划功能的配套修改，使 catalog 在刷新凭证时能获取规划任务的上下文信息。参数为可选，保持向后兼容。这是一个纯规范文档修改，不涉及代码实现变更。
