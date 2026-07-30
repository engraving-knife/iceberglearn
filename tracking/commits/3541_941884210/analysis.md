# 提交 3541：Hive encryption nits (#14659)

## 提交信息

- **序号**：3541 / 4088
- **哈希**：9418842100e7b0e360a13df95d85b9b823f55426
- **短哈希**：941884210
- **日期**：2026-04-15 16:26:44 -0700
- **作者**：Sreesh Maheshwar
- **提交说明**：Hive encryption nits (#14659)
- **PR/Issue**：#14659

## 总体目的

这是对 Hive 表加密相关代码的一组清理和小改进（nits），主要解决几个问题：

1. **错误处理规范化**：`HiveTableOperations` 中检查 key management client 是否为 null 时用的是 `throw new RuntimeException(...)`，不够规范。改为使用 `Preconditions.checkArgument`，并提示用户设置 `ENCRYPTION_KMS_IMPL` catalog 属性，错误信息更友好。

2. **不可变集合**：构建 `encryptionProperties` 时用 `Maps.newHashMap()` 然后 `put`，改为用 `ImmutableMap.of()` 一次性构建不可变 map，更简洁且避免可变性。

3. **加密 key 修改校验的作用域 bug**：原代码中校验「不能删除/修改加密 key」的逻辑有一个作用域问题——`removedProps` 的计算和后续的 key 校验 if 语句在同一个外层 if 块内，但 `removedProps.contains(...)` 和 key 修改校验实际上应该只在 `base != null` 时执行。原代码把 `if (removedProps.contains(...))` 放在了外层 if 之外（缩进显示），可能导致 `removedProps` 未定义或逻辑错误。本提交把两个校验都收拢到 `base != null` 的块内，用 `Preconditions.checkArgument` 替代分散的 `throw new IllegalArgumentException`。

4. **错误消息措辞**：把 "Cannot remove key in encrypted table" 改为 "Cannot remove key ID from an encrypted table"，"Cannot modify key in encrypted table" 改为 "Cannot modify key ID of an encrypted table"，更准确（是 key ID 而非 key 本身）。

5. **新增测试**：新增 `testReplaceKeyChange` 测试，验证 `REPLACE TABLE` 时修改加密 key 也会被拒绝，覆盖此前未覆盖的场景。同时让现有测试断言更精确（断言异常类型和完整消息）。

## 如何达成设计目的

在 `HiveTableOperations` 中：
- 用 `Preconditions.checkArgument` 替代手动 `throw new RuntimeException`/`IllegalArgumentException`
- 用 `ImmutableMap.of()` 替代 `Maps.newHashMap()` + `put`
- 把加密 key 校验逻辑收拢到 `base != null` 块内
- 错误消息补充提示 `ENCRYPTION_KMS_IMPL` 属性

在两个 Spark 版本的 `TestTableEncryption` 中：
- `testKeyDelete` 和 `testKeyAlter` 改为精确断言异常类型（`SparkException`）和完整消息
- 新增 `testReplaceKeyChange` 测试 `REPLACE TABLE` 修改 key 的拒绝场景

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+24/-17 lines)

**修改目的**：规范化加密相关错误处理和集合构建。

**工作逻辑**：
1. key management client 校验：
```java
-      if (keyManagementClient == null) {
-        throw new RuntimeException(
-            "Can't create encryption manager, because key management client is not set");
-      }
+      Preconditions.checkArgument(
+          keyManagementClient != null,
+          "Cannot create encryption manager without a key management client. Consider setting the '%s' catalog property",
+          CatalogProperties.ENCRYPTION_KMS_IMPL);
```
2. encryptionProperties 构建：
```java
-      Map<String, String> encryptionProperties = Maps.newHashMap();
-      encryptionProperties.put(TableProperties.ENCRYPTION_TABLE_KEY, tableKeyId);
-      encryptionProperties.put(
-          TableProperties.ENCRYPTION_DEK_LENGTH, String.valueOf(encryptionDekLength));
+      Map<String, String> encryptionProperties =
+          ImmutableMap.of(
+              TableProperties.ENCRYPTION_TABLE_KEY, tableKeyId,
+              TableProperties.ENCRYPTION_DEK_LENGTH, String.valueOf(encryptionDekLength));
```
3. 加密 key 修改校验收拢到 `base != null` 块内，用 `Preconditions.checkArgument`：
```java
+        Preconditions.checkArgument(
+            !removedProps.contains(TableProperties.ENCRYPTION_TABLE_KEY),
+            "Cannot remove key ID from an encrypted table");
+        Preconditions.checkArgument(
+            Objects.equals(
+                base.properties().get(TableProperties.ENCRYPTION_TABLE_KEY),
+                metadata.properties().get(TableProperties.ENCRYPTION_TABLE_KEY)),
+            "Cannot modify key ID of an encrypted table");
```
新增 import：`CatalogProperties`、`Preconditions`、`ImmutableMap`，移除 `Maps`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+16/-3 lines)

**修改目的**：精确化现有测试断言并新增 REPLACE TABLE 场景测试。

**工作逻辑**：
- `testKeyDelete`：改为断言 `SparkException` 且消息精确匹配 `"Unsupported table change: Cannot remove key ID from an encrypted table"`
- `testKeyAlter`：改为断言 `SparkException` 且消息精确匹配 `"Unsupported table change: Cannot modify key ID of an encrypted table"`
- 新增 `testReplaceKeyChange`：
```java
@TestTemplate
public void testReplaceKeyChange() {
  assertThatThrownBy(
          () -> sql(
              "REPLACE TABLE %s (id bigint) USING iceberg TBLPROPERTIES ('encryption.key-id'='%s')",
              tableName, UnitestKMS.MASTER_KEY_NAME2))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Cannot modify key ID of an encrypted table");
}
```
验证 REPLACE TABLE 用不同 key 会被拒绝。新增 `SparkException` import。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+16/-3 lines)

**修改目的**：Spark 4.1 版本同步相同测试改动。

**工作逻辑**：与 v4.0 完全相同的改动。

## 总结

本提交对 Hive 表加密代码做了一组清理：用 `Preconditions.checkArgument` 规范化错误处理、用 `ImmutableMap.of` 替代可变 map、修复加密 key 校验的作用域问题、改善错误消息（提示 `ENCRYPTION_KMS_IMPL` 属性、措辞改为 "key ID"）。同时新增 `REPLACE TABLE` 修改 key 的拒绝测试，并让现有测试断言更精确。属于代码质量与健壮性改进，无功能行为变化。
