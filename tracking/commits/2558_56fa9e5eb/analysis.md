# 提交 2558：GCP: KeyManagementClient implementation that works with Google Cloud KMS (#13334)

## 提交信息

- **序号**：2558 / 4088
- **哈希**：56fa9e5ebbde6d9432ec1ed1c6a52e7d81a1c634
- **短哈希**：56fa9e5eb
- **日期**：2025-08-25 15:08:49 +0200
- **作者**：Adam Szita
- **提交说明**：GCP: KeyManagementClient implementation that works with Google Cloud KMS (#13334)
- **PR/Issue**：#13334

## 总体目的

该提交为 Iceberg 的 GCP 模块新增了 `KeyManagementClient` 接口的实现 `GcpKeyManagementClient`，使 Iceberg 能够使用 Google Cloud Key Management Service (Cloud KMS) 来管理数据加密密钥（DEK）的主密钥包装（key wrapping）。这填补了 GCP 模块在密钥管理方面的功能空白——此前 AWS 和 Azure 模块已有各自的 KMS 实现，但 GCP 模块尚不支持通过 KMS 进行密钥包装。

Iceberg 的加密体系支持两种模式：客户端加密（CSE）和密钥管理服务（KMS）。在 KMS 模式下，数据加密密钥（DEK）由主密钥（master key）通过 KMS 服务进行包装（wrap/encrypt）和解包（unwrap/decrypt），主密钥本身存储在 KMS 中，从不离开 KMS。`GcpKeyManagementClient` 实现了 `wrapKey` 和 `unwrapKey` 方法，通过 Google Cloud KMS API 对 DEK 进行加密和解密。

此外，该提交还重构了 GCP 的认证逻辑，将原本散落在 `PrefixedStorage` 中的 OAuth2 凭证构建代码提取到独立的 `GCPAuthUtils` 工具类中，使 KMS 客户端和 GCS 存储客户端可以共享同一套认证逻辑。同时新增了集成测试源集（integration source set）用于编写需要真实 GCP 环境的集成测试。

## 如何达成设计目的

- **新增 `GcpKeyManagementClient`**：实现 `KeyManagementClient` 接口，通过 Google Cloud KMS 的 `KeyManagementServiceClient` 执行 `encrypt`（wrapKey）和 `decrypt`（unwrapKey）操作。
- **ByteStringShim 兼容层**：由于 `iceberg-gcp-bundle` 会重定位（shade）`com.google.protobuf.ByteString` 类，KMS 客户端需要通过动态反射（`DynMethods`/`DynClasses`）来调用 ByteString 相关方法，确保在有/无 bundle 的环境下都能正常工作。
- **提取 `GCPAuthUtils`**：将 OAuth2 凭证构建逻辑从 `PrefixedStorage` 提取到 `GCPAuthUtils.oauth2CredentialsFromGcpProperties()`，支持 OAuth2 token 和 refresh handler，供 KMS 客户端和 GCS 客户端共用。
- **资源管理**：使用 `CloseableGroup` 统一管理 KMS 客户端和 refresh handler 的生命周期，确保资源正确释放。
- **新增集成测试**：创建 `TestKeyManagementClient`（基类）、`TestKeyManagementClientWithAppCreds`（应用默认凭证）、`TestKeyManagementClientWithOAuth`（OAuth2 凭证）三个集成测试，验证 KMS 客户端在真实 GCP 环境下的正确性。
- **构建配置**：在 `build.gradle` 中新增 KMS 依赖、集成测试源集和 `integrationTest` 任务；在 `gcp-bundle/build.gradle` 中添加 KMS 依赖以供 bundle 打包。

## 修改详情

### `gcp/src/main/java/org/apache/iceberg/gcp/GcpKeyManagementClient.java` (+171/-0)

**修改目的**：新增 GCP KMS 客户端实现。

**工作逻辑**：
- `initialize()`：从配置属性创建 `GCPProperties`，如果有 OAuth2 token 则通过 `GCPAuthUtils` 构建凭证并设置到 `KeyManagementServiceSettings`，否则使用 Google 应用默认凭证（Application Default Credentials）。创建 `KeyManagementServiceClient` 并加入 `CloseableGroup`。
- `wrapKey()`：构建 `EncryptRequest`，通过 `ByteStringShim` 将明文 DEK 设置为 ByteString，调用 `kmsClient.encrypt()` 加密，返回密文。
- `unwrapKey()`：构建 `DecryptRequest`，通过 `ByteStringShim` 将密文设置为 ByteString，调用 `kmsClient.decrypt()` 解密，返回明文。
- `close()`：关闭 `CloseableGroup` 释放所有资源。
- `ByteStringShim` 内部类：通过 `DynClasses` 和 `DynMethods` 动态加载 ByteString 类和方法，优先使用重定位后的类名（`org.apache.iceberg.gcp.shaded.com.google.protobuf.ByteString`），不存在时回退到原始类名。提供 `setPlainText`、`setCipherText`、`getCipherText`、`getPlainText` 四个静态方法。

### `gcp/src/main/java/org/apache/iceberg/gcp/GCPAuthUtils.java` (+62/-0)

**修改目的**：提取 OAuth2 认证工具类。

**工作逻辑**：`oauth2CredentialsFromGcpProperties()` 从 `GCPProperties` 读取 OAuth2 token 和过期时间构建 `AccessToken`。如果启用了 refresh 且配置了 refresh endpoint，则创建 `OAuth2RefreshCredentialsHandler` 并加入 `CloseableGroup`，构建 `OAuth2CredentialsWithRefresh`；否则构建普通 `OAuth2Credentials`。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/PrefixedStorage.java` (+13/-29)

**修改目的**：重构认证逻辑，使用 `GCPAuthUtils` 替代内联代码。

**工作逻辑**：移除内联的 OAuth2 凭证构建逻辑（AccessToken、OAuth2Credentials、OAuth2CredentialsWithRefresh 等），改为调用 `GCPAuthUtils.oauth2CredentialsFromGcpProperties()`。将原来直接管理的 `refreshHandler` 替换为 `CloseableGroup`，`close()` 方法通过 `CloseableGroup.close()` 统一释放资源。

### `build.gradle` (+22/-0)

**修改目的**：添加 KMS 依赖和集成测试构建配置。

**工作逻辑**：在 `iceberg-gcp` 项目中添加 `compileOnly "com.google.cloud:google-cloud-kms"` 依赖。新增 `integration` source set（`src/integration/java` 和 `src/integration/resources`），配置 `integrationImplementation` 和 `integrationRuntime` 继承测试配置，定义 `integrationTest` Gradle 任务。

### `gcp-bundle/build.gradle` (+1/-0)

**修改目的**：将 KMS 依赖纳入 GCP bundle。

**工作逻辑**：添加 `implementation "com.google.cloud:google-cloud-kms"` 到 bundle 的依赖列表，使 bundle 包含 KMS 客户端。

### `gcp/src/integration/java/org/apache/iceberg/gcp/TestKeyManagementClient.java` (+115/-0)

**修改目的**：KMS 客户端集成测试基类。

**工作逻辑**：定义抽象的 `init()` 和 `properties()` 方法供子类实现不同认证方式。`before()` 在 GCP 中创建 KeyRing 和 CryptoKey，`after()` 清理资源。测试 `wrapKey` 和 `unwrapKey` 的正确性（加密后解密应得到原始数据）。

### `gcp/src/integration/java/org/apache/iceberg/gcp/TestKeyManagementClientWithAppCreds.java` (+50/-0)

**修改目的**：使用应用默认凭证的 KMS 集成测试。

### `gcp/src/integration/java/org/apache/iceberg/gcp/TestKeyManagementClientWithOAuth.java` (+63/-0)

**修改目的**：使用 OAuth2 凭证的 KMS 集成测试。

## 总结

该提交为 GCP 模块新增了完整的 Cloud KMS 密钥管理客户端实现，使 Iceberg 能够在 GCP 环境下使用 KMS 进行数据加密密钥的包装和解包。同时重构了认证逻辑为共享工具类，解决了 bundle 重定位的兼容性问题，并补充了集成测试。这是一个较大的功能新增提交，涉及 8 个文件，新增 501 行代码。
