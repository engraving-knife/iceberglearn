# 提交 0427：Spark 3.5: Fix flaky TestSparkExecutorCache (#9583)

## 提交信息

- **序号**：0427
- **哈希**：f8866bf38e9997dfdea454f3a19c036831277e55
- **短哈希**：f8866bf38
- **日期**：2024 年 1 月 30 日（Tue Jan 30 19:12:17 2024 -0800）
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.5: Fix flaky TestSparkExecutorCache (#9583)
- **PR/Issue**：#9583

## 总体目的

这个提交修复了 `TestSparkExecutorCache` 测试的 flaky（不稳定）问题。`TestSparkExecutorCache` 是 Spark 3.5 模块中验证 `SparkExecutorCache`（执行器端缓存）行为的测试，它会在一个数据上写入位置删除（position delete）和等值删除（equality delete）文件，然后验证缓存相关的读写逻辑。

问题的根因在于 Spark 的 broadcast 机制：当测试调用 `sql("REFRESH TABLE %s", targetTableName)` 刷新表后，Spark 的 executor 端 `MemoryStore` 中仍然可能保留着旧的表广播变量（table broadcast）。这些残留的 broadcast block 携带了过期的表元数据/文件列表，会导致后续依赖最新表状态的断言偶发性失败——因为 executor 读到的可能是旧的广播缓存而非刷新后的状态。由于 broadcast 的失效依赖 GC 和块驱逐时机，这种残留具有时序依赖性，因此表现为 flaky test。

修复思路是：在写入删除文件并 `REFRESH TABLE` 之后，主动调用 `SparkEnv.get().blockManager().memoryStore().clear()` 清空内存存储，强制销毁所有当前存活的表广播。这样下一个测试步骤读到的必然是刷新后的最新表状态，消除了时序竞态。这是一个典型的"在测试夹具中显式重置有状态的外部资源"的修复模式。

## 如何达成设计目的

在测试辅助方法（写入删除文件并返回删除文件列表的方法）中，于 `REFRESH TABLE` 之后新增 3 行代码：获取当前 `SparkEnv`，取得其 `BlockManager` 的 `MemoryStore`，并调用 `clear()` 清空。同时新增两个对应的 import（`org.apache.spark.SparkEnv` 和 `org.apache.spark.storage.memory.MemoryStore`）。实现非常精简（仅 7 行新增），但精准地针对了 broadcast 残留这一根因。

## 修改详情

### spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestSparkExecutorCache.java

**修改目的**：消除测试因 Spark broadcast 残留导致的 flaky 行为。

**工作逻辑**：

1. **新增 import**：在已有 import 区块中加入 `org.apache.spark.SparkEnv` 和 `org.apache.spark.storage.memory.MemoryStore` 两个导入，用于访问 executor 端的内存存储。

2. **在写入删除文件的方法中清理内存存储**：该方法先向目标表追加数据，然后通过 `writePosDeletes` 和 `writeEqDeletes` 写入位置删除和等值删除文件，并通过 `newRowDelta()` 提交（带 `validateFromSnapshot` 和 `validateDataFilesExist` 校验），最后调用 `sql("REFRESH TABLE %s", targetTableName)` 让 Spark 感知表变更。在这行 `REFRESH TABLE` 之后，新增如下逻辑：
   ```java
   // invalidate the memory store to destroy all currently live table broadcasts
   SparkEnv sparkEnv = SparkEnv.get();
   MemoryStore memoryStore = sparkEnv.blockManager().memoryStore();
   memoryStore.clear();
   ```
   注释明确说明意图：使内存存储失效，以销毁所有当前存活的表广播。`MemoryStore.clear()` 会移除所有存储在 executor 内存中的 block（包括 broadcast 变量），从而保证后续测试步骤读取的是刷新后的最新表状态。之后方法返回包含 `posDeleteFile` 和 `eqDeleteFile` 的不可变列表。

## 小结

这是一个小而精准的测试稳定性修复。它体现了 Spark 测试中一个常见的陷阱：`REFRESH TABLE` 只刷新 driver 端的表元数据，但 executor 端的 broadcast 缓存不会同步失效，需要测试自行清理。修复模式（清空 `MemoryStore`）可推广到其他依赖 broadcast 的 Iceberg Spark 测试。该改动仅影响测试代码，不改变产品逻辑，属于质量保障范畴，对 1.4.x 分支的稳定性维护有实际价值。
