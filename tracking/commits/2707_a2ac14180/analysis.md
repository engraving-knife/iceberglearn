# 提交 2707：core: Adding read vector to range readable interface and adding mapper to parquet stream (#13997)

## 提交信息

- **序号**：2707 / 4088
- **哈希**：a2ac14180ecb8e38bb8172e715172b26194b763d
- **短哈希**：a2ac14180
- **日期**：2025-09-30 08:21:24 -0700
- **作者**：stubz151
- **提交说明**：core: Adding read vector to range readable interface and adding mapper to parquet stream (#13997)
- **PR/Issue**：#13997

## 总体目的

本提交为 Iceberg 的 `RangeReadable` 接口新增"向量读取"（vectored read）能力，并新增一个适配器将 Iceberg 的 `RangeReadable` 流适配为 Parquet（Apache Parquet Java 库）所期望的 `readVectored` 接口，使 Parquet 读取器能够利用向量化的范围读（Vectored IO）来提升读取性能。

**背景**：现代云存储（如 S3、Azure ADLS、GCS）与 HDFS 都支持"范围读"（range read / byte-range fetch），即一次性请求文件中多个不连续的字节区间，比逐区间串行请求更高效（减少往返、可并发）。Iceberg 早已定义 `RangeReadable` 接口（含 `readFully(position, buffer, offset, length)` 与 `readTail(...)`），允许 `InputFile` 实现按需读取特定范围。但该接口此前缺少一次读取多个范围的批量能力。

Apache Parquet Java 库较新版本引入了 Vectored IO 支持：`SeekableInputStream` 可实现 `readVectored(List<ParquetFileRange>, ByteBufferAllocator)` 与 `readVectoredAvailable(...)`，Parquet 读取器在读取列块（column chunk）/行组（row group）时会将多个范围合并提交给底层流并发读取。为了让 Iceberg 的 Parquet 读取路径能享受这一优化，需要：

1. 在 Iceberg `RangeReadable` 接口中定义通用的 `readVectored(List<FileRange>, IntFunction<ByteBuffer>)` 默认方法与 `FileRange` 数据结构；
2. 在 `ParquetIO` 中新增适配器 `ParquetRangeReadableInputStreamAdapter`，把 Parquet 的 `readVectored(List<ParquetFileRange>, ByteBufferAllocator)` 调用翻译为 Iceberg `RangeReadable.readVectored(List<FileRange>, IntFunction<ByteBuffer>)`，并将 Parquet 的 `ParquetFileRange` 转换为 Iceberg `FileRange`，把完成的数据 future 回填给 Parquet；
3. 在 `Parquet` 读取选项中启用 `withUseHadoopVectoredIo(true)`，让 Parquet 读取器实际走 vectored 路径。

这样当底层 `InputFile` 的流实现了 `RangeReadable`（如 Hadoop S3A 等），Parquet 读取即可通过向量读减少远程请求次数、提升性能。

## 如何达成设计目的

1. **`FileRange` 数据结构**：封装一个字节范围（offset、length）与对应的 `CompletableFuture<ByteBuffer>`，作为 `readVectored` 的输入/输出载体。读取完成后 future 被 complete，调用方从中获取数据。
2. **`RangeReadable.readVectored` 默认方法**：接收 `List<FileRange>` 与 `IntFunction<ByteBuffer>` 分配器；先 `sortRanges` 排序并校验无重叠，再对每个范围用分配器分配 buffer、`readFully(offset, buffer.array())` 同步读取、`range.byteBuffer().complete(buffer)` 完成 future。提供默认同步实现，子类可覆盖为真正的异步并发实现。`sortRanges` 静态方法排序并检查重叠（`current.offset >= prev.offset + prev.length`）。
3. **`ParquetRangeReadableInputStreamAdapter`**：当 `ParquetIO.stream(...)` 检测到底层 Iceberg 流同时是 `SeekableInputStream` 与 `RangeReadable` 时，用该适配器包装（否则用普通 `ParquetInputStreamAdapter`）。适配器实现 Parquet 的 `readVectored(List<ParquetFileRange>, ByteBufferAllocator)`：把每个 `ParquetFileRange` 转为 `FileRange`（共享 future），调用 Iceberg `delegate.readVectored(...)`；`readVectoredAvailable` 返回 `true`。
4. **启用 Parquet Vectored IO**：在 `Parquet` 读取构建处 `optionsBuilder.withUseHadoopVectoredIo(true)`。
5. **测试**：`TestParquetRangeReadableInputStreamAdapter` 用 mock `RangeReadable` 流验证适配器的 `readVectoredAvailable` 返回 true、`readVectored` 正确转换范围并完成 future 且数据内容正确。

## 修改详情

### `api/src/main/java/org/apache/iceberg/io/FileRange.java` (+55/-0 lines, 新文件)

**修改目的**：定义字节范围数据结构。

**工作逻辑**：`FileRange` 持有 `CompletableFuture<ByteBuffer> byteBuffer`、`long offset`、`int length`。构造时校验 `byteBuffer` 非空、`length >= 0`、`offset >= 0`（注意 `length()` 在校验时调用，存在轻微的初始化顺序问题但语义上 length 已赋值）。提供 `byteBuffer()`/`offset()`/`length()` 访问器。该类作为 `readVectored` 的输入：调用方构造范围并附 future，读取方完成 future。

### `api/src/main/java/org/apache/iceberg/io/RangeReadable.java` (+63/-0 lines)

**修改目的**：为 `RangeReadable` 接口新增 `readVectored` 默认方法与范围排序校验工具。

**工作逻辑**：
- 新增 `default void readVectored(List<FileRange> ranges, IntFunction<ByteBuffer> allocate)`：先 `sortRanges(ranges)` 排序校验，再对每个范围分配 buffer、`readFully(range.offset(), buffer.array())` 同步读取、`range.byteBuffer().complete(buffer)` 完成 future。这是默认同步实现，实现类可覆盖为异步并发。
- 新增 `static List<FileRange> sortRanges(List<FileRange> input)`：非空校验；若元素数 < 2 直接返回；否则按 `offset` 升序排序，遍历检查相邻范围不重叠（`current.offset() >= prev.offset() + prev.length()`），重叠抛 `IllegalArgumentException`。Javadoc 说明 `readVectored` 的语义：异步读取多个范围、`getPos()` 调用后位置未定义、并发普通读可能阻塞、范围不可重叠。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+1/-0 lines)

