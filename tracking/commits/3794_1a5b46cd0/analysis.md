# 提交 3794：Core: Cache PartitionData template in PartitionsTable to avoid rebuilding Avro schema per partition (#16208)

## 提交信息

- **序号**：3794 / 4088
- **哈希**：1a5b46cd02c7aea50524a36d57b5f3996a3ebacd
- **短哈希**：1a5b46cd0
- **日期**：2026-05-28 16:27:57 +0200
- **作者**：SevenJ
- **提交说明**：Core: Cache PartitionData template in PartitionsTable to avoid rebuilding Avro schema per partition (#16208)
- **PR/Issue**：#16208

## 总体目的

这个提交优化了 `PartitionsTable` 的性能，通过缓存 `PartitionData` 模板来避免为每个分区重建 Avro schema。

**问题**：在 `PartitionsTable.partitions()` 方法中，对于每个新发现的分区，都会调用 `new Partition(key, partitionType)`，其中 `partitionType` 是 `Types.StructType`。在 `Partition` 构造函数中，`toPartitionData(key, keyType)` 会创建一个新的 `PartitionData(keyType)` 实例。`PartitionData` 的构造会调用 `getSchema()` 来构建 Avro schema，这是一个相对昂贵的操作，涉及反射和 schema 构建。

当表有大量分区时，这个操作会重复执行很多次，每次都重建相同的 Avro schema（因为所有分区的 partitionType 相同），造成不必要的 CPU 开销。

**修复**：在 `partitions()` 方法中创建一个 `PartitionData` 模板实例，传递给所有 `Partition` 构造函数。`Partition` 使用模板的 `copyFor()` 方法创建副本，复用已构建的 Avro schema。

## 如何达成设计目的

1. 在 `partitions()` 方法中创建 `PartitionData partitionDataTemplate = new PartitionData(partitionType)`。
2. 修改 `Partition` 构造函数接收 `PartitionData` 模板而非 `Types.StructType`。
3. `toPartitionData` 方法使用模板的 `copyFor()` 方法而非每次创建新实例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionsTable.java` (+18/-7 lines)

**修改目的**：缓存 PartitionData 模板避免重复构建 Avro schema。

**工作逻辑**：

1. **`partitions()` 方法**：
   ```java
   Types.StructType partitionType = Partitioning.partitionType(table);
   PartitionData partitionDataTemplate = new PartitionData(partitionType);  // 新增：创建模板
   ```
   将 `computeIfAbsent(key, () -> new Partition(key, partitionType))` 改为 `computeIfAbsent(key, () -> new Partition(key, partitionDataTemplate))`。

2. **`Partition` 构造函数**：
   ```java
   // 修改前
   Partition(StructLike key, Types.StructType keyType) {
     this.partitionData = toPartitionData(key, keyType);
   }
   // 修改后
   Partition(StructLike key, PartitionData partitionDataTemplate) {
     this.partitionData = toPartitionData(key, partitionDataTemplate);
   }
   ```

3. **`toPartitionData` 方法**：
   ```java
   // 修改前
   private static PartitionData toPartitionData(StructLike key, Types.StructType keyType) {
     PartitionData keyTemplate = new PartitionData(keyType);  // 每次创建新实例
     return keyTemplate.copyFor(key);
   }
   // 修改后
   private static PartitionData toPartitionData(StructLike key, PartitionData partitionDataTemplate) {
     return partitionDataTemplate.copyFor(key);  // 复用模板
   }
   ```

   `copyFor()` 方法创建一个新的 `PartitionData` 实例但复用底层的 Avro schema 对象，避免了重复构建 schema 的开销。

4. **可见性调整**：将 `partitions()` 方法标记为 `@VisibleForTesting`，`Partition` 类新增 `partitionData()` 测试访问方法。

### `core/src/test/java/org/apache/iceberg/TestMetadataTableScans.java` (+17/-0 lines)

**修改目的**：验证所有分区共享同一个 Avro schema 实例。

**工作逻辑**：
新增 `testPartitionsTableReusesPartitionDataSchema` 测试：
1. 准备分区表。
2. 创建 `PartitionsTable` 并执行扫描。
3. 收集所有分区的 `PartitionData.getSchema()`。
4. 断言至少有 2 个分区。
5. 断言所有分区的 schema 是同一个对象实例（`isSameAs`），证明 schema 被复用而非重建。

## 总结

这个提交通过缓存 `PartitionData` 模板，避免了 `PartitionsTable` 中为每个分区重复构建 Avro schema 的性能开销。对于有大量分区的表，这可以显著减少 CPU 使用量。优化方式简单有效：创建一个模板实例，通过 `copyFor()` 复用 schema 对象。测试验证了 schema 实例确实被共享。
