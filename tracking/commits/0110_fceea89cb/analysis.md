# 提交 0110：Spark 3.5: Don't cache or reuse manifest entries while rewriting metadata by default (#8935)

## 提交信息

- **序号**：0110 / 4088
- **哈希**：fceea89cb4a8f781641aa65456801b1cd40d0d03
- **短哈希**：fceea89cb
- **日期**：2023-10-30 10:53:23 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Don't cache or reuse manifest entries while rewriting metadata by default (#8935)
- **PR/Issue**：#8935

## 总体目的

这个提交改变了 Spark 3.5 中 `RewriteManifestsSparkAction` 在重写清单（manifest）时处理清单条目 Dataset 的默认策略：默认不再缓存（cache）清单条目 DF，也不再在关闭缓存时做一次额外的 round-robin `repartition` 来“复用”数据，而是让清单文件被读两遍。这是一个基于实际性能观测做出的、反直觉但更优的默认行为调整。

背景：`RewriteManifestsSparkAction` 的核心流程是读取匹配清单中的活动条目（manifest entries），然后按分区列做范围分片（`repartitionByRange`）+ 分区内排序，再写出新的清单文件。对于分区表，这一过程会消费清单条目 DF 两次——`repartitionByRange` 内部需要对 DF 采样以计算范围边界（一次读），随后实际写出时再读一次。

为了避免“读两次”，原实现提供了 `use-caching` 选项（默认 `true`）：
- 当 `use-caching=true` 时，对清单条目 DF 调用 `ds.cache()`，把条目物化到内存，第二次消费时直接命中缓存；
- 当 `use-caching=false` 时，对 DF 做一次 round-robin `repartition(numShufflePartitions)` + 一次 identity `map`，强制把条目通过 shuffle 写到磁盘，第二次消费时从 shuffle 输出读，从而避免再读原始清单文件。

提交者（Anton Okolnychyi）发现这两种“优化”在大表上反而更慢、更不稳健：
- **缓存路径**：缓存清单条目 DF 会占用大量集群内存，对元数据量大的表（清单条目可达上千万甚至上亿）会引发频繁 GC / 溢写，性能差且不稳定；
- **round-robin repartition 路径**：这一步本质上是一次 shuffle，会把所有条目写到磁盘。这次额外的磁盘写实际上比“再读一次清单文件”还要贵——因为读清单是分布式、可并行、且对大元数据表扩展性极好的操作（清单文件本身是列式 Avro/Parquet，读取高效）。

因此本提交把默认值改为 `use-caching=false`，并在关闭缓存时直接使用原始 DF（不再 repartition），让清单文件被读两遍。这种做法既快又稳，因为它把成本放在了分布式、扩展性好的读路径上，而不是内存缓存或额外 shuffle 写上。

这对 Iceberg 演进的意义：它修正了一个基于直觉而非实测的错误默认值，让大表的清单重写在大规模集群上更稳健，避免了因内存压力导致的失败，是 Iceberg 在生产规模场景下可用性的持续打磨。

## 如何达成设计目的

整体设计非常精简，分两步：把 `USE_CACHING_DEFAULT` 从 `true` 改为 `false`；简化 `withReusableDS` 方法——当不使用缓存时直接返回原始 DF（`ds`），不再做 round-robin repartition + identity map。同时清理不再使用的 import（`MapFunction`、`SQLConf`）。测试侧则把 `useCaching` 参数化加入 `TestRewriteManifestsAction` 的参数矩阵，确保新旧两种模式（缓存开/关）都被现有测试覆盖。

`withReusableDS` 这个方法名（“可复用 DS”）在改动后名实略有不符——关闭缓存时它返回的 DS 并不复用、会读两遍清单——但其作为“统一封装是否缓存 DF 的开关点”的职责不变，调用方（`writeManifestsForPartitionedTable`）无需感知差异。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：把缓存默认值改为 `false`，并移除关闭缓存时的 round-robin repartition 复用逻辑，改为直接使用原始 DF。

**工作逻辑**：

- **清理无用 import**：移除 `org.apache.spark.api.java.function.MapFunction` 和 `org.apache.spark.sql.internal.SQLConf`。这两个 import 仅服务于被删除的 round-robin repartition 分支：`MapFunction` 用于 `map((MapFunction<T, T>) value -> value, ds.exprEnc())` 这个 identity 映射；`SQLConf` 用于 `SQLConf.get().numShufflePartitions()` 取 shuffle 分区数。

- **修改默认值**（[RewriteManifestsSparkAction.java:83](../spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java)）：

  ```java
  public static final String USE_CACHING = "use-caching";
  public static final boolean USE_CACHING_DEFAULT = false;  // 原为 true
  ```

  这是本次行为调整的核心。默认关闭后，用户若仍想用缓存，可显式 `.option("use-caching", "true")` 打开，保留了逃生通道。

