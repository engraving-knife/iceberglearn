# 提交 0852：Core: Simplify newTableMetadata method in TableMetadata (#10528)

## 提交信息
- **序号**：0852 / 4088
- **哈希**：3c714402773296e168263c63b5ffd346fdb654b0
- **短哈希**：3c7144027
- **日期**：2024-06-18
- **作者**：DaqianLiao <360989637@qq.com>
- **提交说明**：Core: Simplify newTableMetadata method in TableMetadata (#10528)
- **PR/Issue**：#10528

## 总体目的
本提交是一次针对 `TableMetadata.newTableMetadata` 重载方法的代码简化重构，目的是消除一个三参数重载方法内部与五参数重载方法之间的逻辑重复。

原始的三参数重载 `newTableMetadata(Schema, PartitionSpec, String location, Map properties)` 内部手工完成了三件事：构造 `SortOrder.unsorted()`、用 `PropertyUtil.propertyAsInt` 从 properties 中读取 `format-version`、调用 `persistedProperties(properties)` 处理属性，然后再把这些结果传给六参数的「真正干活」的重载。然而项目里还存在一个五参数重载 `newTableMetadata(Schema, PartitionSpec, SortOrder, String location, Map properties)`，它内部已经完整地包含了「读取 format-version + 调用 persistedProperties + 转发到六参数重载」这套逻辑。

这意味着三参数重载其实是在重复实现五参数重载已经封装好的流程，属于典型的代码重复。本次提交将三参数重载简化为一行委托调用：直接把 `SortOrder.unsorted()` 传给五参数重载，让后者统一负责 format-version 提取与 persistedProperties 处理。

此次修改属于纯重构，不改变任何运行时行为，对外公共 API 签名保持不变。

## 如何达成设计目的
提交的实现非常直接：将三参数 `newTableMetadata` 方法体替换为单行 `return newTableMetadata(schema, spec, SortOrder.unsorted(), location, properties);`，删除原本的局部变量 `sortOrder`、`formatVersion` 以及对 `persistedProperties` 的显式调用。

之所以这种简化是安全的，关键在于五参数重载内部做了完全相同的事：它先用 `PropertyUtil.propertyAsInt(properties, TableProperties.FORMAT_VERSION, DEFAULT_TABLE_FORMAT_VERSION)` 读取 format-version，再调用 `persistedProperties(properties)` 处理属性，最后转发给六参数重载。这与原三参数重载手工完成的步骤逐一对应，因此替换前后语义等价。

简化后的代码更清晰地表达了「三参数重载 = 无排序的五参数重载」这一意图，未来如果五参数重载的内部流程发生变化（例如对 properties 做新的预处理），三参数重载也会自动跟随，不会再出现两边逻辑漂移的风险。

## 修改详情
### `core/src/main/java/org/apache/iceberg/TableMetadata.java`
**修改目的**：消除三参数 `newTableMetadata` 重载与五参数重载之间的逻辑重复。
**工作逻辑**：原方法体为：
```java
SortOrder sortOrder = SortOrder.unsorted();
int formatVersion =
    PropertyUtil.propertyAsInt(
        properties, TableProperties.FORMAT_VERSION, DEFAULT_TABLE_FORMAT_VERSION);
return newTableMetadata(
    schema, spec, sortOrder, location, persistedProperties(properties), formatVersion);
```
改写后为：
```java
return newTableMetadata(schema, spec, SortOrder.unsorted(), location, properties);
```
- 不再在调用点构造 `sortOrder` 局部变量，而是直接把 `SortOrder.unsorted()` 作为参数内联传入。
- 不再手工调用 `PropertyUtil.propertyAsInt` 提取 format-version，也不再手工调用 `persistedProperties(properties)`，这两步交给五参数重载内部完成。
- 五参数重载内部已经依次执行：读取 format-version、调用 `persistedProperties(properties)`、转发到六参数重载，因此语义完全等价。
- 净变更：1 行新增、6 行删除，方法签名与返回类型保持不变。

## 小结
- **成效**：消除了 `TableMetadata` 中两处 `newTableMetadata` 重载之间的逻辑重复，使三参数重载回归为「无排序的快捷方式」，代码可读性与可维护性提升；未来五参数重载的内部流程变更会自动传递到三参数重载，避免逻辑漂移。
- **影响范围**：仅修改 `core/src/main/java/org/apache/iceberg/TableMetadata.java` 一个文件、一个方法体；不改变公共 API、不改变运行时行为，对调用方完全透明。
- **回迁注意事项**：回迁到 1.4.x 风险极低，因为这是纯粹的内部重构。需确认 1.4.x 上的五参数重载确实包含「读取 format-version + persistedProperties」逻辑——若 1.4.x 上五参数重载的内部实现与 main 不同（例如 `persistedProperties` 行为有差异），则需先核对两边语义是否一致再回迁。若 1.4.x 上 `persistedProperties` 的默认值（如 `PARQUET_COMPRESSION_DEFAULT_SINCE_1_4_0`）与 main 不同，也要一并核对。总体上这是一个低风险、可独立回迁的小提交。
