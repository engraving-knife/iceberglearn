# 提交 0125：Core: Use ParallelIterable in Deletes::toPositionIndex (6387) (#8805)

## 提交信息

- **序号**：0125 / 4088
- **哈希**：0b5aacd94207f9d880b9ddd38d04c13f59075c10
- **短哈希**：0b5aacd94
- **日期**：2023-11-02
- **作者**：Wing Yew Poon
- **提交说明**：Core: Use ParallelIterable in Deletes::toPositionIndex (6387) (#8805)
- **PR/Issue**：#8805（内部 issue 6387）

## 总体目的

在 Iceberg 读取路径上，当一个数据文件关联了多个 position delete 文件时，需要把这些 delete 文件中针对该数据文件的位置（position）合并成一个 `PositionDeleteIndex`（基于 `Roaring64Bitmap` 的位图），用于在扫描该数据文件时跳过被删除的行。这一合并由 `Deletes.toPositionIndex(CharSequence dataLocation, List<CloseableIterable<T>> deleteFiles)` 完成：先对每个 delete 文件用 `DataFileFilter` 过滤出目标数据文件路径的记录、提取 `pos` 列得到 `CloseableIterable<Long>`，再把所有位置拼接成一个流，最后遍历该流调用 `BitmapPositionDeleteIndex.delete(position)` 写入位图。

此前的实现用 `CloseableIterable.concat(positions)` 做顺序拼接：先消费完第一个 delete 文件的全部位置，再消费第二个，依此类推。当数据文件关联的 delete 文件较多、单个 delete 文件较大时，这一阶段是串行 I/O + 解码，成为读取链路上的瓶颈，尤其体现在持续追加删除（如 CDC 场景）产生大量 position delete 文件的表上。

本提交把合并阶段改为并行：当 delete 文件数大于 1 且提供了 worker 线程池时，用 `ParallelIterable` 替代 `CloseableIterable.concat`，让多个 delete 文件被并行读取/解码，元素通过 `ConcurrentLinkedQueue` 汇聚后由消费线程统一写入位图。为支持这一并行化，提交同时：

1. 在 `SystemConfigs` 新增配置项 `iceberg.worker.delete-num-threads`（环境变量 `ICEBERG_WORKER_DELETE_NUM_THREADS`），默认 `max(2, availableProcessors)`，用于限定 delete worker 池大小。
2. 在 `ThreadPools` 新增专用的 `DELETE_WORKER_POOL`（`iceberg-delete-worker-pool`）并通过 `getDeleteWorkerPool()` 暴露，与既有的 scan worker 池隔离，避免 delete 合并任务与 scan planning 任务互相争抢线程。
3. 把 `BitmapPositionDeleteIndex.delete(long position)` 标记为 `synchronized`，因为底层 `Roaring64Bitmap.add` 非线程安全，并行路径下需要保证位图写入的线程安全。

这是 Iceberg 在读取性能上的一个针对性优化，对存在大量 position delete 文件的负载（频繁行级更新/删除的表）有显著收益，同时通过独立线程池配置避免与 scan 规划并行度耦合。

## 如何达成设计目的

整体设计分三层：

1. **配置层**：新增 `DELETE_WORKER_THREAD_POOL_SIZE` 配置项，让用户可独立调节 delete 合并的并行度，与 scan 的 `iceberg.worker.num-threads` 解耦。
2. **线程池层**：在 `ThreadPools` 持有静态单例 `DELETE_WORKER_POOL`，复用既有 `newWorkerPool` 工厂创建，命名 `iceberg-delete-worker-pool` 便于诊断。
3. **算法层**：`Deletes.toPositionIndex` 增加 3 参数重载（接收 `ExecutorService deleteWorkerPool`），原 2 参数方法委托新方法并传入 `ThreadPools.getDeleteWorkerPool()`。新方法在 `positions.size() > 1 && deleteWorkerPool != null` 时用 `new ParallelIterable<>(positions, deleteWorkerPool)`，否则回退到 `CloseableIterable.concat(positions)` 保持原顺序语义与单 delete 文件场景的无谓开销。

`ParallelIterable` 采用"多生产者-单消费者"模型：每个 delete 文件由 worker 池中的一个任务串行迭代，元素放入 `ConcurrentLinkedQueue`；消费线程（调用 `forEach` 的线程）通过 `ParallelIterator` 的 `synchronized hasNext()/next()` 从队列取元素并调用 `positionDeleteIndex::delete`。这样并行发生在"I/O + 解码"阶段（每个 delete 文件的读取），而位图写入仍在消费线程上串行，但因为 `delete` 加了 `synchronized`，即便将来出现并发访问也能保证 `Roaring64Bitmap` 的安全。

测试侧，`TestPositionFilter.testCombinedPositionSetRowFilter` 改为 `@ParameterizedTest`，参数源提供 `null`（走顺序 concat 路径）和一个 4 线程的 `ExecutorService`（走 `ParallelIterable` 路径），同一断言覆盖两种实现，确保行为等价。

## 修改详情

### `core/src/main/java/org/apache/iceberg/SystemConfigs.java`

**修改目的**：新增 delete worker 线程池大小的配置项，让 delete 合并并行度可独立于 scan 规划并行度调节。

**工作逻辑**：新增 `ConfigEntry<Integer> DELETE_WORKER_THREAD_POOL_SIZE`：

```java
public static final ConfigEntry<Integer> DELETE_WORKER_THREAD_POOL_SIZE =
    new ConfigEntry<>(
        "iceberg.worker.delete-num-threads",
        "ICEBERG_WORKER_DELETE_NUM_THREADS",
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Integer::parseUnsignedInt);
```

key 为 `iceberg.worker.delete-num-threads`、env 为 `ICEBERG_WORKER_DELETE_NUM_THREADS`、默认值为 `max(2, availableProcessors)`，与既有 `WORKER_THREAD_POOL_SIZE`（`iceberg.worker.num-threads`）形式一致但独立配置。Javadoc 说明该池限制"计算单个数据文件 PositionDeleteIndex 时使用的线程数"。

### `core/src/main/java/org/apache/iceberg/util/ThreadPools.java`

**修改目的**：持有并暴露专用的 delete worker 线程池单例，与 scan worker 池隔离。

**工作逻辑**：

1. 读取配置得到池大小常量：
   ```java
   public static final int DELETE_WORKER_THREAD_POOL_SIZE =
       SystemConfigs.DELETE_WORKER_THREAD_POOL_SIZE.value();
   ```
2. 创建静态单例池：
   ```java
   private static final ExecutorService DELETE_WORKER_POOL =
       newWorkerPool("iceberg-delete-worker-pool", DELETE_WORKER_THREAD_POOL_SIZE);
   ```
   复用既有 `newWorkerPool(String namePrefix, int poolSize)` 工厂，命名前缀 `iceberg-delete-worker-pool` 便于线程 dump 诊断。
3. 暴露访问方法：
   ```java
   public static ExecutorService getDeleteWorkerPool() {
     return DELETE_WORKER_POOL;
   }
   ```
   Javadoc 说明该池"限制计算 PositionDeleteIndex 时使用的线程数"，由 `iceberg.worker.delete-num-threads` 系统属性控制。

与既有的 `WORKER_POOL`（scan 规划用）并列存在，两者互不干扰：delete 合并不会挤占 scan 规划的线程，反之亦然。

### `core/src/main/java/org/apache/iceberg/deletes/Deletes.java`

**修改目的**：让 `toPositionIndex` 在多 delete 文件场景下用 `ParallelIterable` 并行合并位置，提供接收外部线程池的重载并保持原顺序回退路径。

**工作逻辑**：

新增 import `java.util.concurrent.ExecutorService`、`org.apache.iceberg.util.ParallelIterable`、`org.apache.iceberg.util.ThreadPools`。

原 2 参数方法改为委托：
```java
public static <T extends StructLike> PositionDeleteIndex toPositionIndex(
    CharSequence dataLocation, List<CloseableIterable<T>> deleteFiles) {
  return toPositionIndex(dataLocation, deleteFiles, ThreadPools.getDeleteWorkerPool());
}
```

新增 3 参数重载，核心改动在合并策略：
```java
public static <T extends StructLike> PositionDeleteIndex toPositionIndex(
    CharSequence dataLocation,
    List<CloseableIterable<T>> deleteFiles,
    ExecutorService deleteWorkerPool) {
  DataFileFilter<T> locationFilter = new DataFileFilter<>(dataLocation);
  List<CloseableIterable<Long>> positions =
      Lists.transform(
          deleteFiles,
          deletes ->
              CloseableIterable.transform(
                  locationFilter.filter(deletes), row -> (Long) POSITION_ACCESSOR.get(row)));
  if (positions.size() > 1 && deleteWorkerPool != null) {
    return toPositionIndex(new ParallelIterable<>(positions, deleteWorkerPool));
  } else {
    return toPositionIndex(CloseableIterable.concat(positions));
  }
}
```

- 过滤与提取逻辑不变：对每个 delete 文件先用 `DataFileFilter` 按数据文件路径过滤，再用 `POSITION_ACCESSOR` 取出 `pos` 列，得到 `List<CloseableIterable<Long>>`。
- 分支判断：仅当 delete 文件数 `> 1` 且 `deleteWorkerPool != null` 时走并行路径 `new ParallelIterable<>(positions, deleteWorkerPool)`；否则（单 delete 文件、或测试显式传 null）回退到 `CloseableIterable.concat(positions)`，保持与改动前完全一致的顺序拼接语义。
- 最终都委托给既有的 `toPositionIndex(CloseableIterable<Long> posDeletes)`，该方法 `try-with-resources` 关闭源并 `forEach(positionDeleteIndex::delete)` 写入位图。

`ParallelIterable` 的并行发生在每个 delete 文件的迭代/解码阶段（worker 线程把 `Long` 放入 `ConcurrentLinkedQueue`），消费线程通过 `forEach` 轮询队列调用 `delete`，因此"I/O 与解码"被并行化，而位图写入仍是单消费线程串行。

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：为 `delete(long position)` 加 `synchronized`，保证并行路径下 `Roaring64Bitmap` 写入的线程安全。

**工作逻辑**：

```java
@Override
public synchronized void delete(long position) {
  roaring64Bitmap.add(position);
}
```

底层 `Roaring64Bitmap`（来自 `org.roaringbitmap.longlong`）的 `add` 操作非线程安全。类级 Javadoc 也明确"该实现非线程安全，通常在单个读取任务内构建并使用"。引入 `ParallelIterable` 后，虽然当前 `forEach` 消费者是单线程，但把 `delete` 标记为 `synchronized` 是防御性保护——确保位图在并行构建期间即使出现并发写入也不会损坏，且开销相对 I/O 可忽略。注意 `delete(long, long)` 区间重载与 `isDeleted`、`isEmpty` 未同步，因为它们不在并行构建路径上被调用。

### `core/src/test/java/org/apache/iceberg/deletes/TestPositionFilter.java`

**修改目的**：把 `testCombinedPositionSetRowFilter` 参数化，同时覆盖顺序（null executor）与并行（4 线程 executor）两条路径，确保行为等价。

**工作逻辑**：

1. 新增 import `ExecutorService`、`Executors`、`ThreadPoolExecutor`、`MoreExecutors`、`Stream`、`ParameterizedTest`、`MethodSource`。
2. 提供参数源：
   ```java
   static Stream<ExecutorService> executorServiceProvider() {
     return Stream.of(
         null,
         MoreExecutors.getExitingExecutorService(
             (ThreadPoolExecutor) Executors.newFixedThreadPool(4)));
   }
   ```
   `null` 触发 `positions.size() > 1 && deleteWorkerPool != null` 的 false 分支（顺序 concat）；4 线程池触发 true 分支（`ParallelIterable`）。`MoreExecutors.getExitingExecutorService` 包装确保测试结束时线程池退出，避免泄漏。
3. 测试方法改为参数化：
   ```java
   @ParameterizedTest
   @MethodSource("executorServiceProvider")
   public void testCombinedPositionSetRowFilter(ExecutorService executorService) {
     ...
     Predicate<StructLike> isDeleted =
         row ->
             Deletes.toPositionIndex(
                     "file_a.avro",
                     ImmutableList.of(positionDeletes1, positionDeletes2),
                     executorService)
                 .isDeleted(row.get(0, Long.class));
     ...
   }
   ```
   把原来调用 2 参数 `toPositionIndex` 改为调用 3 参数版本并传入参数化的 executor。两个 delete 文件 `positionDeletes1`、`positionDeletes2` 均含 `file_a.avro` 的位置，合并后断言过滤结果为 `[1L, 2L, 5L, 6L, 8L]`。该断言对两条路径都成立，验证并行合并与顺序合并语义一致。

## 小结

本提交把 `Deletes.toPositionIndex` 的位置删除文件合并从顺序拼接升级为基于 `ParallelIterable` 的并行读取，配套新增独立的 delete worker 线程池配置与单例、并对 `BitmapPositionDeleteIndex.delete` 加锁保证位图线程安全，显著优化了多 position delete 文件场景下的读取性能，同时通过参数化测试保证与原顺序实现行为等价。
