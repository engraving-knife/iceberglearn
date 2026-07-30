# 提交 1831：Core: Provide access to format-version of metadata table (#12462)

## 提交信息

- **序号**：1831 / 4088
- **哈希**：19330fa19f833f481063704c1e0122e068289259
- **短哈希**：19330fa19
- **日期**：2025-03-07 07:29:53 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Provide access to format-version of metadata table (#12462)
- **PR/Issue**：#12462

## 总体目的

本提交解决了 `TableUtil.formatVersion()` 无法获取元数据表（metadata table）格式版本的问题。在此之前，`TableUtil.formatVersion(table)` 只能处理两种情况：如果 table 是 `HasTableOperations` 的实例，则通过 `operations().current().formatVersion()` 获取；否则抛出 `IllegalArgumentException`。然而，元数据表（如 `MetadataTableUtils.createMetadataTableInstance` 创建的 `BaseMetadataTable` 实例）虽然实现了 `Table` 接口，但既不是直接的 `HasTableOperations`，也没有直接的格式版本属性，导致调用 `formatVersion` 时抛出异常。

这个问题在 `SerializableTable` 中尤为突出：`SerializableTable.formatVersion(Table table)` 方法在内部调用 `TableUtil.formatVersion(table)`，当传入的 table 是元数据表时，原实现会走 else 分支返回 `UNKNOWN_FORMAT_VERSION`（因为元数据表不是 `HasTableOperations`），导致序列化后的元数据表丢失格式版本信息。

本提交的修复方式是在 `TableUtil.formatVersion` 中新增对 `BaseMetadataTable` 的处理分支：如果是元数据表，则通过 `metadataTable.table().operations().current().formatVersion()` 获取底层原表的格式版本。因为元数据表本质上是基于某个真实表构建的视图，其格式版本应与底层表一致。

## 如何达成设计目的

整体思路是在 `TableUtil.formatVersion` 的类型判断链中，于 `HasTableOperations` 分支之后、抛异常的 else 分支之前，新增一个 `BaseMetadataTable` 分支。同时在 `SerializableTable.formatVersion` 中，将原来对 `HasTableOperations` 的手动判断改为直接调用 `TableUtil.formatVersion`，并通过 try-catch 捕获 `IllegalArgumentException` 来兼容仍无法获取格式版本的场景（返回 `UNKNOWN_FORMAT_VERSION`）。这样既复用了 `TableUtil` 的逻辑，又保证了 `SerializableTable` 的容错性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableUtil.java` (修改)

**修改目的**：在 `formatVersion` 方法中新增对 `BaseMetadataTable` 的支持。

**工作逻辑**：在原有的 `table instanceof StaticTable` 和 `table instanceof HasTableOperations` 两个分支之后，新增 `else if (table instanceof BaseMetadataTable)` 分支：将 table 转型为 `BaseMetadataTable`，然后调用 `metadataTable.table().operations().current().formatVersion()` 获取底层原表的格式版本。这里 `metadataTable.table()` 返回构建该元数据表的底层 `Table`，再通过 `operations().current().formatVersion()` 取得格式版本。这依赖于底层表是 `HasTableOperations`（通常成立）。

### `core/src/main/java/org/apache/iceberg/SerializableTable.java` (修改)

**修改目的**：简化 `formatVersion(Table table)` 方法，复用 `TableUtil.formatVersion`。

**工作逻辑**：原实现手动判断 `table instanceof HasTableOperations`，是则取 formatVersion，否则返回 `UNKNOWN_FORMAT_VERSION`。改为直接 `try { return TableUtil.formatVersion(table); } catch (IllegalArgumentException e) { return UNKNOWN_FORMAT_VERSION; }`。这样元数据表等场景会由 `TableUtil` 正确处理，而仍无法处理的情况（抛 `IllegalArgumentException`）则回退到 `UNKNOWN_FORMAT_VERSION`。

### `core/src/test/java/org/apache/iceberg/TestTableUtil.java` (修改)

**修改目的**：验证元数据表和其 `SerializableTable` 副本都能正确获取格式版本。

**工作逻辑**：`formatVersionForMetadataTables` 测试原来断言 `TableUtil.formatVersion(metadataTable)` 和 `TableUtil.formatVersion(SerializableTable.copyOf(metadataTable))` 都抛出异常。现在改为断言两者都返回与底层表相同的 formatVersion（通过 `((BaseTable) table).operations().current().formatVersion()` 取得期望值）。

### `core/src/test/java/org/apache/iceberg/hadoop/TestTableSerialization.java` (修改)

**修改目的**：验证序列化后的表能正确获取格式版本。

**工作逻辑**：原来断言 `TableUtil.formatVersion(serializableTable)` 抛出 `UnsupportedOperationException`。现在改为断言 `TableUtil.formatVersion(serializableTable)` 返回 `((BaseTable) table).operations().current().formatVersion()` 的值，即与原表格式版本一致。同时新增了对 `BaseTable` 的导入。

## 小结

本提交通过在 `TableUtil.formatVersion` 中新增 `BaseMetadataTable` 分支，使元数据表的格式版本能正确获取，并简化了 `SerializableTable` 的相关逻辑。改动集中在 core 模块，涉及 4 个文件，影响面可控。回迁到 1.4.x 时需注意：`BaseMetadataTable` 类需存在于 1.4.x 中（通常是存在的）；`SerializableTable.formatVersion` 的 try-catch 行为变更需确保不掩盖真实异常。
