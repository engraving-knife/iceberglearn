# 提交 0066：Core: Ignore split offsets when the last split offset is past the file length (#8860)

## 提交信息

- **序号**：0066 / 4088
- **哈希**：ad602a379584512d1d96eda557c20cf2af21d1b2
- **短哈希**：ad602a379
- **日期**：2023-10-17 12:13:57 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Ignore split offsets when the last split offset is past the file length (#8860)
- **PR/Issue**：#8860

## 总体目的

本提交修复了 Iceberg 在读取数据文件元数据时遇到损坏的 split offsets（分片偏移量）的健壮性问题。在 Iceberg 中，每个 `DataFile` 会持久化一组 split offsets，表示文件内每个分片（split）的起始字节偏移，用于读端拆分和扫描任务规划。正常情况下这些偏移量应严格递增且都小于文件实际长度。

但在某些异常场景下（例如写入端 bug、跨版本兼容问题或外部数据写入工具误用），manifest 文件中记录的 split offsets 可能有错误的取值，例如最后一个偏移量超过了文件实际大小（`fileSizeInBytes`）。当这种损坏的 split offsets 被下游引擎（如 Spark/Flink）信任并用于任务划分时，会生成读取越界的任务，进而导致读取失败甚至作业崩溃。

本提交的核心动机是"防御性编程"：在 `BaseFile.splitOffsets()` 这个核心读取入口增加校验，一旦发现最后一个 split offset 大于等于文件大小，就认为整个 split offsets 数组已被污染、不可信，直接返回 `null`。这样下游消费方在拿到 `null` 时会退化到"不按 split 拆分"的行为，从而避免用错误数据继续放大故障。这一改动对 Iceberg 的演进意义在于：让元数据读取层具备更强的数据自愈能力，避免单个损坏字段级联成查询失败，提升生产环境下的鲁棒性。

## 如何达成设计目的

整体设计非常聚焦：只在 `BaseFile.splitOffsets()` 这一访问方法内增加两道校验，而不改动 split offsets 的写入端、存储格式或上下游调用链。改动分为三部分——核心校验逻辑、既有测试用例中受影响的数据修正、以及新增的针对性单测，分别落在 `BaseFile.java`、`TableTestBase.java` 和 `TestManifestReader.java` 三个文件。这种"在读取点统一兜底"的设计既保证了所有读路径（manifest 读取、投影读取等）都受保护，又避免了侵入写入端或修改 Avro schema。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseFile.java`

**修改目的**：在 `splitOffsets()` 读取入口对损坏数据做防御性校验，避免下游消费错误的偏移量。

**工作逻辑**：在原方法体 `return ArrayUtil.toUnmodifiableLongList(splitOffsets);` 之前插入两段校验：

1. 空数组兜底：`if (splitOffsets == null || splitOffsets.length == 0) return null;`。原先依赖底层数组为空时 `ArrayUtil.toUnmodifiableLongList` 的行为，现在显式返回 `null`，统一空值语义，避免下游拿到空列表而误以为"文件无分片"。

2. 越界校验：`if (splitOffsets[splitOffsets.length - 1] >= fileSizeInBytes) return null;`。这里取了**最后一个** split offset 与 `fileSizeInBytes` 比较，因为 split offsets 是递增的，最后一个偏移量是上界，只要它合法（严格小于文件大小），其余偏移量也必然合法。这里使用 `>=` 而非 `>` 的原因在于：偏移量指向的是分片起始字节，若起始字节已经等于文件长度，则该分片长度为 0，没有意义且本身就异常，因此也视为损坏。一旦判定损坏，直接返回 `null`，让下游降级为不拆分读取。

该方法的访问入口对应 `get(pos)` 中 `case 14: return splitOffsets();`（[BaseFile.java:474](core/src/main/java/org/apache/iceberg/BaseFile.java#L474)），这意味着无论是直接调用还是通过投影读取第 14 个字段，都会经过这道校验，覆盖面完整。

### `core/src/test/java/org/apache/iceberg/TableTestBase.java`

**修改目的**：修正既有测试夹具中"故意构造的非法 split offsets"，避免新校验逻辑让老测试误触 null 返回。

**工作逻辑**：原测试基类中构造的 `FILE_C2_DELETES` 和 `FILE_D2_DELETES` 两个删除文件夹具，`withFileSizeInBytes(10)` 只有 10 字节，但 split offsets 却分别是 `ImmutableList.of(2L, 2_000_000L)` 和 `ImmutableList.of(3L, 3_000L, 3_000_000L)`，最后一个偏移量远超 10 字节文件大小。在旧逻辑下这只是"测试数据"，但新增校验后这会被识别为损坏数据并返回 `null`，可能导致依赖这些 split offsets 的既有断言失败。

因此作者把这两个夹具的 split offsets 改为合法范围：`FILE_C2_DELETES` 改为 `(2L, 8L)`，`FILE_D2_DELETES` 改为 `(0L, 3L, 6L)`，所有偏移量都严格小于文件大小 10。这一改动属于"夹具顺应新约束"，而非行为变更。

### `core/src/test/java/org/apache/iceberg/TestManifestReader.java`

**修改目的**：新增针对性单测，验证当 split offsets 越界时，通过 `ManifestReader` 读取出来的 `DataFile.splitOffsets()` 会返回 `null`。

**工作逻辑**：新增测试方法 `testDataFileSplitOffsetsNullWhenInvalid`。该测试构造了一个 `fileSizeInBytes=10`、`splitOffsets=ImmutableList.of(2L, 1000L)` 的 `DataFile`（最后一个偏移 1000 远超文件大小 10），将其写入 manifest 后再用 `ManifestFiles.read(manifest, FILE_IO)` 读取出来，断言 `file.splitOffsets()` 为 `null`。这个测试覆盖了"序列化到 manifest 再反序列化读取"的完整链路，证明校验逻辑不仅在内存对象上生效，也能在 manifest 持久化往返后正确触发，从而保护真实的扫描读路径。同时引入了 `ImmutableList` 的 import。

## 小结

本提交在 `BaseFile.splitOffsets()` 读取入口增加"最后一个偏移量越界即返回 null"的防御性校验，让 Iceberg 在面对损坏的 split offsets 元数据时能优雅降级而非崩溃，显著提升了读取路径的健壮性。
