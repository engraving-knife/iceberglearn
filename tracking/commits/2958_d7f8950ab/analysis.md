# 提交 2958：Exception on encryption key altering (#14723)

## 提交信息

- **序号**：2958 / 4088
- **哈希**：d7f8950ab4a74ece5b02a5a76741a01e61df3b7d
- **短哈希**：d7f8950ab
- **日期**：2025-12-04
- **作者**：ggershinsky
- **提交说明**：Exception on encryption key altering (#14723)
- **PR/Issue**：#14723

## 总体目的

Iceberg 支持表级加密，加密密钥 ID 存储在表属性 `TableProperties.ENCRYPTION_TABLE_KEY`（即 `encryption.key-id`）中。此前的实现（在 `HiveTableOperations.doCommit` 中）仅检测用户是否试图**删除**加密表的密钥属性（即该属性出现在 `removedProps` 中），若删除则抛出异常阻止操作。但这里存在一个安全漏洞：用户可以通过 `ALTER TABLE ... SET TBLPROPERTIES ('encryption.key-id'='新值')` 来**修改**（替换）已有加密表的密钥 ID，而旧代码不会拦截这种修改操作。

修改加密表的密钥 ID 是危险操作：表中已有数据文件是用旧密钥加密的，如果将表属性中的密钥 ID 改为另一个密钥，后续读取旧数据文件时将无法正确解密，导致数据不可读或静默损坏。因此，与"删除密钥"一样，"修改密钥"也应当被禁止。

此外，本提交还将原有"删除密钥"异常的类型从 `RuntimeException` 改为 `IllegalArgumentException`，使异常语义更准确——这本质上是参数/配置非法，而非不可预期的运行时错误。

## 如何达成设计目的

在 `HiveTableOperations` 的 commit 逻辑中，原有的"删除密钥"检查之后新增了一个对 `base`（当前表元数据）与 `metadata`（新提交元数据）中 `ENCRYPTION_TABLE_KEY` 属性值的等值比较：若两者不一致（即属性值被修改），则抛出 `IllegalArgumentException("Cannot modify key in encrypted table")`。使用 `Objects.equals` 进行空安全比较，并用 `base != null` 保护首次建表场景。配套测试通过 `ALTER TABLE SET TBLPROPERTIES` 验证修改密钥会触发新异常。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+8/-1 lines)

**修改目的**：禁止修改加密表的密钥 ID，并将删除密钥的异常类型改为 `IllegalArgumentException`。

**工作逻辑**：
改动位于 `doCommit` 方法的属性变更检查段。第一处将 `throw new RuntimeException("Cannot remove key in encrypted table")` 改为 `throw new IllegalArgumentException(...)`，语义更精确地表达"非法参数/配置"。

第二处新增修改检测：`if (base != null && !Objects.equals(base.properties().get(TableProperties.ENCRYPTION_TABLE_KEY), metadata.properties().get(TableProperties.ENCRYPTION_TABLE_KEY)))`。`base` 是当前已持久化的表元数据，`metadata` 是本次提交的新元数据。两者在 `ENCRYPTION_TABLE_KEY` 属性上的值若不相等（包括从有值变为 null、从 null 变为有值、或值被替换为另一个 key），均视为修改，抛出 `IllegalArgumentException("Cannot modify key in encrypted table")`。`base != null` 守卫确保首次创建表（无 base）时不会误判。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestTableEncryption.java` (+7/-0 lines)

**修改目的**：新增测试验证修改加密表密钥会被拒绝。

**工作逻辑**：
新增 `testKeyAlter` 测试方法（`@TestTemplate`），通过 `sql("ALTER TABLE %s SET TBLPROPERTIES ('encryption.key-id'='abcd')", tableName)` 尝试将加密表的密钥 ID 改为 `'abcd'`，使用 AssertJ 的 `assertThatThrownBy` 断言抛出异常且消息包含 `"Cannot modify key in encrypted table"`。该测试继承自 `CatalogTestBase`，在已建好的加密表上执行，验证新增的保护逻辑生效。

## 总结

本提交补齐了 Hive 加密表密钥保护的一个安全缺口：此前仅禁止删除密钥，现在也禁止修改密钥，防止因密钥 ID 变更导致已加密数据文件无法解密的数据损坏问题。同时将异常类型从 `RuntimeException` 统一为 `IllegalArgumentException`，提升了异常语义的准确性。改动简洁且针对性强，配套测试覆盖了修改密钥的拒绝场景。
