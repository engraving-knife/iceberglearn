# 提交 3932：Data: Add TCK for Encrypt in FileFormat API (#16724)

## 提交信息

- **序号**：3932 / 4088
- **哈希**：d7612ae7b776ae55fb28a0852a3777317ca7dfba
- **短哈希**：d7612ae7b
- **日期**：2026-06-23 18:10:20 +0200
- **作者**：GuoYu
- **提交说明**：Data: Add TCK for Encrypt in FileFormat API (#16724)
- **PR/Issue**：#16724

## 总体目的

这次提交为 Iceberg 的 FileFormat API 添加了加密（Encrypt）相关的技术兼容性套件（TCK，Technology Compatibility Kit）测试。TCK 是用于验证不同引擎实现（如 Parquet、ORC、Avro）是否符合 Iceberg 规范的测试套件。此前的 TCK 已覆盖读写、schema 演进、metrics 等方面，但缺少对文件加密功能的验证。

新增的测试覆盖两种加密模式：
1. **AES Stream Encryption**：使用 `EncryptingFileIO` 对输出流进行 AES 加密，文件元数据中存储加密密钥。
2. **Native Encryption**：利用文件格式原生的加密能力（如 Parquet 的模块级加密），通过 `withFileEncryptionKey` 和 `withAADPrefix` 将密钥和 AAD 前缀传递给 writer。

测试通过 `MISSING_FEATURES` map 声明各文件格式不支持的加密特性：Avro 不支持 native encryption，ORC 不支持 AES stream encryption 和 native encryption，Parquet 不支持 AES stream encryption（但支持 native encryption）。

## 如何达成设计目的

在 `BaseFormatModelTests` 抽象基类中新增两个参数化测试方法，分别针对 AES stream encryption 和 native encryption。两个测试共享 `writeAndAssertEncryptedDataWriter` 辅助方法完成数据写入和验证。通过 `assumeSupports(format, feature)` 机制跳过不支持该特性的格式，使 TCK 既能验证支持方又能声明不支持方。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/BaseFormatModelTests.java` (+108/-3 lines)

**修改目的**：新增加密相关的 TCK 测试。

**工作逻辑**：
1. 新增 import：`EncryptingFileIO`、`EncryptionManager`、`EncryptionTestHelpers`、`NativeEncryptionKeyMetadata`。
2. 新增两个 feature 常量：`FEATURE_NATIVE_ENCRYPTION`、`FEATURE_AES_STREAM_ENCRYPTION`。
3. 更新 `MISSING_FEATURES` map：
   - Avro 新增 `FEATURE_NATIVE_ENCRYPTION` 到缺失列表。
   - ORC 新增 `FEATURE_AES_STREAM_ENCRYPTION` 和 `FEATURE_NATIVE_ENCRYPTION` 到缺失列表。
   - 新增 Parquet 条目，缺失 `FEATURE_AES_STREAM_ENCRYPTION`。
4. 新增 `testDataWriterAesStreamEncryption` 参数化测试：创建 `EncryptingFileIO`，通过 `encryptingFileIO.newEncryptingOutputFile()` 获取带有 keyMetadata 的 EncryptedOutputFile，构建 writer 写入数据并验证。
5. 新增 `testDataWriterNativeEncryption` 参数化测试：使用 `EncryptedFiles.plainAsEncryptedOutput()` 创建不携带 native 加密元数据的输出文件（确保加密仅由 `withFileEncryptionKey`/`withAADPrefix` 驱动），通过 `NativeEncryptionKeyMetadata` 获取密钥和 AAD 前缀传递给 writer。
6. 新增 `writeAndAssertEncryptedDataWriter` 辅助方法：写入数据后验证 (a) 不带解密的读取抛出 RuntimeException；(b) 带解密的读取返回正确记录，确保数据确实被加密。
7. 新增 `readAndAssertGenericRecords` 重载方法：从指定 InputFile 读取并断言记录与预期一致。

## 总结

这次提交为 Iceberg FileFormat API 的加密功能补充了 TCK 测试，覆盖 AES stream encryption 和 native encryption 两种模式。通过 `MISSING_FEATURES` map 明确声明各文件格式的支持矩阵，使引擎实现者能够验证其加密实现是否符合规范。测试中同时验证了未解密读取会失败、解密读取成功，确保加密确实生效。
