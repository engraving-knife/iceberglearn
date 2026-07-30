# 提交 3006：Azure: KeyManagementClient implementation for Azure Key Vault (#13186)

## 提交信息

- **序号**：3006 / 4088
- **哈希**：41b5af3e8a9ffaa709f08171113a8abcd201d765
- **短哈希**：41b5af3e8
- **日期**：2025-12-12 07:51:16 -0800
- **作者**：Nándor Kollár
- **提交说明**：Azure: KeyManagementClient implementation for Azure Key Vault (#13186)
- **PR/Issue**：#13186

## 总体目的

Iceberg 的表数据加密采用 envelope encryption 模式：数据文件用本地生成的数据加密密钥（DEK）加密，DEK 再由 KMS 中的主密钥（KEK）包装后随文件元数据持久化，读取时通过 KMS 解包 DEK。这套机制的抽象边界是 `core/src/main/java/org/apache/iceberg/encryption/KeyManagementClient` 接口，定义了 `wrapKey`、`unwrapKey`、`initialize` 三个核心方法以及可选的 `supportsKeyGeneration`/`generateKey`。在该提交之前，Iceberg 已为 AWS KMS 等提供了 `KeyManagementClient` 实现，但 Azure 侧仅有 ADLS 文件 IO，并没有对接 Azure Key Vault 的密钥管理实现——这意味着使用 Azure 作为存储后端的用户无法在 Iceberg 内利用 Key Vault 来集中管理表主密钥，只能退而用其他 KMS 或放弃服务端包装。

本提交填补这一空白，新增 `AzureKeyManagementClient`，通过 Azure Key Vault 的 `KeyClient`/`CryptographyClient` 完成 DEK 的包装与解包。Azure Key Vault 是 Azure 上托管加密密钥与机密的托管服务，支持 RSA 等密钥类型的服务端加解密（wrap/unwrap），正好满足 Iceberg envelope encryption 对 KMS 的需求。

设计上的一个关键考量是凭证复用。ADLS 文件 IO 已经有一套基于 `AdlsTokenCredentialProviders` 的可配置凭证体系（支持自定义 `AdlsTokenCredentialProvider` 或回退到 `DefaultAzureCredential`，覆盖托管身份、环境变量、CLI 登录等来源）。新增的 KMS 客户端并未另起一套认证，而是直接复用 `AdlsTokenCredentialProviders.from(properties).credential()`，让 Key Vault 与 ADLS 共用同一套 Azure 凭证配置，避免用户为存储和密钥管理分别配置认证。另一个考量是包装算法的可配置性：Key Vault 的 wrap 操作需要一个 `KeyWrapAlgorithm`（如 `RSA_OAEP_256`、`RSA1_5`），本提交把它做成可配置属性并默认 `RSA_OAEP_256`，兼顾安全默认与灵活覆盖。

## 如何达成设计目的

整体思路是新增一个 `AzureKeyManagementClient` 实现 `KeyManagementClient` 接口，在 `initialize` 时从属性中解析 Key Vault URL 与包装算法，并通过复用 ADLS 的凭证提供者构建 `KeyClient`；`wrapKey`/`unwrapKey` 则通过 `keyClient.getCryptographyClient(wrappingKeyId)` 拿到对应主密钥的密码学客户端，调用其 `wrapKey`/`unwrapKey`。配套地，在 `AzureProperties` 中新增 Key Vault URL 与包装算法两个属性及解析逻辑，在 `build.gradle`（主工程与 azure-bundle）中加入 `com.azure:azure-security-keyvault-keys` 依赖，并在 `LICENSE` 中登记该依赖的 MIT 许可。测试侧新增了一个需要真实 Azure 环境的集成测试（由 `AZURE_KEYVAULT_URL` 环境变量启用），以及在 `TestAzureProperties` 中补充序列化往返断言。

## 修改详情

### `azure-bundle/LICENSE` (+7/-0 lines)

**修改目的**：登记新引入依赖 `com.azure:azure-security-keyvault-keys` 的许可信息。

**工作逻辑**：
在 `azure-bundle/LICENSE` 中按已有格式新增一段条目，声明 `Group: com.azure  Name: azure-security-keyvault-keys  Version: 4.10.2`，项目 URL 指向 `https://github.com/Azure/azure-sdk-for-java`，许可为 MIT。这是发布 bundle 时满足第三方依赖许可公示要求的常规动作。

### `azure-bundle/build.gradle` (+1/-0 lines)

**修改目的**：把 `azure-security-keyvault-keys` 加入 azure-bundle 的运行时依赖。

**工作逻辑**：
在 `iceberg-azure-bundle` 子项目的 `dependencies` 块中，于 `azure-storage-file-datalake` 之后新增 `implementation "com.azure:azure-security-keyvault-keys"`。bundle 是供用户直接引入的胖依赖包，把 Key Vault SDK 打包进去后，使用 bundle 的用户即可开箱使用 Azure KMS，无需再单独管理该依赖。

### `azure/src/integration/java/org/apache/iceberg/azure/keymanagement/TestAzureKeyManagementClient.java` (+84/-0 lines, 新文件)

**修改目的**：为 `AzureKeyManagementClient` 提供针对真实 Azure Key Vault 的集成测试。

**工作逻辑**：
该测试类位于 `src/integration`，用 `@EnabledIfEnvironmentVariables({@EnabledIfEnvironmentVariable(named = "AZURE_KEYVAULT_URL", matches = ".*")})` 控制只有设置了 `AZURE_KEYVAULT_URL` 时才执行（即只在有真实 Azure 环境时跑，CI 默认跳过）。`@BeforeAll` 中用 `KeyClientBuilder` 直连 Key Vault 并 `createKey(ICEBERG_TEST_KEY_NAME, KeyType.RSA)` 创建一个 RSA 测试主密钥，再构造 `AzureKeyManagementClient` 并以 `ImmutableMap.of(AZURE_KEYVAULT_URL, keyVaultUri)` 初始化；`@AfterAll` 中 `beginDeleteKey` 并 `purgeDeletedKey` 清理。`keyWrapping` 测试用 `"table-master-key".getBytes()` 作为 DEK，调用 `wrapKey` 后再 `unwrapKey`，断言还原结果与原 DEK 相等。`keyGenerationNotSupported` 断言 `supportsKeyGeneration()` 为 false（接口默认值，未覆盖），即该实现让 Iceberg 在本地生成 DEK 后再调用 `wrapKey` 包装。

### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java` (+22/-0 lines)

**修改目的**：在 `AzureProperties` 中新增 Key Vault URL 与包装算法两个配置属性及其解析。

**工作逻辑**：
新增两个 public 常量：`AZURE_KEYVAULT_URL = "azure.keyvault.url"` 与 `AZURE_KEYVAULT_KEY_WRAP_ALGORITHM = "azure.keyvault.key-wrap-algorithm"`，并新增私有字段 `keyWrapAlgorithm`、`keyVaultUrl`。在带参构造方法中：若 properties 含 `AZURE_KEYVAULT_URL` 则取其值赋给 `keyVaultUrl`；`keyWrapAlgorithm` 取 `AZURE_KEYVAULT_KEY_WRAP_ALGORITHM`，缺省时回退到 `KeyWrapAlgorithm.RSA_OAEP_256.getValue()`，即默认采用 RSA-OAEP with SHA-256（较安全的默认）。新增两个访问器：`keyWrapAlgorithm()` 把字符串通过 `KeyWrapAlgorithm.fromString` 转回枚举；`keyVaultUrl()` 返回 `Optional<String>`。这样 KMS 客户端可通过统一的 `AzureProperties` 拿到所需配置，保持与 ADLS 配置一致的入口。

### `azure/src/main/java/org/apache/iceberg/azure/keymanagement/AzureKeyManagementClient.java` (+68/-0 lines, 新文件)

**修改目的**：实现 `KeyManagementClient` 接口，对接 Azure Key Vault 完成 DEK 的包装/解包。

**工作逻辑**：
类注释为"Azure key management client which connects to Azure Key Vault。"核心逻辑：

- `initialize(Map<String,String> properties)`：构造 `AzureProperties(properties)`，从中取 `keyWrapAlgorithm()`；新建 `KeyClientBuilder`，若 `azureProperties.keyVaultUrl()` 存在则 `vaultUrl(...)`，并复用 `AdlsTokenCredentialProviders.from(properties).credential()` 作为凭证（与 ADLS 共用同一套认证配置），`buildClient()` 得到 `KeyClient`。注意这里没有覆盖 `supportsKeyGeneration()`，沿用接口默认的 `false`，即 Iceberg 本地生成 DEK 再包装。
- `wrapKey(ByteBuffer key, String wrappingKeyId)`：`ByteBuffers.toByteArray(key)` 把 ByteBuffer 转为字节数组，调用 `keyClient.getCryptographyClient(wrappingKeyId).wrapKey(keyWrapAlgorithm, bytes)`，从 `WrapResult.getEncryptedKey()` 取出密文并包回 `ByteBuffer` 返回。`wrappingKeyId` 即 Key Vault 中主密钥的名称/标识。
- `unwrapKey(ByteBuffer wrappedKey, String wrappingKeyId)`：对称地调用 `getCryptographyClient(wrappingKeyId).unwrapKey(keyWrapAlgorithm, bytes)`，从 `UnwrapResult.getKey()` 取回明文 DEK。

设计要点在于通过 `getCryptographyClient(wrappingKeyId)` 按密钥 ID 动态获取密码学客户端，使一个 `KeyClient` 即可对多个主密钥操作；凭证复用 ADLS 提供者避免重复认证配置。

### `azure/src/test/java/org/apache/iceberg/azure/TestAzureProperties.java` (+7/-0 lines)

**修改目的**：补充对新属性的序列化往返测试。

**工作逻辑**：
在已有的序列化往返测试中，向构造 `AzureProperties` 的属性 Map 加入 `AZURE_KEYVAULT_URL = "https://test-key-vault.vault.azure.net"` 和 `AZURE_KEYVAULT_KEY_WRAP_ALGORITHM = KeyWrapAlgorithm.RSA1_5.getValue()`（刻意用非默认的 RSA1_5 以验证配置确实生效），并新增两行断言 `serdedProps.keyVaultUrl()` 与 `serdedProps.keyWrapAlgorithm()` 分别等于原 props 的对应值。这确保新增字段能正确序列化并在反序列化后保持一致，满足 FileIO 在分布式引擎中分发配置的需求。同时新增了对应的 import。

### `build.gradle` (+1/-0 lines)

**修改目的**：在 `iceberg-azure` 主模块中加入 `azure-security-keyvault-keys` 的 compileOnly 依赖。

**工作逻辑**：
在根 `build.gradle` 的 `:iceberg-azure` 项目依赖块中，于 `azure-storage-file-datalake` 之后新增 `compileOnly "com.azure:azure-security-keyvault-keys"`，与同处其他 Azure SDK 依赖一致地置于 `azuresdk.bom` 平台之下。用 `compileOnly` 是因为运行时依赖由 azure-bundle 或用户自身环境提供，主模块仅需要在编译期可见 API。

## 总结

该提交为 Iceberg 的 Azure 集成补上了 Key Vault 密钥管理能力，新增 `AzureKeyManagementClient` 实现 `KeyManagementClient`，使以 Azure 为存储后端的用户能够用 Key Vault 集中托管表主密钥、完成 envelope encryption 的 DEK 包装与解包。核心价值在于：复用 ADLS 既有的凭证体系（`AdlsTokenCredentialProviders`）让存储与密钥管理共用一份 Azure 认证配置；包装算法可配置且默认采用安全的 `RSA_OAEP_256`；通过 `AzureProperties` 统一暴露配置入口并保证可序列化分发。配合依赖、LICENSE 登记与集成测试，这是一次完整且自洽的 KMS 集成落地，补齐了 Azure 侧与 AWS 等对等的加密能力。
