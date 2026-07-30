# 提交 0097：Spark: Clean up FileIO instances on executors for metadata tables (#8924)

## 提交信息

- **序号**：0097 / 4088
- **哈希**：d7f46b4552a88c3966a924aba2dfd51c0c8b451e
- **短哈希**：d7f46b455
- **日期**：2023-10-26
- **作者**：Anton Okolnychyi
- **提交说明**：Spark: Clean up FileIO instances on executors for metadata tables (#8924)
- **PR/Issue**：#8924

## 总体目的

本提交修复 Spark 引擎在广播元数据表（metadata table）对象到 executor 时，executor 端 `FileIO` 实例不被释放导致的资源泄漏问题，是 commit 0018（针对普通数据表 `SerializableTableWithSize` 引入 `AutoCloseable` 与 `serializationMarker` 机制）的补全。

背景如下：Iceberg 的 Spark 集成通过 `SerializableTableWithSize` 把表对象序列化后作为广播变量分发到 executor。表对象内部持有 `FileIO`（如 `HadoopFileIO`、S3/Azure 客户端等），这些对象可能占用底层连接池、HTTP 客户端、文件句柄等资源。Spark 在驱动端 GC 后会清理广播变量副本，调用其 `close()` 方法。为了避免在驱动端误关共享的 `FileIO`、又能在 executor 端释放副本持有的资源，Iceberg 采用了"transient 标记字段"模式：在构造时给实例赋一个 `transient Object serializationMarker`，驱动端原始实例该字段非 null，而反序列化到 executor 后该字段为 null，`close()` 据此判断只在 executor 端调用 `io().close()`。

此前 commit 0018 已将该机制应用到外层类 `SerializableTableWithSize`（处理普通数据表），但内部嵌套类 `SerializableMetadataTableWithSize`（处理元数据表，如 entries 表、files 表、history 表等）仍只实现 `KnownSizeEstimation` 而未实现 `AutoCloseable`。这意味着当查询扫描元数据表时，广播到 executor 的元数据表副本在清理时不会关闭其 `FileIO`，造成资源泄漏——尤其在长时间运行、频繁查询元数据表的 Spark 作业中，连接池/句柄泄漏会逐渐累积。

本提交为 `SerializableMetadataTableWithSize` 补齐同样的 `AutoCloseable` 实现与 `serializationMarker` 机制，使元数据表场景也能在 executor 端正确释放 `FileIO`，与普通表保持一致。改动同步应用到 Spark 3.2/3.3/3.4/3.5 四个版本分支，并扩展既有单元测试覆盖元数据表。

## 如何达成设计目的

设计思路完全沿用 commit 0018 已验证的方案：让 `SerializableMetadataTableWithSize` 实现 `AutoCloseable`，新增一个 `transient Object serializationMarker` 字段在构造器中初始化，并在 `close()` 中仅当该字段为 null（即反序列化副本）时调用 `io().close()`。由于 `transient` 字段不参与序列化，驱动端创建的实例该字段非 null（不关闭共享 `FileIO`），executor 端反序列化得到的副本该字段为 null（关闭副本自身 `FileIO`）。这一区分机制天然适配 Spark 广播变量的生命周期。

测试侧则把既有的两个 close 验证测试（Kryo 与 Java 序列化路径）从只测普通表 `table`，扩展为遍历普通表加所有 `MetadataTableType` 枚举值构造的元数据表，确保元数据表副本在两种序列化路径下都能正确触发 `io().close()` 而驱动端不被误关。

## 修改详情

### `spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/source/SerializableTableWithSize.java`（v3.3/v3.4/v3.5 同名文件改动完全一致）

**修改目的**：为元数据表可序列化副本补齐 `AutoCloseable`，使 executor 端广播副本清理时能释放 `FileIO`。

**工作逻辑**：

`SerializableMetadataTableWithSize` 是 `SerializableTableWithSize` 的内部静态嵌套类，继承 `SerializableMetadataTable`，原本只 `implements KnownSizeEstimation`（用于向 Spark SizeEstimator 报告固定大小估算）。改动如下：

1. **类签名扩展**：增加 `AutoCloseable` 接口：

   ```java
   public static class SerializableMetadataTableWithSize extends SerializableMetadataTable
       implements KnownSizeEstimation, AutoCloseable {
   ```

2. **新增 Logger 与 transient 标记字段**：

   ```java
   private static final Logger LOG =
       LoggerFactory.getLogger(SerializableMetadataTableWithSize.class);

   private final transient Object serializationMarker;
   ```

   `transient` 修饰是关键——Java 序列化与 Kryo 序列化默认都会跳过 `transient` 字段，因此反序列化副本该字段为 null。

3. **构造器初始化标记字段**：

   ```java
   protected SerializableMetadataTableWithSize(BaseMetadataTable metadataTable) {
     super(metadataTable);
     this.serializationMarker = new Object();
   }
   ```

   驱动端通过 `copyOf` 创建的原始实例，`serializationMarker` 非 null。

4. **新增 `close()` 方法**：

   ```java
   @Override
   public void close() throws Exception {
     if (serializationMarker == null) {
       LOG.info("Releasing resources");
       io().close();
     }
   }
   ```

   仅当 `serializationMarker == null`（反序列化副本，即 executor 端）才调用 `io().close()`；驱动端原始实例该字段非 null，`close()` 直接返回，不关闭共享 `FileIO`。日志 `Releasing resources` 用于运维观测。

该实现与外层类 `SerializableTableWithSize` 的 `close()` 完全同构，保证两类表行为一致。

### `spark/v3.2/spark/src/test/java/org/apache/iceberg/TestTableSerialization.java`（v3.3/v3.4/v3.5 同名文件改动完全一致）

**修改目的**：把 close 行为验证从仅覆盖普通表扩展到覆盖全部元数据表类型，确保元数据表副本在 executor 端正确释放 `FileIO`。

**工作逻辑**：

1. **新增辅助方法 `tables()`**：返回一个列表，包含普通数据表 `table` 以及遍历 `MetadataTableType.values()` 通过 `MetadataTableUtils.createMetadataTableInstance(table, type)` 创建的所有元数据表实例（entries、files、history、snapshots、manifests、partitions 等）。

   ```java
   private List<Table> tables() {
     List<Table> tables = Lists.newArrayList();
     tables.add(table);
     for (MetadataTableType type : MetadataTableType.values()) {
       Table metadataTable = MetadataTableUtils.createMetadataTableInstance(table, type);
       tables.add(metadataTable);
     }
     return tables;
   }
   ```

2. **改造 `testCloseSerializableTableKryoSerialization` 与 `testCloseSerializableTableJavaSerialization`**：原测试体被包裹进 `for (Table tbl : tables()) { ... }` 循环，对每个表重复完整验证流程：

   - 用 Mockito `spy(tbl)` 与 `spy(tbl.io())` 包装，`when(spyTable.io()).thenReturn(spyIO)`；
   - `SerializableTableWithSize.copyOf(spyTable)` 得到可序列化表（驱动端实例）；
   - 通过 `KryoHelpers.roundTripSerialize` / `TestHelpers.roundTripSerialize` 模拟广播序列化得到副本，再 `spy` 副本并替换其 `io()` 为 `spyFileIOCopy`；
   - 分别对原始实例（模拟驱动端 close）与副本（模拟 executor 端 close）调用 `((AutoCloseable) ...).close()`；
   - 断言 `verify(spyIO, never()).close()`（驱动端不关）与 `verify(spyFileIOCopy, times(1)).close()`（executor 端关一次）。

   这样无论 `copyOf` 接收到的是普通表还是元数据表，都会验证 close 行为正确——元数据表走 `SerializableMetadataTableWithSize` 路径，普通表走 `SerializableTableWithSize` 路径，两条路径都被覆盖。

新增 import 包括 `java.util.List`、`com.google.common.collect.Lists`，以及（v3.3+）`MetadataTableType` 与 `MetadataTableUtils`（v3.2 测试本已 import）。

## 小结

本提交为元数据表可序列化副本补齐与普通表一致的 `AutoCloseable` + transient 标记机制，修复了 Spark 广播元数据表时 executor 端 `FileIO` 资源泄漏，是 Iceberg Spark 集成资源治理在元数据表场景上的重要补全。
