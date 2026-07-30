# 提交 0189：Data: Always use delete index for position deletes (#9117)

## 提交信息

- **序号**：0189 / 4088
- **哈希**：c61c3ca017aaec145f57948f0da9cb307bf45cde
- **短哈希**：c61c3ca01
- **日期**：2023-11-21 12:20:41 -0800
- **作者**：Anton Okolnychyi
- **提交说明**：Data: Always use delete index for position deletes (#9117)
- **PR/Issue**：#9117

## 总体目的

本提交简化 `iceberg-data` 模块的 [`DeleteFilter.applyPosDeletes`](../../../data/src/main/java/org/apache/iceberg/data/DeleteFilter.java)，移除"按位置删除条数阈值二选一"的策略，改为无论删除条数多少都构建内存 `PositionDeleteIndex`（基于 `Roaring64Bitmap`）来判定行是否被删。

改动前的策略（可追溯到 1772f4f27 引入、8dab33ca8 改用位图后保留）：在 `applyPosDeletes` 中先累加所有 position delete 文件的 `recordCount`，与常量阈值 `DEFAULT_SET_FILTER_THRESHOLD = 100_000L` 比较：

- 小于阈值：调用 `Deletes.toPositionIndex(filePath, deletes)` 构建内存位图，再用 `createDeleteIterable` 按 `positionIndex.isDeleted(pos)` 过滤/标记。
- 大于等于阈值：走流式合并路径——`Deletes.deletePositions(filePath, deletes)` 用 `SortedMerge` 把多个 delete 文件的位置归并成有序流，再用 `Deletes.streamingFilter`（普通过滤）或 `Deletes.streamingMarker`（带 `_deleted` 元数据列时边读边标记）与数据行同步消费，避免一次性把所有删除位置物化进内存。

该阈值是为 `HashSet` 时代设计的内存保护（早期 `Deletes.toPositionSet` 用 `HashSet<Long>` 存位置，百万级会占大量堆内存）。8dab33ca8 已把内存结构换成 `Roaring64Bitmap`（压缩有序位图，稀疏与密集场景都极省内存），但阈值分支被保留。本次提交认为该分支已无必要，理由是：

1. `Roaring64Bitmap` 即便对千万级位置也能高效压缩存储，内存不再是首要瓶颈；
2. 位图提供 O(1) `isDeleted` 查找，且 `deletedRowPositions()`（向量化读取路径用来跳过 row group）本就总是构建位图，统一路径后行为一致；
3. 流式合并要求按位置顺序与数据行同步推进，无法支持随机查找与向量化跳过，且 `SortedMerge` 自身有维护多路归并的开销；
4. 阈值分支使 `_deleted` 列在大删除量时走 `streamingMarker`、小删除量时走 `markDeleted`，两套标记语义需分别维护，增加复杂度。

简化后 `applyPosDeletes` 始终构建位图并经 `createDeleteIterable` 统一处理过滤/标记，代码更清晰，行为可预测，并与向量化读取路径对齐。这是 Iceberg 读取路径上一次"以位图统一位置删除过滤"的收敛建模，体现了 Roaring 位图成熟后对早期内存保护策略的回收。

## 如何达成设计目的

整体设计是"删减分支 + 复用既有方法"：

1. 删除常量 `DEFAULT_SET_FILTER_THRESHOLD` 与字段 `setFilterThreshold`，以及构造函数中对它的赋值——阈值不再被任何分支引用。
2. 把 `applyPosDeletes` 的核心从"if-else 二分"简化为线性三步：打开 position delete 文件、`Deletes.toPositionIndex` 构建位图、`createDeleteIterable` 按位图判定。
3. 复用既有的 `createDeleteIterable(records, isDeleted)`：它内部已根据 `hasIsDeletedColumn` 选择 `Deletes.markDeleted`（写 `_deleted` 标记列）或 `Deletes.filterDeleted`（直接过滤），从而把原流式路径下 `streamingMarker`/`streamingFilter` 的两种行为统一到 bitmap 路径。

`Deletes` 中的 `streamingFilter`、`streamingMarker`、`deletePositions` 未被删除，仍由测试与其他场景使用；只是 `DeleteFilter` 不再调用它们。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/DeleteFilter.java`

**修改目的**：移除 position delete 的阈值分支，统一用 `PositionDeleteIndex`（位图）过滤。

**工作逻辑**：

1. 删除阈值常量与字段：

   原代码：

   ```java
   private static final long DEFAULT_SET_FILTER_THRESHOLD = 100_000L;
   ...
   private final long setFilterThreshold;
   ```

   以及构造函数中：

   ```java
   this.setFilterThreshold = DEFAULT_SET_FILTER_THRESHOLD;
   ```

   全部移除。`setFilterThreshold` 此前不可配置（只在构造函数硬编码赋值），删除后无外部调用方受影响。

2. 简化 `applyPosDeletes`：

   原代码（含阈值二分）：

   ```java
   private CloseableIterable<T> applyPosDeletes(CloseableIterable<T> records) {
     if (posDeletes.isEmpty()) {
       return records;
     }

     List<CloseableIterable<Record>> deletes = Lists.transform(posDeletes, this::openPosDeletes);

     // if there are fewer deletes than a reasonable number to keep in memory, use a set
     if (posDeletes.stream().mapToLong(DeleteFile::recordCount).sum() < setFilterThreshold) {
       PositionDeleteIndex positionIndex = Deletes.toPositionIndex(filePath, deletes);
       Predicate<T> isDeleted = record -> positionIndex.isDeleted(pos(record));
       return createDeleteIterable(records, isDeleted);
     }

     return hasIsDeletedColumn
         ? Deletes.streamingMarker(
             records, this::pos, Deletes.deletePositions(filePath, deletes), this::markRowDeleted)
         : Deletes.streamingFilter(
             records, this::pos, Deletes.deletePositions(filePath, deletes), counter);
   }
   ```

   新代码：

   ```java
   private CloseableIterable<T> applyPosDeletes(CloseableIterable<T> records) {
     if (posDeletes.isEmpty()) {
       return records;
     }

     List<CloseableIterable<Record>> deletes = Lists.transform(posDeletes, this::openPosDeletes);

     PositionDeleteIndex positionIndex = Deletes.toPositionIndex(filePath, deletes);
     Predicate<T> isDeleted = record -> positionIndex.isDeleted(pos(record));
     return createDeleteIterable(records, isDeleted);
   }
   ```

   关键行为变化：

   - 不再按 `recordCount` 之和与 100K 比较分叉；任何规模的 position delete 都构建位图。
   - 大删除量场景原本走 `Deletes.deletePositions`（`SortedMerge` 多路归并）+ `streamingFilter`/`streamingMarker`，数据行与删除位置同步顺序推进；现在改为一次性构建 `Roaring64Bitmap`，再对每行做 O(1) `isDeleted` 查找。
   - `_deleted` 元数据列场景（`hasIsDeletedColumn == true`）原本在大删除量时走 `Deletes.streamingMarker`（边读数据行边消费有序删除位置并调用 `markRowDeleted`），现在统一走 `createDeleteIterable` → `Deletes.markDeleted`（基于位图判定后标记）。两条标记路径语义等价（都是"对每个被删行调用 `markRowDeleted`"），但触发机制不同：流式依赖顺序对齐，位图依赖随机查找。
   - `counter`（`DeleteCounter`）原本在 `streamingFilter` 路径上由 `Deletes.filterDeleted` 内部递增；现在统一由 `createDeleteIterable` → `Deletes.filterDeleted(records, isDeleted, counter)` 递增，计数行为不变。

3. 与 `deletedRowPositions()` 的一致性：

   类内另一方法 `deletedRowPositions()`（向量化读取路径使用）本就总是 `Deletes.toPositionIndex(filePath, deletes)` 构建位图。改动前，`applyPosDeletes` 在大删除量时走流式、`deletedRowPositions` 走位图，两条路径并存；改动后两者都走位图，行为一致，且若同一 `DeleteFilter` 实例先后调用两者，位图构建逻辑完全相同（注：`applyPosDeletes` 不缓存位图，`deletedRowPositions` 缓存到 `deleteRowPositions` 字段，两者仍独立构建，但底层逻辑统一）。

## 小结

通过移除 100K 阈值分支，让 `DeleteFilter.applyPosDeletes` 在任何 position delete 规模下都构建 `Roaring64Bitmap` 索引并经 `createDeleteIterable` 统一过滤/标记，简化了读取路径、与向量化读取的位图用法对齐，并回收了 HashSet 时代遗留的内存保护策略。
