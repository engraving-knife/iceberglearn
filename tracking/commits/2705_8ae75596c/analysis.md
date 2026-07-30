# 提交 2705：Azure: Add support to specify token credential provider (#14136)

## 提交信息

- **序号**：2705 / 4088
- **哈希**：8ae75596c8ba68b37d05ebd0647c8ba4e3a76cbf
- **短哈希**：8ae75596c
- **日期**：2025-09-30 08:24:55 +0200
- **作者**：S N Munendra
- **提交说明**：Azure: Add support to specify token credential provider (#14136)
- **PR/Issue**：#14136

## 总体目的

本提交为 Iceberg 的 Azure/ADLS 模块新增"自定义 token 凭据提供者"（token credential provider）能力，允许用户通过配置指定一个实现 `AdlsTokenCredentialProvider` 接口的类，由该类提供访问 ADLS（Azure Data Lake Storage）所需的 `TokenCredential`，而非只能使用 Iceberg 内置硬编码的 `DefaultAzureCredentialBuilder`。

此前 `AzureProperties` 在配置 ADLS 客户端时，如果没有 SAS token、shared key、显式 token、vended credentials 等，会回退到 `new DefaultAzureCredentialBuilder().build()`。`DefaultAzureCredential` 是 Azure SDK 提供的"万能"凭据链，会按顺序尝试多种认证方式（环境变量、托管身份、VS Code、CLI 等）。但在企业场景中，用户常常需要使用特定的认证方式（如特定的 `ClientSecretCredential`、`ManagedIdentityCredential`、自定义的 token 获取逻辑、与内部密钥管理系统集成的凭据等），硬编码 `DefaultAzureCredential` 无法满足这些需求。

本提交引入 SPI 风格的扩展点：用户实现 `AdlsTokenCredentialProvider` 接口，通过 `adls.token-credential-provider` 配置项指定实现类全限定名，并通过 `adls.token-credential-provider.*` 前缀属性向实现类传递初始化参数。Iceberg 通过反射加载并实例化该类，调用 `initialize(properties)` 注入前缀属性（去掉前缀），再调用 `credential()` 获取 `TokenCredential`。这与 AWS 模块的 `S3FileIOAwsCredentialsProvider`、GCS 模块的凭据提供者机制类似，保持跨云扩展性一致。

## 如何达成设计目的

1. **定义扩展接口 `AdlsTokenCredentialProvider`**：声明 `credential()` 返回 `TokenCredential` 与 `initialize(Map<String,String>)` 接收配置。
2. **定义工厂/加载器 `AdlsTokenCredentialProviders`**：提供 `defaultFactory()`（返回内置 `DefaultTokenCredentialProvider`，封装 `DefaultAzureCredentialBuilder`）与 `from(Map)`（按配置加载自定义实现或回退默认）。加载逻辑用 `DynConstructors` 反射查找无参构造器，实例化后 `initialize` 注入前缀属性，处理"类不存在/无无参构造/未实现接口"等错误。
3. **`AzureProperties` 接入**：新增 `ADLS_TOKEN_CREDENTIAL_PROVIDER` 与 `ADLS_TOKEN_PROVIDER_PREFIX` 配置键；在 `applyClientConfiguration` 的回退分支（无其它凭据时）用 `AdlsTokenCredentialProviders.from(allProperties)` 加载提供者并 `credential()` 设置到 client builder；`allProperties` 默认初始化为空 map（原为未初始化）。
4. **测试**：新增 `TestAdlsTokenCredentialProviders` 覆盖加载逻辑（默认、空、自定义、不存在类、未实现接口、带前缀属性），增强 `TestAzureProperties` 覆盖默认/自定义提供者在 `applyClientConfiguration` 中的实际使用。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/AdlsTokenCredentialProvider.java` (+34/-0 lines, 新文件)

**修改目的**：定义 token 凭据提供者扩展接口。

**工作逻辑**：接口声明两个方法：`TokenCredential credential()` 返回 Azure SDK 的 `TokenCredential`；`void initialize(Map<String,String> properties)` 接收凭据提供者专属属性（已去除前缀）。实现类需有无参构造器（由加载器反射实例化）。

### `azure/src/main/java/org/apache/iceberg/azure/AdlsTokenCredentialProviders.java` (+96/-0 lines, 新文件)

**修改目的**：提供者加载工厂与默认实现。

**工作逻辑**：
- `defaultFactory()` 返回单例 `DefaultTokenCredentialProvider`（内部类，`credential()` 返回 `new DefaultAzureCredentialBuilder().build()`，`initialize` 空实现）。
- `from(Map properties)`：从 `ADLS_TOKEN_CREDENTIAL_PROVIDER` 读取实现类名，用 `PropertyUtil.propertiesWithPrefix(properties, ADLS_TOKEN_PROVIDER_PREFIX)` 提取带前缀的属性（去前缀），调用 `loadCredentialProvider(impl, credentialProviderProperties)`。
- `loadCredentialProvider(impl, properties)`：若 `impl` 为空用默认工厂并 `initialize`；否则用 `DynConstructors.builder(AdlsTokenCredentialProvider.class).loader(...).hiddenImpl(impl).buildChecked()` 查找无参构造器（捕获 `NoSuchMethodException` 转为 `IllegalArgumentException` 提示缺少无参构造），`newInstance` 后（捕获 `ClassCastException` 提示未实现接口）调用 `initialize(properties)` 返回。

### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java` (+27/-3 lines)

**修改目的**：新增配置键并在客户端配置回退分支使用提供者。

**工作逻辑**：
- 移除 `import com.azure.identity.DefaultAzureCredentialBuilder`（移入 `DefaultTokenCredentialProvider`）。
- 新增 `ADLS_TOKEN_CREDENTIAL_PROVIDER = "adls.token-credential-provider"`（含 Javadoc 说明用法：全限定类名、需无参构造、`initialize` 注入属性）与 `ADLS_TOKEN_PROVIDER_PREFIX = "adls.token-credential-provider."`（前缀属性，传递给提供者）。
- `allProperties` 字段默认初始化为 `Collections.emptyMap()`（原未初始化，避免回退分支 NPE）。
- `applyClientConfiguration` 中原 `builder.credential(new DefaultAzureCredentialBuilder().build())` 改为 `AdlsTokenCredentialProviders.from(allProperties)` 加载提供者并 `builder.credential(credentialProvider.credential())`。

### `azure/src/test/java/org/apache/iceberg/azure/TestAdlsTokenCredentialProviders.java` (+152/-0 lines, 新文件)

**修改目的**：测试提供者加载逻辑。

**工作逻辑**：覆盖多种场景：
- `useDefaultFactory`/`emptyPropertiesWithNoProvider`/`emptyCredentialProvider`：无配置或空配置返回 `DefaultTokenCredentialProvider`。
- `defaultProviderAsCredentialProvider`：显式指定默认实现类名仍返回默认。
- `customProviderAsCredentialProvider`：指定自定义 `DummyTokenCredentialProvider`，`credential()` 返回 `DummyTokenCredential`。
- `nonExistentCredentialProvider`：不存在的类抛 `IllegalArgumentException`（"missing no-arg constructor"）。
- `nonImplementingClassAsCredentialProvider`：用 `java.lang.String` 抛 `IllegalArgumentException`（"does not implement"）。
- `loadCredentialProviderWithProperties`：带前缀属性，断言 `initialize` 收到的属性含 `client-id`/`client-secret`、不含 `custom.property` 与 `ADLS_TOKEN_CREDENTIAL_PROVIDER` 本身。
- 内部 `DummyTokenCredentialProvider`/`DummyTokenCredential` 作为测试夹具。

### `azure/src/test/java/org/apache/iceberg/azure/TestAzureProperties.java` (+76/-0 lines)

**修改目的**：测试 `applyClientConfiguration` 中默认/自定义提供者的实际接入。

**工作逻辑**：
- 序列化测试 `testSerializeAndDeserialize` 的属性 map 新增 `ADLS_TOKEN_CREDENTIAL_PROVIDER` 与带前缀属性，验证可序列化。
- `testDefaultTokenCredentialProvider`：空配置 → `applyClientConfiguration` → 验证 client builder 收到 `DefaultAzureCredential`，未收到 SAS/shared key。
- `testCustomTokenCredentialProvider`：配置自定义 `DummyTokenCredentialProvider` + 前缀属性 + 非前缀属性 → 验证 client builder 收到 `DummyTokenCredential`，且 `initialize` 收到的属性仅含前缀项（去前缀），不含非前缀项与提供者类名本身。
- 内部 `DummyTokenCredential`/`DummyTokenCredentialProvider` 夹具。

## 总结

本提交为 Iceberg Azure/ADLS 模块新增自定义 token 凭据提供者扩展点，用户可通过 `adls.token-credential-provider` 指定实现 `AdlsTokenCredentialProvider` 的类，并通过 `adls.token-credential-provider.*` 前缀属性初始化，从而灵活接入企业认证体系（特定 ClientSecret、Managed Identity、自定义 token 获取等），不再受限于硬编码的 `DefaultAzureCredential`。设计上与 AWS/GCS 模块的凭据提供者机制保持一致，通过反射加载、前缀属性注入，并提供完善的错误处理与测试覆盖。
