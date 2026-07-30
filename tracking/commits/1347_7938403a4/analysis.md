# 提交 1347：Core, Puffin: Add DV file writer (#11476)

## 提交信息

- **序号**：1347 / 4088
- **哈希**：7938403a437f81a502fd82053dccff10a1531ada
- **短哈希**：7938403a4
- **日期**：2024-11-06（Wed Nov 6 21:32:07 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core, Puffin: Add DV file writer (#11476)
- **PR/Issue**：#11476

## 总体目的

前面几个 DV 相关提交（#11444/#11446/#11464/#11467）已经让 Iceberg 能够"描述"和"索引"DV（删除向量）：metadata 层面支持 content offset/size/referencedDataFile，扫描层面 `DeleteFileIndex` 能够按数据文件路径索引 DV 并校验序列号。但还缺少一个关键环节——**如何把删除位置实际写入 Puffin 文件、生成 DV 对应的 `DeleteFile` 元数据**。

本提交新增 `DVFileWriter` 接口及其参考实现 `BaseDVFileWriter`，负责：
1. 接收一批 `(数据文件路径, 行号)` 删除请求，按数据文件路径聚合并用 `BitmapPositionDeleteIndex` 累积位图；
2. 可选地把这些删除与同一数据文件路径上已有的旧位置删除（DV 或 file-scoped position deletes）合并，避免同一文件多次删除时产生多个 DV；
3. 在 `close()` 时把每个数据文件对应的位图序列化为一个 Puffin blob（类型 `deletion-vector-v1`），写入单个 Puffin 文件；
4. 为每个数据文件生成一个 `DeleteFile` 元数据（共享同一 Puffin 文件路径与文件大小，但 content offset/length 不同），并产出 `DeleteWriteResult`，其中包含新生成的 DV 列表、被引用数据文件集合、可移除的旧删除文件列表。

配套地，本提交还增强了 Puffin 模块以支持加密输出文件、暴露文件位置与 blob 元数据；新增 `StandardBlobTypes.DV_V1` 常量；并把 `StructCopy.copy` 提升为 `StructLikeUtil.copy`（旧类标记 `@Deprecated`），让 util 包外部统一使用新入口。同时新增了 data 模块的 `TestDVWriters` 测试基类、Spark 端 `TestSparkDVWriters` 与 `DVWriterBenchmark`。

## 如何达成设计目的

- **接口设计**：`DVFileWriter` 只暴露两个核心方法 `delete(path, pos, spec, partition)` 与 `result()`（外加 `close()`），简单清晰；调用方按 (path, pos) 顺序传入删除请求，writer 内部按 path 聚合。
- **位图聚合**：`BaseDVFileWriter` 内部维护 `Map<String, Deletes> deletesByPath`，每个 `Deletes` 持有一个 `BitmapPositionDeleteIndex`、对应数据文件的 `PartitionSpec`/`StructLike partition`（用 `StructLikeUtil.copy` 拷贝避免外部复用）。
- **合并旧删除**：构造时接受 `Function<String, PositionDeleteIndex> loadPreviousDeletes`。`close()` 时对每个 path 调用 loader 获取旧索引，若非空则 `positions.merge(previousPositions)`，并把旧索引中所有 file-scoped 删除文件加入 `rewrittenDeleteFiles`（DV 也是 file-scoped），供上层在 commit 时 `removeDeletes` 移除。
- **Puffin 写入**：`close()` 中 `newWriter()` 通过 `fileFactory.newOutputFile()` 产出 `EncryptedOutputFile`，调用 `Puffin.write(EncryptedOutputFile)`（本提交新增的重载）创建 `PuffinWriter`。对每个 `Deletes` 调用 `writer.write(toBlob(positions, path))` 写入一个 blob 并拿到 `BlobMetadata`（offset/length）。
- **blob 构造**：`toBlob` 创建 `Blob`：
  - type = `StandardBlobTypes.DV_V1` = `"deletion-vector-v1"`
  - inputFields = `[MetadataColumns.ROW_POSITION.fieldId()]`（即 `_pos` 列）
  - snapshot ID/sequence number = -1（由 manifest 继承）
  - data = `positions.serialize()`（位图序列化字节）
  - compression = null（不压缩）
  - properties = `{referenced-data-file: <path>, cardinality: <n>}`
- **元数据生成**：`createDV(puffinPath, puffinFileSize, referencedDataFile)` 用 `FileMetadata.deleteFileBuilder` 构造 `DeleteFile`：
  - `ofPositionDeletes()`（DV 在 manifest 中 content 仍是 POSITION_DELETES）
  - `withFormat(FileFormat.PUFFIN)`
  - `withPath(puffinPath)`、`withFileSizeInBytes(puffinFileSize)`（共享 puffin 文件路径与大小）
  - `withReferencedDataFile(referencedDataFile)`、`withContentOffset(blobMetadata.offset())`、`withContentSizeInBytes(blobMetadata.length())`
  - `withRecordCount(positions.cardinality())`
- **Puffin 增强**：
  - `Puffin.write(EncryptedOutputFile)`：新重载，内部取出 `encryptingOutputFile()` 走原 `write(OutputFile)` 路径，让 Puffin 写入支持加密表。
  - `PuffinWriter.location()`：返回底层 `OutputFile.location()`，便于上层知道 Puffin 文件实际路径。
  - `PuffinWriter.write(Blob)` 返回 `BlobMetadata`（原 `add(Blob)` 改为调用 `write`）；`add` 保留以兼容 `FileAppender<Blob>` 接口。
- **工具方法迁移**：新增 `StructLikeUtil.copy(StructLike)`（内部仍委托 `StructCopy`），把 `BaseTaskWriter`、`ClusteredWriter`、`FanoutWriter` 中的 `StructCopy.copy` 调用替换为 `StructLikeUtil.copy`；`StructCopy` 标记 `@Deprecated since 1.8.0, will be removed in 1.9.0`。这样 DV writer 在 `util` 包外也能用同一入口。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/DVFileWriter.java`（新增）

**修改目的**：定义 DV 文件写入器接口。

**工作逻辑**：
```java
public interface DVFileWriter extends Closeable {
  void delete(String path, long pos, PartitionSpec spec, StructLike partition);
  DeleteWriteResult result();
}
```
`delete` 标记某数据文件 path 的第 pos 行删除；`result` 在 writer 关闭后返回写入结果（含新生成 DV、被引用数据文件、被替代的旧删除文件）。

### `core/src/main/java/org/apache/iceberg/deletes/BaseDVFileWriter.java`（新增，194 行）

**修改目的**：DV 文件写入器参考实现，按 path 聚合删除、合并旧删除、写出 Puffin、生成 DeleteFile 元数据。

**工作逻辑**：如"如何达成设计目的"所述。关键点：
- 内部类 `Deletes` 持有 path、`BitmapPositionDeleteIndex`、`PartitionSpec`、`StructLike partition`（用 `StructLikeUtil.copy` 拷贝）。
- `close()` 用 try-with-resources 关闭 `PuffinWriter`，先写所有 blob 再统一生成 `DeleteFile` 元数据，确保所有 DV 共享同一 `puffinPath`/`puffinFileSize`。
- 只把 file-scoped 旧删除文件加入 `rewrittenDeleteFiles`（DV 与文件级 position deletes 都是 file-scoped，partition-scoped position deletes 不在此列）。

### `core/src/main/java/org/apache/iceberg/puffin/StandardBlobTypes.java`

**修改目的**：新增 DV blob 类型常量。

**工作逻辑**：
```java
/** A serialized deletion vector according to the Iceberg spec */
public static final String DV_V1 = "deletion-vector-v1";
```

### `core/src/main/java/org/apache/iceberg/puffin/Puffin.java`

**修改目的**：让 Puffin 写入支持加密输出文件。

**工作逻辑**：
```java
public static WriteBuilder write(EncryptedOutputFile outputFile) {
  return new WriteBuilder(outputFile.encryptingOutputFile());
}
```
即把 `EncryptedOutputFile` 解包为底层 `OutputFile` 后走原 builder 路径。这样 DV 写入到加密表时也能正确处理加密。

### `core/src/main/java/org/apache/iceberg/puffin/PuffinWriter.java`

**修改目的**：暴露文件位置与 blob 元数据，支持 DV writer 拿到 offset/length。

**工作逻辑**：
- 新增字段 `private final OutputFile outputFile;` 并在构造函数中赋值。
- 新增 `public String location()` 返回 `outputFile.location()`。
- 新增 `public BlobMetadata write(Blob blob)`：原 `add(Blob)` 的实现移到 `write`，并返回写入后构造的 `BlobMetadata`（含 fileOffset、length 等）。`add(blob)` 改为简单调用 `write(blob)` 并忽略返回值，保持 `FileAppender<Blob>` 接口兼容。

### `core/src/main/java/org/apache/iceberg/util/StructLikeUtil.java`（新增，67 行）

**修改目的**：把 `io.StructCopy` 的功能提升到 util 包，供更多模块复用。

**工作逻辑**：
- `public static StructLike copy(StructLike struct)`：内部委托 `StructCopy.copy(struct)`。
- 内部 `StructCopy` 类逻辑与原 `io.StructCopy` 一致：递归拷贝 `StructLike` 的值（不处理 list/map）。

### `core/src/main/java/org/apache/iceberg/io/StructCopy.java`

**修改目的**：标记为废弃，引导使用 `StructLikeUtil.copy`。

**工作逻辑**：Javadoc 加 `@Deprecated since 1.8.0, will be removed in 1.9.0; use org.apache.iceberg.util.StructLikeUtil#copy instead.` 并加 `@Deprecated` 注解。`copy` 方法实现不变。

### `core/src/main/java/org/apache/iceberg/io/BaseTaskWriter.java`、`ClusteredWriter.java`、`FanoutWriter.java`

**修改目的**：把对 `StructCopy.copy` 的调用统一替换为 `StructLikeUtil.copy`，与废弃声明保持一致。

**工作逻辑**：三处都是把 `StructCopy.copy(...)` 改为 `StructLikeUtil.copy(...)`，并新增 import。逻辑无变化（`StructLikeUtil.copy` 内部仍调用 `StructCopy.copy`）。

### `data/src/test/java/org/apache/iceberg/io/TestDVWriters.java`（新增）

**修改目的**：为 `BaseDVFileWriter` 提供基础功能测试。

**工作逻辑**：
- 抽象测试基类，`@Parameters` 限定 `formatVersion = 3`（DV 仅 v3 表）。
- `setupTable()` 创建非分区表，`OutputFileFactory` 用 `FileFormat.PUFFIN`。
- `testBasicDVs`：写两个数据文件，构造 `BaseDVFileWriter`（loader 返回空 map 即不合并旧删除），交错调用 `delete(dataFile1.location(), 1L, ...)`、`delete(dataFile2.location(), 0L, ...)` 等 4 次删除，close 后断言 `result.deleteFiles().size() == 2`、`referencedDataFiles` 包含两个数据文件路径、`referencesDataFiles() == true`。
- 内部 `PreviousDeleteLoader` 实现 `Function<String, PositionDeleteIndex>`，用 `BaseDeleteLoader` 加载旧 DV/位置删除文件。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkDVWriters.java`（新增）

**修改目的**：Spark 端具体测试 DV writer 行为。

**工作逻辑**：继承 `TestDVWriters`，提供 Spark 实现的 `FileWriterFactory`、`writeData`、`toRow` 等。

### `spark/v3.5/spark/src/jmh/java/org/apache/iceberg/spark/source/DVWriterBenchmark.java`（新增，251 行）

**修改目的**：JMH 基准测试，评估 DV writer 在不同数据规模下的写入性能。

**工作逻辑**：用 Spark 生成数据，调用 `BaseDVFileWriter` 写入不同比例的删除，测量吞吐与延迟。

## 小结

- **成效**：Iceberg 现已具备完整的 DV 写入能力：`BaseDVFileWriter` 接收删除请求、聚合位图、可选合并旧删除、写出 Puffin 文件并生成对应 `DeleteFile` 元数据。配套的 Puffin 增强（加密支持、location/blob metadata 暴露）和工具方法整合（`StructLikeUtil.copy`）一并完成。这是 DV 功能链路中"写"侧的核心一环，配合 #11467 的"读"侧索引、#11464 的统计，构成完整的 DV 生命周期管理。
- **影响范围**：core 模块新增 `DVFileWriter`/`BaseDVFileWriter`、Puffin 模块小改动、util 模块新增 `StructLikeUtil`、io 模块 `StructCopy` 废弃；data/spark 模块新增测试与基准。
- **回迁到 1.4.x 的注意事项**：
  - DV 是 v3 表特性，本提交依赖 #11444（Schema 测试去泛型化，便于 v3 测试）、#11446（DeleteFile 的 content offset/size/referencedDataFile 字段）、`BitmapPositionDeleteIndex.serialize`、`PositionDeleteIndex.merge` 等较新 API。
  - 1.4.x 作为维护分支通常不支持 v3 表，单独回迁本提交无实际用途且会引入大量编译依赖。
  - 若 1.4.x 计划支持 v3 表的 DV 写入，需要整套 DV 相关提交（#11444/#11446/#11464/#11467/#11476 及后续引擎集成）一并回迁。
  - **不建议单独回迁**。
