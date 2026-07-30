# 提交 0597：Parquet: Refactor BasePageIterator to add initRepetitionLevelsReader

## 提交信息

- **序号**：0597 / 4088
- **哈希**：560b72344350816eb31f9a165c2947caa7381a9b
- **短哈希**：560b72344
- **日期**：2024-03-15（Fri Mar 15 22:24:23 2024 +0800）
- **作者**：Gang Wu <ustcwg@gmail.com>
- **提交说明**：Parquet: Refactor BasePageIterator to add initRepetitionLevelsReader (#9751)
- **PR/Issue**：#9751

## 总体目的

Apache Iceberg 的 Parquet 读取层中，`BasePageIterator` 是所有列式数据页迭代器的抽象基类，负责把一个 Parquet `DataPage`（V1 或 V2）拆解为 repetition level（重复级别，RL）、definition level（定义级别，DL）和具体值三路读取器，再以 `IntIterator` 形式对外提供 RL/DL 流。

在重构前的代码里，RL 读取器的构造逻辑被「硬编码」在两个 `initFromPage` 重载里：
- `initFromPage(DataPageV1)` 直接 `initPage.getRlEncoding().getValuesReader(...)` 拿到 `ValuesReader`，包成 `ValuesReaderIntIterator`，再 `rlReader.initFromPage(count, in)`。
- `initFromPage(DataPageV2)` 直接 `newRLEIterator(desc.getMaxRepetitionLevel(), initPage.getRepetitionLevels())`。

相比之下，DL 与值的初始化早已通过抽象方法 `initDefinitionLevelsReader(...)`（V1/V2 两个重载）和 `initDataReader(...)` 下沉到子类 `PageIterator` 去定制。这种不对称带来一个问题：任何想要定制 RL 解码行为的子类（例如未来要做向量化读取、要做按位置过滤、要在读取 RL 时做额外缓冲或裁剪的迭代器），都无法仅覆盖一个方法，而必须整体重写 `initFromPage`，复制一大段字节流编排与异常包装逻辑，容易出错且与基类实现耦合。

本提交的目的：把 RL 的初始化逻辑抽取成两个新的 `protected` 方法 `initRepetitionLevelsReader(...)`（V1 与 V2 各一），使 RL 与 DL、值读取器一样成为「可被子类覆盖的扩展点」，统一三种读取器的初始化模式，为后续的定制化迭代器铺路。这是一次典型的「为未来扩展做准备」的纯重构，不改变运行时行为。

## 如何达成设计目的

设计者采用了与现有 `initDefinitionLevelsReader` 完全对称的手法，但有一个关键区别——新方法不是 `abstract`，而是带默认实现的 `protected`：

1. 新增两个 `protected void initRepetitionLevelsReader(...)` 重载，方法签名与 `initDefinitionLevelsReader` 一一对应：
   - V1 版：`initRepetitionLevelsReader(DataPageV1 dataPageV1, ColumnDescriptor descriptor, ByteBufferInputStream in, int count)`，方法体把原先散落在 `initFromPage(V1)` 里的三行（取 encoding → 构造 `ValuesReader` → 包成 `ValuesReaderIntIterator` → `initFromPage(count, in)`）整体搬过来。
   - V2 版：`initRepetitionLevelsReader(DataPageV2 dataPageV2, ColumnDescriptor descriptor)`，方法体把原先的 `newRLEIterator(...)` 调用搬过来。
2. `initFromPage(DataPageV1)` 与 `initFromPage(DataPageV2)` 中对应的内联代码被删除，替换为对上述新方法的调用，方法签名恰好与 DL 的调用形式对称（`initRepetitionLevelsReader(...)` 紧跟在 RL 调试点之后、`initDefinitionLevelsReader(...)` 之前）。
3. 不改任何字段、不改 `IntIterator` 体系、不改 `newRLEIterator` 辅助方法、不改 `PageIterator` 子类——因为新方法是 `protected`（非 `abstract`），`PageIterator` 自动继承默认实现，源码无需任何改动。这是「保留向后兼容」的关键：既有的所有 `BasePageIterator` 子类无须改动即可继续工作。
4. 异常处理保持不变：V1 的 `initRepetitionLevelsReader` 方法声明 `throws IOException`，但实际不会抛出（与原内联代码一致），调用方仍在 `try/catch` 中把它包成 `ParquetDecodingException`。

之所以选择 `protected` 而非 `abstract`，是经过权衡的：
- 选 `abstract` 会强制所有现有子类（`PageIterator` 及其若干类型化子类）立刻补实现，破坏二进制兼容性，且默认实现本身就有意义。
- 选 `protected` 带默认实现既提供了扩展点，又保持「默认行为不变」，是一次零风险的纯提取重构。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/BasePageIterator.java`

**修改目的**：把 RL 读取器初始化从 `initFromPage` 内联代码抽取为可覆盖的 `protected` 方法，对称化 RL/DL/值三类读取器的初始化接口。

**工作逻辑**：

新增的两个方法（位于抽象方法 `initDefinitionLevelsReader` 之后、`currentPageCount()` 之前）：

```java
protected void initRepetitionLevelsReader(
    DataPageV1 dataPageV1, ColumnDescriptor descriptor, ByteBufferInputStream in, int count)
    throws IOException {
  ValuesReader rlReader =
      dataPageV1.getRlEncoding().getValuesReader(descriptor, ValuesType.REPETITION_LEVEL);
  this.repetitionLevels = new ValuesReaderIntIterator(rlReader);
  rlReader.initFromPage(count, in);
}

protected void initRepetitionLevelsReader(DataPageV2 dataPageV2, ColumnDescriptor descriptor)
    throws IOException {
  this.repetitionLevels =
      newRLEIterator(descriptor.getMaxRepetitionLevel(), dataPageV2.getRepetitionLevels());
}
```

V1 版工作逻辑（这是本次重构的重点，需理解 RL 读取器如何被构造与初始化）：

1. Parquet V1 数据页把 RL、DL、值三段连续存储在同一个字节流里。RL 段位于最前面（offset 0），其编码方式由 `dataPageV1.getRlEncoding()` 给出（通常是 `RLE` 或 `BIT_PACKED` 等 `Encoding`）。
2. 通过 `getRlEncoding().getValuesReader(descriptor, ValuesType.REPETITION_LEVEL)` 拿到一个针对「重复级别」的 `ValuesReader` 实例。`ValuesType.REPETITION_LEVEL` 告诉工厂方法「我要读的是 RL 而不是 DL 或值」，这会影响解码器的初始化（例如位宽由 `descriptor.getMaxRepetitionLevel()` 决定）。
3. 把这个 `ValuesReader` 包成 `ValuesReaderIntIterator`（`BasePageIterator` 的内部静态类，`nextInt()` 委托给 `delegate.readInteger()`），赋给字段 `this.repetitionLevels`。这样后续 `next()` 调用就能以统一 `IntIterator` 接口逐个读出 RL。
4. `rlReader.initFromPage(count, in)` 是关键一步：它让 RL 解码器从字节流 `in` 的当前位置「消费」自己那段字节，并把 `in` 的 position 推进到 DL 段的起点。`count` 是该页的 value 总数（即 `triplesCount`），解码器据此判断需要读多少个 RL 值。这一步之后，`in.position()` 正好指向 DL 段开头，紧接着的 `initDefinitionLevelsReader(initPage, desc, in, triplesCount)` 才能正确开始读 DL。这就是 V1 页面里 RL/DL/值三段必须严格顺序初始化的原因，也是把 RL 抽出来但仍由 `initFromPage` 编排的原因。

V2 版工作逻辑：

1. V2 页面把 RL、DL、值分别独立存储在三个 `BytesInput` 字段里（`getRepetitionLevels()`、`getDefinitionLevels()`、`getData()`），没有连续流，因此不需要 `ByteBufferInputStream` 参数。
2. V2 的 RL/DL 固定用 RLE 混合编码（Run-Length Bit-Packing Hybrid），所以直接调 `newRLEIterator(maxLevel, bytes)`：
   - 若 `maxLevel == 0`（非嵌套列，RL 恒为 0），返回 `NullIntIterator` 省去解码——这是 Iceberg 对扁平列的优化。
   - 否则用 `RunLengthBitPackingHybridDecoder` 包装字节流，位宽由 `BytesUtils.getWidthFromMaxInt(maxLevel)` 计算（如 maxLevel=3 → 2 bits）。
3. 因为 V2 各段独立，V2 的 `initFromPage` 把 RL 初始化放在 `try` 块最前面，紧跟 `initDefinitionLevelsReader` 和 `initDataReader`，三者互不依赖流的 position。

`initFromPage(DataPageV1)` 的变化（删除内联代码，替换为调用）：

```java
// 删除：
//   ValuesReader rlReader = initPage.getRlEncoding().getValuesReader(desc, ValuesType.REPETITION_LEVEL);
//   this.repetitionLevels = new ValuesReaderIntIterator(rlReader);
//   ... rlReader.initFromPage(triplesCount, in);
// 替换为：
initRepetitionLevelsReader(initPage, desc, in, triplesCount);
```

调用点仍位于 `LOG.debug("reading repetition levels at 0")` 之后、`LOG.debug("reading definition levels at {}", in.position())` 之前，保持原有日志顺序与流位置语义不变。

`initFromPage(DataPageV2)` 的变化：

```java
// 删除（原本在 try 之前）：
//   this.repetitionLevels = newRLEIterator(desc.getMaxRepetitionLevel(), initPage.getRepetitionLevels());
// 替换为（移到 try 内第一行）：
try {
  initRepetitionLevelsReader(initPage, desc);
  initDefinitionLevelsReader(initPage, desc);
  ...
}
```

注意 V2 版本里调用位置从 `try` 块外移到了 `try` 块内首行。原代码把 `newRLEIterator` 放在 `try` 外是因为它内部已自己 try/catch `IOException` 并抛 `ParquetDecodingException`；新代码把它移进 `try` 后，即使 `initRepetitionLevelsReader` 抛 `IOException`（默认实现不会，但子类可能），也会被同一个 catch 包装为 `ParquetDecodingException`，行为更一致、更安全。

## 小结

- 这是一次纯重构提交：仅 1 个文件、净 +11 行（+17 / -6），不改变任何运行时行为，`PageIterator` 及其子类零改动，二进制兼容。
- 设计价值在于「对称化」：让 RL 与已有的 DL、值读取器一样具备可覆盖的初始化接口，消除「RL 不可定制」这一历史不对称。新方法选 `protected`（带默认实现）而非 `abstract`，既提供扩展点又不破坏既有子类。
- 回迁到 1.4.x 的注意事项：
  - 风险极低，可直接 cherry-pick。1.4.x 上 `BasePageIterator` 的 `initFromPage(V1/V2)` 仍是内联 RL 逻辑，cherry-pick 后会与 main 的最终形态一致。
  - 需确认 1.4.x 上该文件没有其他本地改动（如中文注释提交 `0879cfe50` 在 1.4.x 上给该文件加了注释，但注释是 doc-comment，不影响逻辑，cherry-pick 时可能有少量上下文冲突需手动解决，但语义无冲突）。
  - 该重构本身不带来新功能，但为后续任何想定制 RL 解码的特性（如向量化读取增强、按 RL 过滤、position-aware 读取）打开扩展点。若 1.4.x 后续无此类定制需求，回迁价值主要是「与主线保持一致、降低未来合并冲突」。
