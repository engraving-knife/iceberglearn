# 提交 0111：Spark 3.2: Don't cache or reuse manifest entries while rewriting metadata by default (#8956)

## 提交信息

- **序号**：0111 / 4088
- **哈希**：c171c5703389a551065f6aa86aa4a6648c9ebcee
- **短哈希**：c171c5703
- **日期**：2023-10-30 17:34:48 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.2: Don't cache or reuse manifest entries while rewriting metadata by default (#8956)
- **PR/Issue**：#8956（cherry-pick 自 #8935）

## 总体目的

这个提交要解决的是 `RewriteManifestsSparkAction` 在重写元数据（rewrite manifests）时默认缓存或重用 manifest entries 所导致的潜在正确性问题。在此前的实现中，[`USE_CACHING_DEFAULT`](../../../../spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java) 默认值为 `true`，即默认会对参与重写的 Dataset 执行 `ds.cache()`。当关闭缓存时（`use-caching=false`），旧代码并不会直接使用原 Dataset，而是走一条"重用 entries"的兜底路径：调用 `ds.repartition(parallelism).map(value -> value, ds.exprEnc())`，即按 Spark shuffle 分区数重分区后用一个恒等 `map` 重新生成 Dataset。这条兜底路径会重新物化 manifest entries，存在把已序列化/反序列化的 entry 对象在多个 manifest 之间重复使用或缓存的隐患，可能导致写入的 manifest 内容不正确。

提交作者 Anton Okolnychyi 在 PR #8935 中提出：在重写 manifest 元数据时不应缓存或重用 manifest entries，把默认行为改为不缓存，并且在关闭缓存时也不再走 repartition + 恒等 map 的兜底逻辑，而是直接使用原 Dataset。本提交（#8956）就是把这个改动 cherry-pick 到 Spark 3.2 分支。

对 Iceberg 演进的意义在于：manifest 重写是表元数据维护的关键操作，其正确性直接关系到表的快照一致性。把不安全的默认值翻转、并移除有问题的兜底路径，是用"安全优先"的默认值取代"性能优先但可能不正确"的默认值，属于正确性加固。本提交与 0110、0112、0113 一起构成跨 Spark 3.2/3.3/3.4/3.5 多版本同步的同一系列改动，体现了 Iceberg 对各 Spark 版本分支并行维护的工程模式。

## 如何达成设计目的

整体设计思路有两点：一是翻转默认值，把 `USE_CACHING_DEFAULT` 从 `true` 改为 `false`，使默认情况下不缓存 Dataset；二是简化 `withReusableDS` 的非缓存分支，移除 `repartition + 恒等 map` 的兜底逻辑，改为 `useCaching ? ds.cache() : ds`，即关闭缓存时直接返回原 Dataset，不再人为重分区与重映射。同时清理不再使用的 `MapFunction`、`SQLConf` import。测试侧则把 `useCaching` 作为参数化维度加入 `TestRewriteManifestsAction`，确保缓存开/关两种路径都被覆盖。改动整体结构很集中：一个生产文件 + 一个测试文件。

## 修改详情

### `spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：翻转缓存默认值为关闭，并移除关闭缓存时的"重分区 + 恒等 map"兜底路径，避免重用/缓存 manifest entries。

**工作逻辑**：

1. 默认值翻转：`public static final boolean USE_CACHING_DEFAULT = true;` 改为 `= false;`。这是核心行为变更，使默认不缓存。

2. `withReusableDS` 方法简化。旧实现：
   ```java
   Dataset<T> reusableDS;
   boolean useCaching = PropertyUtil.propertyAsBoolean(options(), USE_CACHING, USE_CACHING_DEFAULT);
   if (useCaching) {
     reusableDS = ds.cache();
   } else {
     int parallelism = SQLConf.get().numShufflePartitions();
     reusableDS = ds.repartition(parallelism).map((MapFunction<T, T>) value -> value, ds.exprEnc());
   }
   ```
   新实现：
   ```java
   boolean useCaching = PropertyUtil.propertyAsBoolean(options(), USE_CACHING, USE_CACHING_DEFAULT);
   Dataset<T> reusableDS = useCaching ? ds.cache() : ds;
   ```
   即关闭缓存时直接用原 `ds`，不再 `repartition` 与恒等 `map`。这去除了非缓存分支下 entries 被重物化、重用的风险。

3. 清理 import：移除 `org.apache.spark.api.java.function.MapFunction` 与 `org.apache.spark.sql.internal.SQLConf`，因为 `withReusableDS` 不再使用它们。

### `spark/v3.2/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：把 `useCaching` 作为参数化测试维度，确保缓存开/关两条路径都覆盖到所有 rewrite 场景。

**工作逻辑**：

1. 参数化维度扩展。原来 `@Parameterized.Parameters` 只有一个维度 `snapshotIdInheritanceEnabled`（"true"/"false"），现在扩展为二维：`{snapshotIdInheritanceEnabled, useCaching}`，组合数为 4（`{true,true}, {false,true}, {true,false}, {false,false}`）。构造函数相应增加 `useCaching` 字段。

2. 各 rewrite 测试用例统一注入 `useCaching` 选项。原本只在某个用例里硬编码 `.option("use-caching", "false")`，现在改为在所有 rewrite 调用处 `.option(RewriteManifestsSparkAction.USE_CACHING, useCaching)`，使每个参数组合都跑一遍。涉及多处 `actions.rewriteManifests(table)...execute()` 调用，均补上该 option。

3. 把原先硬编码的 `.option("use-caching", "false")` 字符串字面量替换为常量引用 `RewriteManifestsSparkAction.USE_CACHING`，并改用参数化变量 `useCaching`，同时把注释 `// rewrite only the first manifest without caching` 改为 `// rewrite only the first manifest`，因为该用例不再固定为"不缓存"。

## 小结

通过把 Spark 3.2 的 `RewriteManifestsSparkAction` 缓存默认值翻转为关闭、并移除非缓存分支下的 repartition+恒等 map 兜底路径，本提交以"安全优先"的默认值加固了 manifest 重写的正确性，是 PR #8935 跨 Spark 版本同步落地到 3.2 的一环。
