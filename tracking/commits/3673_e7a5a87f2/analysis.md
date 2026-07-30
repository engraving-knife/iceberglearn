# 提交 3673：Azure: Avoid depending on KeyWrapAlgorithm in AzureProperties (#16186)

## 提交信息

- **序号**：3673 / 4088
- **哈希**：e7a5a87f26f9de5b200254155aa037368b13a29c
- **短哈希**：e7a5a87f2
- **日期**：2026-05-08 11:39:59 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Azure: Avoid depending on KeyWrapAlgorithm in AzureProperties (#16186)
- **PR/Issue**：#16186

## 总体目的

这个提交将 `AzureProperties` 对 Azure SDK `KeyWrapAlgorithm` 枚举类的依赖移除，改为使用纯字符串表示 key wrap 算法。

此前 `AzureProperties` 直接 import 并使用 `com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm` 枚举类：`keyWrapAlgorithm()` 方法返回 `KeyWrapAlgorithm` 类型，默认值通过 `KeyWrapAlgorithm.RSA_OAEP_256.getValue()` 获取。这意味着 `AzureProperties`（一个配置类）硬依赖了 Azure Key Vault Cryptography 模块的枚举类。

这种耦合的问题在于：`AzureProperties` 是 Azure 模块的核心配置类，被广泛使用（包括在不使用 Key Vault 加密的场景）。将其与 `KeyWrapAlgorithm` 枚举耦合，使得不使用 Key Vault 的用户也间接依赖该类，且在未来 Azure SDK 版本升级时 `KeyWrapAlgorithm` 可能变化导致兼容性问题。本提交将 `keyWrapAlgorithm()` 改为返回纯 `String`，将 `KeyWrapAlgorithm` 的使用下推到真正需要它的 `AzureKeyManagementClient`。

## 如何达成设计目的

1. 在 `AzureProperties` 中移除 `KeyWrapAlgorithm` 的 import，新增常量 `DEFAULT_KEY_WRAP_ALGORITHM = "RSA-OAEP-256"`（注释说明必须匹配 `KeyWrapAlgorithm.RSA_OAEP_256.getValue()`）。
2. `keyWrapAlgorithm()` 方法返回类型从 `KeyWrapAlgorithm` 改为 `String`，直接返回存储的字符串值。
3. 在 `AzureKeyManagementClient` 中，调用 `KeyWrapAlgorithm.fromString(azureProperties.keyWrapAlgorithm())` 将字符串转换回枚举。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java` (+6/-5 lines)

**修改目的**：移除对 KeyWrapAlgorithm 枚举的依赖。

**工作逻辑**：
1. 移除 import `com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm`。
2. 新增常量：
```java
// Must match KeyWrapAlgorithm.RSA_OAEP_256.getValue() from azure-security-keyvault-keys
private static final String DEFAULT_KEY_WRAP_ALGORITHM = "RSA-OAEP-256";
```
3. 默认值改用常量：
```java
this.keyWrapAlgorithm =
    properties.getOrDefault(
        AzureProperties.AZURE_KEYVAULT_KEY_WRAP_ALGORITHM, DEFAULT_KEY_WRAP_ALGORITHM);
```
4. 方法返回类型改为 String：
```java
public String keyWrapAlgorithm() {
  return this.keyWrapAlgorithm;
}
```

### `azure/src/main/java/org/apache/iceberg/azure/keymanagement/AzureKeyManagementClient.java` (+2/-1 line)

**修改目的**：在使用处将字符串转换为 KeyWrapAlgorithm 枚举。

**工作逻辑**：
```java
KeyWrapAlgorithm keyWrapAlgorithm =
    KeyWrapAlgorithm.fromString(azureProperties.keyWrapAlgorithm());
```

## 总结

这个提交将 `AzureProperties` 对 Azure SDK `KeyWrapAlgorithm` 枚举类的依赖解耦，改为使用纯字符串表示 key wrap 算法。`KeyWrapAlgorithm` 的枚举转换下推到真正需要它的 `AzureKeyManagementClient`。这降低了配置类与 Azure SDK 具体枚举类的耦合，提升了模块的灵活性和未来 SDK 升级的兼容性。
