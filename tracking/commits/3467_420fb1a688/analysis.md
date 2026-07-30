# 提交 3467：Spark 4.1: Set data file sort_order_id in manifest for writes from Spark (#15150)

## 提交信息

- **序号**：3467 / 4088
- **哈希**：420fb1a688926440582fe078d7348f60f27997a8
- **短哈希**：420fb1a688
- **日期**：2026-03-27 12:44:23 -0500
- **作者**：jbewing
- **提交说明**：Spark 4.1: Set data file sort_order_id in manifest for writes from Spark (#15150)
- **PR/Issue**：#15150

## 总体目的

在 Spark 写入 Iceberg 表时，正确设置数据文件清单（manifest）中的 `sort_order_id` 字段。之前 Spark 写入的数据文件不包含排序顺序 ID 信息，导致读取端无法利用排序信息进行优化。此提交实现：

1. 在 Spark 写入时确定正确的 sort order ID
2. 将 sort order ID 传递到 writer 并写入 manifest
3. 在数据文件重写时也正确匹配表的 sort order
4. 增强 `SerializableTable` 以支持序列化多个 sort order

## 如何达成设计目的

- 在 `SparkWriteConf` 中添加 `outputSortOrderId()` 方法确定写入使用的 sort order ID
- 新增 `OUTPUT_SORT_ORDER_ID` 写入选项
- 在 `SparkWrite` 和 `SparkPositionDeltaWrite` 中将 sortOrderId 传递到 WriterFactory
- 在 writer builder 中调用 `dataSortOrder()` 设置数据文件的排序顺序
- 在 `SparkShufflingFileRewriteRunner` 中使用 `SortOrderUtil.findTableSortOrder()` 匹配表排序顺序
- 增强 `SerializableTable` 序列化所有 sort orders（而非仅当前 sort order）
- 新增 `SortOrderUtil.findTableSortOrder()` 工具方法

## 修改详情

### `core/src/main/java/org/apache/iceberg/SerializableTable.java` (+17/-2 lines)

**修改目的**：增强 SerializableTable 以序列化所有 sort orders。

**工作逻辑**：
- 新增 `sortOrderAsJsonMap` 字段，存储所有 sort order 的 JSON 表示
- 新增 `lazySortOrders` 懒加载字段
- 构造函数中遍历 `table.sortOrders()` 序列化所有排序顺序
- 重写 `sortOrders()` 方法，优先从序列化的 JSON 反序列化，避免远程调用

### `core/src/main/java/org/apache/iceberg/util/SortOrderUtil.java` (+17/-0 lines)

**修改目的**：新增 `findTableSortOrder()` 方法。

**工作逻辑**：
```java
public static SortOrder findTableSortOrder(Table table, SortOrder userSuppliedSortOrder) {
    return table.sortOrders().values().stream()
        .filter(sortOrder -> sortOrder.sameOrder(userSuppliedSortOrder))
        .findFirst()
        .orElseGet(SortOrder::unsorted);
}
```
- 遍历表的所有 sort orders，找到与用户提供的排序顺序匹配的
- 如果没有匹配则返回 `SortOrder.unsorted()`

### `core/src/test/java/org/apache/iceberg/util/TestSortOrderUtil.java` (+64/-0 lines)

**修改目的**：添加 `findTableSortOrder()` 的测试。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java` (+20/-0 lines)

**修改目的**：添加 `outputSortOrderId()` 方法。

**工作逻辑**：
1. 首先检查显式配置的 `output-sort-order-id` 选项
2. 如果未显式配置且写入有排序要求，使用表当前 sort order 的 ID
3. 否则返回 unsorted 的 order ID

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java` (+1/-0 lines)

**修改目的**：添加 `OUTPUT_SORT_ORDER_ID` 选项常量。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+10/-1 lines)

**修改目的**：在 WriterFactory 中传递 sortOrderId 并设置 dataSortOrder。

**工作逻辑**：
- 在创建 WriterFactory 前调用 `writeConf.outputSortOrderId()` 获取 sortOrderId
- WriterFactory 新增 sortOrderId 字段
- 在创建 writer builder 时调用 `.dataSortOrder(table.sortOrders().get(sortOrderId))`

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (+11/-1 lines)

**修改目的**：在 PositionDelta 写入中也传递 sortOrderId。

**工作逻辑**：
- 与 SparkWrite 相同的模式
- PositionDeltaWriteFactory 新增 sortOrderId 字段
- 在创建 delete writer builder 时设置 dataSortOrder

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/SparkShufflingFileRewriteRunner.java` (+16/-0 lines)

**修改目的**：在数据文件重写时匹配表 sort order。

**工作逻辑**：
- 使用 `SortOrderUtil.findTableSortOrder()` 查找重写排序顺序对应的表 sort order
- 如果排序顺序不匹配任何表 sort order，记录警告日志
- 通过 `OUTPUT_SORT_ORDER_ID` 选项传递匹配的 sort order ID

### 测试文件

- `TestCopyOnWriteDelete.java`、`TestCopyOnWriteMerge.java`、`TestCopyOnWriteUpdate.java`、`TestMergeOnReadMerge.java`、`TestMergeOnReadUpdate.java`：添加 sort_order_id 验证测试
- `TestSparkWriteConf.java`：添加 `outputSortOrderId` 测试
- `TestRewriteDataFilesAction.java`：添加重写后 sort_order_id 验证
- `TestSparkDataWrite.java`、`TestStructuredStreaming.java`：添加 sort_order_id 验证

## 总结

该提交实现了 Spark 4.1 写入 Iceberg 表时正确设置 manifest 中 `sort_order_id` 字段的功能。通过在写入配置中确定 sort order ID，传递到 writer 并设置 `dataSortOrder`，确保读取端可以利用排序信息进行优化。同时增强了 `SerializableTable` 的序列化能力和 `SortOrderUtil` 的匹配能力，并在数据文件重写时也正确处理 sort order。
