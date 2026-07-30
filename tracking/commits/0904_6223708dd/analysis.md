# 提交 0904：Spark 3.5: Support read of partition metadata column when table has over 1k columns (#10547)

## 提交信息

- **序号**：0904 / 4088
- **哈希**：6223708dd574d2e6f775f8e107a78f217c676481
- **短哈希**：6223708dd
- **日期**：2024-07-05
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Spark 3.5: Support read of partition metadata column when table has over 1k columns (#10547)
- **PR/Issue**：#10547

## 总体目的

Iceberg 的 `_partition` 元数据列（metadata column）是一个 struct 类型，其内部各子字段的 field ID 来自表的分区 spec 中的 partition field ID。按 Iceberg 规范，partition field ID 从 1000（`PARTITION_FIELD_ID_START`）开始递增分配。

当表的列数超过 1000 时，普通列的 column ID（从 1 开始递增）也会到达 1000、1001 等区间，与 partition field ID 发生数值重叠。在 Spark 读取场景下，`SparkScanBuilder.schemaWithMetadataColumns()` 会把基础 schema 与元数据列 schema 用 `TypeUtil.join` 合并成一个完整 schema，此时同一个 schema 内出现两个相同 ID 的字段（例如 column ID 1000 的 `c1000` 列与 partition struct 内部 field ID 1000 的分区字段），导致 Spark 读取 `_partition` 时出错（字段解析混乱、数据错位或异常）。

本提交针对 Spark 3.5 模块修复该问题：在构造元数据列 schema 时，检测 `_partition` 是否被请求，若是，则将 partition struct 内部的 field ID 重新分配为不与任何已用 ID（包括所有历史 schema 的列 ID 和元数据列 ID）冲突的新 ID，从而保证合并后的 schema 字段 ID 全局唯一。

## 如何达成设计目的

核心思路是在 `SparkScanBuilder` 中新增 `calculateMetadataSchema` 方法，专门处理元数据列 schema 的构造。当请求中包含 `_partition` 列时：

1. 提取 partition struct 类型内部的所有 field ID（`idsToReassign`）；
2. 收集所有"已占用"的 ID：元数据列自身的 field ID + 表所有 schema（含历史版本）中所有字段的 ID；
3. 用 `Schema` 的 id 重写构造器（`new Schema(fields, identifierFieldIds, idRewriter)`），把 `idsToReassign` 中的每个旧 ID 映射为一个从 1 递增、跳过所有已占用 ID 的新 ID；
4. 不在 `idsToReassign` 中的 ID 保持不变。

这样 partition struct 内部的字段 ID 被重新分配到空闲区间，与基础 schema 合并后不再冲突。若请求中不包含 `_partition`，则直接走原有路径 `new Schema(metaColumnFields)`，无额外开销。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：在元数据列 schema 构造阶段，对 `_partition` 列内部的 partition field ID 进行去重分配，避免与高列数表的 column ID 冲突。

**工作逻辑**：

新增 import：`java.util.Optional`、`java.util.Set`、`java.util.concurrent.atomic.AtomicInteger`、`org.apache.iceberg.relocated.com.google.common.collect.Sets`。

原 `schemaWithMetadataColumns()` 方法简化为：构造 `metadataFields` 列表后调用新方法 `calculateMetadataSchema(metadataFields)` 得到 `metadataSchema`，再 `TypeUtil.join(schema, metadataSchema)`。原方法不再直接 `new Schema(fields)`。

新增私有方法 `calculateMetadataSchema(List<Types.NestedField> metaColumnFields)`：

```java
private Schema calculateMetadataSchema(List<Types.NestedField> metaColumnFields) {
  Optional<Types.NestedField> partitionField =
      metaColumnFields.stream()
          .filter(f -> MetadataColumns.PARTITION_COLUMN_ID == f.fieldId())
          .findFirst();

  // only calculate potential column id collision if partition metadata column was requested
  if (!partitionField.isPresent()) {
    return new Schema(metaColumnFields);
  }

  Set<Integer> idsToReassign =
      TypeUtil.indexById(partitionField.get().type().asStructType()).keySet();

  // Calculate used ids by union metadata columns with all base table schemas
  Set<Integer> currentlyUsedIds =
      metaColumnFields.stream().map(Types.NestedField::fieldId).collect(Collectors.toSet());
  Set<Integer> allUsedIds =
      table.schemas().values().stream()
          .map(currSchema -> TypeUtil.indexById(currSchema.asStruct()).keySet())
          .reduce(currentlyUsedIds, Sets::union);

  // Reassign selected ids to deduplicate with used ids.
  AtomicInteger nextId = new AtomicInteger();
  return new Schema(
      metaColumnFields,
      table.schema().identifierFieldIds(),
      oldId -> {
        if (!idsToReassign.contains(oldId)) {
          return oldId;
        }
        int candidate = nextId.incrementAndGet();
        while (allUsedIds.contains(candidate)) {
          candidate = nextId.incrementAndGet();
        }
        return candidate;
      });
}
```

关键逻辑解析：

- **partition 字段检测**：通过 `MetadataColumns.PARTITION_COLUMN_ID`（即 `Integer.MAX_VALUE - 5`）从元数据列列表中筛选出 `_partition` 列。若不存在则直接返回简单 schema，避免无谓计算。
- **idsToReassign**：`TypeUtil.indexById(partitionField.get().type().asStructType())` 把 partition struct 类型展平为 `Map<fieldId, NestedField>`，其 keySet 即为所有需要重新分配的 partition field ID（例如 1000、1001 等）。
- **allUsedIds**：以元数据列自身的 field ID 集合为初始值，再与表所有 schema（`table.schemas().values()`，含历史 schema）的字段 ID 集合做并集。考虑历史 schema 是为了防止旧 partition field ID 在某个历史 schema 中曾被用作 column ID。
- **id 重写函数**：`oldId -> ...` 仅对 `idsToReassign` 中的 ID 生效，用 `AtomicInteger` 从 1 开始递增，跳过 `allUsedIds` 中已占用的 ID，返回第一个空闲 ID。`Schema` 的三参构造器会对所有字段（含嵌套字段）应用此函数重写 ID。
- **identifierFieldIds**：传入当前 schema 的标识字段 ID，保持 schema 的标识字段语义。注意 identifier field 是基础 schema 的列，不在 `idsToReassign` 中，其 ID 不受重写影响。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkMetadataColumns.java`

**修改目的**：新增回归测试，验证表列数超过 1000 时读取 `_partition` 元数据列的正确性。

**工作逻辑**：新增 `testPartitionMetadataColumnWithManyColumns` 测试方法：

- 构造一个 1010 列的 schema：列 0 为 `id`（LongType），列 1-1009 为 `c1`-`c1009`（StringType）。注意列 ID 直接使用 `i`（1-1009），所以列 ID 1000 对应 `c1000`。
- 分区 spec 为 `identity("id")`，对应的 partition field ID 自动分配为 1000（`PARTITION_FIELD_ID_START`），与列 `c1000` 的 column ID 1000 冲突。
- 通过 `TableOperations` 直接 commit 一个新 schema（`base.updateSchema(manyColumnsSchema, manyColumnsSchema.highestFieldId())`）和新 partition spec，绕过正常建表流程以精确控制列 ID。
- 用 Spark DataFrame 写入 2 行数据（id=0 和 id=1），所有 `c1`-`c1009` 列值为 id 的字符串形式。
- 断言 `SELECT *, _partition` 返回 2 行；并断言 `SELECT _partition, id, c999, c1000, c1001 FROM table ORDER BY id` 返回 `[(0, 0, "0", "0", "0"), (1, 1, "1", "1", "1")]`，其中 `_partition` 值为 `row(0L)` / `row(1L)`（identity 分区的 struct 形式）。

测试选取 `c999, c1000, c1001` 三个列做断言，正好覆盖冲突边界（partition field ID 1000 与 column ID 1000 重叠），确保修复后字段解析和数据读取均正确。

新增 import：`static org.apache.spark.sql.functions.expr`、`java.util.stream.IntStream`、`org.apache.spark.sql.types.StructType`。

## 小结

- **成效**：修复了 Spark 3.5 读取列数超过 1000 的表的 `_partition` 元数据列时因 partition field ID 与 column ID 冲突导致的读取错误，使 `_partition` 在宽表场景下可正常使用。
- **影响范围**：仅影响 Spark 3.5 模块，2 个文件、93 行新增、3 行删除。改动集中在 `SparkScanBuilder` 的元数据列 schema 构造逻辑，不影响正常列数表的读取路径（无 `_partition` 请求时无额外开销）。
- **回迁到 1.4.x 的注意事项**：适合回迁，且建议回迁。这是一个功能性 Bug 修复，影响所有在 Spark 3.5 上使用宽表（>1000 列）并查询 `_partition` 列的用户。回迁注意事项：1) 该修复仅依赖 Iceberg core 中已有的 `TypeUtil.indexById`、`Schema` 三参构造器（id 重写）、`MetadataColumns.PARTITION_COLUMN_ID` 等公共 API，1.4.x 上这些 API 应已存在，无外部依赖问题；2) 回迁时需确认 1.4.x 的 `SparkScanBuilder` 代码结构与 main 分支该文件未发生大幅重构，若已重构需手工对应；3) 测试中直接通过 `TableOperations.commit` 修改 schema/spec 的方式在 1.4.x 上同样可用；4) 该修复不改变表的元数据格式或序列化方式，对已有数据完全兼容。