- **简化 `withReusableDS`**：

  原实现：

  ```java
  private <T, U> U withReusableDS(Dataset<T> ds, Function<Dataset<T>, U> func) {
    Dataset<T> reusableDS;
    boolean useCaching =
        PropertyUtil.propertyAsBoolean(options(), USE_CACHING, USE_CACHING_DEFAULT);
    if (useCaching) {
      reusableDS = ds.cache();
    } else {
      int parallelism = SQLConf.get().numShufflePartitions();
      reusableDS =
          ds.repartition(parallelism).map((MapFunction<T, T>) value -> value, ds.exprEnc());
    }
    try {
      return func.apply(reusableDS);
    } finally {
      ...
    }
  }
  ```

  新实现：

  ```java
  private <T, U> U withReusableDS(Dataset<T> ds, Function<Dataset<T>, U> func) {
    boolean useCaching =
        PropertyUtil.propertyAsBoolean(options(), USE_CACHING, USE_CACHING_DEFAULT);
    Dataset<T> reusableDS = useCaching ? ds.cache() : ds;

    try {
      return func.apply(reusableDS);
    } finally {
      ...
    }
  }
  ```

  关键变化在 `else` 分支：原来用 `ds.repartition(parallelism).map(value -> value, ...)` 强制 shuffle + identity map 把条目物化到磁盘以“复用”，现在直接用 `ds`。这意味着 `func` 在 `repartitionByRange` 采样和实际写出两个阶段会分别触发对清单文件的读取——即“读两遍清单”，但如提交说明所述，这比缓存占内存或额外 shuffle 写磁盘更便宜、更稳健。`try/finally` 中对 `reusableDS` 的 unpersist 等清理逻辑保持不变（缓存时才有实际作用）。

  调用方 `writeManifestsForPartitionedTable` 通过 `withReusableDS(manifestEntryDF, df -> { df.repartitionByRange(...).sortWithinPartitions(...).mapPartitions(...).collectAsList(); })` 消费 DF，行为变化对它透明：开缓存时 DF 被缓存复用，关缓存时 DF 被读两遍。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`

**修改目的**：把 `useCaching` 作为参数加入测试矩阵，确保缓存开/关两种模式都经过现有测试覆盖，验证新默认行为不破坏正确性。

**工作逻辑**：

- **参数矩阵扩展**：原来参数化测试只有 `snapshotIdInheritanceEnabled` 一个维度（`true`/`false`），现在新增 `useCaching` 维度，组合成 4 种：

  ```java
  @Parameterized.Parameters(name = "snapshotIdInheritanceEnabled = {0}, useCaching = {1}")
  public static Object[] parameters() {
    return new Object[][] {
      new Object[] {"true", "true"},
      new Object[] {"false", "true"},
      new Object[] {"true", "false"},
      new Object[] {"false", "false"}
    };
  }
  ```

  构造函数相应增加 `useCaching` 字段。这样所有原有测试用例（建表、写入、rewrite、校验清单数与条目数等）都会在 4 种参数组合下各跑一遍，既覆盖了新的默认行为（`use-caching=false`），也保证旧的缓存开启路径不回归。

- **测试中显式传入 `useCaching` 选项**：在多个 rewrite 调用点（`@Before` 中的预 rewrite、`testRewriteManifestsSmallTable`、`testRewriteManifestsBigTable`、`testRewriteLargeManifestsSmallTargetThreshold`、`testRewriteManifestsReplacement`、`testRewriteWithSnapshotIdInheritanceEnabled`、`testRewriteUseCaching`）追加 `.option(RewriteManifestsSparkAction.USE_CACHING, useCaching)`，把参数化的 `useCaching` 值传给 action。同时把原先一处硬编码的 `.option("use-caching", "false")`（在 `testRewriteUseCaching` 中）替换为参数化版本，统一由参数矩阵驱动。

  特别地，`testRewriteUseCaching` 中原注释 `// rewrite only the first manifest without caching` 也同步改为 `// rewrite only the first manifest`，因为该用例现在不再固定“不缓存”，而是跟随参数矩阵跑开/关两种模式，原注释已不准确。

## 小结

这个提交把 Spark 3.5 清单重写动作的 `use-caching` 默认值从 `true` 改为 `false`，并移除了关闭缓存时的 round-robin repartition 复用步骤，改为直接读两遍清单文件——一个基于实测、反直觉但更优的默认值调整，让大表的清单重写在内存压力与性能上更稳健，并辅以参数化测试覆盖新旧两种模式。
