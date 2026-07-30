# 提交 2372：AWS: KeyManagementClient implementation that works with AWS KMS (#13136)

## 提交信息

- **序号**：2372 / 4088
- **哈希**：77f1f5ba47f0e5250b8af4e16cbe6febc8244ee5
- **短哈希**：77f1f5ba4
- **日期**：2025-07-19 06:18:24 -0600
- **作者**：Adam Szita
- **提交说明**：AWS: KeyManagementClient implementation that works with AWS KMS (#13136)
- **PR/Issue**：#13136

## 总体目的

本提交为 Iceberg 的加密模块实现了 AWS KMS（Key Management Service）的 KeyManagementClient 接口实现。Iceberg 的加密架构中定义了一个 `KeyManagementClient` 接口，用于密钥的包装（wrap）、解包（unwrap）和生成（generate），此前该接口是包级私有的，且没有 AWS KMS 的官方实现。

通过本次修改，用户可以使用 AWS KMS 来管理 Iceberg 表的主加密密钥，实现信封加密（envelope encryption）模式。AwsKeyManagementClient 支持使用 KMS 管理的主密钥来加密/解密数据密钥，以及生成新的数据密钥。同时，将 KeyManagementClient 接口从包级私有改为 public，使得外部模块（如 aws 模块）可以实现该接口。

## 如何达成设计目的

设计思路是创建一个独立的 AwsKeyManagementClient 实现类，通过 AWS SDK 的 KmsClient 与 AWS KMS 服务交互，并支持通过配置属性自定义加密算法和数据密钥规格。关键设计点如下：

1. **接口公开化**：将 core 模块中的 `KeyManagementClient` 接口从包级私有改为 public，同时将其内部类 `KeyGenerationResult` 的构造函数也改为 public，使外部模块可以实现该接口。
2. **实现类设计**：新建 `AwsKeyManagementClient` 类实现 `KeyManagementClient` 接口，内部持有 `KmsClient` 实例和可配置的加密算法、数据密钥规格。
3. **配置扩展**：在 `AwsProperties` 中新增 KMS 相关配置项，包括加密算法（`kms.encryption-algorithm-spec`，默认 SYMMETRIC_DEFAULT）和数据密钥规格（`kms.data-key-spec`，默认 AES_256）。
4. **集成测试**：新建集成测试类，通过 AWS 环境变量触发测试，验证密钥包装/解包和密钥生成功能。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsKeyManagementClient.java` (+105/-0 lines, 新建文件)

**修改目的**：实现基于 AWS KMS 的 KeyManagementClient。

**工作逻辑**：该类实现了 KeyManagementClient 接口的全部方法：
- `initialize`：通过 AwsClientFactories 创建 KmsClient，并从 AwsProperties 读取加密算法和数据密钥规格配置。
- `wrapKey`：调用 KMS 的 Encrypt API，使用指定的 wrappingKeyId（KMS 主密钥 ID）和配置的加密算法对明文密钥进行加密，返回密文。
- `unwrapKey`：调用 KMS 的 Decrypt API，使用指定的 wrappingKeyId 和加密算法对密文进行解密，返回明文密钥。
- `supportsKeyGeneration`：返回 true，表示支持密钥生成。
- `generateKey`：调用 KMS 的 GenerateDataKey API，生成新的数据密钥，返回包含明文密钥和密文密钥的 KeyGenerationResult。
- `close`：关闭 KmsClient 资源。

### `aws/src/main/java/org/apache/iceberg/aws/AwsProperties.java` (+34/-0 lines)

**修改目的**：添加 KMS 相关的配置属性和访问方法。

**工作逻辑**：新增两个配置项常量及其默认值：
- `KMS_ENCRYPTION_ALGORITHM_SPEC`（"kms.encryption-algorithm-spec"）：指定加密/解密主密钥时使用的加密算法，默认为 `EncryptionAlgorithmSpec.SYMMETRIC_DEFAULT`。
- `KMS_DATA_KEY_SPEC`（"kms.data-key-spec"）：指定 KMS 生成的数据密钥长度，默认为 `DataKeySpec.AES_256`。
在两个 AwsProperties 构造函数中均初始化这些字段为默认值，在有 properties 参数的构造函数中从配置映射读取覆盖值。新增两个 getter 方法 `kmsEncryptionAlgorithmSpec()` 和 `kmsDataKeySpec()`。

### `core/src/main/java/org/apache/iceberg/encryption/KeyManagementClient.java` (+2/-2 lines)

**修改目的**：将 KeyManagementClient 接口及其内部类的构造函数从包级私有改为 public。

**工作逻辑**：将 `interface KeyManagementClient` 改为 `public interface KeyManagementClient`，使 aws 等外部模块可以实现该接口。同时将 `KeyGenerationResult` 的构造函数从包级私有改为 public，使外部模块可以创建该类的实例。

### `aws/src/integration/java/org/apache/iceberg/aws/TestKeyManagementClient.java` (+127/-0 lines, 新建文件)

**修改目的**：为 AwsKeyManagementClient 创建集成测试。

**工作逻辑**：这是一个需要真实 AWS 环境的集成测试类，通过 `@EnabledIfEnvironmentVariables` 注解要求设置 AWS 凭证环境变量后才运行。测试逻辑包括：
- `@BeforeAll`：通过 KmsClient 创建一个 SYMMETRIC_DEFAULT 类型的测试用主密钥。
- `@AfterAll`：将测试密钥标记为待删除状态（KMS 最短 7 天后删除），并关闭客户端。
- `testKeyWrapping`：验证使用主密钥包装密钥后可以正确解包还原。
- `testKeyGeneration`：参数化测试，验证在不同 DataKeySpec（null 使用默认值、AES_128、AES_256）下生成的密钥长度正确，且生成的密钥可以正确解包还原。

## 总结

本提交是一个重要的功能新增，为 Iceberg 的加密模块提供了 AWS KMS 的原生支持。通过实现 KeyManagementClient 接口，用户可以使用 AWS KMS 管理的主密钥对 Iceberg 表进行信封加密，支持可配置的加密算法和数据密钥规格。同时将核心接口公开化，为其他云平台的 KMS 实现铺平了道路。该提交包含完整的实现和集成测试，新增 268 行代码，是一个高质量的功能贡献。
