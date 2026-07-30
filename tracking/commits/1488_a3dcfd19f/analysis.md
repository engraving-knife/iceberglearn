# 提交 1488：Hive: Optimize tableExists API in hive catalog (#11597)

## 提交信息

- **序号**：1488 / 4088
- **哈希**：a3dcfd19fd1b2a709f7bdf013b83836953d49c6f
- **短哈希**：a3dcfd19f
- **日期**：2024-12-12（Thu Dec 12 11:01:44 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Hive: Optimize tableExists API in hive catalog (#11597)
- **PR/Issue**：#11597

## 总体目的

Iceberg 的 `Catalog` 接口为 `tableExists` 提供了默认实现：`try { loadTable(identifier); return true; } catch (NoSuchTableException) { return false; }`。该实现为了判断"表是否存在"会完整加载表——对 `HiveCatalog` 而言，`loadTable` 会：

1. 调用 `newTableOps(identifier)` 创建 `HiveTableOperations`，后者涉及 HiveMetaStore client 获取、Table 存储位置解析等额外上下文构建；
2. 调用 `ops.current()` 触发 metadata 文件读取与解析（如果表存在）；
3. 构造完整的 `BaseTable` 对象（含 schema、分区规范、snapshot 等全部元数据）。

但"判断表是否存在"本质只需要向 HiveMetaStore 发一次 `getTable(database, tableName)` RPC 并确认返回的 `Table` 是 Iceberg 类型即可，无需读取 metadata 文件、无需构造 `HiveTableOperations`、无需解析完整 `TableMetadata`。这在频繁调用 `tableExists` 的场景（如引擎元数据刷新、CREATE TABLE IF NOT EXISTS 前的预检、写入前的存在性校验、metadata table 解析等）下造成不必要的 HMS 与文件系统开销。

本提交在 `HiveCatalog` 中覆盖 `tableExists`，直接调一次 `client.getTable` 并校验 Iceberg 类型，跳过 `HiveTableOperations` 创建与 metadata 加载。

## 如何达成设计目的

新增 `HiveCatalog.tableExists(TableIdentifier)` 覆盖方法，核心逻辑：

1. **标识符分类**：
   - 若 `isValidIdentifier(identifier)` 为 true（即 `db.table` 形式，namespace 恰好 1 层）→ 直接以该标识符检查。
   - 否则若 `isValidMetadataIdentifier(identifier)` 为 true（即 `db.table.partitions` 这类 metadata table 标识符，最后一段是 `MetadataTableType` 且 `db.table` 是合法 base 标识符）→ 取 base 标识符 `TableIdentifier.of(identifier.namespace().levels())` 检查其存在性。metadata table 的存在性等价于 base 表存在性。
   - 否则（既不是普通标识符也不是 metadata 标识符，如 `db.invalid.table` 中 "table" 不是 metadata 类型）→ 直接返回 `false`。
2. **轻量 HMS 调用**：`clients.run(client -> client.getTable(database, tableName))`，单次 RPC。
3. **Iceberg 校验**：`HiveOperationsBase.validateTableIsIceberg(table, fullTableName(...))` 检查 `table_type` 参数是否为 `ICEBERG`。若不是（如同名 Hive 原生表、view），抛 `NoSuchIcebergTableException`（继承自 `NoSuchTableException`）→ 被 catch 后返回 `false`。
4. **异常分类**：
   - `NoSuchTableException` / `NoSuchObjectException`（Hive 表不存在）→ 返回 `false`；
   - `TException`（其它 HMS 通信异常）→ 包成 `RuntimeException` 抛出；
   - `InterruptedException` → 恢复中断标志后包成 `RuntimeException` 抛出（这是 Hive client 调用的标准异常处理范式）。

为支持上述逻辑，需要把 `BaseMetastoreCatalog.isValidMetadataIdentifier` 从 `private` 改为 `protected`，使 `HiveCatalog` 能调用它判断 metadata table 标识符。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetastoreCatalog.java`

**修改目的**：暴露 `isValidMetadataIdentifier` 给子类使用。

**工作逻辑**：把方法可见性从 `private` 改为 `protected`：

```java
protected boolean isValidMetadataIdentifier(TableIdentifier identifier) {
  return MetadataTableType.from(identifier.name()) != null
      && isValidIdentifier(TableIdentifier.of(identifier.namespace().levels()));
}
```

方法逻辑不变：判断标识符最后一段是否是已知 `MetadataTableType`（如 `partitions`、`snapshots`、`history` 等），且其 namespace 部分构成的 base 标识符是否合法。该方法被 `loadTable` 内部用于识别 metadata table 请求，现在也让 `tableExists` 能复用同一判断。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：覆盖 `tableExists`，用轻量 HMS 调用替代完整 `loadTable`。

**工作逻辑**：新增方法（带 Javadoc 说明行为）：

```java
@Override
public boolean tableExists(TableIdentifier identifier) {
  TableIdentifier baseTableIdentifier = identifier;
  if (!isValidIdentifier(identifier)) {
    if (!isValidMetadataIdentifier(identifier)) {
      return false;
    } else {
      baseTableIdentifier = TableIdentifier.of(identifier.namespace().levels());
    }
  }

  String database = baseTableIdentifier.namespace().level(0);
  String tableName = baseTableIdentifier.name();
  try {
    Table table = clients.run(client -> client.getTable(database, tableName));
    HiveOperationsBase.validateTableIsIceberg(table, fullTableName(name, baseTableIdentifier));
    return true;
  } catch (NoSuchTableException | NoSuchObjectException e) {
    return false;
  } catch (TException e) {
    throw new RuntimeException("Failed to check table existence of " + baseTableIdentifier, e);
  } catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new RuntimeException(
        "Interrupted in call to check table existence of " + baseTableIdentifier, e);
  }
}
```

**Javadoc 关键说明**："If a hive table with the same identifier exists in catalog, this method will return `false`."——即同名 Hive 原生表（非 Iceberg）存在时返回 `false`，因为 `validateTableIsIceberg` 会抛 `NoSuchIcebergTableException`（继承自 `NoSuchTableException`）。

**关键行为**：
- **metadata table**：`db.tbl.partitions` → 检查 `db.tbl` 是否存在。若 base 表是 Iceberg 表则返回 `true`（意味着 partitions metadata table 可访问），否则 `false`。
- **同名 Hive 原生表**：HMS `getTable` 成功返回，但 `validateTableIsIceberg` 失败 → 返回 `false`。与默认实现的最终效果一致（默认实现 `loadTable` 也会在校验 Iceberg 类型时失败抛 `NoSuchTableException`），但省去了 `HiveTableOperations` 与 metadata 加载开销。
- **同名 view**：HMS `getTable` 返回 view 的 Table 对象（`tableType=VIRTUAL_VIEW`），`validateTableIsIceberg` 检查 `table_type` 参数不为 ICEBERG → 抛异常 → 返回 `false`。
- **表不存在**：HMS `getTable` 抛 `NoSuchObjectException` → 返回 `false`。
- **中断处理**：`InterruptedException` 时调用 `Thread.currentThread().interrupt()` 恢复中断状态再抛 `RuntimeException`，遵循 Java 并发最佳实践。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/HiveTableTest.java`

