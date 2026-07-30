# 提交 1496：Core: Add TableUtil to provide access to a table's format version (#11620)

## 提交信息

- **序号**：1496 / 4088
- **哈希**：f40ec2096bc078b9fd2b59d6beb32cd77e371ac4
- **短哈希**：f40ec2096
- **日期**：2024-12-16（Mon Dec 16 11:10:03 2024 +0100）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Add TableUtil to provide access to a table's format version (#11620)
- **PR/Issue**：#11620

## 总体目的

Iceberg 表有格式版本（format version，1/2/3）的概念，存储在表的当前 metadata 文件中。要获取版本，通常需要 `((BaseTable) table).operations().current().formatVersion()`——但这要求调用方能访问到 `TableOperations`，且代码分散、易错：

- 对 `SerializableTable`（序列化到执行端的表实例）无法直接拿到，因为序列化后不再持有可用的 `TableOperations`（`StaticTableOperations` 也只在特定场景可用）。
- 对 `MetadataTable`（如 `tbl.history`、`tbl.snapshots` 等元数据表）根本没有"自己的" format version。
- 调用方各处自己 cast 到 `BaseTable` 或 `HasTableOperations`，缺乏统一入口。

本提交引入 `TableUtil` 工具类，提供 `TableUtil.formatVersion(Table)` 静态方法，统一封装"如何取表的格式版本"这一逻辑，支持 `SerializableTable`、普通 `HasTableOperations` 表，并对 `MetadataTable` 等无版本场景抛出明确异常。同时让 `SerializableTable` 在序列化时就把 format version 一起快照下来，使反序列化端无需再访问 metadata 文件即可得知版本。

## 如何达成设计目的

