# 提交 1342：Core: Support DVs in DeleteFileIndex (#11467)

## 提交信息

- **序号**：1342 / 4088
- **哈希**：5bd314bdf6c3b5e0e5346d0f7408353bdf31bc81
- **短哈希**：5bd314bdf
- **日期**：2024-11-05（Tue Nov 5 15:52:24 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Support DVs in DeleteFileIndex (#11467)
- **PR/Issue**：#11467

## 总体目的

Iceberg 表格式 v3 引入了 DV（Deletion Vector，删除向量）这一新型删除文件：它使用 Puffin 文件格式存储一个位图，表示某个数据文件中哪些行被删除。与传统按位置列式存储的 POSITION_DELETES 文件相比，DV 更紧凑、读取开销更小，并按数据文件粒度引用（一个 DV 对应一个数据文件）。

`DeleteFileIndex` 是 Iceberg 在执行扫描时索引删除文件、按数据文件过滤出"应用哪些删除"的核心组件。此前该索引只支持等值删除（EQUALITY_DELETES）和位置删除（POSITION_DELETES）两类。本提交将 DV 作为第三类删除文件纳入索引体系，使扫描时能够正确地：
1. 将 DV 单独归类到按引用数据文件路径索引的 `dvByPath` 映射中；
2. 在 `forDataFile` 时优先返回 DV，避免将 DV 与传统位置删除混合应用；
3. 校验 DV 与数据文件的序列号顺序及单一性约束。

这是 DV 功能落地到 core 模块扫描侧的关键一步，为后续在 commit/scan/snapshot stats 中统计 DV、Puffin 写入 DV 等配套工作铺平道路。

## 如何达成设计目的

- **识别 DV**：在 `ContentFileUtil` 中新增 `isDV(DeleteFile)` 方法，以 `deleteFile.format() == FileFormat.PUFFIN` 作为判定条件。DV 实际上是一种 content 类型为 POSITION_DELETES 但文件格式为 PUFFIN 的特殊删除文件，因此通过 format 来区分。
- **按数据文件路径索引**：在 `DeleteFileIndex` 构造期间，对每个 POSITION_DELETES 类型的删除文件先判断是否为 DV。若是，调用新方法 `add(Map<String, DeleteFile> dvByPath, DeleteFile dv)`，以 `referencedDataFile()`（即 DV 引用的数据文件路径）为键存入 `dvByPath`；否则沿用旧逻辑加入 `posDeletesByPath`/`posDeletesByPartition`。
- **查询时短路返回**：在 `forDataFile(...)` 中调用 `findDV(seq, dataFile)`。若命中 DV：
  - 仅 DV 命中（无 global、无 eq 分区删除）时直接返回单元素数组，跳过位置删除查询；
  - 同时存在等值删除时，只将 global + eqPartition + DV 拼接，**不**再返回传统位置删除；
  - 若无 DV，则走原 `posPartition + posPath` 路径。
- **校验**：`findDV` 中校验 `dv.dataSequenceNumber() >= seq`（DV 序列号不小于数据文件序列号）；`add` 中用 `putIfAbsent` 检查同一数据文件路径只能有一个 DV，否则抛出 `ValidationException`。
- **可观测性**：新增 `ContentFileUtil.dvDesc(DeleteFile)` 用于在异常信息中打印 DV 的 location/offset/length/referencedDataFile，便于排错。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java`

**修改目的**：让 `DeleteFileIndex` 支持 DV 的索引构建与按数据文件查询。

**工作逻辑**：

1. 新增字段 `private final Map<String, DeleteFile> dvByPath;`，键为 DV 引用的数据文件路径，值为 DV 对应的 `DeleteFile`。
2. 构造函数新增 `dvByPath` 参数；`hasPosDeletes` 判定改为 `posDeletesByPartition != null || posDeletesByPath != null || dvByPath != null`，使 DV 也算作"存在位置删除"以正确触发 isEmpty 等逻辑。
3. `forDataFile(long sequenceNumber, DataFile file)` 改为先尝试 `findDV(seq, file)`：
   ```java
   DeleteFile dv = findDV(sequenceNumber, file);
   if (dv != null && global == null && eqPartition == null) {
     return new DeleteFile[] {dv};
   } else if (dv != null) {
     return concat(global, eqPartition, new DeleteFile[] {dv});
   } else {
     DeleteFile[] posPartition = findPosPartitionDeletes(sequenceNumber, file);
     DeleteFile[] posPath = findPathDeletes(sequenceNumber, file);
     return concat(global, eqPartition, posPartition, posPath);
   }
   ```
   这是因为 DV 已经包含了对该数据文件全部删除位置的完整描述，与传统位置删除文件互斥，不应叠加应用。
4. 新增 `private DeleteFile findDV(long seq, DataFile dataFile)`：从 `dvByPath` 按 `dataFile.location()` 取 DV，命中时执行 `ValidationException.check(dv.dataSequenceNumber() >= seq, ...)`。
5. `isEmpty()`/`hasDeletes()` 等公共路径无变化（依赖 `hasPosDeletes`/`hasEqDeletes`）。
6. `builderFor(...)` 流程中：
   - 新增局部 `Map<String, DeleteFile> dvByPath = Maps.newHashMap();`
   - 在遍历 `files` 时，对 `POSITION_DELETES` 走 `ContentFileUtil.isDV(file)` 分支：是 DV 则 `add(dvByPath, file)`，否则走原 `add(posDeletesByPath, posDeletesByPartition, file)`。
   - `build()` 时传入 `dvByPath.isEmpty() ? null : dvByPath`，保持空集合不占用实例字段。
7. 新增私有方法 `add(Map<String, DeleteFile> dvByPath, DeleteFile dv)`：
   ```java
   String path = dv.referencedDataFile();
   DeleteFile existingDV = dvByPath.putIfAbsent(path, dv);
   if (existingDV != null) {
     throw new ValidationException(
         "Can't index multiple DVs for %s: %s and %s",
         path, ContentFileUtil.dvDesc(dv), ContentFileUtil.dvDesc(existingDV));
   }
   ```

### `core/src/main/java/org/apache/iceberg/util/ContentFileUtil.java`

**修改目的**：集中提供 DV 的判定与描述工具方法。

**工作逻辑**：

- 新增 import `org.apache.iceberg.FileFormat`。
- `public static boolean isDV(DeleteFile deleteFile)`：返回 `deleteFile.format() == FileFormat.PUFFIN`。这是基于规格定义：DV 在 manifest 中存储为 `content=POSITION_DELETES` 但其 `file_format=PUFFIN`，因此用 format 字段唯一区分 DV 与传统位置删除。
- `public static String dvDesc(DeleteFile deleteFile)`：返回形如 `DV{location=..., offset=..., length=..., referencedDataFile=...}` 的字符串，用于异常信息中可读地展示 DV。

### `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java`

**修改目的**：为 DV 索引功能补充测试覆盖。

**工作逻辑**：

- 引入 `assumeThat`、`Collections`、`ValidationException`、`ContentFileUtil` 等。
- `testMixDeleteFilesAndDVs`：构造混合 DV 与位置删除的列表（FILE_A 有 seq=2 的 DV 和 seq=1 的位置删除；FILE_B 仅有两条位置删除），断言 FILE_A 的 `forDataFile` 只返回 DV（且 `referencedDataFile` 与 FILE_A 路径匹配），FILE_B 返回两条位置删除文件。`assumeThat(formatVersion).isGreaterThanOrEqualTo(3)` 确保仅在 v3 表上执行。
- `testMultipleDVs`：对同一 FILE_A 构造两个 DV（seq=1 和 seq=2），断言 build 时抛出 `ValidationException` 且信息包含 "Can't index multiple DVs for"。
- `testInvalidDVSequenceNumber`：构造 seq=1 的 DV，并以 seq=2 调用 `forDataFile`，断言抛出 `ValidationException` 且信息包含 "must be greater than or equal to data file sequence number"。

## 小结

- **成效**：`DeleteFileIndex` 现可对 DV 进行索引、查询与校验。DV 与传统位置删除在索引层面互斥，单数据文件至多一个 DV，且 DV 的 dataSequenceNumber 必须不小于被查询数据文件的 sequenceNumber。配套工具方法 `ContentFileUtil.isDV/dvDesc` 可被其他模块复用。
- **影响范围**：仅 core 模块的 `DeleteFileIndex`、`ContentFileUtil` 及对应测试基类，无 API/格式变更（依赖前面 #11446 提供的 `DeleteFile.contentOffset/contentSizeInBytes/referencedDataFile` 与 `FileFormat.PUFFIN`）。
- **回迁到 1.4.x 的注意事项**：
  - DV 是 v3 表格式特性，依赖 manifest v3 写入、`DeleteFile` 的 content offset/size/referencedDataFile 字段以及 Puffin 写入器（见 #11476 等后续提交）。
  - 1.4.x 作为维护分支通常只支持 v1/v2 表，单独回迁本提交会因为缺少 DV 写入、统计、读取（Puffin reader）等配套链路而无法独立工作。
  - 若 1.4.x 需要支持 v3 表读取 DV，需要整套 DV 相关提交一并回迁（包括 #11444/#11446/#11464/#11467/#11476 等），并验证 manifest reader 兼容性、Spark/Flink 引擎侧适配。
  - 单独回迁本提交意义不大且会引入编译依赖（如 `ContentFileUtil.isDV` 依赖 `FileFormat.PUFFIN`，本提交假设该枚举值已存在），**不建议单独回迁**。
