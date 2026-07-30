# 提交 1187：Core: Remove unused code for streaming position deletes (#11175)

## 提交信息

- **序号**：1187 / 4088
- **哈希**：b1d38b3cac326da296b770c8ab9219bc4f791666
- **短哈希**：b1d38b3ca
- **日期**：2024-09-25（Wed Sep 25 17:34:30 2024 -0700）
- **作者**：Wing Yew Poon <wypoon@cloudera.com>
- **提交说明**：Core: Remove unused code for streaming position deletes (#11175)
- **PR/Issue**：#11175

## 总体目的

Iceberg 的 `Deletes` 工具类中曾有一套"流式位置删除"实现，包括 `streamingFilter` 和 `streamingMarker` 两个公共方法，以及配套的私有内部类 `PositionStreamDeleteIterable`、`PositionStreamDeleteFilter`、`PositionStreamDeleteMarker`。这套实现的设计思路是：在遍历数据行流的同时，维护一个删除位置迭代器，**利用"数据行位置与删除位置都升序"的前提**，通过双指针推进来逐行判断是否被删除，从而避免把所有删除位置一次性物化到内存。

但 Iceberg 的实际删除应用流程早已转向"先构建 `PositionDeleteIndex`（位图），再基于位图判断行是否被删除"的模式（即 `filterDeleted` / `markDeleted` 配合 `PositionDeleteIndex.isDeleted`）。`streamingFilter` / `streamingMarker` 这两个方法在主代码路径中**已无调用方**，仅被 `TestPositionFilter` 中针对它们的专项测试引用。也就是说，这套流式实现是"死代码 + 自维护测试"的组合，徒增维护负担与认知成本。

本提交的目的是：(1) 删除 `streamingFilter` / `streamingMarker` 的内部流式实现（`PositionStreamDeleteIterable` 及两个子类），但**保留方法签名并标记 `@Deprecated`**，将其内部实现改为委托给基于 `PositionDeleteIndex` 的现有方法（`filterDeleted` / `markDeleted`），以保持向后兼容；(2) 删除针对流式实现的专项测试；(3) 清理因此变得无用的 import 与 logger。

## 如何达成设计目的

1. **保留公共方法签名但改写实现**：`streamingFilter` 和 `streamingMarker` 的两个重载仍存在，但内部不再实例化 `PositionStreamDeleteFilter` / `PositionStreamDeleteMarker`，而是先调用 `toPositionIndex(posDeletes)` 把删除位置流物化为 `PositionDeleteIndex`，再委托给 `filterDeleted(rows, isDeleted, counter)` 或 `markDeleted(rows, isDeleted, markRowDeleted)`。这样旧调用方仍可工作，但底层走的是与主路径一致的位图实现。
2. **标记为 `@Deprecated`**：在两个 `streamingFilter` 重载和 `streamingMarker` 重载上添加 `@Deprecated` 与 JavaDoc `@deprecated since 1.7.0, will be removed in 1.8.0.`，明确告知用户这些方法将在 1.8.0 移除，引导迁移到 `filterDeleted` / `markDeleted`。
3. **删除私有内部类**：移除 `PositionStreamDeleteIterable`（抽象基类，含 `isDeleted` 双指针逻辑与 `applyDelete` 抽象方法）、`PositionStreamDeleteFilter`（用 `FilterIterator` 过滤被删除行）、`PositionStreamDeleteMarker`（用自定义 `CloseableIterator` 标记被删除行）三个内部类。
4. **删除专项测试**：移除 `TestPositionFilter` 中针对流式实现的 5 个测试方法（`testPositionStreamRowFilter`、`testPositionStreamRowDeleteMarker`、`testPositionStreamRowFilterWithDuplicates`、`testPositionStreamRowFilterWithRowGaps`、`testCombinedPositionStreamRowFilter`、`testClosePositionStreamRowDeleteMarker`、`testDeleteMarkerFileClosed`）以及辅助内部类 `CheckingClosableIterable`。
5. **清理无用 import**：移除 `CloseableGroup`、`CloseableIterator`、`FilterIterator`、`Logger`、`LoggerFactory` 等 import；同时移除 `TestPositionFilter` 中不再使用的 `IOException`、`Iterator`、`AtomicBoolean`、`CloseableIterator` 等 import。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/Deletes.java`

**修改目的**：删除流式实现、改写公共方法为委托实现、标记废弃。

**工作逻辑**：
- 移除 import：`org.apache.iceberg.io.CloseableGroup`、`org.apache.iceberg.io.CloseableIterator`、`org.apache.iceberg.io.FilterIterator`、`org.slf4j.Logger`、`org.slf4j.LoggerFactory`。
- 移除类级 logger：`private static final Logger LOG = LoggerFactory.getLogger(Deletes.class);`。
- `streamingFilter(rows, rowToPosition, posDeletes)` 重载：加 `@Deprecated` + JavaDoc，实现改为：
  ```java
  PositionDeleteIndex positionIndex = toPositionIndex(posDeletes);
  Predicate<T> isDeleted = row -> positionIndex.isDeleted(rowToPosition.apply(row));
  return filterDeleted(rows, isDeleted, counter);
  ```
  即把删除位置流物化为位图，再用 `filterDeleted` 过滤。
- `streamingFilter(rows, rowToPosition, posDeletes, counter)` 重载：同样 `@Deprecated` + 委托实现。
- `streamingMarker(rows, rowToPosition, posDeletes, markRowDeleted)` 重载（参数名从 `markDeleted` 改为 `markRowDeleted` 避免与静态方法 `markDeleted` 同名混淆）：`@Deprecated` + 委托到 `markDeleted(rows, isDeleted, markRowDeleted)`。
- 删除三个私有内部类：`PositionStreamDeleteIterable<T>`（抽象基类，含双指针 `isDeleted` 逻辑、`nextDeletePos` 字段、`applyDelete` 抽象方法）、`PositionStreamDeleteFilter<T>`（继承前者，用 `FilterIterator` 实现 `shouldKeep`）、`PositionStreamDeleteMarker<T>`（继承前者，自定义 `CloseableIterator` 在 `next()` 中调用 `markDeleted.accept(row)`）。共删除约 142 行。

### `core/src/test/java/org/apache/iceberg/deletes/TestPositionFilter.java`

**修改目的**：删除针对流式实现的专项测试。

**工作逻辑**：
- 移除 import：`java.io.IOException`、`java.util.Iterator`、`java.util.concurrent.atomic.AtomicBoolean`、`org.apache.iceberg.io.CloseableIterator`。
- 删除 5 个测试方法：
  - `testPositionStreamRowFilter`：基础流式过滤。
  - `testPositionStreamRowDeleteMarker`：流式标记删除。
  - `testPositionStreamRowFilterWithDuplicates`：删除位置含重复值的流式过滤。
  - `testPositionStreamRowFilterWithRowGaps`：数据行位置大于删除位置的边界情况。
  - `testCombinedPositionStreamRowFilter`：多个 position delete 文件合并后的流式过滤。
  - `testClosePositionStreamRowDeleteMarker`：验证 `streamingFilter` 迭代器关闭时数据源与删除位置源都被关闭。
  - `testDeleteMarkerFileClosed`：验证 `streamingMarker` 的迭代器关闭行为。
- 删除辅助内部类 `CheckingClosableIterable<E>`：用于检测 `close()` 是否被调用的测试夹具。
- 保留 `testPositionSetRowFilter` 等基于 `PositionDeleteIndex` 的测试（这些是主路径测试，不受影响）。
- 共删除约 254 行。

## 小结

- **成效**：移除了一套约 142 行的死代码（流式位置删除的三个内部类）与约 254 行的对应测试，降低了 `Deletes` 类的认知与维护成本。公共方法 `streamingFilter` / `streamingMarker` 通过委托 `filterDeleted` / `markDeleted` 保持向后兼容，并标记 `@Deprecated`（1.7.0 废弃，1.8.0 移除）引导用户迁移。底层实现统一为基于 `PositionDeleteIndex` 的位图路径。
- **影响范围**：核心 deletes 包的 1 个 Java 源文件 + 1 个测试文件，净删除约 387 行、新增约 19 行（主要是 `@Deprecated` JavaDoc 与委托实现）。属于代码清理，**对外 API 仍保留但语义上从"流式"变为"物化后过滤"**。
- **回迁到 1.4.x 的注意事项**：这是一个代码清理提交，**回迁价值不高**。1.4.x 作为维护分支应以稳定性优先，不建议引入这种"内部实现改写"的变更，除非 1.4.x 中确有 `streamingFilter` / `streamingMarker` 的调用方且其行为差异（流式 vs 物化）会造成问题——这种情况下才需要回迁。需注意：(1) 改写后 `streamingFilter` 内部会调用 `toPositionIndex(posDeletes)`，这会把删除位置流**全量物化到内存**（位图），与原流式实现的内存特性不同，对于删除位置极多的场景可能增加内存占用，但实际场景下问题不大；(2) `@Deprecated` 标记是 1.7.0 才引入的语义，若 1.4.x 早于 1.7.0，回迁时可将 JavaDoc 中的版本号调整为 1.4.x 对应版本；(3) 删除测试是安全的，因为这些测试只验证被废弃方法的旧实现，新委托实现由 `filterDeleted` / `markDeleted` 的现有测试覆盖。**总体建议：不回迁**，除非有具体问题驱动。
