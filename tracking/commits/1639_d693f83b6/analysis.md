# 提交 1639：Spark 3.5: Fix broadcasting specs in RewriteTablePath (#11982)

## 提交信息

- **序号**：1639 / 4088
- **哈希**：d693f83b6ad6e306550d117ff0bf0014356500b2
- **短哈希**：d693f83b6
- **日期**：2025-01-25（Sat Jan 25 07:51:46 2025 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark 3.5: Fix broadcasting specs in RewriteTablePath
- **PR/Issue**：#11982

## 总体目的

`RewriteTablePathSparkAction` 在 Driver 端把表与 `specsById` 分两个 broadcast 发到 Executor：

```
Broadcast<Table> serializableTable = sparkContext().broadcast(SerializableTable.copyOf(table));
Broadcast<Map<Integer, PartitionSpec>> specsById = sparkContext().broadcast(tableMetadata.specsById());
```

Executor 上分别 `tableBroadcast.getValue().io()` 与 `specsById.getValue()` 取用。这个写法存在两个问题：

1. **broadcast 不一致 / 序列化不完整**：`SerializableTable`（不带 `WithSize`）在 Kryo 序列化路径下不能保证 `specs()` 等元数据完整还原；同时把 specs 单独 broadcast 与表本身"分两路"传递，二者可能在 Executor 端读取到不一致的快照（例如中途表被刷新），导致用错 `PartitionSpec`。
2. **重复 broadcast**：`rewriteManifests` 与 `rewritePositionDeleteFiles` 各自独立 broadcast 一次表，Driver 端会广播多份相同数据，浪费带宽与序列化开销。

本提交修复这两点：

- 把 `SerializableTable.copyOf` 替换为 `SerializableTableWithSize.copyOf`（Spark 专用、实现 `KnownSizeEstimation`，序列化更可靠）；
- 取消单独的 `specsById` broadcast，Executor 端改为 `table.getValue().specs()` 直接从广播的表上取 specs —— specs 与表绑定在同一份广播对象里，杜绝不一致；
- 把 `tableBroadcast` 缓存为实例字段，懒加载（`tableBroadcast()` 方法首次调用时 broadcast 并缓存），避免重复广播；
- 新增 `testKryoDeserializeBroadcastValues` 测试，强制 Kryo 序列化器、清空本地 BlockManager 缓存以模拟"Executor 端反序列化"路径，断言广播表 `uuid()` 与原表一致。

## 如何达成设计目的

1. 在 `RewriteTablePathSparkAction` 中新增字段 `private Broadcast<Table> tableBroadcast = null;`；
2. 新增 `@VisibleForTesting` 方法 `tableBroadcast()`：若 `tableBroadcast == null` 则 `sparkContext().broadcast(SerializableTableWithSize.copyOf(table))` 并赋值缓存，返回该 broadcast；
3. `rewriteManifests` 与 `rewritePositionDeleteFiles` 改为调 `tableBroadcast()` 取广播表，删除各自独立的 `serializableTable`/`specsById` broadcast；
4. `toManifests`/`writeDataManifest`/`writeDeleteManifest`/`rewritePositionDelete` 的签名去掉 `Broadcast<Map<Integer, PartitionSpec>> specsById` 参数，Executor 端改为 `table.getValue().specs()` 取 specs；
5. `import SerializableTable` 替换为 `import SerializableTableWithSize`；
6. 测试 `TestRewriteTablePathsAction` 新增 `testKryoDeserializeBroadcastValues` 与辅助方法 `removeBroadcastValuesFromLocalBlockManager`。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java`（修改，+19/-50）

**修改目的**：消除独立 specsById broadcast，统一用 `SerializableTableWithSize` 广播表，Executor 端从表自身取 specs；缓存 broadcast 避免重复。

**工作逻辑**：

- import 变更：删除 `org.apache.iceberg.SerializableTable`，新增 `org.apache.iceberg.spark.source.SerializableTableWithSize` 与 `org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting`；
- 新增字段 `private Broadcast<Table> tableBroadcast = null;`；
- 新增方法：
  ```
  @VisibleForTesting
  Broadcast<Table> tableBroadcast() {
    if (tableBroadcast == null) {
      this.tableBroadcast = sparkContext().broadcast(SerializableTableWithSize.copyOf(table));
    }
    return tableBroadcast;
  }
  ```
  懒加载 + 缓存，多次调用只广播一次；`@VisibleForTesting` 让测试能拿到 broadcast 对象做反序列化验证。
- `rewriteManifests`：
  - 删除 `Broadcast<Table> serializableTable = sparkContext().broadcast(SerializableTable.copyOf(table));` 与 `Broadcast<Map<Integer, PartitionSpec>> specsById = sparkContext().broadcast(tableMetadata.specsById());` 两行；
  - `toManifests(...)` 调用改为 `toManifests(tableBroadcast(), stagingDir, tableMetadata.formatVersion(), sourcePrefix, targetPrefix)`，去掉 `specsById` 参数。
- `toManifests(...)` 签名：参数 `Broadcast<Table> tableBroadcast` 改名为 `table`，删除 `Broadcast<Map<Integer, PartitionSpec>> specsById`；`DATA`/`DELETES` 分支调用 `writeDataManifest`/`writeDeleteManifest` 时同样去掉 `specsById` 参数。
- `writeDataManifest`/`writeDeleteManifest` 签名：`Broadcast<Table> tableBroadcast` 改名 `table`，删除 `specsByIdBroadcast`；内部 `Map<Integer, PartitionSpec> specsById = specsByIdBroadcast.getValue();` 改为 `Map<Integer, PartitionSpec> specsById = table.getValue().specs();` —— 直接从广播表上取 specs。
- `rewritePositionDeleteFiles`：删除独立 `serializableTable`/`specsById` broadcast，`foreach(rewritePositionDelete(...))` 调用改为 `foreach(rewritePositionDelete(tableBroadcast(), sourcePrefix, targetPrefix, stagingDir, posDeleteReaderWriter))`。
- `rewritePositionDelete` 签名：`Broadcast<Table> tableBroadcast` 改名 `tableArg`，删除 `Broadcast<Map<Integer, PartitionSpec>> specsById`；内部 `PartitionSpec spec = specsById.getValue().get(deleteFile.specId());` 改为 `PartitionSpec spec = tableArg.getValue().specs().get(deleteFile.specId());`。

净效果：广播变量从"表 + specsById 两路"收敛为"表一路"，且用 `SerializableTableWithSize` 替代 `SerializableTable`，specs 通过 `table.specs()` 在 Executor 端获取，保证一致性；broadcast 缓存避免重复广播。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java`（修改，+28）

