# 提交 2974：Core: Disallow encryption table properties in v1 and v2 (#14668)

## 提交信息

- **序号**：2974 / 4088
- **哈希**：19b4bd024486d9d516d0e547e273419c1bc7074e
- **短哈希**：19b4bd024
- **日期**：2025-12-07
- **作者**：Yuya Ebihara
- **提交说明**：Core: Disallow encryption table properties in v1 and v2 (#14668)
- **PR/Issue**：#14668

## 总体目的

Iceberg v3 引入了表级加密能力，通过两个表属性配置：`encryption.key-id`（`TableProperties.ENCRYPTION_TABLE_KEY`，指定主密钥）与 `encryption.data-key-length`（`TableProperties.ENCRYPTION_DEK_LENGTH`，数据密钥长度，默认 16）。这两个属性是 v3 才支持的语义，在 v1/v2 表上设置它们没有意义且行为未定义——此前 Iceberg 在创建新表时并不校验这一点，用户在 v1/v2 表上误设这些属性会被静默接受，可能造成"配置了却实际不生效"的混淆，或在后续提交/读取时出现难以理解的错误。

本提交要解决的问题是：在创建新表（`TableMetadata.newTableMetadata`）时，对格式版本进行前置校验，显式禁止在 v1 和 v2 表上设置上述加密表属性，以 fail-fast 的方式给出清晰错误信息，避免误用。这与 v3 加密特性的边界一致：加密仅在 v3 表上受支持，低版本表应在建表阶段就被拒绝。提交标题 "Disallow encryption table properties in v1 and v2" 即此意图。

## 如何达成设计目的

在 `EncryptionUtil` 中新增 `checkCompatibility(tableProperties, formatVersion)` 方法：当 `formatVersion < 3` 时，检查表属性键集与加密属性集合的交集是否为空，非空则抛 `IllegalArgumentException`。在 `TableMetadata.newTableMetadata` 中、`PropertyUtil.validateCommitProperties(properties)` 之后、构造 `Builder` 之前调用该校验。同时，原先使用 `encryption.key-id` 但未显式指定 `format-version`（默认为 2）的两个 Spark 加密测试（CTAS 与表加密）会在新校验下失败，因此补上 `'format-version'='3'` 使其符合 v3 语义。

## 修改详情

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (+22/-0 lines)

**修改目的**：新增针对加密表属性的版本兼容性校验。

**工作逻辑**：
新增常量集合 `ENCRYPTION_TABLE_PROPERTIES`（`ImmutableSet` of `TableProperties.ENCRYPTION_TABLE_KEY` 与 `TableProperties.ENCRYPTION_DEK_LENGTH`）。新增 public static 方法 `checkCompatibility(Map<String,String> tableProperties, int formatVersion)`：若 `formatVersion >= 3` 直接返回（v3 允许加密属性）；否则用 `Sets.intersection(ENCRYPTION_TABLE_PROPERTIES, tableProperties.keySet())` 求出实际出现的加密属性，并通过 `Preconditions.checkArgument(encryptionProperties.isEmpty(), "Invalid properties for v%s: %s", formatVersion, encryptionProperties)` 在非空时抛出 `IllegalArgumentException`，错误信息形如 `Invalid properties for v2: [encryption.key-id, encryption.data-key-length]`。这样把"低版本表不得配置加密属性"的不变量集中在加密工具类中，供建表路径调用。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (+3/-0 lines)

**修改目的**：在建表路径接入加密属性版本校验。

**工作逻辑**：
新增 `import ...EncryptionUtil;`，并在 `newTableMetadata(...)` 静态工厂中、`PropertyUtil.validateCommitProperties(properties);` 之后、`return new Builder()...build()` 之前插入 `EncryptionUtil.checkCompatibility(properties, formatVersion);`。该校验位于已有"新表才做"的校验序列中（如 `MetricsConfig.fromProperties(properties).validateReferencedColumns(schema)` 之后），与既有"对新表属性做前置校验、避免破坏已存在表"的策略一致——只作用于新建表，不影响历史表元数据加载。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java` (+19/-0 lines)

**修改目的**：验证在 v1/v2 表上设置加密属性会被拒绝。

**工作逻辑**：
新增 `@ParameterizedTest` + `@ValueSource(ints = {1, 2})` 的 `testEncryptionVersionValidation(int formatVersion)`，调用 `TableMetadata.newTableMetadata(...)` 并传入 `ImmutableMap.of("encryption.key-id", "test", "encryption.data-key-length", "5")` 与对应 `formatVersion`，断言抛出 `IllegalArgumentException` 且消息为 `"Invalid properties for v%s: [encryption.key-id, encryption.data-key-length]"`（`%s` 由 formatVersion 填充）。参数化覆盖 v1 与 v2 两个版本，确认两者均被拒绝。同时新增对应 import `org.junit.jupiter.params.provider.ValueSource`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestCTASEncryption.java` (+1/-1 lines)

**修改目的**：让 CTAS 加密测试显式声明 v3 表，以适配新校验。

**工作逻辑**：
在 `CREATE TABLE ... USING iceberg TBLPROPERTIES ( 'encryption.key-id'='%s')` 的 TBLPROPERTIES 中追加 `'format-version'='3'`，即改为 `TBLPROPERTIES ( 'encryption.key-id'='%s', 'format-version'='3')`。因为该测试使用 `encryption.key-id`，而默认 format-version 为 2，在新校验下会被拒绝，故必须显式声明为 v3 才符合加密特性的前置条件。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+1/-1 lines)

**修改目的**：让表加密测试显式声明 v3 表，以适配新校验。

**工作逻辑**：
与上一个测试对称：将 `CREATE TABLE ... TBLPROPERTIES ( 'encryption.key-id'='%s')` 改为 `TBLPROPERTIES ( 'encryption.key-id'='%s', 'format-version'='3')`，使带加密属性的建表语句在 v3 语义下执行，避免被新校验拦截。

## 总结

本提交在 Iceberg v3 表级加密特性的边界上补上前置校验：在 `newTableMetadata` 创建新表时，禁止在 v1/v2 表上设置 `encryption.key-id` 与 `encryption.data-key-length`，以 fail-fast 的 `IllegalArgumentException` 取代原先的静默接受，避免用户误用导致的不生效或难以定位的错误。校验逻辑集中在 `EncryptionUtil.checkCompatibility`，配套参数化单元测试覆盖 v1/v2 拒绝路径，并将两个使用加密属性的 Spark 测试显式升级为 v3 表以适配新规则。该改动对正确性和用户反馈即时性有实际价值，明确了加密特性仅属于 v3。
