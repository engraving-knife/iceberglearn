# 提交 1304：Core: Add portable Roaring bitmap for row positions (#11372)

## 提交信息

- **序号**：1304 / 4088
- **哈希**：1e3ee1e4e80873018af716a190e541925f09c285
- **短哈希**：1e3ee1e4e
- **日期**：2024-10-28（Mon Oct 28 22:02:55 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnochyi@apache.org>
- **提交说明**：Core: Add portable Roaring bitmap for row positions
- **PR/Issue**：#11372

## 总体目的

Iceberg 表格式 v3 计划引入 DV（Deletion Vector，删除向量）作为新的位置删除承载方式：DV 使用 Puffin 文件存储一个位图，标识某个数据文件中哪些行被删除。位图的载体必须满足两个硬性要求：

1. **支持 64 位行位置**：Iceberg 数据文件行号是 long，理论上可达 64 位范围；
2. **可移植的序列化格式**：DV 文件会被多种引擎（Spark/Flink/Trino/Python 等）读写，位图的二进制布局必须跨语言一致、有公开规范，否则不同引擎无法互读 DV。

Iceberg 既有代码（`BitmapPositionDeleteIndex`、`SortingPositionOnlyDeleteWriter` 等）使用 `org.roaringbitmap.longlong.Roaring64Bitmap`，它有两个问题：

- `Roaring64Bitmap` 的序列化是 Java 库内部格式，**没有跨语言规范**，其他语言（如 Python 的 `pyroaring`、C++ 的 `CRoaring`）无法直接读取；
- 它内部使用" buckets + 32-bit bitmap "的结构，但默认实现并不针对"绝大多数位置落在 32 位以内"的常见场景做空间优化，且接口偏重，不利于后续在 DV 写入/读取链路中精细控制字节布局。

本提交新增 `RoaringPositionBitmap`：一个用"32 位 Roaring 位图数组"模拟 64 位位图、且按官方 Roaring Format Spec 序列化的可移植实现，作为后续 DV 读写链路的底层数据结构。LICENSE 中也注明该实现参考自 Delta Lake 的 `RoaringBitmapArray`。

## 如何达成设计目的

整体设计思路如下：

1. **64 位位置拆分为 key + 32 位位置**：把 64 位 position 的高 32 位作为 key，低 32 位作为 32-bit position。维护一个按 key 索引的 `RoaringBitmap[]` 数组，每个 key 对应一个标准 32 位 Roaring bitmap。
2. **惰性扩张数组**：数组长度按需扩张到 `maxKey + 1`，避免一开始就分配超大数组；同时保证 key 之间的"空洞"也分配空 bitmap，使得数组下标 == key，访问 O(1)。
3. **可移植序列化格式**：自定义一套字节布局——先写 8 字节 bitmap 数量，再对每个非空 bitmap 写 4 字节 key + 标准 32 位 Roaring 序列化字节流。要求 ByteBuffer 为 little-endian（与 Roaring 官方规范一致），从而可被其他语言的标准 Roaring 库读取。反序列化时按 key 升序遍历，并对稀疏 key 之间的空洞补空 bitmap。
4. **限制最大位置**：通过 `MAX_POSITION = toPosition(Integer.MAX_VALUE - 1, Integer.MIN_VALUE)` 显式限制最高 key 为 `Integer.MAX_VALUE - 1`，避免 `Integer.MAX_VALUE + 1` 的整数溢出导致数组分配失败。
5. **配套测试 + JMH 基准**：用官方 Roaring 仓库提供的样例二进制文件（`64map*.bin`）验证反序列化兼容性；用随机生成的稀疏/密集/混合位图验证 set/contains/forEach/序列化往返的正确性；JMH 基准对比 `RoaringPositionBitmap` 与 `Roaring64Bitmap` 在 500 万位置下的 add/contains 性能，证明新实现性能不输甚至更优。

## 修改详情

### `LICENSE`（修改，+1 行）

**修改目的**：声明 `RoaringPositionBitmap` 借鉴自 Delta Lake 的 `RoaringBitmapArray`，满足 Apache 许可证下的第三方代码引用披露要求。

