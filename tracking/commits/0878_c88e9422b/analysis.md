# 提交 0878：Azure: Make AzureProperties w/ shared-key creds serializable (#10045)

## 提交信息

- **序号**：0878 / 4088
- **哈希**：c88e9422bb5a2a27ff6ee71c26740f20dd00ea29
- **短哈希**：c88e9422b
- **日期**：2024-06-26 15:41:14 +0200（Wed Jun 26 15:41:14 2024 +0200）
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：Azure: Make AzureProperties w/ shared-key creds serializable (#10045)
- **提交说明（含原因）**：As `StorageSharedKeyCredential` is not serializable, shared key auth doesn't work with Spark.
- **PR/Issue**：#10045

## 总体目的

Iceberg 的 Azure 模块通过 `AzureProperties` 类管理与 Azure Data Lake Storage (ADLS) 交互所需的各种配置（SAS token、连接字符串、shared-key 凭据、读写块大小等）。`AzureProperties` 实现了 `Serializable` 接口，因为它需要在 Spark 等分布式引擎中随任务序列化后分发到 executor 上执行。

在配置 shared-key 认证时，原先的 `AzureProperties` 构造器会直接构造一个 Azure SDK 提供的 `com.azure.storage.common.StorageSharedKeyCredential` 对象，并保存到字段 `namedKeyCreds` 中：

```java
this.namedKeyCreds = new StorageSharedKeyCredential(sharedKeyAccountName, sharedKeyAccountKey);
```

问题在于：Azure SDK 的 `StorageSharedKeyCredential` 类**没有实现 `Serializable`**。因此当 `AzureProperties` 被序列化（例如 Spark driver 把配置广播到 executor）时，整个对象图会因为 `namedKeyCreds` 字段不可序列化而抛出 `NotSerializableException`，导致 shared-key 认证方式在 Spark 场景下完全不可用——这正是本提交提交说明中明确指出的："As `StorageSharedKeyCredential` is not serializable, shared key auth doesn't work with Spark."

本提交的目的就是修复这个 bug：让 `AzureProperties` 在持有 shared-key 凭据时仍可被序列化，使 Spark 用户可以使用 Azure shared-key 认证方式访问 Iceberg 表。

## 如何达成设计目的

整体设计思路是"延迟构造 + 持有可序列化形式"：不直接持有 `StorageSharedKeyCredential` 实例，而是把它分解为两个 `String`（account name 和 account key），保存为一个 `Map.Entry<String, String>`（这是 `Serializable` 的）。等到真正需要使用凭据时——也就是在 `configureClientBuilder(DataLakeFileSystemClientBuilder)` 调用流程中——再从 `Map.Entry` 中还原出 `StorageSharedKeyCredential`，传递给 `builder.credential(...)`。

具体步骤：

1. 字段类型从 `StorageSharedKeyCredential namedKeyCreds` 改为 `Map.Entry<String, String> namedKeyCreds`。`Map.Entry` 的常见实现（如 `Maps.immutableEntry` 返回的 `ImmutableEntry`）都是 `Serializable` 的，前提是其 key 和 value 都可序列化——这里 key/value 都是 `String`，满足条件。
2. 构造器中不再 `new StorageSharedKeyCredential(...)`，而是 `Maps.immutableEntry(sharedKeyAccountName, sharedKeyAccountKey)`。
3. 在 `configureClientBuilder` 中使用时，从 entry 还原：`new StorageSharedKeyCredential(namedKeyCreds.getKey(), namedKeyCreds.getValue())`，再传给 builder。

这样 `AzureProperties` 的对象图就完全由 `Serializable` 类型构成（Map、String、Integer 等都可序列化），可以通过 Java 标准序列化机制在 Spark driver/executor 间传输。

## 修改详情

### `azure/src/main/java/org/apache/iceberg/azure/AzureProperties.java`

**修改目的**：让 `AzureProperties` 在持有 shared-key 凭据时仍可序列化。

**工作逻辑**：
- 新增 import `org.apache.iceberg.relocated.com.google.common.collect.Maps;`。
- 字段声明从
  ```java
  private StorageSharedKeyCredential namedKeyCreds;
  ```
  改为
  ```java
  private Map.Entry<String, String> namedKeyCreds;
  ```
  `Map.Entry<String, String>` 是可序列化的（前提是 key/value 可序列化，String 显然满足）。
- 构造器中构造逻辑从
  ```java
  this.namedKeyCreds = new StorageSharedKeyCredential(sharedKeyAccountName, sharedKeyAccountKey);
  ```
  改为
  ```java
  this.namedKeyCreds = Maps.immutableEntry(sharedKeyAccountName, sharedKeyAccountKey);
  ```
  `Maps.immutableEntry` 返回的 `ImmutableEntry` 实现了 `Serializable`。
- 在 `configureClientBuilder` 中使用凭据处从
  ```java
  builder.credential(namedKeyCreds);
  ```
  改为
  ```java
  builder.credential(
      new StorageSharedKeyCredential(namedKeyCreds.getKey(), namedKeyCreds.getValue()));
  ```
  即在使用时才现场构造 `StorageSharedKeyCredential`，避免把它作为字段保存。

### `azure/src/test/java/org/apache/iceberg/azure/AzurePropertiesTest.java`

**修改目的**：新增序列化回归测试，确保含 shared-key 凭据的 `AzureProperties` 能被 Java 序列化 round-trip。

**工作逻辑**：
- 新增多个 import，包括常量 `ADLS_CONNECTION_STRING_PREFIX`、`ADLS_READ_BLOCK_SIZE`、`ADLS_SAS_TOKEN_PREFIX`、`ADLS_WRITE_BLOCK_SIZE`、`ADLS_SHARED_KEY_ACCOUNT_KEY`、`ADLS_SHARED_KEY_ACCOUNT_NAME`、`assertThat`、`TestHelpers`。
- 新增测试 `testSerializable()`：
  - 构造一个 `AzureProperties`，同时配置 SAS token、connection string、读块大小 42、写块大小 42、shared-key 账户名 `me`、账户密钥 `secret`——覆盖所有可能影响序列化的字段。
  - 调用 `TestHelpers.roundTripSerialize(props)` 完成 Java 序列化 + 反序列化。
  - 断言反序列化后的 `adlsReadBlockSize()` 与 `adlsWriteBlockSize()` 与原对象相等（42）。
  - 该测试在修复前会因为 `StorageSharedKeyCredential` 不可序列化而抛 `NotSerializableException` 失败，修复后通过。

## 小结

- **成效**：修复了 `AzureProperties` 在持有 shared-key 凭据时无法被 Java 序列化的 bug，使 Spark 用户可以使用 Azure shared-key 认证方式访问 Iceberg 表（之前会因为 `NotSerializableException` 直接失败）。修复方式是把不可序列化的 `StorageSharedKeyCredential` 字段替换为可序列化的 `Map.Entry<String, String>`，在使用时再还原。
- **影响范围**：仅 Azure 模块的 2 个文件——`AzureProperties.java` 主代码（4 处改动：import、字段类型、构造、使用）与 `AzurePropertiesTest.java` 测试（新增 1 个测试用例 + import）。不波及其它模块。
- **回迁到 1.4.x 的注意事项**：这是一个直接的 bug 修复，**对 1.4.x 强烈建议回迁**，因为该 bug 会让 1.4.x 的 Azure shared-key 用户在 Spark 中无法使用。注意点：
  1. 改动很小且自包含，不依赖其他提交。回迁时只需带上 `AzureProperties.java` 与 `AzurePropertiesTest.java` 两个文件。
  2. 回迁后应在 1.4.x 环境中运行新增的 `testSerializable` 测试以验证修复有效（确保 `TestHelpers.roundTripSerialize` 在 1.4.x 中存在且 API 兼容）。
  3. 该修复对序列化格式有"隐式"影响：序列化的 `AzureProperties` 字节流从包含 `StorageSharedKeyCredential`（不可序列化）变为包含 `Map.Entry<String, String>`。由于原本根本无法序列化，不存在跨版本兼容问题。
  4. 如果 1.4.x 的 `AzureProperties` 已经有其他独立修改（例如额外的字段或方法），需要确保本回迁不与之冲突。
