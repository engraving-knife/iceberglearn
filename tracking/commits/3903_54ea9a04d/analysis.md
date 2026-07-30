# 提交 3903：Kafka Connect: Pre-size collections in RecordConverter list/map conversion (#16657)

## 提交信息

- **序号**：3903 / 4088
- **哈希**：54ea9a04d287f21bca16c5a1d40bb20e332bd864
- **短哈希**：54ea9a04d
- **日期**：2026-06-18 17:25:56 +0200
- **作者**：Vova Kolmakov
- **提交说明**：Kafka Connect: Pre-size collections in RecordConverter list/map conversion (#16657)
- **PR/Issue**：#16657

## 总体目的

优化 `RecordConverter` 中 list 和 map 转换的性能。此前，在将 Kafka Connect 的 Struct 值转换为 Iceberg Record 时，list 转换使用 `Collectors.toList()`（默认初始容量 16），map 转换使用 `Maps.newHashMap()`（默认初始容量 16）。当输入 list/map 大小与默认容量差异较大时，会导致不必要的扩容（数组重分配和复制）。

对于 Kafka Connect 场景，每条记录都要经过转换，大量小规模 list/map 的转换开销会累积。通过预分配正确大小的集合，可以消除扩容开销，提升吞吐量。同时，原代码在 lambda 内部重复访问 `type.fields()` 和类型信息，提取到循环外可以减少重复计算。

## 如何达成设计目的

将 stream + collect 模式改为预分配容量的 for 循环，将字段 ID 和类型信息提取到循环外部。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordConverter.java` (+17/-15 lines)

**修改目的**：预分配集合容量并提取循环不变量。

**工作逻辑**：

1. **list 转换优化**：
```java
// 旧：stream + Collectors.toList()
return list.stream()
    .map(element -> {
      int fieldId = type.fields().get(0).fieldId();
      return convertValue(element, type.elementType(), fieldId, schemaUpdateConsumer);
    })
    .collect(Collectors.toList());

// 新：预分配容量 + for 循环
int elementFieldId = type.fields().get(0).fieldId();
Type elementType = type.elementType();
List<Object> result = Lists.newArrayListWithCapacity(list.size());
for (Object element : list) {
  result.add(convertValue(element, elementType, elementFieldId, schemaUpdateConsumer));
}
return result;
```

2. **map 转换优化**：
```java
// 旧：Maps.newHashMap() + lambda 内重复获取字段
Map<Object, Object> result = Maps.newHashMap();
map.forEach((k, v) -> {
  int keyFieldId = type.fields().get(0).fieldId();
  int valueFieldId = type.fields().get(1).fieldId();
  result.put(
      convertValue(k, type.keyType(), keyFieldId, schemaUpdateConsumer),
      convertValue(v, type.valueType(), valueFieldId, schemaUpdateConsumer));
});

// 新：预分配容量 + 提取到循环外
int keyFieldId = type.fields().get(0).fieldId();
int valueFieldId = type.fields().get(1).fieldId();
Type keyType = type.keyType();
Type valueType = type.valueType();
Map<Object, Object> result = Maps.newHashMapWithExpectedSize(map.size());
map.forEach((k, v) ->
    result.put(
        convertValue(k, keyType, keyFieldId, schemaUpdateConsumer),
        convertValue(v, valueType, valueFieldId, schemaUpdateConsumer)));
```

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/TestRecordConverter.java` (+15/-0 lines)

**修改目的**：添加空 list/map 转换测试。

**工作逻辑**：
```java
@Test
public void testEmptyListAndMapConvert() {
  // ...
  data.put("li", ImmutableList.of());
  data.put("ma", ImmutableMap.of());
  Record record = converter.convert(data);
  assertThat((List<?>) record.getField("li")).isEmpty();
  assertThat((Map<?, ?>) record.getField("ma")).isEmpty();
}
```
验证空集合的转换不会出错且结果为空集合。预分配容量为 0 时 Guava 的 `newArrayListWithCapacity(0)` 和 `newHashMapWithExpectedSize(0)` 都能正确处理。

## 总结

通过预分配集合容量和提取循环不变量优化了 RecordConverter 的 list/map 转换性能，消除了不必要的扩容开销和重复字段访问。优化对 Kafka Connect 高吞吐场景下的每条记录转换都有积极影响。同时新增了空集合转换的边界测试，确保预分配容量为 0 时的正确性。

该提交由 Vova Kolmakov 和 Claude Opus 4.8 协作完成，体现了 AI 辅助开发在性能优化场景中的应用。
