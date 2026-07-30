# 提交 3787：REST Spec: Add unregister table endpoint (#16400)

## 提交信息

- **序号**：3787 / 4088
- **哈希**：d2cde2950460bcb86a69ee10c98de962b5557b2a
- **短哈希**：d2cde2950
- **日期**：2026-05-26 10:04:15 -0700
- **作者**：Ryan Blue
- **提交说明**：REST Spec: Add unregister table endpoint (#16400)
- **PR/Issue**：#16400

## 总体目的

这个提交为 Iceberg REST Catalog OpenAPI 规范添加了"注销表"（unregister table）端点。这个端点的作用是从 catalog 中移除表的注册，但保留底层的数据文件和元数据文件，与 `registerTable` 操作相反。

使用场景：当需要将表从一个 catalog 迁移到另一个 catalog 时，可以先在原 catalog 中注销表（保留数据），然后在目标 catalog 中注册表。这与 `dropTable` 不同，后者可能会删除数据文件。

端点设计为返回表最后的元数据位置和完整的表元数据，确保：
1. 注销操作前所有提交都包含在返回的元数据中。
2. 注销后该 catalog 上的所有提交操作必须失败。
3. 返回的元数据位置可用于在另一个 catalog 中注册表。

## 如何达成设计目的

在 REST Catalog OpenAPI 规范（YAML 和 Python 版本）中新增：
1. `POST /v1/{prefix}/namespaces/{namespace}/tables/{table}/unregister` 端点。
2. `UnregisterTableResult` 响应模式，包含 `metadata-location` 和 `metadata` 字段。
3. `UnregisterTableResponse` 响应定义。
4. 支持幂等性密钥（idempotency-key）参数。

## 修改详情

### `open-api/rest-catalog-open-api.py` (+13/-0 lines)

**修改目的**：在 Python 版本的 OpenAPI 模型中添加注销表响应。

**工作逻辑**：
新增 `UnregisterTableResult` Pydantic 模型：
```python
class UnregisterTableResult(BaseModel):
    """Last metadata location and the corresponding table metadata for the table
    that was successfully unregistered and is no longer tracked by the catalog."""
    metadata_location: str = Field(..., alias='metadata-location',
        description='The last metadata location for the table at the time it was unregistered.')
    metadata: TableMetadata
```

### `open-api/rest-catalog-open-api.yaml` (+74/-0 lines)

**修改目的**：在 YAML 版本的 OpenAPI 规范中添加完整的注销表端点定义。

**工作逻辑**：

1. **端点路径**：`/v1/{prefix}/namespaces/{namespace}/tables/{table}/unregister`，使用 POST 方法。

2. **端点描述**：
   - 从 catalog 注销表，与 `registerTable` 相反。
   - 表从 catalog 中消失，但数据文件和元数据文件保留。
   - 成功时返回最后的元数据位置和表元数据。
   - 注销前所有提交必须包含在元数据中，注销后所有提交必须失败。

3. **参数**：`prefix`、`namespace`、`table` 路径参数，以及可选的 `idempotency-key`。

4. **响应**：
   - 200：`UnregisterTableResponse`，包含 `UnregisterTableResult`。
   - 400：BadRequest
   - 401：Unauthorized
   - 403：Forbidden
   - 404：NoSuchTableException（表不存在）
   - 419：AuthenticationTimeout
   - 503：ServiceUnavailable
   - 5XX：ServerError

5. **`UnregisterTableResult` Schema**：包含 `metadata-location`（string）和 `metadata`（TableMetadata）两个必填字段。

6. **`UnregisterTableResponse`**：响应定义，内容类型为 `application/json`，引用 `UnregisterTableResult` schema。

## 总结

这个提交为 Iceberg REST Catalog 规范添加了注销表端点，提供了一种安全地从 catalog 移除表注册而不删除数据文件的机制。返回的元数据位置和表元数据使表可以在另一个 catalog 中重新注册。这是 REST Catalog 规范的重要扩展，支持表迁移和 catalog 间转移场景。该提交由 Ryan Blue 编写，并标注了 Claude Code AI 辅助共同创作。
