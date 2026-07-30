# 提交 1320：Core: Add cardinality to PositionDeleteIndex (#11442)

## 提交信息

- **序号**：1320 / 4088
- **哈希**：8b4ebc66e1d34dc345f8370c341720cbda98f877
- **短哈希**：8b4ebc66e
- **日期**：2024-11-02（Sat Nov 2 10:06:12 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Add cardinality to PositionDeleteIndex (#11442)
- **PR/Issue**：#11442

## 总体目的

`PositionDeleteIndex` 是 Iceberg 在读取侧用来表示"位置删除索引"的接口：对一个数据文件中被删除的行号集合提供 `delete(pos)`、`isDeleted(pos)`、`isEmpty()`、`forEach(consumer)`、`deleteFiles()` 等查询。前一个提交（1318 #11441）刚把 `BitmapPositionDeleteIndex` 的内部实现从 `Roaring64Bitmap` 切换到自研的 `RoaringPositionBitmap`，而 `RoaringPositionBitmap` 自提交 #11372 起就提供了 `cardinality()` 方法（返回位图中已置位的元素个数）。

但是 `PositionDeleteIndex` 接口本身并没有暴露"被删除位置数"这一信息。下游（比如读取侧统计、计划决策、未来的 deletion vector 协议）想拿到一个数据文件实际被删除的行数时，需要绕路或重新计数。

本提交在接口层面新增 `cardinality()` 方法，作为默认方法抛出 `UnsupportedOperationException`（保证既有实现不破），并在 `BitmapPositionDeleteIndex` 中实现为 `bitmap.cardinality()` 的直接代理。这是为后续 deletion vector / puffin 等特性铺路的小步骤改动。

## 如何达成设计目的

1. 在 `PositionDeleteIndex` 接口中新增 default 方法：
   ```java
   /** Returns the cardinality of this index. */
   default long cardinality() {
     throw new UnsupportedOperationException(getClass().getName() + " does not support cardinality");
   }
   ```
   作为 default 方法抛 `UnsupportedOperationException`，与接口中既有的 `forEach`、`merge`、`deleteFiles` 等 default 方法的"不支持时抛异常"模式保持一致。
2. 在 `BitmapPositionDeleteIndex` 中 override：
   ```java
   @Override
   public long cardinality() {
     return bitmap.cardinality();
   }
   ```
   直接代理给底层 `RoaringPositionBitmap.cardinality()`。

这种"接口加 default 抛异常 + 主实现 override 提供真值"的模式，与 `forEach`、`merge`、`deleteFiles` 的处理方式一致，既向后兼容（其他实现不破），又能让需要该能力的能力被暴露出来。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/PositionDeleteIndex.java`

**修改目的**：在接口上声明 `cardinality()`。

**工作逻辑**：在 `deleteFiles()` default 方法之后、`empty()` 静态工厂之前插入：

```java
/** Returns the cardinality of this index. */
default long cardinality() {
  throw new UnsupportedOperationException(getClass().getName() + " does not support cardinality");
}
```

返回类型为 `long`（而非 `int`），以适应未来可能非常大的位置删除集合（理论上一个表可能有超过 2^31 个被删除的位置）。异常消息中带上 `getClass().getName()`，方便调用方定位是哪个实现不支持。

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：为位图实现提供真实的 cardinality。

**工作逻辑**：在类末尾追加：

```java
@Override
public long cardinality() {
  return bitmap.cardinality();
}
```

底层 `RoaringPositionBitmap.cardinality()` 实现是遍历内部 `RoaringBitmap[]` 数组累加 `getLongCardinality()`：

```java
public long cardinality() {
  long cardinality = 0L;
  for (RoaringBitmap bitmap : bitmaps) {
    cardinality += bitmap.getLongCardinality();
  }
  return cardinality;
}
```

时间复杂度是 O(数组长度)（每个 32-bit RoaringBitmap 的 `getLongCardinality()` 是 O(1) 的近似/缓存值，并非逐位遍历），调用成本极低。

## 小结

- **成效**：`PositionDeleteIndex` 现可对外暴露"被删除位置数"，便于下游做统计、计划、deletion vector 元数据填写等；以 default 方法形式新增，向后兼容。
- **影响范围**：2 个文件、共 10 行新增。属于纯 API 扩展，不改变既有行为。
- **回迁到 1.4.x 的注意事项**：
  - 这是接口扩展（default 方法），**对 1.4.x 二进制兼容性友好**——既有的 `PositionDeleteIndex` 实现不需要改动即可继续工作；如果调用方在新版调用 `cardinality()` 而实现未 override，会得到 `UnsupportedOperationException`，行为可预期。
  - 回迁前提：`BitmapPositionDeleteIndex` 已切换到 `RoaringPositionBitmap`（即先回迁 1318 #11441），而后者又依赖 #11372。若 1.4.x 仍用 `Roaring64Bitmap`，则要么也用其 `getLongCardinality()` 实现 override，要么先回迁 1318。
  - 注意 `EmptyPositionDeleteIndex` 并未 override `cardinality()`——若调用方对空索引调用此方法会抛 `UnsupportedOperationException`。1.4.x 若回迁此接口扩展并对外暴露给下游，建议同时考虑给 `EmptyPositionDeleteIndex` override 返回 `0L`（本提交未做），以免下游误用空索引时抛异常。但若 1.4.x 仅内部使用，可暂不处理。
  - 风险极低、收益清晰，**建议回迁**，尤其是若 1.4.x 计划支持 deletion vector 相关特性时。