**修改目的**：为优化后的 `tableExists` 补充全面回归测试。

**工作逻辑**：新增 `testTableExists` 方法，覆盖以下场景：

1. **非法标识符**（`db.invalid.test_table_exists`，"test_table_exists" 不是 metadata 类型）→ 返回 `false`。
2. **表不存在时**（identifier 尚未创建）→ 返回 `false`。
3. **表创建后**（`catalog.buildTable(identifier, SCHEMA).create()`）：
   - `tableExists(identifier)` → `true`；
   - `tableExists(metadataIdentifier)`（`db.test_table_exists.partitions`）→ `true`（metadata table 存在性跟随 base 表）。
4. **表删除后**：
   - `tableExists(identifier)` → `false`；
   - `tableExists(metadataIdentifier)` → `false`。
5. **同名 Hive 原生表**（通过 `HIVE_METASTORE_EXTENSION.metastoreClient().createTable(createHiveTable(testTableName, TableType.EXTERNAL_TABLE))` 直接在 HMS 建一个非 Iceberg 的 EXTERNAL_TABLE）：
   - `tableExists(identifier)` → `false`（验证 Javadoc 所述行为）；
   - `tableExists(metadataIdentifier)` → `false`。
   - 测试后清理该 Hive 表。
6. **同名 view**（通过 `catalog.buildView(identifier)...create()` 创建 Iceberg view）：
   - `tableExists(identifier)` → `false`（identifier 指向 view 而非 table）。
   - 测试后 `catalog.dropView(identifier)` 清理。

测试覆盖了优化前后所有边界行为，确保性能优化不改变任何对外语义。

## 小结

- **成效**：`HiveCatalog.tableExists` 现在仅发一次 HMS `getTable` RPC 并校验 Iceberg 类型，跳过 `HiveTableOperations` 创建与 `TableMetadata` 加载，显著降低存在性检查开销，对高频调用路径（引擎元数据刷新、写入预检、metadata table 解析等）有明显性能收益。同时正确处理 metadata table 标识符、同名 Hive 原生表、同名 view 等边界场景，行为与原默认实现一致。
- **影响范围**：3 个文件、87 行新增 / 1 行修改。仅影响 `HiveCatalog` 的 `tableExists` 路径与 `BaseMetastoreCatalog` 中一个方法的可见性（`private` → `protected`，无行为变化）。不影响其它 catalog 实现（JdbcCatalog、RESTCatalog 等各自独立实现 `tableExists`）。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯性能优化，**对外行为与原实现等价**，**建议回迁**，对 1.4.x 用户在 Hive catalog 下的存在性检查性能有直接收益。
  - cherry-pick 风险较低：
    - `BaseMetastoreCatalog.isValidMetadataIdentifier` 可见性变更（`private` → `protected`）是 ABI 友好的放宽，不影响任何调用方。
    - `HiveCatalog.tableExists` 是新增覆盖方法，不与 1.4.x 既有方法冲突。
    - 依赖 `HiveOperationsBase.validateTableIsIceberg`、`clients.run`、`fullTableName` 等，这些在 1.4.x 中应已存在（历史较久）。
  - 测试用到的 `createHiveTable` helper、`HIVE_METASTORE_EXTENSION`、`SCHEMA`、`DB_NAME` 等在 1.4.x 的 `HiveTableBaseTest` 中应已具备。
  - **行为差异注意**：原默认实现 `loadTable` 在遇到同名 Hive 原生表时，会走 `newTableOps` → `current()` → `validateTableIsIceberg` 抛 `NoSuchIcebergTableException`（继承 `NoSuchTableException`）→ `tableExists` 返回 `false`。新实现直接 `getTable` + `validateTableIsIceberg` 也返回 `false`，结果一致但路径更短。若 1.4.x 的 `loadTable` / `newTableOps` 有自定义逻辑（如权限校验、审计日志），新实现可能绕过这些逻辑——需评估 1.4.x 是否有此类自定义。
  - 若 1.4.x 中 `HiveCatalog` 已有自己的 `tableExists` 覆盖（不太可能，但需确认），cherry-pick 会冲突，需手工合并。
