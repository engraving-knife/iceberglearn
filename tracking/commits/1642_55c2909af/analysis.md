# 提交 1642：Core: Add metadataFileLocation in TableUtil (#12082)

## 提交信息

- **序号**：1642 / 4088
- **哈希**：55c2909aff625407e6fec2ecf9c639b97d8945f9
- **短哈希**：55c2909af
- **日期**：2025-01-27（Mon Jan 27 00:18:41 2025 -0800）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Core: Add metadataFileLocation in TableUtil (#12082)
- **PR/Issue**：#12082

## 总体目的

获取 Iceberg 表的元数据文件位置（metadata file location）是一个常见需求——`register_table` 过程需要用它把已有表注册到另一个 catalog、`rewrite_table_path` 过程需要它定位元数据文件做路径重写、序列化表后需要从元数据文件恢复表对象等。

但此前没有统一的获取入口。调用方需要自行做类型判断与强转：

```java
String metadataJson = (((HasTableOperations) table).operations()).current().metadataFileLocation();
```

这种模式有几个问题：
1. **重复散落**：同样的强转逻辑在多个测试与生产代码中重复出现；
2. **覆盖不全**：仅处理了 `HasTableOperations` 的表，对 `SerializableTable`（序列化后的表副本，其 `operations()` 抛 `UnsupportedOperationException`）和 `BaseMetadataTable`（元数据表，如 `files`/`snapshots` 等虚拟表）不适用；
3. **`SerializableTable` 的 metadataFileLocation 无法外部获取**：`SerializableTable` 内部已持有 `metadataFileLocation` 字段（在 copy 时从原表提取），但该字段为 `private` 且无公开 getter，外部只能通过反射或重新走 `HasTableOperations` 路径（后者对 `SerializableTable` 会抛异常）获取。

本提交在 `TableUtil` 中新增静态方法 `metadataFileLocation(Table table)`，按表的实际类型分派获取元数据文件位置；同时在 `SerializableTable` 上暴露公开的 `metadataFileLocation()` getter。最后把各处测试中散落的强转调用替换为新方法。

## 如何达成设计目的

通过三层改动：

1. **`SerializableTable` 暴露 getter**：新增 `public String metadataFileLocation()`，返回内部字段值，字段为 null 时抛 `UnsupportedOperationException`（说明该序列化表未持有元数据位置）。
2. **`TableUtil.metadataFileLocation(Table)` 统一入口**：按类型分派——`SerializableTable` 走 getter、`HasTableOperations` 走 `operations().current().metadataFileLocation()`、`BaseMetadataTable` 走基础表的 operations、其余抛异常。
3. **调用方替换**：把 Spark 3.3/3.4/3.5 测试中 `((HasTableOperations) table).operations().current().metadataFileLocation()` 替换为 `TableUtil.metadataFileLocation(table)`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SerializableTable.java`（修改，+8 行）

**修改目的**：暴露序列化表的元数据文件位置 getter。

**工作逻辑**：

```java
public String metadataFileLocation() {
  if (metadataFileLocation == null) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not have a metadata file location");
  }
  return metadataFileLocation;
}
```

`metadataFileLocation` 字段在 `SerializableTable.copyOf(table)` 构造时通过私有方法 `metadataFileLocation(Table table)` 设置——若原表是 `HasTableOperations` 则取 `ops.current().metadataFileLocation()`，否则为 `null`。`SerializableMetadataTable`（元数据表的序列化副本）的该字段也可能为 `null`（取决于基础表是否实现了 `HasTableOperations`），此时调用 getter 抛 `UnsupportedOperationException`。

### `core/src/main/java/org/apache/iceberg/TableUtil.java`（修改，+19 行）

**修改目的**：提供统一的获取表元数据文件位置的入口。

**工作逻辑**：

```java
public static String metadataFileLocation(Table table) {
  Preconditions.checkArgument(null != table, "Invalid table: null");

  if (table instanceof SerializableTable) {
    SerializableTable serializableTable = (SerializableTable) table;
    return serializableTable.metadataFileLocation();
  } else if (table instanceof HasTableOperations) {
    HasTableOperations ops = (HasTableOperations) table;
    return ops.operations().current().metadataFileLocation();
  } else if (table instanceof BaseMetadataTable) {
    return ((BaseMetadataTable) table).table().operations().current().metadataFileLocation();
  } else {
    throw new IllegalArgumentException(
        String.format(
            "%s does not have a metadata file location", table.getClass().getSimpleName()));
  }
}
```

分派逻辑按优先级：
1. **`SerializableTable`**：直接走 getter（避免触发 `operations()` 的 `UnsupportedOperationException`）；
2. **`HasTableOperations`**：通过 operations 获取当前元数据的 `metadataFileLocation`（最常见的路径，适用于 `BaseTable`、`RESTTable`、`JdbcTable` 等）；
3. **`BaseMetadataTable`**：元数据表（如 `MetadataTable` 的各种子类：`files`、`snapshots`、`history` 等）本身没有独立元数据，取其**基础表**（`table()`）的元数据位置；
4. **其他**：抛 `IllegalArgumentException`。

注意 `SerializableTable` 的判断必须在 `HasTableOperations` 之前——因为 `SerializableTable` 实现了 `HasTableOperations` 但其 `operations()` 会抛异常（除非已 lazy-load）。`BaseMetadataTable` 也可能实现 `HasTableOperations`，但其 `operations()` 返回的是元数据表自身的 operations（基于基础表元数据构建的 `StaticTableOperations`），取 `current().metadataFileLocation()` 可能与基础表的实际元数据位置不同，因此单独走基础表路径。

### `core/src/test/java/org/apache/iceberg/TestTableUtil.java`（修改，+40/-15 行）

**修改目的**：覆盖 `metadataFileLocation` 的测试，并重构既有测试。

**工作逻辑**：
- 提取 `SCHEMA` 为常量，减少重复；
- `testInvalidTable` 新增 null 校验断言；
- `formatVersionForMetadataTables` 从 `for` 循环重构为 `@ParameterizedTest` + `@EnumSource(MetadataTableType.class)`，每个元数据表类型独立报告；
- 新增 `metadataFileLocationForBaseTable`：验证普通表与 `SerializableTable.copyOf(table)` 的 `metadataFileLocation` 与 `TableMetadata` 一致；
- 新增 `metadataFileLocationForMetadataTables`（参数化）：验证所有元数据表类型及其序列化副本的 `metadataFileLocation` 都返回基础表的元数据位置。

### `core/src/test/java/org/apache/iceberg/hadoop/TestTableSerialization.java`（修改，+12/-8 行）

**修改目的**：验证序列化表的 `metadataFileLocation` 一致性。

**工作逻辑**：
- `testSerializableTable` 新增断言：`TableUtil.metadataFileLocation(serializableTable)` 等于 `TableUtil.metadataFileLocation(table)`；
- `testSerializableMetadataTable` 从 `for` 循环重构为参数化测试，新增同样的 `metadataFileLocation` 一致性断言。

### Spark 测试文件（3.3/3.4/3.5 `TestRegisterTableProcedure.java`、3.5 `TestRewriteTablePathProcedure.java`，各 -2/+1 行）

**修改目的**：用 `TableUtil.metadataFileLocation(table)` 替换 `((HasTableOperations) table).operations().current().metadataFileLocation()`。

**工作逻辑**：纯调用方替换，行为不变。`TestRegisterTableProcedure` 在 3 个 Spark 版本中各替换 1 处；`TestRewriteTablePathProcedure` 在 3.5 中替换 3 处。

## 小结

- **成效**：提供了统一的 `TableUtil.metadataFileLocation(Table)` 入口，覆盖 `SerializableTable`、`HasTableOperations`、`BaseMetadataTable` 三种表类型，消除了散落在测试与生产代码中的重复强转逻辑。`SerializableTable` 暴露了 getter 使序列化表的元数据位置可直接获取。
- **影响范围**：`core` 模块（`TableUtil`、`SerializableTable`、测试）与 Spark 3.3/3.4/3.5 测试。`TableUtil` 与 `SerializableTable` 都在 `core` 模块（非 `api`），因此此方法仅供 core 内部及依赖 core 的模块使用，不是公共 API 的一部分。
- **回迁到 1.4.x 的注意事项**：可安全 cherry-pick。需确认 1.4.x 的 `SerializableTable` 有 `metadataFileLocation` 私有字段（1.4.x 中已具备，在 `copyOf` 时设置）；`TableUtil` 类在 1.4.x 中已存在（含 `formatVersion` 方法）。`BaseMetadataTable` 的 `table()` 方法需存在（1.4.x 中已具备）。测试重构（`@ParameterizedTest` + `@EnumSource`）需确认 1.4.x 测试依赖的 JUnit 5 版本支持 `@EnumSource`。Spark 测试替换无风险，纯调用方重构。
