# 提交 3255：Core: Add support for encryption.kms-type with aws/azure/gcp (#15272)

## 提交信息

- **序号**：3255 / 4088
- **哈希**：8c2ca1d084fca37671ba8b38d59ea3f5a187b147
- **短哈希**：8c2ca1d08
- **日期**：2026-02-15
- **作者**：Yuya Ebihara
- **提交说明**：Core: Add support for encryption.kms-type with aws/azure/gcp (#15272)
- **PR/Issue**：#15272

## 总体目的

Iceberg 支持通过 KMS（Key Management Service）对表数据进行加密，此前用户需要通过 `encryption.kms-impl` 属性指定 KMS 客户端的全限定类名（如 `org.apache.iceberg.aws.AwsKeyManagementClient`）来启用加密。这种方式要求用户知道并输入冗长的类路径，使用体验不佳且容易出错。同时，`EncryptionUtil.createKmsClient` 方法中已存在 `// TODO: Add KMS implementations` 注释和 `Preconditions.checkArgument(kmsType == null, "Unsupported KMS type: %s", kmsType)` 的占位逻辑，说明框架早已预留了 `encryption.kms-type` 属性但尚未实现具体的类型映射。

本次提交完成了这一功能：用户现在可以通过简单的 `encryption.kms-type=aws`（或 `azure`、`gcp`）来选择预定义的 KMS 客户端，无需输入完整类名。系统在 `EncryptionUtil` 中将类型字符串自动映射到对应的实现类。同时保留了 `encryption.kms-impl` 用于自定义 KMS 客户端，但两者互斥——不能同时设置 `kms-type` 和 `kms-impl`，避免配置冲突。`HiveCatalog` 的初始化逻辑也相应更新，检测到任一属性存在时即创建 KMS 客户端。

这一改进显著降低了加密配置的使用门槛，使三大云厂商（AWS KMS、Azure Key Vault、GCP KMS）的加密集成可以通过一个简短的枚举值启用。

## 如何达成设计目的

在 `CatalogProperties` 中定义 KMS 类型常量（`aws`/`azure`/`gcp`）和对应的实现类全限定名常量。在 `EncryptionUtil.createKmsClient` 中用 switch 表达式将类型映射为实现类名，替换原来的拒绝逻辑。在 `HiveCatalog` 构造函数中将 KMS 客户端创建的触发条件从仅检查 `ENCRYPTION_KMS_IMPL` 扩展为同时检查 `ENCRYPTION_KMS_TYPE` 或 `ENCRYPTION_KMS_IMPL`。同步更新加密文档。

## 修改详情

### `core/src/main/java/org/apache/iceberg/CatalogProperties.java` (+10 lines)

**修改目的**：定义 KMS 类型枚举值和对应实现类的全限定名常量。

**工作逻辑**：
在已有的 `ENCRYPTION_KMS_TYPE` 和 `ENCRYPTION_KMS_IMPL` 属性键之后，新增三组常量：`ENCRYPTION_KMS_TYPE_AWS = "aws"`、`ENCRYPTION_KMS_TYPE_AZURE = "azure"`、`ENCRYPTION_KMS_TYPE_GCP = "gcp"` 作为用户可配置的类型值；`ENCRYPTION_KMS_IMPL_AWS = "org.apache.iceberg.aws.AwsKeyManagementClient"`、`ENCRYPTION_KMS_IMPL_AZURE = "org.apache.iceberg.azure.keymanagement.AzureKeyManagementClient"`、`ENCRYPTION_KMS_IMPL_GCP = "org.apache.iceberg.gcp.GcpKeyManagementClient"` 作为对应的实现类路径。这些常量集中定义在 `CatalogProperties` 中，便于在整个代码库中引用，避免硬编码字符串。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (+15/-2 lines)

**修改目的**：实现 kms-type 到 kms-impl 的自动映射，替换原先的占位拒绝逻辑。

**工作逻辑**：
原逻辑为 `Preconditions.checkArgument(kmsType == null, "Unsupported KMS type: %s", kmsType)`，即拒绝任何非 null 的 kmsType。新逻辑改为：当 `kmsType != null` 时，使用 switch 表达式（Java 17+ 特性）将 `kmsType.toLowerCase(Locale.ROOT)` 映射到对应的实现类常量——`"aws"` 映射到 `ENCRYPTION_KMS_IMPL_AWS`，`"azure"` 映射到 `ENCRYPTION_KMS_IMPL_AZURE`，`"gcp"` 映射到 `ENCRYPTION_KMS_IMPL_GCP`，default 分支抛出 `IllegalStateException("Unsupported KMS type: " + kmsType)`。映射结果赋值给局部变量 `kmsImpl`，覆盖入参中可能传入的 kmsImpl。

值得注意的是，该方法上游（`createKmsClient` 入口处）已有 `Preconditions.checkArgument` 校验 `kmsType` 和 `kmsImpl` 不能同时设置（测试 `testInvalidTypeAndImpl` 验证此互斥约束），因此此处的 `kmsImpl` 赋值是在 kmsImpl 入参为 null 的前提下进行的。使用 `toLowerCase(Locale.ROOT)` 确保类型匹配不受区域设置影响。后续代码使用 `DynConstructors` 反射加载映射出的实现类。

### `core/src/test/java/org/apache/iceberg/encryption/TestEncryptionUtil.java` (+17 lines)

**修改目的**：验证 kms-type 和 kms-impl 互斥约束的正确性。

**工作逻辑**：
新增测试 `testInvalidTypeAndImpl`，同时设置 `ENCRYPTION_KMS_TYPE=aws` 和 `ENCRYPTION_KMS_IMPL=org.apache.iceberg.aws.AwsKeyManagementClient`，断言 `EncryptionUtil.createKmsClient` 抛出 `IllegalArgumentException`，且消息为 `"Cannot set both KMS type (aws) and KMS impl (org.apache.iceberg.aws.AwsKeyManagementClient)"`。该测试确保用户不会同时配置两种 KMS 指定方式，避免歧义。

### `docs/docs/encryption.md` (+4/-2 lines)

**修改目的**：更新加密文档，反映新增的 `encryption.kms-type` 配置选项。

**工作逻辑**：
将激活加密所需的第一步描述从"Catalog property `encryption.kms-impl`，指定 KMS 客户端的类路径"改为"Catalog property 可以是 `encryption.kms-type`（用于预定义 KMS 客户端：`aws`、`azure` 或 `gcp`）或 `encryption.kms-impl`（用类路径指定自定义 KMS 客户端）"。同时将 Spark 配置示例从 `--conf spark.sql.catalog.local.encryption.kms-impl=org.apache.iceberg.aws.AwsKeyManagementClient` 简化为 `--conf spark.sql.catalog.local.encryption.kms-type=aws`，直观展示了新配置方式的简洁性。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (+3/-1 lines)

**修改目的**：扩展 HiveCatalog 初始化时 KMS 客户端创建的触发条件。

**工作逻辑**：
将 KMS 客户端创建的判断条件从 `catalogProperties.containsKey(CatalogProperties.ENCRYPTION_KMS_IMPL)` 扩展为 `catalogProperties.containsKey(CatalogProperties.ENCRYPTION_KMS_TYPE) || catalogProperties.containsKey(CatalogProperties.ENCRYPTION_KMS_IMPL)`。这确保用户通过 `encryption.kms-type=aws` 配置时，HiveCatalog 也能正确触发 KMS 客户端的创建。REST Catalog 等其他目录类型若已有类似逻辑也需相应更新，但本次提交仅修改了 HiveCatalog。

## 总结

本次提交实现了 `encryption.kms-type` 配置功能，使用户可以通过简短的枚举值（`aws`/`azure`/`gcp`）启用三大云厂商的 KMS 加密，无需输入冗长的实现类路径。核心改动在 `EncryptionUtil` 中用 switch 表达式完成类型到实现类的映射，`HiveCatalog` 同步扩展触发条件，文档和测试同步更新。这显著降低了 Iceberg 加密功能的使用门槛，完成了框架中早已预留的 KMS 类型映射 TODO。
