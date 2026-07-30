# 提交 0169：Azure: Allow shared-key auth for testing purposes (#9068)

## 提交信息

- **序号**：0169 / 4088
- **哈希**：798f1c8abe3ed24a2229d4f3d1b5d7c009a7fa7e
- **短哈希**：798f1c8ab
- **日期**：2023-11-16
- **作者**：Robert Stupp
- **提交说明**：Azure: Allow shared-key auth for testing purposes (#9068)
- **PR/Issue**：#9068

## 总体目的

Iceberg 的 Azure 模块（`azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java`）在改造前只支持三类认证方式：SAS token（按 account 维度配置 `adls.sas-token.<account>`）、连接字符串（`adls.connection-string.` 前缀），以及回退到 `DefaultAzureCredentialBuilder`（基于环境/managed identity 等的默认链）。但 Azure Blob Storage 客户端库（包括 hadoop-azure）本身是支持 shared-key（账户名 + 账户密钥）认证的，这种认证方式在用 Azurite 这类本地 Azure 模拟器做集成测试时非常方便——Azurite 默认就提供一对固定的 `devstoreaccount1` / `Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVEyCOK4...` 风格的 shared-key 给测试使用。可惜 `AzureProperties` 没有暴露这条路径，测试者要么强行注入连接字符串（其中可包含 `AccountName=...;AccountKey=...`），要么就得在测试环境配置真正的 managed identity，使用门槛高且和 hadoop-azure 等周边生态的常见做法不对齐。

这个提交给 `AzureProperties` 引入两个新的配置项 `adls.auth.shared-key.account.name` 与 `adls.auth.shared-key.account.key`：当两者都给出时构造 `StorageSharedKeyCredential` 并缓存到 `namedKeyCreds` 字段，在 `applyClientConfiguration` 中按“SAS token > shared-key > DefaultAzureCredential”的优先级把该 credential 注入 `DataLakeFileSystemClientBuilder`。这让 Iceberg 在本地/CI 集成测试中能够方便地用 Azurite + shared-key 跑通 Azure 相关读写流程，不必依赖真实云资源。同时引入 `Preconditions.checkArgument` 强制“account name 与 account key 必须同时提供”，避免半套配置引发难以定位的认证失败。

## 如何达成设计目的

整体设计思路：在 `AzureProperties` 构造器里读取两个新属性键，若任一非空则校验两者必须同时非空，然后用 `new StorageSharedKeyCredential(name, key)` 构造凭证并缓存到实例字段 `namedKeyCreds`。`applyClientConfiguration` 里在原 SAS token 分支之后、默认 DefaultAzureCredential 分支之前插入 `else if (namedKeyCreds != null) builder.credential(namedKeyCreds)` 分支，从而把 shared-key credential 注入 client builder。`StorageSharedKeyCredential` 是 Azure SDK 提供的可重用凭证对象（账号名+密钥），与 `TokenCredential`（OAuth 系）不同体系，因此需要单独的 `builder.credential(StorageSharedKeyCredential)` 重载。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java`

**修改目的**：为 `AzureProperties` 增加 shared-key 认证支持。

**工作逻辑**：
- 新增 import：`com.azure.storage.common.StorageSharedKeyCredential` 与 `org.apache.iceberg.relocated.com.google.common.base.Preconditions`。
- 新增两个公开常量键：`ADLS_SHARED_KEY_ACCOUNT_NAME = "adls.auth.shared-key.account.name"` 与 `ADLS_SHARED_KEY_ACCOUNT_KEY = "adls.auth.shared-key.account.key"`。
- 新增私有字段 `private StorageSharedKeyCredential namedKeyCreds;`，与既有 `adlsSasTokens`/`adlsConnectionStrings` 并列作为认证状态。
- 在构造器中读取两键：若 `sharedKeyAccountName != null || sharedKeyAccountKey != null`，则用 `Preconditions.checkArgument(sharedKeyAccountName != null && sharedKeyAccountKey != null, "Azure authentication: shared-key requires both %s and %s", ADLS_SHARED_KEY_ACCOUNT_NAME, ADLS_SHARED_KEY_ACCOUNT_KEY)` 强制两者同时存在，再 `new StorageSharedKeyCredential(sharedKeyAccountName, sharedKeyAccountKey)` 构造凭证存入 `namedKeyCreds`。这种“两者只要有一个出现就要求两个都给”的校验是避免误配置半套凭证的常见做法。
- `applyClientConfiguration(String account, DataLakeFileSystemClientBuilder builder)` 中插入新分支：原先是 `if (sasToken != null && !sasToken.isEmpty()) builder.sasToken(sasToken); else builder.credential(new DefaultAzureCredentialBuilder().build());`；改为 `if (sasToken) ... else if (namedKeyCreds != null) builder.credential(namedKeyCreds); else builder.credential(new DefaultAzureCredentialBuilder().build());`。这一优先级确保 SAS token 仍优先于 shared-key，shared-key 优先于 DefaultAzureCredential。

### `azure/src/test/java/org/apache/iceberg/azure/AzurePropertiesTest.java`

**修改目的**：覆盖 shared-key 认证路径与既有认证路径不被破坏。

**工作逻辑**：
- 新增 import：`ADLS_SHARED_KEY_ACCOUNT_KEY`、`ADLS_SHARED_KEY_ACCOUNT_NAME`、`StorageSharedKeyCredential`、`never`、`Assertions`。
- 在三个既有测试（`testSasToken`、`testConnectionString`、`testDefaultAzureCredential` 等）中追加 `verify(clientBuilder, never()).credential(any(StorageSharedKeyCredential.class));`，确保在未配置 shared-key 的场景下不会误注入 shared-key credential——这是回归保护，避免未来重构破坏既有认证行为。
- 新增测试 `testSharedKey`：
  - 第一种用例：只给 `ADLS_SHARED_KEY_ACCOUNT_KEY`，断言抛 `IllegalArgumentException` 且消息匹配 `"Azure authentication: shared-key requires both adls.auth.shared-key.account.name and adls.auth.shared-key.account.key"`；
  - 第二种用例：只给 `ADLS_SHARED_KEY_ACCOUNT_NAME`，同样断言抛 `IllegalArgumentException`；
  - 第三种用例：两者都给，构造 `AzureProperties` 后 `applyClientConfiguration("account", clientBuilder)`，断言 `verify(clientBuilder).credential(any(StorageSharedKeyCredential.class))` 与 `verify(clientBuilder, never()).credential(any(TokenCredential.class))`，证明 shared-key 路径生效且不会回退到 `DefaultAzureCredential`。
- 三个既有用例补充 `never` 断言与新用例共同把优先级链路（SAS > shared-key > default）锁死。

## 小结

这个提交通过为 `AzureProperties` 引入 `adls.auth.shared-key.account.name`/`account.key` 两个属性键、构造 `StorageSharedKeyCredential` 并在 `applyClientConfiguration` 中按“SAS > shared-key > Default”优先级注入 client builder，让 Iceberg 在本地 Azurite 测试环境下能直接使用 shared-key 认证跑通 Azure 集成流程，补齐了 Azure 认证方式的一处缺口，主要服务于测试用途而非生产场景。
