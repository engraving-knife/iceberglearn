# 提交 3839：API, Core: Implement filter() for partition statistics scan API (#16582)

## 提交信息

- **序号**：3839 / 4088
- **哈希**：34e812e18a2522b4fbf612551cd6e2e6865a2270
- **短哈希**：34e812e18
- **日期**：2026-06-08 13:31:16 +0200
- **作者**：gaborkaszab
- **提交说明**：API, Core: Implement filter() for partition statistics scan API (#16582)
- **PR/Issue**：#16582

## 总体目的

本提交为分区统计扫描 API（`PartitionStatisticsScan`）实现了 `filter()` 方法的真正功能。在此之前，`BasePartitionStatisticsScan.filter()` 方法直接抛出 `UnsupportedOperationException("Filtering is not supported")`，意味着用户无法在扫描分区统计数据时进行过滤，必须读取全部分区统计记录然后在客户端自行过滤。

随着 Iceberg 分区统计功能（Partition Statistics）的成熟和实际使用场景增多，用户可能拥有大量分区但只关心其中一部分（例如某个特定分区、满足某些条件的分区）。每次都读取全部统计文件并在内存中过滤既浪费 I/O 也浪费内存。本提交通过支持基于 `Expression` 的过滤，让用户可以在扫描层面就过滤掉不需要的分区统计记录，从而显著提升查询效率并减少资源消耗。

同时，本提交还新增了 `caseSensitive(boolean)` 方法，允许用户控制过滤表达式中列名匹配是否区分大小写，提供了更灵活的查询选项。

## 如何达成设计目的

整体设计思路如下：

1. **API 层扩展**：在 `PartitionStatisticsScan` 接口中新增 `caseSensitive(boolean)` 默认方法（默认抛出 `UnsupportedOperationException`，保持向后兼容）。
2. **Core 层实现**：在 `BasePartitionStatisticsScan` 中：
   - 将 `filter` 字段从无实现改为存储传入的过滤表达式（默认 `alwaysTrue()`）。
   - 实现 `caseSensitive(boolean)` 方法以存储大小写敏感配置。
   - 在 `scan()` 方法中，使用 `Evaluator` 对每条分区统计记录进行过滤。
   - 处理投影（projection）与过滤器的交互：当用户设置了投影但又使用了过滤器时，需要确保过滤器引用的列也被读取（即使不在投影中），否则过滤无法执行。
3. **测试覆盖**：新增了大量测试用例覆盖各种过滤场景，包括 null 过滤器、alwaysTrue/alwaysFalse、分区列过滤、统计字段过滤、未知列报错、投影与过滤组合、大小写敏感等。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionStatisticsScan.java` (+11/-0 lines)

**修改目的**：在接口中新增 `caseSensitive` 方法。

**工作逻辑**：
新增默认方法 `caseSensitive(boolean)`，默认抛出 `UnsupportedOperationException`，使得该方法是可选实现，保持接口向后兼容：
```java
default PartitionStatisticsScan caseSensitive(boolean caseSensitive) {
  throw new UnsupportedOperationException("caseSensitive is not supported");
}
```

### `core/src/main/java/org/apache/iceberg/BasePartitionStatisticsScan.java` (+57/-7 lines)

**修改目的**：实现 filter 与 caseSensitive 的真正逻辑。

**工作逻辑**：

1. **新增字段**：新增 `filter`（默认 `Expressions.alwaysTrue()`）和 `caseSensitive`（默认 `true`）字段。

2. **实现 filter()**：将原来抛异常改为存储过滤器：
```java
@Override
public PartitionStatisticsScan filter(Expression newFilter) {
  Preconditions.checkArgument(newFilter != null, "Invalid filter: null");
  this.filter = newFilter;
  return this;
}
```

3. **实现 caseSensitive()**：
```java
@Override
public PartitionStatisticsScan caseSensitive(boolean newCaseSensitive) {
  this.caseSensitive = newCaseSensitive;
  return this;
}
```

4. **在 scan() 中应用过滤**：读取数据后，如果过滤器不是 `alwaysTrue()`，使用 `Evaluator` 对每条记录进行评估并过滤：
```java
if (filter != Expressions.alwaysTrue()) {
  Evaluator evaluator = new Evaluator(readSchema.asStruct(), filter, caseSensitive);
  result = CloseableIterable.filter(result, evaluator::eval);
}
```

5. **处理投影与过滤的交互**：新增 `readSchema(Schema)` 私有方法。当用户设置了投影时，读取的 schema 不仅要包含投影字段，还要包含过滤器引用的字段，否则 `Evaluator` 无法评估：
```java
private Schema readSchema(Schema schema) {
  if (projection == null) {
    return schema;
  }
  Set<Integer> fieldIdsToRead = Sets.newHashSet();
  fieldIdsToRead.addAll(TypeUtil.getProjectedIds(projection));
  fieldIdsToRead.addAll(
      Binder.boundReferences(
          schema.asStruct(), Collections.singletonList(filter), caseSensitive));
  return TypeUtil.select(schema, fieldIdsToRead);
}
```
使用 `Binder.boundReferences()` 解析过滤器中引用的字段 ID，将其加入读取集合，确保过滤条件能正常工作。

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsScanTestBase.java` (+309/-0 lines)

**修改目的**：为新增的过滤功能添加全面的测试覆盖。

**工作逻辑**：

新增了以下测试用例：

- `testNullFilter`：验证传入 null 过滤器会抛出 `IllegalArgumentException`。
- `testAlwaysTrueFilter` / `testAlwaysFalseFilter`：验证恒真/恒假过滤器的行为。
- `testFilterOnPartitionColumn`：验证基于分区列（如 `partition.c2`、`partition.c3`）的过滤。
- `testFilterOnStatsField`：验证基于统计字段（如 `data_file_count > 2`）的过滤。
- `testFilterOnUnknownColumnFails` / `testFilterOnUnknownPartitionSubFieldFails`：验证引用不存在列时会抛出 `ValidationException`。
- `testFilterDvCountOnV2Stats`：验证在 V2 统计中过滤 `dv_count` 字段会报错（因为该字段在 V2 不存在）。
- `testFilterColumnIncludedInProjection`：验证过滤器引用的列在投影中时的行为。
- `testFilterColumnNotInProjection`：验证过滤器引用的列不在投影中时，仍能正确过滤（因为 readSchema 会自动包含过滤字段）。
- `testCaseSensitiveFilterFailsOnUppercaseRef`：验证大小写敏感模式下用大写列名会报错。
- `testCaseInsensitiveFilterMatchesUppercaseRef`：验证大小写不敏感模式下用大写列名能匹配。

还新增了两个辅助方法 `tableWithTwoPartitions` 和 `tableWithUnevenPartitions` 用于创建测试表（前者创建两个等量分区，后者创建分区 A 有 3 个文件、分区 B 有 1 个文件的不均匀表，用于测试基于 `data_file_count` 的过滤）。

## 总结

本提交是一个功能增强，为分区统计扫描 API 补齐了过滤能力。设计上充分考虑了与投影功能的交互（确保过滤字段被读取）和大小写敏感配置，并提供了全面的测试覆盖。这使分区统计功能的实用性大幅提升，用户可以高效地查询特定分区的统计数据而无需读取全部统计文件，对拥有大量分区的表的查询性能优化尤其重要。
