# 提交 0440：Spark 3.4: Rework DeleteFileIndexBenchmark (#9600)

## 提交信息

- **序号**：0440
- **哈希**：e8c197d360ca75d3be8f9c886615a196c63d36c5
- **短哈希**：e8c197d36
- **日期**：2024-02-01 09:34:25 -0800
- **作者**：Anton Okolnychyi <aokolnychyi@apple.com>
- **提交说明**：Spark 3.4: Rework DeleteFileIndexBenchmark (#9600)
- **PR/Issue**：#9600

## 总体目的

本提交对 `DeleteFileIndexBenchmark` 这个 JMH 基准测试做了大幅简化与重写，核心目的是去掉冗余、脆弱且慢的数据准备逻辑，改用 Iceberg core 测试工具类 `FileGenerationUtil` 直接生成数据文件和删除文件的元数据，使基准测试更专注于真正要度量的事情——`DeleteFileIndex` 的构建性能，而不是被数据准备阶段的开销和复杂度拖累。

原先的基准测试在 `initDataAndDeletes` 中走了非常重的路径：它用 Spark `RandomData` 生成真实 RDD 数据，通过 `writeTo` 真正写入 Parquet 文件，再通过 `DELETE FROM` SQL 生成真实的删除文件，然后读取快照里 `addedDataFiles`/`addedDeleteFiles` 拿到模板文件，再用 `DataFiles.builder(...).copy(...)` 配合随机路径"复制"出 5 万个数据文件副本和 100 个删除文件副本。这套流程有几个明显问题：一是真正写文件和跑 SQL 极慢且依赖 Spark 运行时；二是"复制数据文件"这种 hack 依赖快照里有且仅有一个 added 文件（`Iterables.getOnlyElement`），稍有不符就崩；三是它区分了"真实数据文件"（25 个）和"副本数据文件"（5 万个），逻辑复杂且意图不清。

重写后，每个分区直接用 `FileGenerationUtil.generateDataFile` 和 `FileGenerationUtil.generatePositionDeleteFile` 生成 5 万个数据文件和 100 个删除文件的元数据（带随机 metrics），通过 `RowDelta` 一次性提交，不再写真实数据、不再跑 DELETE SQL、不再做文件复制。这使得基准测试更快、更稳定、更易理解，也让 `DeleteFileIndex` 在大规模文件场景下的性能度量更纯粹。

## 如何达成设计目的

实现路径很直接：在 `build.gradle` 中给 spark-extensions 模块新增对 `iceberg-core` 测试产物（`testArtifacts` 配置）的依赖，从而能够使用 `core/src/test` 下的 `FileGenerationUtil` 和 `TestHelpers.Row`；然后在基准测试类中删除所有真实数据生成、SQL 删除、文件复制相关的方法和常量，`initDataAndDeletes` 改为遍历分区，用 `FileGenerationUtil` 生成元数据并通过 `RowDelta` 提交。常量也做了精简，去掉了 `NUM_REAL_DATA_FILES_PER_PARTITION`、`NUM_REPLICA_DATA_FILES_PER_PARTITION`、`NUM_ROWS_PER_DATA_FILE`，只保留统一的 `NUM_DATA_FILES_PER_PARTITION = 50_000`。

## 修改详情

### spark/v3.4/build.gradle

**修改目的**：让 spark-extensions 模块的测试（含 JMH 基准）能够访问 `iceberg-core` 的测试工具类。

**工作逻辑**：在 `iceberg-spark-extensions` 项目的依赖块中新增一行：
```gradle
testImplementation project(path: ':iceberg-core', configuration: 'testArtifacts')
```
`testArtifacts` 是 Iceberg 项目的约定配置，用于把一个模块的测试代码（如 `FileGenerationUtil`、`TestHelpers`）打包成可被其他模块测试复用的产物。这一行是后续在基准测试中使用 `FileGenerationUtil` 和 `TestHelpers.Row` 的前提。

### spark/v3.4/spark-extensions/src/jmh/java/org/apache/iceberg/DeleteFileIndexBenchmark.java

**修改目的**：重写数据准备逻辑，去掉真实数据写入与文件复制，改用 `FileGenerationUtil` 生成文件元数据。

**工作逻辑**：改动可归纳为四部分：

1. **import 清理**：移除 `lit`、`LocationProvider`、`Iterables`、`SparkSchemaUtil`、`RandomData`、`JavaRDD`、`JavaSparkContext`、`Dataset`、`Row`、`InternalRow`、`StructType` 等 Spark 数据生成相关 import；新增依赖 `FileGenerationUtil`（来自 iceberg-core 测试）和 `TestHelpers.Row`。

2. **常量精简**：删除 `NUM_REAL_DATA_FILES_PER_PARTITION = 25`、`NUM_REPLICA_DATA_FILES_PER_PARTITION = 50_000`、`NUM_ROWS_PER_DATA_FILE = 500`，新增统一的 `NUM_DATA_FILES_PER_PARTITION = 50_000`。这意味着每个分区现在直接生成 5 万个数据文件，不再区分"真实"与"副本"。

3. **`initDataAndDeletes` 重写**：原方法先按 schema 生成随机 DataFrame，写 25 次真实文件，跑一次 `DELETE FROM` 生成删除文件，再从快照取模板文件复制 5 万份数据副本和 100 份删除副本，多次 commit。新方法改为：
```java
for (int partitionOrdinal = 0; partitionOrdinal < NUM_PARTITIONS; partitionOrdinal++) {
  StructLike partition = TestHelpers.Row.of(partitionOrdinal);
  RowDelta rowDelta = table.newRowDelta();
  for (int fileOrdinal = 0; fileOrdinal < NUM_DATA_FILES_PER_PARTITION; fileOrdinal++) {
    DataFile dataFile = FileGenerationUtil.generateDataFile(table, partition);
    rowDelta.addRows(dataFile);
  }
  for (int fileOrdinal = 0; fileOrdinal < NUM_DELETE_FILES_PER_PARTITION; fileOrdinal++) {
    DeleteFile deleteFile = FileGenerationUtil.generatePositionDeleteFile(table, partition);
    rowDelta.addDeletes(deleteFile);
  }
  rowDelta.commit();
}
```
每个分区一个 `RowDelta`，里面 5 万数据文件 + 100 删除文件，一次 commit。`FileGenerationUtil.generateDataFile` 会用 `LocationProvider` 生成随机路径，构造带随机 metrics 的 `DataFile` 元数据（不写真实数据）；`generatePositionDeleteFile` 类似，构造 position delete 文件元数据。这样 `DeleteFileIndex` 在 benchmark 中要处理的文件元数据规模与原先一致（5 万/分区），但生成成本和稳定性大幅改善。

4. **删除辅助方法**：移除 `loadAddedDataFile`、`loadAddedDeleteFile`、`appendAsFile`、`randomDataDF` 四个私有方法，它们都是为旧的真实数据写入流程服务的，重写后不再需要。`initDataAndDeletes` 的 throws 声明也去掉了 `NoSuchTableException`（不再调用 `writeTo`）。

## 小结

本提交是一个典型的"基准测试卫生"改动：用专门的测试工具类 `FileGenerationUtil` 替代手写的、依赖真实 Spark 写入与 SQL 的数据准备流程，使 `DeleteFileIndexBenchmark` 更快、更稳定、更聚焦于被测对象本身。它体现了 Iceberg 社区对基准测试质量的要求——度量目标要纯粹，准备阶段不能引入额外噪声。新增 `iceberg-core` 测试产物依赖也示范了 Iceberg 内部跨模块复用测试工具的约定（`testArtifacts` 配置），这种模式在其他 benchmark 和测试中也会反复出现。值得注意的是，重写后每个分区只有一次 `RowDelta` commit（原先有多次 append + 一次 rowDelta），文件元数据规模保持不变但提交次数减少，这对 benchmark 的预热和数据准备阶段本身也有加速效果。
