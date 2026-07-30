# 提交 2086：API, Core: Add table metadata keys for encryption

## 提交信息

- **序号**：2086 / 4088
- **哈希**：92d89dc912d3bf2ae70fb2f3e1eb41d091772cdb
- **短哈希**：92d89dc91
- **日期**：2025-05-06 11:13:40 -0700
- **作者**：Ryan Blue
- **提交说明**：API, Core: Add table metadata keys for encryption (#12927)
- **PR/Issue**：#12927

## 总体目的

提交 2080 已在 Iceberg 规范中定义了表加密密钥（Table Encryption Keys）的概念：表元数据中新增 `encryption-keys` 列表字段，快照中新增 `key-id` 字段标识使用的加密密钥。本提交是该规范的代码实现，为 Iceberg Core 和 API 添加加密密钥的 Java 数据模型、JSON 序列化/反序列化、元数据更新操作和快照集成。

具体包括：
1. 定义 `EncryptedKey` 接口和 `BaseEncryptedKey` 实现类，表示一个加密密钥（key-id、encrypted-key-metadata、encrypted-by-id、properties）。
2. 在 `Snapshot` 接口中新增 `keyId()` 方法，在 `BaseSnapshot` 中新增 `keyId` 字段。
3. 在 `TableMetadata` 中新增 `encryptionKeys` 列表字段和 Builder 支持。
4. 新增 `EncryptedKeyParser` 用于加密密钥的 JSON 序列化/反序列化。
5. 在 `MetadataUpdate` 中新增 `AddEncryptionKey` 和 `RemoveEncryptionKey` 更新操作。
6. 在 `TableMetadataParser` 和 `SnapshotParser` 中集成加密密钥的序列化。
7. 在 `JsonUtil` 中新增 JSON 工具方法。
8. 配套完整的单元测试。

## 如何达成设计目的

整体设计采用分层模式，关键组件协作关系如下：

- **`EncryptedKey` 接口**（API 层）：定义密钥的四个字段访问方法。
- **`BaseEncryptedKey`**（Core 层）：接口的可序列化实现类。
- **`EncryptedKeyParser`**（Core 层）：负责 `EncryptedKey` 与 JSON 之间的转换。
- **`Snapshot.keyId()` / `BaseSnapshot.keyId`**：快照记录使用的密钥 ID。
- **`TableMetadata.encryptionKeys`**：表元数据维护密钥列表；Builder 提供 `addEncryptionKey`/`removeEncryptionKey` 方法。
- **`MetadataUpdate.AddEncryptionKey` / `RemoveEncryptionKey`**：元数据更新操作，通过 `applyTo(builder)` 应用到 TableMetadata Builder。
- **`MetadataUpdateParser`**：注册新更新操作的 JSON action 名（`add-encryption-key`、`remove-encryption-key`）。
- **`TableMetadataParser`**：序列化/反序列化 `encryption-keys` 字段。
- **`SnapshotParser` / `SnapshotProducer`**：序列化/反序列化快照的 `key-id` 字段。
- **`JsonUtil`**：新增 `parseByteBufferOrNull`、`getObjectList` 等工具方法支持 JSON 解析。

## 修改详情

### `api/src/main/java/org/apache/iceberg/encryption/EncryptedKey.java` (新增, +32/-0 lines)

**修改目的**：定义加密密钥的公共接口。

**工作逻辑**：
定义接口，包含四个方法：`keyId()`（String）、`encryptedKeyMetadata()`（ByteBuffer）、`encryptedById()`（String）、`properties()`（Map<String,String>），对应规范中加密密钥的四个字段。

### `api/src/main/java/org/apache/iceberg/Snapshot.java` (修改, +9/-0 lines)

**修改目的**：在快照接口中新增 `keyId()` 方法。

**工作逻辑**：
新增 default 方法 `keyId()` 返回 null（向后兼容），表示加密该快照 manifest list 的密钥 ID。

### `core/src/main/java/org/apache/iceberg/encryption/BaseEncryptedKey.java` (新增, +60/-0 lines)

**修改目的**：提供 `EncryptedKey` 的可序列化实现。

**工作逻辑**：
实现 `EncryptedKey` 接口，包含四个字段和构造器，实现 `equals`/`hashCode`/`toString`。`encryptedKeyMetadata` 使用 `ByteBuffer` 存储加密后的密钥元数据。

### `core/src/main/java/org/apache/iceberg/EncryptedKeyParser.java` (新增, +85/-0 lines)

**修改目的**：实现 `EncryptedKey` 的 JSON 序列化/反序列化。

**工作逻辑**：
- `toJson(EncryptedKey, JsonGenerator)`：将密钥序列化为 JSON 对象，包含 `key-id`、`encrypted-key-metadata`（base64）、`encrypted-by-id`（可选）、`properties`（可选）。
- `fromJson(JsonNode)`：从 JSON 节点反序列化为 `BaseEncryptedKey`。
- 使用 `JsonUtil.parseByteBufferOrNull` 解析 base64 编码的密钥元数据。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (修改, +44/-0 lines)

**修改目的**：在表元数据中集成加密密钥列表。

**工作逻辑**：
- 新增 `encryptionKeys` 字段（`List<EncryptedKey>`）和 `encryptionKeys()` 访问方法。
- 构造器新增 `encryptionKeys` 参数，校验非 null。
- Builder 新增 `encryptionKeys` 列表和 `keysById` 索引 Map，提供 `addEncryptionKey(EncryptedKey)`（添加并索引）、`removeEncryptionKey(String keyId)`（按 ID 移除）方法。
- Builder 从 base 复制时继承加密密钥列表。

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java` (修改, +18/-0 lines)

**修改目的**：序列化/反序列化表元数据中的 `encryption-keys` 字段。

**工作逻辑**：
- 序列化时，若 `encryptionKeys` 非空，写入 `encryption-keys` JSON 数组，每个元素通过 `EncryptedKeyParser.toJson` 序列化。
- 反序列化时，若节点包含 `encryption-keys`，通过 `JsonUtil.getObjectList` 和 `EncryptedKeyParser.fromJson` 解析；否则为空列表。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java` (修改, +35/-0 lines)

**修改目的**：新增加密密钥的元数据更新操作。

**工作逻辑**：
- `AddEncryptionKey`：包含一个 `EncryptedKey`，`applyTo` 调用 `builder.addEncryptionKey(key)`。
- `RemoveEncryptionKey`：包含一个 `keyId` 字符串，`applyTo` 调用 `builder.removeEncryptionKey(keyId)`。

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java` (修改, +45/-0 lines)

**修改目的**：注册新更新操作的 JSON 序列化。

**工作逻辑**：
- 定义 action 名 `add-encryption-key` 和 `remove-encryption-key`。
- 在 ACTIONS Map 中注册 `AddEncryptionKey` 和 `RemoveEncryptionKey` 的映射。
- 新增 `writeAddEncryptionKey` 和 `writeRemoveEncryptionKey` 方法，以及对应的读取方法。

### `core/src/main/java/org/apache/iceberg/BaseSnapshot.java` (修改, +11/-1 lines)

**修改目的**：在快照实现中新增 `keyId` 字段。

**工作逻辑**：
新增 `keyId` 字段，主构造器新增参数，从 JSON 解析的构造器设为 null。

### `core/src/main/java/org/apache/iceberg/SnapshotParser.java` (修改, +8/-0 lines)

**修改目的**：序列化/反序列化快照的 `key-id` 字段。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (修改, +3/-0 lines)

**修改目的**：在快照生产者中传递 `keyId`。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (修改, +13/-0 lines)

**修改目的**：新增加密密钥相关工具方法。

### `core/src/main/java/org/apache/iceberg/util/JsonUtil.java` (修改, +48/-0 lines)

**修改目的**：新增 JSON 解析工具方法。

**工作逻辑**：
新增 `parseByteBufferOrNull`（从 base64 字符串解析 ByteBuffer，null 安全）和 `getObjectList`（从 JSON 数组解析为对象列表，使用提供的 mapper 函数）。

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (修改, +2/-1 lines)

**修改目的**：适配 `TableMetadata` 构造器参数变更。

### 测试文件（新增/修改，约 280 行）

**新增测试**：
- `TestEncryptedKeyParser.java`（+76）：测试 `EncryptedKeyParser` 的 JSON 序列化/反序列化。
- `TestJsonUtil.java`（+63）：测试新增的 `parseByteBufferOrNull` 和 `getObjectList` 方法。

**修改测试**：
- `TestTableMetadata.java`（+28）：测试表元数据中加密密钥的添加、移除和序列化。
- `TestMetadataUpdateParser.java`（+63）：测试 `AddEncryptionKey` 和 `RemoveEncryptionKey` 更新的 JSON 序列化。
- `TestSnapshotJson.java`（+10）：测试快照 `key-id` 字段的序列化。
- `TestRowLineageMetadata.java`、`TestDataTaskParser.java`、`EncryptionTestHelpers.java`：适配构造器变更。

## 总结

本提交实现了提交 2080 定义的表加密密钥规范的代码部分。新增 `EncryptedKey` 接口和 `BaseEncryptedKey` 实现类、`EncryptedKeyParser` JSON 序列化器；在 `Snapshot` 接口新增 `keyId()` 方法；在 `TableMetadata` 新增 `encryptionKeys` 列表和 Builder 的增删方法；新增 `AddEncryptionKey`/`RemoveEncryptionKey` 元数据更新操作及其 JSON 序列化；在 `TableMetadataParser` 和 `SnapshotParser` 中集成加密密钥字段；在 `JsonUtil` 新增工具方法。配套完整的单元测试，总计约 675 行新增代码。