**工作逻辑**：在 "This product includes code from Delta Lake" 一节追加一行：

```
* RoaringPositionBitmap is a Java implementation of RoaringBitmapArray in Delta.
```

### `core/src/main/java/org/apache/iceberg/deletes/RoaringPositionBitmap.java`（新增，318 行）

**修改目的**：实现支持 64 位行位置、可移植序列化的 Roaring 位图。

**工作逻辑**：

- **类级约定**：包级私有 `class RoaringPositionBitmap`，注释说明只能处理非负 64 位 position（最高位必须为 0），并对"绝大多数 position 落在 32 位以内"的场景做空间优化。`MAX_POSITION = toPosition(Integer.MAX_VALUE - 1, Integer.MIN_VALUE)`，即 key 上限为 `Integer.MAX_VALUE - 1`，防止 `+1` 溢出。
- **字段**：
  - `private RoaringBitmap[] bitmaps;`，初始为 `EMPTY_BITMAP_ARRAY`（共享空数组）；
  - 常量 `BITMAP_COUNT_SIZE_BYTES = 8L`、`BITMAP_KEY_SIZE_BYTES = 4L`，用于序列化大小计算。
- **核心方法**：
  - `set(long pos)`：`validatePosition` 后取 `key = (int)(pos >> 32)`、`pos32Bits = (int) pos`，调用 `allocateBitmapsIfNeeded(key + 1)` 保证数组长度足够，再 `bitmaps[key].add(pos32Bits)`。
  - `setRange(long start, long end)`：朴素循环 `set`（注释中没有用 Roaring 的 `add(long, long)` 是因为要跨多个 32-bit bitmap）。
  - `setAll(RoaringPositionBitmap that)`：先扩张到 `that.bitmaps.length`，再逐 key `bitmaps[key].or(that.bitmaps[key])`，原地合并。
  - `contains(long pos)`：`key < bitmaps.length && bitmaps[key].contains(pos32Bits)`，未分配该 key 视为不存在。
  - `cardinality()`：累加所有 bitmap 的 `getLongCardinality()`。
  - `runLengthEncode()`：对每个 bitmap 调用 `runOptimize()`，返回是否有变更；用于把 array container 压成 run container，降低序列化体积。
  - `forEach(LongConsumer)`：按 key 升序遍历，对每个 bitmap 用 `bitmap.forEach((int pos32Bits) -> consumer.accept(toPosition(key, pos32Bits)))` 还原 64 位 position。
- **数组扩张** `allocateBitmapsIfNeeded(int requiredLength)`：
  - 若当前长度足够，直接返回；
  - 特殊情况：当前为空且只需要 1 个 bitmap，直接 `new RoaringBitmap[]{new RoaringBitmap()}`，避免 `System.arraycopy` 的开销；
  - 否则新建 `RoaringBitmap[requiredLength]`，拷贝旧数组，对 `[bitmaps.length, requiredLength)` 区间每个 key 新建空 `RoaringBitmap()`。这保证数组下标 == key，且空洞 key 也有空 bitmap，便于 `setAll` 与序列化时按 key 顺序遍历。
- **序列化** `serialize(ByteBuffer buffer)`：
  - `validateByteOrder(buffer)` 要求 little-endian（Roaring 官方规范要求）；
  - 先 `buffer.putLong(bitmaps.length)`（数组长度，即"非空 + 空洞"bitmap 数量）；
  - 对每个 key（0 到 `bitmaps.length - 1`）：`buffer.putInt(key)` + `bitmaps[key].serialize(buffer)`。
  - 注释中明确这是"portable serialization format"——key 数量 + (key, bitmap) 对，且要求按 unsigned 升序排列（这里下标即 key，天然升序）。
- **反序列化** `static deserialize(ByteBuffer buffer)`：
  - `validateByteOrder`；
  - `readBitmapCount` 读 8 字节数量，校验在 `[0, Integer.MAX_VALUE]`；
  - 循环 `remainingBitmapCount` 次：`readKey`（校验 key >= 0、key <= Integer.MAX_VALUE - 1、key > lastKey 升序），对 `lastKey` 与 `key` 之间的空洞补空 bitmap（`while (lastKey < key - 1) { bitmaps.add(new RoaringBitmap()); lastKey++; }`），再 `readBitmap` 读出一个 `RoaringBitmap`。
  - `readBitmap` 中：`new RoaringBitmap().deserialize(buffer)` 后**手动推进 buffer.position** `bitmap.serializedSizeInBytes()` 字节，因为 RoaringBitmap 的 deserialize 不会推进 position。
