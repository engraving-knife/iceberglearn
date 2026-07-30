# 提交 2080：Spec additions for encryption

## 提交信息

- **序号**：2080 / 4088
- **哈希**：7f3f450bbddf55bb383ff1409d6d0ca4557c9ffc
- **短哈希**：7f3f450bb
- **日期**：2025-05-05 10:08:14 -0700
- **作者**：ggershinsky
- **提交说明**：Spec additions for encryption (#12162)
- **PR/Issue**：#12162

## 总体目的

Iceberg 规范此前已支持文件级加密（通过 `EncryptedInputFile`/`EncryptedOutputFile` 机制），但加密密钥的管理方式未在规范中正式定义——密钥通常由外部 KMS（密钥管理服务）管理，引擎自行处理密钥获取。随着 V3 规范的开发，社区决定在表元数据中正式引入"表加密密钥"（Table Encryption Keys）的概念，使加密密钥成为表元数据的一部分，便于密钥轮换、密钥继承和多表共享密钥的管理。

本提交向 Iceberg 规范文档 `format/spec.md` 添加表加密密钥的相关定义，作为后续代码实现（如提交 2086 添加元数据键）的规范基础。具体包括：将"Table encryption keys"列入 V3 规范的新特性列表；在快照（Snapshot）元数据中新增 `key-id` 字段以标识该快照使用的加密密钥；在表元数据中新增 `encryption-keys` 列表字段用于跟踪表的所有加密密钥；定义加密密钥的结构（key-id、encrypted-key-metadata、encrypted-by-id、properties）；以及 JSON 序列化示例和 V3 规范变更说明。

## 如何达成设计目的

通过在规范文档的多处位置添加加密密钥相关定义，构建完整的密钥管理规范：

- **V3 特性列表**：将表加密密钥列为 V3 新特性之一。
- **快照结构**：在快照字段表中添加 `key-id`（v3 optional），标识加密 manifest list key metadata 的密钥 ID。
- **表元数据结构**：在表元数据字段表中添加 `encryption-keys`（v3 optional），作为加密密钥列表。
- **Encryption Keys 小节**：定义密钥的结构 schema（key-id、encrypted-key-metadata、encrypted-by-id、properties），并说明 encrypted-key-metadata 的格式由表的加密方案和 KMS 提供者决定。
- **JSON 序列化**：在 Appendix C 中添加 `encryption-keys` 的 JSON 序列化示例。
- **V3 变更说明**：在规范版本变更章节中说明加密密钥的跟踪方式和快照密钥指定方式。

## 修改详情

### `format/spec.md` (修改, +25/-0 lines)

**修改目的**：向 Iceberg 规范添加表加密密钥的完整定义。

**工作逻辑**：
共在 6 处添加内容：

1. **V3 特性列表**（第 53 行附近）：在 V3 扩展的特性列表中新增 `* Table encryption keys` 条目，与多参数转换、行血缘、二进制删除向量等并列。

2. **快照字段表**（第 743 行附近）：在快照结构字段表中新增一行 `| | | _optional_ | **`key-id`** | ID of the encryption key that encrypts the manifest list key metadata |`，表示 v3 中快照可选地记录使用的加密密钥 ID。

3. **表元数据字段表**（第 943 行附近）：在表元数据字段表中新增一行 `| | | _optional_ | **`encryption-keys`** | A list (optional) of encryption keys used for table encryption. |`，表示 v3 中表元数据可选地包含加密密钥列表。

4. **Encryption Keys 小节**（第 1032 行后新增）：在统一分区类型说明之后新增 `#### Encryption Keys` 小节，定义加密密钥的结构 schema：
   - `key-id`（string, required）：加密密钥的 ID
   - `encrypted-key-metadata`（string, required）：加密后的密钥和元数据，base64 编码
   - `encrypted-by-id`（string, optional）：用于加密/包装本密钥的密钥 ID
   - `properties`（map<string,string>, optional）：加密方案所需的额外元数据
   - 注释说明 encrypted-key-metadata 的格式由表的加密方案和 KMS 提供者决定。

5. **JSON 序列化示例**（Appendix C，第 1547 行附近）：添加 `encryption-keys` 的 JSON 序列化示例 `[ {"key-id": "5f819b", "key-metadata": "aWNlYmVyZwo="} ]`。

6. **V3 规范变更说明**（第 1674 行附近）：在 V3 变更列表中新增 "Encryption changes" 小节，说明加密密钥通过表元数据 `encryption-keys` 跟踪，快照使用的密钥通过 `key-id` 指定。

## 总结

本提交向 Iceberg 规范文档添加表加密密钥（Table Encryption Keys）的完整定义，作为 V3 规范的一部分。涉及 V3 特性列表、快照结构（新增 `key-id`）、表元数据结构（新增 `encryption-keys` 列表）、密钥结构 schema、JSON 序列化示例和 V3 变更说明共 6 处新增，为后续代码实现提供规范基础。
