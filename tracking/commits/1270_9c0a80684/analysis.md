# 提交 1270：Spark 3.4: Action to remove dangling deletes (#11377)

## 提交信息

- **序号**：1270 / 4088
- **哈希**：9c0a8068462720992b955442b5324ef9b0afee83
- **短哈希**：9c0a80684
- **日期**：2024-10-23（Wed Oct 23 10:23:53 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Spark 3.4: Action to remove dangling deletes (#11377)
- **PR/Issue**：#11377，回移植自 #9724

## 总体目的

在 Iceberg 的 Copy-on-Write / Merge-on-Read 模型中，删除操作通过 delete file（位置删除 position delete 和等值删除 equality delete）来标记哪些行被删除。每个 delete file 关联一个数据序列号（data sequence number），表示该删除操作生效的"时间点"。随着表不断演进——数据文件被重写（`RewriteDataFiles`）、分区演化为新 spec、旧数据文件被淘汰——会出现一类"悬空删除文件"（dangling delete files）：

- **位置删除文件**：其序列号 **小于** 同分区中所有存活数据文件的最小数据序列号时，它要删除的行已不存在于任何存活数据文件中，因此该删除文件已无作用。
- **等值删除文件**：其序列号 **小于等于** 同分区中所有存活数据文件的最小数据序列号时，同样不再适用于任何存活数据文件。
- **无数据文件的分区**：若某分区已无任何存活数据文件，则该分区内的所有删除文件都是悬空的。

这些悬空删除文件不会自动清除，会持续占用存储空间并增加读取时的扫描开销。Iceberg 的 `ManifestFilterManager` 在每次 commit 时会对**非分区表**执行表级别的悬空删除清理，但对于**分区表**，由于需要逐分区比较序列号，commit 链路中并未实现此逻辑。

本提交为 Spark 3.4 模块新增 `RemoveDanglingDeletesSparkAction`，提供一个独立的 Spark Action 来扫描并移除分区表中的悬空删除文件。同时将该 Action 集成到 `RewriteDataFilesSparkAction` 中，使得在数据文件重写后可自动清理因重写而产生的悬空删除文件。这是对 main 分支 PR #9724 的 Spark 3.4 回移植。

## 如何达成设计目的

整体设计分为四个层面：

1. **修复 `SparkContentFile.specId()`**：原实现始终返回 `-1`，无法获取删除文件所属的分区 spec ID。悬空删除检测需要按 spec_id 分组（分区演化后不同 spec 的分区键结构不同），因此必须修复此方法，从 Spark Row 中实际读取 `spec_id` 字段。

2. **新建 `RemoveDanglingDeletesSparkAction`**：利用 Spark DataFrame API 对表的 `ENTRIES` 元数据表进行操作——先按分区键和 spec_id 分组求各分区的最小数据序列号，再左连接删除文件条目，通过序列号比较筛选出悬空删除文件，最后通过 `RewriteFiles` API 提交删除。

3. **集成到 `RewriteDataFilesSparkAction`**：新增 `remove-dangling-deletes` 选项（默认关闭）。在数据文件重写完成后，若开启此选项，则调用 `RemoveDanglingDeletesSparkAction` 清理悬空删除文件，并将移除数量计入结果。

4. **注册到 `SparkActions`**：实现 `ActionsProvider` 接口中新增的 `removeDanglingDeleteFiles(Table)` 方法，使用户可通过 `SparkActions.get().removeDanglingDeleteFiles(table)` 独立调用。

**前置依赖**：`api` 模块的 `RemoveDanglingDeleteFiles` 接口、`RewriteDataFiles` 中的 `REMOVE_DANGLING_DELETES` 常量、以及 `ImmutableRemoveDanglingDeleteFiles.Result` 等已由 main 分支的原始 PR #9724 添加，本提交仅涉及 Spark 3.4 实现层。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkContentFile.java`（修改，+7 / -1）

**修改目的**：修复 `specId()` 方法，使其能从 Spark Row 中读取实际的 `spec_id` 字段值，而非始终返回 `-1`。

**工作逻辑**：

`SparkContentFile<F>` 是 Iceberg 内容文件（`DataFile`/`DeleteFile`）在 Spark 侧的包装抽象，内部持有一个 Spark `Row`（`wrapped`）和各字段在 Row 中的位置索引。原实现中 `specId()` 硬编码返回 `-1`：

```java
@Override
public int specId() {
  return -1;
}
```

修改后新增 `fileSpecIdPosition` 字段，在构造器中通过 `positions.get(DataFile.SPEC_ID.name())` 获取 `spec_id` 字段的位置索引，并在 `specId()` 中读取该字段：

```java
private final int fileSpecIdPosition;  // 新增字段

// 构造器中：
this.fileSpecIdPosition = positions.get(DataFile.SPEC_ID.name());

@Override
public int specId() {
  if (wrapped.isNullAt(fileSpecIdPosition)) {
    return -1;
  }
  return wrapped.getAs(fileSpecIdPosition);
}
```

`isNullAt` 检查是为了兼容 `spec_id` 字段可能为 null 的旧数据。此修复是悬空删除检测的前提——`RemoveDanglingDeletesSparkAction` 需要按 `spec_id` 分组来正确处理分区演化后的表。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RemoveDanglingDeletesSparkAction.java`（新增，171 行）

**修改目的**：实现 Spark 版本的悬空删除文件清理 Action。

**工作逻辑**：

该类继承 `BaseSnapshotUpdateSparkAction<RemoveDanglingDeletesSparkAction>` 并实现 `RemoveDanglingDeleteFiles` 接口。核心流程如下：

#### `execute()` 方法——入口与快速返回

```java
public Result execute() {
  if (table.specs().size() == 1 && table.spec().isUnpartitioned()) {
    // ManifestFilterManager already performs this table-wide delete on each commit
    return ImmutableRemoveDanglingDeleteFiles.Result.builder()
        .removedDeleteFiles(Collections.emptyList())
        .build();
  }
  String desc = String.format("Removing dangling delete files in %s", table.name());
  JobGroupInfo info = newJobGroupInfo("REMOVE-DELETES", desc);
  return withJobGroupInfo(info, this::doExecute);
}
```

对非分区表直接返回空结果（`ManifestFilterManager` 在每次 commit 时已处理）。对分区表，设置 Spark JobGroup 后调用 `doExecute()`。

#### `doExecute()` 方法——执行删除

```java
Result doExecute() {
  RewriteFiles rewriteFiles = table.newRewrite();
  List<DeleteFile> danglingDeletes = findDanglingDeletes();
  for (DeleteFile deleteFile : danglingDeletes) {
    rewriteFiles.deleteFile(deleteFile);
  }
  if (!danglingDeletes.isEmpty()) {
    commit(rewriteFiles);
  }
  return ImmutableRemoveDanglingDeleteFiles.Result.builder()
      .removedDeleteFiles(danglingDeletes)
      .build();
}
```

通过 `RewriteFiles` API（只删除文件、不添加新文件）提交一个新快照来移除悬空删除文件。若未发现悬空删除则不产生 commit。

#### `findDanglingDeletes()` 方法——核心检测逻辑

这是整个 Action 的核心，利用 Spark DataFrame 操作完成悬空删除检测，分为四步：

**第一步：计算每个分区的最小数据序列号**

```java
Dataset<Row> minSequenceNumberByPartition =
    loadMetadataTable(table, MetadataTableType.ENTRIES)
        .filter("data_file.content == 0 AND status < 2")  // content==0 为数据文件, status<2 为存活(ADDED/EXISTING)
        .selectExpr("data_file.partition as partition", "data_file.spec_id as spec_id", "sequence_number")
        .groupBy("partition", "spec_id")
        .agg(min("sequence_number"))
        .toDF("grouped_partition", "grouped_spec_id", "min_data_sequence_number");
```

从 `ENTRIES` 元数据表中筛选存活的数据文件（`content == 0` 表示数据文件，`status < 2` 排除已删除条目），按分区键和 spec_id 分组，求每组的最小序列号。

**第二步：获取存活删除文件条目**

```java
Dataset<Row> deleteEntries =
    loadMetadataTable(table, MetadataTableType.ENTRIES)
        .filter("data_file.content != 0 AND status < 2");  // content!=0 为删除文件
```

**第三步：左连接并筛选悬空删除**

```java
Column joinOnPartition =
    deleteEntries.col("data_file.spec_id").equalTo(minSequenceNumberByPartition.col("grouped_spec_id"))
        .and(deleteEntries.col("data_file.partition").equalTo(minSequenceNumberByPartition.col("grouped_partition")));

Column filterOnDanglingDeletes =
    col("min_data_sequence_number").isNull()  // 分区无数据文件
        .or(col("data_file.content").equalTo("1")  // 位置删除: 序列号 < 最小数据序列号
            .and(col("sequence_number").$less(col("min_data_sequence_number"))))
        .or(col("data_file.content").equalTo("2")  // 等值删除: 序列号 <= 最小数据序列号
            .and(col("sequence_number").$less$eq(col("min_data_sequence_number"))));

Dataset<Row> danglingDeletes =
    deleteEntries.join(minSequenceNumberByPartition, joinOnPartition, "left")
        .filter(filterOnDanglingDeletes)
        .select("data_file.*");
```

左连接确保即使某分区无数据文件（`min_data_sequence_number` 为 null），该分区的删除文件也会被保留。筛选条件严格对应悬空删除的三种定义：
- `isNull()`：分区无数据文件，所有删除文件都是悬空的；
- `content == 1`（位置删除）且 `sequence_number < min`：位置删除只影响序列号更大的数据文件；
- `content == 2`（等值删除）且 `sequence_number <= min`：等值删除影响序列号相同或更大的数据文件（等值删除对同序列号的数据文件也生效，因此用 `<=`）。

**第四步：收集结果并包装为 DeleteFile**

```java
return danglingDeletes.collectAsList().stream()
    .map(row -> deleteFileWrapper(danglingDeletes.schema(), row))
    .collect(Collectors.toList());
```

`collectAsList()` 将结果拉取到 Driver 端，然后通过 `deleteFileWrapper` 将每行包装为 `SparkDeleteFile`。注释说明"map on driver because SparkDeleteFile is not serializable"。

#### `deleteFileWrapper()` 方法——行到 DeleteFile 的转换

```java
private DeleteFile deleteFileWrapper(StructType sparkFileType, Row row) {
  int specId = row.getInt(row.fieldIndex("spec_id"));
  Types.StructType combinedFileType = DataFile.getType(Partitioning.partitionType(table));
  Types.StructType projection = DataFile.getType(table.specs().get(specId).partitionType());
  return new SparkDeleteFile(combinedFileType, projection, sparkFileType).wrap(row);
}
```

根据行中的 `spec_id` 获取对应的分区 spec，构造正确的 `SparkDeleteFile` 类型投影后包装 Row。`combinedFileType` 使用表的联合分区类型，`projection` 使用具体 spec 的分区类型。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`（修改，+33 / -12）

**修改目的**：将悬空删除清理集成到数据文件重写流程中，使得重写后可自动清理不再适用的删除文件。

**工作逻辑**：

1. **注册新选项**：在 `SUPPORTED_OPTIONS` 集合中添加 `REMOVE_DANGLING_DELETES`，并在 `init()` 方法中读取该选项：

   ```java
   private static final Set<String> SUPPORTED_OPTIONS = ImmutableSet.of(
       ..., OUTPUT_SPEC_ID, REMOVE_DANGLING_DELETES);

   // init() 方法中：
   removeDanglingDeletes =
       PropertyUtil.propertyAsBoolean(
           options(), REMOVE_DANGLING_DELETES, REMOVE_DANGLING_DELETES_DEFAULT);
   ```

   默认值为 `false`，需显式开启。

2. **改造 `execute()` 方法**：原方法在 partial-progress 启用/未启用时分别调用 `doExecuteWithPartialProgress` / `doExecute` 后直接返回 `Result`。修改后改为获取 `Builder` 而非最终 `Result`，在重写完成后可选地执行悬空删除清理：

   ```java
   Builder resultBuilder =
       partialProgressEnabled
           ? doExecuteWithPartialProgress(ctx, groupStream, commitManager(startingSnapshotId))
           : doExecute(ctx, groupStream, commitManager(startingSnapshotId));
   if (removeDanglingDeletes) {
     RemoveDanglingDeletesSparkAction action =
         new RemoveDanglingDeletesSparkAction(spark(), table);
     int removedCount = Iterables.size(action.execute().removedDeleteFiles());
     resultBuilder.removedDeleteFilesCount(removedCount);
   }
   return resultBuilder.build();
   ```

   `removedDeleteFilesCount` 被写入结果，供调用方获知清理了多少个删除文件。

3. **方法返回类型变更**：`doExecute` 和 `doExecuteWithPartialProgress` 的返回类型从 `Result` 改为 `Builder`，去掉末尾的 `.build()` 调用，使调用方（`execute`）能在构建最终结果前追加 `removedDeleteFilesCount`。新增 `import org.apache.iceberg.actions.ImmutableRewriteDataFiles.Result.Builder` 和 `import org.apache.iceberg.relocated.com.google.common.collect.Iterables`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java`（修改，+6 / -0）

**修改目的**：在 `SparkActions` 中注册新的 `removeDanglingDeleteFiles` 工厂方法。

**工作逻辑**：

实现 `ActionsProvider` 接口（已在 api 模块中定义）中的 `removeDanglingDeleteFiles(Table)` 方法：

```java
@Override
public RemoveDanglingDeleteFiles removeDanglingDeleteFiles(Table table) {
  return new RemoveDanglingDeletesSparkAction(spark, table);
}
```

使用户可通过 `SparkActions.get().removeDanglingDeleteFiles(table).execute()` 独立调用悬空删除清理，而不仅限于在 `RewriteDataFiles` 中使用。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRemoveDanglingDeleteAction.java`（新增，426 行）

**修改目的**：为 `RemoveDanglingDeletesSparkAction` 提供独立的单元测试。

**工作逻辑**：

测试类继承 `SparkTestBase`，使用 `HadoopTables` 创建格式版本 2 的表。定义了 4 个分区（c1=a/b/c/d）的数据文件和对应的 position/equality 删除文件，以及非分区表的文件。三个核心测试：

1. **`testPartitionedDeletesWithLesserSeqNo`**：
   - 先在分区 b/c/d 追加数据文件（seq=1），然后在分区 a/b 追加删除文件（seq=2），最后在分区 a/b/c/d 追加新数据文件（seq=3）。
   - 分区 a 的删除文件序列号（2）小于该分区数据文件的最小序列号（3），因此全部 4 个删除文件（2 个 position + 2 个 equality）都是悬空的，应被移除。
   - 分区 b 的删除文件序列号（2）小于数据文件最小序列号（1 不成立，因为 b 有 seq=1 和 seq=3 的数据文件，min=1），所以分区 b 的删除文件不是悬空的（2 > 1 for position, 2 > 1 for equality）。
   - 验证移除了恰好 4 个文件，且移除后 entries 表的状态正确。

2. **`testPartitionedDeletesWithEqSeqNo`**：
   - 先在分区 a/c/d 追加数据文件（seq=1），然后在同一 RowDelta 中追加分区 a/b 的数据文件和删除文件（seq=2）。
   - 分区 b 无 seq=1 的数据文件，其数据文件和删除文件都在 seq=2。对 equality 删除，序列号等于数据文件最小序列号（2 == 2），因此 equality 删除是悬空的。对 position 删除，序列号等于最小序列号（2 == 2），不满足 `<` 条件，因此 position 删除不是悬空的。
   - 验证移除了恰好 2 个 equality 删除文件（分区 b 的两个），position 删除文件保留。

3. **`testUnpartitionedTable`**：
   - 非分区表添加删除文件后添加数据文件，调用 Action 后验证返回空结果（非分区表由 `ManifestFilterManager` 在 commit 时处理，Action 直接跳过）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteDataFilesAction.java`（修改，+193 / -1）

**修改目的**：测试在 `RewriteDataFiles` 中启用 `REMOVE_DANGLING_DELETES` 选项后的端到端行为，包括分区演化场景。

**工作逻辑**：

新增两个测试方法和若干辅助方法：

1. **`testRemoveDangledEqualityDeletesPartitionEvolution`**：
   - 创建按 c1 分区的 v2 表，写入 4 个数据文件（seq=1），写入 2 个 equality 删除文件（seq=2, 3），执行分区演化（添加 c3 分区字段），再写入新数据文件（seq=4/5）。
   - 调用 `RewriteDataFiles` 并启用 `REMOVE_DANGLING_DELETES` 和 `REWRITE_ALL`，过滤 c1=1 分区。
   - 验证：重写后无存活删除文件（`removedDeleteFilesCount == 2`），新增 2 个数据文件，重写 3 个数据文件，分区 c1=1 的最小序列号为 5（新写入的文件），数据内容不变，7 个快照，5 个文件。

2. **`testRemoveDangledPositionDeletesPartitionEvolution`**：
   - 类似场景，但使用 position 删除。写入 4 个数据文件（seq=1），写入 1 个 position 删除（seq=2），分区演化后写入新数据文件（seq=3）。
   - 重写 c1=1 分区并启用 `REMOVE_DANGLING_DELETES`。
   - 验证：重写 2 个数据文件为 1 个，移除 1 个 position 删除文件，分区最小序列号为 3，5 个快照，`total-position-deletes` 为 0，数据内容不变。

3. **辅助方法**：
   - `shouldHaveMinSequenceNumberInPartition(Table, String, long)`：通过 Spark 读取 ENTRIES 元数据表，过滤指定分区后聚合求最小序列号并断言。
   - `writeRecords(List<ThreeColumnRecord>)`：通过 DataFrame 写入记录。
   - `writeEqDeleteRecord(...)` 系列：构造 equality 删除文件并提交。使用 `GenericAppenderFactory` 创建 `EqualityDeleteWriter`，通过 `OutputFileFactory` 生成输出文件路径，按分区键写入。
   - `createPartitionKey(Table, Record)`：根据表 spec 和记录构造分区键。
   - `createEncryptedOutputFile(PartitionKey, OutputFileFactory)`：根据是否有分区键创建加密输出文件。
   - 修改 `writePosDeletesToFile` 中的文件路径生成，添加 `.parquet` 扩展名（`FileFormat.PARQUET.addExtension(...)`）。

## 小结

- **成效**：为 Spark 3.4 模块新增了独立的悬空删除文件清理能力（`RemoveDanglingDeletesSparkAction`），并使其可集成到 `RewriteDataFiles` 流程中。填补了分区表在 commit 链路中缺失的逐分区悬空删除清理能力，有效减少存储浪费和读取开销。同时修复了 `SparkContentFile.specId()` 始终返回 `-1` 的 bug，使 Spark 侧能正确获取文件的分区 spec ID。
- **影响范围**：涉及 `spark/v3.4` 模块的 4 个生产文件和 2 个测试文件。`SparkContentFile.specId()` 修复对依赖该方法的代码有行为变更影响（从始终 `-1` 变为返回真实值），但这是正确性修复。`RemoveDanglingDeletesSparkAction` 为纯新增。`RewriteDataFilesSparkAction` 的改动仅在 `remove-dangling-deletes` 选项开启时生效（默认关闭），向后兼容。
- **回迁到 1.4.x 的注意事项**：
  1. **前置依赖**：需确认 1.4.x 分支的 `api` 模块中已存在 `RemoveDanglingDeleteFiles` 接口、`RewriteDataFiles` 中的 `REMOVE_DANGLING_DELETES`/`REMOVE_DANGLING_DELETES_DEFAULT` 常量、以及 `ImmutableRemoveDanglingDeleteFiles.Result`。若不存在，需先回移植 main 分支 PR #9724 的 api/core 部分。
  2. **`SparkContentFile.specId()` 修复**：此修复改变行为，需检查 1.4.x 上是否有代码依赖 `specId()` 返回 `-1` 的旧行为。
  3. **`SparkDeleteFile` 构造器签名**：`deleteFileWrapper` 中使用的 `new SparkDeleteFile(combinedFileType, projection, sparkFileType)` 三参构造器需确认在 1.4.x 上存在且签名一致。
  4. **`BaseSnapshotUpdateSparkAction` 基类**：需确认 1.4.x 上的 `withJobGroupInfo`、`commit`、`loadMetadataTable` 等方法可用。