1. `SerializableTable` 新增 `formatVersion` 字段：在构造时通过 `formatVersion(table)` 私有方法取版本（若 table 是 `HasTableOperations` 则从 `operations().current().formatVersion()` 取，否则记为 `UNKNOWN_FORMAT_VERSION = -1`）；并暴露 `public int formatVersion()` 方法（未知时抛 `UnsupportedOperationException`）。
2. 新增 `TableUtil`：`formatVersion(Table)` 静态方法按优先级判断——`SerializableTable` 走其 `formatVersion()`；其它 `HasTableOperations` 走 operations；否则抛 `IllegalArgumentException`。
3. `CatalogTests`、`TestTableSerialization` 用 `TableUtil.formatVersion(...)` 替换原本直接 cast 到 `BaseTable` 的写法，统一调用入口；并扩展 `createTableTransaction` 的参数化为 `{1, 2, 3}`，`TestTableSerialization` 新增对序列化表 format version 的断言。
4. 新增 `TestTableUtil` 覆盖 null、BaseTable、SerializableTable、各 `MetadataTableType` 等场景。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SerializableTable.java`

**修改目的**：让序列化表携带并暴露 format version。

**工作逻辑**：

- 新增常量 `UNKNOWN_FORMAT_VERSION = -1` 与字段 `formatVersion`、并把已有的 `uuid` 字段一起重新组织顺序（仅为整洁，行为不变）。
- 构造函数末尾调用 `this.formatVersion = formatVersion(table);`。私有方法 `formatVersion(Table)`：若 table 是 `HasTableOperations`，返回 `ops.operations().current().formatVersion()`；否则返回 `UNKNOWN_FORMAT_VERSION`。
- 新增 public `formatVersion()`：若字段为 -1 抛 `UnsupportedOperationException("... does not have a format version")`，否则返回字段值。

这样反序列化端调用 `TableUtil.formatVersion(serializableTable)` 时无需再访问底层 metadata 文件（已快照在字段中）。

### `core/src/main/java/org/apache/iceberg/TableUtil.java`（新增）

**修改目的**：提供统一的 `formatVersion(Table)` 入口。

**工作逻辑**：

```java
public static int formatVersion(Table table) {
  Preconditions.checkArgument(null != table, "Invalid table: null");
  if (table instanceof SerializableTable) {
    return ((SerializableTable) table).formatVersion();
  } else if (table instanceof HasTableOperations) {
    return ((HasTableOperations) table).operations().current().formatVersion();
  } else {
    throw new IllegalArgumentException(
        String.format("%s does not have a format version", table.getClass().getSimpleName()));
  }
}
```

注意 `SerializableTable` 优先判断，因为序列化表虽也实现 `HasTableOperations`，但其 `operations()` 在反序列化端可能不可用（`StaticTableOperations`），故走快照字段。

### `core/src/test/java/org/apache/iceberg/TestTableUtil.java`（新增）

**修改目的**：覆盖 `TableUtil.formatVersion` 各分支。

**工作逻辑**：

- `nullTable`：传 null 抛 `IllegalArgumentException("Invalid table: null")`。
- `formatVersionForBaseTable(formatVersion)`：参数化 `{1, 2, 3}`，用 `InMemoryCatalog` 建表，断言 `TableUtil.formatVersion(table)` 与 `TableUtil.formatVersion(SerializableTable.copyOf(table))` 都返回对应版本。
- `formatVersionForMetadataTables`：遍历所有 `MetadataTableType`，断言对 `MetadataTable` 实例抛 `IllegalArgumentException("... does not have a format version")`；对 `SerializableTable.copyOf(metadataTable)` 抛 `UnsupportedOperationException`（因为序列化时拿不到 operations，字段为 -1）。

### `core/src/test/java/org/apache/iceberg/catalog/CatalogTests.java`

**修改目的**：用 `TableUtil.formatVersion` 替换直接 cast。

**工作逻辑**：

- 引入 `import org.apache.iceberg.TableUtil;`。
- `createTableTransaction` 的 `@ValueSource` 从 `{1, 2}` 扩展到 `{1, 2, 3}`，验证 v3 事务建表也能正确读取版本。
- 两处断言改为 `assertThat(TableUtil.formatVersion(...)).isEqualTo(...)`，移除 `((BaseTable) table).operations().current().formatVersion()` 的强转写法。

### `core/src/test/java/org/apache/iceberg/hadoop/TestTableSerialization.java`

**修改目的**：验证序列化表的 format version 行为。

**工作逻辑**：

- 引入 `TableUtil`。
- 在已有断言序列化表为 `StaticTableOperations` 的测试中，新增 `assertThat(TableUtil.formatVersion(serializableTable)).isEqualTo(2);`（基线表是 v2）。
- 在验证 `SerializableTable.copyOf(metadataTable)` 不支持 operations 的测试中，新增断言：调用 `TableUtil.formatVersion` 会抛 `UnsupportedOperationException`，消息以 "does not have a format version" 结尾。

## 小结

- **成效**：提供了统一的 `TableUtil.formatVersion(Table)` 入口，消除各处 `((BaseTable) t).operations().current().formatVersion()` 的散乱强转；`SerializableTable` 序列化时携带版本，反序列化端无需再访问 metadata；明确区分了"无版本"（MetadataTable、未知实现）与"有版本"场景，异常更清晰；测试扩展到 v3。
- **影响范围**：`core` 模块 5 个文件，163 行新增/7 行删除。新增公开 API `TableUtil.formatVersion` 与 `SerializableTable.formatVersion()`；测试基础设施增强。
- **回迁到 1.4.x 的注意事项**：
  - `TableUtil` 是新增的工具类，对 1.4.x 是纯增强，**可安全回迁**，前提是 1.4.x 的 `SerializableTable` 与 `HasTableOperations` 接口与本提交基线一致。
  - 若 1.4.x 尚不支持 v3（format version = 3），则 `TestTableUtil` 中 `@ValueSource(ints = {1, 2, 3})` 与 `CatalogTests.createTableTransaction` 的 v3 分支需调整为 `{1, 2}`，否则 v3 建表会失败。
  - 回迁后应确保 `SerializableTable` 的序列化兼容性：新增的 `formatVersion` 字段是 `int`（基本类型），序列化布局变化需评估是否影响跨版本读写（Iceberg 的 `SerializableTable` 通常用于同版本进程间传递，影响较小）。
  - 依赖本提交的下游（如 Spark/Flink 引擎中读取 format version 的逻辑）若已存在于 1.4.x，应一并切换到 `TableUtil`，避免维护两套入口。
