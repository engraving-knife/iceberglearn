# 提交 2827：Core: Check table UUID in RESTTableOperations (#14363)

## 提交信息

- **序号**：2827 / 4088
- **哈希**：f17f34bce65567fd1479607038221bcccb594b29
- **短哈希**：f17f34bce
- **日期**：2025-11-03 17:45:05 -0800
- **作者**：Yuya Ebihara
- **提交说明**：Core: Check table UUID in RESTTableOperations (#14363)
- **PR/Issue**：#14363

## 总体目的

本提交修复了一个 REST catalog 中的数据一致性问题。当通过 REST catalog 加载表并刷新元数据时，`RESTTableOperations` 会在元数据文件位置变化时更新当前的 `TableMetadata`。但原有的逻辑没有验证刷新后的表元数据中的 UUID 是否与之前的 UUID 一致。

这可能导致一个问题场景：如果一张表被删除后又用相同的表名重新创建（drop and recreate），新表会有一个不同的 UUID。如果客户端仍然持有旧表的引用并尝试提交（commit）操作，REST 服务端可能会返回新表的元数据。如果不检查 UUID，客户端可能会将提交应用到错误的新表上，导致数据混淆或损坏。

通过在刷新元数据时检查 UUID 一致性，可以及时发现表已被替换的情况并抛出 `IllegalStateException`，防止在错误的表上执行提交操作。

## 如何达成设计目的

设计思路是在 `RESTTableOperations` 更新 `TableMetadata` 的位置新增 UUID 校验：

1. 新增私有静态方法 `checkUUID(TableMetadata currentMetadata, TableMetadata newMetadata)`：当新旧元数据的 UUID 都非空且当前元数据非空时，断言两者的 UUID 相等，否则抛出 `IllegalStateException`，消息包含当前 UUID 和刷新后的 UUID 以便诊断。
2. 在原有的元数据更新点（当 `current == null` 或元数据文件位置变化时），将 `this.current = response.tableMetadata()` 改为 `this.current = checkUUID(current, response.tableMetadata())`，在赋值前执行校验。

UUID 校验是防御性的：仅在双方 UUID 都存在时才检查（避免首次加载表时 current 为 null 的情况，以及 UUID 尚未设置的边缘情况）。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableOperations.java` (+15/-1 lines)

**修改目的**：在元数据刷新时校验表 UUID 一致性。

**工作逻辑**：
- 原代码：`this.current = response.tableMetadata();` 直接赋值新元数据。
- 新代码：`this.current = checkUUID(current, response.tableMetadata());` 先校验再赋值。
- 新增 `checkUUID` 方法：接收当前和新的 `TableMetadata`，当 `currentMetadata != null` 且两者的 `uuid()` 均非空时，使用 `Preconditions.checkState` 断言 `newUUID.equals(currentMetadata.uuid())`。校验失败时抛出 `IllegalStateException`，消息格式为 "Table UUID does not match: current=%s != refreshed=%s"，便于排查是哪两张表的 UUID 不一致。校验通过则返回新元数据。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+43/-0 lines)

**修改目的**：验证 UUID 不匹配时抛出异常的测试。

**工作逻辑**：新增 `testDifferentTableUUID` 测试方法：
1. 创建表后，通过 Mockito 拦截 REST adapter 的 `LoadTableResponse`，将返回的表元数据替换为带有新 UUID（`386b9f01-...`）的元数据，模拟"表被删除并用同名重建"的场景。
2. 断言原始 UUID 与新 UUID 不同。
3. 尝试通过 `catalog.loadTable(TABLE).newFastAppend().appendFile(file).commit()` 进行提交操作。
4. 断言抛出 `IllegalStateException`，且消息匹配 "Table UUID does not match: current=.* != refreshed=386b9f01-..."，验证 UUID 校验逻辑生效。
5. 新增导入 `org.apache.iceberg.TableMetadata`（用于构建替换的元数据）。

## 总结

本提交在 `RESTTableOperations` 的元数据刷新流程中新增了表 UUID 一致性校验，防止在表被删除并同名重建后，客户端仍向新表提交旧表的操作，避免数据混淆。这是一个重要的数据完整性保护，尤其适用于表名复用的场景。测试通过模拟 UUID 变更验证了校验逻辑的正确性。