**修改目的**：验证 Kryo 反序列化路径下广播表能正确还原。

**工作逻辑**：

- 新增 import：`SparkEnv`、`Broadcast`、`BlockId`、`BlockInfoManager`、`BlockManager`、`BroadcastBlockId`；
- 新增测试 `testKryoDeserializeBroadcastValues`：
  ```
  sparkContext.getConf().set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
  RewriteTablePathSparkAction action = (RewriteTablePathSparkAction) actions().rewriteTablePath(table);
  Broadcast<Table> tableBroadcast = action.tableBroadcast();
  // force deserializing broadcast values
  removeBroadcastValuesFromLocalBlockManager(tableBroadcast.id());
  assertThat(tableBroadcast.getValue().uuid()).isEqualTo(table.uuid());
  ```
  - 强制使用 KryoSerializer（这是问题最可能复现的序列化器）；
  - 调用 `action.tableBroadcast()`（依赖 `@VisibleForTesting` 暴露）拿到广播对象；
  - 调用 `removeBroadcastValuesFromLocalBlockManager` 清空本地 BlockManager 中该广播的缓存块，迫使下一次 `getValue()` 重新走反序列化路径；
  - 断言 `tableBroadcast.getValue().uuid()` 与原 `table.uuid()` 相等——验证 `SerializableTableWithSize` 在 Kryo 下能完整反序列化（包括 uuid 这种来自表 ops 的字段）。
- 新增辅助方法 `removeBroadcastValuesFromLocalBlockManager(long id)`：
  ```
  BlockId blockId = new BroadcastBlockId(id, "");
  SparkEnv env = SparkEnv.get();
  env.broadcastManager().cachedValues().clear();
  BlockManager blockManager = env.blockManager();
  BlockInfoManager blockInfoManager = blockManager.blockInfoManager();
  blockInfoManager.lockForWriting(blockId, true);
  blockInfoManager.removeBlock(blockId);
  blockManager.memoryStore().remove(blockId);
  ```
  清空 broadcastManager 缓存、移除 BlockManager 中该 broadcast 的块与内存缓存，模拟"Executor 端首次反序列化"。

## 小结

- **成效**：修复了 `RewriteTablePathSparkAction` 在 Spark 集群上（尤其 Kryo 序列化器）可能因 `SerializableTable` 反序列化不完整 / specs 与表 broadcast 不一致导致的 `PartitionSpec` 取错或表元数据丢失问题；同时减少 broadcast 数量（2→1）并缓存广播对象，降低 Driver→Executor 的序列化与带宽开销。新增的 Kryo 反序列化测试覆盖了回归路径。
- **影响范围**：仅 `RewriteTablePathSparkAction` 一处行为变化（broadcast 方式），无对外 API 变化（`tableBroadcast()` 是 `@VisibleForTesting` 包级方法）。`SerializableTable` → `SerializableTableWithSize` 的替换对该 Action 内部透明。其他 Spark action 不受影响。
- **回迁到 1.4.x 的注意事项**：
  - 1.4.x 分支的 `RewriteTablePathSparkAction` 若同样存在"双 broadcast + SerializableTable"写法，应回迁此修复；
  - 依赖 `SerializableTableWithSize`（Spark 模块已有，1.4.x 上确认存在）；
  - 测试依赖 `SparkEnv`/`BlockManager`/`BlockInfoManager`/`BroadcastBlockId` 等 Spark 内部 API，1.4.x 上的 Spark 版本需有等价 API（Spark 3.5 应可用）；
  - 若 1.4.x 上 #11931（procedure 化）尚未回迁，本修复仍可独立回迁到 Action 层；
  - 注意 `tableBroadcast()` 字段非线程安全（无同步），但 Spark Action 通常单线程使用，无问题；若 1.4.x 上有并发调用场景需自行加锁。
