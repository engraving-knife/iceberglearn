# 提交 1073：Core: Add ManifestWrite benchmark (#8637)

## 提交信息

- **序号**：1073 / 4088
- **哈希**：2f2c367b3582b58c9c867d26b203593aa1e8e667
- **短哈希**：2f2c367b3
- **日期**：2024-08-20 15:08:43 +0200
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Core: Add ManifestWrite benchmark (#8637)
- **PR/Issue**：#8637

## 总体目的

Iceberg 通过 manifest 文件（`ManifestFile`）和 manifest 列表文件（`ManifestList`，即 snap-xxxxxxxx-xxxxx.avro）来记录快照中包含的数据文件集合。写入 manifest 是 Iceberg 提交操作（commit）的必经路径，其性能直接影响表写入吞吐量，尤其是在小文件合并、写入频繁的场景下。然而项目此前已有 `ManifestReadBenchmark`（衡量 manifest 读取性能），却缺少对应的"写入"基准测试，导致 manifest 写入路径的性能回退、改进效果无法量化。

本提交的目的是新增 `ManifestWriteBenchmark`，使用 JMH 框架对 manifest 文件及 manifest 列表文件的写入进行可重复的微基准测试，覆盖 format version 1 和 2 两种格式。这样可以为后续涉及 manifest 写入路径的优化（例如 Avro 编码改进、metric 序列化优化、字段裁剪等）提供"前后对比"数据，避免无依据的"凭感觉优化"。

同时，本提交对已有的 `ManifestReadBenchmark` 做了小重构，将 `ManifestListWriter` 改为 try-with-resources 形式，消除"显式 close + 异常时漏关"的资源管理隐患，并使读写两份 benchmark 在资源管理风格上保持一致。

## 如何达成设计目的

设计思路：

1. 新建 `ManifestWriteBenchmark.java`，参照 `ManifestReadBenchmark` 的整体结构（`@State`、`@Setup`、`@TearDown`、`@Benchmark`），把写入 manifest 的逻辑封装成一个 JMH benchmark 方法。
2. 通过 `@Param({"1", "2"})` 参数化 format version，让 JMH 自动跑两轮以覆盖 v1 和 v2 两种 manifest 格式。
3. benchmark 内部模拟真实写入流程：先写若干个 manifest 文件（每个含若干 `DataFile`），再把它们汇总到一个 manifest list 中，覆盖"manifest 文件写 + manifest list 写"完整链路。
4. 把 `Metrics` 这种耗时构造的对象在 `@Setup` 阶段一次性生成并复用，避免 benchmark 主体中混入"非写入"耗时，干扰测量结果。
5. 使用 `@BenchmarkMode(Mode.SingleShotTime)` + `@Threads(1)` + 5 分钟 `@Timeout`，单次完整跑一遍写入流程并测耗时，避免 warmup 阶段被 JVM 内联优化掩盖真实性能。
6. 顺手把 `ManifestReadBenchmark` 中显式 `listWriter.close()` 改为 try-with-resources，提升代码健壮性。

## 修改详情

### `core/src/jmh/java/org/apache/iceberg/ManifestWriteBenchmark.java`（新增）

**修改目的**：新增 manifest 写入性能基准测试类，量化 manifest 文件与 manifest list 文件写入耗时，覆盖 format version 1/2。

**工作逻辑**：

- **类级注解**：
  - `@Fork(1)`：每次跑 benchmark 都 fork 一个新 JVM，避免 JVM 状态污染。
  - `@State(Scope.Benchmark)`：状态在 benchmark 范围内共享。
  - `@Measurement(iterations = 5)`：测量 5 轮取统计。
  - `@BenchmarkMode(Mode.SingleShotTime)`：单次执行计时（而非吞吐量），适合"一次完整写入"这种非循环操作。
  - `@Timeout(time = 5, timeUnit = TimeUnit.MINUTES)`：5 分钟超时兜底，防止极端环境卡死 CI。

- **常量**：`NUM_FILES = 10`（写 10 个 manifest 文件），`NUM_ROWS = 100000`（每个 manifest 内 10 万个 DataFile 条目），`NUM_COLS = 100`（metrics 中 100 列）。整体写入量较大，确保单次执行有可测量耗时。

- **`@Setup before()`**：用 `Random` 预生成一个 `Metrics` 对象（含 100 列的 columnSizes/valueCounts/nullValueCounts/nanValueCounts/lowerBounds/upperBounds），避免在 benchmark 主体中生成 metrics 干扰测量。

- **`@TearDown after()`**：清理 `baseDir` 临时目录、清空 `manifestListFile`，防止 JMH 多轮跑之间产生磁盘累积。

- **`BenchmarkState` 静态内部类**：用 `@Param({"1", "2"})` 声明 `formatVersion`，JMH 会自动为 v1 和 v2 各跑一组，便于对比两种 manifest 格式的写入开销差异。

- **`@Benchmark writeManifestFile(BenchmarkState state)`**：核心测量方法，工作流程：
  1. 创建临时目录 `baseDir` 和 manifest list 文件路径（UUID 命名）。
  2. 用 `ManifestLists.write(formatVersion, ...)` 打开一个 `ManifestListWriter`（try-with-resources）。
  3. 循环 `NUM_FILES` 次，每次：
     - 创建一个 manifest 文件输出路径；
     - 用 `ManifestFiles.write(formatVersion, PartitionSpec.unpartitioned(), manifestFile, snapshotId=1L)` 打开 `ManifestWriter<DataFile>`；
     - 内层循环 `NUM_ROWS` 次，构造 `DataFile`（无分区、Parquet 格式、随机 path、`j` 作为 fileSizeInBytes 和 recordCount、复用预生成的 metrics），通过 `finalWriter.add(dataFile)` 加入；
     - close `ManifestWriter`，调用 `writer.toManifestFile()` 拿到 manifest 元数据，加入到 `ManifestListWriter`。
  4. 自动 close `ManifestListWriter` 完成写入。

  该流程模拟了真实 commit 路径中"写多个 manifest + 汇总到 manifest list"的完整逻辑。

- **`randomMetrics(Random)`**：辅助方法，构造一个含 100 列、每列 columnSizes/valueCounts/nullValueCounts/nanValueCounts/lowerBounds/upperBounds 的 `Metrics` 对象，`lowerBounds`/`upperBounds` 用随机字节填充。

### `core/src/jmh/java/org/apache/iceberg/ManifestReadBenchmark.java`

**修改目的**：把 `ManifestListWriter` 的资源管理从"手动 close"改为 try-with-resources，统一读写两份 benchmark 的代码风格，并消除异常路径下漏关资源的隐患。

**工作逻辑**：

```diff
-    ManifestListWriter listWriter =
-        ManifestLists.write(1, org.apache.iceberg.Files.localOutput(manifestListFile), 0, 1L, 0);
-
-    try {
+    try (ManifestListWriter listWriter =
+        ManifestLists.write(1, org.apache.iceberg.Files.localOutput(manifestListFile), 0, 1L, 0)) {
       for (int i = 0; i < NUM_FILES; i++) {
         ...
         listWriter.add(writer.toManifestFile());
       }
-
-      listWriter.close();
     } catch (IOException e) {
       throw new UncheckedIOException(e);
     }
```

改造后，`listWriter` 在 try-with-resources 退出时自动 close（即使中途抛异常也会关），原先的 `listWriter.close()` 显式调用被移除。如果原 `close()` 抛 `IOException`，会被外层 `catch (IOException)` 捕获并包装为 `UncheckedIOException`，与改造前语义一致。

## 小结

- **成效**：新增了 `ManifestWriteBenchmark`，使 Iceberg core 模块具备 manifest 写入路径的 JMH 基准测试能力，覆盖 format version 1 和 2，可用于后续写入路径优化的回归性评估；同时把 `ManifestReadBenchmark` 的资源管理改造为 try-with-resources，提升健壮性。
- **影响范围**：仅 JMH benchmark 模块，2 个文件（新增 1 个、修改 1 个），170 行新增代码。不影响任何生产代码路径，不会改变运行时行为。
- **回迁到 1.4.x 的注意事项**：本提交属于测试/工具类增强，不影响生产逻辑，可以安全回迁到 1.4.x。回迁时需确认 1.4.x 分支的 `ManifestReadBenchmark` 是否在该路径上（应当存在，因为本提交是对其做小重构），以及所引用的 `ManifestFiles.write`、`ManifestLists.write`、`DataFiles.builder`、`Metrics` 等 API 签名在 1.4.x 上是否一致。如果 1.4.x 上 `ManifestListWriter`/`ManifestWriter` 不实现 `AutoCloseable`（早期版本可能未实现），try-with-resources 改造可能无法编译，需要先确认。该 benchmark 主要供开发者本地或 CI 跑性能测试，不参与发布构建产物，回迁优先级低。
