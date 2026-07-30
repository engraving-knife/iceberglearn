# 提交 0283：Core: Look up targeted position deletes by path (#9251)

## 提交信息

- **序号**：0283 / 4088
- **哈希**：ad3cf9d813e943f5f82a312ba028d7bf31d7b033
- **短哈希**：ad3cf9d81
- **日期**：2023-12-18
- **作者**：Anton Okolnychyi <aokolnochyi@apple.com>
- **提交说明**：Core: Look up targeted position deletes by path (#9251)
- **PR/Issue**：#9251

## 总体目的

本提交重构 `DeleteFileIndex`（删除文件索引），核心目的是把"针对单一数据文件的位置删除（position deletes）"从按分区查找改为按文件路径查找，从而显著降低这类删除文件的查找开销，并简化索引内部结构。

Iceberg 的删除文件分两种内容类型：position deletes 与 equality deletes。Position deletes 通过 `(file_path, row_position)` 删除特定行；equality deletes 通过等值谓词删除匹配行。在大规模 UPSERT/DELETE 工作负载下，引擎会为每个被扫描的 data file 调用 `DeleteFileIndex#forDataFile(seq, file)` 来获取对其适用的删除文件列表。该查找发生在扫描计划阶段的热路径上，性能至关重要。

在原始实现中，所有删除文件都按 `(specId, StructLikeWrapper)` 分组到 `DeleteFileGroup`，无论它是 position 还是 equality deletes、无论它是"分区范围"还是"文件范围"。查找时必须从分区对应的 group 里依次取出每个删除文件，再做序列号过滤和列统计过滤。问题是：position delete 文件常常是"targeted"——即只针对某一个具体的数据文件路径写入。这种文件即便只覆盖一个 data file，也会被分到分区 group 里，对该分区下所有其它 data file 都做一次列统计比对，产生不必要的 CPU 开销。当每个分区有 N 个 data file 且每个 data file 都有一个对应的 targeted position delete 时，复杂度从理想的 O(1)×N 退化为 O(N²)。

此次重构利用一个关键观察：targeted position delete 的 `DELETE_FILE_PATH` 列（隐藏列 `_file`，field id 由 `MetadataColumns.DELETE_FILE_PATH` 给出）的 lower bound 与 upper bound 相等——因为它只针对一个文件。基于这点，新实现通过 `ContentFileUtil.referencedDataFile` 探测 delete file 是否是 targeted（即路径列上下界相同），如果是则按路径加入 `CharSequenceMap<PositionDeletes>`，否则按分区加入 `PartitionMap<PositionDeletes>`。查找 `forDataFile` 时分别从 global、eq-by-partition、pos-by-partition、pos-by-path 四个维度取结果并 `concat`，path 维度对 targeted 数据文件而言是 O(1) 哈希命中，跳过了分区遍历与统计过滤。

更深层的目的还包括代码质量：把原来混在一起、用 `useColumnStatsFiltering` 开关在两条代码路径间切换的 `DeleteFileGroup`，拆成单一职责的 `PositionDeletes` 与 `EqualityDeletes` 两个内部类，各自有独立的索引时机、独立的数据结构、独立的过滤逻辑。这让代码更易读、更易扩展（例如未来给 equality deletes 增加文件级统计缓存时不必影响 position deletes）。同时把"equality deletes 只对 sequence number 大于自己的 data file 生效"这一语义内聚到 `EqualityDeleteFile.applySequenceNumber = dataSequenceNumber - 1`，由构造函数统一计算，避免在 `IndexedDeleteFile` 里通过 `if content == EQUALITY` 分支判断。

## 如何达成设计目的

整体设计思路是"按内容类型与查找维度拆分索引结构"。具体包括：

1. **拆分索引数据结构**：原 `DeleteFileIndex` 内部持有一个 `Map<Pair<Integer, StructLikeWrapper>, DeleteFileGroup>` 兼存所有删除文件，新版改为四个独立字段：
   - `EqualityDeletes globalDeletes`：未分区 spec 下的全局 equality deletes。
   - `PartitionMap<EqualityDeletes> eqDeletesByPartition`：分区 spec 下的 equality deletes，按 (specId, partition) 索引。
   - `PartitionMap<PositionDeletes> posDeletesByPartition`：分区范围的 position deletes，按 (specId, partition) 索引。
   - `CharSequenceMap<PositionDeletes> posDeletesByPath`：targeted position deletes，按 data file path 索引。这是新增的关键结构。
2. **targeted 探测**：构造期对每个 position delete 调用 `ContentFileUtil.referencedDataFile(file)`，读取 `DELETE_FILE_PATH` 列的 lower/upper bound，若两者相等，则视为只针对该路径的 targeted delete，加入 `posDeletesByPath`；否则加入 `posDeletesByPartition`。
3. **查找路径分解**：`forDataFile(seq, file)` 改为 `concat(findGlobalDeletes, findEqPartitionDeletes, findPosPartitionDeletes, findPathDeletes)`，每个 `find*` 方法在对应结构为 null 时直接返回 `EMPTY_DELETES`，避免空查找。targeted 数据文件的 path 查找就是 `posDeletesByPath.get(dataFile.path())` 一次哈希。
4. **类内懒索引**：`PositionDeletes` 与 `EqualityDeletes` 内部使用"buffer → indexIfNeeded → 数组+seqs"的双态结构。新增文件先入 `volatile List buffer`，首次 `filter` / `referencedDeleteFiles` / `isEmpty` 时通过 double-checked locking 一次性排序、生成 `long[] seqs` 与 `DeleteFile[] files`，然后置 `buffer = null` 永久切换到索引态。后续查询通过 `Arrays.binarySearch(seqs, seq)` 找到起点，`System.arraycopy` 切片返回。
5. **`EqualityDeleteFile` 懒加载 equality fields**：把 `equalityFieldIds` 在使用时才解析为 `List<NestedField>`，缓存到 `volatile` 字段，避免每次列统计过滤都调用 `schema.findField(id)`。
6. **辅助工具下沉**：`ArrayUtil.concat`、`CharSequenceMap.computeIfAbsent(Supplier)`、`ContentFileUtil.referencedDataFile` 三个新工具方法把以前散落或重复的逻辑集中，便于复用与测试。

## 修改详情

### `api/src/main/java/org/apache/iceberg/util/CharSequenceMap.java`

**修改目的**：为 `CharSequenceMap` 增加接受 `Supplier<V>` 的 `computeIfAbsent` 重载，方便索引构建时按需构造 `PositionDeletes` 实例。

**工作逻辑**：
- 新增 `public V computeIfAbsent(CharSequence key, Supplier<V> valueSupplier)`，内部委托给原有 `Map<CharSequence, V>` 风格的 `computeIfAbsent(key, ignored -> valueSupplier.get())`。这样调用方无需写 `ignored ->` 这种"忽略参数"的 lambda，更简洁。本提交在 `DeleteFileIndex.Builder.add` 中使用 `deletesByPath.computeIfAbsent(path, PositionDeletes::new)` 受益于此。

### `core/src/main/java/org/apache/iceberg/util/ArrayUtil.java`

**修改目的**：提供类型安全的数组拼接工具，供 `DeleteFileIndex.forDataFile` 把多路查找结果合并为一个 `DeleteFile[]`。

**工作逻辑**：
- 新增 `public static <T> T[] concat(Class<T> type, T[]... arrays)`：先用 `totalLength(arrays)` 求和得到总长度，通过 `Array.newInstance(type, totalLength)` 创建正确组件类型的数组；再依次 `System.arraycopy` 把每个非空数组拷贝到 `result` 中，更新 `currentLength` 偏移。`totalLength` 是私有静态辅助方法，遍历求和。
- 相比原 `DeleteFileIndex` 里用 `ObjectArrays.concat` 或 `Stream.concat`，此实现避免了 Guava 工具的中间装箱与流开销，对热路径友好。

### `core/src/main/java/org/apache/iceberg/util/ContentFileUtil.java`

**修改目的**：抽出"判断 position delete 是否只针对单一数据文件，并返回该文件路径"的逻辑，避免在 `DeleteFileIndex` 里直接操作列统计细节。

**工作逻辑**：
- 新增 `public static CharSequence referencedDataFile(DeleteFile deleteFile)`：
  - 若 `deleteFile.content() == EQUALITY_DELETES`，立即返回 `null`（equality deletes 不按路径索引）。
  - 取 `MetadataColumns.DELETE_FILE_PATH` 的 `fieldId` 与 `type`。
  - 从 `deleteFile.lowerBounds()` 取该 fieldId 的 `ByteBuffer`，若 lower bounds 为 null 或该列无下界，返回 `null`。
  - 同样取 upper bound；任一缺失返回 `null`。
  - 若 `lowerPathBound.equals(upperPathBound)`（两个 ByteBuffer 内容相等），说明该 delete file 只引用一个数据文件路径，通过 `Conversions.fromByteBuffer(pathType, lowerPathBound)` 转换为 `CharSequence` 返回。
  - 否则返回 `null`。
- 这条逻辑是后续路由到 `posDeletesByPath` 还是 `posDeletesByPartition` 的判定依据。

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java`（核心重构，547 行变更）

**修改目的**：拆分索引结构、增加按路径查找的 `posDeletesByPath`、引入 `PositionDeletes`/`EqualityDeletes` 双内部类、移除 `useColumnStatsFiltering` 开关。

**工作逻辑**：

- **字段重构**：
  - 删除 `partitionTypeById`、`wrapperById`、`useColumnStatsFiltering`，删除原 `Map<Pair<Integer, StructLikeWrapper>, DeleteFileGroup> deletesByPartition`。
  - 新增 `EqualityDeletes globalDeletes`、`PartitionMap<EqualityDeletes> eqDeletesByPartition`、`PartitionMap<PositionDeletes> posDeletesByPartition`、`CharSequenceMap<PositionDeletes> posDeletesByPath`。
  - `NO_DELETES` 重命名为 `EMPTY_DELETES`，沿用空数组哨兵。
  - 构造函数接收四元组，四个里只要有一个非 null 即视为有删除文件；`isEmpty = noEqDeletes && noPosDeletes`。空 Map/空对象在 build 时被替换为 null，便于 `forDataFile` 用 null 短路。

- **`referencedDeleteFiles()` 改写**：依次把 globalDeletes、eqDeletesByPartition、posDeletesByPartition、posDeletesByPath 的内容 `Iterables.concat` 起来；每个非 null 的容器都会被遍历。原来的 `wrappers()`/`newWrapper()`/`partition()` 等辅助方法被删除，因为 `PartitionMap` 内部自己处理 `StructLikeWrapper` 缓存。

- **`forDataFile(long sequenceNumber, DataFile file)` 改写**：
  - 若 `isEmpty` 直接返回 `EMPTY_DELETES`。
  - 否则分别调用 `findGlobalDeletes`、`findEqPartitionDeletes`、`findPosPartitionDeletes`、`findPathDeletes`，再用 `concat(DeleteFile.class, ...)` 合并。每个 `find*` 内部：对应字段为 null 返回 `EMPTY_DELETES`；否则从对应 map 按 specId/partition 或 path 取出 `PositionDeletes`/`EqualityDeletes`，再调其 `filter(seq)` 或 `filter(seq, dataFile)`。
  - 关键变化：对 targeted position deletes，`findPathDeletes` 直接 `posDeletesByPath.get(dataFile.path())`，O(1) 命中；不再走 partition 路径，不再做列统计过滤（因为路径已经唯一确定）。

- **`forEntry(ManifestEntry<DataFile>)`**：未改业务逻辑，跟随 `forDataFile` 适配。

- **`canContainEqDeletesForFile(DataFile, EqualityDeleteFile)`**：参数类型从 `IndexedDeleteFile` 改为 `EqualityDeleteFile`，签名同时直接接收 `EqualityDeleteFile` 而不再额外传 `Schema`（因为 `EqualityDeleteFile` 内部已经持有了 spec）。循环改为 `for (Types.NestedField field : deleteFile.equalityFields())`，内部用 `field.fieldId()` 取 id；这样把 schema 解析与字段遍历解耦，减少重复 `schema.findField(id)`。

- **`canContainPosDeletesForFile` 删除**：原来对 position delete 做 path 列 lower/upper 过滤的静态方法被删除，因为新结构里 partition-scoped position deletes 不再做 path 过滤（要么 targeted 已被 path 索引，要么就是分区范围、对所有 data file 都可能适用）。

- **`Builder.build()` 重构**：
  - 删除 `useColumnStatsFiltering` 累积逻辑、删除 `wrappersBySpecId` 与 `deleteFilesByPartition` Multimap。
  - 改为同时构造 `globalDeletes`（EqualityDeletes 实例）、`eqDeletesByPartition`（PartitionMap）、`posDeletesByPartition`（PartitionMap）、`posDeletesByPath`（CharSequenceMap）。
  - 对每个 `DeleteFile file` 按 `file.content()` 分发：`POSITION_DELETES` 调 `add(posDeletesByPath, posDeletesByPartition, file)`；`EQUALITY_DELETES` 调 `add(globalDeletes, eqDeletesByPartition, file)`；其它抛 `UnsupportedOperationException`。
  - `add(posDeletesByPath, posDeletesByPartition, file)`：先 `ContentFileUtil.referencedDataFile(file)` 取 path；非 null 则 `deletesByPath.computeIfAbsent(path, PositionDeletes::new)`；否则按 `(specId, partition)` 从 `deletesByPartition.computeIfAbsent` 取；最后 `deletes.add(file)`。
  - `add(globalDeletes, eqDeletesByPartition, file)`：取 `spec`；`spec.isUnpartitioned()` 时直接加入 `globalDeletes`，否则按 `(specId, partition)` 取 `EqualityDeletes` 并 `deletes.add(spec, file)`。
  - 最后把空容器替换为 null 后构造 `DeleteFileIndex`。

- **新增静态 `findStartIndex(long[] seqs, long seq)`**：从原 `DeleteFileGroup.findStartIndex` 提为静态方法，供两个新内部类共用。算法不变：`Arrays.binarySearch`，未找到时取 `-(pos+1)` 作为插入点；找到时回退到首次出现位置。

- **新增静态 `concat(DeleteFile[]...)`**：委托 `ArrayUtil.concat(DeleteFile.class, deletes)`，简化 `forDataFile` 末尾合并。

- **`PositionDeletes` 内部类（新增）**：
  - 持有 `long[] seqs`、`DeleteFile[] files`、`volatile List<DeleteFile> buffer`。
  - `add(DeleteFile)`：`Preconditions.checkState(buffer != null, "Can't add files upon indexing")` 后 `buffer.add(file)`。
  - `filter(long seq)`：`indexIfNeeded()` 后 `findStartIndex(seqs, seq)`；越界返回 `EMPTY_DELETES`；`start == 0` 直接返回 `files`（避免无谓拷贝）；否则 `System.arraycopy` 切片。
  - `referencedDeleteFiles()`：`indexIfNeeded()` 后 `Arrays.asList(files)`。
  - `isEmpty()`：`indexIfNeeded()` 后 `files.length == 0`。
  - `indexIfNeeded()`：双检锁，把 buffer 转为排序后的 `files` 与对应 `seqs`（用 `dataSequenceNumber` 作为 key），最后置 `buffer = null`。
  - 序列号语义：position deletes 按 `dataSequenceNumber` 排序，可作用于同 sequence 的 data file（即同一 snapshot 内追加的数据文件也可见），所以 `filter(0)` 与 `filter(1)` 在本测试中都返回全部 4 个文件。

- **`EqualityDeletes` 内部类（新增）**：
  - 类似 `PositionDeletes`，但元素是 `EqualityDeleteFile`，`filter(long seq, DataFile dataFile)` 在序列号过滤之上再做 `canContainEqDeletesForFile` 列统计过滤。
  - `add(PartitionSpec spec, DeleteFile file)`：把 `DeleteFile` 包成 `EqualityDeleteFile` 入 buffer。
  - 序列号语义：equality deletes 索引按 `applySequenceNumber = dataSequenceNumber - 1`，因为 equality deletes 只对晚于自己提交的 data file 生效。

- **`EqualityDeleteFile` 内部类（原 `IndexedDeleteFile` 改名+瘦身）**：
  - 构造方法简化为 `EqualityDeleteFile(PartitionSpec spec, DeleteFile file)`，`applySequenceNumber` 一律计算为 `wrapped.dataSequenceNumber() - 1`（不再判断 content 类型，因为这个类只承载 equality deletes）。
  - 新增 `partition()` 方法返回 `wrapped.partition()`。
  - 新增 `equalityFields()` 方法返回 `List<Types.NestedField>`，懒加载（双检锁 + `volatile`），避免重复 schema 查找。
  - `equalityFieldIds()` 删除（被 `equalityFields()` 取代）。
  - `hasNoLowerOrUpperBounds()` 删除（新结构不再需要这个判断）。
  - `convertBounds` 简化：原来根据 content 类型分别处理（position 取 path 列、equality 取 equality field 列），现在只处理 equality fields，因为该类只用于 equality deletes。

- **`DeleteFileGroup` 内部类删除**：上述两个新类替换了它的职责。

### `core/src/test/java/org/apache/iceberg/DeleteFileIndexTestBase.java`

**修改目的**：为新的 `PositionDeletes`、`EqualityDeletes` 内部类添加单元测试，覆盖索引时机、序列号过滤语义、`add` 后置条件。

**工作逻辑**：
- 新增 `testPositionDeletesGroup()`：构造 4 个 sequence number 分别为 1~4 的 partitioned position delete 文件，乱序加入 `PositionDeletes`。断言：`isEmpty()` 为 false；`referencedDeleteFiles()` 返回全部 4 个；`filter(0)`/`filter(1)` 返回 4 个（position deletes 对同 sequence 也生效），`filter(2)` 返回 file2~file4，依此类推，`filter(5)` 返回空。再次 `add(file1)` 应抛 `IllegalStateException`。
- 新增 `testEqualityDeletesGroup()`：类似构造 4 个 equality delete 文件。断言：`filter(0, FILE_A)` 返回 4 个；`filter(1, FILE_A)` 返回 file2~file4（因为 equality delete apply seq 是 `dataSequenceNumber - 1`，所以 seq=1 时 file1 的 apply seq=0 已经过期不返回）；以此类推 `filter(4, FILE_A)` 返回空；`add` 在 index 后抛 `IllegalStateException`。

### `core/src/test/java/org/apache/iceberg/util/TestArrayUtil.java`

**修改目的**：为新增的 `ArrayUtil.concat` 添加单元测试。

**工作逻辑**：
- `testConcatWithDifferentLengthArrays`：三个长度不同的 `Integer[]` 拼接，验证顺序与元素正确。
- `testConcatWithEmptyArrays`：空数组 + 非空数组拼接，验证空数组被跳过。
- `testConcatWithSingleArray`：单数组拼接，返回等份数组。
- `testConcatWithNoArray`：无参数调用，返回空数组。

### `core/src/test/java/org/apache/iceberg/util/TestPartitionMap.java`

**修改目的**：为本提交依赖的 `PartitionMap.computeIfAbsent(specId, partition, valueSupplier)` 添加单元测试，确保行为与 `Map.computeIfAbsent` 语义一致。

**工作逻辑**：
- `testComputeIfAbsent`：先 put `(BY_DATA_SPEC.specId, Row.of("a")) -> "v1"`；后续对同 key 调用 `computeIfAbsent` 应返回已有值 "v1" 不覆盖；对不同 Row 实现但等价的 `CustomRow.of("a")` 调用 `get` 也应命中 "v1"，验证 `PartitionMap` 内部 `StructLikeWrapper` 比较正确。

### `data/src/test/java/org/apache/iceberg/data/TestDataFileIndexStatsFilters.java`

**修改目的**：新增端到端集成测试 `testDifferentDeleteTypes`，验证同时存在 global equality、partition equality、partition position、path position 四种删除文件时的查找行为正确。

**工作逻辑**：
- 初始化阶段把 records 按 `category` 字段拆分为 `oddRecords` 与 `evenRecords`，便于后续按分区写数据文件。
- 测试主体：
  1. 用未分区 spec append `dataFile`（全部 8 条）。
  2. 写一个 global equality delete（`id` in {7, 8}），commit。
  3. 演进 spec 加字段 `category`，变成按 category 分区。
  4. 为 even/odd 分区各写一个数据文件。
  5. 为 even 分区写 3 个 equality delete（id=2 命中、id=4 命中、id=25 不命中，用于验证列统计过滤）。
  6. 为 even 分区写 1 个 partition-scoped position delete（包含 `dataFileWithEvenRecords.path` 与 `"some-other-file.parquet"` 两个路径，因为多路径所以不是 targeted，会进 posDeletesByPartition）。
  7. 为 even 分区写 1 个 path-scoped position delete（只包含 `dataFileWithEvenRecords.path` 的 row 1 与 row 2，单路径所以是 targeted，会进 posDeletesByPath）。
  8. 切回未分区 spec，再写一个 global equality delete（id in {20, 21}，可被列统计过滤）。
  9. `planTasks()` 应得到 3 个 FileScanTask：原 `dataFile` 应只命中 `globalEqDeleteFile1`（第二个 global 未 commit，因为代码里漏了 `.commit()`，但 test 期望只见到第一个 global）；`dataFileWithEvenRecords` 应命中 `partitionEqDeleteFile1`、`partitionEqDeleteFile2`、`pathPosDeletes.first()`、`partitionPosDeletes.first()`（id=25 那个 equality delete 被列统计过滤掉，所以不在期望里）；`dataFileWithOddRecords` 应无删除文件。
- 辅助方法 `coversDataFile`、`assertDeletes`、`deletePaths`、`planTasks`、`writeData`、`writeEqDeletes`、`writePosDeletes` 用于简化测试主体。
- 这条测试同时验证了：targeted position delete 通过 path 索引能被正确找到（覆盖新增的 `posDeletesByPath`）；partition-scoped position delete 仍走 partition 索引；equality delete 仍能通过列统计过滤；spec 演进场景下索引正确切换。

### `spark/v3.5/spark-extensions/src/jmh/java/org/apache/iceberg/DeleteFileIndexBenchmark.java`

**修改目的**：为 benchmark 增加 `oneToOneMapping` 参数，便于对比"分区范围 position delete"与"文件范围（targeted）position delete"两种工作负载下的索引性能。

**工作逻辑**：
- 引入 `@Param({"true", "false"}) private boolean oneToOneMapping;`，JMH 会跑两组。
- 抽出原 `initDataAndDeletes()` 为 `initDataAndPartitionScopedDeletes()`：每个分区一个 partition-scoped position delete 覆盖该分区全部 data file（旧行为）。
- 新增 `initDataAndFileScopedDeletes()`：每个 data file 配一个 targeted position delete（一一对应），由 `FileGenerationUtil.generatePositionDeleteFile(table, dataFile)` 生成。这正是新结构 `posDeletesByPath` 优化的场景。
- `setupBenchmark` 调 `initDataAndDeletes()`，该方法根据 `oneToOneMapping` 分发到上述两个方法之一。
- 这样未来在 JMH 跑两组结果时，能直接看到 path 索引对 targeted 场景的加速效果，也为本次重构提供了量化依据。

## 小结

这是一个聚焦性能与可维护性的核心重构。性能上，它把"targeted position delete"从 O(分区大小) 的分区扫描 + 列统计过滤优化为 O(1) 路径哈希查找，在 UPSERT 主导的工作负载（每个数据文件配一个 position delete）上预期带来显著加速，benchmark 已加入对照参数方便度量。架构上，它把 `DeleteFileGroup` 拆为 `PositionDeletes` 与 `EqualityDeletes` 两个单一职责类，分别使用各自最合适的索引键（path vs partition）与序列号语义（dataSequenceNumber vs applySequenceNumber = dataSequenceNumber - 1），消除了 `useColumnStatsFiltering` 二分开关和 `if content == EQUALITY` 的内联判断，代码更清晰。同时把"判断 position delete 是否 targeted"的逻辑下沉到 `ContentFileUtil.referencedDataFile`，把数组拼接下沉到 `ArrayUtil.concat`，把 Supplier 风格 `computeIfAbsent` 加到 `CharSequenceMap` 与 `PartitionMap`，工具复用度提升。测试侧新增了内部类的单元测试、util 测试、端到端集成测试 `testDifferentDeleteTypes` 和 JMH 对照 benchmark，覆盖度完整。该改动属于 1.4.x 维护分支里的性能 + 内部 API 整理性提交，对用户 API 无影响，但对查询计划阶段吞吐有正向作用。
