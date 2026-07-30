# 提交 3073：AWS: Merge catalog properties with properties prefixed with client.credentials-provider. (#14608)

## 提交信息

- **序号**：3073 / 4088
- **哈希**：055a73a92cc2f14b8adbd7b0851d6c5b8538db5c
- **短哈希**：055a73a92
- **日期**：2026-01-07
- **作者**：Thomas Powell
- **提交说明**：AWS: Merge catalog properties with properties prefixed with client.credentials-provider. (#14608)
- **PR/Issue**：#14608

## 总体目的

本提交修复 AWS 客户端属性（`AwsClientProperties`）在配置 credentials provider 时属性合并逻辑的缺陷。问题涉及两类属性的传递：一是通用的 catalog 属性（非前缀属性），二是以 `client.credentials-provider.` 为前缀的专属属性（前缀属性，前缀会被剥离后传入 credentials provider 的配置 map）。

此前的实现存在两个问题：

1. **属性传递时机不当**：构造 `AwsClientProperties` 时，将 `allProperties`（全部原始属性）保存为字段，但 `clientCredentialsProviderProperties` 只包含前缀属性。直到 `credentialsProvider()` 方法被调用且启用 vended credentials（refresh credentials）时，才在方法内通过 `clientCredentialsProviderProperties.putAll(allProperties)` 将全部属性合并进去。这意味着对于非 vended 场景，通用 catalog 属性不会传递给 credentials provider；且 `putAll` 直接修改了 `clientCredentialsProviderProperties`，产生副作用。

2. **vended 场景下属性覆盖语义错误**：`putAll(allProperties)` 会用通用属性覆盖前缀属性（如果存在同名键），但实际期望应是前缀属性优先——用户显式为 credentials provider 设置的前缀属性应覆盖通用属性。

修复后的方案在构造时即完成属性合并：取所有非 `client.credentials-provider.` 前缀的通用属性作为基础，再用前缀属性（已剥离前缀）覆盖合并，确保前缀属性优先。同时在 `PropertyUtil` 中新增通用的 `mergeProperties` 工具方法，移除了 `AwsClientProperties` 中的 `allProperties` 字段和 `SerializableMap` 依赖。

## 如何达成设计目的

在 `AwsClientProperties` 构造函数中，使用 `PropertyUtil.filterProperties` 过滤出所有非前缀属性，与 `PropertyUtil.propertiesWithPrefix` 提取的前缀属性通过新增的 `PropertyUtil.mergeProperties` 合并（前缀属性作为 overrides 优先），直接赋给 `clientCredentialsProviderProperties`。在 `credentialsProvider()` 的 vended 分支中移除延迟合并逻辑。同时在 `PropertyUtil` 新增 `mergeProperties` 方法并配套单元测试。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/AwsClientProperties.java` (+6/-7 lines)

**修改目的**：修正 credentials provider 属性的合并时机和覆盖优先级。

**工作逻辑**：
- 新增 import `java.util.function.Predicate`，移除 import `org.apache.iceberg.util.SerializableMap`。
- 移除字段 `private final Map<String, String> allProperties;`，无参构造函数中移除 `this.allProperties = null;`，有参构造函数中移除 `this.allProperties = SerializableMap.copyOf(properties);`。
- 有参构造函数中 `clientCredentialsProviderProperties` 的赋值改为：`PropertyUtil.mergeProperties(PropertyUtil.filterProperties(properties, Predicate.not(property -> property.startsWith(CLIENT_CREDENTIAL_PROVIDER_PREFIX))), PropertyUtil.propertiesWithPrefix(properties, CLIENT_CREDENTIAL_PROVIDER_PREFIX))`。即先过滤出所有非前缀属性作为基础，再用前缀属性（已剥离前缀）作为 overrides 覆盖合并，确保前缀属性优先。
- `credentialsProvider()` 方法 vended 分支中移除 `clientCredentialsProviderProperties.putAll(allProperties);`，因为合并已在构造时完成。

### `core/src/main/java/org/apache/iceberg/util/PropertyUtil.java` (+15/-0 lines)

**修改目的**：新增通用的 `mergeProperties` 方法。

**工作逻辑**：
新增 `public static Map<String, String> mergeProperties(Map<String, String> properties, Map<String, String> overrides)`：若 overrides 为 null 或空则返回 properties；若 properties 为 null 或空则返回 overrides；否则以 properties 为基础创建 HashMap，再 `putAll(overrides)` 覆盖，返回合并结果。该方法实现了"后者覆盖前者"的语义。

### `core/src/test/java/org/apache/iceberg/util/TestPropertyUtil.java` (+41/-0 lines)

**修改目的**：为 `mergeProperties` 方法新增单元测试。

**工作逻辑**：
新建测试类 `TestPropertyUtil`，测试 `mergeProperties` 的各种边界：null 入参（返回 null）、空 overrides（返回原 properties）、空 properties（返回 overrides）、正常合并（`{k1:v1, k2:v2}` 与 `{k1:v11, k3:v3}` 合并后为 `{k1:v11, k2:v2, k3:v3}`，验证 overrides 覆盖语义）。

### `aws/src/test/java/org/apache/iceberg/aws/TestAwsClientProperties.java` (+11/-10 lines)

**修改目的**：更新 vended credentials 测试以反映新的属性合并逻辑。

**工作逻辑**：
- 将测试中的 `CatalogProperties.URI` 值从 `"http://localhost:1234/v1"` 改为 `"http://localhost:1234/v1/catalog"`（更真实的 catalog URI）。
- 移除原来的 `expectedProperties`（用 `putAll(properties)` 合并全部属性的方式构建），改为直接断言 `VendedCredentialsProvider` 的 properties 等于一个精确的 ImmutableMap，包含 `REFRESH_CREDENTIALS_ENDPOINT`、`credentials.uri`（由前缀属性 `client.credentials-provider.uri` 剥离前缀而来）、`CatalogProperties.URI`、`OAuth2Properties.TOKEN`。这验证了通用属性和前缀属性被正确合并，且前缀属性 `credentials.uri` 与通用属性 `uri` 共存。

## 总结

本提交修正了 AWS credentials provider 属性合并的时机和优先级问题：将延迟合并改为构造时合并，确保前缀属性优先覆盖通用属性，并移除了不必要的 `allProperties` 字段和 `SerializableMap` 依赖。新增的 `PropertyUtil.mergeProperties` 工具方法可被其他模块复用。修复后 vended credentials 场景下的属性传递更加正确和可预测。
