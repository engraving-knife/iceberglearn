# 提交 1184：Core: Support iterating over positions in PositionDeleteIndex (#11202)

## 提交信息

- **序号**：1184 / 4088
- **哈希**：474a770aa08abc713282709427a59dca95a35a4c
- **短哈希**：474a770aa
- **日期**：2024-09-25（Wed Sep 25 08:30:56 2024 -0700）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Support iterating over positions in PositionDeleteIndex (#11202)
- **PR/Issue**：#11202

## 总体目的

`PositionDeleteIndex` 是 Iceberg 用于记录"被位置删除（position delete）的行号集合"的核心抽象，主要实现 `BitmapPositionDeleteIndex`（基于 Roaring64Bitmap）。此前该接口只提供按位查询（`isDeleted(position)`）、添加、合并、判空等能力，但**无法遍历索引中所有被删除的行位置**。

在某些场景下（例如需要将位置删除索引物化、合并到其他结构、做差集运算、或将删除信息序列化输出），需要能够按升序遍历索引中的所有位置。本提交为 `PositionDeleteIndex` 接口新增 `forEach(LongConsumer)` 方法，并在 `BitmapPositionDeleteIndex` 中利用 Roaring64Bitmap 的原生 `forEach` 实现高效升序遍历。

这是后续支持 `PositionDeleteIndex` 合并、删除文件重写等特性的基础能力之一（参见紧随其后的 #11208 提交）。

## 如何达成设计目的

1. 在 `PositionDeleteIndex` 接口中新增 `default void forEach(LongConsumer consumer)` 方法。默认实现采取保守策略：若索引非空则抛 `UnsupportedOperationException`，提示该实现未支持遍历；空索引则无操作。这样既给具体实现提供了扩展点，又保证老实现（如 `EmptyPositionDeleteIndex`）默认行为正确——空索引无需遍历。
2. 在 `BitmapPositionDeleteIndex` 中覆写 `forEach`，委托给底层 `Roaring64Bitmap.forEach(consumer::accept)`。Roaring64Bitmap 自身的 `forEach` 会按容器（container）顺序、容器内升序输出，因此能保证输出升序。
3. 新增单元测试 `TestBitmapPositionDeleteIndex`，覆盖：跨容器边界的位置按任意顺序插入后仍按升序输出；空位图索引的遍历输出空；空索引（`PositionDeleteIndex.empty()`）的遍历输出空。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/PositionDeleteIndex.java`

**修改目的**：在接口层声明 `forEach` 遍历能力。

**工作逻辑**：
- 新增 `import java.util.function.LongConsumer;`。
- 新增 `default void forEach(LongConsumer consumer)` 方法，JavaDoc 说明"按升序遍历索引中所有位置并应用 consumer"。
- 默认实现：
  ```java
  default void forEach(LongConsumer consumer) {
    if (isNotEmpty()) {
      throw new UnsupportedOperationException(getClass().getName() + " does not support forEach");
    }
  }
  ```
  即只有空索引会无操作返回，非空索引若未实现 `forEach` 则抛异常，强迫子类显式覆写。

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：为位图实现提供高效的升序遍历。

**工作逻辑**：
- 新增 `import java.util.function.LongConsumer;`。
- 覆写 `forEach`：
  ```java
  @Override
  public void forEach(LongConsumer consumer) {
    roaring64Bitmap.forEach(consumer::accept);
  }
  ```
  `Roaring64Bitmap.forEach(LongConsumer)` 是其原生 API，会按桶（bucket）与容器内位序升序输出 64 位 long 值，因此遍历结果天然升序。

### `core/src/test/java/org/apache/iceberg/deletes/TestBitmapPositionDeleteIndex.java`（新文件）

**修改目的**：验证 `forEach` 的升序输出与边界情况。

**工作逻辑**：
- `testForEach()`：构造 6 个跨容器边界的位置（高 32 位分别为 0、1、2、3），按乱序插入位图索引；调用 `forEach` 收集到列表，断言输出严格按升序等于 `[pos1, pos2, pos3, pos4, pos5, pos6]`。这验证了 Roaring64Bitmap 跨容器遍历仍能保持整体升序。
- `testForEachEmptyBitmapIndex()`：对新建（空）的 `BitmapPositionDeleteIndex` 调用 `forEach`，断言收集结果为空。
- `testForEachEmptyIndex()`：对 `PositionDeleteIndex.empty()`（单例空实现）调用 `forEach`，断言收集结果为空。这同时验证了接口默认实现的安全行为（空索引不抛异常）。
- 私有辅助方法 `collect(PositionDeleteIndex)` 用 `positions::add` 作为 `LongConsumer` 收集到 `List<Long>`。

## 小结

- **成效**：`PositionDeleteIndex` 现可通过 `forEach` 按升序遍历所有被删除位置，`BitmapPositionDeleteIndex` 利用底层 Roaring64Bitmap 原生能力实现高效遍历。接口默认实现保证空索引安全无操作、未实现遍历的非空索引显式抛异常。
- **影响范围**：核心 deletes 包的 2 个 Java 文件 + 1 个新测试文件，共新增约 91 行。属于纯增量 API，不破坏现有调用。
- **回迁到 1.4.x 的注意事项**：这是为后续 `PositionDeleteIndex` 合并特性（#11208）做铺垫的接口扩展。**单独回迁价值有限**——若 1.4.x 不打算引入 `forEach` 的下游使用方（如合并、重写、序列化），则无需回迁。若 1.4.x 需要回迁 #11208 的合并能力，则必须先回迁本提交（因为 #11208 依赖 `forEach`）。回迁时接口默认实现的设计保证了与现有 `PositionDeleteIndex` 实现的兼容性，无破坏性变更，可放心带回。
