# 提交 0629：Hive: Add test to make sure iceberg table with same name as hive table can't be created

## 提交信息

- **序号**：0629 / 4088
- **哈希**：2eabd52a809fa8c56105b42d552170058fad0489
- **短哈希**：2eabd52a8
- **日期**：2024-03-26 20:10:56 +0530
- **作者**：Naveen Kumar <nk1506@gmail.com>
- **提交说明**：Hive: Add test to make sure iceberg table with same name as hive table can't be created (#9980)
- **PR/Issue**：#9980

## 总体目的

本提交是一个**纯测试增强**提交，不修改任何产品代码。其目的是通过新增参数化测试，验证一个重要的安全保证：**当 Hive Metastore 中已存在同名的 Hive 原生表（非 Iceberg 表）时，Iceberg 不应允许在其上创建同名的 Iceberg 表**，以避免表类型混淆和数据丢失风险。

**背景动机**：

1. Hive Metastore 是一个共享的元数据存储，同一数据库下不能有两个同名的表对象。但 HMS 中的"表"可以是不同类型：Iceberg 表（通过 `table_type=iceberg` 参数标识）、Hive 原生外部表（`EXTERNAL_TABLE`）、Hive 管理表（`MANAGED_TABLE`）、Hive 虚拟视图（`VIRTUAL_VIEW`）等。
2. 如果用户尝试用 Iceberg 的 `catalog.createTable()` 创建一个与已有 Hive 原生表同名的表，Iceberg 应当检测到该表已存在但不是 Iceberg 表，并抛出 `NoSuchIcebergTableException`（而非 `AlreadyExistsException`），明确告知"该名称对应的表不是 Iceberg 表"。
3. 此前缺乏针对不同 Hive 表类型（`EXTERNAL_TABLE`、`VIRTUAL_VIEW`、`MANAGED_TABLE`）的全面测试覆盖，本提交补齐这一空白。

## 如何达成设计目的

通过以下方式实现测试目标：

1. **改造辅助方法**：将原有的 `createHiveTable(String hiveTableName)` 辅助方法扩展为 `createHiveTable(String hiveTableName, TableType type)`，支持创建不同类型的 Hive 表（此前硬编码为 `EXTERNAL_TABLE`）。
2. **新增参数化测试**：使用 JUnit 5 的 `@ParameterizedTest` + `@EnumSource`，对 `TableType` 枚举中的 `EXTERNAL_TABLE`、`VIRTUAL_VIEW`、`MANAGED_TABLE` 三种类型分别执行相同的测试逻辑，验证 Iceberg 在所有三种 Hive 表类型冲突场景下的行为一致性。
3. **保持既有测试兼容**：原有 `testListTables` 测试中对 `createHiveTable` 的调用更新为显式传入 `TableType.EXTERNAL_TABLE`，保持原有行为不变。