**修改目的**：启用 Parquet 的 Vectored IO 选项。

**工作逻辑**：在 Parquet 读取构建处（`ParquetReadOptions.Builder` 构建前）新增 `optionsBuilder.withUseHadoopVectoredIo(true)`，使 Parquet 读取器在读取列块时走 vectored 路径，调用流的 `readVectored`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetIO.java` (+83/-0 lines)

**修改目的**：新增 `ParquetRangeReadableInputStreamAdapter` 适配器，桥接 Parquet vectored 接口与 Iceberg `RangeReadable`。

**工作逻辑**：
- `stream(...)` 方法中，在原有 `HadoopStreams.wrap` 分支后新增：若底层流是 `RangeReadable`，返回 `new ParquetRangeReadableInputStreamAdapter(stream)`；否则返回普通 `ParquetInputStreamAdapter`。
- 新增 `@VisibleForTesting static class ParquetRangeReadableInputStreamAdapter<T extends SeekableInputStream & RangeReadable> extends DelegatingSeekableInputStream implements RangeReadable`：
  - 委托 `getPos`/`seek`/`readFully`/`readTail` 给 delegate；
  - `readVectoredAvailable(ByteBufferAllocator)` 返回 `true`；
  - `readVectored(List<ParquetFileRange> ranges, ByteBufferAllocator allocate)`：把 `allocate::allocate` 转为 `IntFunction<ByteBuffer>`，调用 `convertRanges(ranges)` 把 `ParquetFileRange` 列表转为 `FileRange` 列表（共享 `CompletableFuture`：先创建 future，`parquetFileRange.setDataReadFuture(future)`，再用该 future 构造 `FileRange`），再调用 `delegate.readVectored(delegateRange, delegateAllocate)`。delegate 读取完成后完成 future，Parquet 端通过 `getDataReadFuture()` 获取数据。`EOFException` 转为 `RuntimeIOException`。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetRangeReadableInputStreamAdapter.java` (+147/-0 lines, 新文件)

**修改目的**：测试适配器的 vectored 行为。

**工作逻辑**：
- `testRangeReadableAdapterReadVectoredAvailable`：构造适配器，断言 `readVectoredAvailable` 返回 true。
- `testRangeReadableAdapterReadVectored`：mock `ByteBufferAllocator` 按长度返回 buffer；构造两个 `ParquetFileRange`（offset 0/length 100、offset 200/length 50）；调用 `adapter.readVectored(ranges, allocator)`；`MockRangeReadableStream.readVectored` 实现按 `range.offset() + i` 填充数据并 complete future；断言 allocator 被调用两次、两个 range 的 future 均 done、读出的字节内容与预期一致（`expected1[i] = (byte) i`、`expected2[i] = (byte)(200+i)`）。
- `MockRangeReadableStream`：继承 `SeekableInputStream` 实现 `RangeReadable`，`readVectored` 用 `allocate` 分配 buffer、按 offset+i 填充、flip 后 complete future。

## 总结

本提交为 Iceberg 引入向量读（Vectored IO）支持：在 `RangeReadable` 接口新增 `readVectored(List<FileRange>, IntFunction<ByteBuffer>)` 默认方法与 `FileRange` 数据结构（含 future 完成机制、范围排序与重叠校验）；在 `ParquetIO` 新增 `ParquetRangeReadableInputStreamAdapter` 将 Parquet 的 vectored 接口桥接到 Iceberg `RangeReadable`；并在 Parquet 读取选项中启用 `withUseHadoopVectoredIo(true)`。这样当底层存储流支持范围读时，Parquet 读取器可一次性并发获取多个字节范围，减少远程请求次数、提升读取性能。配套测试以 mock 验证适配器的范围转换与 future 完成逻辑。
