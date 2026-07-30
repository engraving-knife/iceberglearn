# 提交 3777：Core: Fix SerializableTable.sortOrders() throwing on historical sort orders with dropped fields (#16519) (#16521)

## 提交信息

- **序号**：3777 / 4088
- **哈希**：988d6cd6f31a58e86d58b2e442a2a0afdf4c5aca
- **短哈希**：988d6cd6f
- **日期**：2026-05-24 06:56:48 -0700
- **作者**：Yong Zheng
- **提交说明**：Core: Fix SerializableTable.sortOrders() throwing on historical sort orders with dropped fields (#16519) (#16521)
- **PR/Issue**：#16519, #16521

## 总体目的

这个提交修复了 `SerializableTable.sortOrders()` 在表有历史排序订单（sort order）引用已删除字段时抛出异常的 bug。

问题场景：当表的历史排序订单引用了一个后来被删除的列时，`SerializableTable` 在反序列化排序订单时会调用 `SortOrderParser.fromJson(schema(), json)` 来解析 JSON。这个方法内部会调用 `bind(schema)` 将排序订单绑定到当前 schema，但如果排序订单引用的列已不存在，`bind` 会抛出异常。

`SerializableTable` 在序列化时保存了所有历史排序订单的 JSON，在反序列化时需要全部解析。只要有一个历史排序订单引用了已删除的列，整个 `sortOrders()` 就会失败。修复方案是在解析时传入 `defaultSortOrderId`，使解析器知道哪个是当前默认排序订单，从而在 `bind` 失败时能够正确处理（跳过绑定而非抛出异常）。

## 如何达成设计目的

1. 在 `SerializableTable` 中新增 `defaultSortOrderId` 字段，序列化时保存当前默认排序订单 ID。
2. 反序列化时使用新增的 `SortOrderParser.fromJson(schema, json, defaultSortOrderId)` 重载方法。
3. 在 `SortOrderParser` 中新增接受 `defaultSortOrderId` 参数的 `fromJson` 方法，利用已有的 `fromJson(schema, node, defaultSortOrderId)` 逻辑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SerializableTable.java` (+4/-1 lines)

**修改目的**：保存默认排序订单 ID，反序列化时传递给解析器。

**工作逻辑**：
1. 新增字段 `private final int defaultSortOrderId;`。
2. 在构造函数中保存：`this.defaultSortOrderId = table.sortOrder().orderId();`。
3. 在 `lazyLoadSortOrders()` 方法中，将 `SortOrderParser.fromJson(schema(), json)` 改为 `SortOrderParser.fromJson(schema(), json, defaultSortOrderId)`。

这样解析器在绑定 schema 时知道当前默认排序订单 ID，可以正确处理引用已删除字段的历史排序订单（不会影响默认排序订单的绑定）。

### `core/src/main/java/org/apache/iceberg/SortOrderParser.java` (+4/-0 lines)

**修改目的**：新增接受默认排序订单 ID 的 JSON 解析方法。

**工作逻辑**：
```java
public static SortOrder fromJson(Schema schema, String json, int defaultSortOrderId) {
  return JsonUtil.parse(json, node -> fromJson(schema, node, defaultSortOrderId));
}
```
新增方法委托给已有的 `fromJson(Schema, JsonNode, int)` 重载，该方法在绑定 schema 时会利用 `defaultSortOrderId` 判断当前排序订单是否为默认订单，对非默认的历史订单允许引用已不存在的字段。

### `core/src/test/java/org/apache/iceberg/hadoop/TestTableSerialization.java` (+16/-0 lines)

**修改目的**：测试历史排序订单引用已删除列时的序列化。

**工作逻辑**：
新增 `testSerializableTableSortOrdersWithDroppedColumn` 测试：
1. 添加列 `ts`，创建排序订单 1 引用 `id` 和 `ts`。
2. 替换排序订单为只引用 `id`（创建排序订单 2），然后删除 `ts` 列。
3. 此时历史排序订单 1 引用了已删除的 `ts` 列。
4. 验证 `TestHelpers.assertSerializedAndLoadedMetadata` 和 Kryo 序列化都能正确处理，不抛出异常。

## 总结

这个提交修复了 `SerializableTable` 在有序列化历史排序订单引用已删除字段时抛出异常的问题。通过保存和传递 `defaultSortOrderId`，使解析器能够正确区分默认排序订单和历史排序订单，对历史订单中引用已删除字段的情况进行容错处理。这对表 schema 演进场景下的序列化可靠性很重要。
