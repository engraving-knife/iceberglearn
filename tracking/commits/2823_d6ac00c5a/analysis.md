# 提交 2823：Core: Prevent dropping namespace when it contains views (#14456)

## 提交信息

- **序号**：2823 / 4088
- **哈希**：d6ac00c5a8dcea7cc3251ff6323edbd130843b91
- **短哈希**：d6ac00c5a
- **日期**：2025-11-03 09:10:32 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Prevent dropping namespace when it contains views (#14456)
- **PR/Issue**：#14456

## 总体目的

本提交修复了一个数据完整性相关的 bug：当命名空间（namespace）中只包含视图（view）而不包含表时，某些 catalog 实现允许直接删除该命名空间，这会导致命名空间内的视图变成"孤儿"对象（指向不存在的命名空间），破坏数据一致性。

根据 Iceberg 的语义，`dropNamespace` 操作应当在命名空间非空时（无论包含的是表还是视图）抛出 `NamespaceNotEmptyException`。原有的实现中，`InMemoryCatalog` 和 `JdbcCatalog` 的 `dropNamespace` 方法只检查了命名空间中是否存在表，但遗漏了对视图的检查。这意味着如果一个命名空间中只有视图没有表，该命名空间可以被错误地删除。

本提交在两个 catalog 实现中补充了对视图的检查，确保命名空间包含视图时也无法被删除。

## 如何达成设计目的

设计思路是在 `dropNamespace` 方法中，于检查表之后、实际删除之前，新增对视图列表的检查：

1. 调用 `listViews(namespace)` 获取命名空间下的所有视图标识符
2. 若视图列表非空，抛出 `NamespaceNotEmptyException`，提示包含的视图数量

同时，在 `ViewCatalogTests` 抽象测试基类中新增了通用测试用例 `dropNonEmptyNamespace`，验证所有实现 `ViewCatalog` 的 catalog 都能正确阻止删除包含视图的命名空间。

## 修改详情

### `core/src/main/java/org/apache/iceberg/inmemory/InMemoryCatalog.java` (+6/-0 lines)

**修改目的**：InMemoryCatalog 的 `dropNamespace` 方法新增视图检查。

**工作逻辑**：在原有检查表的逻辑之后（`tableIdentifiers` 为空才会继续），新增 `listViews(namespace)` 调用获取视图列表，若非空则抛出 `NamespaceNotEmptyException`，消息格式与表检查一致："Namespace %s is not empty. Contains %d view(s)."。

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` (+10/-2 lines)

**修改目的**：JdbcCatalog 的 `dropNamespace` 方法新增视图检查，并改进错误消息格式。

**工作逻辑**：
- 将原有表的错误消息从 "is not empty. %s tables exist." 改为 "is not empty. Contains %d table(s)."（使用 `%d` 替代 `%s`，与 InMemoryCatalog 格式统一）。
- 新增视图检查：仅当 `schemaVersion != JdbcUtil.SchemaVersion.V0` 时才检查视图。这是因为 V0 schema 版本不支持视图（视图表是后续 schema 版本才引入的），对 V0 schema 检查视图无意义且可能出错。若视图列表非空则抛出 `NamespaceNotEmptyException`。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcCatalog.java` (+3/-3 lines)

**修改目的**：更新测试中期望的异常消息文本以匹配新格式。

**工作逻辑**：将三处断言中的期望消息从 "is not empty. N tables exist." 更新为 "is not empty. Contains N table(s)."，与 JdbcCatalog 修改后的消息格式一致。

### `core/src/test/java/org/apache/iceberg/view/ViewCatalogTests.java` (+52/-0 lines)

**修改目的**：新增通用的命名空间非空删除测试。

**工作逻辑**：新增 `dropNonEmptyNamespace` 测试方法，验证场景：
1. 在命名空间下创建表和视图后，删除命名空间应失败（因表和视图均存在）
2. 删除表后，删除命名空间仍应失败（因视图仍存在）
3. 删除视图后，删除命名空间应成功

这确保所有 `ViewCatalog` 实现都正确阻止删除包含视图的命名空间。该测试方法放在 `ViewCatalogTests` 抽象基类中，会被所有 view catalog 测试子类继承执行。

## 总结

本提交修复了 `InMemoryCatalog` 和 `JdbcCatalog` 在 `dropNamespace` 时未检查视图的 bug，防止命名空间在包含视图时被误删导致视图成为孤儿对象。修改包括在两个 catalog 中新增视图检查逻辑、统一错误消息格式（JdbcCatalog 还考虑了 schema 版本兼容性），并在 `ViewCatalogTests` 基类中新增通用测试用例确保所有 view catalog 实现的正确性。这是一个重要的数据完整性修复。
