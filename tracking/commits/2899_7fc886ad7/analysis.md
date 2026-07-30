# 提交 2899：OpenAPI: Add storage-credentials to CompletedPlanningResult

## 提交信息

- **序号**：2899 / 4088
- **哈希**：7fc886ad751eaf4f53915ca0da69bdb8c2c1e72b
- **短哈希**：7fc886ad7
- **日期**：2025-11-20 15:55:13 +0100
- **作者**：ajreid21
- **提交说明**：OpenAPI: Add storage-credentials to CompletedPlanningResult
- **PR/Issue**：#14563

## 总体目的

本提交的目的在于完善 REST Catalog OpenAPI 规范中 `CompletedPlanningResult` 的定义，为其新增 `storage-credentials` 字段。

在 Iceberg 的 REST Catalog 规范中，扫描规划（scan planning）完成后会返回 `CompletedPlanningResult`，其中包含 `FileScanTasks`，即客户端需要读取的数据文件信息。然而，当底层存储需要特定的访问凭证时（例如 S3、GCS 等对象存储的临时凭证），规范此前并未在 `CompletedPlanningResult` 中明确定义返回这些凭证的字段。

通过在 `CompletedPlanningResult` 中新增 `storage-credentials` 字段，服务器可以在扫描规划完成时一并下发存储凭证，客户端据此即可使用这些凭证读取返回的 FileScanTasks 中的文件。这一设计使得扫描规划结果自包含（self-contained），客户端无需额外请求即可完成文件读取，减少了往返通信开销，同时与 Iceberg REST Catalog 中其他端点（如加载表、提交事务等）下发 storage-credentials 的既有模式保持一致。

## 如何达成设计目的

该提交通过修改 OpenAPI 规范的两个表示文件来实现：

1. `rest-catalog-open-api.yaml`：YAML 格式的 OpenAPI 规范定义文件，在其中 `CompletedPlanningResult` schema 下新增 `storage-credentials` 数组字段，其 items 引用既有的 `StorageCredential` schema。
2. `rest-catalog-open-api.py`：由 YAML 规范生成的 Python（Pydantic）模型文件，同步新增对应的 `storage_credentials` 字段，使用 `Optional[List[StorageCredential]]` 类型并设置 alias 为 `storage-credentials`。

字段被设为可选（Optional / 无 required 标记），表明服务器可选择性地返回凭证，保证了向后兼容性。字段描述明确说明了客户端的预期行为：若服务器在完成扫描规划响应中返回了 storage credentials，客户端应使用这些凭证读取扫描结果中的文件。

## 修改详情

### `open-api/rest-catalog-open-api.py` (+5/-0 lines)

**修改目的**：在 Python Pydantic 模型 `CompletedPlanningResult` 中新增 `storage_credentials` 字段。

**工作逻辑**：
在 `CompletedPlanningResult` 类中，紧随 `status` 字段之后新增 `storage_credentials` 字段：

```python
storage_credentials: Optional[List[StorageCredential]] = Field(
    None,
    alias='storage-credentials',
    description='Storage credentials for accessing the files returned in the scan result.\nIf the server returns storage credentials as part of the completed scan planning response, the expectation is for the client to use these credentials to read the files returned in the FileScanTasks as part of the scan result.',
)
```

字段类型为 `Optional[List[StorageCredential]]`，默认值为 `None`，并使用 `alias='storage-credentials'` 以匹配 YAML 规范中 kebab-case 命名约定。

### `open-api/rest-catalog-open-api.yaml` (+10/-0 lines)

**修改目的**：在 YAML OpenAPI 规范的 `CompletedPlanningResult` schema 中新增 `storage-credentials` 字段定义。

**工作逻辑**：
在 `CompletedPlanningResult` 的 properties 中，紧随 `status` 之后新增 `storage-credentials` 数组字段：

```yaml
storage-credentials:
  type: array
  description:
    Storage credentials for accessing the files returned in the scan result.

    If the server returns storage credentials as part of the completed scan
    planning response, the expectation is for the client to use these credentials
    to read the files returned in the FileScanTasks as part of the scan result.
  items:
    $ref: '#/components/schemas/StorageCredential'
```

字段为 array 类型，其 items 引用已存在的 `StorageCredential` schema，未列入 required 列表，因此是可选字段。

## 总结

本提交是一个纯规范层面的增强，为 REST Catalog OpenAPI 的 `CompletedPlanningResult` 新增了 `storage-credentials` 可选字段，使服务器能够在扫描规划完成响应中下发存储访问凭证。修改仅涉及 OpenAPI 规范定义文件（yaml 与生成的 py 模型），不涉及 Java/客户端实现代码。该字段设为可选，保证了向后兼容，并与 REST Catalog 其他端点下发凭证的既有模式保持一致。
