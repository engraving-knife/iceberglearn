# 提交 2198：Arrow: Reduce code duplication in VectorizedParquetDefinitionLevelReader (#11661)

## 提交信息

- **序号**：2198 / 4088
- **哈希**：f9cc62eb0d98e360b452a3ab8fdc6efdc4969f6e
- **短哈希**：f9cc62eb0
- **日期**：2025-06-03 04:45:06 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Arrow: Reduce code duplication in VectorizedParquetDefinitionLevelReader (#11661)
- **PR/Issue**：#11661

## 总体目的

这个提交对 `VectorizedParquetDefinitionLevelReader` 类进行重构，减少代码重复。该类是 Iceberg Arrow 向量化读取 Parquet 文件的核心组件之一，负责根据 Parquet 的定义级别（definition level）读取数据并填充 Arrow 向量。原代码中，`NumericBaseReader` 等内部抽象类的 `nextBatch`（普通读取）和 `nextDictEncodedBatch`（字典编码读取）两个方法存在大量结构相同的循环逻辑——都包含"按组读取、根据 mode（RLE/PACKED）分支处理、维护索引和计数"的控制流，差异仅在于具体如何处理每个值。这种重复使得维护成本高、容易在修改时遗漏。本提交通过引入函数式接口 `ReaderFunction` 和提取公共的 `nextBatch` 重载方法，将控制流与具体值处理逻辑分离，消除了重复代码。

## 如何达成设计目的

- 引入 `@FunctionalInterface` 接口 `ReaderFunction`，其 `apply` 方法接收 mode、索引、值数量、字节数组、validity buffer 等参数，封装"如何处理一批值"的逻辑。
- 将原 `NumericBaseReader` 重命名为 `CommonReader`，提取一个私有的 `nextBatch` 重载方法，该方法接收 `ReaderFunction` 参数，负责公共的循环控制流（readNextGroup、计算 numValues、维护 idx、递减 left 和 currentCount）。
- 原有的 `nextBatch`（普通）和 `nextDictEncodedBatch`（字典编码）方法改为调用公共 `nextBatch`，并传入各自的 `ReaderFunction` lambda，在 lambda 内根据 mode 分发到 `nextRleBatch`/`nextPackedBatch` 或 `nextRleDictEncodedBatch`/`nextPackedDictEncodedBatch`。
- 声明抽象方法 `nextRleBatch`、`nextPackedBatch` 等，由各具体子类实现，将原来内联在 switch-case 中的逻辑抽取为独立方法。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/parquet/VectorizedParquetDefinitionLevelReader.java` (修改, +242/-241 lines)

**修改目的**：消除 nextBatch 和 nextDictEncodedBatch 之间的控制流重复，提升可维护性。

**工作逻辑**：
- 新增 `ReaderFunction` 函数式接口，定义 `apply(Mode mode, int idx, int numValues, byte[] byteArray, ArrowBuf validityBuffer)` 方法。
- 将 `NumericBaseReader` 重命名为 `CommonReader`，新增私有 `nextBatch(...)` 重载方法封装公共循环：循环读取组、计算 numValues、调用 `consumer.apply(mode, idx, numValues, byteArray, validityBuffer)`、推进 idx、递减 left 和 currentCount。
- 原 `nextBatch`（普通值读取）改为委托给公共 `nextBatch`，传入 lambda 在 RLE 模式调用 `nextRleBatch`、PACKED 模式调用 `nextPackedBatch`。
- 原 `nextDictEncodedBatch`（字典编码读取）同样改为委托给公共 `nextBatch`，传入 lambda 在 RLE 模式调用 `nextRleDictEncodedBatch`、PACKED 模式调用 `nextPackedDictEncodedBatch`。
- 新增 `protected abstract` 方法声明：`nextRleBatch`、`nextPackedBatch`、`nextRleDictEncodedBatch`、`nextPackedDictEncodedBatch`，由各数值类型子类实现，将原内联逻辑拆分为独立方法。
- 各具体子类（如整型、浮点型等 reader）相应实现这些抽象方法，逻辑与原 switch-case 内的代码等价。

## 总结

该提交是一次纯重构，通过引入函数式接口和提取公共控制流方法，消除了 `VectorizedParquetDefinitionLevelReader` 中 `nextBatch` 与 `nextDictEncodedBatch` 之间的大量重复代码。重构不改变运行时行为，但显著提升了代码的可维护性和可读性，使后续修改控制流只需改一处。
