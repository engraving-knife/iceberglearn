# 提交 3688：Core: Add partition to TrackedFile (#16253)

## 提交信息

- **序号**：3688 / 4088
- **哈希**：64ba246f3311ccc003fe33b4fa038f9d0cf266e3
- **短哈希**：64ba246f3
- **日期**：2026-05-12 15:05:19 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Add partition to TrackedFile (#16253)
- **PR/Issue**：#16253

## 总体目的

这个提交为 `TrackedFile` 接口及其实现类 `TrackedFileStruct` 添加了 `partition` 字段。`TrackedFile` 是 Iceberg 中用于跟踪文件元信息的接口，包含文件路径、格式、记录数、大小、spec ID 等信息。

此前 `TrackedFile` 包含 `spec_id`（分区规范 ID）但没有直接包含分区数据本身。这意味着消费者如果需要知道文件属于哪个分区，需要额外通过 spec ID 查找分区规范并解析分区数据。添加 `partition` 字段后，分区信息直接嵌入到 `TrackedFile` 中，简化了消费者的使用，避免了额外的查找步骤。

分区数据使用 `PartitionData`（一种 `StructLike`）表示，其 schema 基于分区规范（partition spec）动态确定。

## 如何达成设计目的

通过以下修改实现：
1. 在 `TrackedFile` 接口中定义新的 `partition` 字段常量（ID=102）和 `partition()` 方法
2. 在 `schemaWithContentStats` 方法中增加 `partitionType` 参数，将分区字段加入 schema
3. 在 `TrackedFileStruct` 实现类中添加 `partitionData` 字段和相关逻辑
4. 更新所有字段的 ordinal 位置映射（因为新字段插入到中间位置）
5. 更新测试类

## 修改详情

### `core/src/main/java/org/apache/iceberg/TrackedFile.java` (+8/-2 lines)

**修改目的**：定义 partition 字段常量和接口方法。

**工作逻辑**：

新增字段定义：
```java
int PARTITION_ID = 102;
String PARTITION_NAME = "partition";
String PARTITION_DOC = "Partition data tuple, schema based on the partition spec";
```

更新 `schemaWithContentStats` 方法签名，增加 `partitionType` 参数：
```java
static Types.StructType schemaWithContentStats(
    Types.StructType partitionType, Types.StructType contentStatsType) {
  return Types.StructType.of(
      TRACKING,
      CONTENT_TYPE,
      // ...
      SPEC_ID,
      Types.NestedField.required(PARTITION_ID, PARTITION_NAME, partitionType, PARTITION_DOC),
      // ...
  );
}
```

新增接口方法：
```java
/** Returns partition for this file as a {@link StructLike}. */
StructLike partition();
```

### `core/src/main/java/org/apache/iceberg/TrackedFileStruct.java` (+53/-8 lines)

**修改目的**：实现 partition 字段的存储和访问逻辑。

**工作逻辑**：

1. **新增空分区数据常量**：用于未指定分区时的默认值：
```java
private static final PartitionData EMPTY_PARTITION_DATA =
    new PartitionData(EMPTY_STRUCT_TYPE) {
      @Override
      public PartitionData copy() {
        return this; // this does not change
      }
    };
```

2. **更新 BASE_TYPE**：在 `SPEC_ID` 和 `CONTENT_STATS` 之间插入 `partition` 字段（使用空 struct 类型作为占位）。

3. **新增 partitionData 字段**：默认值为 `EMPTY_PARTITION_DATA`。

4. **更新投影构造函数**：从投影 schema 中读取 partition 字段类型并创建对应的 `PartitionData`：
```java
Type partType = projection.fieldType("partition");
if (partType != null) {
  this.partitionData = new PartitionData(partType.asNestedType().asStructType());
}
```

5. **更新直接构造函数**：增加 `PartitionData partition` 参数。

6. **更新拷贝构造函数**：增加 `this.partitionData = toCopy.partitionData.copy();`。

7. **实现 `partition()` 方法**：返回 `partitionData`。

8. **更新 `get` 和 `set` 方法的 ordinal 映射**：由于 partition 字段插入在位置 7（原 contentStats 位置），所有后续字段的 ordinal 都加 1。例如原 `case 7: return contentStats;` 变为 `case 7: return partitionData; case 8: return contentStats;`，以此类推。

### `core/src/test/java/org/apache/iceberg/TestTrackedFile.java` (+14/-14 lines) 和 `core/src/test/java/org/apache/iceberg/TestTrackedFileStruct.java` (+67/-53 lines)

**修改目的**：更新测试以适配新的 partition 字段。

**工作逻辑**：更新测试中的 schema 构造方法调用（增加 partitionType 参数），并添加针对 partition 字段的测试用例。

## 总结

这是一个核心 API 增强提交，为 `TrackedFile` 添加了分区数据字段，使文件跟踪信息更加完整。修改虽然涉及字段 ordinal 的全面调整，但设计上考虑了向后兼容性（使用默认空分区数据）。这一改进简化了消费者获取分区信息的流程，避免了额外的查找步骤，提升了使用效率。