- **辅助方法**：
  - `key(long pos)` = `(int)(pos >> 32)`；
  - `pos32Bits(long pos)` = `(int) pos`；
  - `toPosition(int key, int pos32Bits)` = `(((long) key) << 32) | (((long) pos32Bits) & 0xFFFFFFFFL)`，注意低 32 位要先 mask 再 or，避免符号扩展；
  - `validatePosition(long pos)`：`pos >= 0 && pos <= MAX_POSITION`。
- `@VisibleForTesting int allocatedBitmapCount()`：返回当前数组长度，供测试断言扩张行为。

### `core/src/test/java/org/apache/iceberg/deletes/TestRoaringPositionBitmap.java`（新增，515 行）

**修改目的**：覆盖 `RoaringPositionBitmap` 的功能正确性与序列化兼容性。

**工作逻辑**：使用 Iceberg 自带的 `ParameterizedTestExtension`（参数化测试，传入 `seed` 与 `validationSeed`），主要用例：

- `testAdd` / `testAddPositionsRequiringMultipleBitmaps`：验证单点 add 与跨多个 key 的 add，断言 `allocatedBitmapCount` 等于 `maxKey + 1`（如 key=100 时为 101）。
- `testAddRange` / `testAddRangeAcrossKeys` / `testAddEmptyRange`：验证 `setRange`，包括跨 key 边界的范围（如 `((1<<32) - 5, (1<<32) + 5)`）。
- `testAddAll` / `testAddAllWithEmptyBitmap` / `testAddAllWithOverlappingBitmap` / `testAddAllSparseBitmaps`：验证 `setAll` 的合并语义与原地修改、不修改入参的特性。
- `testCardinality` / `testCardinalitySparseBitmaps`：验证计数正确，重复 set 不增加计数。
- `testSerializeDeserializeAllContainerBitmap`：构造覆盖 array / bitset / 可 RLE 压缩三类 container 的位图，`runLengthEncode` 后往返序列化，断言 `cardinality` 与逐位置 `contains` 一致。
- `testDeserializeSupportedRoaringExamples`：用 Roaring 官方仓库提供的 `64mapempty.bin`、`64map32bitvals.bin`、`64mapspreadvals.bin` 三个样例文件验证反序列化兼容性（证明本实现可读取符合官方规范的 64-bit bitmap 序列化字节流）。
- `testDeserializeUnsupportedRoaringExample`：`64maphighvals.bin` 含 `Integer.MAX_VALUE` 的 key，断言反序列化抛出 `IllegalArgumentException("Invalid unsigned key")`。
- `testUnsupportedPositions`：验证 `-1`、`MAX_POSITION + 1` 等越界位置在 `set` / `contains` 时抛出 `IllegalArgumentException`。
- `testInvalidSerializationByteOrder`：验证非 little-endian buffer 反序列化抛出异常。
- `testRandomSparseBitmap` / `testRandomDenseBitmap` / `testRandomMixedBitmap`：用随机种子生成稀疏（10 万 position，范围跨 5 个 key）、密集（连续 run + 间隔，扩展到 9 个 bitmap）、混合（稀疏 + 多次 setAll 密集/稀疏）位图，断言：
  - `cardinality` 与参照集 `Set<Long>` 大小一致；
  - 逐位置 `contains` 与 `Set.contains` 一致；
  - 往返序列化（含 RLE 前后两次）后仍一致；
  - 用 `validationSeed` 随机查询 2 万个位置，断言 `bitmap.contains` 与 `positions.contains` 完全一致。
