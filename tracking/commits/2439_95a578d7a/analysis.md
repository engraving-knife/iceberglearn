# 提交 2439：Core: Fix case-insensitive validation of fields in schema evolution (#13697)

## 提交信息

- **序号**：2439 / 4088
- **哈希**：95a578d7ad96aa3c82dccd305f556c83b3360528
- **短哈希**：95a578d7a
- **日期**：2025-07-31 16:29:30 -0600
- **作者**：Tobias Riemenschneider
- **提交说明**：Core: Fix case-insensitive validation of fields in schema evolution (#13697)
- **PR/Issue**：#13697

## 总体目的

本提交修复了 schema 演进（schema evolution）中标识字段（identifier fields）校验的大小写敏感性问题。在 `SchemaUpdate` 中验证标识字段要求时，需要构建字段名到字段 ID 的映射（`nameToId`），以便根据标识字段名查找对应的字段 ID。此前，无论 schema 更新是否配置为大小写敏感，都使用 `TypeUtil.indexByName(struct)` 构建映射，该方法保留原始字段名大小写。

问题在于：当 `caseSensitive=false`（大小写不敏感模式）时，如果用户提供的标识字段名与表中字段名大小写不一致，将无法在 `nameToId` 映射中找到匹配项，导致校验失败。正确做法是大小写不敏感模式下应使用 `TypeUtil.indexByLowerCaseName(struct)` 构建全小写的映射，使查找时大小写不敏感。

这个 bug 会在大小写不敏感模式下添加标识字段时暴露，例如表中有列 `ID`，用户以 `id` 引用该列作为标识字段，会因找不到匹配而报错。

## 如何达成设计目的

根据 `caseSensitive` 标志选择不同的索引构建方式：大小写敏感时用 `indexByName`，大小写不敏感时用 `indexByLowerCaseName`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SchemaUpdate.java` (+3/-1 lines)

**修改目的**：修复大小写不敏感模式下标识字段校验的索引构建。

**工作逻辑**：将 `Map<String, Integer> nameToId = TypeUtil.indexByName(struct);` 修改为条件选择：
```java
Map<String, Integer> nameToId =
    caseSensitive ? TypeUtil.indexByName(struct) : TypeUtil.indexByLowerCaseName(struct);
```
这样在大小写不敏感模式下，映射的键全部为小写，后续按标识字段名查找时（也会被转为小写）能够正确匹配，避免因大小写差异导致的误判。

### `core/src/test/java/org/apache/iceberg/TestSchemaUpdate.java` (+16/-0 lines)

**修改目的**：新增测试验证大小写不敏感模式下的列名冲突检测。

**工作逻辑**：新增 `testAddMultipleRequiredColumnCaseInsensitive` 测试。在大小写不敏感模式下，尝试添加两个仅大小写不同的 required 列 `data` 和 `DATA`，断言抛出 `IllegalArgumentException`，消息为 `"Cannot build lower case index: data and DATA collide"`。这验证了 `indexByLowerCaseName` 能正确检测出大小写不敏感的列名冲突。该测试间接覆盖了修复后的索引构建路径。

## 总结

本提交修复了 schema 演进中标识字段校验的一个大小写敏感性 bug：在大小写不敏感模式下应使用 `indexByLowerCaseName` 而非 `indexByName` 构建字段名映射。修复确保了大小写不敏感模式下标识字段的正确校验。测试验证了大小写不敏感模式下的列名冲突检测逻辑。
