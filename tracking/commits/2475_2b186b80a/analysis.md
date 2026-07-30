# 提交 2475：OpenAPI: Correct type annotation in TableMetadat#encryption-keys field (#13762)

## 提交信息

- **序号**：2475 / 4088
- **哈希**：2b186b80a5ffaaa25398a2a2064362939f782b3c
- **短哈希**：2b186b80a
- **日期**：2025-08-08 15:15:39 +0200
- **作者**：Blake Smith
- **提交说明**：OpenAPI: Correct type annotation in TableMetadat#encryption-keys field (#13762)
- **PR/Issue**：#13762

## 总体目的

该提交修正了 OpenAPI 规范文件中 `TableMetadata` 的 `encryption-keys` 字段的类型标注错误，将无效的 `type: list` 改为正确的 `type: array`。

在 OpenAPI（Swagger）规范中，数组类型的标准关键字是 `array`，而非 `list`。`TableMetadata` schema 的 `encryption-keys` 字段被错误地标注为 `type: list`，这不符合 OpenAPI 规范，可能导致生成的客户端代码、文档或校验工具出现错误。修正为 `type: array` 使其符合 OpenAPI 规范的标准类型定义。

## 如何达成设计目的

直接将 `open-api/rest-catalog-open-api.yaml` 文件中 `encryption-keys` 字段的 `type: list` 修改为 `type: array`。这是一处单行修改，不涉及其他逻辑变更。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+1/-1 lines)

**修改目的**：修正 `encryption-keys` 字段的类型标注。

**工作逻辑**：

修改前：
```yaml
encryption-keys:
  type: list
  items:
    $ref: '#/components/schemas/EncryptedKey'
```

修改后：
```yaml
encryption-keys:
  type: array
  items:
    $ref: '#/components/schemas/EncryptedKey'
```

将 `type: list` 改为 `type: array`，使其符合 OpenAPI 规范中数组类型的标准定义。`items` 引用保持不变，仍指向 `EncryptedKey` schema。

## 总结

这是一个单行修正提交，将 OpenAPI 规范中 `TableMetadata` 的 `encryption-keys` 字段类型从无效的 `list` 改为规范的 `array`。该提交虽小但重要，确保了 REST Catalog OpenAPI 规范的合规性，避免因类型标注错误导致代码生成或校验工具出现问题。
