# 提交 1338：Core: Make PositionDeleteIndex serializable (#11463)

## 提交信息

- **序号**：1338 / 4088
- **哈希**：43b2f7d007c26ad79ebdf60d37ccca144db1f08f
- **短哈希**：43b2f7d00
- **日期**：2024-11-05（Tue Nov 5 08:35:42 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Make PositionDeleteIndex serializable (#11463)
- **PR/Issue**：#11463

## 总体目的

Iceberg DV（Deletion Vector，删除向量）的本质是一个 Roaring bitmap，表示"被删除的行号集合"。这个 bitmap 需要**写入 Puffin 文件作为 blob**，并能**从 Puffin 文件读出后还原成 `PositionDeleteIndex`**。本提交为 `PositionDeleteIndex` 接口与 `BitmapPositionDeleteIndex` 实现加上 `serialize()`/`deserialize()` 方法，定义了 Puffin blob 内的**自描述二进制格式**，使位置删除索引可以被持久化与还原。

本提交的格式与 Delta Lake（Delta 的 Puffin DV blob 格式）保持兼容，便于跨工具读取同样的 DV blob（提交说明提到 "for compatibility with Delta"）。具体格式定义在 `serialize()` 方法的 Javadoc 中：

```
[length: 4B, big-endian] [magic: 4B, little-endian, 0x64324D31 = 1681511377] [bitmap: portable Roaring, little-endian] [crc32: 4B, big-endian]
```

- length：magic + bitmap 的总字节数（不含 length 自身和 CRC）。
- magic：固定 magic number，用于格式识别。
- bitmap：使用 Roaring 的 portable serialization（与具体实现版本解耦）。
- CRC：对 magic + bitmap 计算的 CRC-32 校验，读取时验证。

这是 DV 整体特性的"读写两端"——`serialize()` 把内存中的 bitmap 写成字节序列，`deserialize()` 把字节序列还原成 bitmap 并通过 `deleteFile.contentSizeInBytes()`、`recordCount()` 做交叉验证。

## 如何达成设计目的

分三层落地：

1. **接口层**：在 `PositionDeleteIndex` 接口新增 `default ByteBuffer serialize()` 抛出 `UnsupportedOperationException`（向后兼容旧实现），并新增 `static deserialize(byte[], DeleteFile)` 静态方法，转发到 `BitmapPositionDeleteIndex.deserialize`。
2. **实现层**：在 `BitmapPositionDeleteIndex` 中实现 `serialize()` 与 `static deserialize()`，定义二进制布局、CRC 校验、长度/基数交叉校验。
3. **测试层**：新增 4 个 golden file（`.bin`）作为"权威字节序列"，对 4 种典型 bitmap 形态做"序列化 → 与 golden 比对 → 反序列化 → 比对内容"双向验证。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/PositionDeleteIndex.java`

**修改目的**：为接口增加 `serialize`/`deserialize` 抽象。

**工作逻辑**：

新增 `import java.nio.ByteBuffer;`，新增两个 default/static 方法：

```java
default ByteBuffer serialize() {
    throw new UnsupportedOperationException(getClass().getName() + " does not support serialize");
}

static PositionDeleteIndex deserialize(byte[] bytes, DeleteFile deleteFile) {
    return BitmapPositionDeleteIndex.deserialize(bytes, deleteFile);
}
```

`serialize` 用 default + 抛异常的方式，避免破坏 `EmptyPositionDeleteIndex` 等其他实现。`deserialize` 作为静态入口集中转发，便于外部调用。

### `core/src/main/java/org/apache/iceberg/deletes/BitmapPositionDeleteIndex.java`

**修改目的**：实现具体序列化/反序列化逻辑，定义 Puffin DV blob 的二进制格式。

**工作逻辑**：

新增常量：

```java
private static final int LENGTH_SIZE_BYTES = 4;
private static final int MAGIC_NUMBER_SIZE_BYTES = 4;
private static final int CRC_SIZE_BYTES = 4;
private static final int BITMAP_DATA_OFFSET = 4;        // 即 LENGTH_SIZE_BYTES
private static final int MAGIC_NUMBER = 1681511377;     // 0x64324D31, little-endian 即 "1M2d"
```

新增包级别构造方法（用于反序列化后构造对象）：

```java
BitmapPositionDeleteIndex(RoaringPositionBitmap bitmap, DeleteFile deleteFile) {
    this.bitmap = bitmap;
    this.deleteFiles = deleteFile != null ? Lists.newArrayList(deleteFile) : Lists.newArrayList();
}
```

`serialize()` 实现：

1. `bitmap.runLengthEncode()` 先做 RLE 压缩，减少体积。
2. 计算 `bitmapDataLength = MAGIC_NUMBER_SIZE_BYTES + bitmap.serializedSizeInBytes()`，并校验总缓冲 ≤ 2GB。
3. 分配缓冲 `[4 + bitmapDataLength + 4]`，按顺序写入：length（big-endian）、magic（little-endian）、bitmap（portable Roaring，little-endian）、CRC32（对 magic+bitmap 计算，big-endian）。
4. `buffer.rewind()` 后返回。

`deserialize(byte[] bytes, DeleteFile deleteFile)` 实现：

1. 读 length（big-endian），与 `deleteFile.contentSizeInBytes() - 4 - 4` 比对校验（DV blob 大小应与 delete file 声明的 content size 一致）。
2. 读 magic，校验等于 `MAGIC_NUMBER`。
3. 用 `RoaringPositionBitmap.deserialize` 读 bitmap。
4. 校验 bitmap 的 `cardinality()` 等于 `deleteFile.recordCount()`（DV 的 recordCount 应表示被删除的行数）。
5. 读 CRC，与对 magic+bitmap 计算的 CRC 比对，不等抛 `IllegalArgumentException("Invalid CRC")`。
6. 返回 `new BitmapPositionDeleteIndex(bitmap, deleteFile)`。

辅助方法：`computeBitmapDataLength`、`serializeBitmapData`、`pointToBitmapData`（设置 little-endian）、`readBitmapDataLength`、`deserializeBitmap`、`computeChecksum`（用 `java.util.zip.CRC32`）。

格式与 Delta 兼容：magic number 0x64324D31（"1M2d"，即 Delta DV magic）、portable Roaring 序列化、CRC32 校验，整体布局与 Delta 的 Puffin DV blob 一致。

### `core/src/test/java/org/apache/iceberg/deletes/TestBitmapPositionDeleteIndex.java`

**修改目的**：用 golden file 双向验证序列化稳定性与反序列化正确性。

**工作逻辑**：

新增常量：

```java
private static final long BITMAP_OFFSET = 0xFFFFFFFFL + 1L;          // 即 2^32，Roaring 高 32 位分桶
private static final long CONTAINER_OFFSET = Character.MAX_VALUE + 1L; // 即 2^16，Roaring 容器分桶
```

新增 4 个测试方法，分别对应 4 种典型 bitmap 形态：

- `testEmptyIndexSerialization`：空 index，对应 `empty-position-index.bin`。
- `testSmallAlternatingValuesIndexSerialization`：1、3、5、7、9（小交替值），对应 `small-alternating-values-position-index.bin`。
- `testSmallAndLargeValuesIndexSerialization`：100、101、`Integer.MAX_VALUE+100`、`Integer.MAX_VALUE+101`（跨 2^31 边界，触发不同 Roaring 高位桶），对应 `small-and-large-values-position-index.bin`。
- `testAllContainerTypesIndexSerialization`：覆盖 Roaring 三种容器类型（array、bitset、run-length encoded），分布在 2 个高位桶 × 3 个低位容器，对应 `all-container-types-position-index.bin`。

`validate(index, goldenFile)` 方法：

1. `index.serialize()` 得到字节。
2. 用 `mockDV(bytes.length, index.cardinality())` mock 一个 `DeleteFile`（提供 `contentSizeInBytes`、`recordCount`）。
3. `PositionDeleteIndex.deserialize(bytes, dv)` 反序列化得到 `indexCopy`，断言内容相等。
4. 读取 golden file 字节，断言序列化字节与 golden 完全一致。
5. 再用 golden 字节反序列化得到 `goldenIndex`，断言与原 index 内容相等。

这样既验证了序列化稳定（与 golden 一致）、又验证了反序列化可还原（双向一致）。

辅助方法：`mockDV`、`assertEqual`（互相 `forEach` 检查 `isDeleted`）、`position(bitmapIndex, containerIndex, value)`（按 Roaring 桶结构组合出全局行号）、`readTestResource`。

### 新增 4 个 golden file（位于 `core/src/test/resources/org/apache/iceberg/deletes/`）

- `empty-position-index.bin`（20 字节）
- `small-alternating-values-position-index.bin`（50 字节）
- `small-and-large-values-position-index.bin`（56 字节）
- `all-container-types-position-index.bin`（94 字节）

这些二进制文件作为"权威序列化结果"锚定，未来若序列化格式无意改动，测试会立刻失败。

## 小结

- **成效**：为 `PositionDeleteIndex` 提供 Puffin blob 兼容的序列化/反序列化能力，DV 可以以"自描述 + CRC 校验"的字节流写入 Puffin 文件、读出后还原；格式与 Delta 兼容便于跨工具读取。
- **影响范围**：2 个 Core 源文件（`PositionDeleteIndex.java`、`BitmapPositionDeleteIndex.java`，新增 +124 行）、1 个测试类（`TestBitmapPositionDeleteIndex.java`，新增 +105 行）、4 个测试资源 golden file（共 ~220 字节）。无 manifest 格式或 API 破坏性变更。
- **回迁到 1.4.x 的注意事项**：
  1. 本提交是 DV 落地的"读写原语"，单独回迁到 1.4.x 的价值取决于 1.4.x 是否引入 DV。**不建议单独回迁**，应与 1335（DV 字段）等 DV 整体特性一起回迁。
  2. `deserialize` 依赖 `DeleteFile.contentSizeInBytes()` 与 `DeleteFile.recordCount()`——前者是 1335 引入的字段。1.4.x 若无 1335，`contentSizeInBytes()` 走 default 返回 null，反序列化会 NPE；所以 1338 强依赖 1335。
  3. 序列化格式与 Delta 兼容是一个跨工具承诺，回迁到 1.4.x 也会让 1.4.x 用户能读取 Delta 写出的 DV blob，反之亦然——这是一项需要慎重承诺的兼容性约束。
  4. Golden file 测试对 JVM 版本与 Roaring 库版本敏感：若 1.4.x 上的 `RoaringPositionBitmap` 实现与 main 有差异，序列化字节可能不一致。回迁时需重跑测试确认。