## 修改详情

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveTableTest.java`

**修改目的**：新增同名表冲突的参数化测试，并改造辅助方法以支持多种 Hive 表类型。

**工作逻辑**：本文件是 Hive 表操作的测试类，继承自 `HiveTableBaseTest`。具体改动分为三部分：

#### 1. 新增 import

新增三个 import：
- `org.apache.iceberg.exceptions.NoSuchIcebergTableException`：用于断言抛出的异常类型。
- `org.junit.jupiter.params.ParameterizedTest`：JUnit 5 参数化测试注解。
- `org.junit.jupiter.params.provider.EnumSource`：以枚举值作为参数源的注解。

#### 2. 改造 `createHiveTable` 辅助方法

原方法签名 `createHiveTable(String hiveTableName)` 改为 `createHiveTable(String hiveTableName, TableType type)`。方法内部构建 HMS `Table` 对象时，原来硬编码 `TableType.EXTERNAL_TABLE.name()` 作为构造器最后一个参数（tableType），现在改为 `type.name()`，使用调用方传入的类型。其余构建逻辑（SerDeInfo、StorageDescriptor、表参数等）不变。

同时更新 `testListTables` 测试中对该辅助方法的调用，从 `createHiveTable(hiveTableName)` 改为 `createHiveTable(hiveTableName, TableType.EXTERNAL_TABLE)`，保持原有测试行为不变（原本就是创建外部表）。

#### 3. 新增参数化测试 `testHiveTableAndIcebergTableWithSameName`

使用 `@ParameterizedTest` 和 `@EnumSource(value = TableType.class, names = {"EXTERNAL_TABLE", "VIRTUAL_VIEW", "MANAGED_TABLE"})` 注解，对三种 Hive 表类型分别执行测试。测试步骤如下：

1. **前置断言**：`catalog.listTables(TABLE_IDENTIFIER.namespace())` 应只包含 1 个表（即基类 `HiveTableBaseTest` 已创建的 `TABLE_IDENTIFIER`），确认初始状态干净。
2. **创建同名 Hive 表**：以 `hiveTableName = "test_hive_table"` 为名，通过 `HIVE_METASTORE_EXTENSION.metastoreClient().createTable(createHiveTable(hiveTableName, tableType))` 在 HMS 中直接创建一个指定类型的 Hive 原生表（绕过 Iceberg catalog，模拟已存在的非 Iceberg 表）。
3. **验证 listTables 行为**：
   - `catalog.setListAllTables(true)` 时，`listTables` 返回 2 个表（Iceberg 表 + Hive 表），因为 `listAllTables=true` 会列出 HMS 中所有表（不区分是否 Iceberg）。
   - `catalog.setListAllTables(false)` 重置为默认值（仅列出 Iceberg 表）。
4. **核心断言 —— 创建同名 Iceberg 表应失败**：
   ```java
   assertThatThrownBy(() -> catalog.createTable(identifier, schema, PartitionSpec.unpartitioned()))
       .isInstanceOf(NoSuchIcebergTableException.class)
       .hasMessageStartingWith(String.format("Not an iceberg table: hive.%s", identifier));
   ```
   验证：当尝试用 `catalog.createTable` 创建与已有 Hive 表同名的 Iceberg 表时，抛出 `NoSuchIcebergTableException`，消息以 `"Not an iceberg table: hive.db.test_hive_table"` 开头。这说明 Iceberg 检测到该名称已存在但不是 Iceberg 表，拒绝创建。
5. **验证 tableExists 行为**：
   - `catalog.tableExists(identifier)` 返回 `false`：因为 `tableExists` 只认 Iceberg 表，同名 Hive 表不算。
   - `catalog.tableExists(TABLE_IDENTIFIER)` 返回 `true`：原有的 Iceberg 表仍然存在。
6. **清理**：`metastoreClient.dropTable(DB_NAME, hiveTableName)` 删除测试创建的 Hive 表。

**关键测试意图**：该测试确保无论 HMS 中已存在的是哪种类型的 Hive 表（外部表、视图、管理表），Iceberg 都不会错误地在其上创建 Iceberg 表，而是抛出明确的 `NoSuchIcebergTableException`。这防止了表类型混淆——如果允许创建，会导致 HMS 中原有的 Hive 表定义被 Iceberg 表覆盖，造成数据丢失或元数据损坏。

## 小结

**成效**：本提交以 43 行新增、4 行删除的纯测试改动，补齐了 Iceberg 与 Hive 同名表冲突场景的测试覆盖，覆盖了三种 Hive 表类型（`EXTERNAL_TABLE`、`VIRTUAL_VIEW`、`MANAGED_TABLE`）。测试验证了 Iceberg 的安全防护机制：拒绝在非 Iceberg 表上创建同名 Iceberg 表。

**影响范围**：仅影响测试代码，不改变任何产品行为。被测的产品逻辑（`HiveCatalog.createTable` 中检测非 Iceberg 表并抛出 `NoSuchIcebergTableException`）已存在于 1.4.x，本提交只是为其添加回归测试。

**回迁到 1.4.x 的注意事项**：
- 这是纯测试提交，回迁风险极低，建议优先回迁以增强 1.4.x 的测试覆盖。
- 需确认 1.4.x 的 `HiveCatalog.createTable` 在遇到同名非 Iceberg 表时确实抛出 `NoSuchIcebergTableException`（而非 `AlreadyExistsException`），否则测试会失败，说明 1.4.x 可能缺少相关产品逻辑。
- 需确认 1.4.x 的 `HiveTableTest` 使用 JUnit 5（`org.junit.jupiter.api.Test`），且依赖中包含 `junit-jupiter-params`（用于 `@ParameterizedTest` 和 `@EnumSource`）。若 1.4.x 仍使用 JUnit 4，需调整注解。
- `createHiveTable` 辅助方法的签名变更需同步更新所有调用点（本提交已处理 `testListTables`，需检查 1.4.x 上是否有其他调用点）。
