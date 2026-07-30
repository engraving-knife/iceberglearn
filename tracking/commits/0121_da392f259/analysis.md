# 提交 0121：Spark: Use SerializableTableWithSize when optimizing metadata (#8957)

## 提交信息

- **序号**：0121 / 4088
- **哈希**：da392f259cd8fc4788453e309e3369a3a190bbb1
- **短哈希**：da392f259
- **日期**：2023-10-31
- **作者**：Anton Okolnychyi
- **提交说明**：Spark: Use SerializableTableWithSize when optimizing metadata (#8957)
- **PR/Issue**：#8957

## 总体目的

`RewriteManifestsSparkAction` 是 Iceberg 在 Spark 引擎上执行 manifest 重写（rewriting manifests）动作的核心实现，会在 Driver 端把当前 `Table` 对象作为广播变量（`Broadcast<Table>`）分发给所有 Executor，以便每个任务在写新的 manifest 时访问表元数据（schema、partition spec 等）。

在此之前，该 action 使用 `org.apache.iceberg.SerializableTable.copyOf(table)` 来构造可序列化的表副本。`SerializableTable` 实现了 Java 序列化，但并不实现 Spark 的 `KnownSizeEstimation` 接口。当 Spark 要广播一个对象时，会先估算其大小以决定广播阈值与分块策略；对于未实现 `KnownSizeEstimation` 的对象，Spark 必须真正对该对象做一次序列化（或对 ByteBuffer 进行大小测量）才能得到大小，这在表较大或广播频繁时会带来不必要的开销。

本提交将 `RewriteManifestsSparkAction` 中所有 `SerializableTable.copyOf(table)` 替换为 `org.apache.iceberg.spark.source.SerializableTableWithSize.copyOf(table)`。`SerializableTableWithSize` 是 `SerializableTable` 的子类，并额外实现了 Spark 的 `KnownSizeEstimation` 接口，通过 `estimatedSize()` 返回一个固定常量（`32_768L`），让 Spark 直接拿到一个低成本的大小估算值，避免真正序列化对象来测量大小。这是 metadata 优化路径上的一次性能微调，与 Iceberg 在 Spark scan 路径上既有的 `SerializableTableWithSize` 用法保持一致，统一了表广播变量的处理方式。

## 如何达成设计目的

改动非常集中：针对 Spark 3.2/3.3/3.4/3.5 四个版本下的同名文件 `RewriteManifestsSparkAction.java`，将 import 从 `SerializableTable` 改为 `SerializableTableWithSize`，并将 `writeManifestsForUnpartitionedTable` 与 `writeManifestsForPartitionedTable` 两个方法中构造广播变量的语句由 `SerializableTable.copyOf(table)` 替换为 `SerializableTableWithSize.copyOf(table)`。`SerializableTableWithSize.copyOf` 内部会判断目标表是否为 `BaseMetadataTable`，分别构造 `SerializableTableWithSize` 或 `SerializableMetadataTableWithSize`，二者均实现 `KnownSizeEstimation.estimatedSize()` 返回常量，从而满足 Spark 对广播变量大小的快速估算需求。

## 修改详情

### `spark/v3.2/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`

**修改目的**：四个 Spark 版本下，把 manifest 重写动作中广播的 `Table` 由 `SerializableTable` 改为 `SerializableTableWithSize`，让 Spark 通过 `KnownSizeEstimation` 直接拿到大小估算，避免真正序列化对象来测量大小。

**工作逻辑**：每个文件的改动完全相同：

1. 移除 import `org.apache.iceberg.SerializableTable`，新增 import `org.apache.iceberg.spark.source.SerializableTableWithSize`。
2. 在 `writeManifestsForUnpartitionedTable(Dataset<Row> manifestEntryDF, int numManifests)` 中：
   ```java
   // 旧：
   Broadcast<Table> tableBroadcast = sparkContext().broadcast(SerializableTable.copyOf(table));
   // 新：
   Broadcast<Table> tableBroadcast =
       sparkContext().broadcast(SerializableTableWithSize.copyOf(table));
   ```
3. 在 `writeManifestsForPartitionedTable(Dataset<Row> manifestEntryDF, int numManifests, int targetNumManifestEntries)` 中做同样的替换。

`SerializableTableWithSize.copyOf(table)` 会根据 table 类型返回合适的包装类（普通表返回 `SerializableTableWithSize`，元数据表返回 `SerializableMetadataTableWithSize`），二者都实现 `KnownSizeEstimation.estimatedSize()` 返回常量 `32_768L`。Spark 在广播时即可直接使用该值进行阈值判断，跳过实际序列化测量步骤，从而降低 manifest 重写作业在 Driver 端的元数据广播开销。

## 小结

统一 Spark manifest 重写动作中 `Table` 广播变量与 scan 路径相同的 `SerializableTableWithSize` 包装，借助 `KnownSizeEstimation` 避免 Spark 对广播对象做真实序列化以估算大小，属于元数据优化路径上的一次小而聚焦的性能改进。
