# 提交 0518：Allow creating metadata tables based on SerializableTable instances (#9735)

## 提交信息

- **序号**：0518 / 4088
- **哈希**：598552e134c2a31a6eb258cb2b071d08f04e079f
- **短哈希**：598552e13
- **日期**：2024-02-19 18:08:18 +0100
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Allow creating metadata tables based on SerializableTable instances (#9735)
- **PR/Issue**：#9735

## 总体目的

Iceberg 的 `SerializableTable` 是一种特殊的 `Table` 实现：它把表的核心元数据（schema、spec、sort order、location、metadataFileLocation 等）序列化为字符串字段，可以在 JVM 之间传输（典型场景是 Spark driver 把表序列化后发给 executor）。在 executor 侧拿到 `SerializableTable` 后，它通过 `lazyTable()` 方法基于 `StaticTableOperations` 懒加载还原出一个 `BaseTable` 来执行查询。

问题在于：`MetadataTableUtils.createMetadataTableInstance(Table table, MetadataTableType type)` 这个工厂方法在修改前**只接受 `BaseTable`**：

```java
if (table instanceof BaseTable) {
  return createMetadataTableInstance(table, metadataTableName(table.name(), type), type);
} else {
  throw new IllegalArgumentException(
      String.format("Cannot create metadata table for table %s: not a base table", table));
}
```

当 executor 上拿到的是 `SerializableTable`（不是 `BaseTable`）时，调用方想基于它构造元数据表（如 `db.t.entries`、`db.t.snapshots`）就会直接抛 `IllegalArgumentException`。这意味着序列化后的表实例无法访问元数据表，限制了在分布式执行器上对元数据的探查能力。

本提交的目的就是打通这条路径：让 `SerializableTable` 也能作为创建元数据表的输入。

## 如何达成设计目的

设计思路是利用 Iceberg 已有的 `HasTableOperations` 接口和已有的 `createMetadataTableInstance(TableOperations, String, String, MetadataTableType)` 重载，做最小改动：

1. **让 `SerializableTable` 实现 `HasTableOperations` 接口**。`HasTableOperations` 是 core 内部接口，只声明 `TableOperations operations()` 方法，用于在不暴露到公共 `Table` 接口的前提下让内部代码拿到表的 operations。`BaseTable` 本来就实现了这个接口，所以 `MetadataTableUtils` 之前对 `BaseTable` 的判断其实隐含了"有 operations"的语义。

2. **在 `SerializableTable` 中实现 `operations()` 方法**。`SerializableTable` 内部已经通过 `lazyTable()` 持有一个还原出来的 `BaseTable`（其 operations 是 `StaticTableOperations`），所以 `operations()` 只需要把 `lazyTable().operations()` 转型为 `StaticTableOperations` 返回即可：
   ```java
   @Override
   public StaticTableOperations operations() {
     return (StaticTableOperations) ((BaseTable) lazyTable()).operations();
   }
   ```
   这里复用了 `lazyTable()` 的双重检查锁懒加载机制，保证只在首次调用时才从 `metadataFileLocation` 读取元数据文件。

3. **在 `MetadataTableUtils.createMetadataTableInstance` 中新增一个 `else if (table instanceof HasTableOperations)` 分支**。这个分支把传入的表当作 `HasTableOperations`，取出它的 `operations()`，然后委托给已有的 `createMetadataTableInstance(ops, baseTableName, metadataTableName, type)` 重载——后者会用 ops 包装出一个新的 `BaseTable`，再走标准的元数据表工厂路由。这样 `SerializableTable` 就能复用所有已有的元数据表构造逻辑，无需为它单独写一套。

这种设计的巧妙之处在于：它没有为 `SerializableTable` 单开特例，而是把判定条件从"是不是 BaseTable"放宽到"是不是 HasTableOperations"。任何未来出现的、能暴露 operations 的 `Table` 实现都能自动获益。同时，错误信息也从"not a base table"改为"table is not a base table or does not have table operations"，更准确地描述了新的接受条件。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MetadataTableUtils.java`

**修改目的**：放宽 `createMetadataTableInstance(Table, MetadataTableType)` 的入参约束，让实现了 `HasTableOperations` 的非 `BaseTable` 实例（典型就是 `SerializableTable`）也能创建元数据表。

**工作逻辑**：修改集中在 `createMetadataTableInstance(Table table, MetadataTableType type)` 方法。原逻辑只有一个 `if (table instanceof BaseTable)` 分支和一个 else 抛异常。修改后变成三分支：

```java
if (table instanceof BaseTable) {
  return createMetadataTableInstance(table, metadataTableName(table.name(), type), type);
} else if (table instanceof HasTableOperations) {
  return createMetadataTableInstance(
      ((HasTableOperations) table).operations(),
      table.name(),
      metadataTableName(table.name(), type),
      type);
} else {
  throw new IllegalArgumentException(
      String.format(
          "Cannot create metadata table for table %s: "
              + "table is not a base table or does not have table operations",
          table));
}
```

- 第一个分支保持不变：`BaseTable` 直接走私有重载。
- 新增第二个分支：对 `HasTableOperations`（但不是 `BaseTable`）的实现，取出 `operations()`，调用四参数重载 `createMetadataTableInstance(TableOperations, String, String, MetadataTableType)`。该重载内部会 `new BaseTable(ops, baseTableName)` 再走工厂路由，相当于把"非 BaseTable 但有 operations"的表"提升"回 BaseTable 路径。
- 第三个分支：错误信息更新，描述新的接受条件。

注意第二个分支传入的 `baseTableName` 用的是 `table.name()`，对于 `SerializableTable` 来说就是它序列化时保存的表名，保证元数据表的命名与原表一致。

### `core/src/main/java/org/apache/iceberg/SerializableTable.java`

**修改目的**：让 `SerializableTable` 实现 `HasTableOperations` 接口，暴露 `operations()` 方法，使其能被上一步的新分支识别。

**工作逻辑**：

1. 类声明从 `implements Table, Serializable` 改为 `implements Table, HasTableOperations, Serializable`。`HasTableOperations` 是 core 内部接口，只声明 `TableOperations operations()`。

2. 新增 `operations()` 方法，放在类的尾部（紧跟 `newTransaction()` 的不支持实现之后）：

```java
@Override
public StaticTableOperations operations() {
  return (StaticTableOperations) ((BaseTable) lazyTable()).operations();
}
```

工作逻辑拆解：
- `lazyTable()` 是 `SerializableTable` 的私有方法，用双重检查锁懒加载：首次调用时基于 `metadataFileLocation` 字段构造 `new StaticTableOperations(metadataFileLocation, io, locationProvider)`，再用 `newTable(ops, name)`（默认 `new BaseTable(ops, name)`）包成 `BaseTable` 缓存到 `lazyTable` 字段。
- `lazyTable()` 返回的是 `BaseTable`，其 `operations()`（来自 `HasTableOperations` 实现）返回的就是构造时传入的 `StaticTableOperations`。
- 所以 `operations()` 方法本质上是 `(StaticTableOperations) lazyTable().operations()`，强转一次是因为 `BaseTable.operations()` 的声明返回类型是 `TableOperations`（接口），而这里知道运行时一定是 `StaticTableOperations`，返回更具体的类型方便调用方使用。

这个设计的副作用是：**首次调用 `operations()` 会触发完整元数据的加载**（读取 metadata.json 文件）。这与 `SerializableTable` 的整体懒加载哲学一致——很多方法（`schemas()`、`sortOrders()`、`statisticsFiles()`、`newScan()` 等）都需要先 `lazyTable()` 才能工作。对于需要创建元数据表的场景，本来就需要读取元数据文件，所以这个代价是合理且不可避免的。

### `core/src/test/java/org/apache/iceberg/hadoop/TestTableSerialization.java`

**修改目的**：验证 `SerializableTable` 现在实现了 `HasTableOperations`，并且能基于它创建元数据表，结果与基于原始表创建的元数据表一致。

**工作逻辑**：

1. 在 `testBasicSerialization()` 中新增两个断言，确认序列化后的表是 `HasTableOperations` 实例，且 `operations()` 返回 `StaticTableOperations`：
   ```java
   Assertions.assertThat(serializableTable).isInstanceOf(HasTableOperations.class);
   Assertions.assertThat(((HasTableOperations) serializableTable).operations())
       .isInstanceOf(StaticTableOperations.class);
   ```

2. 把原有的 `testSerializableMetadataTablesPlanning()` 从 `@Test` 改为参数化测试 `@ParameterizedTest` + `@ValueSource(booleans = {true, false})`，新增一个 `fromSerialized` 参数：
   - `fromSerialized=false`：行为与原来一致，用原始 `table` 创建元数据表。
   - `fromSerialized=true`：用 `SerializableTable.copyOf(table)` 创建元数据表（这是本次新增的测试路径）。
   
   两条路径都遍历所有 `MetadataTableType`，对每个类型创建元数据表、序列化、反序列化、扫描，比较读取到的文件集合。这样就能证明：基于 `SerializableTable` 创建的元数据表，其规划结果与基于原始表创建的完全一致。

3. 新增 `testMetadataTableFromSerializedTable()` 测试：分别用原始 `table` 和 `SerializableTable.copyOf(table)` 创建 `ENTRIES` 元数据表，然后用 `TestHelpers.assertSerializedAndLoadedMetadata(metaFromOriginal, metaFromSerializable)` 断言两者的元数据一致。这是一个更聚焦的等价性验证。

## 小结

这个提交通过两个小改动打通了"序列化表 → 元数据表"的链路：让 `SerializableTable` 实现 `HasTableOperations` 并暴露 `StaticTableOperations`，同时让 `MetadataTableUtils` 接受任何 `HasTableOperations` 实例。改动量很小（核心逻辑约 15 行），但语义清晰，复用了已有的四参数重载，没有引入新的工厂分支。测试覆盖了等价性和序列化往返。

**影响范围**：主要惠及在 Spark executor 等分布式场景下基于序列化表构造元数据表的需求。对 driver 侧直接基于 `BaseTable` 创建元数据表的现有路径无影响（第一个分支未变）。

**回迁到 1.4.x 的注意事项**：

1. 这个提交是纯增量改动，不修改已有方法签名（只是 `SerializableTable` 多实现一个接口、`MetadataTableUtils` 多一个分支），二进制兼容性良好，cherry-pick 风险低。
2. 依赖 `HasTableOperations` 接口和 `StaticTableOperations` 类，这两个在 1.4.x 中都已存在，无新增依赖。
3. 需要确认 1.4.x 的 `SerializableTable.lazyTable()` 实现与 main 一致（都基于 `StaticTableOperations`），否则 `operations()` 的强转可能失败。从当前工作树看，1.4.x 的 `lazyTable()` 逻辑相同，可以安全回迁。
4. 测试文件改动涉及参数化测试（`@ParameterizedTest`），需要 1.4.x 的 JUnit 5 版本支持，这一般没有问题。
