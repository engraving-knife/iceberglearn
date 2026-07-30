# 提交 2113：REST spec: Add encryption keys

## 提交信息

- **序号**：2113 / 4088
- **哈希**：7217fb44635f5ef2794b0cb39cc8c893452092ea
- **短哈希**：7217fb446
- **日期**：2025-05-12 13:24:51 -0700
- **作者**：Ryan Blue
- **提交说明**：REST spec: Add encryption keys (#12987)
- **PR/Issue**：#12987

## 总体目的

本提交为 Iceberg REST Catalog 规范增加了加密密钥（encryption keys）的支持。这是 Iceberg 在 REST API 层面对表数据进行加密管理的重要扩展。通过在表元数据中引入加密密钥的概念，可以支持对存储在底层文件系统中的数据进行加密保护，满足企业级数据安全合规需求。此改动为后续实现端到端的数据加密能力奠定了规范基础，使得 REST Catalog 客户端和服务器能够就加密密钥的添加和移除进行通信协商。

## 如何达成设计目的

1. 在 OpenAPI 规范中定义了新的 `EncryptedKey` 数据模型，包含 key-id、encrypted-key-metadata、encrypted-by-id 和 properties 字段
2. 在 `TableMetadata` 中新增 `encryption-keys` 字段，用于存储表关联的加密密钥列表
3. 定义了两个新的表更新操作：`AddEncryptionKeyUpdate` 和 `RemoveEncryptionKeyUpdate`，分别用于添加和移除加密密钥
4. 将新的更新操作注册到 `TableUpdate` 的联合类型中，使客户端能够通过 REST API 发起加密密钥的变更请求
5. 同时更新了 Python 模型（rest-catalog-open-api.py）和 YAML 规范文件，保持两者一致

## 修改详情

### `open-api/rest-catalog-open-api.py` (修改, +20/-0 lines)

**修改目的**：在 Python 数据模型中添加加密密钥相关的类定义，与 YAML 规范保持同步。

**工作逻辑**：新增了 `EncryptedKey` 模型类，包含 key_id（key-id 别名）、encrypted_key_metadata（encrypted-key-metadata 别名）、encrypted_by_id（encrypted-by-id 别名，可选）和 properties（可选）字段。同时定义了 `AddEncryptionKeyUpdate` 和 `RemoveEncryptionKeyUpdate` 两个更新操作类，分别携带 encryption-key 和 key-id。最后在 `TableMetadata` 中添加了 `encryption_keys` 字段，并在 `TableUpdate` 的联合类型中注册了两个新的更新操作。

### `open-api/rest-catalog-open-api.yaml` (修改, +52/-0 lines)

**修改目的**：在 YAML 规范中正式定义加密密钥相关的 schema，供 REST API 客户端和服务端使用。

**工作逻辑**：
- 定义了 `EncryptedKey` 对象 schema，其中 encrypted-key-metadata 使用 base64 编码（format: byte, contentEncoding: base64）
- 在 `TableMetadata` schema 中添加了 `encryption-keys` 字段，类型为 EncryptedKey 列表
- 在 `TableUpdate` 的 action 映射中注册了 `add-encryption-key` 和 `remove-encryption-key` 两个新动作
- 定义了 `AddEncryptionKeyUpdate`（包含 encryption-key 引用）和 `RemoveEncryptionKeyUpdate`（包含 key-id）两个 schema，并通过 allOf 继承 BaseUpdate
- 将两个新更新操作添加到 `TableUpdate` 的 anyOf 联合类型中

## 总结

本提交为 Iceberg REST Catalog 规范引入了加密密钥管理能力，是数据加密功能的基础设施层改动。通过定义标准化的 API 接口，使得表的加密密钥可以通过 REST 协议进行生命周期管理（添加和移除），为后续的端到端数据加密实现提供了规范支撑。
