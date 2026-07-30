# 提交 3500：Spark (4.0, 3.5): Set data file sort_order_id in manifest for writes from Spark (#15832)

## 提交信息

- **序号**：3500 / 4088
- **哈希**：1a2a8a5ecc701629183405ca842f2d1eb23505ee
- **短哈希**：1a2a8a5ecc
- **日期**：2026-04-02 09:54:55 -0700
- **作者**：jbewing
- **提交说明**：Spark (4.0, 3.5): Set data file sort_order_id in manifest for writes from Spark (#15832)
- **PR/Issue**：#15832

## 总体目的

修复 Spark 写入 Iceberg 时数据文件的 `sort_order_id` 未在 manifest 中正确设置的问题。当 Spark 按表的 sort order 写入数据时，生成的数据文件应该在 manifest 中标记其 sort_order_id，但原实现未设置该字段。这导致读取时无法利用排序信息进行优化（如文件裁剪）。

该修复在 Spark 3.5 和 4.0 中同步应用，新增 `output-sort-order-id` 写入选项和 `outputSortOrderId` 配置方法，在写入时确定并传递 sort order ID 到 writer。

## 如何达成设计目的

1. 在 `SparkWriteOptions` 中新增 `OUTPUT_SORT_ORDER_ID` 选项。
2. 在 `SparkWriteConf` 中新增 `outputSortOrderId(SparkWriteRequirements)` 方法，确定写入使用的 sort order ID：
   - 优先使用显式指定的 `output-sort-order-id` 选项。
   - 否则，如果 writeRequirements 有排序要求，使用表当前 sort order 的 ID。
   - 否则返回 unsorted 的 ID。
3. 在 `SparkWrite` 和 `SparkPositionDeltaWrite` 中调用 `outputSortOrderId` 并传递到 WriterFactory。
4. 在 WriterFactory 中使用 `table.sortOrders().get(sortOrderId)` 获取 SortOrder 并通过 `dataSortOrder()` 传递给 writer builder。
5. 在 `SparkShufflingFileRewriteRunner` 中，使用 `SortOrderUtil.findTableSortOrder` 匹配表的 sort order，并传递 `OUTPUT_SORT_ORDER_ID` 选项。如果排序不匹配任何表 sort order，发出警告。
6. 新增测试验证 sort_order_id 在各种写入场景（COPY_ON_WRITE、MERGE_ON_READ、streaming、rewrite）中正确设置。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+20 lines)

**修改目的**：新增 outputSortOrderId 方法。

**工作逻辑**：
```java
public int outputSortOrderId(SparkWriteRequirements writeRequirements) {
  Integer explicitId = confParser.intConf().option(SparkWriteOptions.OUTPUT_SORT_ORDER_ID).parseOptional();
  if (explicitId != null) {
    Preconditions.checkArgument(table.sortOrders().containsKey(explicitId), ...);
    return explicitId;
  }
  if (writeRequirements.hasOrdering()) {
    return table.sortOrder().orderId();
  }
  return SortOrder.unsorted().orderId();
}
```

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java` (+1 line)

**修改目的**：新增 OUTPUT_SORT_ORDER_ID 常量。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+10/-1 lines)

**修改目的**：在 WriterFactory 中传递 sortOrderId 并设置 dataSortOrder。

**工作逻辑**：
- `createWriterFactory` 中调用 `writeConf.outputSortOrderId(writeRequirements)` 获取 sortOrderId。
- WriterFactory 构造函数新增 sortOrderId 参数。
- `createWriter` 中调用 `.dataSortOrder(table.sortOrders().get(sortOrderId))` 设置数据文件的 sort order。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (+11/-1 lines)

**修改目的**：在 PositionDelta 写入中传递 sortOrderId。

**工作逻辑**：与 SparkWrite 类似，在 PositionDeltaWriteFactory 中新增 sortOrderId，在 createWriter 中设置 dataSortOrder。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingFileRewriteRunner.java` (+16 lines)

**修改目的**：在数据文件重写中设置 sort order ID。

**工作逻辑**：
- 使用 `SortOrderUtil.findTableSortOrder(table(), sortOrder())` 匹配表的 sort order。
- 如果排序不匹配任何表 sort order（isSorted 但 maybeMatchingTableSortOrder isUnsorted），发出警告。
- 写入时传递 `.option(SparkWriteOptions.OUTPUT_SORT_ORDER_ID, maybeMatchingTableSortOrder.orderId())`。

### Spark 3.5 同步修改 (28 个文件)

**修改目的**：在 Spark 3.5 中同步应用相同的修改。

**工作逻辑**：所有 3.5 版本的对应文件做了相同的修改。

### 测试文件 (+860 lines 总计)

**修改目的**：验证 sort_order_id 在各种写入场景中正确设置。

**工作逻辑**：
- `TestSparkWriteConf`：测试 outputSortOrderId 方法的各种场景。
- `TestSparkDataWrite`：验证 append、overwrite 场景下 sort_order_id 正确。
- `TestStructuredStreaming`：验证流式写入的 sort_order_id。
- `TestRewriteDataFilesAction`：验证数据文件重写的 sort_order_id。
- `TestCopyOnWriteDelete/Merge/Update`、`TestMergeOnReadMerge/Update`：验证 CDC 场景的 sort_order_id。

## 总结

功能修复提交，解决 Spark 写入 Iceberg 时数据文件 manifest 中 sort_order_id 未设置的问题。新增 `output-sort-order-id` 选项和 `outputSortOrderId` 配置方法，在 append、overwrite、CDC、streaming、rewrite 等场景中正确传递 sort order ID。对于重写场景，使用 SortOrderUtil 匹配表 sort order，不匹配时发出警告。在 Spark 3.5 和 4.0 中同步应用。
