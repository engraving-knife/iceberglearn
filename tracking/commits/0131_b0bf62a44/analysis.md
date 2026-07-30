# 提交 0131：Spark 3.5: Use rolling manifest writers when optimizing metadata (#8972)

## 提交信息

- **序号**：0131 / 4088
- **哈希**：b0bf62a448617bd5f57ca72c2648452e6600fa20
- **短哈希**：b0bf62a44
- **日期**：2023-11-03 19:19:33 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Use rolling manifest writers when optimizing metadata (#8972)
- **PR/Issue**：#8972

## 总体目的

这个提交重构了 Spark 3.5 中 `RewriteManifestsSparkAction` 的清单文件重写实现，将其从"基于条目数预估 + 手动二分切分"的方式改为使用 Iceberg core 已有的 [`RollingManifestWriter`](../../core/src/main/java/org/apache/iceberg/RollingManifestWriter.java)。

此前的实现存在两个核心问题。其一，重写逻辑依赖对每个分区条目数（`numEntries`）的精确预估来决定单个清单文件最多放多少条目（`targetNumManifestEntries`），并在分区数据超过阈值时简单地把条目列表二分成两半（`0` 到 `midIndex`、`midIndex` 到末尾）写入两个清单。这种二分切分既粗糙又低效——它只能产生至多 2 个清单，无法应对单个分区内数据量远超目标大小的情况，导致生成的清单文件仍可能过大。其二，整个流程需要预先收集所有 `Row` 到内存列表（`Lists.newArrayList(rows)`），在大表场景下内存压力较大，且与 Spark 的惰性流式处理模型相悖。

本次改动引入 `RollingManifestWriter`，它内部按目标文件大小（`targetFileSizeInBytes`）和行数阈值（每 250 行检查一次）自动滚动切分，可产出任意数量的清单文件。这彻底解决了"预估不准导致清单过大"的问题，让 Spark manifest rewrite 的产物大小真正受 `target-manifest-size-bytes` 控制，与 Iceberg core 写路径（如普通 append/rewrite）的行为保持一致。

## 如何达成设计目的

整体设计思路是"复用 core 的滚动写入能力，移除 Spark 侧的手动预估与切分逻辑"。改动分三步：第一，删除 `RewriteManifestsSparkAction` 中负责预估的 `targetNumManifestEntries` 方法和负责二分写入的 `writeManifest`/旧 `toManifests` 方法；第二，新增一个 `ManifestWriterFactory`（可序列化的工厂类）封装清单写入器的构造参数（广播表、格式版本、specId、输出位置、目标大小），并提供 `newRollingManifestWriter()` 方法；第三，将新的 `toManifests` 改为流式遍历 `rows` 迭代器，直接调用 `RollingManifestWriter.existing(...)` 写入，由写入器自行决定何时切分。

改动整体结构集中在单个文件 [`RewriteManifestsSparkAction.java`](../../spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java) 的方法级重写，外加对应测试用例的增强。改动后该方法从 231 行净增减到更紧凑的形态（净减 24 行），代码结构更清晰。

## 修改详情

### [`spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`](../../spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java)

**修改目的**：将 manifest rewrite 从"预估条目数 + 手动二分切分"切换为基于 `RollingManifestWriter` 的滚动写入。

**工作逻辑**：

1. **目标清单数计算简化**：原 `execute()` 中先遍历累加 `totalSizeBytes` 和 `numEntries`，再分别算 `targetNumManifests` 和 `targetNumManifestEntries`。改动后只保留 `targetNumManifests`（由新增的 `totalSizeBytes(manifests)` 方法计算），不再需要条目数。因为切分粒度交给 `RollingManifestWriter` 按字节控制，Spark 侧只需决定 repartition 的分区数。

2. **分区表/非分区表入参收敛**：`writeManifestsForPartitionedTable` 不再接收 `targetNumManifestEntries`；两个 write 方法的 `maxNumManifestEntries`（原为 `Long.MAX_VALUE` 或 `1.1 * targetNumManifestEntries`）被移除，统一改用 `manifestWriters()` 工厂。

3. **新增 `ManifestWriterFactory` 内部类**：这是一个 `Serializable` 静态内部类，持有 `Broadcast<Table>`、`formatVersion`、`specId`、`outputLocation` 和 `maxManifestSizeBytes`。关键方法：
   - `newRollingManifestWriter()`：以 `this::newManifestWriter` 作为 `Supplier<ManifestWriter>` 构造 `RollingManifestWriter`，目标大小传 `maxManifestSizeBytes`。
   - `newManifestWriter()`：调用 `ManifestFiles.write(formatVersion, spec(), newOutputFile(), null)`。
   - `newManifestLocation()`：生成 `optimized-m-<uuid>.avro` 形式的路径。
   - 注意目标大小阈值设为 `1.2 * targetManifestSizeBytes`（注释明确：允许实际清单比估算值大 20%，因为估算不精确），相比原先分区表用的 1.1 倍略放宽，非分区表则从无限制（`Long.MAX_VALUE`）变为受控。

4. **`toManifests` 改为流式写入**：新 `toManifests(ManifestWriterFactory writers, combinedPartitionType, partitionType, sparkType)` 返回的 `MapPartitionsFunction` 不再先把 rows 收集成 List，而是直接 `while (rows.hasNext())` 流式读取，对每个 row 调用 `writer.existing(wrapper.wrap(file), snapshotId, sequenceNumber, fileSequenceNumber)` 写入，最后 `writer.close()` 并返回 `writer.toManifestFiles().iterator()`。`RollingManifestWriter` 会在内部按需切分产出多个 `ManifestFile`。这同时解决了原先的内存收集问题。

5. **删除 `writeManifest` 与旧 `toManifests`**：原先负责二分切分（`if rowsAsList.size() <= maxNumManifestEntries` 则写一个，否则取 `midIndex` 写两个）的逻辑整体删除。`import` 也相应清理：移除 `java.io.IOException`、`java.util.Collections`，新增 `java.io.Serializable`、`org.apache.iceberg.RollingManifestWriter`。

6. **`totalSizeBytes` 提取为独立方法**：将原先散落在 `execute()` 中的累加逻辑抽成 `private long totalSizeBytes(Iterable<ManifestFile>)`，并保留对 `hasFileCounts` 的 `ValidationException` 校验。

### [`spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java`](../../spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java)

**修改目的**：增强测试以验证滚动写入能产生多于 2 个清单，且替换原先仅验证恰好 2 个清单的弱断言。

**工作逻辑**：

- 原 `testSparkManifestRewriteUseAllManifests`（或对应场景）通过写入 50 条同分区记录触发重写，断言"重写 1 个清单、新增恰好 2 个清单"。这种构造数据量过小，无法触发真正的滚动切分。改动后改为构造 1000 个不同分区（`c3=0..999`）的数据文件，通过 `writeManifest` + `newFastAppend().appendManifest()` 直接附加一个含 1000 个 DataFile 的清单作为起点，使重写后必须按字节切分。
- 断言从 `Assert.assertEquals(..., 2, ...)` 改为 `Assertions.assertThat(result.addedManifests()).hasSizeGreaterThanOrEqualTo(2)` 和 `Assertions.assertThat(newManifests).hasSizeGreaterThanOrEqualTo(2)`，即"至少 2 个"——这与滚动写入语义一致（具体数量取决于实际写入大小，不再硬编码）。
- 新增私有辅助方法 `writeManifest(Table, List<DataFile>)`（用 `ManifestFiles.write` 写一个临时清单）和 `newDataFile(Table, String)`（构造带随机 UUID 路径、10 字节、1 条记录的 DataFile）。`newDataFile` 设的 `withFileSizeInBytes(10)` 故意很小，配合 1000 个文件让总大小落在可触发多清单切分的区间。

## 小结

通过复用 core 的 `RollingManifestWriter`，Spark 3.5 manifest rewrite 从粗糙的条目数预估 + 二分切分升级为按字节滚动的精确切分，既消除内存收集隐患，又使产物大小真正受目标大小控制，是 Iceberg 元数据优化链路向 core 写路径行为对齐的重要一步。