- 辅助：`position(int bitmapIndex, int containerIndex, long value)` = `bitmapIndex * BITMAP_OFFSET + containerIndex * CONTAINER_OFFSET + value`，其中 `BITMAP_OFFSET = 0xFFFFFFFFL + 1`、`CONTAINER_OFFSET = Character.MAX_VALUE + 1`，用于精确构造落在指定 bitmap 与指定 container 内的位置。

### `core/src/test/java/org/apache/iceberg/deletes/RoaringPositionBitmapBenchmark.java`（新增，162 行，位于 `jmh` 源集）

**修改目的**：JMH 基准，对比 `RoaringPositionBitmap` 与 `Roaring64Bitmap` 在大规模 position 下的性能。

**工作逻辑**：

- 配置：`@Fork(1)`、`@Warmup(iterations=3)`、`@Measurement(iterations=5)`、`@BenchmarkMode(Mode.SingleShotTime)`、`@Timeout(5 minutes)`，单线程。
- 数据：`TOTAL_POSITIONS = 5_000_000`、`STEP = 5`，即从 0 到 2500 万、步长 5 生成 500 万个 position；`orderedPositions` 为升序，`shuffledPositions` 为洗牌后无序。
- 4 组 add 基准：分别用 `RoaringPositionBitmap` 与 `Roaring64Bitmap` 处理 ordered / shuffled 位置，对比插入吞吐。
- 2 组 add + contains 基准：插入 shuffled 后再遍历 `[0, TOTAL_POSITIONS * STEP]` 全部 position 调用 `contains`，对比"构建 + 全量查询"总耗时。
- 消费者用 `Blackhole` 防止 JVM 死码消除。

### `core/src/test/resources/org/apache/iceberg/deletes/64map*.bin`（新增，4 个二进制文件）

**修改目的**：Roaring 官方仓库提供的 64-bit bitmap 序列化样例，用于测试反序列化兼容性。

**工作逻辑**：

- `64mapempty.bin`（8 字节）：空位图，仅 8 字节数量 = 0；
- `64map32bitvals.bin`（48 字节）：仅 key=0 的少量 32 位值；
- `64mapspreadvals.bin`（408 字节）：跨多个 key 的稀疏值；
- `64maphighvals.bin`（1086 字节）：包含 key = `Integer.MAX_VALUE` 的值，本实现不支持，用于负向测试。

## 小结

- **成效**：新增 `RoaringPositionBitmap`，一种基于 32 位 Roaring 位图数组、支持 64 位行位置、按官方 Roaring Format Spec 序列化的可移植位图实现。配合 515 行测试（含官方样例兼容性验证、随机稀疏/密集/混合位图往返验证）与 JMH 基准，为后续 DV（Deletion Vector）读写链路提供底层支持。
- **影响范围**：纯新增，仅 `core` 模块新增 1 个生产类（`RoaringPositionBitmap`，包级私有）、1 个 JMH 基准类、1 个测试类、4 个测试资源文件，以及 LICENSE 中一行第三方代码声明。未修改任何既有类（`BitmapPositionDeleteIndex` 等仍使用 `Roaring64Bitmap`，本提交不替换它们，仅为后续 DV 链路预备新工具）。
- **回迁到 1.4.x 的注意事项**：此为纯新增基础设施类，无对既有行为的修改，回迁安全。要点：
  1. 依赖 `org.roaringbitmap:roaringbitmap` 依赖已在 `core` 模块中（`Roaring64Bitmap` 已在用），无需新增依赖；
  2. 类为包级私有（`class RoaringPositionBitmap`，无 `public`），仅 `org.apache.iceberg.deletes` 包内可见，回迁时若 1.4.x 已有 DV 相关类位于同包可直接使用，否则需调整可见性；
  3. 测试依赖 Roaring 官方样例 `64map*.bin`，需一并回迁资源文件；
  4. `MAX_POSITION` 限制（key 上限为 `Integer.MAX_VALUE - 1`）是本实现的有意约束，若 1.4.x 后续 DV 链路需要支持更大位置，需重新评估；
  5. 本提交是 DV 功能链路的第 1 块拼图，后续依赖它的是 1342（`DeleteFileIndex` 支持 DV）、1367（commit 支持 DV）、1384/1385/1386（DV benchmark）等，回迁时需按依赖顺序进行。
