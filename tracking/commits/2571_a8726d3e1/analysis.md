# 提交 2571：Arrow, Spark: Fix Direct Memory Leak on Vectorized Parquet Mixed Encoding Pages (#13935)

## 提交信息

- **序号**：2571 / 4088
- **哈希**：a8726d3e14f1f11ceab53d5216d2e2a42759f28b
- **短哈希**：a8726d3e1
- **日期**：2025-08-28 18:54:23 -0500
- **作者**：Russell Spitzer
- **提交说明**：Arrow, Spark: Fix Direct Memory Leak on Vectorized Parquet Mixed Encoding Pages (#13935)
- **PR/Issue**：#13935

## 总体目的

此次提交修复了向量化 Parquet 读取器在遇到"混合编码页"（Mixed Encoding Pages）时的堆外内存泄漏问题。在 Iceberg 的向量化读取路径中，当同一列在不同数据页中使用了不同的编码方式（例如某一页是字典编码 dictEncoded，另一页是普通编码）时，`VectorizedArrowReader` 会在读取过程中切换读取类型（ReadType）。原先在切换类型并重新分配 Arrow `FieldVector` 时，旧的 vector 没有被显式关闭，导致其占用的堆外内存（direct memory）无法被释放，长期运行会持续累积直至耗尽直接内存。

这是一个影响生产环境的严重资源泄漏问题：当 Parquet 文件中存在混合编码（部分页字典编码、部分页非字典编码）时，每次类型切换都会泄漏一个 vector 的内存，在读取大量文件或大表时会导致直接内存溢出（OOM）。

此外，提交还对 `VectorizedReaderBuilder` 的内存分配器（BufferAllocator）进行了重构，使其支持外部传入自定义分配器，而非硬编码使用 `ArrowAllocation.rootAllocator()`。这为测试中精确度量内存泄漏提供了基础——测试可以创建独立的子分配器，在读取完成后断言其剩余内存为 0，从而验证泄漏已修复。

## 如何达成设计目的

- **核心修复**：在 `VectorizedArrowReader` 中，当检测到读取类型变化需要重新分配 vector 时，先调用 `vec.close()` 关闭旧的 vector，再调用 `allocateFieldVector(...)` 分配新的，避免旧 vector 内存泄漏。
- **分配器可注入**：为 `VectorizedReaderBuilder` 新增一个 protected 构造函数，接收外部 `BufferAllocator` 参数；原有公开构造函数委托给新构造函数并默认使用 `ArrowAllocation.rootAllocator()`，保持向后兼容。
- **Spark 读取器适配**：在 Spark 各版本（v3.4/v3.5/v3.6）的 `VectorizedSparkParquetReaders` 中新增带 `BufferAllocator` 参数的 `buildReader` 重载，并将内部 Builder 的构造调用传入该分配器；保留旧的重载方法以默认分配器调用，维持兼容。
- **回归测试**：在测试基类中新增 `assertNoLeak` 工具方法，使用独立子分配器执行读取逻辑后断言 `getAllocatedMemory() == 0`，并将现有读取测试包装进该断言中，确保混合编码场景下内存被完全释放。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (+5)

**修改目的**：修复类型切换时旧 vector 未关闭导致的内存泄漏。

**工作逻辑**：在 `setReady`/类型判断逻辑中，当 `reuse == null` 或读取类型（dictEncoded 与 readType）不匹配需要重新分配 vector 时，先检查 `vec != null`，若存在则调用 `vec.close()` 释放其堆外内存，然后再调用 `allocateFieldVector(dictEncoded)` 分配新 vector 并新建 `NullabilityHolder`。注释明确说明"vector 可能已存在但类型不同，需要先清理"。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedReaderBuilder.java` (+21/-1)

**修改目的**：使内存分配器可外部注入，便于测试度量泄漏。

**工作逻辑**：
- 新增 protected 构造函数，额外接收 `BufferAllocator bufferAllocator` 参数，使用 `bufferAllocator.newChildAllocator("VectorizedReadBuilder", 0, Long.MAX_VALUE)` 创建子分配器。
- 原有公开构造函数改为委托调用新构造函数，传入 `ArrowAllocation.rootAllocator()` 作为默认分配器，保持向后兼容。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java` (+23/-2)

**修改目的**：Spark v3.4 读取器支持外部分配器注入。

**工作逻辑**：
- `buildReader(...)` 新增带 `BufferAllocator bufferAllocator` 参数的重载，将该分配器透传给内部 `SparkReaderBuilder` 构造函数。
- 保留原有四参数 `buildReader` 重载，内部以 `ArrowAllocation.rootAllocator()` 调用新重载，保持兼容。
- 内部 `SparkReaderBuilder` 构造函数新增 `bufferAllocator` 参数并传递给父类 `VectorizedReaderBuilder`。

### `spark/v3.5/spark/.../VectorizedSparkParquetReaders.java` 与 `spark/v3.6/spark/.../VectorizedSparkParquetReaders.java` (各 +23/-2)

**修改目的**：与 v3.4 相同的适配，应用于 Spark v3.5 和 v3.6 版本。

**工作逻辑**：与 v3.4 完全一致的改动模式，为各版本读取器注入分配器并保留兼容重载。

### `spark/v3.4/spark/src/test/java/.../TestParquetVectorizedReads.java` (+68/-15)

**修改目的**：新增内存泄漏断言，将读取测试包装在 `assertNoLeak` 中。

**工作逻辑**：
- 新增 `assertNoLeak(String testName, Consumer<BufferAllocator> testFunction)` 方法：创建独立子分配器执行测试函数，结束后断言 `allocator.getAllocatedMemory() == 0`，并在 finally 中关闭分配器。
- 将 `assertRecordsMatch` 方法中的读取逻辑改为接收一个 `BufferAllocator` 并通过 `assertNoLeak` 执行，`buildReader` 调用传入该分配器。
- 这样混合编码页场景下若仍泄漏，断言会失败。

### `spark/v3.5/.../TestParquetDictionaryEncodedVectorizedReads.java` 与 `spark/v3.6/...` 测试 (各 +51/-15, +68/-15)

**修改目的**：在 v3.5、v3.6 版本同步引入 `assertNoLeak` 断言与分配器注入测试。

**工作逻辑**：与 v3.4 测试改动一致，确保各 Spark 版本都能检测内存泄漏。

## 总结

此次提交修复了向量化 Parquet 读取在混合编码页场景下的堆外内存泄漏：核心是在 `VectorizedArrowReader` 切换读取类型重新分配 vector 前关闭旧 vector。同时重构 `VectorizedReaderBuilder` 使 `BufferAllocator` 可外部注入，并在 Spark 各版本读取器中透传分配器，最后新增 `assertNoLeak` 测试工具方法，通过独立子分配器断言读取完成后内存归零，形成完整的修复与回归验证闭环。
