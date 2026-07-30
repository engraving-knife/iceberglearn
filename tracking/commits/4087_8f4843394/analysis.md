# 提交 4087：Hive: Use server-side filter to list Iceberg tables in HiveCatalog

## 提交信息

- **序号**：4087 / 4088
- **哈希**：8f4843394cbfca3583108b8f6174f86f8580071b
- **短哈希**：8f4843394
- **日期**：2026-07-24 11:30:51 +0200
- **作者**：terrytlu
- **提交说明**：Hive: Use server-side filter to list Iceberg tables in HiveCatalog (#17317)
- **PR/Issue**：#17317

## 总体目的

`HiveCatalog.listTables(Namespace)` 在不列出所有表（`listAllTables=false`，即只返回 Iceberg 表）的场景下，原来的实现是：先调用 `client.getAllTables(database)` 从 Hive Metastore（HMS）拉取该数据库下**全部**表名，再在客户端遍历每个表读取其元数据，根据 `table_type` 参数是否等于 `"iceberg"` 来过滤出 Iceberg 表。

这个方案有两个性能问题：
1. **拉取了不必要的表名**：数据库中可能存在大量非 Iceberg 表（Hive 内部表、外部表、视图等），全部拉到客户端浪费网络带宽和内存；
2. **客户端逐个加载表元数据**：`listIcebergTables` 对每个表名都要发一次 `getTable` 请求获取表对象来判断类型，N 个表就是 N 次 RPC，在表数量多时延迟显著放大。

HMS 实际上支持基于表参数的**服务端过滤**（`listTableNamesByFilter`），可以直接在 HMS 侧按 `parameters.table_type` 过滤并只返回匹配的表名。本提交改用服务端过滤，把"先拉全部再客户端过滤"优化为"在 HMS 侧按 `table_type like 'ICEBERG'` 过滤后只返回 Iceberg 表名"，大幅减少网络往返和数据传输量。

## 如何达成设计目的

新增私有方法 `listIcebergTablesByFilter(Namespace, String tableTypeValue)`，构造一个 HMS 过滤表达式：

```
HIVE_FILTER_FIELD_PARAMS + TABLE_TYPE_PROP + " like \"ICEBERG\""
```

即 `parameters.table_type like "ICEBERG"`，调用 `client.listTableNamesByFilter(database, filter, (short)-1)`（-1 表示无数量上限）直接从 HMS 拿到匹配的表名列表，再映射为 `TableIdentifier`。

`listTables` 方法在 `listAllTables=false` 分支改为调用这个新方法，不再先 `getAllTables` 再客户端过滤。`listAllTables=true` 分支保持原行为（返回所有表，不过滤类型）。

过滤表达式设计上有几个关键考量（在代码注释中详细说明）：
1. **大小写**：HMS 在持久化 `table_type` 时会规范化为大写（如 `"iceberg"` 存为 `"ICEBERG"`），所以过滤值必须 `toUpperCase(Locale.ROOT)`；
2. **用 `like` 而非 `=`**：部分 HMS 后端（Derby、Oracle，其中 `PARAM_VALUE` 是 CLOB 类型）不支持对属性值做 `=` 比较，会报错（HIVE-21614）；新版 HMS 内部会把 `=` 改写为 `like`。由于过滤值不含 `%`/`_` 通配符，`like` 在此处语义等价于 `=`，故直接用 `like` 兼容所有后端。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (+37/-3 lines)

**修改目的**：用服务端过滤替代客户端过滤，提升 `listTables` 性能。

**工作逻辑**：

1. **导入新增**：`java.util.Locale`、`org.apache.hadoop.hive.metastore.api.hive_metastoreConstants`。

2. **`listTables` 方法重构**：
   - 修改前：先 `List<String> tableNames = clients.run(client -> client.getAllTables(database));`，然后两个分支都用这同一份 tableNames（`listAllTables` 直接映射，非 listAll 调 `listIcebergTables(tableNames, ...)` 客户端过滤）；
   - 修改后：`getAllTables` 调用移入 `listAllTables=true` 分支（只有该分支需要全部表名）；`listAllTables=false` 分支改为调用新方法 `listIcebergTablesByFilter(namespace, ICEBERG_TABLE_TYPE_VALUE)`，直接从 HMS 拿过滤后的 Iceberg 表名。

3. **新增 `listIcebergTablesByFilter` 方法**：
```java
private List<TableIdentifier> listIcebergTablesByFilter(
    Namespace namespace, String tableTypeValue) throws TException, InterruptedException {
  String database = namespace.level(0);
  String filter =
      hive_metastoreConstants.HIVE_FILTER_FIELD_PARAMS
          + BaseMetastoreTableOperations.TABLE_TYPE_PROP
          + " like \""
          + tableTypeValue.toUpperCase(Locale.ROOT)
          + "\"";

  List<String> icebergTableNames =
      clients.run(
          client -> client.listTableNamesByFilter(database, filter, (short) -1));

  return icebergTableNames.stream()
      .map(tableName -> TableIdentifier.of(namespace, tableName))
      .collect(Collectors.toList());
}
```
   - `HIVE_FILTER_FIELD_PARAMS` 是 HMS 过滤表达式的前缀（`parameters.`）；
   - `TABLE_TYPE_PROP` 是 `"table_type"`；
   - `tableTypeValue.toUpperCase(Locale.ROOT)` 把 `"iceberg"` 转为 `"ICEBERG"` 匹配 HMS 存储；
   - `(short) -1` 表示不限制返回数量；
   - 注释详细解释了大小写和 `like` 选择的理由。

原有的 `listIcebergTables(tableNames, namespace, tableTypeValue)` 客户端过滤方法保留未删除（可能在其他地方仍有使用或作为回退）。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java` (+71/-0 lines)

**修改目的**：验证服务端过滤正确排除非 Iceberg 表并返回全部 Iceberg 表。

**工作逻辑**：

新增两个测试和一个辅助方法：

1. **`testListTablesFiltersOutNonIcebergTables`**：
   - 在数据库中创建一个 Iceberg 表 `iceberg_tbl`（通过 `catalog.createTable`）；
   - 通过 HMS 客户端直接创建一个非 Iceberg 的 Hive 外部表 `hive_tbl`（用 `createNonIcebergTable` 辅助方法，`TableType.EXTERNAL_TABLE`，无 `table_type=iceberg` 参数）；
   - 断言 `catalog.listTables(ns)` 只包含 `iceberg_tbl`，验证服务端过滤正确排除了非 Iceberg 表；
   - finally 块清理两个表。

2. **`testListTablesByFilterReturnsAllIcebergTables`**：
   - 创建两个 Iceberg 表 `t1`、`t2`；
   - 断言 `catalog.listTables(ns)` 包含两者（`containsExactlyInAnyOrder`），验证过滤不会漏掉任何 Iceberg 表；
   - finally 块清理。

3. **`createNonIcebergTable(String, TableType)` 辅助方法**：直接构造一个 Hive `Table` 对象（含 `StorageDescriptor`、`SerDeInfo`，指定 `TableType`），用于在 HMS 中创建原生 Hive 表（非 Iceberg），不经过 Iceberg catalog。

新增导入 `TableType`、`SerDeInfo`、`StorageDescriptor`、`Lists`。

## 总结

一个针对 `HiveCatalog.listTables` 的性能优化提交：把"先拉全部表名再客户端逐个加载元数据过滤"改为"通过 HMS `listTableNamesByFilter` 服务端按 `parameters.table_type like 'ICEBERG'` 过滤"。这避免了拉取非 Iceberg 表名和逐表的 `getTable` RPC，在表数量多时显著降低延迟和网络开销。过滤表达式的设计考虑了 HMS 大小写规范化和部分后端（Derby/Oracle CLOB）不支持 `=` 的兼容性问题，使用 `like` 兼容所有后端。配套测试通过创建混合的 Iceberg 和非 Iceberg 表，验证了过滤的准确性和完整性。
